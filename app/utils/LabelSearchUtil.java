package utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.LabelOwner;
import models.ResourceConvertible;

import play.mvc.Http.Request;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Expression;
import com.avaje.ebean.Query;
import com.avaje.ebean.QueryResultVisitor;

public class LabelSearchUtil {
    private static final String FIELD_NAME_ID = "id";

    /**
     * {@code query} 에 저장되어 있는 검색 조건을 만족하면서
     * {@code labelId} 에 해당되는 라벨을 모두 가지고 있는 객체들의 ID를 찾는다.
     * 찾아진 ID 들은 {@code Expr.in} 으로 반환되며, 찾은 ID가 없을 경우
     * 성립 불가능한 Expression 이 반환된다.
     *
     * @param query query
     * @param labelIds 검색할 라벨 ID
     * @return 검색 조건에 추가되어야 할 {@link Expression}
     */
    @SuppressWarnings("unchecked")
    public static Expression createLabelSearchExpression(final Query<? extends LabelOwner> query, final Set<Long> labelIds) {
        List<Object> ids = findIds((Query<LabelOwner>) query, labelIds);
        if (ids.isEmpty()) {
            return Expr.isNull(FIELD_NAME_ID);
        } else {
            return Expr.in(FIELD_NAME_ID, ids);
        }
    }

    /**
     * 요청 객체에서 라벨ID 를 set 형태로 추출한다.
     * @param request 요청
     * @return 라벨ID set
     */
    public static Set<Long> getLabelIds(final Request request) {
        Set<Long> set = new HashSet<>();
        String[] labelIds = request.queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                set.add(Long.valueOf(labelId));
            }
        }
        return set;
    }

    /*
     * query 에 저장되어 있는 검색 조건을 만족하면서,
     * labelIds 에 해당되는 라벨을 모두 가지고 있는 labelOwner 들의 ID를 찾는다.
     * 조건을 만족하는 건이 없을 경우 빈 List 가 반환된다.
     */
    private static List<Object> findIds(final Query<LabelOwner> query, final Set<Long> labelIds) {
        final List<Object> ids = new ArrayList<>();
        query.copy().findVisit(new QueryResultVisitor<LabelOwner>() {
            /**
             * @see com.avaje.ebean.QueryResultVisitor#accept
             */
            @Override
            public boolean accept(LabelOwner labelOwner) {
                if (hasLabels(labelOwner, labelIds)) {
                    ids.add(labelOwner.asResource().getId());
                }
                return true;
            }
        });
        return ids;
    }

    /*
     * labelOwner 가 labelIds 에 해당하는 모든 라벨을 가지고 있는지 확인한다.
     */
    private static boolean hasLabels(final LabelOwner labelOwner, final Set<Long> labelIds) {
        Set<Long> objectLabelIds = new HashSet<>();
        Set<? extends ResourceConvertible> labels = (Set<? extends ResourceConvertible>) labelOwner.getLabels();
        for (ResourceConvertible label : labels) {
            objectLabelIds.add(Long.valueOf(label.asResource().getId()));
        }
        return objectLabelIds.containsAll(labelIds);
    }
}
