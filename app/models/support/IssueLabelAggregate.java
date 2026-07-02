/**
 * Yona, 21c Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models.support;

import io.ebean.annotation.Sql;
import io.ebean.Model;

import jakarta.persistence.Entity;

@Entity
@Sql
public class IssueLabelAggregate extends Model {
    private static final long serialVersionUID = -8843323869004757091L;
    public Long issueId;
    public Long issueLabelId;
}
