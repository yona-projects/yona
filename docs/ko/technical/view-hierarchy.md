# View Hierarchy

Yobi의 view hierarchy를 각 기능별로 정리한 문서입니다.

## Yobi Home

* *http://[Yobi URL]/*
* views.layout.scala.html
	* views.siteLayout.scala.html
		* views.common.usermenu.scala.html
		* if
			* views.index.partial_intro.scala.html
		* views.common.alert.scala.html
        * **views.index.index.scala.html**
        * if
            * views.index.partial_notifications.scala.html
            * views.index.myprojectList.scala.html

## 계정

### 로그인

* *http://[Yobi URL]/users/loginform*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.user.login.scala.html**

### 회원가입

* *http://[Yobi URL]/users/signupform*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.user.signup.scala.html**

### 아바타 > 프로필

* *http://[Yobi URL]/[UserName]?daysAgo=&selected=*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.user.view.scala.html**
            * views.user.partial_projectlist.scala.html
            * views.user.partial_postings.scala.html
            * views.user.partial_issues.scala.html
            * views.user.partial_pullRequests.scala.html
            * views.user.partial_milestones.scala.html

### 아바타 > 설정

* *http://[Yobi URL]/user/editform*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.user.edit.scala.html**

## 도움말

* *http://[Yobi URL]/help*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.help.toc.scala.html**

## 프로젝트

### 프로젝트 목록

* *http://[Yobi URL]/projects*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.project.list.scala.html**

### 새 프로젝트 만들기

* *http://[Yobi URL]/projectform*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.project.create.scala.html**

#### 새 프로젝트 만들기 > Git 저장소에서 코드 가져오기

* *http://[Yobi URL]/import*
* views.layout.scala.html
    * views.siteLayout.scala.html
        * views.common.usermenu.scala.html
        * views.common.alert.scala.html
        * **views.project.importing.scala.html**

### 프로젝트 홈

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.project.overview.scala.html**
            * views.projectMenu.scala.html
            * views.common.markdown.scala.html

## 게시판

### 게시판 홈

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/posts*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.board.list.scala.html**
            * views.projectMenu.scala.html
            * views.board.partial_list.scala.html
            * views.help.keymap.scala.html

### 게시판 > 글쓰기

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/postsform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.board.create.scala.html**
            * views.projectMenu.scala.html
            * views.help.markdown.scala.html
            * if
                * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

### 게시판 > 항목선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/post/[post id]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.board.view.scala.html**
            * views.projectMenu.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.help.keymap.scala.html
            * views.common.markdown.scala.html

### 게시판 > 항목선택 > 수정

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/post/[post id]/editform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.board.edit.scala.html**
            * views.projectMenu.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

## 코드

### 코드 > 파일

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/code/master*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.code.view.scala.html**
            * views.code.partial_branchitem.scala.html

### 코드 > 파일 > 파일선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/code/[branch]/[path]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.code.view.scala.html**
            * views.code.partial_branchitem.scala.html

### 코드 > 파일 > 파일선택 > 변경이력 ( > [파일보기] )

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/commits/[branch]/[path]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.code.history.scala.html**
            * views.projectMenu.scala.html
            * views.code.partial_branchitem.scala.html

### 코드 > 파일 > 파일선택 > 변경이력 > 항목선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/commit/[commit id]?branch=[branch]?path=[path]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * if
            * **views.code.svnDiff.scala.html**
            * views.projectMenu.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html
        * else
            * **views.code.diff.scala.html**
            * views.projectMenu.scala.html
            * views.code.partial_branchitem.scala.html
            * views.partial_diff.scala.html
                * views.partial_filediff.scala.html
                * views.common.mergely.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html
            * views.common.mergely.scala.html


### 코드 > 커밋

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/commits/[branch]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.code.history.scala.html**
            * views.projectMenu.scala.html
            * views.code.partial_branchitem.scala.html

### 코드 > 커밋 > 항목선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/commit/[commit id]?branch=[branch]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * if
            * **views.code.svnDiff.scala.html**
            * views.projectMenu.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html
        * else
            * **views.code.diff.scala.html**
            * views.projectMenu.scala.html
            * views.code.partial_branchitem.scala.html
            * views.partial_diff.scala.html
                * views.partial_filediff.scala.html
                * views.common.mergely.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html
            * views.common.mergely.scala.html

## 코드주고받기

### 코드주고받기 홈

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/pullRequests*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.git.list.scala.html**
            * views.projectMenu.scala.html
            * views.git.partial_recentlry_pushed_branches.scala.html
            * views.git.partial_list.scala.html

