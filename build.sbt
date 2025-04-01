lazy val snackomatenVersion = "0.4.0"

lazy val osLibVer = "0.11.3" 

lazy val jlineVer = "3.29.0" // https://github.com/jline/jline3

ThisBuild / organization := "se.bjornregnell"
ThisBuild / version      := snackomatenVersion
ThisBuild / scalaVersion := "3.6.4"

Global / onChangedBuildSource := ReloadOnSourceChanges

fork := true
run / javaOptions += "-Xmx8G"
outputStrategy := Some(StdoutOutput)
run / connectInput := true

Compile / doc / scalacOptions ++= Seq("-siteroot", "https://fileadmin.cs.lth.se/snackomaten/api")
Compile / doc / target := file("target/api")
Compile / doc / scalacOptions ++= Seq("-deprecation", "-project", "snackomaten")

lazy val root = (project in file("."))
  .aggregate(common, client, server)
  .settings(
    console / initialCommands := """import snackomaten.*""",
  )
  .dependsOn(common, client, server)

lazy val common = (project in file("common"))
  .settings(
    libraryDependencies += "com.lihaoyi" %% "os-lib" % osLibVer,
    libraryDependencies += "org.jline" % "jline" % jlineVer,
    console / initialCommands := """import snackomaten.*""",
    scalacOptions := Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-Werror", "-feature"),
  )

lazy val client = (project in file("client"))
  .settings(
    name := "snackomatenClient",
    scalacOptions := Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-Werror", "-feature"),

    assembly / assemblyJarName := s"snackomatenClient-assembly-$snackomatenVersion.jar",
    assembly / mainClass := Some("snackomaten.ClientMain"),
  )
  .dependsOn(common)

lazy val server = (project in file("server"))
  .settings(
    name := "snackomatenServer",
    scalacOptions := Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-Werror", "-feature"),

    console / initialCommands := """import snackomaten.*""",

    assembly / assemblyJarName := s"snackomatenServer-assembly-$snackomatenVersion.jar",
    assembly / mainClass := Some("snackomaten.ServerMain"),
  )
  .dependsOn(common)

