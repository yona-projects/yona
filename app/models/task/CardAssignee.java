package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.ProjectUser;
import play.db.ebean.Model;

@Entity
public class CardAssignee extends Model {
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
}
