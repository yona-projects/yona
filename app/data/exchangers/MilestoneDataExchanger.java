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
public class MilestoneDataExchanger extends DefaultExchanger {

    private static final String ID = "id"; // BIGINT(19) NOT NULL
    private static final String TITLE = "title"; // VARCHAR(255)
    private static final String DUE_DATE = "due_date"; // TIMESTAMP(23, 10)
    private static final String CONTENTS = "contents"; // CLOB(2147483647)
    private static final String STATE = "state"; // INTEGER(10)
    private static final String PROJECT_ID = "project_id"; // BIGINT(19)

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(TITLE).textValue());
        ps.setTimestamp(index++, timestamp(node.get(DUE_DATE).longValue()));
        setClob(ps, index++, node, CONTENTS);
        ps.setInt(index++, node.get(STATE).intValue());
        setNullableLong(ps, index++, node, PROJECT_ID);
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, TITLE, rs, index++);
        putTimestamp(generator, DUE_DATE, rs, index++);
        putClob(generator, CONTENTS, rs, index++);
        putInt(generator, STATE, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
    }

    @Override
    public String getTable() {
        return "MILESTONE";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO MILESTONE (ID, TITLE, DUE_DATE, CONTENTS, STATE, PROJECT_ID) " +
                values(6);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, TITLE, DUE_DATE, CONTENTS, STATE, PROJECT_ID FROM MILESTONE";
    }
}
