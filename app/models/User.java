package models;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class User extends Model {
	
	//FIXME DUMMY
	public User(String name) {
		this.name = name;
		this.id = 19894242L;
	}

	private static final long serialVersionUID = 1L;
	
	@Id
	public Long id;
	public String name;
	
	public static User guest = new User("guest");
	
	public static User findById(Long Id)
	{
		return guest;
	}
}
