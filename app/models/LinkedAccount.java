package models;

import com.feth.play.module.pa.user.AuthUser;
import io.ebean.Finder;
import io.ebean.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class LinkedAccount extends Model {

	private static final long serialVersionUID = 1L;

	@Id
	public Long id;

	@ManyToOne
	public UserCredential userCredential;

	public String providerUserId;
	public String providerKey;

	public static final Finder<Long, LinkedAccount> find = new Finder<Long, LinkedAccount>(LinkedAccount.class);

	public static LinkedAccount findByProviderKey(final UserCredential userCredential, String key) {
		return find.query().where().eq("userCredential", userCredential).eq("providerKey", key)
				.findOne();
	}

	public static LinkedAccount create(final AuthUser authUser) {
		final LinkedAccount ret = new LinkedAccount();
		ret.update(authUser);
		return ret;
	}
	
	public void update(final AuthUser authUser) {
		this.providerKey = authUser.getProvider();
		this.providerUserId = authUser.getId();
	}

	public static LinkedAccount create(final LinkedAccount acc) {
		final LinkedAccount ret = new LinkedAccount();
		ret.providerKey = acc.providerKey;
		ret.providerUserId = acc.providerUserId;

		return ret;
	}
}