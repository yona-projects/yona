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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.exchangers.*;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.dao.CleanupFailureDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import play.Configuration;
import play.db.DB;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Keeun Baik
 */
@Service
public class DataService {

    private List<Exchanger> exchangers;

    private static final Comparator<Exchanger> COMPARATOR = new Comparator<Exchanger>() {
        @Override
        public int compare(Exchanger ex1, Exchanger ex2) {
            return ex1.getTable().compareTo(ex2.getTable());
        }
    };

    String dataSourceName;

    public DataService() {
        exchangers = new ArrayList<>();
        exchangers.add(new AssigneeDataExchanger());
        exchangers.add(new AttachmentDataExchanger());
        exchangers.add(new CommentThreadDataExchanger());
        exchangers.add(new CommentThreadUserDataExchanger());
        exchangers.add(new CommitCommentDataExchanger());
        exchangers.add(new EmailDataExchanger());
        exchangers.add(new IssueCommentDataExchanger());
        exchangers.add(new IssueCommentVoterDataExchanger());
        exchangers.add(new IssueDataExchanger());
        exchangers.add(new IssueEventDataExchanger());
        exchangers.add(new IssueIssueLabelDataExchanger());
        exchangers.add(new IssueLabelCategoryDataExchanger());
        exchangers.add(new IssueLabelDataExchanger());
        exchangers.add(new IssueVoterDataExchanger());
        exchangers.add(new LabelDataExchanger());
        exchangers.add(new MentionDataExchanger());
        exchangers.add(new MilestoneDataExchanger());
        exchangers.add(new NotificationEventDataExchanger());
        exchangers.add(new NotificationEventUserDataExchanger());
        exchangers.add(new NotificationMailDataExchanger());
        exchangers.add(new OrganizationDataExchanger());
        exchangers.add(new OrganizationUserDataExchanger());
        exchangers.add(new OriginalEmailDataExchanger());
        exchangers.add(new PostingCommentDataExchanger());
        exchangers.add(new PostingDataExchanger());
        exchangers.add(new ProjectDataExchanger());
        exchangers.add(new ProjectLabelDataExchanger());
        exchangers.add(new ProjectMenuDataExchanger());
        exchangers.add(new ProjectPushedBranchDataExchanger());
        exchangers.add(new ProjectTransferDataExchanger());
        exchangers.add(new ProjectUserDataExchanger());
        exchangers.add(new ProjectVisitationDataExchanger());
        exchangers.add(new PropertyDataExchanger());
        exchangers.add(new PullRequestCommitDataExchanger());
        exchangers.add(new PullRequestDataExchanger());
        exchangers.add(new PullRequestEventDataExchanger());
        exchangers.add(new PullRequestReviewersDataExchanger());
        exchangers.add(new RecentlyVisitedProjectsDataExchanger());
        exchangers.add(new ReviewCommentDataExchanger());
        exchangers.add(new RoleDataExchanger());
        exchangers.add(new SiteAdminDataExchanger());
        exchangers.add(new UnwatchDataExchanger());
        exchangers.add(new UserDataExchanger());
        exchangers.add(new UserEnrolledOrganizationDataExchanger());
        exchangers.add(new UserEnrolledProjectDataExchanger());
        exchangers.add(new UserProjectNotificationDataExchanger());
        exchangers.add(new WatchDataExchanger());
        Collections.sort(exchangers, COMPARATOR);
        dataSourceName = Configuration.root().getString("ebeanconfig.datasource.default", "default");
    }

    public InputStream exportData() {
        final DateTime start = DateTime.now();
        DataSource dataSource = DB.getDataSource(dataSourceName);
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        final String dbName = getDBName(dataSource);
        final String catalogName = getCatalogName(dataSource);
        ObjectMapper mapper = getObjectMapper();
        final JsonFactory factory = mapper.getFactory();

        PipedInputStream in = new PipedInputStream();
        try {
            final PipedOutputStream out = new PipedOutputStream(in);
            new Thread(
                new Runnable() {
                    public void run () {
                        try {
                            JsonGenerator generator = factory.createGenerator(out, JsonEncoding.UTF8);
                            generator.setPrettyPrinter(new DefaultPrettyPrinter());
                            generator.writeStartObject();

                            for (Exchanger exchanger : exchangers) {
                                exchanger.exportData(dbName, catalogName, generator, jdbcTemplate);
                            }

                            generator.writeEndObject();
                            generator.close();

                            DateTime end = DateTime.now();
                            Duration duration = new Duration(start, end);
                            play.Logger.info("Data export took {{}}", duration.getStandardSeconds());
                        }
                        catch (IOException e) {
                            play.Logger.error("Failed to export data");
                        }
                    }
                }
            ).start();
            return in;
        } catch (IOException e) {
            play.Logger.error("Failed to export data");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String getCatalogName(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return connection.getCatalog();
        } catch (SQLException e) {
            throw new CannotGetJdbcConnectionException("failed to get connection", e);
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (SQLException e) {
                    throw new CleanupFailureDataAccessException("failed to close connection", e);
                }
            }
        }
    }

    public void importData(File file) throws IOException {
        DateTime start = DateTime.now();
        ObjectMapper mapper = getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        JsonParser parser = factory.createParser(file);

        JsonToken current = parser.nextToken();
        if (current != JsonToken.START_OBJECT) {
            play.Logger.info("Data import failed cause of root if not an object.");
            return;
        }

        DataSource dataSource = DB.getDataSource(dataSourceName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String dbName = getDBName(dataSource);
        disableReferentialIntegtiry(dbName, jdbcTemplate);
        String message = "";
        try {
            for (Exchanger exchanger : exchangers) {
                exchanger.importData(dbName, parser, jdbcTemplate);
            }
            message = "Data import done. it took {{}}";
        } catch (Exception e) {
            message = "Data import failed. it took {{}}";
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            enableReferentialIntegrity(dbName, jdbcTemplate);
            DateTime end = DateTime.now();
            Duration duration = new Duration(start, end);
            play.Logger.info(message, duration.getStandardSeconds());
        }
    }

    private String getDBName(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            throw new CannotGetJdbcConnectionException("failed to get meta data", e);
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (SQLException e) {
                    throw new CleanupFailureDataAccessException("failed to close connection", e);
                }
            }
        }
    }

    private void enableReferentialIntegrity(String dbName, JdbcTemplate jdbcTemplate) {
        if (dbName.equals("H2")) {
            for (Exchanger exchanger : exchangers) {
                jdbcTemplate.update("ALTER TABLE " + exchanger.getTable() + " SET REFERENTIAL_INTEGRITY TRUE");
            }
            jdbcTemplate.update("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private void disableReferentialIntegtiry(String dbName, JdbcTemplate jdbcTemplate) {
        if(dbName.equals("H2")) {
            jdbcTemplate.update("SET REFERENTIAL_INTEGRITY FALSE");
            for (Exchanger exchanger : exchangers) {
                jdbcTemplate.update("ALTER TABLE " + exchanger.getTable() + " SET REFERENTIAL_INTEGRITY FALSE");
            }
        }
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // prevent serializing null property to a json field
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // use filed access only
        mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        return mapper;
    }

}
