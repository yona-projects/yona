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
 * @author Suwon Chae
 */
public class PostingCommentDataExchanger extends DefaultExchanger {
    private static final String ID = "id";  //BIGINT  nullable? 0
    private static final String CREATED_DATE = "created_date";  //TIMESTAMP  nullable? 1
    private static final String AUTHOR_ID = "author_id";  //BIGINT  nullable? 1
    private static final String AUTHOR_LOGIN_ID = "author_login_id";  //VARCHAR  nullable? 1
    private static final String AUTHOR_NAME = "author_name";  //VARCHAR  nullable? 1
    private static final String POSTING_ID = "posting_id";  //BIGINT  nullable? 1
    private static final String CONTENTS = "contents";  //CLOB  nullable? 1

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue()); //BIGINT  nullable? 0
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue())); //TIMESTAMP  nullable? 1
        setNullableLong(ps, index++, node, AUTHOR_ID); //BIGINT  nullable? 1
        ps.setString(index++, node.get(AUTHOR_LOGIN_ID).textValue()); //VARCHAR  nullable? 1
        ps.setString(index++, node.get(AUTHOR_NAME).textValue()); //VARCHAR  nullable? 1
        setNullableLong(ps, index++, node, POSTING_ID); //BIGINT  nullable? 1
        setClob(ps, index++, node, CONTENTS); //CLOB  nullable? 1
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putLong(generator, AUTHOR_ID, rs, index++);
        putString(generator, AUTHOR_LOGIN_ID, rs, index++);
        putString(generator, AUTHOR_NAME, rs, index++);
        putLong(generator, POSTING_ID, rs, index++);
        putClob(generator, CONTENTS, rs, index++);
    }

    @Override
    public String getTable() {
        return "POSTING_COMMENT";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO POSTING_COMMENT (ID, CREATED_DATE, AUTHOR_ID, AUTHOR_LOGIN_ID, AUTHOR_NAME, " +
                "POSTING_ID, CONTENTS) " + values(7);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, CREATED_DATE, AUTHOR_ID, AUTHOR_LOGIN_ID, AUTHOR_NAME, POSTING_ID, CONTENTS " +
                "FROM POSTING_COMMENT";
    }
}
