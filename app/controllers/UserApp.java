package controllers;

import models.User;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import org.apache.shiro.util.SimpleByteSource;

import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Result;
import utils.Constants;
import views.html.login;
import views.html.signup;

public class UserApp extends Controller {
	public static final String SESSION_USERID = "userId";
	public static final String SESSION_USERNAME = "userName";
	public static final String SUBJECT = "nforge.subject";

	public static User anonymous = new User();

	public static Result login() {
		// Remember Me
		Cookie cookie = request().cookies().get(SUBJECT);
		if (cookie != null) {
			String value = cookie.value();
			Logger.debug(value);
			String[] subject = value.split(":");

			User user = User.findByLoginId(subject[0]);
			setUserInfoInSession(user);
			return redirect(routes.Application.index());
		}

		return ok(login.render("title.login", form(User.class)));
	}

	public static Result logout() {
		session().clear();
		response().discardCookies(SUBJECT);

		flash(Constants.SUCCESS, "user.logout.success");
		return redirect(routes.Application.index());
	}

	public static Result authenticate() {
		User sourceUser = form(User.class).bindFromRequest().get();
		User targetUser = User.findByLoginId(sourceUser.loginId);
		
		UsernamePasswordToken token = new UsernamePasswordToken(sourceUser.loginId,
				sourceUser.password);
		token.setRememberMe(sourceUser.rememberMe);
		// Subject currentUser = SecurityUtils.getSubject();
		
		if(authenticate(sourceUser, targetUser)) {
			
			setUserInfoInSession(targetUser);
			if (sourceUser.rememberMe) {
				rememberMe(targetUser);
			}
			return redirect(routes.Application.index());
		}

		flash(Constants.WARNING, "user.login.failed");
		return redirect(routes.UserApp.login());
	}
	
	public static boolean authenticate(User sourceUser, User targetUser) {
		if (targetUser != null) {
			if (targetUser.password.equals(hashedPassword(sourceUser.password,
					targetUser.passwordSalt))) {
				return true;
			}
		}
		return false;
	}

	private static String hashedPassword(String plaintextPassword,
			String passwordSalt) {
		return new Sha256Hash(plaintextPassword,
				ByteSource.Util.bytes(passwordSalt), 1024).toBase64();
	}

	private static void rememberMe(User user) {
		response().setCookie(SUBJECT, user.loginId + ":" + "123456789");
		Logger.debug("remember me enabled");
	}

	public static Result signup() {
		return ok(signup.render("title.signup", form(User.class)));
	}

	public static Result saveUser() {
		Form<User> newUserForm = form(User.class).bindFromRequest();
		validate(newUserForm);
		if (newUserForm.hasErrors())
			return badRequest(signup.render("title.signup", newUserForm));
		else {
			User user = newUserForm.get();
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
		session(SESSION_USERNAME, user.name);
	}

	// FIXME
	public static User currentUser() {
		String userId = session().get(SESSION_USERID);
		if (userId == null) {
			return anonymous;
		}
		return User.findById(Long.valueOf(userId));
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
}
