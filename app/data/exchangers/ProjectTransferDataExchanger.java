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
public class ProjectTransferDataExchanger extends DefaultExchanger {

    private static final String ID = "id"; // BIGINT(19) NOT NULL
    private static final String SENDER_ID = "sender_id"; // VARCHAR(255)
    private static final String DESTINATION = "destination"; // VARCHAR(255)
    private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL
    private static final String REQUESTED = "requested"; // TIMESTAMP(23, 10)
    private static final String CONFIRM_KEY = "confirm_key"; // VARCHAR(50)
    private static final String ACCEPTED = "accepted"; // BOOLEAN(1)
    private static final String NEW_PROJECT_NAME = "new_project_name"; // VARCHAR(255)

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(SENDER_ID).textValue());
        ps.setString(index++, node.get(DESTINATION).textValue());
        ps.setLong(index++, node.get(PROJECT_ID).longValue());
        ps.setTimestamp(index++, timestamp(node.get(REQUESTED).longValue()));
        ps.setString(index++, node.get(CONFIRM_KEY).textValue());
        ps.setBoolean(index++, node.get(ACCEPTED).booleanValue());
        ps.setString(index++, node.get(NEW_PROJECT_NAME).textValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, SENDER_ID, rs, index++);
        putString(generator, DESTINATION, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putTimestamp(generator, REQUESTED, rs, index++);
        putString(generator, CONFIRM_KEY, rs, index++);
        putBoolean(generator, ACCEPTED, rs, index++);
        putString(generator, NEW_PROJECT_NAME, rs, index++);
    }

    @Override
    public String getTable() {
        return "PROJECT_TRANSFER";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PROJECT_TRANSFER (ID, SENDER_ID, DESTINATION, PROJECT_ID, REQUESTED, CONFIRM_KEY, " +
                "ACCEPTED, NEW_PROJECT_NAME) " + values(8);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, SENDER_ID, DESTINATION, PROJECT_ID, REQUESTED, CONFIRM_KEY, ACCEPTED, NEW_PROJECT_NAME " +
                "FROM PROJECT_TRANSFER";
    }
}
