package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

@Entity
public class CardLabel extends Model {

    private static final long serialVersionUID = -5436658861223275375L;

    public CardLabel() {
        // TODO Auto-generated constructor stub
    }

    public CardLabel(Card card, Long labelId) {
        this.card = card;
        this.label = Label.findById(labelId);
    }

    @Id
    public Long id;

    @ManyToOne
    public Card card;

    @ManyToOne
    public Label label;
}
