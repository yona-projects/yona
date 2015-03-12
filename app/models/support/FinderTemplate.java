/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yoon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models.support;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import play.db.ebean.Model;

import java.util.Collection;
import java.util.List;

public class FinderTemplate {

    private static <K, T> ExpressionList<T> makeExpressionList(OrderParams mop,
                                                               SearchParams msp,
                                                               Model.Finder<K, T> finder) {
        ExpressionList<T> el = finder.where();

        if (msp != null && !msp.getSearchParams().isEmpty()) {
            for (SearchParam sp : msp.getSearchParams()) {
                String field = sp.getField();
                Object value = sp.getValue();
                if (value == null) {
                    continue;
                }
                switch (sp.getMatching()) {
                    case EQUALS:
                        el.eq(field, value);
                        break;
                    case NOT_EQUALS:
                        el.ne(field, value);
                        break;
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
                    case IN:
                        el.in(sp.getField(), (Collection<?>) value);
                        break;
                    default: /*TODO*/
                        break;
                }
            }
        }

        if (mop != null && !mop.getOrderParams().isEmpty()) {
            for (OrderParam op : mop.getOrderParams()) {
                if (op.getSort() == null || op.getSort().trim().isEmpty()) {
                    continue;
                }
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
