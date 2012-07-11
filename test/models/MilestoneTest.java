package models;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Date;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class MilestoneTest extends ModelTest {

    @Test
    @Ignore
    public void testCRUD() throws Exception {
        Milestone v01Milestone = new Milestone();
        v01Milestone.versionName = "v.0.1";
        v01Milestone.contents = "Dark launch";
        v01Milestone.dueDate = new Date();
        Milestone.create(v01Milestone); // Create

        Milestone actualMilestone = Milestone.findById(v01Milestone.id); // Read
        assertThat("Milestone model create & read", actualMilestone.id, is(notNullValue()));
        assertThat("Milestone model create & read", actualMilestone.versionName, is(v01Milestone.versionName));
        assertThat("Milestone model create & read", actualMilestone.contents, is(v01Milestone.contents));
        assertThat("Milestone model create & read test", actualMilestone.dueDate, is(v01Milestone.dueDate));

        actualMilestone.contents = "Light launch";
        actualMilestone.dueDate = new Date();

        Milestone.update(actualMilestone, actualMilestone.id); // Update
        Milestone updatedlMilestone = Milestone.findById(actualMilestone.id); // Read
        assertThat("Milestone model update test", updatedlMilestone.contents, is(actualMilestone.contents));
        assertThat("Milestone model update test", updatedlMilestone.dueDate, is(actualMilestone.dueDate));

        Milestone.delete(updatedlMilestone.id); // Delete
        assertThat("Milestone model delete test", Milestone.findById(updatedlMilestone.id), is(nullValue()));
    }
}
