/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.util.List;

@Entity
public class FavoriteOrganization extends Model {
    public static Finder<Long, FavoriteOrganization> finder = new Finder<>(Long.class, FavoriteOrganization.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @OneToOne
    public Organization organization;

    public String organizationName;

    public FavoriteOrganization(User user, Organization organization) {
        this.user = user;
        this.organization = organization;

        this.organizationName = organization.name;
    }

    public static void updateFavoriteOrganization(Organization organization) {
        List<FavoriteOrganization> organizationList = finder.where().eq("organization.id", organization.id).findList();
        for(FavoriteOrganization favoriteOrganization: organizationList){
            favoriteOrganization.organization.refresh();
            favoriteOrganization.organizationName = organization.name;
            favoriteOrganization.update();
        }
    }
}
