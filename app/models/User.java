package models;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class User extends Model {
	@Id
	public Long id;
	public String name;
	public String userId;
	public String password;
	public String role;

	private static final long serialVersionUID = 1L;

	private static Finder<Long, User> find = new Finder<Long, User>(Long.class, User.class);
	
	public static User findByName(String name)
	{
		return find.where().eq("name", name).findUnique();		
	}

	public static User findById(Long writerId) {
		return find.byId(writerId);
	}
}
