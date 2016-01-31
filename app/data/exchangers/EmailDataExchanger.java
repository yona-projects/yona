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
public class EmailDataExchanger extends DefaultExchanger {
    private static final String ID = "id";  //BIGINT  nullable? 0
    private static final String USER_ID = "user_id";  //BIGINT  nullable? 1
    private static final String EMAIL = "email";  //VARCHAR  nullable? 1
    private static final String TOKEN = "token";  //VARCHAR  nullable? 1
    private static final String VALID = "valid";  //BOOLEAN  nullable? 1

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue()); //BIGINT  nullable? 0
        ps.setLong(index++, node.get(USER_ID).longValue()); //BIGINT  nullable? 1
        ps.setString(index++, node.get(EMAIL).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(TOKEN).textValue()); //VARCHAR  nullable? 1
        ps.setBoolean(index++, node.get(VALID).booleanValue()); //BOOLEAN  nullable? 1
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, USER_ID, rs, index++);
        putString(generator, EMAIL, rs, index++);
        putString(generator, TOKEN, rs, index++);
        putBoolean(generator, VALID, rs, index++);
    }

    @Override
    public String getTable() {
        return "EMAIL";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO EMAIL (ID, USER_ID, EMAIL, TOKEN, VALID)" + values(5);
    }

    @Override
    protected String getSelectSql() {
        return "select ID, USER_ID, EMAIL, TOKEN, VALID from EMAIL";
    }
}
