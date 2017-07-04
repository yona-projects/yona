/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
package utils;

import controllers.UserApp;
import models.User;
import org.apache.commons.codec.binary.Base64;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.Result;

import java.io.UnsupportedEncodingException;

import static utils.LdapService.USE_EMAIL_BASE_LOGIN;

public class BasicAuthAction extends Action<Object> {
    private static final String REALM = "Yobi";

    public static Result unauthorized(Response response) {
        // challenge   = "Basic" realm
        // realm       = "realm" "=" realm-value
        // realm-value = quoted-string

        String challenge = "Basic realm=\"" + REALM + "\"";

        response.setHeader(Http.HeaderNames.WWW_AUTHENTICATE, challenge);
        return unauthorized("Invalid username or password");
    }

    public static User parseCredentials(String credentials) throws MalformedCredentialsException, UnsupportedEncodingException {
        // credentials = "Basic" basic-credentials
        // basic-credentials = base64-user-pass
        // base64-user-pass  = <base64 [4] encoding of user-pass,
        // user-pass   = userid ":" password
        // userid      = *<TEXT excluding ":">
        // password    = *TEXT

        if (credentials == null) {
            return null;
        }

        byte[] userpassBytes = Base64.decodeBase64(credentials.substring(6));

        // Use ISO-8859-1 only and not others, even if in RFC 2616, Section 2.2 "Basic Rules" allows
        // TEXT to be encoded according to the rules of RFC 2047.
        //
        // Why: Julian Reschke, an editor for Authentication Scheme Registration of HTTPbis, said
        // "RFC 2047 encoding doesn't apply in this case." and "the HTTPbis specs will not mention
        // RFC 2047 anymore." See
        // http://stackoverflow.com/questions/7242316/what-encoding-should-i-use-for-http-basic-authentication#comment11372987_9056877
        String userpass = new String(userpassBytes, "ISO-8859-1");

        int colonAt = userpass.indexOf(':');

        if (colonAt < 0) {
            throw new MalformedCredentialsException();
        }

        User authUser = new User();
        authUser.loginId = userpass.substring(0, colonAt);
        authUser.password = userpass.substring(colonAt + 1);

        return authUser;
    }

    // !! Important !! For ldap, intentionally, user email is used for ldap authentication
    // instead of authUser.loginId.
    public User authenticate(Request request) throws UnsupportedEncodingException, MalformedCredentialsException {
        String credential = request.getHeader(Http.HeaderNames.AUTHORIZATION);
        User authUser = parseCredentials(credential);

        if (authUser == null) {
            return User.anonymous;
        } else {
            String credentialKey = authUser.loginId;
            if (LdapService.useLdap) {
                if (USE_EMAIL_BASE_LOGIN) {
                    // Notice: Email is used for LDAP authentication
                    User targetUser = User.findByLoginId(authUser.loginId);
                    if (!targetUser.isAnonymous()) {
                        credentialKey = targetUser.email;
                    }
                }
                return UserApp.authenticateWithLdap(credentialKey, authUser.password);
            }

            return UserApp.authenticateWithPlainPassword(credentialKey, authUser.password);
        }
    }

    @Override
    public Promise<Result> call(Context context) throws Throwable {
        User user;
        try {
            user = authenticate(context.request());
        } catch (MalformedCredentialsException error) {
            Promise<Result> promise = Promise.pure((Result) badRequest());
            AccessLogger.log(context.request(), promise, null);
            return promise;
        } catch (UnsupportedEncodingException e) {
            Promise<Result> promise = Promise.pure((Result) internalServerError());
            AccessLogger.log(context.request(), promise, null);
            return promise;
        }

        if (!user.isAnonymous()) {
            UserApp.addUserInfoToSession(user);
        }

        return delegate.call(context);
    }
}
