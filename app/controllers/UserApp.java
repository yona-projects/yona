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
import utils.ReservedWordsValidator;
import views.html.login;
import views.html.user.*;

import org.codehaus.jackson.node.ObjectNode;
import play.libs.Json;

import java.util.ArrayList;
import java.util.List;

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
    private static final int HASH_ITERATIONS = 1024;

    /**
     * ajax 를 이용한 사용자 검색
     * 요청 헤더의 accept 파라미터에 application/json 값이 없으면 406 응답
     * 응답에 포함되는 데이터 수는 MAX_FETCH_USERS 로 제한된다
     * 입력 파라미터 query 가 부분매칭 되는 loginId 목록을 json 형태로 응답
     * 
     * @param query 검색어
     * @return
     */
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

    /**
     * 로그인 폼으로 이동
     * 
     * @return
     */
    public static Result loginForm() {
        return ok(login.render("title.login", form(User.class)));
    }

    /**
     * 로그아웃
     * 로그인 유지 기능 해제
     * 메인으로 이동
     * 
     * @return
     */
    public static Result logout() {
        processLogout();
        flash(Constants.SUCCESS, "user.logout.success");
        return redirect(routes.Application.index());
    }    
    
    /**
     * 로그인 처리
     * 시스템 설정에서 가입승인 기능이 활성화 되어 있고 사용자 상태가 잠금상태(미승인?)라면 계정이 잠겼다는 메시지를 노출하고 로그인 폼으로 돌아감
     * 시스템 설정에서 가입승인 기능이 활성화 되어 있지 않다면, 사용자 상태가 잠금상태라도 로그인이 가능하다 (스펙확인 필요)
     * 요청의 정보로 사용자 인증에 성공하면 로그인쿠키를 생성하고 로그인유지하기가 선택되었다면, 로그인유지를 위한 쿠키를 별도로 생성한다
     * 인증에 실패하면 관련된 메시지를 노출하고 로그인 폼으로 돌아간다
     * 
     * @return
     */
    public static Result login() {
        Form<User> userForm = form(User.class).bindFromRequest();
        if(userForm.hasErrors()) {
            return badRequest(login.render("title.login", userForm));
        }
        User sourceUser = form(User.class).bindFromRequest().get();

        if (isUseSignUpConfirm()) {
            if (User.findByLoginId(sourceUser.loginId).isLocked == true ) {
                flash(Constants.WARNING, "user.locked");
                return redirect(routes.UserApp.loginForm());
            }
        }
        User authenticate = authenticateWithPlainPassword(sourceUser.loginId, sourceUser.password);

        if (authenticate != null) {
            addUserInfoToSession(authenticate);
            if (sourceUser.rememberMe) {
                setupRememberMe(authenticate);
            }
            return redirect(routes.UserApp.userInfo(authenticate.loginId));
        }

        flash(Constants.WARNING, "user.login.failed");
        return redirect(routes.UserApp.loginForm());
    }

    /**
     * loginId 와 hash 값을 이용해서 사용자 인증.
     * 인증에 성공하면 DB 에서 조회된 사용자 정보를 리턴
     * 인증에 실패하면 null 리턴
     * 
     * @param loginId 로그인ID
     * @param password hash된 비밀번호
     * @return
     */
    public static User authenticateWithHashedPassword(String loginId, String password) {
        User user = User.findByLoginId(loginId);
        return authenticate(user, password);
    }

    /**
     * loginId 와 plain password 를 이용해서 사용자 인증
     * 인증에 성공하면 DB 에서 조회된 사용자 정보를 리턴
     * 인증에 실패하면 null 리턴
     * 
     * @param loginId 로그인ID
     * @param password 입력받은 비밀번호
     * @return
     */
    public static User authenticateWithPlainPassword(String loginId, String password) {
        User user = User.findByLoginId(loginId);
        return authenticate(user, hashedPassword(password, user.passwordSalt));
    }

    /**
     * 로그인 유지기능 사용 여부
     * 로그인 유지 기능이 사용중이면 로그인쿠키를 생성하고 true 를 리턴
     * 사용중이지 않으면 false 리턴
     * 
     * @return 로그인 유지기능 사용 여부
     */
    public static boolean isRememberMe() {
        // Remember Me
        Cookie cookie = request().cookies().get(TOKEN);
        if (cookie != null) {
            String[] subject = cookie.value().split(":");
            Logger.debug(cookie.value());
            if(subject.length < 2) return false;
            User user = authenticateWithHashedPassword(subject[0], subject[1]);
            if (user != null) {
                addUserInfoToSession(user);
            }
            return true;
        }
        return false;
    }    

    /**
     * 사용자 가입 화면 이동
     * 
     * @return
     */
    public static Result signupForm() {
        return ok(signup.render("title.signup", form(User.class)));
    }

    /**
     * 사용자 가입 처리
     * 입력된 데이터 유효성 검증에 실패하면 bad request 응답
     * 사용자 정보를 저장, 로그인 쿠기 생성 후 메인 페이지로 이동
     * 시스템 설정에서 가입승인 기능이 활성화되어 있다면 사용자의 계정 상태를 잠금으로 설정하여 저장, 로그인 쿠키 생성 안됨
     * 
     * @return
     */
    public static Result newUser() {
        Form<User> newUserForm = form(User.class).bindFromRequest();
        validate(newUserForm);
        if (newUserForm.hasErrors()) {
            return badRequest(signup.render("title.signup", newUserForm));
        } else {
            User user = createNewUser(newUserForm.get());
            User.create(user);
            if (user.isLocked) {
                flash(Constants.INFO, "user.signup.requested");
            } else {
                addUserInfoToSession(user);
            }
            return redirect(routes.Application.index());
        }
    }

    /**
     * 사용자 비밀번호 변경
     * 비밀번호 변경에 성공하면 로그인 화면으로 이동
     * 비밀번호 변경에 실패하면 수정화면으로 돌아간다
     * 
     * @return
     */
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
        processLogout();
        flash(Constants.WARNING, "user.loginWithNewPassword");
        return redirect(routes.UserApp.loginForm());

    }
    
    /**
     * 비밀번호 검증
     * 사용자 객체의 hash 된 비밀번호 값과 입력 받은 비밀번호의 hash 값이 같은지 검사한다
     * 
     * @param currentUser 사용자 객체
     * @param password 입력받은 비밀번호
     * @return
     */
    public static boolean isValidPassword(User currentUser, String password) {
        String hashedOldPassword = hashedPassword(password, currentUser.passwordSalt);
        return currentUser.password.equals(hashedOldPassword); 
    }   

    /**
     * 신규 비밀번호의 hash 값을 구하여 설정 후 저장한다
     * 
     * @param user 사용자객체
     * @param newPassword 신규비밀번호
     */
    public static void resetPassword(User user, String newPassword) {
        user.password = hashedPassword(newPassword, user.passwordSalt);
        user.save();
    }   

    /**
     * 세션에 저장된 정보를 이용해서 사용자 객체를 생성한다
     * 세션에 저장된 정보가 없다면 anonymous 객체가 리턴된다
     * 
     * @return 세션 정보 기준 조회된 사용자 객체
     */
    public static User currentUser() {
        String userId = session().get(SESSION_USERID);
        if (StringUtils.isEmpty(userId) || !StringUtils.isNumeric(userId)) {
            return User.anonymous;
        }
        User foundUser = User.find.byId(Long.valueOf(userId));
        if (foundUser == null) {
            processLogout();
            return User.anonymous;
        }
        return foundUser;
    }

    /**
     * 사용자 정보 조회
     * 
     * @param loginId 로그인ID
     * @return
     */
    public static Result userInfo(String loginId){
        User user = User.findByLoginId(loginId);
        return ok(info.render(user));
    }

    /**
     * 사용자 정보 수정 폼으로 이동
     * 현재 로그인된 사용자 기준
     * 
     * @return
     */
    public static Result editUserInfoForm() {
        User user = UserApp.currentUser();
        Form<User> userForm = new Form<User>(User.class);
        userForm = userForm.fill(user);
        return ok(edit.render(userForm, user));
    }

    /**
     * 사용자 정보 수정
     * 
     * @return
     */
    public static Result editUserInfo() {
        Form<User> userForm = new Form<User>(User.class).bindFromRequest("name", "email");
        String newEmail = userForm.data().get("email");
        String newName = userForm.data().get("name");
        User user = UserApp.currentUser();
        if (!StringUtils.equals(user.email, newEmail)) {
            if (User.isEmailExist(newEmail)) {
                userForm.reject("email", "user.email.duplicate");
            }
        }
        if (userForm.error("email") != null) {
            flash(Constants.WARNING, userForm.error("email").message());
            return badRequest(edit.render(userForm, user));
        }
        user.email = newEmail;
        user.name = newName;

        int attachCount = Attachment.countByContainer(user.asResource());
        if (attachCount > 0) {
            Attachment.deleteAll(user.avatarAsResource());
            Attachment.moveAll(currentUser().asResource(), currentUser().avatarAsResource());
            user.avatarUrl = "/files/" + user.avatarId();
        }

        user.update();
        return redirect(routes.UserApp.userInfo(user.loginId));
    }

    /**
     * 현재 사용자가 특정 프로젝트에서 탈퇴
     * 
     * @param userName 프로젝트 매니저의 로그인ID
     * @param projectName 프로젝트 이름
     * @return
     */
    public static Result leave(String userName, String projectName) {
        ProjectApp.deleteMember(userName, projectName, UserApp.currentUser().id);
        return redirect(routes.UserApp.userInfo(UserApp.currentUser().loginId));
    }

    /**
     * 로그인ID 존재 여부, 로그인ID 예약어 여부
     * 
     * @param loginId 로그인ID
     * @return
     */
    public static Result isUserExist(String loginId) {
        ObjectNode result = Json.newObject();        
        result.put("isExist", User.isLoginIdExist(loginId));
        result.put("isReserved", ReservedWordsValidator.isReserved(loginId));
        return ok(result);
    }

    /**
     * 이메일 존재 여부
     * 
     * @param email 이메일
     * @return
     */
    @BodyParser.Of(BodyParser.Json.class)
    public static Result isEmailExist(String email) {
        ObjectNode result = Json.newObject();
        result.put("isExist", User.isEmailExist(email));
        return ok(result);
    }

    /**
     * 비밀번호 hash 값 생성
     * 
     * @param plainTextPassword plain text
     * @param passwordSalt hash salt
     * @return hashed password
     */
    public static String hashedPassword(String plainTextPassword, String passwordSalt) {
        if (plainTextPassword == null  || passwordSalt == null) {
            return null;
        }
        return new Sha256Hash(plainTextPassword, ByteSource.Util.bytes(passwordSalt), HASH_ITERATIONS).toBase64();
    }
    
    /*
     * 사용자 인증
     * 
     * 사용자 객체와 hash 값을 이용
     */
    private static User authenticate(User user, String password) {
        if (!user.isAnonymous()) {
            if (user.password.equals(password)) {
                return user;
            }
        }
        return null;
    }
    
    /*
     * 시스템 설정에서 가입 승인 여부 조회
     */
    private static boolean isUseSignUpConfirm(){
        Configuration config = play.Play.application().configuration();
        String useSignUpConfirm = config.getString("signup.require.confirm");
        if (useSignUpConfirm != null && useSignUpConfirm.equals("true")) {
            return true;
        } else {
            return false;
        }
    }
    
    /*
     * 로그인 유지기능 활성화
     */
    private static void setupRememberMe(User user) {
        response().setCookie(TOKEN, user.loginId + ":" + user.password, MAX_AGE);
        Logger.debug("remember me enabled");
    }
    
    /*
     * 로그아웃 처리
     * 
     * 세션 데이터 클리어
     * 로그인 유지 쿠키 폐기
     */
    private static void processLogout() {
        session().clear();
        response().discardCookie(TOKEN);
    }
    
    /*
     * 사용자 가입 입력 폼 유효성 체크
     */
    private static void validate(Form<User> newUserForm) {
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
        
        // 중복된 email로 가입할 수 없다.
        if (User.isEmailExist(newUserForm.field("email").value())) {
            newUserForm.reject("email", "user.email.duplicate");
        }
    }
    
    /*
     * 신규 가입 사용자 생성
     */
    private static User createNewUser(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        user.passwordSalt = rng.nextBytes().getBytes().toString();
        user.password = hashedPassword(user.password, user.passwordSalt);
        user.avatarUrl = DEFAULT_AVATAR_URL;
        if (isUseSignUpConfirm()) {
            user.isLocked = true;
        }
        return user;
    }
    
    /*
     * 사용자 정보를 세션에 저장한다 (로그인 처리됨)
     */
    private static void addUserInfoToSession(User user) {
        session(SESSION_USERID, String.valueOf(user.id));
        session(SESSION_LOGINID, user.loginId);
        session(SESSION_USERNAME, user.name);
    }
}
