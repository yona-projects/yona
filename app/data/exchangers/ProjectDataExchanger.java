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
public class ProjectDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String OVERVIEW = "overview";
    private static final String VCS = "vcs";
    private static final String SITEURL = "siteurl";
    private static final String OWNER = "owner";
    private static final String CREATED_DATE = "created_date";
    private static final String LAST_ISSUE_NUMBER = "last_issue_number";
    private static final String LAST_POSTING_NUMBER = "last_posting_number";
    private static final String ORIGINAL_PROJECT_ID = "original_project_id";
    private static final String LAST_PUSHED_DATE = "last_pushed_date";
    private static final String IS_USING_REVIEWER_COUNT = "is_using_reviewer_count";
    private static final String DEFAULT_REVIEWER_COUNT = "default_reviewer_count";
    private static final String ORGANIZATION_ID = "organization_id";
    private static final String PROJECT_SCOPE = "project_scope";

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(NAME).textValue());
        ps.setString(index++, node.get(OVERVIEW).textValue());
        ps.setString(index++, node.get(VCS).textValue());
        ps.setString(index++, node.get(SITEURL).textValue());
        ps.setString(index++, node.get(OWNER).textValue());
        ps.setTimestamp(index++, timestamp(node.get(CREATED_DATE).longValue()));
        ps.setLong(index++, node.get(LAST_ISSUE_NUMBER).longValue());
        ps.setLong(index++, node.get(LAST_POSTING_NUMBER).longValue());
        setNullableLong(ps, index++, node, ORIGINAL_PROJECT_ID);
        ps.setTimestamp(index++, timestamp(node.get(LAST_PUSHED_DATE).longValue()));
        ps.setBoolean(index++, node.get(IS_USING_REVIEWER_COUNT).booleanValue());
        ps.setLong(index++, node.get(DEFAULT_REVIEWER_COUNT).longValue());
        setNullableLong(ps, index++, node, ORGANIZATION_ID);
        ps.setString(index++, node.get(PROJECT_SCOPE).textValue());
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, NAME, rs, index++);
        putString(generator, OVERVIEW, rs, index++);
        putString(generator, VCS, rs, index++);
        putString(generator, SITEURL, rs, index++);
        putString(generator, OWNER, rs, index++);
        putTimestamp(generator, CREATED_DATE, rs, index++);
        putLong(generator, LAST_ISSUE_NUMBER, rs, index++);
        putLong(generator, LAST_POSTING_NUMBER, rs, index++);
        putLong(generator, ORIGINAL_PROJECT_ID, rs, index++);
        putTimestamp(generator, LAST_PUSHED_DATE, rs, index++);
        putBoolean(generator, IS_USING_REVIEWER_COUNT, rs, index++);
        putLong(generator, DEFAULT_REVIEWER_COUNT, rs, index++);
        putLong(generator, ORGANIZATION_ID, rs, index++);
        putString(generator, PROJECT_SCOPE, rs, index++);
    }

    @Override
    public String getTable() {
        return "PROJECT";
    }

    @Override
    protected String getInsertSql() {
        return  "INSERT INTO PROJECT (ID, NAME, OVERVIEW, VCS, SITEURL, OWNER, CREATED_DATE, LAST_ISSUE_NUMBER, " +
                "LAST_POSTING_NUMBER, ORIGINAL_PROJECT_ID, LAST_PUSHED_DATE, IS_USING_REVIEWER_COUNT, " +
                "DEFAULT_REVIEWER_COUNT, ORGANIZATION_ID, PROJECT_SCOPE) " + values(15);
    }

    @Override
    protected String getSelectSql() {
        return  "SELECT ID, NAME, OVERVIEW, VCS, SITEURL, OWNER, CREATED_DATE, LAST_ISSUE_NUMBER, " +
                "LAST_POSTING_NUMBER, ORIGINAL_PROJECT_ID, LAST_PUSHED_DATE, IS_USING_REVIEWER_COUNT, " +
                "DEFAULT_REVIEWER_COUNT, ORGANIZATION_ID, PROJECT_SCOPE FROM PROJECT";
    }
}
