package models.task;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

import models.ModelTest;

public class CheckListTest extends ModelTest<CheckList> {
    @Test
    public void addItem() throws Exception {
        // Given
        CheckList checklist = CheckList.findById(1l);
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
        CheckList checklist = CheckList.findById(1l);
        Item item = Item.findById(1l);
        // When
        checklist.removeItem(item);
        // Then
        assertThat(checklist.items).excludes(item);
    }
}
