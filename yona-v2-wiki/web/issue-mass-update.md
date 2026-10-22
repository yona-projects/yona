---
title: 이슈 일괄 변경
type: web
sources: [app/controllers/IssueApp.java:389-560, app/models/IssueMassUpdate.java, public/javascripts/service/yobi.issue.View.js:170-250]
updated: 2026-10-23
---

# 이슈 일괄 변경

`POST /:user/:project/issues`이다. 이슈 목록의 일괄 변경 막대와 이슈 보기 오른쪽 창(상태, 담당자, 마일스톤, 라벨, 마감일)이 같은 주소를 쓴다. 구현은 `IssueMassUpdateController`와 `IssueMassUpdateService`이고 테스트는 `IssueMassUpdateTest` 10건이다.

## 요청

- `issues[0].id`, `issues[1].id`, ...: 대상 이슈(필수, 없으면 `400`).
- `state`, `assignee.id`(`-1`이면 담당자 비움), `milestone.id`(`-1`이면 마일스톤 비움), `dueDate`(`yyyy-MM-dd`)와 `isDueDateChanged=true`(비운 값이면 마감일 삭제), `attachingLabelIds`, `detachingLabelIds`(`이름`, `이름[]`, `이름[0]` 모두 받는다), `delete=true`.
- 보내지 않은 항목은 바꾸지 않는다.

## 동작

1. 초안은 건너뛴다.
2. `delete`이면 삭제 권한이 있는 이슈를 지우고(이슈 보기의 삭제와 같은 처리), 없으면 거부 건수에 넣는다.
3. 수정 권한이 없는 이슈는 거부 건수에 넣는다. 하나도 바꾸지 못했고 거부가 있으면 `403`.
4. 바뀐 항목마다 타임라인 이벤트와 `IssueChangedActivity`를 남긴다. 담당자, 상태, 마일스톤은 연달아 바꾼 중간 값을 합치고(되돌리면 둘 다 지운다), 라벨은 합치지 않는다. 마일스톤 이벤트 값은 번호이고 없음은 `-1`이다. 라벨 이벤트 값은 `분류 - 이름 색` 형식이다(여러 개면 구분자 없이 이어 붙인다).
5. 마감일은 그날의 마지막 초(23:59:59)로 맞춘다. 모든 이슈의 수정 시각을 갱신한다.

## 응답

- `Accept`가 JSON을 선호하면: 마감일을 바꿨을 때 `{"isOverDue":…,"dueDateMsg":…}`, 아니면 `{}`.
- 아니면 `Referer`로 `303`.
