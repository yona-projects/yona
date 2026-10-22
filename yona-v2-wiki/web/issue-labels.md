---
title: 이슈 라벨과 라벨 분류
type: web
sources: [app/controllers/IssueLabelApp.java, app/models/IssueLabel.java, app/models/IssueLabelCategory.java, app/views/project/issuelabels.scala.html, app/views/project/partial_issuelabels_*.scala.html, app/views/common/issueLabelColor.scala.html, public/javascripts/service/yobi.issue.LabelEditor.js]
updated: 2026-10-22
---

# 이슈 라벨과 라벨 분류

프로젝트 설정의 "Issue Label" 탭(`GET /:user/:project/issue/labelsform`)과 편집 화면 스크립트(`issue.LabelEditor`)가 쓰는 요청을 옮겼다. 구현은 `IssueLabelController`, `IssueLabelService`, `IssueLabelStyles`, 화면은 `project/issue-labels.html`과 `project/partial-issue-labels-list.html`이다. 테스트 `IssueLabelTest` 31건.

## 권한

| 요청 | 필요 권한 |
|---|---|
| 라벨 목록(JSON, PJAX), 분류 조회, `labels.css` | 프로젝트 읽기 |
| 관리 화면 | 프로젝트 수정(관리자) |
| 라벨, 분류 만들기, 복사 | 프로젝트 안에서 라벨, 분류를 만들 권한(구성원) |
| 라벨, 분류 수정, 삭제 | 해당 리소스의 수정, 삭제 권한(관리자) |

라벨, 분류는 번호만으로 찾기 때문에 경로의 프로젝트와 달라도 통과한다(R-12, v1.6과 같다).

## 요청

| 요청 | 입력 | 응답 |
|---|---|---|
| `GET issue/labels` | `X-PJAX: true`이면 목록 조각 | 조각은 `Cache-Control: no-cache, no-store`. 아니면 JSON `[{id, category, categoryId, color, name}]`(분류 이름, 라벨 이름순). JSON을 받지 못하는 `Accept`는 `406` |
| `POST issue/labels` | `labelName`, `labelColor`, `categoryName`, `categoryIsExclusive` | 항목별 오류는 `400`과 `{필드: [문구]}`. 같은 분류에 같은 이름이 있으면 `204`. 만들면 `201`(JSON을 받으면 라벨을 담는다). 분류가 이미 있으면 그대로 쓰고 `categoryIsExclusive`는 무시한다 |
| `POST issue/label/:id/delete` | `_method=delete` | `200`. 없거나 다르면 `400`. 이슈와 게시글에서 떼고, 분류에 남은 라벨이 없으면 분류도 지운다 |
| `PUT issue/label/:id` | `name`, `color`, `category.id` | `200`. 필수 항목이 비면 `400`과 항목별 오류 |
| `GET issue/labels.css` | | 라벨 색 스타일 시트. `ETag`가 같으면 `304` |
| `GET issue/label/categories`, `GET issue/label/category/:id` | | JSON `{id, name, isExclusive}`. JSON을 받지 못하면 `406` |
| `POST issue/label/categories` | `name`, `isExclusive`, `project.id` | 이름이 겹치면 `204`, 만들면 `201`, 입력 오류는 본문 없는 `400` |
| `PUT issue/label/category/:id` | `name`, `isExclusive`, `project.id` | `200`. 같은 프로젝트의 다른 분류와 이름이 겹치면 `400` |
| `DELETE issue/label/category/:id` | | `200`. 속한 라벨도 함께 지운다 |
| `POST copyLabels` | `owner`, `projectName` | 읽을 수 있는 프로젝트의 라벨을 가져온 뒤 `303` 관리 화면. 분류는 이름으로 찾거나 같은 설정으로 만들고, 같은 분류, 이름의 라벨은 건너뛴다(색은 유지) |

이름 길이의 상한은 안내 문구에 250자라고 되어 있지만 실제 검사는 255자이다(v1.6과 같다).

## 라벨 색

`labels.css`는 라벨마다 `.issue-label[data-label-id]`(왼쪽 색 띠)와 `.issue-label.active[data-label-id]`(배경색과 글자색)를 만든다. 글자색은 배경 밝기(`R*0.21+G*0.72+B*0.07`)가 192를 넘으면 `dimgray`, 아니면 `white`이다. **3자리 16진수(`#abc`)는 흰색으로 처리된다**(v1.6 결함 재현, R-22).

## 구조가 달라진 곳

- v1.6의 `ETag`는 `List.hashCode()`(Ebean 엔티티 해시 포함)였다. v2는 (번호, 색, 이름) 목록의 해시를 쓴다. 라벨이 바뀌면 달라진다는 성질이 같다.
- 라벨을 지울 때 v1.6은 이슈의 연결 표만 정리했다. v2는 게시글(`posting_issue_label`)의 연결도 정리한다(A-33).
- 라벨 수정에서 다른 프로젝트의 분류로 옮기는 요청은 거부한다. 화면은 같은 프로젝트의 분류만 고르게 한다(A-24).
