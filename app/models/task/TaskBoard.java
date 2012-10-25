package models.task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import models.Project;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;

import play.Logger;
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
        taskBoard.lines.add(createLine("Todo"));
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
        // 이미 있는 목록을 지워버리고 온거로만 채운다. 지워지면 난 몰라!
        // TODO delete를 고려할것.
        lines.clear();
        for(int i =0; i < json.size(); i++){
            JsonNode lineJson = json.get(i);
            Long lineId = lineJson.get("_id").asLong();
            Line line = Line.findById(lineId);
            if(line == null){
                line = new Line();
            }
            lines.add(line);
            line.taskBoard = this;
            line.accecptJSON(lineJson);
            line.save();
        }
        save();
    }
   
    public JsonNode toJSON() {
        //라인중에서 넣을 것만 넣고 나머지는 다 위임한다.
        ArrayNode json = Json.newObject().arrayNode();
        
        for(Line line : lines) {
            json.add(line.toJSON());
        }
        return json;
    }
}
