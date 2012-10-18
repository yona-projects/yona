package models.task;

import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;
import models.Project;

import org.codehaus.jackson.JsonNode;
import org.junit.Ignore;
import org.junit.Test;

import play.libs.Json;

import controllers.ProjectApp;

public class TaskBoardTest extends ModelTest<TaskBoard> {

    @Test
    public void create() throws Exception {
        // Given
        Project project = ProjectApp.getProject("hobi", "nForge4java");
        // When
        TaskBoard taskboard = TaskBoard.create(project);
        // Then
        assertThat(taskboard.id).isNotNull();
        assertThat(taskboard.lines.size()).isEqualTo(5);
        assertThat(taskboard.labels.size()).isEqualTo(10);
        assertThat(taskboard.project).isEqualTo(project);
    }

    @Ignore @Test
    public void findByProject() throws Exception {
        // Given
        Project project = ProjectApp.getProject("hobi", "nForge4java");
        // When
        TaskBoard taskboard = TaskBoard.findByProject(project);
        // Then
        assertThat(taskboard).isNotNull();
        assertThat(taskboard.project).isEqualTo(project);
    }
    @Ignore @Test
    public void accecptJSON() throws Exception{
        //Given
        JsonNode data = Json.parse("[{'_id':'1','title':'BOX','cards':[{'_id':'2','title':'bbb','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00G'},{'_id':'3','title':'ccc','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00I'}],'$$hashKey':'004'},{'_id':'2','title':'TODO','cards':[{'_id':'4','title':'alpha','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00K'},{'_id':'6','title':'chaile','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00O'},{'_id':'1','title':'aaa','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00E'},{'_id':'7','title':'delta','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00Q'}],'$$hashKey':'006'},{'_id':'3','title':'Doing','cards':[{'_id':'5','title':'bravo','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00M'}],'$$hashKey':'008'},{'_id':'4','title':'Test','cards':[{'_id':'8','title':'check','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00S'},{'_id':'9','title':'test','body':[],'comment':[],'assignee':[],'labels':[],'checkList':{},'dueDate':{},'storyPoint':-1,'$$hashKey':'00U'}],'$$hashKey':'00A'},{'_id':'5','title':'Done','cards':[],'$$hashKey':'00C'}]");
        //When
        TaskBoard project = TaskBoard.findByProject(getTestProject());
        project.accecptJSON(data);
        //Then
        assertThat(project.lines.size()).isEqualTo(5);
        
    }
    @Ignore @Test
    public void toJSON() throws Exception {
        //현재 DB에 들어있는 놈을 JSON으로 내려 보내야 한다.
        //Given
        TaskBoard taskBoard = TaskBoard.findByProject(getTestProject());
        //assertThat(taskBoard).isNotNull();
        assertThat(taskBoard.lines).isNotNull();
        assertThat(taskBoard.lines.size()).isEqualTo(2);
        //When
        JsonNode json = taskBoard.toJSON();
        //Then
        //assertThat(json.size()).isEqualTo(2);
    }

}
