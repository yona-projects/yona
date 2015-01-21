/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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

import com.avaje.ebean.Page;
import models.enumeration.SearchType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SearchResult {

    private String keyword;
    private SearchType searchType;

    private int usersCount;
    private int projectsCount;
    private int issuesCount;
    private int postsCount;
    private int milestonesCount;
    private int issueCommentsCount;
    private int postCommentsCount;
    private int reviewsCount;

    private Page<User> users;
    private Page<Project> projects;
    private Page<Issue> issues;
    private Page<Posting> posts;
    private Page<Milestone> milestones;
    private Page<IssueComment> issueComments;
    private Page<PostingComment> postComments;
    private Page<ReviewComment> reviews;

    public List<String> makeSnippets(String contents, int threshold) {
        String lowerCaseContents = contents.toLowerCase();
        String lowerCaseKeyword = keyword.toLowerCase();

        LinkedList<BeginAndEnd> beginAndEnds = new LinkedList<>();
        List<Integer> indexes = findIndexes(lowerCaseContents, lowerCaseKeyword); // 6, 40

        for(int i = 0 ; i < indexes.size() ; i++) {
            int currentIndex = indexes.get(i);
            int beginIndex = beginIndex(currentIndex, threshold);
            int endIndex = endIndex(currentIndex + lowerCaseKeyword.length(), lowerCaseContents.length(), threshold);
            BeginAndEnd thisOne = new BeginAndEnd(beginIndex, endIndex);
            if(i == 0) {
                beginAndEnds.push(thisOne);
            } else {
                BeginAndEnd latestOne = beginAndEnds.peek();
                if(latestOne.getEndIndex() >= thisOne.getBeginIndex()) {
                    BeginAndEnd mergedOne = new BeginAndEnd(latestOne.getBeginIndex(), thisOne.getEndIndex());
                    beginAndEnds.pop();
                    beginAndEnds.push(mergedOne);
                } else {
                    beginAndEnds.push(thisOne);
                }
            }
        }

        Collections.reverse(beginAndEnds);

        List<String> snippets = new ArrayList<>();
        for(BeginAndEnd bae : beginAndEnds) {
            snippets.add(contents.substring(bae.beginIndex, bae.endIndex));
        }

        return snippets;
    }

    private List<Integer> findIndexes(String contents, String keyword) {
        List<Integer> indexes = new ArrayList<>();
        int index = contents.indexOf(keyword);
        while (index != -1) {
            indexes.add(index);
            index = contents.indexOf(keyword, index + keyword.length());
        }
        return indexes;
    }

    private int beginIndex(int index, int threshold) {
        return index < threshold ? 0 : index - threshold;
    }

    private int endIndex(int keywordEndIndex, int contentLength, int threshold) {
        int endIndex = keywordEndIndex + threshold;
        return endIndex < contentLength ? endIndex : contentLength;
    }

    public void updateSearchType() {
        if(!(this.searchType == SearchType.AUTO)) {
            return;
        }

        if (getIssuesCount() > 0) {
            setSearchType(SearchType.ISSUE);
            return;
        }

        if (getUsersCount() > 0) {
            setSearchType(SearchType.USER);
            return;
        }

        if (getProjectsCount() > 0) {
            setSearchType(SearchType.PROJECT);
            return;
        }

        if (getPostsCount() > 0) {
            setSearchType(SearchType.POST);
            return;
        }

        if (getMilestonesCount() > 0) {
            setSearchType(SearchType.MILESTONE);
            return;
        }

        if (getIssueCommentsCount() > 0) {
            setSearchType(SearchType.ISSUE_COMMENT);
            return;
        }

        if (getPostCommentsCount() > 0) {
            setSearchType(SearchType.POST_COMMENT);
            return;
        }

        if (getReviewsCount() > 0) {
            setSearchType(SearchType.REVIEW);
            return;
        }

        setSearchType(SearchType.ISSUE);
    }

    private class BeginAndEnd {
        int beginIndex;
        int endIndex;

        BeginAndEnd(int beginIndex, int endIndex) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
        }

        public int getBeginIndex() {
            return beginIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        @Override
        public String toString() {
            return "BeginAndEnd{" +
                    "beginIndex=" + beginIndex +
                    ", endIndex=" + endIndex +
                    '}';
        }
    }


    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getUsersCount() {
        return usersCount;
    }

    public void setUsersCount(int usersCount) {
        this.usersCount = usersCount;
    }

    public int getProjectsCount() {
        return projectsCount;
    }

    public void setProjectsCount(int projectsCount) {
        this.projectsCount = projectsCount;
    }

    public int getIssuesCount() {
        return issuesCount;
    }

    public void setIssuesCount(int issuesCount) {
        this.issuesCount = issuesCount;
    }

    public int getPostsCount() {
        return postsCount;
    }

    public void setPostsCount(int postsCount) {
        this.postsCount = postsCount;
    }

    public int getMilestonesCount() {
        return milestonesCount;
    }

    public void setMilestonesCount(int milestonesCount) {
        this.milestonesCount = milestonesCount;
    }

    public int getIssueCommentsCount() {
        return issueCommentsCount;
    }

    public void setIssueCommentsCount(int issueCommentsCount) {
        this.issueCommentsCount = issueCommentsCount;
    }

    public int getPostCommentsCount() {
        return postCommentsCount;
    }

    public void setPostCommentsCount(int postCommentsCount) {
        this.postCommentsCount = postCommentsCount;
    }

    public int getReviewsCount() {
        return reviewsCount;
    }

    public void setReviewsCount(int reviewsCount) {
        this.reviewsCount = reviewsCount;
    }


    public Page<User> getUsers() {
        return users;
    }

    public void setUsers(Page<User> users) {
        this.users = users;
    }

    public Page<Project> getProjects() {
        return projects;
    }

    public void setProjects(Page<Project> projects) {
        this.projects = projects;
    }

    public Page<Issue> getIssues() {
        return issues;
    }

    public void setIssues(Page<Issue> issues) {
        this.issues = issues;
    }

    public Page<Posting> getPosts() {
        return posts;
    }

    public void setPosts(Page<Posting> posts) {
        this.posts = posts;
    }

    public Page<Milestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(Page<Milestone> milestones) {
        this.milestones = milestones;
    }

    public Page<IssueComment> getIssueComments() {
        return issueComments;
    }

    public void setIssueComments(Page<IssueComment> issueComments) {
        this.issueComments = issueComments;
    }

    public Page<PostingComment> getPostComments() {
        return postComments;
    }

    public void setPostComments(Page<PostingComment> postComments) {
        this.postComments = postComments;
    }

    public Page<ReviewComment> getReviews() {
        return reviews;
    }

    public void setReviews(Page<ReviewComment> reviews) {
        this.reviews = reviews;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

}
