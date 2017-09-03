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
public class ProjectMenuDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String PROJECT_ID = "project_id";
    private static final String CODE = "code";
    private static final String ISSUE = "issue";
    private static final String PULL_REQUEST = "pull_request";
    private static final String REVIEW = "review";
    private static final String MILESTONE = "milestone";
    private static final String BOARD = "board";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setLong(index++, node.get(PROJECT_ID).longValue());
        ps.setBoolean(index++, node.get(CODE).booleanValue());
        ps.setBoolean(index++, node.get(ISSUE).booleanValue());
        ps.setBoolean(index++, node.get(PULL_REQUEST).booleanValue());
        ps.setBoolean(index++, node.get(REVIEW).booleanValue());
        ps.setBoolean(index++, node.get(MILESTONE).booleanValue());
        ps.setBoolean(index++, node.get(BOARD).booleanValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putBoolean(generator, CODE, rs, index++);
        putBoolean(generator, ISSUE, rs, index++);
        putBoolean(generator, PULL_REQUEST, rs, index++);
        putBoolean(generator, REVIEW, rs, index++);
        putBoolean(generator, MILESTONE, rs, index++);
        putBoolean(generator, BOARD, rs, index++);
    }

    @Override
    public String getTable() {
        return "PROJECT_MENU_SETTING";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PROJECT_MENU_SETTING (ID, PROJECT_ID, CODE, ISSUE, PULL_REQUEST, REVIEW, " +
                "MILESTONE, BOARD) " + values(8);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, PROJECT_ID, CODE, ISSUE, PULL_REQUEST, REVIEW, MILESTONE, BOARD FROM PROJECT_MENU_SETTING";
    }
}
