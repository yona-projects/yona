package com.feth.play.module.pa.views

import com.feth.play.module.pa.PlayAuthenticate
import com.feth.play.module.pa.user.AuthUser
import play.twirl.api.Html

package object html {
  case class Provider(key: String, url: String) {
    def getKey: String = key
    def getUrl: String = url
  }

  def currentAuth(playAuthenticate: PlayAuthenticate)(block: AuthUser => Html): Html = {
    block(playAuthenticate.getUser(utils.LegacyRequestContext.session()))
  }

  def forProviders(playAuthenticate: PlayAuthenticate, skipCurrent: Boolean, always: Boolean, withUrls: Boolean)(block: Provider => Html): Html = {
    val configured = Option(play.Configuration.root.getString("application.social.login.support", ""))
      .getOrElse("")
      .split(",")
      .map(_.trim)
      .filter(_.nonEmpty)

    Html(configured.map { provider =>
      block(Provider(provider, controllers.routes.Application.oAuth(provider).url)).body
    }.mkString)
  }
}
