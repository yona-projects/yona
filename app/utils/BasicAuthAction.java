package utils;

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;

import controllers.UserApp;
import models.User;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Result;

public class BasicAuthAction extends Action<Object> {
    private static final String REALM = "nforge4";
    
    public static Result unauthorized(Context context) {
        // challenge   = "Basic" realm
        // realm       = "realm" "=" realm-value
        // realm-value = quoted-string

        String challenge = "Basic realm=\"" + REALM + "\"";
        context.response().setHeader(Http.HeaderNames.WWW_AUTHENTICATE, challenge);
        return unauthorized();
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

        String userpassBase64 = credentials.substring(6);
        byte[] userpassBytes;
        
        userpassBytes = Base64.decodeBase64(userpassBase64.getBytes());
        
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
    
    @Override
    public Result call(Context context) throws Throwable {

        String credential = context.request().getHeader(Http.HeaderNames.AUTHORIZATION);
        User authUser = null;
        
        try {
            authUser = parseCredentials(credential);
        } catch (MalformedCredentialsException error) {
            return badRequest();
        } catch (UnsupportedEncodingException e) {
            return internalServerError();
        }
        
        if (authUser != null) {
        	User targetUser = User.findByLoginId(authUser.loginId);
        	if(UserApp.authenticate(authUser, targetUser)){
        		context.session().put(UserApp.SESSION_USERID, String.valueOf(targetUser.id));
        		context.session().put(UserApp.SESSION_USERNAME, targetUser.name);
        		return delegate.call(context);
        	}
        } 
        return unauthorized(context);
    }
}