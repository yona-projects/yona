---
title: 마일스톤
type: web
sources: [app/controllers/MilestoneApp.java, app/models/Milestone.java, app/views/milestone/list.scala.html, app/views/milestone/create.scala.html, app/views/milestone/edit.scala.html, app/views/milestone/view.scala.html]
updated: 2026-10-22
---

# 마일스톤

구현은 `MilestoneController`, `MilestoneService`, `MilestoneStatusService`(진행 현황), 화면은 `milestone/{list,create,edit,view}.html`이다. 테스트 `MilestoneTest` 30건.

## 권한

| 요청 | 필요 권한 |
|---|---|
| 목록, 보기 | 프로젝트 읽기 |
| 만들기 폼, 만들기 | 프로젝트 안에서 마일스톤을 만들 권한(구성원). 폼은 로그인을 요구한다 |
| 수정 폼, 수정, 열기, 닫기 | 마일스톤 수정 권한(구성원). 폼은 로그인을 요구한다 |
| 삭제 | 마일스톤 삭제 권한(구성원) |

## 목록 (`GET milestones?state=&orderBy=&orderDir=`)

- 문서 제목은 `프로젝트이름 - milestone - 소유자/프로젝트`(v1.6가 제목 키를 번역하지 않고 그대로 붙인다).
- 탭 `open`(기본), `closed`, `all`. 알 수 없는 값은 열림이다.
- 정렬은 `dueDate`(기본), `completionRate`, `title`. 마감일이 없는 마일스톤은 오름차순에서 먼저, 내림차순에서 나중이다(v1.6이 쓰던 MariaDB와 같다). 완료율은 메모리에서 정렬하며 같은 값은 번호 순이다. 알 수 없는 정렬 항목은 마감일이다(A-37).
- 항목: 이름 링크, `닫힌 이슈 / 전체 이슈`, 상태 표시(`all` 탭만), 마감일과 남은 기간(`N days left`/`N days past`/`Today`, 닫힌 마일스톤은 기간 없음), 지난 마감일은 `over`, 완료율(이슈가 있을 때만), 진행 막대, 열린 이슈와 닫힌 이슈 링크(번호 순, 담당자와 라벨 표시).
- 마일스톤이 둘 이상이면 정렬 링크와 검색 상자가 보인다.

## 만들기와 수정

폼 입력은 `title`(필수, 250자까지 입력), `contents`, `state`(`OPEN`/`CLOSED`), `dueDate`(`yyyy-MM-dd`, 엄격 해석). 마감일은 그날의 마지막 초(23:59:59)로 저장한다.

- 검증 실패는 `200`으로 폼을 다시 보여 준다. 제목이 비면 `error.required`, 같은 프로젝트에 같은 제목이 있으면 `milestone.title.duplicated`(수정은 제목을 바꾼 경우만 검사), 마감일 형식이 틀리면 `milestone.error.duedateFormat` 안내.
- 오류로 폼을 다시 보여 줄 때 만들기는 상태를 항상 열림으로 표시한다. 수정은 제목과 내용을 **고치기 전의 값**으로, 상태와 마감일은 보낸 값으로 보여 준다(v1.6 템플릿 그대로).
- 수정에서 `contents`가 오지 않으면(폼 본문이 한도를 넘어 값이 사라진 경우) `413`과 `error.tooLargeText.*` 화면을 보여 준다([editor-components.md](editor-components.md)의 본문 크기 한도).
- 성공하면 `303`으로 보기 화면.
- 임시 첨부: **만들기**는 폼이 멀티파트일 때만 `temporaryUploadFiles`를 읽는데 마일스톤 폼은 멀티파트가 아니라서 옮겨지지 않는다. **수정**은 사용자의 임시 첨부를 모두 마일스톤으로 옮긴다(R-23).

## 삭제, 열기, 닫기

- 삭제: 속한 이슈의 마일스톤을 비운 뒤 지운다. 경로의 프로젝트와 마일스톤의 프로젝트가 다르면 `500`. `X-Requested-With: XMLHttpRequest`이면 `204`와 목록 주소의 `Location`, 아니면 `303` 목록.
- 열기, 닫기: 상태만 바꾸고 `303` 보기 화면.

## 보기 (`GET milestone/:id`)

제목 링크, 마감일과 남은 기간(열린 마일스톤만), 상태 배지(`badge-issue-open|closed`), 진행 막대, 내용(마크다운, 없으면 빈 칸), 첨부 목록(`data-attachments` JSON), 목록/삭제/수정/열기·닫기 버튼(권한에 따라), 삭제 확인 대화상자. 로그인한 사용자는 최근 방문 프로젝트에 기록된다.

**대기**: 이슈 탭과 이슈 목록(`#issues`)은 이슈 화면(로드맵 3c)을 옮길 때 더한다.
