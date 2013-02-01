package controllers;

import models.*;
import models.enumeration.ResourceType;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.apache.shiro.util.Factory;

import play.Logger;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.Cookie;
import utils.Constants;
import views.html.login;
import views.html.user.*;

import org.codehaus.jackson.node.ObjectNode;
import play.libs.Json;

import static play.data.Form.form;

public class UserApp extends Controller {

	public static final String SESSION_USERID = "userId";
	public static final String SESSION_LOGINID = "loginId";
	public static final String SESSION_USERNAME = "userName";
	public static final String TOKEN = "nforge.token";
	public static final int MAX_AGE = 30*24*60*60;
	public static final String DEFAULT_AVATAR_URL = "/assets/images/default-avatar-128.png";

	public static User anonymous = new User();


	public static Result loginForm() {
		return ok(login.render("title.login", form(User.class)));
	}

	public static Result logout() {
		session().clear();
		response().discardCookie(TOKEN);

		flash(Constants.SUCCESS, "user.logout.success");
		return redirect(routes.Application.index());
	}

	public static Result login() {
		Form<User> userForm = form(User.class).bindFromRequest();
		if(userForm.hasErrors()) {
			return badRequest(login.render("title.login", userForm));
		}
		User sourceUser = form(User.class).bindFromRequest().get();

		Factory<SecurityManager> factory = new IniSecurityManagerFactory("classpath:shiro.ini");
		SecurityManager securityManager = factory.getInstance();
		SecurityUtils.setSecurityManager(securityManager);

        Subject currentUser = SecurityUtils.getSubject();
        if(!currentUser.isAuthenticated()) {
        	UsernamePasswordToken token = new UsernamePasswordToken(sourceUser.loginId,
    				sourceUser.password);
        	token.setRememberMe(sourceUser.rememberMe);

        	//Object principal = token.getPrincipal();

        	try {
                currentUser.login(token);
            } catch (UnknownAccountException uae) {
            	Logger.info("There is no user with username of " + token.getPrincipal());
            } catch (IncorrectCredentialsException ice) {
            	Logger.info("Password for account " + token.getPrincipal() + " was incorrect!");
            } catch (LockedAccountException lae) {
            	Logger.info("The account for username " + token.getPrincipal() + " is locked.  " +
                        "Please contact your administrator to unlock it.");
            }
            // ... catch more exceptions here (maybe custom ones specific to your application?
            catch (AuthenticationException ae) {
                //unexpected condition?  error?
            }
        }

		User authenticate = authenticateWithPlainPassword(sourceUser.loginId, sourceUser.password);

		if(authenticate!=null) {
			setUserInfoInSession(authenticate);
			if (sourceUser.rememberMe) {
				setupRememberMe(authenticate);
			}
			return redirect(routes.Application.index());
		}

		flash(Constants.WARNING, "user.login.failed");
		return redirect(routes.UserApp.loginForm());
	}

	public static User authenticateWithHashedPassword(String loginId, String password) {
		User user = User.findByLoginId(loginId);
		if (user != null) {
			if (user.password.equals(password)) {
				return user;
			}
		}
		return null;
	}

	public static User authenticateWithPlainPassword(String loginId, String password) {
		User user = User.findByLoginId(loginId);
		if (user != null) {
			if (user.password.equals(hashedPassword(password,
					user.passwordSalt))) {
				return user;
			}
		}
		return null;
	}

	private static String hashedPassword(String plaintextPassword,
			String passwordSalt) {
		return new Sha256Hash(plaintextPassword,
				ByteSource.Util.bytes(passwordSalt), 1024).toBase64();
	}

	public static boolean isRememberMe() {
		// Remember Me
		Cookie cookie = request().cookies().get(TOKEN);

		if (cookie != null) {
			String[] subject = cookie.value().split(":");
			User user = authenticateWithHashedPassword(subject[0], subject[1]);
			if(user!=null) {
				setUserInfoInSession(user);
			}
			return true;
		}
		return false;
	}

	private static void setupRememberMe(User user) {
		response().setCookie(TOKEN, user.loginId + ":" + user.password, MAX_AGE);
		Logger.debug("remember me enabled");
	}

