package models.support;

import models.Milestone;
import models.ModelTest;
import models.enumeration.Matching;
import models.enumeration.Ordering;
import org.junit.Test;
import play.db.ebean.Model;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;


public class FinderTemplateTest extends ModelTest {

    private static Model.Finder<Long, Milestone> find = new Model.Finder<Long, Milestone>(
            Long.class, Milestone.class);

    @Test
    public void findBy() throws Exception {
        OrderParams orderParams = new OrderParams();
        SearchParams searchParams = new SearchParams();

        orderParams.add("dueDate", Ordering.ASC);
        searchParams.add("projectId", 2l, Matching.EQUALS);
        searchParams.add("isCompleted", false, Matching.EQUALS);

        List<Milestone> p2MilestoneList = FinderTemplate.findBy(orderParams, searchParams, find);
        assertThat(p2MilestoneList.get(0).getId()).isEqualTo(5);

        orderParams.clean();
        searchParams.clean();

        orderParams.add("dueDate", Ordering.DESC);
        searchParams.add("projectId", 2l, Matching.LT);
        searchParams.add("isCompleted", false, Matching.NOT_EQUALS);

        List<Milestone> p1MilestoneList = FinderTemplate.findBy(orderParams, searchParams, find);
        assertThat(p1MilestoneList.get(0).getId()).isEqualTo(1);
    }
}
