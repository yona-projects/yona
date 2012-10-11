package models.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;

import models.ProjectUser;

import play.db.ebean.Model;

@Entity
public class Card extends Model{
    @Id
    public Long id;
    public String title;
    public List<TaskComment> comments = new ArrayList<TaskComment>();
    public Set<ProjectUser> assignee = new HashSet<ProjectUser>() ;
    int StoryPoint; //!주의 10배로 표현
    public Set<Label> labels = new HashSet<Label>();
    public String body;
    public Date dueDate;
    public CheckList checklist;
    
    private static Finder<Long, Card> find = new Finder<Long, Card>(Long.class, Card.class);
    
    public static Card findById(Long cardid){
        return find.byId(cardid);
    }
    
    public void assignMember(ProjectUser member){
        assignee.add(member);
    }
    public void unassignMember(ProjectUser member){
        assignee.remove(member);
    }
    public void addLabel(Label label){
        labels.add(label);
    }
    public void removeLabel(Label label){
        labels.remove(label);
    }
    public void addComment(TaskComment comment){
        comments.add(comment);
        comment.save();
    }
    public void removeComment(TaskComment comment){
        comments.remove(comment);
    }

    public void setCheckList(CheckList checklist) {
        if(this.checklist != null) {
            this.checklist.delete();
        }
        this.checklist = checklist;
        checklist.save();
    }
}
