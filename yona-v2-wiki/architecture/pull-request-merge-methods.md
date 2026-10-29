---
title: 풀 리퀘스트 병합 방식
type: architecture
sources:
  - app/models/PullRequest.java:356-570 (60a5ac406)
  - app/controllers/PullRequestApp.java (accept, 60a5ac406)
updated: 2026-10-29
---

# 풀 리퀘스트 병합 방식 (X-28, D-41)

v1.6은 병합 커밋 하나를 만드는 방식뿐이었다. 추가 개발 항목 X-28은 GitHub처럼 병합 커밋, 스쿼시, 리베이스를 프로젝트 설정으로 허용하고, 병합 화면에서 고르며, 각 방식이 가능한지 병합하기 전에 보여 준다. 결정은 [D-41](../decisions.md)이다.

## 1. 설정

`project` 컬럼 네 개이다.

| 컬럼 | 기존 프로젝트와 v1.6 가져오기 | 새 프로젝트와 새 포크 |
|---|---|---|
| `allow_merge_commit` | 허용 | 허용 |
| `allow_squash_merge` | 불허 | 허용 |
| `allow_rebase_merge` | 불허 | 허용 |
| `default_merge_method` | `merge` | `merge` |

DB 기본값이 기존 행을 v1.6 동작(병합 커밋만)으로 채우므로 업그레이드하면 동작이 바뀌지 않는다. 프로젝트 설정 화면의 "풀 리퀘스트 병합 방식" 영역(`#mergeMethodSettingPanel`)에서 바꾸고, 허용한 방식이 하나 이상이어야 하며 기본 방식은 허용한 방식이어야 한다(아니면 400). v1.6 데이터 가져오기 파일에 이 컬럼이 없으면 병합 커밋만 허용으로 읽는다(`DataColumn.absentBoolean`, `absentString`).

## 2. 병합 구현 (`PullRequestMerger.merge`)

| 방식 | 결과 |
|---|---|
| 병합 커밋 | v1.6과 같다. 부모가 둘이고 `Reviewed-by:` 꼬리말이 있다 |
| 스쿼시 | 같은 병합 결과 트리, 부모는 받는 브랜치 머리 하나. 제목 `제목 (#번호)`, 본문은 포함된 커밋 제목 목록과 `Reviewed-by:`. 작성자는 풀 리퀘스트 작성자, 커미터는 병합한 사람 |
| 리베이스 | 원래 커밋을 오래된 것부터 받는 브랜치 위에 하나씩 다시 적용한다. 메시지와 작성자는 그대로이고 커미터만 병합한 사람이다 |

리베이스는 `V16ResolveMerger`(기준 트리를 직접 정하는 세 방향 병합)로 커밋마다 원래 부모 트리를 기준으로 병합한다. 한 커밋에서라도 충돌하거나 병합 커밋이 섞여 있으면 아무것도 바꾸지 않고 실패한다. 병합 커밋과 스쿼시는 제목(`commitTitle`)과 본문(`commitMessage`)을 직접 줄 수 있고, 리베이스는 무시한다.

## 3. 가능 여부 (병합 전에 보여 주기)

- 병합 커밋과 스쿼시는 기존 충돌 판정(`pull_request.is_conflict`)을 그대로 쓴다.
- 리베이스는 병합 검사(`PullRequestMergeProcessor`)가 저장소를 바꾸지 않고 시뮬레이션해 `pull_request.rebase_blocked`에 저장한다. 값은 `null`(가능), `conflict`, `merge-commits`이다.
- 시뮬레이션은 트리만 합치므로 커밋이나 참조를 만들지 않는다. 커밋이 200개를 넘으면 시험하지 않고 가능한 것으로 본다.
- 새 커밋이 들어오거나 병합 검사를 다시 하면 함께 다시 계산한다.

## 4. 화면과 REST

- 보기 화면: GitHub, Forgejo처럼 분할 단추이다. 주 단추(`#btnAccept`)는 선택한 방식의 이름이고, 옆의 화살표(`#btnMergeOptions`)가 부트스트랩 드롭다운인 방식 목록(`#merge-method-menu`)을 연다. 목록에는 허용한 방식만 있고 항목마다 설명이 붙으며, 할 수 없는 방식은 이유와 함께 회색으로 막힌다. 허용한 방식이 하나뿐이면 화살표와 목록이 없다. 주 단추를 누르면 실제로 기록될 커밋 제목과 본문을 미리 채운(`PullRequestMerger.defaults`) 확인 칸(`form#merge-options`, 리베이스는 입력칸을 감춘다)이 열리고 확인을 눌러야 병합한다.
- 웹 요청: `POST .../accept`에 `method`, `commitTitle`, `commitMessage`를 더할 수 있다. 모르는 방식이나 허용하지 않은 방식은 400이다.
- 타임라인: 병합 이벤트의 이전 값(`old_value`)에 `squash`나 `rebase`를 남기고 "스쿼시로 병합했습니다", "리베이스로 병합했습니다"로 보인다. 병합 커밋은 v1.6 행과 문구 그대로이다.
- REST: `POST .../merge`의 본문 `method`, `commitTitle`, `commitMessage`. 상세 응답에 `mergeMethod`(병합된 요청만)와 `mergeMethods`(방식마다 `allowed`, `available`, `reason`)가 있다. 자세한 계약은 [api-pull-requests.md](api-pull-requests.md)이다.

## 5. v1.6과 다른 점

[differences-from-v16.md](differences-from-v16.md)의 X-28 행과 `decisions.md`의 A-239부터 A-243을 본다.