	public static Result signupForm() {
		return ok(signup.render("title.signup", form(User.class)));
	}

	public static Result newUser() {
		Form<User> newUserForm = form(User.class).bindFromRequest();
		validate(newUserForm);
		if (newUserForm.hasErrors())
			return badRequest(signup.render("title.signup", newUserForm));
		else {
			User user = newUserForm.get();
			user.avatarUrl = DEFAULT_AVATAR_URL;
			User.create(hashedPassword(user));

			setUserInfoInSession(user);
			return redirect(routes.Application.index());
		}
	}

	public static User hashedPassword(User user) {
		RandomNumberGenerator rng = new SecureRandomNumberGenerator();
		user.passwordSalt = rng.nextBytes().getBytes().toString();
		user.password = new Sha256Hash(user.password,
				ByteSource.Util.bytes(user.passwordSalt), 1024).toBase64();

		return user;
	}

	public static void setUserInfoInSession(User user) {
		session(SESSION_USERID, String.valueOf(user.id));
		session(SESSION_LOGINID, user.loginId);
		session(SESSION_USERNAME, user.name);
	}

	// FIXME
	public static User currentUser() {
		String userId = session().get(SESSION_USERID);
		if (StringUtils.isEmpty(userId) || !StringUtils.isNumeric(userId)) {
			anonymous.id = -1l;
			return anonymous;
		}
		Logger.debug("userId="+userId);
		return User.find.byId(Long.valueOf(userId));
	}

	public static void validate(Form<User> newUserForm) {
		// loginId가 빈 값이 들어오면 안된다.
		if (newUserForm.field("loginId").value() == "") {
			newUserForm.reject("loginId", "user.wrongloginId.alert");
		}

		// password가 빈 값이 들어오면 안된다.
		if (newUserForm.field("password").value() == "") {
			newUserForm.reject("password", "user.wrongPassword.alert");
		}

		// 중복된 loginId로 가입할 수 없다.
		if (User.isLoginId(newUserForm.field("loginId").value())) {
			newUserForm.reject("loginId", "user.loginId.duplicate");
		}
	}

	public static Result memberEdit(Long userId) {
		User user = User.find.byId(userId);
		Form<User> userForm = new Form<User>(User.class);
        userForm = userForm.fill(user);
        return ok(edit.render(userForm, user));
	}

	public static Result userInfo(String loginId){
	    User user = User.findByLoginId(loginId);
	    return ok(info.render(user));
	}

    public static Result editUserInfoForm() {
        User user = UserApp.currentUser();
        Form<User> userForm = new Form<User>(User.class);
        userForm = userForm.fill(user);

        return ok(edit.render(userForm, user));
    }

    public static Result editUserInfo() {
    	//FIXME email검증이 필요함.
        Form<User> userForm = new Form<User>(User.class).bindFromRequest();
        User user = UserApp.currentUser();
        user.email = userForm.data().get("email");
        user.name = userForm.data().get("name");
        
        Attachment.deleteAll(ResourceType.USER_AVATAR, currentUser().id);
        int attachFiles = Attachment.attachFiles(currentUser().id, null, ResourceType.USER_AVATAR, currentUser().id);
        if(attachFiles>0) {
        	user.avatarUrl = "/files/" + user.avatarId();
        }
    	
        user.update();
        return redirect(routes.UserApp.userInfo(user.loginId));
    }

    public static Result leave(String userName, String projectName) {
        ProjectApp.deleteMember(userName, projectName, UserApp.currentUser().id);
        return redirect(routes.UserApp.userInfo(UserApp.currentUser().loginId));
    }


    public static Result isUserExist(String loginId) {
        User user = User.findByLoginId(loginId);
        ObjectNode result = Json.newObject();
        if( user == null){
            result.put("isExist", false);
        } else {
            result.put("isExist", true);
        }
        return ok(result);
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result isEmailExist(String email) {
        ObjectNode result = Json.newObject();
        result.put("isExist", User.isEmailExist(email));
        return ok(result);
    }
}
