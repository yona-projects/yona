package models;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class MilestoneTest extends ModelTest {

    @Test
    public void testCreate() throws Exception {
        Milestone v01Milestone = new Milestone();
        v01Milestone.versionName = "v.0.1";
        v01Milestone.contents = "Dark launch";
        v01Milestone.dueDate  = new Date("07/10/2012");

        Milestone.create(v01Milestone);
        Milestone actualMilestone = Milestone.findById(v01Milestone.id);
        assertEquals("v.0.1",actualMilestone.versionName);

    }
}
