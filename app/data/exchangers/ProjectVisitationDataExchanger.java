/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2015 NAVER Corp.
 * http://yobi.io
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
package data.exchangers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import data.DefaultExchanger;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Keeun Baik
 */
public class ProjectVisitationDataExchanger extends DefaultExchanger {

    private static final String ID = "id"; // BIGINT(19) NOT NULL
    private static final String VISITED = "visited"; // TIMESTAMP(23, 10)
    private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL
    private static final String RECENTLY_VISITED_PROJECTS_ID = "recently_visited_projects_id"; // BIGINT(19) NOT NULL

    @Override
    public String getTable() {
        return "PROJECT_VISITATION";
    }


    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setTimestamp(index++, timestamp(node.get(VISITED).longValue()));
        ps.setLong(index++, node.get(PROJECT_ID).longValue());
        ps.setLong(index++, node.get(RECENTLY_VISITED_PROJECTS_ID).longValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putTimestamp(generator, VISITED, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putLong(generator, RECENTLY_VISITED_PROJECTS_ID, rs, index++);
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PROJECT_VISITATION (ID, VISITED, PROJECT_ID, RECENTLY_VISITED_PROJECTS_ID) " + values(4);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, VISITED, PROJECT_ID, RECENTLY_VISITED_PROJECTS_ID FROM PROJECT_VISITATION";
    }
}
