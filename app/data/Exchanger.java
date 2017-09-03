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
package data;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;

/**
 * @author Keeun Baik
 */
public interface Exchanger {

    /**
     * The thable name is used when exporting and importing data.
     * YOU SHOULD RETURN DATABASE TABLE NAME NOT ENTITY NAME.
     *
     * @return table name
     */
    String getTable();

    /**
     * Read data from database with {@code jdbcTemplate}
     * and write it as json with {@code generator}.
     *
     * You should make a json with a field name and an array. like:
     * {"users": [{"id":1, "loginId":"keesun}, {"id":2, "loginId":"doortts"}]}
     *
     * The field name should be a table name and an entity inside the array represents a row.
     * The one json node inside the json array, has exactly same field name and value with the row.
     * As a result, YOU SHOULD CHANGE THE IMPLEMENTATIONS IF YOU HAVE CHANGED THE DB SCHEMA OR MAPPING.
     *
     * @param dbName
     * @param generator
     * @param jdbcTemplate
     * @throws IOException
     */
    void exportData(String dbName, String catalogName, JsonGenerator generator, JdbcTemplate jdbcTemplate) throws IOException;

    /**
     * Read data from a {@code parser}, and write the data into database with {@code jdbcTemplate}.
     *
     * This operation assumes that the sequence of the json data which will be loaded by {@code parser}
     * is exactly same with exported data with {@link #exportData(String, String, JsonGenerator, JdbcTemplate)}.
     *
     * YOU SHOULD BACKUP DATABASE BEFORE USING THIS OPERATION, because this operation usually
     * truncate existing table and insert all data read from the {@code jdbcTemplate}, in some cases,
     * you can lose all data or break referential integrity.
     *
     * @param parser
     * @param jdbcTemplate
     * @throws IOException
     */
    void importData(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOException;
}
