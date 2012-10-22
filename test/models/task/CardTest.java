package models.task;

import static org.fest.assertions.Assertions.assertThat;
import models.ModelTest;
import models.ProjectUser;

import org.codehaus.jackson.JsonNode;
import org.junit.Test;

public class CardTest extends ModelTest<Card> {

    @Test
    public void findById(){
        //Given
        //When
        Card card = Card.findById(1l);
        //Then
        assertThat(card).isNotNull();
        assertThat(card.body).isEqualTo("test card");
    }
    @Test
    public void assign() throws Exception {
        //Given
        Card card = new Card();
        //When
        card.assignMember(ProjectUser.findByIds(1l, 1l));
        //Then
        assertThat(card.assignee).contains(ProjectUser.findByIds(1l, 1l));
    }
    @Test
    public void unassign() throws Exception {
        //Given
        Card card = Card.findById(1l);
        //when
        card.unassignMember(ProjectUser.findByIds(1l, 1l));
        //Then
        assertThat(card.assignee).isEmpty();
    }
    @Test
    public void addLabel() throws Exception {
        //Given
        Card card = Card.findById(1l);
        Label label = new Label();//XXX
        //When
        card.addLabel(label);
        //Then
        assertThat(card.labels).isNotEmpty();
        assertThat(card.labels.contains(label));
    }
    @Test
    public void removeLabel() throws Exception {
        //Given
        Card card = Card.findById(1l);
        Label label = Label.findById(1l);
        //When
        card.removeLabel(label);
        //Then
        assertThat(card.labels).excludes(label);
    }
    @Test
    public void addComment() throws Exception {
        //Given
        Card card = Card.findById(1l);
        TaskComment comment = new TaskComment();
        comment.body ="aaa";
        //When
        card.addComment(comment);
        //Then
        assertThat(card.comments).contains(comment);
        assertThat(comment.id).isNotNull();
    }
    @Test
    public void removeComment() throws Exception {
        //Given
        Card card = Card.findById(1l);
        //When
        card.removeComment(TaskComment.findById(1l));
        //Then
        assertThat(card.comments).excludes(TaskComment.findById(1l));
    }
    @Test
    public void addCheckList() throws Exception {
        //Given
        Card card = Card.findById(1l);
        CheckList checklist = new CheckList();
        //When
        card.setCheckList(checklist);
        //Then
        assertThat(card.checklist).isNotNull();
        assertThat(card.checklist).isEqualTo(checklist);
        assertThat(checklist.id).isNotNull();
    }
    @Test
    public void toJSON() throws Exception {
        //Given
        Card card = Card.findById(1l);
        //When
        JsonNode json = card.toJSON();
        //Then
        assertThat(json.get("title").asText()).isEqualTo("test title");
        assertThat(json.get("body").asText()).isEqualTo("test card");
    }
}
