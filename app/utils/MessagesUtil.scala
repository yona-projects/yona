/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package utils

import play.api.i18n.Lang
import play.i18n.MessagesApi
import play.Play

import scala.annotation.varargs
import scala.util.control.NonFatal

object MessagesUtil {

  private def messagesApi: Option[MessagesApi] =
    try {
      Some(Play.application().injector().instanceOf(classOf[MessagesApi]))
    } catch {
      case NonFatal(_) => None
    }

  private def lang: Lang = Lang(utils.RequestUtil.languageCode(utils.LegacyRequestContext.currentRequestOrNull()))

  @varargs def get(key: String, args: Object*): String =
    messagesApi.map(_.get(lang, key, args: _*)).getOrElse(defaultMessage(key, args))

  @varargs def get(lang: Lang, key: String, args: Object*): String =
    messagesApi.map(_.get(lang, key, args: _*)).getOrElse(defaultMessage(key, args))

  private def defaultMessage(key: String, args: Seq[Object]): String =
    if (args.isEmpty) key else key + args.mkString("(", ", ", ")")
}
