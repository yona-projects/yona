package models.task;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import models.ProjectUser;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Card extends Model {
	private static final long serialVersionUID = 1L;
	
    @Id
    public Long id;
    public String title;
    
    // FIXME 원래는 OneToOne으로 하려 했으나 Project.Delete시의 
    //       Ebean 에러로 인해 ManyToOne으로 변경
    @OneToMany(mappedBy="card", cascade=CascadeType.ALL)
    public List<Checklist> checklists;
    
    @OneToMany(mappedBy="card", cascade=CascadeType.ALL)
    public List<TaskComment> comments;

    @ManyToOne
    public Line line;
    
    // TODO 아래에 있는것 중에 관계를 맺어줘야 하는 것들도 있다.
    //Action도 저장해야 함.
    public Set<ProjectUser> assignee = new HashSet<ProjectUser>();
    public int storyPoint; // !주의 10배로 표현
    public Set<Label> labels = new HashSet<Label>();
    public String body;
    public Date dueDate;

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
        comment.card = this;
        comment.save();
        save();
    }

    public void removeComment(TaskComment comment) {
        comments.remove(comment);
    }

    /*public void addCheckList(CheckList checklist) {
        if(this.checkList != null){
            this.checkList.delete();
        }
        this.checkList = checklist;
        checklist.save();
    }*/

    public JsonNode toJSON() {
        ObjectNode json = Json.newObject();
        json.put("_id", id);
        json.put("title", title);
        json.put("body", body);
        json.put("storyPoint", storyPoint);
        ArrayNode commentsJson = Json.newObject().arrayNode();
        for(TaskComment comment : comments){
           commentsJson.add(comment.toJSON());
        }
        json.put("comments", commentsJson);
        
        if(checklists.size() != 0){
            json.put("checklist", checklists.get(0).toJSON());
        }
        return json;
    }

    public void accecptJSON(JsonNode json) {
        title = json.get("title").asText();
        body = json.get("body").asText();
        storyPoint = json.get("storyPoint").asInt();
        checklists.get(0).acceptJSON(json.get("checklist"));
        // TODO 기타 다른것들도 데이터를 집어 넣어 줘야 함.
        this.save();
    }
}
