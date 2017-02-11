package service;

import com.feth.play.module.pa.service.UserServicePlugin;
import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.AuthUserIdentity;
import com.feth.play.module.pa.user.BasicIdentity;
import models.User;
import models.UserCredential;
import play.Application;

public class YonaUserServicePlugin extends UserServicePlugin {
    private static boolean useSocialNameSync = play.Configuration.root().getBoolean("application.use.social.login.name.sync", false);

	public YonaUserServicePlugin(final Application app) {
		super(app);
	}

	@Override
	public Object save(final AuthUser authUser) {
		final boolean isLinked = UserCredential.existsByAuthUserIdentity(authUser);
		if (!isLinked) {
			return UserCredential.create(authUser).id;
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

	private void updateLocalUserName(UserCredential u, BasicIdentity authUser) {
		u.name = authUser.getName();
		u.update();

		User localUser = User.findByEmail(authUser.getEmail());
		if(localUser != null){
            localUser.name = authUser.getName();
            localUser.update();
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

}
