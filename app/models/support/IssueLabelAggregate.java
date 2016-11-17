/**
 * Yona, 21c Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models.support;

import com.avaje.ebean.annotation.Sql;
import play.db.ebean.Model;

import javax.persistence.Entity;

@Entity
@Sql
public class IssueLabelAggregate extends Model {
    private static final long serialVersionUID = -8843323869004757091L;
    public Long issueId;
    public Long issueLabelId;
}
