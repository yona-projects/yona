package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

@Entity
public class ProjectUser extends Model {
    private static final long serialVersionUID = 1L;
    
    @Id
    public Long id;
    @ManyToOne
    public User user; 
    @ManyToOne
    public Project project;
    @ManyToOne
    public Role role;
   
}
