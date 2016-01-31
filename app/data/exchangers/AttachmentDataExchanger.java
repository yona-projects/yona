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
public class AttachmentDataExchanger extends DefaultExchanger {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String HASH = "hash";
    private static final String CONTAINER_TYPE = "content_type";
    private static final String MIME_TYPE = "mime_type";
    private static final String SIZE = "size";
    private static final String CONTAINER_ID = "container_id";
    private static final String CREATED_DATE = "created_date";

    @Override
    protected boolean hasSequence() {
        return true;
    }

    @Override
    protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException {
        short index = 1;
        ps.setLong(index++, node.get(ID).longValue());
        ps.setString(index++, node.get(NAME).textValue());
        ps.setString(index++, node.get(HASH).textValue());
        ps.setString(index++, node.get(CONTAINER_TYPE).textValue());
        ps.setString(index++, node.get(MIME_TYPE).textValue());
        ps.setLong(index++, node.get(SIZE).longValue());
        ps.setString(index++, node.get(CONTAINER_ID).textValue());
        ps.setDate(index++, date(node.get(CREATED_DATE).longValue()));
    }

    @Override
    protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException {
        short index = 1;
        putLong(generator, ID, rs, index++);
        putString(generator, NAME, rs, index++);
        putString(generator, HASH, rs, index++);
        putString(generator, CONTAINER_TYPE, rs, index++);
        putString(generator, MIME_TYPE, rs, index++);
        putLong(generator, SIZE, rs, index++);
        putString(generator, CONTAINER_ID, rs, index++);
        putDate(generator, CREATED_DATE, rs, index++);
    }

    @Override
    public String getTable() {
        return "ATTACHMENT";
    }

    @Override
    protected String getInsertSql() {
        return "INSERT INTO ATTACHMENT (ID, NAME, HASH, CONTAINER_TYPE, MIME_TYPE, SIZE, CONTAINER_ID, " +
                "CREATED_DATE) " + values(8);
    }

    @Override
    protected String getSelectSql() {
        return "SELECT ID, NAME, HASH, CONTAINER_TYPE, MIME_TYPE, SIZE, CONTAINER_ID, CREATED_DATE " +
                "FROM ATTACHMENT";
    }
}
