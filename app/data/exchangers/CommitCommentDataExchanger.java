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
 * @author Yi EungJun
 */
public class CommitCommentDataExchanger extends DefaultExchanger {
    private static final String ID = "id";
    private static final String PROJECT_ID = "project_id";
    private static final String COMMIT_ID = "commit_id";
    private static final String PATH = "path";
    private static final String LINE = "line";
    private static final String SIDE = "side";
    private static final String CREATED_DATE = "created_date";
    private static final String AUTHOR_ID = "author_id";
    private static final String AUTHOR_LOGIN_ID = "author_login_id";
    private static final String AUTHOR_NAME = "author_name";
    private static final String CONTENTS = "contents";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        setNullableLong(ps, index++, node, PROJECT_ID);
        ps.setString(index++, node.get(COMMIT_ID).textValue());
        ps.setString(index++, node.get(PATH).textValue());
        setNullableLong(ps, index++, node, LINE);
        ps.setString(index++, node.get(SIDE).textValue());
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue()));
        setNullableLong(ps, index++, node, AUTHOR_ID);
        ps.setString(index++, node.get(AUTHOR_LOGIN_ID).textValue());
        ps.setString(index++, node.get(AUTHOR_NAME).textValue());
        setClob(ps, index++, node, CONTENTS);
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putLong(generator, PROJECT_ID, rs, index++);
        putString(generator, COMMIT_ID, rs, index++);
        putString(generator, PATH, rs, index++);
        putLong(generator, LINE, rs, index++);
        putString(generator, SIDE, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putLong(generator, AUTHOR_ID, rs, index++);
        putString(generator, AUTHOR_LOGIN_ID, rs, index++);
        putString(generator, AUTHOR_NAME, rs, index++);
        putClob(generator, CONTENTS, rs, index++);
    }

    @Override
    public String getTable() {
        return "COMMIT_COMMENT";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO COMMIT_COMMENT " +
                "(ID, PROJECT_ID, COMMIT_ID, PATH, LINE, SIDE, CREATED_DATE, " +
                "AUTHOR_ID, AUTHOR_LOGIN_ID, AUTHOR_NAME, CONTENTS) " + values(11);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, PROJECT_ID, COMMIT_ID, PATH, LINE, SIDE, CREATED_DATE, " +
                "AUTHOR_ID, AUTHOR_LOGIN_ID, AUTHOR_NAME, CONTENTS FROM COMMIT_COMMENT";
    }
}
