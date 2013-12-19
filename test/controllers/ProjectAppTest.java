package controllers;

import models.Label;
import models.Project;
import models.User;
import org.codehaus.jackson.JsonNode;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class ProjectAppTest {
    protected static FakeApplication app;

    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Before
    public void before() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    @Test
    public void label() {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("category", "OS");
        data.put("name", "linux");
        User admin = User.findByLoginId("admin");

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.attachLabel("yobi", "projectYobi"),
                fakeRequest()
                        .withFormUrlEncodedBody(data)
                        .withHeader("Accept", "application/json")
                        .withSession(UserApp.SESSION_USERID, admin.id.toString())
        );

        //Then
        assertThat(status(result)).isEqualTo(CREATED);
        Iterator<Map.Entry<String, JsonNode>> fields = Json.parse(contentAsString(result)).getFields();
        Map.Entry<String, JsonNode> field = fields.next();

        Label expected = new Label(field.getValue().get("category").asText(), field.getValue().get("name").asText());
        expected.id = Long.valueOf(field.getKey());

        assertThat(expected.category).isEqualTo("OS");
        assertThat(expected.name).isEqualTo("linux");
        assertThat(Project.findByOwnerAndProjectName("yobi", "projectYobi").labels.contains(expected)).isTrue();
    }

    @Test
    public void labels() {
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        Label label1 = new Label("OS", "yobi-linux");
        label1.save();
        project.labels.add(label1);
        project.update();

        // If null is given as the first parameter, "Label" is chosen as the category.
        Label label2 = new Label(null, "foo");
        label2.save();
        project.labels.add(label2);
        project.update();

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.labels("yobi", "projectYobi"),
                fakeRequest().withHeader("Accept", "application/json")
        );

        //Then
        assertThat(status(result)).isEqualTo(OK);
        JsonNode json = Json.parse(contentAsString(result));

        String id1 = label1.id.toString();
        String id2 = label2.id.toString();

        assertThat(json.has(id1)).isTrue();
        assertThat(json.has(id2)).isTrue();
        assertThat(json.get(id1).get("category").asText()).isEqualTo("OS");
        assertThat(json.get(id1).get("name").asText()).isEqualTo("yobi-linux");
        assertThat(json.get(id2).get("category").asText()).isEqualTo("Label");
        assertThat(json.get(id2).get("name").asText()).isEqualTo("foo");
    }

    @Test
    public void detachLabel() {
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        Label label1 = new Label("OS", "linux");
        label1.save();
        project.labels.add(label1);
        project.update();
        Long labelId = label1.id;

        assertThat(project.labels.contains(label1)).isTrue();

        Map<String,String> data = new HashMap<>();
        data.put("_method", "DELETE");
        User admin = User.findByLoginId("admin");

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.detachLabel("yobi", "projectYobi",
                        labelId),
                fakeRequest()
                        .withFormUrlEncodedBody(data)
                        .withHeader("Accept", "application/json")
                        .withSession(UserApp.SESSION_USERID, admin.id.toString())
        );

        //Then
        assertThat(status(result)).isEqualTo(204);
        assertThat(Project.findByOwnerAndProjectName("yobi", "projectYobi").labels.contains(label1)).isFalse();
    }
}
