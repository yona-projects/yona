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

}
