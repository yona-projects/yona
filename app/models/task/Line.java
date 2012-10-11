package models.task;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;


import play.db.ebean.Model;

@Entity
public class Line extends Model {
    @Id
    Long id;
    String title;
    List<Card> cards;
}
