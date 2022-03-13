ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "OAuth2_sample"
  )


libraryDependencies += "org.typelevel" %% "cats-core" % "2.7.0"

libraryDependencies ++= {
  val akkaVersion = "2.6.18"
  val akkaHttpVersion = "10.2.6"
  Seq(
    "com.typesafe.akka"       %%  "akka-actor-typed"               % akkaVersion,
    "com.typesafe.akka"       %%  "akka-stream"                    % akkaVersion,
    "com.typesafe.akka"       %%  "akka-http-spray-json"           % akkaHttpVersion,
    "com.typesafe.akka"       %%  "akka-http"                      % akkaHttpVersion,
    "com.typesafe.akka"       %%  "akka-slf4j"                     % akkaVersion,
    "ch.qos.logback"           %  "logback-classic"                % "1.2.10",
    "com.emarsys"             %%  "jwt-akka-http"                  % "1.4.4",
    "com.typesafe.akka"       %%  "akka-testkit"                   % akkaVersion   % "test",
    "org.scalatest"           %%  "scalatest"                      % "3.2.9"       % "test"
  )
}

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "org.slf4j" % "slf4j-nop" % "1.7.33",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "io.github.nafg.slick-migration-api" %% "slick-migration-api" % "0.8.2",
  "org.postgresql" % "postgresql" % "42.3.1"
)

libraryDependencies += "org.springframework.security" % "spring-security-web" % "5.6.2"
//libraryDependencies += "org.scalatra.scalate" %% "scalate-camel" % "1.9.6"
libraryDependencies += "org.scalatra.scalate" %% "scalate-core" % "1.9.6"
