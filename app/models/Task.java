package models;

import java.util.List;

import play.db.ebean.Model;

public class Task extends Model{
    public Project project;
    public String title;
    public String body;
    
    private static Finder<Long, Task> find = new Finder<Long, Task>(Long.class, Task.class);
    
    public static List<Task> list(String userName, String projectName){
        return find.where().eq("project.owner", userName).eq("project.name", projectName).findList();
    }
}
