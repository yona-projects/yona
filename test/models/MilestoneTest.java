package models;

import models.enumeration.Direction;
import models.enumeration.IssueState;
import models.enumeration.StateType;
import org.junit.Test;
import utils.JodaDateUtil;

import java.util.*;

import static org.fest.assertions.Assertions.assertThat;

public class MilestoneTest extends ModelTest<Milestone> {

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
        expactDueDate.set(2012, Calendar.JULY, 12, 23, 59, 59); // 2012-07-12

        Calendar dueDate = new GregorianCalendar();
        dueDate.setTime(firstMilestone.dueDate);

        assertThat(expactDueDate.get(Calendar.YEAR)).isEqualTo(
            dueDate.get(Calendar.YEAR));
        assertThat(expactDueDate.get(Calendar.MONTH)).isEqualTo(
            dueDate.get(Calendar.MONTH));
        assertThat(expactDueDate.get(Calendar.DAY_OF_MONTH)).isEqualTo(
            dueDate.get(Calendar.DAY_OF_MONTH));

        assertThat(firstMilestone.numClosedIssues).isEqualTo(2);
        assertThat(firstMilestone.numOpenIssues).isEqualTo(2);
        assertThat(firstMilestone.numTotalIssues).isEqualTo(4);
        assertThat(firstMilestone.project).isEqualTo(Project.findById(1l));
        assertThat(firstMilestone.completionRate).isEqualTo(50);
    }

    @Test
    public void delete() throws Exception {
        // Given
        Milestone firstMilestone = Milestone.findById(1l);
        assertThat(firstMilestone).isNotNull();
        // When
        Milestone.delete(firstMilestone);
        flush();

        //Then
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
        assertThat(firstProjectMilestones.size()).isEqualTo(2);
        checkIfTheMilestoneIsBelongToTheProject(firstProjectMilestones, 1l, 2l);

        // Given
        // When
        List<Milestone> secondProjectMilestones = Milestone.findByProjectId(2l);
        // Then
        assertThat(secondProjectMilestones.size()).isEqualTo(2);
        checkIfTheMilestoneIsBelongToTheProject(secondProjectMilestones, 3l, 4l);
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
        assertThat(p1Milestones.size()).isEqualTo(0);

        // Given
        // When
        List<Milestone> p2Milestones = Milestone.findClosedMilestones(2l);
        // Then
        assertThat(p2Milestones.size()).isEqualTo(1);
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
            StateType.OPEN);
        // Then
        assertThat(p1InCmpleteMilestones.size()).isEqualTo(2);

        // Given
        // When
        List<Milestone> p2CompletedMilestones = Milestone.findMilestones(2l,
            StateType.CLOSED);
        // Then
        assertThat(p2CompletedMilestones.size()).isEqualTo(1);

        // Given
        // When
        List<Milestone> p2Milestones = Milestone.findMilestones(2l,
            StateType.ALL);
        // Then
        assertThat(p2Milestones.size()).isEqualTo(2);

        // Given
        // When
        List<Milestone> p1MilestonesASCDirection = Milestone.findMilestones(1l,
            StateType.ALL, "completionRate", Direction.ASC);
        // Then
        assertThat(p1MilestonesASCDirection.get(0).completionRate).isEqualTo(50);

        // Given
        // When
        List<Milestone> p2MilestonesDESCDirection = Milestone.findMilestones(
            2l, StateType.ALL, "completionRate", Direction.DESC);
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
        assertThat(milestoneOptions).hasSize(2);
    }

    @Test
    public void addIssue() throws Exception {
        // GIVEN
        Milestone m5 = Milestone.findById(5l);
        int totalNumber = m5.numTotalIssues;
        int openNumber = m5.numOpenIssues;
        Issue issue = new Issue("불필요한 로그 출력 코드 제거test");
        issue.date = JodaDateUtil.today();
        issue.state = IssueState.ASSIGNED;
        issue.stateType = StateType.OPEN;
        issue.authorId = User.findById(1l).id;
        issue.milestoneId = 5l;

        // WHEN
        Issue.create(issue);

        // THEN
        m5 = Milestone.findById(5l);
        assertThat(m5.numTotalIssues).isEqualTo(totalNumber + 1);
        assertThat(m5.numOpenIssues).isEqualTo(openNumber + 1);
    }

    @Test
    public void updateIssue() throws Exception {
        //Given
        Issue issue = new Issue("불필요한 로그 출력 코드 제거test");
        issue.updateStateType(issue);
        issue.milestoneId = 6l;
        issue.update(5l);

        Long milestoneId = issue.milestoneId;
        Milestone m6 = Milestone.findById(milestoneId);
        assertThat(m6.numOpenIssues).isEqualTo(0);
        assertThat(m6.numClosedIssues).isEqualTo(1);
        assertThat(m6.numTotalIssues).isEqualTo(1);
        assertThat(m6.completionRate).isEqualTo(100);

        //When
        m6.updateIssueInfo();
        flush();

        //Then
        m6 = Milestone.findById(milestoneId);
        assertThat(m6.numOpenIssues).isEqualTo(1);
        assertThat(m6.numClosedIssues).isEqualTo(1);
        assertThat(m6.numTotalIssues).isEqualTo(2);
        assertThat(m6.completionRate).isEqualTo(50);
    }

    @Test
    public void deleteIssue() throws Exception {
        //Given
        Issue issue = Issue.findById(7l);
        Milestone m5 = Milestone.findById(issue.milestoneId);
        assertThat(m5.numOpenIssues).isEqualTo(1);
        assertThat(m5.numClosedIssues).isEqualTo(1);
        assertThat(m5.numTotalIssues).isEqualTo(2);
        assertThat(m5.completionRate).isEqualTo(50);

        //When
        issue.delete();
        m5.delete(issue);

        //Then
        assertThat(m5.numOpenIssues).isEqualTo(0);
        assertThat(m5.numClosedIssues).isEqualTo(1);
        assertThat(m5.numTotalIssues).isEqualTo(1);
        assertThat(m5.completionRate).isEqualTo(100);
    }
}
