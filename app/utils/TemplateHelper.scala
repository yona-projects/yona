package utils

import play.mvc.Call

object TemplateHelper {

  def buildQueryString(call: Call, queryMap: Map[String, String]): String = {
      val baseUrl = call.toString
      var prefix = "?"
      var query = ""
      if((baseUrl indexOf "?") != -1) {
          prefix = "&"
      }
      queryMap.map{ v=> query += v._1+ "=" + v._2 + "&"}
      baseUrl + prefix + query.dropRight(1)
  }
}


