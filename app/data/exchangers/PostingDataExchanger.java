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
public class PostingDataExchanger extends DefaultExchanger {
    private static final String ID = "id";  //BIGINT  nullable? 0
    private static final String TITLE = "title";  //VARCHAR  nullable? 1
    private static final String BODY = "body";  //CLOB  nullable? 1
    private static final String CREATED_DATE = "created_date";  //TIMESTAMP  nullable? 1
    private static final String NUM_OF_COMMENTS = "num_of_comments";  //INTEGER  nullable? 1
    private static final String AUTHOR_ID = "author_id";  //BIGINT  nullable? 1
    private static final String AUTHOR_LOGIN_ID = "author_login_id";  //VARCHAR  nullable? 1
    private static final String AUTHOR_NAME = "author_name";  //VARCHAR  nullable? 1
    private static final String PROJECT_ID = "project_id";  //BIGINT  nullable? 1
    private static final String NUMBER = "number";  //BIGINT  nullable? 1
    private static final String NOTICE = "notice";  //BOOLEAN  nullable? 1
    private static final String UPDATED_DATE = "updated_date";  //TIMESTAMP  nullable? 1
    private static final String README = "readme";  //BOOLEAN  nullable? 1

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue()); //BIGINT  nullable? 0
        ps.setString(index++, node.get(TITLE).textValue()); //VARCHAR  nullable? 1
        setClob(ps, index++, node, BODY); //CLOB  nullable? 1
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue()));
        ps.setInt(index++, node.get(NUM_OF_COMMENTS).intValue());
        ps.setLong(index++, node.get(AUTHOR_ID).longValue());
        ps.setString(index++, node.get(AUTHOR_LOGIN_ID).textValue());
        ps.setString(index++, node.get(AUTHOR_NAME).textValue());
        ps.setLong(index++, node.get(PROJECT_ID).longValue());
        ps.setLong(index++, node.get(NUMBER).longValue());
        ps.setBoolean(index++, node.get(NOTICE).booleanValue()); //BOOLEAN  nullable? 1
        ps.setTimestamp(index++, timestamp(node.get(UPDATED_DATE).longValue()));
        ps.setBoolean(index++, node.get(README).booleanValue()); //BOOLEAN  nullable? 1
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, TITLE, rs, index++);
        putClob(generator, BODY, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putInt(generator, NUM_OF_COMMENTS, rs, index++);
        putLong(generator, AUTHOR_ID, rs, index++);
        putString(generator, AUTHOR_LOGIN_ID, rs, index++);
        putString(generator, AUTHOR_NAME, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putLong(generator, NUMBER, rs, index++);
        putBoolean(generator, NOTICE, rs, index++);
        putTimestamp(generator, UPDATED_DATE, rs, index++);
        putBoolean(generator, README, rs, index++);
    }

    @Override
    public String getTable() {
        return "POSTING";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO POSTING (ID, TITLE, BODY, CREATED_DATE, NUM_OF_COMMENTS, AUTHOR_ID, AUTHOR_LOGIN_ID, " +
                "AUTHOR_NAME, PROJECT_ID, NUMBER, NOTICE, UPDATED_DATE, README) " + values(13);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, TITLE, BODY, CREATED_DATE, NUM_OF_COMMENTS, AUTHOR_ID, AUTHOR_LOGIN_ID, " +
                "AUTHOR_NAME, PROJECT_ID, NUMBER, NOTICE, UPDATED_DATE, README FROM POSTING";
    }
}
