package models.task;

import static org.fest.assertions.Assertions.assertThat;

import org.codehaus.jackson.JsonNode;
import org.junit.Test;

import models.ModelTest;

public class CheckListTest extends ModelTest<Checklist> {
    @Test
    public void addItem() throws Exception {
        // Given
        Checklist checklist = Checklist.findById(1l);
        Item item = new Item();
        // When
        checklist.addItem(item);
        // Then
        assertThat(checklist.items).isNotEmpty();
        assertThat(checklist.items).contains(item);
    }

    @Test
    public void removeItem() throws Exception {
        // Given
        Checklist checklist = Checklist.findById(1l);
        Item item = Item.findById(1l);
        // When
        checklist.removeItem(item);
        // Then
        assertThat(checklist.items).excludes(item);
    }
    
    @Test
    public void toJSON() throws Exception {
        //Given
        Checklist checklist = Checklist.findById(1l);
        //When
        JsonNode json = checklist.toJSON();
        //Then
        assertThat(json.get("title").asText()).isEqualTo("TODO");
        assertThat(json.get("items").size()).isEqualTo(2);
    }
}
