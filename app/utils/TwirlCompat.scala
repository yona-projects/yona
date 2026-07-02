package utils

import play.api.i18n.{Lang, MessagesImpl, MessagesProvider}
import play.data.Form
import play.data.validation.ValidationError
import play.mvc.Http
import play.twirl.api.{Html, HtmlFormat}

import scala.jdk.OptionConverters._

object TwirlCompat {
  implicit def implicitRequestHeader: play.api.mvc.RequestHeader =
    LegacyRequestContext.request().asScala()

  implicit def messagesProvider: MessagesProvider = new MessagesProvider {
    override def messages: play.api.i18n.Messages = {
      val messagesApi = play.Play.application().injector().instanceOf(classOf[play.api.i18n.MessagesApi])
      MessagesImpl(Lang(RequestUtil.languageCode(LegacyRequestContext.currentRequestOrNull())), messagesApi)
    }
  }

  def requestHeader: LegacyRequestHeader =
    LegacyRequestHeader(LegacyRequestContext.request())

  def implicitJavaLang: LegacyLang =
    LegacyLang(RequestUtil.languageCode(LegacyRequestContext.currentRequestOrNull()))

  def formErrors(form: Form[_], field: String): java.util.List[ValidationError] =
    form.errors(field)

  def formError(form: Form[_], field: String): Option[ValidationError] =
    form.error(field).toScala

  def fieldValue(form: Form[_], field: String): String =
    form.field(field).value().orElse("")

  def fieldValueOption(form: Form[_], field: String): Option[String] =
    form.field(field).value().toScala

  def csrfFormField: Html = {
    play.filters.csrf.CSRF.getToken(LegacyRequestContext.request()).toScala
      .map { token =>
        Html(
          s"""<input type="hidden" name="${HtmlFormat.escape(token.name).body}" value="${HtmlFormat.escape(token.value).body}">"""
        )
      }
      .getOrElse(Html(""))
  }

  def csrfMetaTags: Html = {
    play.filters.csrf.CSRF.getToken(LegacyRequestContext.request()).toScala
      .map { token =>
        Html(
          s"""<meta name="csrf-param" content="${HtmlFormat.escape(token.name).body}">
             |<meta name="csrf-token" content="${HtmlFormat.escape(token.value).body}">""".stripMargin
        )
      }
      .getOrElse(Html(""))
  }

  case class LegacyLang(code: String) {
    def language: String = code
  }

  case class LegacyRequestHeader(request: Http.Request) {
    def getQueryString(key: String): Option[String] = Option(request.getQueryString(key))
    def headers: LegacyHeaders = LegacyHeaders(request)
    def session: LegacySession = LegacySession()
    def flash: LegacyFlash = LegacyFlash()
    def uri: String = request.uri()
    def path: String = request.path()
  }

  case class LegacyHeaders(request: Http.Request) {
    def get(name: String): Option[String] = {
      val value = request.getHeaders.get(name)
      if (value.isPresent) Some(value.get()) else None
    }
  }

  case class LegacySession() {
    def get(key: String): Option[String] = Option(LegacyRequestContext.session().get(key))
  }

  case class LegacyFlash() {
    def get(key: String): Option[String] = Option(LegacyRequestContext.flash().get(key))
  }
}
