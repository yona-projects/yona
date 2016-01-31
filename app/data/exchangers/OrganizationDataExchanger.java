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
public class OrganizationDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String DESCR = "descr";
    private static final String CREATED = "created";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(NAME).textValue());
        ps.setString(index++, node.get(DESCR).textValue());
        ps.setTimestamp(index++, timestamp(node.get(CREATED).longValue()));
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, NAME, rs, index++);
        putString(generator, DESCR, rs, index++);
        putTimestamp(generator, CREATED, rs, index++);
    }

    @Override
    public String getTable() {
        return "ORGANIZATION";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO ORGANIZATION (ID, NAME, DESCR, CREATED) " + values(4);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, NAME, DESCR, CREATED FROM ORGANIZATION";
    }
}
