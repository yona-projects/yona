package models;

import java.util.Date;

import javax.persistence.Id;

import play.db.ebean.Model;

public class Post extends Model {
    @Id
    public Long id;
    public Date date;
    public String content;
    public String title;
    public int commentCount;
    public Date modified;
}
