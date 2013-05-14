package controllers;

import models.Project;
import models.Tag;
import models.User;
import org.codehaus.jackson.JsonNode;
import org.junit.BeforeClass;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Result;
import play.test.Helpers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class ProjectAppTest {
    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Test
    public void tag() {
        running(fakeApplication(Helpers.inMemoryDatabase()), new Runnable() {
            public void run() {
                //Given
                Map<String,String> data = new HashMap<String,String>();
                data.put("category", "OS");
                data.put("name", "linux");
                User admin = User.findByLoginId("admin");

                //When
                Result result = callAction(
                        controllers.routes.ref.ProjectApp.tag("hobi", "nForge4java"),
                        fakeRequest()
                                .withFormUrlEncodedBody(data)
                                .withHeader("Accept", "application/json")
                                .withSession(UserApp.SESSION_USERID, admin.id.toString())
                );

                //Then
                assertThat(status(result)).isEqualTo(CREATED);
                Iterator<Map.Entry<String, JsonNode>> fields = Json.parse(contentAsString(result)).getFields();
                Map.Entry<String, JsonNode> field = fields.next();
                Tag expected = new Tag();
                expected.id = Long.valueOf(field.getKey());
                expected.name = field.getValue().asText();

                assertThat(expected.name).isEqualTo("OS - linux");
                assertThat(Project.findByOwnerAndProjectName("hobi", "nForge4java").tags.contains(expected)).isTrue();
            }
        });
    }

    @Test
    public void tags() {
        running(fakeApplication(Helpers.inMemoryDatabase()), new Runnable() {
            public void run() {
                //Given
                Project project = Project.findByOwnerAndProjectName("hobi", "nForge4java");

                Tag tag1 = new Tag("OS", "linux");
                tag1.save();
                project.tags.add(tag1);
                project.update();

                // If null is given as the first parameter, "Tag" is chosen as the category.
                Tag tag2 = new Tag(null, "foo");
                tag2.save();
                project.tags.add(tag2);
                project.update();

                //When

                Result result = callAction(
                        controllers.routes.ref.ProjectApp.tags("hobi", "nForge4java"),
                        fakeRequest().withHeader("Accept", "application/json")
                );

                //Then
                assertThat(status(result)).isEqualTo(OK);
                JsonNode json = Json.parse(contentAsString(result));
                assertThat(json.has(tag1.id.toString())).isTrue();
                assertThat(json.has(tag2.id.toString())).isTrue();
                assertThat(json.get(tag1.id.toString()).asText()).isEqualTo("OS - linux");
                assertThat(json.get(tag2.id.toString()).asText()).isEqualTo("Tag - foo");
            }
        });
    }

    @Test
    public void untag() {
        running(fakeApplication(Helpers.inMemoryDatabase()), new Runnable() {
            public void run() {
                //Given
                Project project = Project.findByOwnerAndProjectName("hobi", "nForge4java");

                Tag tag1 = new Tag("OS", "linux");
                tag1.save();
                project.tags.add(tag1);
                project.update();
                Long tagId = tag1.id;

                assertThat(project.tags.contains(tag1)).isTrue();

                Map<String,String> data = new HashMap<String,String>();
                data.put("_method", "DELETE");
                User admin = User.findByLoginId("admin");

                //When
                Result result = callAction(
                        controllers.routes.ref.ProjectApp.untag("hobi", "nForge4java", tagId),
                        fakeRequest()
                                .withFormUrlEncodedBody(data)
                                .withHeader("Accept", "application/json")
                                .withSession(UserApp.SESSION_USERID, admin.id.toString())
                );

                //Then
                assertThat(status(result)).isEqualTo(204);
                assertThat(Project.findByOwnerAndProjectName("hobi", "nForge4java").tags.contains(tag1)).isFalse();
            }
        });
    }
}
