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
public class IssueDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String BODY = "body";
    private static final String CREATED_DATE = "created_date";
    private static final String NUM_OF_COMMENTS = "num_of_comments";
    private static final String MILESTONE_ID = "milestone_id";
    private static final String AUTHOR_ID = "author_id";
    private static final String AUTHOR_LOGIN_ID = "author_login_id";
    private static final String AUTHOR_NAME = "author_name";
    private static final String STATE = "state";
    private static final String PROJECT_ID = "project_id";
    private static final String ASSIGNEE_ID = "assignee_id";
    private static final String NUMBER = "number";
    private static final String UPDATED_DATE = "updated_date";
    private static final String DUE_DATE = "due_date";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(TITLE).textValue());
        setClob(ps, index++, node, BODY);
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue()));
        ps.setInt(index++, node.get(NUM_OF_COMMENTS).intValue());
        setNullableLong(ps, index++, node, MILESTONE_ID);
        ps.setLong(index++, node.get(AUTHOR_ID).longValue());
        ps.setString(index++, node.get(AUTHOR_LOGIN_ID).textValue());
        ps.setString(index++, node.get(AUTHOR_NAME).textValue());
        ps.setInt(index++, node.get(STATE).intValue());
        ps.setLong(index++, node.get(PROJECT_ID).longValue());
        setNullableLong(ps, index++, node, ASSIGNEE_ID);
        ps.setLong(index++, node.get(NUMBER).longValue());
        ps.setTimestamp(index++, timestamp(node.get(UPDATED_DATE).longValue()));
        ps.setTimestamp(index++, timestamp(node.get(DUE_DATE).longValue()));
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, TITLE, rs, index++);
        putClob(generator, BODY, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putInt(generator, NUM_OF_COMMENTS, rs, index++);
        putLong(generator, MILESTONE_ID, rs, index++);
        putLong(generator, AUTHOR_ID, rs, index++);
        putString(generator, AUTHOR_LOGIN_ID, rs, index++);
        putString(generator, AUTHOR_NAME, rs, index++);
        putInt(generator, STATE, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putLong(generator, ASSIGNEE_ID, rs, index++);
        putLong(generator, NUMBER, rs, index++);
        putTimestamp(generator, UPDATED_DATE, rs, index++);
        putTimestamp(generator, DUE_DATE, rs, index++);
    }

    @Override
    public String getTable() {
        return "ISSUE";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO ISSUE (ID, TITLE, BODY, CREATED_DATE, NUM_OF_COMMENTS, MILESTONE_ID, AUTHOR_ID, " +
                "AUTHOR_LOGIN_ID, AUTHOR_NAME, STATE, PROJECT_ID, ASSIGNEE_ID, NUMBER, UPDATED_DATE, DUE_DATE) " +
                values(15);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, TITLE, BODY, CREATED_DATE, NUM_OF_COMMENTS, MILESTONE_ID, AUTHOR_ID, AUTHOR_LOGIN_ID, " +
                "AUTHOR_NAME, STATE, PROJECT_ID, ASSIGNEE_ID, NUMBER, UPDATED_DATE, DUE_DATE FROM ISSUE";
    }
}
