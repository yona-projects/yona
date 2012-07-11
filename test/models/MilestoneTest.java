package models;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class MilestoneTest extends ModelTest {

    @Test
    public void testCreate() {
        Milestone newMilestone = new Milestone();
        newMilestone.dueDate = new Date();
        newMilestone.contents = "테스트 마일스톤";
        newMilestone.numClosedIssues = 10;
        newMilestone.numOpenIssues = 20;
        newMilestone.projectId = 100l;
        newMilestone.versionName = "0.1";

        Milestone.write(newMilestone);

        assertThat(newMilestone.id, is(notNullValue()));
    }

    @Test
    public void testFindById() {
        Milestone firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone.versionName, is("v.0.1"));
        assertThat(firstMilestone.contents, is("nFORGE 첫번째 버전."));

        Calendar expactDueDate = new GregorianCalendar();
        expactDueDate.set(2012, 6, 12); // 2012-07-12

        Calendar dueDate = new GregorianCalendar();
        dueDate.setTime(firstMilestone.dueDate);

        assertThat(expactDueDate.get(Calendar.YEAR), is(dueDate.get(Calendar.YEAR)));
        assertThat(expactDueDate.get(Calendar.MONTH), is(dueDate.get(Calendar.MONTH)));
        assertThat(expactDueDate.get(Calendar.DAY_OF_MONTH), is(dueDate.get(Calendar.DAY_OF_MONTH)));

        assertThat(firstMilestone.numClosedIssues, is(12));
        assertThat(firstMilestone.numOpenIssues, is(33));
        assertThat(firstMilestone.projectId, is(1l));
    }

    @Test
    public void testDelete() {
        Milestone firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone, is(notNullValue()));
        Milestone.delete(firstMilestone.id);

        firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone, is(nullValue()));
    }

    @Test
    public void testUpdate() {
        Milestone milestone = new Milestone();
        milestone.contents = "엔포지 첫번째 버전.";
        milestone.versionName = "1.0.0-SNAPSHOT";
        milestone.numClosedIssues = 100;
        milestone.numOpenIssues = 200;

        Milestone.update(milestone, 1l);

        Milestone actualMilestone = Milestone.findById(1l);
        assertThat(actualMilestone.contents, is(milestone.contents));
        assertThat(actualMilestone.versionName, is(milestone.versionName));
        assertThat(actualMilestone.numClosedIssues, is(100));
        assertThat(actualMilestone.numOpenIssues, is(200));
    }
}
