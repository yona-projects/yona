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
public class PullRequestCommitDataExchanger extends DefaultExchanger {

    private static final String ID = "id"; // BIGINT(19) NOT NULL
    private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19)
    private static final String COMMIT_ID = "commit_id"; // VARCHAR(255)
    private static final String COMMIT_SHORT_ID = "commit_short_id"; // VARCHAR(7)
    private static final String COMMIT_MESSAGE = "commit_message"; // CLOB(2147483647)
    private static final String CREATED = "created"; // TIMESTAMP(23, 10)
    private static final String AUTHOR_DATE = "author_date"; // TIMESTAMP(23, 10)
    private static final String AUTHOR_EMAIL = "author_email"; // VARCHAR(255)
    private static final String STATE = "state"; // VARCHAR(10)

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setLong(index++, node.get(PULL_REQUEST_ID).longValue());
        ps.setString(index++, node.get(COMMIT_ID).textValue());
        ps.setString(index++, node.get(COMMIT_SHORT_ID).textValue());
        setClob(ps, index++, node, COMMIT_MESSAGE);
        ps.setTimestamp(index++, timestamp(node.get(CREATED).longValue()));
        ps.setTimestamp(index++, timestamp(node.get(AUTHOR_DATE).longValue()));
        ps.setString(index++, node.get(AUTHOR_EMAIL).textValue());
        ps.setString(index++, node.get(STATE).textValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, PULL_REQUEST_ID, rs, index++);
        putString(generator, COMMIT_ID, rs, index++);
        putString(generator, COMMIT_SHORT_ID, rs, index++);
        putClob(generator, COMMIT_MESSAGE, rs, index++);
        putTimestamp(generator, CREATED, rs, index++);
        putTimestamp(generator, AUTHOR_DATE, rs, index++);
        putString(generator, AUTHOR_EMAIL, rs, index++);
        putString(generator, STATE, rs, index++);
    }

    @Override
    public String getTable() {
        return "PULL_REQUEST_COMMIT";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PULL_REQUEST_COMMIT (ID, PULL_REQUEST_ID, COMMIT_ID, COMMIT_SHORT_ID, " +
                "COMMIT_MESSAGE, CREATED, AUTHOR_DATE, AUTHOR_EMAIL, STATE) " + values(9);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, PULL_REQUEST_ID, COMMIT_ID, COMMIT_SHORT_ID, COMMIT_MESSAGE, CREATED, " +
                "AUTHOR_DATE, AUTHOR_EMAIL, STATE FROM PULL_REQUEST_COMMIT";
    }
}