### 코드주고받기 > 이 프로젝트에 코드 보내기

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/newPullRequestForm*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.git.create.scala.html**
            * views.projectMenu.scala.html
            * views.git.partial_diff.scala.html
                * views.common.fileUploader.scala.html
                * views.common.markdown.scala.html
                * if
                    * views.partial_diff.scala.html
                        * views.partial_filediff.scala.html
                        * views.common.mergely.scala.html

### 코드주고받기 > 항목선택 > 개요

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/pullRequest/[pullrequest id]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.git.view.scala.html**
            * views.projectMenu.scala.html
            * views.git.partial_info.scala.html
            * views.git.partial_state.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

### 코드주고받기 > 항목선택 > 커밋

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/pullRequest/[pullrequest id]/commits*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.git.viewCommits.scala.html**
            * views.projectMenu.scala.html
            * views.git.partial_info.scala.html
            * views.common.markdown.scala.html

### 코드주고받기 > 항목선택 > 커밋 > 항목선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/pullRequest/[pullrequest id]/commit/[commit id]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.git.diff.scala.html**
            * views.partial_diff.scala.html
                * views.partial_filediff.scala.html
                * views.common.mergely.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html
            * views.common.mergely.scala.html

### 코드주고받기 > 항목선택 > 변경내역

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/pullRequest/[id]/changes*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.git.viewChanges.scala.html**
            * views.projectMenu.scala.html
            * views.git.partial_info.scala.html
            * if
                * views.partial_diff.scala.html
                    * views.partial_filediff.scala.html
                    * views.common.mergely.scala.html
            * else
                * views.git.partial_state.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html
            * views.common.mergely.scala.html

## 이슈

### 이슈 홈

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/issues?state=open*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.issue.list.scala.html**
            * views.projectMenu.scala.html
            * views.issue.partial_search.scala.html
                * views.milestone.partial_status.scala.html
                * if
                    * views.issue.partial_massupdate.scala.html
                    * views.issue.partial_list.scala.html
                    * views.help.keymap.scala.html
                * else
                    * views.help.keymap.scala.html

### 이슈 > 새 이슈

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/issueform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.issue.create.scala.html**
            * views.projectMenu.scala.html
            * views.help.markdown.scala.html
            * views.help.experimental.scala.html
            * if
                * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

### 이슈 > 항목선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/issue/[issue id]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.issue.view.scala.html**
            * views.projectMenu.scala.html
            * views.common.commentForm.scala.html
                * views.help.markdown.scala.html
                * if
                    * views.common.fileUploader.scala.html
            * views.help.keymap.scala.html
            * views.common.markdown.scala.html

### 이슈 > 항목선택 > 수정

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/issue/[issue id]/editform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.issue.edit.scala.html**
            * views.projectMenu.scala.html
            * views.help.markdown.scala.html
            * views.help.experimental.scala.html
            * if
                * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

## 마일스톤

### 마일스톤

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/milestones*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.milestone.list.scala.html**
            * views.projectMenu.scala.html

### 마일스톤 > 새 마일스톤

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/newMilestoneForm*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.milestone.create.scala.html**
            * views.projectMenu.scala.html
            * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

### 마일스톤 > 항목선택

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/milestone/[milestone id]*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.milestone.view.scala.html**
            * views.projectMenu.scala.html
            * views.issue.partial_massupdate.scala.html
            * views.issue.partial_list.scala.html
            * views.common.markdown.scala.html

### 마일스톤 > 수정

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/milestone/[milestone id]/editform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.milestone.edit.scala.html**
            * views.projectMenu.scala.html
            * views.help.markdown.scala.html
            * views.help.experimental.scala.html
            * if
                * views.common.fileUploader.scala.html
            * views.common.markdown.scala.html

## 프로젝트 설정

### 프로젝트 > 설정

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/settingform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.project.setting.scala.html**
            * views.projectMenu.scala.html
            * views.project.partial_settingmenu.scala.html

### 프로젝트 > 멤버

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/members*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.project.members.scala.html**
            * views.projectMenu.scala.html
            * views.project.partial_settingmenu.scala.html

### 프로젝트 > 프로젝트 삭제

* *http://[Yobi URL]/[ProjectCreator]/[ProjectName]/deleteform*
* views.layout.scala.html
    * views.projectLayout.scala.html
        * views.project.navbar.scala.html
        * views.project.header.scala.html
        * **views.project.delete.scala.html**
            * views.projectMenu.scala.html
            * views.project.partial_settingmenu.scala.html

