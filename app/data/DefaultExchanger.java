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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.IOUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import play.Configuration;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Keeun Baik
 */
public abstract class DefaultExchanger implements Exchanger {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private final static String DATA_BATCH_SIZE_KEY = "data.batch.size";

    protected Long timestamp(Timestamp timestamp) {
        if (timestamp != null) {
            return timestamp.getTime();
        }
        else {
            return null;
        }
    }

    protected Long date(Date date) {
        if (date != null) {
            return date.getTime();
        }
        else {
            return null;
        }
    }

    protected Timestamp timestamp(long time) {
        if (time == 0l) {
            return null;
        } else {
            return new Timestamp(time);
        }
    }

    protected Date date(long time) {
        if (time == 0l) {
            return null;
        } else {
            return new Date(time);
        }
    }

    protected void setNullableLong(PreparedStatement ps, short index, JsonNode node, String column) throws SQLException {
        if (node.get(column).isNull()) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, node.get(column).longValue());
        }
    }

    protected String clobString(@Nullable Clob clob) throws SQLException {
        if (clob == null) {
            return null;
        }
        Reader reader = clob.getCharacterStream();
        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(reader, writer);
            return writer.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected void setClob(PreparedStatement ps, short index, JsonNode node, String column) throws SQLException {
        String value = node.get(column).textValue();
        if (value == null) {
            ps.setNull(index, Types.CLOB);
        } else {
            Clob clob = ps.getConnection().createClob();
            clob.setString(1, value);
            ps.setClob(index, clob);
        }
    }

    /**
     * generates VALUES part of a sql like, VALUES (?, ?, ?)
     *
     * @param size
     * @return
     */
    protected String values(int size) {
        String values = "VALUES (";
        for(int i = 0 ; i < size - 1 ; i++) {
            values += "?, ";
        }
        values += "?)";
        return values;
    }

    protected void putLong(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        long value = rs.getLong(index);
        if (rs.wasNull()) {
            generator.writeNull();
        } else {
            generator.writeNumber(value);
        }
    }

    protected void putInt(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        int value = rs.getInt(index);
        if (rs.wasNull()) {
            generator.writeNull();
        } else {
            generator.writeNumber(value);
        }
    }

    protected void putString(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        String string = rs.getString(index);
        if (string == null) {
            generator.writeNull();
        } else {
            generator.writeString(string);
        }
    }

    protected void putBoolean(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        generator.writeBoolean(rs.getBoolean(index));
    }

    protected void putTimestamp(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        Timestamp timestamp = rs.getTimestamp(index);
        if (timestamp == null) {
            generator.writeNull();
        } else {
            generator.writeNumber(timestamp.getTime());
        }
    }

    protected void putDate(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        Date date = rs.getDate(index);
        if (date == null) {
            generator.writeNull();
        } else {
            generator.writeNumber(date.getTime());
        }
    }

    protected void putClob(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws SQLException, IOException {
        generator.writeFieldName(fieldName);
        String clobString = clobString(rs.getClob(index));
        if (clobString == null) {
            generator.writeNull();
        } else {
            generator.writeString(clobString);
        }
    }

    public void exportData(String dbName, String catalogName, final JsonGenerator generator, JdbcTemplate jdbcTemplate) throws IOException {
        generator.writeFieldName(getTable());
        generator.writeStartArray();
        final int[] rowCount = {0};
        jdbcTemplate.query(getSelectSql(), new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                try {
                    generator.writeStartObject();
                    setNode(generator, rs);
                    generator.writeEndObject();
                    rowCount[0]++;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
        generator.writeEndArray();
        play.Logger.info("exported {{}} {}", rowCount[0], getTable());

        if (hasSequence()) {
            String sequenceName = sequenceName();
            long sequenceValue = 0;
            if (dbName.equalsIgnoreCase("MySQL")) {
                String sql = String.format("SELECT `AUTO_INCREMENT` FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'", catalogName, getTable());
                sequenceValue = jdbcTemplate.queryForObject(sql, Long.class);
            } else if (dbName.equalsIgnoreCase("H2")) {
                sequenceValue = jdbcTemplate.queryForObject("CALL NEXT VALUE FOR " + sequenceName, Long.class);
            }
            generator.writeFieldName(sequenceName);
            generator.writeNumber(sequenceValue);
            play.Logger.info("exported sequence {{}}", sequenceName());
        }
    }

    public void importData(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOException {
        PlatformTransactionManager tm = new DataSourceTransactionManager(jdbcTemplate.getDataSource());
        TransactionStatus ts = tm.getTransaction(new DefaultTransactionDefinition());

        try {
            if (dbName.equals("MySQL")) {
                jdbcTemplate.update("SET FOREIGN_KEY_CHECKS = 0");
                jdbcTemplate.update("SET NAMES \'utf8mb4\'");
            }

            final Configuration config = Configuration.root();
            int batchSize = config.getInt(DATA_BATCH_SIZE_KEY, DEFAULT_BATCH_SIZE);
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                play.Logger.debug("importing {}", fieldName);
                if (fieldName.equalsIgnoreCase(getTable())) {
                    truncateTable(jdbcTemplate);
                    JsonToken current = parser.nextToken();
                    if (current == JsonToken.START_ARRAY) {
                        importDataFromArray(parser, jdbcTemplate, batchSize);
                        importSequence(dbName, parser, jdbcTemplate);
                    } else {
                        play.Logger.info("Error: records should be an array: skipping.");
                        parser.skipChildren();
                    }
                }
            }
            tm.commit(ts);
        } catch (Exception e) {
            e.printStackTrace();
            tm.rollback(ts);
        } finally {
            if (dbName.equals("MySQL")) {
                jdbcTemplate.update("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private void importSequence(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOException {
        if (hasSequence()) {
            JsonToken fieldNameToken = parser.nextToken();
            if (fieldNameToken == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                if (fieldName.equalsIgnoreCase(sequenceName())) {
                    JsonToken current = parser.nextToken();
                    if (current == JsonToken.VALUE_NUMBER_INT) {
                        long sequenceValue = parser.getNumberValue().longValue();
                        if (dbName.equals("MySQL")) {
                            jdbcTemplate.execute("ALTER TABLE " + getTable() + " AUTO_INCREMENT = " + sequenceValue);
                        } else if (dbName.equals("H2")) {
                            jdbcTemplate.execute("ALTER SEQUENCE " + sequenceName() + " RESTART WITH " + sequenceValue);
                        }
                    }
                }
            }
            play.Logger.info("imported sequence {{}}", sequenceName());
        }
    }

    private void importDataFromArray(JsonParser parser, JdbcTemplate jdbcTemplate, int batchSize) throws IOException {
        int importedNodesCount = 0;
        final List<JsonNode> nodes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            final JsonNode node = parser.readValueAsTree();
            nodes.add(node);
            if (nodes.size() == batchSize) {
                importedNodesCount += batchUpdate(jdbcTemplate, nodes).length;
                nodes.clear();
            }
        }
        if (nodes.size() > 0) {
            importedNodesCount += batchUpdate(jdbcTemplate, nodes).length;
        }
        play.Logger.info("imported {{}} {}", importedNodesCount, getTable());
    }

    private void truncateTable(JdbcTemplate jdbcTemplate) {
        play.Logger.debug("truncate table {}", getTable());
        jdbcTemplate.execute("TRUNCATE TABLE " + getTable());
        play.Logger.debug("truncated table {}", getTable());
    }

    private int[] batchUpdate(JdbcTemplate jdbcTemplate, final List<JsonNode> nodes) {
        int[] updateCounts = jdbcTemplate.batchUpdate(getInsertSql(), new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                setPreparedStatement(ps, nodes.get(i));
            }

            @Override
            public int getBatchSize() {
                return nodes.size();
            }
        });
        return updateCounts;
    }

    /**
     * This method is used when importing data.
     * Set a preparedStatement with a JsonNode.
     *
     * @param ps
     * @param node
     * @throws SQLException
     * @see #importData(JsonParser, JdbcTemplate)
     */
    abstract protected void setPreparedStatement(PreparedStatement ps, JsonNode node) throws SQLException;

    /**
     * Thia method is used when exporting data.
     * Set a node with JsonGenerator from a ResultSet.
     *
     * @param generator
     * @param rs
     * @throws IOException
     * @throws SQLException
     * @see #exportData(JsonGenerator, JdbcTemplate)
     */
    abstract protected void setNode(JsonGenerator generator, ResultSet rs) throws IOException, SQLException;

    /**
     * Insert sql is used when importing data.
     *
     * @return insertion sql
     */
    abstract protected String getInsertSql();

    /**
     * Select sql is used when exporting data
     *
     * @return selection sql
     */
    abstract protected String getSelectSql();

    protected boolean hasSequence() {
        return true;
    }

    protected String sequenceName() {
        return getTable() + "_SEQ";
    }

}
