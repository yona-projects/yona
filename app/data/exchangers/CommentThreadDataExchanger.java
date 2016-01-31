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
 * @author Yi EungJun
 */
public class CommentThreadDataExchanger extends DefaultExchanger {
    private static final String DTYPE = "dtype";
    private static final String ID = "id";
    private static final String PROJECT_ID = "project_id";
    private static final String AUTHOR_ID = "author_id";
    private static final String AUTHOR_LOGIN_ID = "author_login_id";
    private static final String AUTHOR_NAME = "author_name";
    private static final String STATE = "state";
    private static final String COMMIT_ID = "commit_id";
    private static final String PATH = "path";
    private static final String START_SIDE = "start_side";
    private static final String START_LINE = "start_line";
    private static final String START_COLUMN = "start_column";
    private static final String END_SIDE = "end_side";
    private static final String END_LINE = "end_line";
    private static final String END_COLUMN = "end_column";
    private static final String PULL_REQUEST_ID = "pull_request_id";
    private static final String CREATED_DATE = "created_date";
    private static final String PREV_COMMIT_ID = "prev_commit_id";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setString(index++, node.get(DTYPE).textValue()); //VARCHAR  nullable? 0
        ps.setLong(index++, node.get(ID).longValue()); //BIGINT  nullable? 0
        ps.setLong(index++, node.get(PROJECT_ID).longValue()); //BIGINT  nullable? 0
        setNullableLong(ps, index++, node, AUTHOR_ID);
        ps.setString(index++, node.get(AUTHOR_LOGIN_ID).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(AUTHOR_NAME).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(STATE).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(COMMIT_ID).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(PATH).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(START_SIDE).textValue()); //VARCHAR  nullable? 1
        setNullableLong(ps, index++, node, START_LINE);
        setNullableLong(ps, index++, node, START_COLUMN);
        ps.setString(index++, node.get(END_SIDE).textValue()); //VARCHAR  nullable? 1
        setNullableLong(ps, index++, node, END_LINE);
        setNullableLong(ps, index++, node, END_COLUMN);
        setNullableLong(ps, index++, node, PULL_REQUEST_ID);
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue())); //TIMESTAMP  nullable? 1
        ps.setString(index++, node.get(PREV_COMMIT_ID).textValue()); //VARCHAR
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putString(generator, DTYPE, rs, index++);
        putLong(generator, ID, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putLong(generator, AUTHOR_ID, rs, index++);
        putString(generator, AUTHOR_LOGIN_ID, rs, index++);
        putString(generator, AUTHOR_NAME, rs, index++);
        putString(generator, STATE, rs, index++);
        putString(generator, COMMIT_ID, rs, index++);
        putString(generator, PATH, rs, index++);
        putString(generator, START_SIDE, rs, index++);
        putLong(generator, START_LINE, rs, index++);
        putLong(generator, START_COLUMN, rs, index++);
        putString(generator, END_SIDE, rs, index++);
        putLong(generator, END_LINE, rs, index++);
        putLong(generator, END_COLUMN, rs, index++);
        putLong(generator, PULL_REQUEST_ID, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putString(generator, PREV_COMMIT_ID, rs, index++);
    }

    @Override
    public String getTable() {
        return "COMMENT_THREAD";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO COMMENT_THREAD (DTYPE, ID, PROJECT_ID, AUTHOR_ID, " +
                "AUTHOR_LOGIN_ID, AUTHOR_NAME, STATE, COMMIT_ID, PATH, START_SIDE, START_LINE, " +
                "START_COLUMN, END_SIDE, END_LINE, END_COLUMN, PULL_REQUEST_ID, CREATED_DATE, " +
                "PREV_COMMIT_ID) " + values(18);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT DTYPE, ID, PROJECT_ID, AUTHOR_ID, AUTHOR_LOGIN_ID, AUTHOR_NAME, " +
                "STATE, COMMIT_ID, PATH, START_SIDE, START_LINE, START_COLUMN, END_SIDE, " +
                "END_LINE, END_COLUMN, PULL_REQUEST_ID, CREATED_DATE, PREV_COMMIT_ID " +
                "FROM COMMENT_THREAD";
    }
}
