package models;

import models.resource.Resource;

import org.joda.time.*;
import play.data.validation.*;
import play.db.ebean.*;
import utils.*;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.*;

@MappedSuperclass
abstract public class Comment extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Constraints.Required @Column(length = 4000) @Size(max=4000)
    public String contents;

    @Constraints.Required
    public Date date;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    public Comment() {
        date = new Date();
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.date);
    }

    abstract public Resource asResource();

    abstract public AbstractPosting getParent();

    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    public void save() {
        super.save();
        getParent().save();
    }

    public void delete() {
        super.delete();
        getParent().save();
    }
}
