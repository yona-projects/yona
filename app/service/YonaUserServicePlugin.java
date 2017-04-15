package service;

import com.feth.play.module.pa.service.UserServicePlugin;
import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.AuthUserIdentity;
import com.feth.play.module.pa.user.BasicIdentity;
import controllers.UserApp;
import models.User;
import models.UserCredential;
import models.enumeration.UserState;
import play.Application;
import utils.Constants;

import javax.annotation.Nonnull;

import static controllers.Application.useSocialNameSync;
import static play.mvc.Controller.flash;
import static play.mvc.Http.Context.Implicit.session;

public class YonaUserServicePlugin extends UserServicePlugin {

	public YonaUserServicePlugin(final Application app) {
		super(app);
	}

	@Override
	public Object save(final AuthUser authUser) {
		final boolean isLinked = UserCredential.existsByAuthUserIdentity(authUser);
		if (!isLinked) {
			UserCredential userCredential = UserCredential.create(authUser);
			User existed = User.findByEmail(userCredential.email);
			if (existed.isAnonymous()) {
				existed = UserApp.createLocalUserWithOAuth(userCredential);
			}
			if (existed.state == UserState.ACTIVE) {
				UserApp.setupRememberMe(existed);
				UserApp.addUserInfoToSession(existed);
			}
			return userCredential.id;
		} else {
			// we have this user already, so return null
			return null;
		}
	}

	@Override
	public Object getLocalIdentity(final AuthUserIdentity identity) {
		// For production: Caching might be a good idea here...
		// ...and dont forget to sync the cache when users get deactivated/deleted
		final UserCredential u = UserCredential.findByAuthUserIdentity(identity);
		if(u != null) {
			if(identity instanceof BasicIdentity){
				BasicIdentity authUser = ((BasicIdentity) identity);
				setStatusLoggedIn(u, authUser);
				if(useSocialNameSync && !u.name.equals(authUser.getName())){
					updateLocalUserName(u, authUser);
				}
			}
			return u.id;
		} else {
			return null;
		}
	}

	private boolean setStatusLoggedIn(@Nonnull UserCredential u, BasicIdentity authUser) {
		User localUser = User.findByEmail(authUser.getEmail()); //find with oAuth email address
		if(localUser.isAnonymous()){
			localUser = User.findByEmail(u.email);  // 1st trial: same email address with local user credential
			if(localUser == null || localUser.isAnonymous()) localUser =  User.find.byId(u.user.id); // 2nd trial: linked user
			if(localUser == null) localUser = User.anonymous;
		}

		User willLoginUser = null;
		if(localUser.isAnonymous() && u.loginId == null){
			willLoginUser = UserApp.createLocalUserWithOAuth(u);
		} else {
			willLoginUser = localUser;
			if(u.loginId == null){
				u.loginId = willLoginUser.loginId;
				u.user = willLoginUser;
				u.update();
			}
		}

		if(!willLoginUser.isAnonymous() && willLoginUser.state == UserState.ACTIVE){
			UserApp.setupRememberMe(willLoginUser);
			UserApp.addUserInfoToSession(willLoginUser);
			return true;
		} else {
			flash(Constants.WARNING, "user.locked");
			forceOAuthLogout();
			return false;
		}
	}

	private void updateLocalUserName(UserCredential u, BasicIdentity authUser) {
		u.name = authUser.getName();
		u.update();

		User localUser = User.findByEmail(authUser.getEmail());
		if(localUser != null && localUser.state == UserState.ACTIVE){
            localUser.name = authUser.getName();
            localUser.update();
			UserApp.addUserInfoToSession(localUser);
        } else {
			flash(Constants.WARNING, "user.locked");
			forceOAuthLogout();
		}
	}

	@Override
	public AuthUser merge(final AuthUser newUser, final AuthUser oldUser) {
		if (!oldUser.equals(newUser)) {
			UserCredential.merge(oldUser, newUser);
		}
		return oldUser;
	}

	@Override
	public AuthUser link(final AuthUser oldUser, final AuthUser newUser) {
		UserCredential.addLinkedAccount(oldUser, newUser);
		return null;
	}

	private static void forceOAuthLogout() {
		session().put("pa.url.orig", controllers.routes.Application.oAuthLogout().url());
	}
}
