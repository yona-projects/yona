package models;

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

/**
 * @author Keeun Baik
 */
@Entity
public class OrganizationUser extends Model {

    private static final long serialVersionUID = -1L;

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @ManyToOne
    public Organization organization;

    @ManyToOne
    public Role role;

}
