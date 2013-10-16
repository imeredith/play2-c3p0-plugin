package io.applicative.play2.plugins

import play.api.Application
import play.api.db.DBPlugin
import play.api.Configuration
import play.api.db.DBApi
import java.sql.DriverManager
import javax.sql.DataSource
import java.sql.Driver
import com.mchange.v2.c3p0.ComboPooledDataSource
import play.api.Logger
import java.sql.Connection
import play.api.libs.JNDI
import com.mchange.v2.c3p0.DataSources
import play.api.Mode

class C3P0Plugin(app: Application) extends DBPlugin{
	lazy val dbConfig = app.configuration.getConfig("db").getOrElse(Configuration.empty)
	 private def dbURL(conn: Connection): String = {
    val u = conn.getMetaData.getURL
    conn.close()
    u
  }

  // should be accessed in onStart first
  private lazy val dbApi: DBApi = new C3P0Api(dbConfig, app.classloader)

  /**
   * plugin is disabled if either configuration is missing or the plugin is explicitly disabled
   */
  private lazy val isDisabled = {
    false
  }

  /**
   * Is this plugin enabled.
   *
   * {{{
   * dbplugin=disabled
   * }}}
   */
  override def enabled = isDisabled == false

  /**
   * Retrieves the underlying `DBApi` managing the data sources.
   */
  def api: DBApi = dbApi

  var retries=0

  /**
   * Reads the configuration and connects to every data source.
   */
   def connect {   
    retries=retries+1
   dbApi.datasources.map { ds =>
      try {
        ds._1.getConnection.close()
        app.mode match {
          case Mode.Test =>0
          case mode => Logger("play").info("ret=" + retries + "aaadatabase [" + ds._2 + "] connected at " + dbURL(ds._1.getConnection))             
        }
      } catch {
        case e: Exception => {          
          if(retries>20)  {
            throw dbConfig.reportError(ds._2 + ".url", "aaCannot connect to database [" + ds._2 + "]", Some(e.getCause))        
           }else{             
              Thread.sleep(5000) 
              connect
           } 
        }
      }
    }
  }
  override def onStart() {
    retries=0
    connect
    // Try to connect to each, this should be the first access to dbApi
  
  }

  /**
   * Closes all data sources.
   */
  override def onStop() {
    dbApi.datasources.foreach {
      case (ds, _) => try {
        dbApi.shutdownPool(ds)
      } catch { case _: Exception => }
    }
    val drivers = DriverManager.getDrivers()
    while (drivers.hasMoreElements) {
      val driver = drivers.nextElement
      DriverManager.deregisterDriver(driver)
    }
  }

	
}

class  C3P0Api(configuration: Configuration, classloader: ClassLoader) extends DBApi {
  private val dbNames = configuration.subKeys
  private def error(db: String, message: String = "") = throw configuration.reportError(db, message)
  
  val datasources: List[(DataSource, String)] = dbNames.map { dbName =>
    val url = configuration.getString(dbName + ".url").getOrElse(error(dbName, "Missing configuration [db." + dbName + ".url]"))
    val driver = configuration.getString(dbName + ".driver").getOrElse(error(dbName, "Missing configuration [db." + dbName + ".driver]"))
    val extraConfig = configuration.getConfig(dbName).getOrElse(error(dbName, "Missing configuration [db." + dbName + "]"))
    register(driver, extraConfig)
    createDataSource(dbName, url, driver, extraConfig) -> dbName
  }.toList
  
 private def register(driver: String, c: Configuration) {
    try {
      DriverManager.registerDriver(new play.utils.ProxyDriver(Class.forName(driver, true, classloader).newInstance.asInstanceOf[Driver]))
    } catch {
      case e: Exception => throw c.reportError("driver", "Driver not found: [" + driver + "]", Some(e))
    }
  }
  
