/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import Keys._

object SilkBuild extends Build {

  val testLib = Seq(
    "junit" % "junit" % "4.10" % "test",
    "org.scalatest" %% "scalatest" % "1.6.1" % "test",
    "org.hamcrest" % "hamcrest-core" % "1.3.RC2"
  )

  val remoteLib = Seq(
    "io.netty" % "netty" % "3.3.0.Final",
    "com.typesafe.akka" % "akka-actor" % "2.0-M2",
    "com.typesafe.akka" % "akka-remote" % "2.0-M2"
  )

  lazy val root = Project(id = "silk", base = file(".")) aggregate(core, model, lens, parser, store, weaver) settings (
    commands ++= Seq(hello)
    )

  lazy val core = Project(id = "silk-core", base = file("silk-core")) settings (
    libraryDependencies ++= testLib ++
      Seq(
        "org.javassist" % "javassist" % "3.15.0-GA"
      )
    )

  lazy val model = Project(id = "silk-model", base = file("silk-model")) settings (
    libraryDependencies ++= testLib
  ) dependsOn(core % "test->test;compile->compile")

  lazy val lens = Project(id = "silk-lens", base = file("silk-lens")).dependsOn(core % "test->test;compile->compile")

  lazy val parser = Project(id = "silk-parser", base = file("silk-parser")).
    dependsOn(core % "test->test;compile->compile").
    settings(
    libraryDependencies ++= testLib
  )

  lazy val store = Project(id = "silk-store", base = file("silk-store")).
    dependsOn(core % "test->test;compile->compile").
    settings(
    libraryDependencies ++= testLib
  )

  lazy val weaver = Project(id = "silk-weaver", base = file("silk-weaver")) dependsOn (core % "test->test;compile->compile") settings(
    resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases",
    libraryDependencies ++= remoteLib
    )

  def hello = Command.command("hello") {
    state =>
      println("Hello silk!")
      state
  }


}