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
public class PullRequestReviewersDataExchanger extends DefaultExchanger {

    private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19) NOT NULL
    private static final String USER_ID = "user_id"; // INTEGER(10) NOT NULL

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(PULL_REQUEST_ID).longValue());
        ps.setLong(index++, node.get(USER_ID).intValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, PULL_REQUEST_ID, rs, index++);
        putLong(generator, USER_ID, rs, index++);
    }

    @Override
    public String getTable() {
        return "PULL_REQUEST_REVIEWERS";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PULL_REQUEST_REVIEWERS (PULL_REQUEST_ID, USER_ID) " + values(2);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT PULL_REQUEST_ID, USER_ID FROM PULL_REQUEST_REVIEWERS";
    }

    @Override
    protected boolean hasSequence() {
        return false;
    }
}
