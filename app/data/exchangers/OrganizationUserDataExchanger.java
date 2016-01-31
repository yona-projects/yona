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
public class OrganizationUserDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String USER_ID = "user_id";
    private static final String ORGANIZATION_ID = "organization_id";
    private static final String ROLE_ID = "role_id";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setLong(index++, node.get(USER_ID).longValue());
        ps.setLong(index++, node.get(ORGANIZATION_ID).longValue());
        ps.setLong(index++, node.get(ROLE_ID).longValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, USER_ID, rs, index++);
        putLong(generator, ORGANIZATION_ID, rs, index++);
        putLong(generator, ROLE_ID, rs, index++);
    }

    @Override
    public String getTable() {
        return "ORGANIZATION_USER";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO ORGANIZATION_USER (ID, USER_ID, ORGANIZATION_ID, ROLE_ID) " + values(4);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, USER_ID, ORGANIZATION_ID, ROLE_ID FROM ORGANIZATION_USER";
    }
}
