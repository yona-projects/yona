package models.task;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.ProjectUser;
import play.db.ebean.Model;

@Entity
public class CardAssignee extends Model {
    private static Finder<Long, CardAssignee> find = new Finder<Long, CardAssignee>(Long.class,
        CardAssignee.class);

    public CardAssignee() {

    }

    public CardAssignee(Card card, Long projectUserId) {
        projectUser = ProjectUser.findById(projectUserId);
    }

    @Id
    public Long id;

    @ManyToOne
    public Card card;

    @ManyToOne
    public ProjectUser projectUser;

    public static List<CardAssignee> findByAssignee(Long userId) {
        return find.where().eq("projectUser.user.id", userId).findList();
    }
}