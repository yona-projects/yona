package utils

import play.mvc.Call
import org.joda.time.DateTimeConstants
import play.i18n.Messages
import controllers.routes
import java.security.MessageDigest
import views.html._

object TemplateHelper {

  def buildQueryString(call: Call, queryMap: Map[String, String]): String = {
    val baseUrl = call.toString
    var prefix = "?"
    var query = ""
    if ((baseUrl indexOf "?") != -1) {
      prefix = "&"
    }
    queryMap.map {
      v => query += v._1 + "=" + v._2 + "&"
    }
    baseUrl + prefix + query.dropRight(1)
  }

  def agoString(duration: org.joda.time.Duration) = {
    val sec = duration.getMillis / DateTimeConstants.MILLIS_PER_SECOND

    sec match {
      case x if x >= 86400 => plural("time.day", duration.getStandardDays)
      case x if x >= 3600 => plural("time.hour", duration.getStandardHours)
      case x if x >= 60 => plural("time.minute", duration.getStandardMinutes)
      case x if x > 0 => plural("time.second", duration.getStandardSeconds)
      case _ => Messages.get("time.just")
    }
  }

  def plural(key: String, count: Number): String = {
    var _key = key
    if (count != 1) _key = key + "s"
    Messages.get(_key, count toString)
  }

  def getJSLink(name: String): String = {
    loadAssetsLink("javascripts", name, "js")
  }

  def getCSSLink(name: String): String = {
   loadAssetsLink("stylesheets", name, "css")
  }

  def loadAssetsLink(base: String, name: String, _type: String): String = {
    var minified = ""
//    if (play.Play.isProd) minified = ".min"
    routes.Assets.at(base + "/" + name + minified + "." + _type).toString
  }

  def urlToPicture(email: String, size: Int = 34) = {
    "http://www.gravatar.com/avatar/" + MessageDigest.getInstance("MD5").digest(email.toLowerCase.getBytes).map("%02x".format(_)).mkString + "?s=" + size
  }

  def simpleForm(elements: helper.FieldElements) = {
    elements.input
  }
}
