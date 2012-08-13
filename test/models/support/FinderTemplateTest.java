package models.support;

import models.Milestone;
import models.ModelTest;
import models.enumeration.Direction;
import models.enumeration.Matching;
import org.junit.Test;
import play.db.ebean.Model;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;


public class FinderTemplateTest extends ModelTest<Milestone> {

    private static Model.Finder<Long, Milestone> find = new Model.Finder<Long, Milestone>(
            Long.class, Milestone.class);

    @Test
    public void findBy() throws Exception {
        OrderParams orderParams = new OrderParams();
        SearchParams searchParams = new SearchParams();

        orderParams.add("dueDate", Direction.ASC);
        searchParams.add("project.id", 2l, Matching.EQUALS);
        searchParams.add("completionRate", 100, Matching.LT);

        List<Milestone> p2MilestoneList = FinderTemplate.findBy(orderParams, searchParams, find);
        assertThat(p2MilestoneList.get(0).id).isEqualTo(4);

        orderParams.clean();
        searchParams.clean();

        orderParams.add("dueDate", Direction.DESC);
        searchParams.add("project.id", 1l, Matching.EQUALS);
        searchParams.add("completionRate", 50, Matching.EQUALS);

        List<Milestone> p1MilestoneList = FinderTemplate.findBy(orderParams, searchParams, find);
        assertThat(p1MilestoneList.get(0).id).isEqualTo(1);
    }
}
