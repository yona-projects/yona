package models.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.ProjectUser;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Card extends Model {
    @Id
    public Long id;
    public String title;
    // TODO 아래에 있는것 중에 관계를 맺어줘야 하는 것들도 있다.
    public List<TaskComment> comments = new ArrayList<TaskComment>();
    //Action도 저장해야 함.
    public Set<ProjectUser> assignee = new HashSet<ProjectUser>();
    public int storyPoint; // !주의 10배로 표현
    public Set<Label> labels = new HashSet<Label>();
    public String body;
    public Date dueDate;
    public CheckList checklist;

    @ManyToOne
    public Line line;

    private static Finder<Long, Card> find = new Finder<Long, Card>(Long.class, Card.class);

    public static Card findById(Long id) {
        return find.byId(id);
    }

    public void assignMember(ProjectUser member) {
        assignee.add(member);
    }

    public void unassignMember(ProjectUser member) {
        assignee.remove(member);
    }

    public void addLabel(Label label) {
        labels.add(label);
    }

    public void removeLabel(Label label) {
        labels.remove(label);
    }

    public void addComment(TaskComment comment) {
        comments.add(comment);
        comment.save();
    }

    public void removeComment(TaskComment comment) {
        comments.remove(comment);
    }

    public void setCheckList(CheckList checklist) {
        if (this.checklist != null) {
            this.checklist.delete();
        }
        this.checklist = checklist;
        checklist.save();
    }

    public JsonNode toJSON() {
        ObjectNode json = Json.newObject();
        json.put("_id", id);
        json.put("title", title);
        json.put("body", body);
        json.put("storyPoint", storyPoint);
        /*commentsJson
        for(TaskComment comment : comments){
           
        }*/
        json.put("comments", Json.newObject().arrayNode());
        return json;
    }

    public void accecptJSON(JsonNode json) {
        title = json.get("title").asText();
        body = json.get("body").asText();
        // TODO 기타 다른것들도 데이터를 집어 넣어 줘야 함.

        save();
    }
}
