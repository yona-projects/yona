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
public class IssueCommentVoterDataExchanger extends DefaultExchanger {

    private static final String ISSUE_COMMENT_ID = "issue_comment_id";
    private static final String USER_ID = "user_id";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ISSUE_COMMENT_ID).longValue());
        ps.setLong(index++, node.get(USER_ID).longValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ISSUE_COMMENT_ID, rs, index++);
        putLong(generator, USER_ID, rs, index++);
    }

    @Override
    public String getTable() {
        return "ISSUE_COMMENT_VOTER";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO ISSUE_COMMENT_VOTER (ISSUE_COMMENT_ID, USER_ID) " + values(2);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ISSUE_COMMENT_ID, USER_ID FROM ISSUE_COMMENT_VOTER";
    }

    @Override
    protected boolean hasSequence() {
        return false;
    }
}
