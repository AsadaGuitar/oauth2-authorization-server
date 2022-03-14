ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "OAuth2_sample"
  )


// Akka
libraryDependencies ++= {
  val akkaVersion = "2.6.18"
  val akkaHttpVersion = "10.2.9"
  Seq(
    "com.typesafe.akka"                    %%  "akka-actor-typed"               % akkaVersion,
    "com.typesafe.akka"                    %%  "akka-stream"                    % akkaVersion,
    "com.typesafe.akka"                    %%  "akka-http-spray-json"           % akkaHttpVersion,
    "com.typesafe.akka"                    %%  "akka-http"                      % akkaHttpVersion,
    "com.typesafe.akka"                    %%  "akka-slf4j"                     % akkaVersion,
    "com.emarsys"                          %%  "jwt-akka-http"                  % "1.4.4",
    "com.softwaremill.akka-http-session"   %%  "core"                           % "0.7.0",
    "com.typesafe.akka"                    %%  "akka-testkit"                   % akkaVersion            % "test",
  )
}

// Slick
libraryDependencies ++= Seq(
  "com.typesafe.slick"                   %%  "slick"                 % "3.3.3",
  "com.typesafe.slick"                   %%  "slick-hikaricp"        % "3.3.3",
  "io.github.nafg.slick-migration-api"   %%  "slick-migration-api"   % "0.8.2",
)

// Scalate
libraryDependencies ++= Seq(
  "org.scalatra.scalate" %%  "scalate-core" % "1.9.6"
)

// Log
libraryDependencies ++= Seq(
  "org.slf4j"       %  "slf4j-nop"         % "1.7.33",
  "ch.qos.logback"  %  "logback-classic"   % "1.2.10"
)

// Utility
libraryDependencies ++= Seq(
  "org.postgresql"                %  "postgresql"           % "42.3.1",
  "org.springframework.security"  %  "spring-security-web"  % "5.6.2",
  "org.scalatest"                 %% "scalatest"            % "3.2.9"       % "test"
)



