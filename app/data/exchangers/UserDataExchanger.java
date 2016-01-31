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
public class UserDataExchanger extends DefaultExchanger {

    private static final String TABLE = "n4user";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String LOGIN_ID = "login_id";
    private static final String PASSWORD = "password";
    private static final String PASSWORD_SALT = "password_salt";
    private static final String EMAIL = "email";
    private static final String REMEMBER_ME = "remember_me";
    private static final String CREATED_DATE = "created_date";
    private static final String STATE = "state";
    private static final String LAST_STATE_MODIFIED_DATE = "last_state_modified_date";
    private static final String LANG = "lang";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(NAME).textValue());
        ps.setString(index++, node.get(LOGIN_ID).textValue());
        ps.setString(index++, node.get(PASSWORD).textValue());
        ps.setString(index++, node.get(PASSWORD_SALT).textValue());
        ps.setString(index++, node.get(EMAIL).textValue());
        ps.setBoolean(index++, node.get(REMEMBER_ME).booleanValue());
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue()));
        ps.setString(index++, node.get(STATE).textValue());
        ps.setTimestamp(index++, timestamp(node.get(LAST_STATE_MODIFIED_DATE).longValue()));
        ps.setString(index++, node.get(LANG).textValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, NAME, rs, index++);
        putString(generator, LOGIN_ID, rs, index++);
        putString(generator, PASSWORD, rs, index++);
        putString(generator, PASSWORD_SALT, rs, index++);
        putString(generator, EMAIL, rs, index++);
        putBoolean(generator, REMEMBER_ME, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putString(generator, STATE, rs, index++);
        putTimestamp(generator, LAST_STATE_MODIFIED_DATE, rs, index++);
        putString(generator, LANG, rs, index++);
    }

    @Override
    public String getTable() {
        return "N4USER";
    }

    @Override
    protected String getInsertSql() {
        return  "INSERT INTO N4USER (ID, NAME, LOGIN_ID, PASSWORD, PASSWORD_SALT, EMAIL, REMEMBER_ME, " +
                "CREATED_DATE, STATE, LAST_STATE_MODIFIED_DATE, LANG) " + values(11);
    }

    @Override
    protected String getSelectSql() {
        return  "SELECT ID, NAME, LOGIN_ID, PASSWORD, PASSWORD_SALT, EMAIL, REMEMBER_ME, CREATED_DATE, " +
                "STATE, LAST_STATE_MODIFIED_DATE, LANG FROM N4USER";
    }
}