  private def createDataSource(dbName: String, url: String, driver: String, conf: Configuration): DataSource = {

    val datasource = new ComboPooledDataSource

    // Try to load the driver
    conf.getString("driver").map { driver =>
      try {
        DriverManager.registerDriver(new play.utils.ProxyDriver(Class.forName(driver, true, classloader).newInstance.asInstanceOf[Driver]))
      } catch {
        case e: Exception => throw conf.reportError("driver", "Driver not found: [" + driver + "]", Some(e))
      }
    }

    val autocommit = conf.getBoolean("autocommit").getOrElse(true)
    val isolation = conf.getString("isolation").map {
      case "NONE" => Connection.TRANSACTION_NONE
      case "READ_COMMITTED" => Connection.TRANSACTION_READ_COMMITTED
      case "READ_UNCOMMITTED " => Connection.TRANSACTION_READ_UNCOMMITTED
      case "REPEATABLE_READ " => Connection.TRANSACTION_REPEATABLE_READ
      case "SERIALIZABLE" => Connection.TRANSACTION_SERIALIZABLE
      case unknown => throw conf.reportError("isolation", "Unknown isolation level [" + unknown + "]")
    }
    val catalog = conf.getString("defaultCatalog")
    val readOnly = conf.getBoolean("readOnly").getOrElse(false)

    //datasource.get.setClassLoader(classloader)

    val logger = Logger("com.c3p0")

    
    val PostgresFullUrl = "^postgres://([a-zA-Z0-9_]+):([^@]+)@([^/]+)/([^\\s]+)$".r
    val MysqlFullUrl = "^mysql://([a-zA-Z0-9_]+):([^@]+)@([^/]+)/([^\\s]+)$".r
    val MysqlCustomProperties = ".*\\?(.*)".r

    conf.getString("url") match {
      case Some(PostgresFullUrl(username, password, host, dbname)) =>
        datasource.setJdbcUrl("jdbc:postgresql://%s/%s".format(host, dbname))
        datasource.setUser(username)
        datasource.setPassword(password)
      case Some(url @ MysqlFullUrl(username, password, host, dbname)) =>
        val defaultProperties = """?useUnicode=yes&characterEncoding=UTF-8&connectionCollation=utf8_general_ci"""
        val addDefaultPropertiesIfNeeded = MysqlCustomProperties.findFirstMatchIn(url).map(_ => "").getOrElse(defaultProperties)
        datasource.setJdbcUrl("jdbc:mysql://%s/%s".format(host, dbname + addDefaultPropertiesIfNeeded))
        datasource.setUser(username)
        datasource.setPassword(password)
      case Some(s: String) =>
        datasource.setJdbcUrl(s)
      case _ =>
        throw conf.globalError("Missing url configuration for database [%s]".format(conf))
    }

    conf.getString("user").map(datasource.setUser(_))
    conf.getString("pass").map(datasource.setPassword(_))
    conf.getString("password").map(datasource.setPassword(_))
    
    datasource.setDriverClass(driver)
    datasource.setAcquireIncrement(conf.getInt("acquireIncrement").getOrElse(1))
    datasource.setAcquireRetryAttempts(conf.getInt("acquireRetryAttempts").getOrElse(10))
    datasource.setMinPoolSize(conf.getInt("minPoolSize").getOrElse(10));
    datasource.setMaxPoolSize(conf.getInt("maxPoolSize").getOrElse(10));
    datasource.setMaxIdleTime(conf.getInt("maxIdleTime").getOrElse(0));
                 
  
    // Bind in JNDI
    conf.getString("jndiName").map { name =>
      JNDI.initialContext.rebind(name, datasource)
      Logger("play").info("datasource [" + conf.getString("url").get + "] bound to JNDI as " + name)
    }

    datasource

  }
  def shutdownPool(ds: DataSource) = {
    ds match {
      case ds: ComboPooledDataSource => DataSources.destroy( ds );
      case _ => error(" - could not recognize DataSource, therefore unable to shutdown this pool")
    }
  }

  /**
   * Retrieves a JDBC connection, with auto-commit set to `true`.
   *
   * Don’t forget to release the connection at some point by calling close().
   *
   * @param name the data source name
   * @return a JDBC connection
   * @throws an error if the required data source is not registered
   */
  def getDataSource(name: String): DataSource = {
    datasources.filter(_._2 == name).headOption.map(e => e._1).getOrElse(error(" - could not find datasource for " + name))
  }

}