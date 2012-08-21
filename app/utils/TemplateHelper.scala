package utils

import play.mvc.Call
import org.joda.time.DateTimeConstants
import play.i18n.Messages

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
      case x if x >= 86400 => duration.getStandardDays + " " + Messages.get("time.day")
      case x if x >= 3600 => duration.getStandardHours + " " + Messages.get("time.hour")
      case x if x >= 60 => duration.getStandardMinutes + " " + Messages.get("time.minute")
      case x if x > 0 => duration.getStandardSeconds + " " + Messages.get("time.second")
      case _ => Messages.get("time.just")
    }
  }
}


