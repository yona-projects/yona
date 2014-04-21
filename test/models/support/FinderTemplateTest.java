/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yoon
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

import models.Milestone;
import models.ModelTest;
import models.enumeration.Direction;
import models.enumeration.Matching;
import org.junit.Test;
import play.db.ebean.Model;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;


public class FinderTemplateTest extends ModelTest<Milestone> {

    private static Model.Finder<Long, Milestone> find = new Model.Finder<>(
            Long.class, Milestone.class);

    @Test
    public void findBy() throws Exception {
        OrderParams orderParams = new OrderParams();
        SearchParams searchParams = new SearchParams();

        orderParams.add("dueDate", Direction.ASC);
        searchParams.add("project.id", 2l, Matching.EQUALS);

        List<Milestone> p2MilestoneList = FinderTemplate.findBy(orderParams, searchParams, find);
        assertThat(p2MilestoneList.get(0).id).isEqualTo(3);

        orderParams.clean();
        searchParams.clean();

        orderParams.add("dueDate", Direction.DESC);
        searchParams.add("project.id", 1l, Matching.EQUALS);

        List<Milestone> p1MilestoneList = FinderTemplate.findBy(orderParams, searchParams, find);
        assertThat(p1MilestoneList.get(0).id).isEqualTo(2);
    }
}
