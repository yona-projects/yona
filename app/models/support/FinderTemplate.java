package models.support;

import com.avaje.ebean.ExpressionList;
import play.db.ebean.Model;

import java.util.List;

public class FinderTemplate {

    public static <K, T> List<T> findBy(OrderParams mop, SearchParams msp, Model.Finder<K, T> finder) {
        ExpressionList<T> el = finder.where();

        if (!msp.getSearchParams().isEmpty()) {
            for (SearchParam sp : msp.getSearchParams()) {
                String field = sp.getField();
                Object value = sp.getValue();
                switch (sp.getMatching()) {
                    case EQUALS:
                        el.eq(field, value);
                        break;
                    case NOT_EQUALS :
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
                    case LT :
                        el.lt(field, value);
                        break;
                    case CONTAINS:
                        el.contains(sp.getField(), (String) value);
                        break;
                    default: /*TODO*/
                        break;
                }
            }
        }

        if (!mop.getOrderParams().isEmpty()) {
            for (OrderParam op : mop.getOrderParams()) {
                switch (op.getOrdering()) {
                    case ASC:
                        el.orderBy(op.getField() + " asc");
                        break;
                    case DESC:
                        el.orderBy(op.getField() + " desc");
                        break;
                }
            }
        }

        return el.findList();
    }
}
