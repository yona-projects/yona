package models.task;

import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

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

    @OneToOne(cascade = CascadeType.ALL)
    public Checklist checklist;

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL)
    public List<TaskComment> comments;

    @ManyToOne
    public Line line;

    @OneToMany(cascade = CascadeType.ALL)
    public Set<CardAssignee> assignee;

    @OneToMany(cascade = CascadeType.ALL)
    public Set<CardLabel> labels;

    public int storyPoint; // !주의 10배로 표현

    public String body;
    public Date dueDate;

    public static Finder<Long, Card> find = new Finder<Long, Card>(Long.class,
            Card.class);

    public static Card findById(Long id) {
        return find.byId(id);
    }

    public static List<Card> findByAssignee(Long userId) {
        return find.where().eq("assignee.id", userId).findList();
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

    /*
     * public void addCheckList(CheckList checklist) { if(this.checkList !=
     * null){ this.checkList.delete(); } this.checkList = checklist;
     * checklist.save(); }
     */

    public JsonNode toJSON() {

        ObjectNode json = Json.newObject();
        json.put("_id", id);
        json.put("title", title);
        json.put("body", body);
        json.put("storyPoint", storyPoint);
        ArrayNode commentsJson = Json.newObject().arrayNode();
        for (TaskComment comment : comments) {
            commentsJson.add(comment.toJSON());
        }
        json.put("comments", commentsJson);

        ArrayNode assigneeJson = Json.newObject().arrayNode();
        for (CardAssignee cardAssignee : assignee) {
            ObjectNode tmp = Json.newObject();
            tmp.put("_id", cardAssignee.projectUser.id);
            tmp.put("loginId", cardAssignee.projectUser.user.loginId);
            assigneeJson.add(tmp);
        }
        json.put("assignee", assigneeJson);

        ArrayNode labelsJson = Json.newObject().arrayNode();
        for (CardLabel label : labels) {
            ObjectNode tmp = Json.newObject();
            tmp.put("_id", label.label.id);
            tmp.put("name", label.label.name);
            labelsJson.add(tmp);
        }
        json.put("labels", labelsJson);

        if (checklist != null) {
            json.put("checklist", checklist.toJSON());
        }

        return json;
    }

    public void accecptJSON(JsonNode json) {
        title = json.get("title").asText();
        body = json.get("body").asText();
        storyPoint = json.get("storyPoint").asInt();
        JsonNode checklistJson = json.get("checklist");
        if (checklistJson != null) {
            if (checklist == null) {
                checklist = new Checklist();
            }
            checklist.acceptJSON(checklistJson);
        } else {
            if(checklist != null){
                checklist.delete();
            }
            checklist = null;
        }

        JsonNode assigneeJson = json.get("assignee");
        for (CardAssignee tmp : assignee) {
            tmp.delete();
        }
        assignee.clear();

        for (int i = 0; i < assigneeJson.size(); i++) {
            JsonNode memberJson = assigneeJson.get(i);
            Long projectUserId = memberJson.get("_id").asLong();
            CardAssignee cardAssignee = new CardAssignee(this, projectUserId);
            assignee.add(cardAssignee);
        }

        JsonNode labelsJson = json.get("labels");

        for (CardLabel label : this.labels) {
            label.delete();
        }
        this.labels.clear();
        for (int i = 0; i < labelsJson.size(); i++) {
            JsonNode labelJson = labelsJson.get(i);
            Long labelId = labelJson.get("_id").asLong();
            labelJson.get("name");
            this.labels.add(new CardLabel(this, labelId));
        }
        this.save();
    }

}
