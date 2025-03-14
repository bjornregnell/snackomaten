ThisBuild / organization := "se.bjornregnell"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.4"

lazy val root = (project in file("."))
  .aggregate(common, client, server)
  .settings(
    //update / aggregate := false
  )

lazy val common = (project in file("common"))
  .settings(
    // other settings
  )

lazy val client = (project in file("client"))
  .settings(
    // other settings
  )
  .dependsOn(common)

lazy val server = (project in file("server"))
  .settings(
    // other settings
  )
  .dependsOn(common)

