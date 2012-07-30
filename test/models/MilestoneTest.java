package models;

import models.enumeration.Direction;
import models.enumeration.MilestoneState;
import org.junit.Test;

import java.util.*;

import static org.fest.assertions.Assertions.assertThat;

public class MilestoneTest extends ModelTest {

    @Test
    public void create() throws Exception {
        // Given
        Milestone newMilestone = new Milestone();
        newMilestone.dueDate = new Date();
        newMilestone.contents = "테스트 마일스톤";
        newMilestone.project = Project.findById(1l);
        newMilestone.numClosedIssues = 0;
        newMilestone.numOpenIssues = 0;
        newMilestone.numTotalIssues = 0;
        newMilestone.title = "0.1";
        newMilestone.completionRate = 0;
        // When
        Milestone.create(newMilestone);
        // Then
        assertThat(newMilestone.id).isNotNull();
    }

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Milestone firstMilestone = Milestone.findById(1l);
        // Then
        assertThat(firstMilestone.title).isEqualTo("v.0.1");
        assertThat(firstMilestone.contents).isEqualTo("nFORGE 첫번째 버전.");

        Calendar expactDueDate = new GregorianCalendar();
        expactDueDate.set(2012, 6, 12, 23, 59, 59); // 2012-07-12

        Calendar dueDate = new GregorianCalendar();
        dueDate.setTime(firstMilestone.dueDate);

        assertThat(expactDueDate.get(Calendar.YEAR)).isEqualTo(
                dueDate.get(Calendar.YEAR));
        assertThat(expactDueDate.get(Calendar.MONTH)).isEqualTo(
                dueDate.get(Calendar.MONTH));
        assertThat(expactDueDate.get(Calendar.DAY_OF_MONTH)).isEqualTo(
                dueDate.get(Calendar.DAY_OF_MONTH));

