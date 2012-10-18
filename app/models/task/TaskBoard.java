package models.task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import models.Project;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class TaskBoard extends Model {
    @Id
    public Long id;
    
    @OneToMany(mappedBy = "taskBoard", cascade=CascadeType.ALL)
    public List<Line> lines = new ArrayList<Line>();
    @OneToMany(mappedBy = "taskBoard", cascade=CascadeType.ALL)
    public List<Label> labels;
    
    @OneToOne
    public Project project;
    
    private static Finder<Long, TaskBoard> find = new Finder<Long, TaskBoard>(Long.class, TaskBoard.class);
    
    public static TaskBoard create(Project project) {
        TaskBoard taskBoard = new TaskBoard();
        //create default line
        taskBoard.lines = new ArrayList<Line>();
        taskBoard.lines.add(createLine("Box"));
        taskBoard.lines.add(createLine("TODO"));
        taskBoard.lines.add(createLine("Doing"));
        taskBoard.lines.add(createLine("Test"));
        taskBoard.lines.add(createLine("Done"));
        
        //create default Label
        taskBoard.labels = new ArrayList<Label>();
        for(int i = 0; i < 10; i++){
            Label label = new Label();
            label.save();
            taskBoard.labels.add(label);
        }
        
        taskBoard.project = project;
        
        taskBoard.save();
        
        return taskBoard;
    }
    private static Line createLine(String title) {
        Line line  = new Line();
        line.title = title;
        line.save();
        return line;
    }

    public static TaskBoard findByProject(Project project) {
        return find.where().eq("project.id", project.id).findUnique();
    }
    public void accecptJSON(JsonNode json) {
        // TODO json객체를 taskboard객체로 전환한다. 단 이때 이미 있는걸 확인해야 한다.
        //아니면 특정 카드만 보내면 _id 로 찾아서 그거만 변경?
        
    }
   
    public JsonNode toJSON() {
        //라인중에서 넣을 것만 넣고 나머지는 다 위임한다.
        ArrayNode json = Json.newObject().arrayNode();
        for(int i = 0; i < lines.size(); i++){
            json.add(1);
        }
        /*assert(lines != null);
        assert(lines.size() == 2);
        Iterator<Line> iter = lines.iterator();
        while(iter.hasNext()){
            Line line = iter.next();
            json.add(1);
        }*/
        return json;
    }
    
}
