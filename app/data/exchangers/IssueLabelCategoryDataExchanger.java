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
public class IssueLabelCategoryDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String PROJECT_ID = "project_id";
    private static final String NAME = "name";
    private static final String IS_EXCLUSIVE = "is_exclusive";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setLong(index++, node.get(PROJECT_ID).longValue());
        ps.setString(index++, node.get(NAME).textValue());
        ps.setBoolean(index++, node.get(IS_EXCLUSIVE).booleanValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putString(generator, NAME, rs, index++);
        putBoolean(generator, IS_EXCLUSIVE, rs, index++);
    }

    @Override
    public String getTable() {
        return "ISSUE_LABEL_CATEGORY";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO ISSUE_LABEL_CATEGORY (ID, PROJECT_ID, NAME, IS_EXCLUSIVE) " + values(4);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, PROJECT_ID, NAME, IS_EXCLUSIVE FROM ISSUE_LABEL_CATEGORY";
    }
}
