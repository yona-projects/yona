package models.support;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import play.db.ebean.Model;

import java.util.List;

public class FinderTemplate {

    private static <K, T> ExpressionList<T> makeExpressionList(OrderParams mop,
                                                               SearchParams msp,
                                                               Model.Finder<K, T> finder) {
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
                    default: /*TODO*/
                        break;
                }
            }
        }

        if (!mop.getOrderParams().isEmpty()) {
            for (OrderParam op : mop.getOrderParams()) {
                switch (op.getDirection()) {
                    case ASC:
                        el.orderBy(op.getSort() + " asc");
                        break;
                    case DESC:
                        el.orderBy(op.getSort() + " desc");
                        break;
                }
            }
        }

        return el;
    }

    public static <K, T> List<T> findBy(OrderParams mop,
                                        SearchParams msp,
                                        Model.Finder<K, T> finder) {
        return makeExpressionList(mop, msp, finder).findList();
    }

    public static <K, T> Page<T> getPage(OrderParams mop,
                                         SearchParams msp,
                                         Model.Finder<K, T> finder, int pageSize, int page) {
        return makeExpressionList(mop, msp, finder).findPagingList(pageSize).getPage(page);
    }

}