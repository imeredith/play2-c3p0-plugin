Play 2.1 C3P0 plugin
====================

This plugin provides an alternative to BoneCP as the connection pool manager in Play 2.1.
The main reason for doing this is that I have seen issues with connections not being released
properly by BoneCP when there are low max connections available. 

To use this plugin you need to add `dbplugin=disabled` to your `conf/application.conf` file. 

Add `val c3p0 = RootProject(uri("git://github.com/hadashi/play2-c3p0-plugin.git"))` to your `project/Build.scala`
and then add `.dependsOn(c3p0)` to the `play.Project` line.
