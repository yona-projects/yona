package controllers;

import com.avaje.ebean.ExpressionList;
import models.*;
import models.enumeration.ResourceType;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;

import play.Configuration;
import play.Logger;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.Cookie;
import utils.Constants;
import views.html.login;
import views.html.user.*;

import org.codehaus.jackson.node.ObjectNode;
import play.libs.Json;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Transient;

import static play.data.Form.form;
import static play.libs.Json.toJson;

public class UserApp extends Controller {
	public static final String SESSION_USERID = "userId";
	public static final String SESSION_LOGINID = "loginId";
	public static final String SESSION_USERNAME = "userName";
	public static final String TOKEN = "nforge.token";
	public static final int MAX_AGE = 30*24*60*60;
	public static final String DEFAULT_AVATAR_URL = "/assets/images/default-avatar-128.png";
    public static final int MAX_FETCH_USERS = 1000;

    //ToDO anonymous를 사용하는 것이아니라 향후 NullUser 패턴으로 usages들을 교체해야 함
	public static User anonymous = new NullUser();

    public static Result users(String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        ExpressionList<User> el = User.find.select("loginId").where().contains("loginId", query);
        int total = el.findRowCount();
        if (total > MAX_FETCH_USERS) {
            el.setMaxRows(MAX_FETCH_USERS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_USERS + "/" + total);
        }

        List<String> loginIds = new ArrayList<String>();
        for (User user: el.findList()) {
            loginIds.add(user.loginId);
        }

        return ok(toJson(loginIds));
    }

	public static Result loginForm() {
		return ok(login.render("title.login", form(User.class)));
	}

	public static Result logout() {
		session().clear();
		response().discardCookie(TOKEN);

		flash(Constants.SUCCESS, "user.logout.success");
		return redirect(routes.Application.index());
	}

    private static boolean isUseSignUpConfirm(){
        Configuration config = play.Play.application().configuration();
        String useSignUpConfirm = config.getString("signup.require.confirm");
        if (useSignUpConfirm != null && useSignUpConfirm.equals("true")) {
            return true;
        } else {
            return false;
        }
    }
	public static Result login() {
		Form<User> userForm = form(User.class).bindFromRequest();
		if(userForm.hasErrors()) {
            return badRequest(login.render("title.login", userForm));
        }
        User sourceUser = form(User.class).bindFromRequest().get();

        if (isUseSignUpConfirm()) {
            if( User.findByLoginId(sourceUser.loginId).isLocked == true ){
                flash(Constants.WARNING, "user.locked");
                return redirect(routes.UserApp.loginForm());
            }
        }
        User authenticate = authenticateWithPlainPassword(sourceUser.loginId, sourceUser.password);

		if(authenticate != null) {
			addUserInfoToSession(authenticate);
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
		if (!user.isAnonymous()) {
			if (user.password.equals(password)) {
				return user;
			}
		}
		return null;
	}

	public static User authenticateWithPlainPassword(String loginId, String password) {
		User user = User.findByLoginId(loginId);
		if (!user.isAnonymous()) {
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
            Logger.debug(cookie.value());
            if(subject.length < 2) return false;
			User user = authenticateWithHashedPassword(subject[0], subject[1]);
			if(user!=null) {
				addUserInfoToSession(user);
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
            lockAccountIfSignUpConfirmModeIsUsed(user);
            User.create(hashedPassword(user));
            if(user.isLocked){
                flash(Constants.INFO, "user.signup.requested");
            } else {
                addUserInfoToSession(user);
            }
			return redirect(routes.Application.index());
		}
	}

    private static void lockAccountIfSignUpConfirmModeIsUsed(User user) {
        Configuration config = play.Play.application().configuration();
        String useSignUpConfirm = config.getString("signup.require.confirm");
        if (useSignUpConfirm != null && useSignUpConfirm.equals("true")) {
            user.isLocked = true;
        }
    }

    //Fixme user.password가 plain text 였다가 다시 덮여쓰여지는 식으로 동작한다. 혹시라도 패스워드 reset을 위해 이 메소드를 잘못 사용했다가는 자칫 로그인을 할 수 없게 되는 상황이 발생할 수 있다.
	public static User hashedPassword(User user) {
		RandomNumberGenerator rng = new SecureRandomNumberGenerator();
		user.passwordSalt = rng.nextBytes().getBytes().toString();
		user.password = new Sha256Hash(user.password,
				ByteSource.Util.bytes(user.passwordSalt), 1024).toBase64();

		return user;
	}

	public static Result resetUserPassword() {
		Form<User> userForm = form(User.class).bindFromRequest();

		if(userForm.hasErrors()) {
			return badRequest();
		}

		User currentUser = currentUser();
		User user = userForm.get();
		
		if(!isValidPassword(currentUser, user.oldPassword)) {
			Form<User> currentUserForm = new Form<User>(User.class);
			currentUserForm = currentUserForm.fill(currentUser);
	    
			flash(Constants.WARNING, "user.wrongPassword.alert");
			return badRequest(edit.render(currentUserForm, currentUser));
		}
		
		resetPassword(currentUser, user.password);
		
		//go to login page
		session().clear();
		response().discardCookie(TOKEN);
		
		flash(Constants.WARNING, "user.loginWithNewPassword");
        return redirect(routes.UserApp.loginForm());

	}
	
	public static boolean isValidPassword(User currentUser, String password) {
		String hashedOldPassword = hashedPassword(password, currentUser.passwordSalt);
		return currentUser.password.equals(hashedOldPassword); 
	}
	
    public static void resetPassword(User user, String newPassword) {
		user.password = new Sha256Hash(newPassword,
				ByteSource.Util.bytes(user.passwordSalt), 1024).toBase64();
        user.save();
	}

	public static void addUserInfoToSession(User user) {
		session(SESSION_USERID, String.valueOf(user.id));
		session(SESSION_LOGINID, user.loginId);
		session(SESSION_USERNAME, user.name);
	}

	public static User currentUser() {
		String userId = session().get(SESSION_USERID);
		if (StringUtils.isEmpty(userId) || !StringUtils.isNumeric(userId)) {
			return anonymous;
		}
        User foundUser = User.find.byId(Long.valueOf(userId));
        if (foundUser == null) {
            session().clear();
            response().discardCookie(TOKEN);
            return anonymous;
        }
		return foundUser;
	}

	public static void validate(Form<User> newUserForm) {
		// loginId가 빈 값이 들어오면 안된다.
		if (newUserForm.field("loginId").value() == "") {
			newUserForm.reject("loginId", "user.wrongloginId.alert");
		}

        if (newUserForm.field("loginId").value().contains(" ")) {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }

		// password가 빈 값이 들어오면 안된다.
		if (newUserForm.field("password").value() == "") {
			newUserForm.reject("password", "user.wrongPassword.alert");
		}

		// 중복된 loginId로 가입할 수 없다.
		if (User.isLoginIdExist(newUserForm.field("loginId").value())) {
			newUserForm.reject("loginId", "user.loginId.duplicate");
		}
		
		if (User.isEmailExist(newUserForm.field("email").value())) {
		    newUserForm.reject("email", "validation.duplicated");
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
        ObjectNode result = Json.newObject();
        User user = User.findByLoginId(loginId);
        if(user.isAnonymous()){
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
