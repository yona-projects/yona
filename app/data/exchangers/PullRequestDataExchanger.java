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
public class PullRequestDataExchanger extends DefaultExchanger {

    private static final String ID = "id"; // BIGINT(19) NOT NULL
    private static final String TITLE = "title"; // VARCHAR(255)
    private static final String BODY = "body"; // CLOB(2147483647)
    private static final String TO_PROJECT_ID = "to_project_id"; // BIGINT(19)
    private static final String FROM_PROJECT_ID = "from_project_id"; // BIGINT(19)
    private static final String TO_BRANCH = "to_branch"; // VARCHAR(255)
    private static final String FROM_BRANCH = "from_branch"; // VARCHAR(255)
    private static final String CONTRIBUTOR_ID = "contributor_id"; // BIGINT(19)
    private static final String RECEIVER_ID = "receiver_id"; // BIGINT(19)
    private static final String CREATED = "created"; // TIMESTAMP(23, 10)
    private static final String UPDATED = "updated"; // TIMESTAMP(23, 10)
    private static final String RECEIVED = "received"; // TIMESTAMP(23, 10)
    private static final String STATE = "state"; // INTEGER(10)
    private static final String LAST_COMMIT_ID = "last_commit_id"; //VARCHAR(255)
    private static final String MERGED_COMMIT_ID_FROM = "merged_commit_id_from"; // VARCHAR(255)
    private static final String MERGED_COMMIT_ID_TO = "merged_commit_id_to"; // VARCHAR(255)
    private static final String NUMBER = "number"; // BIGINT(19)
    private static final String IS_CONFLICT = "is_conflict"; // BOOLEAN(1)
    private static final String IS_MERGING = "is_merging"; // BOOLEAN(1)

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(TITLE).textValue());
        setClob(ps, index++, node, BODY);
        ps.setLong(index++, node.get(TO_PROJECT_ID).longValue());
        ps.setLong(index++, node.get(FROM_PROJECT_ID).longValue());
        ps.setString(index++, node.get(TO_BRANCH).textValue());
        ps.setString(index++, node.get(FROM_BRANCH).textValue());
        setNullableLong(ps, index++, node, CONTRIBUTOR_ID);
        setNullableLong(ps, index++, node, RECEIVER_ID);
        ps.setTimestamp(index++, timestamp(node.get(CREATED).longValue()));
        ps.setTimestamp(index++, timestamp(node.get(UPDATED).longValue()));
        ps.setTimestamp(index++, timestamp(node.get(RECEIVED).longValue()));
        ps.setInt(index++, node.get(STATE).intValue());
        ps.setString(index++, node.get(LAST_COMMIT_ID).textValue());
        ps.setString(index++, node.get(MERGED_COMMIT_ID_FROM).textValue());
        ps.setString(index++, node.get(MERGED_COMMIT_ID_TO).textValue());
        ps.setLong(index++, node.get(NUMBER).longValue());
        ps.setBoolean(index++, node.get(IS_CONFLICT).booleanValue());
        ps.setBoolean(index++, node.get(IS_MERGING).booleanValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, TITLE, rs, index++);
        putClob(generator, BODY, rs, index++);
        putLong(generator, TO_PROJECT_ID, rs, index++);
        putLong(generator, FROM_PROJECT_ID, rs, index++);
        putString(generator, TO_BRANCH, rs, index++);
        putString(generator, FROM_BRANCH, rs, index++);
        putLong(generator, CONTRIBUTOR_ID, rs, index++);
        putLong(generator, RECEIVER_ID, rs, index++);
        putTimestamp(generator, CREATED, rs, index++);
        putTimestamp(generator, UPDATED, rs, index++);
        putTimestamp(generator, RECEIVED, rs, index++);
        putInt(generator, STATE, rs, index++);
        putString(generator, LAST_COMMIT_ID, rs, index++);
        putString(generator, MERGED_COMMIT_ID_FROM, rs, index++);
        putString(generator, MERGED_COMMIT_ID_TO, rs, index++);
        putLong(generator, NUMBER, rs, index++);
        putBoolean(generator, IS_CONFLICT, rs, index++);
        putBoolean(generator, IS_MERGING, rs, index++);
    }

    @Override
    public String getTable() {
        return "PULL_REQUEST";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO PULL_REQUEST (ID, TITLE, BODY, TO_PROJECT_ID, FROM_PROJECT_ID, " +
                "TO_BRANCH, FROM_BRANCH, CONTRIBUTOR_ID, RECEIVER_ID, CREATED, UPDATED, RECEIVED, " +
                "STATE, LAST_COMMIT_ID, MERGED_COMMIT_ID_FROM, MERGED_COMMIT_ID_TO, NUMBER, IS_CONFLICT, IS_MERGING) " +
                values(19);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, TITLE, BODY, TO_PROJECT_ID, FROM_PROJECT_ID, TO_BRANCH, FROM_BRANCH, " +
                "CONTRIBUTOR_ID, RECEIVER_ID, CREATED, UPDATED, RECEIVED, STATE, LAST_COMMIT_ID, " +
                "MERGED_COMMIT_ID_FROM, MERGED_COMMIT_ID_TO, NUMBER, IS_CONFLICT, IS_MERGING FROM PULL_REQUEST";
    }
}
