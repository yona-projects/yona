package models;

import org.junit.Test;

import java.util.*;

import static org.fest.assertions.Assertions.assertThat;

public class MilestoneTest extends ModelTest {

    @Test
    public void create() throws Exception {
        Milestone newMilestone = new Milestone();
        newMilestone.dueDate = new Date();
        newMilestone.contents = "테스트 마일스톤";
        newMilestone.numClosedIssues = 10;
        newMilestone.numOpenIssues = 10;
        newMilestone.projectId = 100l;
        newMilestone.versionName = "0.1";
        newMilestone.isCompleted = true;

        Milestone.create(newMilestone);

        assertThat(newMilestone.id).isNotNull();
    }

    @Test
    public void findById() throws Exception {
        Milestone firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone.versionName).isEqualTo("v.0.1");
        assertThat(firstMilestone.contents).isEqualTo("nFORGE 첫번째 버전.");

        Calendar expactDueDate = new GregorianCalendar();
        expactDueDate.set(2012, 6, 12, 23, 59, 59); // 2012-07-12

        Calendar dueDate = new GregorianCalendar();
        dueDate.setTime(firstMilestone.dueDate);

        assertThat(expactDueDate.get(Calendar.YEAR)).isEqualTo(dueDate.get(Calendar.YEAR));
        assertThat(expactDueDate.get(Calendar.MONTH)).isEqualTo(dueDate.get(Calendar.MONTH));
        assertThat(expactDueDate.get(Calendar.DAY_OF_MONTH)).isEqualTo(dueDate.get(Calendar.DAY_OF_MONTH));

        assertThat(firstMilestone.numClosedIssues).isEqualTo(10);
        assertThat(firstMilestone.numOpenIssues).isEqualTo(10);
        assertThat(firstMilestone.projectId).isEqualTo(1l);
        assertThat(firstMilestone.isCompleted).isEqualTo(true);
    }

    @Test
    public void delete() throws Exception {
        Milestone firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone).isNotNull();
        Milestone.delete(firstMilestone.id);

        firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone).isNull();
    }

    @Test
    public void update() throws Exception {
        Milestone updateMilestone = new Milestone();
        updateMilestone.contents = "엔포지 첫번째 버전.";
        updateMilestone.versionName = "1.0.0-SNAPSHOT";
        updateMilestone.numClosedIssues = 100;
        updateMilestone.numOpenIssues = 200;

        Milestone.update(updateMilestone, 1l);

        Milestone actualMilestone = Milestone.findById(1l);
        assertThat(actualMilestone.contents).isEqualTo(updateMilestone.contents);
        assertThat(actualMilestone.versionName).isEqualTo(updateMilestone.versionName);
        assertThat(actualMilestone.numClosedIssues).isEqualTo(100);
        assertThat(actualMilestone.numOpenIssues).isEqualTo(200);
        assertThat(actualMilestone.isCompleted).isEqualTo(false);
    }

    @Test
    public void findByProjectId() throws Exception {
        List<Milestone> firstProjectMilestones = Milestone.findByProjectId(1l);
        assertThat(firstProjectMilestones.size()).isEqualTo(2);

        checkIfTheMilestoneIsBelongToTheProject(firstProjectMilestones, 1l, 2l);

        List<Milestone> secondProjectMilestones = Milestone.findByProjectId(2l);
        assertThat(secondProjectMilestones.size()).isEqualTo(4);

        checkIfTheMilestoneIsBelongToTheProject(secondProjectMilestones, 3l, 4l, 5l, 6l);
    }

    private void checkIfTheMilestoneIsBelongToTheProject(List<Milestone> milestones, Long... actualMilestoneIds) {
        List<Long> milestoneIds = Arrays.asList(actualMilestoneIds);
        for (Milestone milestone : milestones) {
            assertThat(milestoneIds.contains(milestone.id)).isEqualTo(true);
        }
    }

    @Test
    public void findClosedMilestones() throws Exception {
        List<Milestone> p1Milestones = Milestone.findClosedMilestones(1l);
        assertThat(p1Milestones.size()).isEqualTo(1);

        List<Milestone> p2Milestones = Milestone.findClosedMilestones(2l);
        assertThat(p2Milestones.size()).isEqualTo(2);
    }

    @Test
    public void findOpenMilestones() throws Exception {
        List<Milestone> p1Milestones = Milestone.findOpenMilestones(1l);
        assertThat(p1Milestones.size()).isEqualTo(1);

        List<Milestone> p2Milestones = Milestone.findOpenMilestones(2l);
        assertThat(p2Milestones.size()).isEqualTo(2);
    }

    @Test
    public void findMilestones() throws Exception {
        List<Milestone> p1InCmpleteMilestones = Milestone.findMilestones(1l, "open");
        assertThat(p1InCmpleteMilestones.size()).isEqualTo(1);

        List<Milestone> p2CompletedMilestones = Milestone.findMilestones(2l, "closed");
        assertThat(p2CompletedMilestones.size()).isEqualTo(2);

        List<Milestone> p2Milestones = Milestone.findMilestones(2l, "all");
        assertThat(p2Milestones.size()).isEqualTo(4);
    }

    @Test
    public void getCompletionRate() throws Exception {
        Milestone m1 = Milestone.findById(1l);
        int m1CompletionRate = m1.getCompletionRate();
        assertThat(m1CompletionRate).isEqualTo(100);

        Milestone m2 = Milestone.findById(2l);
        int m2CompletionRate = m2.getCompletionRate();
        assertThat(m2CompletionRate).isEqualTo(11);

        Milestone m3 = Milestone.findById(3l);
        int m3CompletionRate = m3.getCompletionRate();
        assertThat(m3CompletionRate).isEqualTo(100);

        Milestone m6 = Milestone.findById(6l);
        int m6CompletionRate = m6.getCompletionRate();
        assertThat(m6CompletionRate).isEqualTo(0);

    }

    @Test
    public void getDueDate() throws Exception {
        Milestone m1 = Milestone.findById(1l);
        String m1DueDate = m1.getDueDate();
        assertThat(m1DueDate).isEqualTo("2012-07-12");

        Milestone m4 = Milestone.findById(4l);
        String m4DueDate = m4.getDueDate();
        assertThat(m4DueDate).isEqualTo("2012-04-11");
    }
}
