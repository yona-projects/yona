/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author yoon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import com.avaje.ebean.Ebean;
import models.enumeration.State;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.i18n.Messages;
import play.libs.Yaml;
import support.Helpers;
import utils.JodaDateUtil;
import utils.YamlUtil;

import java.io.IOException;
import java.util.*;

import static org.fest.assertions.Assertions.assertThat;

public class MilestoneTest extends ModelTest<Milestone> {

    @Test
    public void create() throws Exception {
        // Given
        Milestone newMilestone = new Milestone();
        newMilestone.dueDate = new Date();
        newMilestone.setContents("테스트 마일스톤");
        newMilestone.project = Project.find.byId(3l);
        newMilestone.title = "0.1";

        // When
        Milestone.create(newMilestone);

        // Then
        assertThat(newMilestone.id).isNotNull();
        //To keep data clean after this test.
        newMilestone.delete();
    }

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Milestone firstMilestone = Milestone.findById(1l);
        // Then
        assertThat(firstMilestone.title).isEqualTo("v.0.1");
        assertThat(firstMilestone.getContents()).isEqualTo("nFORGE 첫번째 버전.");

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

        assertThat(firstMilestone.getNumClosedIssues()).isEqualTo(2);
        assertThat(firstMilestone.getNumOpenIssues()).isEqualTo(2);
        assertThat(firstMilestone.getNumTotalIssues()).isEqualTo(4);
        assertThat(firstMilestone.project).isEqualTo(Project.find.byId(1l));
        assertThat(firstMilestone.getCompletionRate()).isEqualTo(50);
    }

    @Test
    public void delete() throws Exception {
        // Given
        Milestone milestone = new Milestone();
        milestone.title = "test";
        milestone.project = getTestProject();
        milestone.contents = "test";
        milestone.save();

        Milestone savedMilestone = Milestone.findById(milestone.id);
        assertThat(savedMilestone).isNotNull();

        // When
        savedMilestone.delete();

        //Then
        savedMilestone = Milestone.findById(milestone.id);
        assertThat(savedMilestone).isNull();
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
            State.OPEN);
        // Then
        assertThat(p1InCmpleteMilestones.size()).isEqualTo(2);

        // Given
        // When
        List<Milestone> p2CompletedMilestones = Milestone.findMilestones(2l,
            State.CLOSED);
        // Then
        assertThat(p2CompletedMilestones.size()).isEqualTo(1);

        // Given
        // When
        List<Milestone> p2Milestones = Milestone.findMilestones(2l,
            State.ALL);
        // Then
        assertThat(p2Milestones.size()).isEqualTo(2);
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
        int totalNumber = m5.getNumTotalIssues();
        int openNumber = m5.getNumOpenIssues();
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거test";
        issue.createdDate = JodaDateUtil.today();
        issue.state = State.OPEN;
        issue.authorId = User.find.byId(1l).id;
        issue.milestone = Milestone.find.byId(5l);
        issue.project = issue.milestone.project;

        // WHEN
        issue.save();

        // THEN
        m5 = Milestone.findById(5l);
        assertThat(m5.getNumTotalIssues()).isEqualTo(totalNumber + 1);
        assertThat(m5.getNumOpenIssues()).isEqualTo(openNumber + 1);
        //To keep data clean after this test.
        issue.delete();
    }

    @Test
    public void updateIssue() throws Exception {
        //Given
        Issue issue = new Issue();
        issue.title = "불필요한 로그 출력 코드 제거test";
        issue.milestone = Milestone.find.byId(6l);
        Milestone m6 = issue.milestone;
        assertThat(m6.getNumOpenIssues()).isEqualTo(0);
        assertThat(m6.getNumClosedIssues()).isEqualTo(1);
        assertThat(m6.getNumTotalIssues()).isEqualTo(1);
        assertThat(m6.getCompletionRate()).isEqualTo(100);

        //When
        issue.update(5l);

        //Then
        m6 = Milestone.findById(m6.id);
        assertThat(m6.getNumOpenIssues()).isEqualTo(1);
        assertThat(m6.getNumClosedIssues()).isEqualTo(1);
        assertThat(m6.getNumTotalIssues()).isEqualTo(2);
        assertThat(m6.getCompletionRate()).isEqualTo(50);
    }

    @Test
    public void deleteIssue() throws Exception {
        //Given
        Issue issue = Issue.finder.byId(7l);
        Milestone m5 = issue.milestone;
        assertThat(m5.getNumOpenIssues()).isEqualTo(1);
        assertThat(m5.getNumClosedIssues()).isEqualTo(1);
        assertThat(m5.getNumTotalIssues()).isEqualTo(2);
        assertThat(m5.getCompletionRate()).isEqualTo(50);

        //When
        issue.delete();

        //Then
        m5 = Milestone.find.byId(m5.id);
        assertThat(m5.getNumOpenIssues()).isEqualTo(0);
        assertThat(m5.getNumClosedIssues()).describedAs("number of closed issues should be 1").isEqualTo(1);
        assertThat(m5.getNumTotalIssues()).describedAs("number of total issues should be 1").isEqualTo(1);
        assertThat(m5.getCompletionRate()).isEqualTo(100);
    }

    @Test
    public void isUniqueProjectIdAndTitle() {
        //Given
        //When
        boolean isUnique = Milestone.isUniqueProjectIdAndTitle(1l, "v.0.1");
        //Then
        assertThat(isUnique == false);

        //Given
        //When
        isUnique = Milestone.isUniqueProjectIdAndTitle(1l, "unique milestone");
        //Then
        assertThat(isUnique == true);
    }

    @Test
    public void untilOver() {
        // Given
        int days = 3;
        Milestone milestone = new Milestone();
        milestone.dueDate = DateUtils.truncate(DateUtils.addDays(new Date(), -days), Calendar.DATE);

        // When
        String until = milestone.until();

        // Then
        assertThat(until).isEqualTo(Messages.get("common.time.overday", days));
    }

    @Test
    public void untilToday() {
        // Given
        Milestone milestone = new Milestone();
        milestone.dueDate = DateUtils.truncate(new Date(), Calendar.DATE);

        // When
        String until = milestone.until();

        // Then
        assertThat(until).isEqualTo(Messages.get("common.time.today"));
    }

    @Test
    public void untilLeft() {
        // Given
        int days = 3;
        Milestone milestone = new Milestone();
        milestone.dueDate = DateUtils.truncate(DateUtils.addDays(new Date(), days), Calendar.DATE);

        // When
        String until = milestone.until();

        // Then
        assertThat(until).isEqualTo(Messages.get("common.time.leftday", days));
    }
}
