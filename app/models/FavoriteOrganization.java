/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import io.ebean.Finder;
import io.ebean.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.util.List;

@Entity
public class FavoriteOrganization extends Model {
    public static Finder<Long, FavoriteOrganization> finder = new Finder<>(FavoriteOrganization.class);

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
        List<FavoriteOrganization> organizationList = finder.query().where().eq("organization.id", organization.id).findList();
        for(FavoriteOrganization favoriteOrganization: organizationList){
            favoriteOrganization.organization.refresh();
            favoriteOrganization.organizationName = organization.name;
            favoriteOrganization.update();
        }
    }

    public static FavoriteOrganization findByOrganizationId(Long userId, Long organizationId) {
        return finder.query().where()
                .eq("user.id", userId)
                .eq("organization.id", organizationId)
                .findOne();
    }
}
