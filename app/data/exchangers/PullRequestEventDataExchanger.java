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
public class PullRequestEventDataExchanger extends DefaultExchanger {

    private static final String ID = "id"; // BIGINT(19) NOT NULL
    private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19)
    private static final String CREATED = "created"; // TIMESTAMP(23, 10)
    private static final String SENDER_LOGIN_ID = "sender_login_id"; // VARCHAR(255)
    private static final String EVENT_TYPE = "event_type"; // VARCHAR(255)
    private static final String NEW_VALUE = "new_value"; // CLOB(2147483647)
    private static final String OLD_VALUE = "old_value"; // CLOB(2147483647)

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setLong(index++, node.get(PULL_REQUEST_ID).longValue());
        ps.setTimestamp(index++, timestamp(node.get(CREATED).longValue()));
        ps.setString(index++, node.get(SENDER_LOGIN_ID).textValue());
        ps.setString(index++, node.get(EVENT_TYPE).textValue());
        setClob(ps, index++, node, NEW_VALUE);
        setClob(ps, index++, node, OLD_VALUE);
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, PULL_REQUEST_ID, rs, index++);
        putTimestamp(generator, CREATED, rs, index++);
        putString(generator, SENDER_LOGIN_ID, rs, index++);
        putString(generator, EVENT_TYPE, rs, index++);
        putClob(generator, NEW_VALUE, rs, index++);
        putClob(generator, OLD_VALUE, rs, index++);
    }

    @Override
    public String getTable() {
        return "PULL_REQUEST_EVENT";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PULL_REQUEST_EVENT (ID, PULL_REQUEST_ID, CREATED, SENDER_LOGIN_ID, " +
                "EVENT_TYPE, NEW_VALUE, OLD_VALUE) " + values(7);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, PULL_REQUEST_ID, CREATED, SENDER_LOGIN_ID, EVENT_TYPE, " +
                "NEW_VALUE, OLD_VALUE FROM PULL_REQUEST_EVENT";
    }
}