        assertThat(firstMilestone.numClosedIssues).isEqualTo(10);
        assertThat(firstMilestone.numOpenIssues).isEqualTo(0);
        assertThat(firstMilestone.numTotalIssues).isEqualTo(10);
        assertThat(firstMilestone.project).isEqualTo(Project.findById(1l));
        assertThat(firstMilestone.completionRate).isEqualTo(100);
    }

    @Test
    public void delete() throws Exception {
        // Given
        Milestone firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone).isNotNull();
        // When
        Milestone.delete(firstMilestone.id);
        // Then
        firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone).isNull();
    }

    @Test
    public void update() throws Exception {
        // Given
        Milestone updateMilestone = new Milestone();
        updateMilestone.contents = "엔포지 첫번째 버전.";
        updateMilestone.title = "1.0.0-SNAPSHOT";
        updateMilestone.numClosedIssues = 10;
        updateMilestone.numOpenIssues = 1;
        updateMilestone.numTotalIssues = 11;

        // When
        Milestone.update(updateMilestone, 2l);
        // Then
        Milestone actualMilestone = Milestone.findById(2l);
        assertThat(actualMilestone.contents)
                .isEqualTo(updateMilestone.contents);
        assertThat(actualMilestone.title).isEqualTo(updateMilestone.title);
        assertThat(actualMilestone.numClosedIssues).isEqualTo(10);
        assertThat(actualMilestone.numOpenIssues).isEqualTo(1);
        assertThat(actualMilestone.numTotalIssues).isEqualTo(11);
        assertThat(actualMilestone.completionRate).isEqualTo(90);
    }

    @Test
    public void findByProjectId() throws Exception {
        // Given
        // When
        List<Milestone> firstProjectMilestones = Milestone.findByProjectId(1l);
        // Then
        assertThat(firstProjectMilestones.size()).isEqualTo(3);
        checkIfTheMilestoneIsBelongToTheProject(firstProjectMilestones, 1l, 2l,
                7l);

        // Given
        // When
        List<Milestone> secondProjectMilestones = Milestone.findByProjectId(2l);
        // Then
        assertThat(secondProjectMilestones.size()).isEqualTo(4);
        checkIfTheMilestoneIsBelongToTheProject(secondProjectMilestones, 3l,
                4l, 5l, 6l);
    }

    private void checkIfTheMilestoneIsBelongToTheProject(
            List<Milestone> milestones, Long... actualMilestoneIds) {
        List<Long> milestoneIds = Arrays.asList(actualMilestoneIds);
        for (Milestone milestone : milestones) {
            assertThat(milestoneIds.contains(milestone.id)).isEqualTo(true);
        }
    }

    @Test
    public void findClosedMilestones() throws Exception {
        // Given
        // When
        List<Milestone> p1Milestones = Milestone.findClosedMilestones(1l);
        // Then
        assertThat(p1Milestones.size()).isEqualTo(1);

        // Given
        // When
        List<Milestone> p2Milestones = Milestone.findClosedMilestones(2l);
        // Then
        assertThat(p2Milestones.size()).isEqualTo(3);
    }

    @Test
    public void findOpenMilestones() throws Exception {
        // Given
        // When
        List<Milestone> p1Milestones = Milestone.findOpenMilestones(1l);

        // Then
        assertThat(p1Milestones.size()).isEqualTo(2);

        // Given
        // When
        List<Milestone> p2Milestones = Milestone.findOpenMilestones(2l);

        // Then
        assertThat(p2Milestones.size()).isEqualTo(1);
    }

    @Test
    public void findMilestones() throws Exception {
        // Given
        // When
        List<Milestone> p1InCmpleteMilestones = Milestone.findMilestones(1l,
                MilestoneState.OPEN);
        // Then
        assertThat(p1InCmpleteMilestones.size()).isEqualTo(2);

        // Given
        // When
        List<Milestone> p2CompletedMilestones = Milestone.findMilestones(2l,
                MilestoneState.CLOSED);
        // Then
        assertThat(p2CompletedMilestones.size()).isEqualTo(3);

        // Given
        // When
        List<Milestone> p2Milestones = Milestone.findMilestones(2l,
                MilestoneState.ALL);
        // Then
        assertThat(p2Milestones.size()).isEqualTo(4);

        // Given
        // When
        List<Milestone> p1MilestonesASCDirection = Milestone.findMilestones(1l,
                MilestoneState.ALL, "completionRate", Direction.ASC);
        // Then
        assertThat(p1MilestonesASCDirection.get(0).completionRate).isEqualTo(9);

        // Given
        // When
        List<Milestone> p2MilestonesDESCDirection = Milestone.findMilestones(
                2l, MilestoneState.ALL, "completionRate", Direction.DESC);
        // Then
        assertThat(p2MilestonesDESCDirection.get(0).completionRate).isEqualTo(
                100);
    }

    @Test
    public void getDueDateString() throws Exception {
        // Given
        // When
        Milestone m1 = Milestone.findById(1l);
        // Then
        String m1DueDate = m1.getDueDateString();
        assertThat(m1DueDate).isEqualTo("2012-07-12");

        // Given
        // When
        Milestone m4 = Milestone.findById(4l);
        // Then
        String m4DueDate = m4.getDueDateString();
        assertThat(m4DueDate).isEqualTo("2012-04-11");
    }

    @Test
    public void options() {
        // Given
        // When
        Map<String, String> milestoneOptions = Milestone.options(1l);
        // Then
        assertThat(milestoneOptions).hasSize(3);
    }

    
    // TODO 추후에 관계 설정후에?
//    @Test
//    public void addIssue() throws Exception {
//        // GIVEN
//        // WHEN
//        Milestone m1 = Milestone.findById(1l);
//        Issue issue1 = Issue.findById(1l);
//        m1.add(issue1);
//
//        // THEN
//        assertThat(m1.numTotalIssues).isEqualTo(1);
//
//        issue1 = Issue.findById(1l);
////        assertThat(issue1.milestoneId).isEqualTo(m1.id);
//    }
}
