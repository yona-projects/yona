package models.task;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import models.Project;

import play.db.ebean.Model;

@Entity
public class TaskBoard extends Model {
    @Id
    public Long id;
    public List<Line> lines;
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
        return find.where().eq("project", project).findUnique();
    }
    
}
