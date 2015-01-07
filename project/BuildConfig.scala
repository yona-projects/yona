import sbt._
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.io.IOException;

object ApplicationBuild extends Build {

  val APPLICATION_CONF_DEFAULT = "application.conf.default"
  val APPLICATION_CONF = "application.conf"
  val LOG_CONF_DEFAULT = "application-logger.xml.default"
  val LOG_CONF = "application-logger.xml"
  val CONFIG_DIRNAME = "conf"

  def basePath = new File(System.getProperty("user.dir")).getAbsolutePath()

  def configDirPath = basePath + "/" + CONFIG_DIRNAME

  def initConfig(pathToDefaultConfig: Path, pathToConfig: Path): Unit = {
    val configFile = pathToConfig.toFile()

    if (!configFile.exists()) {
        try {
          Files.copy(pathToDefaultConfig, pathToConfig)
        } catch {
            case e: IOException => throw new Exception("Failed to initialize configuration", e)
        }
    } else {
        if (!configFile.isFile()) {
            throw new Exception("Failed to initialize configuration: '" + pathToConfig + "' is a directory.")
        }
    }
  }

  def initConfig: Unit = {
    initConfig(
      Paths.get(configDirPath, APPLICATION_CONF_DEFAULT),
      Paths.get(configDirPath, APPLICATION_CONF))
    initConfig(
      Paths.get(configDirPath, LOG_CONF_DEFAULT),
      Paths.get(configDirPath, LOG_CONF))
  }

  val main = initConfig
}
