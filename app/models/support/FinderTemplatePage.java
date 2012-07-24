package models.support;

import models.Issue;
import play.db.ebean.Model;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.PagingList;

public class FinderTemplatePage {

    public static <K, T> PagingList<T> findBy(OrderParams mop,
            SearchParams msp, Model.Finder<K, T> finder) {
        ExpressionList<T> el = finder.where();

        if (!msp.getSearchParams().isEmpty()) {
            for (SearchParam sp : msp.getSearchParams()) {
                String field = sp.getField();
                Object value = sp.getValue();
                switch (sp.getMatching()) {
                case EQUALS:
                    el.eq(field, value);
                    break;
                case NOT_EQUALS:
                    el.ne(field, value);
                case GE:
                    el.ge(field, value);
                    break;
                case GT:
                    el.gt(field, value);
                    break;
                case LE:
                    el.le(field, value);
                    break;
                case LT:
                    el.lt(field, value);
                    break;
                case CONTAINS:
                    el.contains(sp.getField(), (String) value);
                    break;
                default: /* TODO */
                    break;
                }
            }
        }

        if (!mop.getOrderParams().isEmpty()) {
            for (OrderParam op : mop.getOrderParams()) {
                switch (op.getDirection()) {
                case ASC:
                    el.orderBy(op.getSort() + " " + "asc");
                    break;
                case DESC:
                    el.orderBy(op.getSort() + " " + "desc");
                    break;
                }
            }
        }

        return el.findPagingList(Issue.ISSUE_COUNT_PER_PAGE);
    }

}
