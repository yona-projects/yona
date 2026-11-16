---
title: W3 v1.6 결함 수정 결과 (이슈, 마일스톤)
type: ui-contract-verification
updated: 2026-11-11
---

# W3 v1.6 결함 수정 결과 (이슈, 마일스톤)

대상 24건(`C-issue-milestone.md`에서 "v1.6 결함 유지"로 판정된 항목). 방침은 `decisions.md` A-462.

## 요약

- 고침(FIX) 12건, 그대로 둠(SKIP) 7건, 확인을 받을 것(ASK) 5건. 합계 24건.
- 커밋 9개. 시험은 모두 먼저 실패하는 것을 확인한 뒤 고쳤다(가중치 단추 숨김과 초안 후보 제외, 익명 숨김 묶음은 기존 보고서의 재현을 Red로 삼고 같은 화면 시험을 더했다).
- 돌린 시험: `IssueListTest`, `IssueMassUpdateTest`, `MilestoneTest`, `IssueCreateTest`, `IssueEditTest`, `IssueViewTest`, `IssueCommentTest`(모두 H2 통과), 화면 스크립트 `issueview`, `usermenu`, `milestoneview`(새 시험), `jquery4`, `esm`(모두 통과).

## 항목별 결과

| 항목 | 분류 | 한 일 또는 이유 | 커밋 | 시험 |
|---|---|---|---|---|
| issue-F2 | ASK | 죽은 코드(`#milestoneOption .yobicon-refresh` 처리기) 삭제는 확인 대상 | | |
| issue-F3 | FIX | 가중치 올림, 내림 단추는 수정 권한이 있을 때만 보이고 숫자는 모두에게 보인다. 이슈 별 즐겨찾기가 JSON이 아닌 실패(익명 403)를 받아도 예외 없이 "Update failed:" 알림을 띄운다(프로젝트, 조직 별도 같은 도우미 사용) | 8b874e6 | `IssueViewTest`, `usermenu.test.mjs` |
| issue-F7 | ASK | `GET nextstate`가 상태를 바꾼다. 방식과 URL을 바꾸는 일이라 확인 대상 | | |
| issue-F8 | FIX | 마감일을 지우면 `(null)`을 보이던 것을 빈 글자로 | 3bb3099 | `issueview.test.mjs` |
| issue-F9 | FIX(일부) + ASK(일부) | (b) 경로 프로젝트에 속하지 않은 이슈가 섞이면 404이고 아무것도 바꾸지 않는다. (c) 다른 프로젝트의 라벨은 붙이지 않는다(마일스톤은 issue-F11 커밋). 남은 (a) 일괄 삭제 UI 없음, (d) `MERGED`, `DRAFT`, `REJECTED` 상태 허용, 배타 분류 중복 선택 서버 검증은 ASK | 96a317b | `IssueMassUpdateTest` |
| issue-F11 | FIX(일부) | (c) 다른 프로젝트 마일스톤 번호로 작성, 수정, 일괄 변경하면 마일스톤 없음으로 처리한다. (a) `maxlength=250`과 서버 255의 차이는 영향이 없어 SKIP | df472e5 | `IssueCreateTest`, `IssueEditTest`, `IssueMassUpdateTest` |
| issue-F12 | FIX | 라벨 관리 톱니바퀴를 라벨 관리 화면이 요구하는 프로젝트 UPDATE 권한이 있을 때만 보인다 | 747d372 | `IssueListTest` |
| issue-F13 | ASK | 죽은 코드와 중복 id 정리는 삭제가 포함되어 확인 대상 | | |
| issue-F15 | SKIP | 번역 누락과 영어 title은 폴백으로 동작하는 번역 작업이다. 결함이 아니라 번역 보강 과제 | | |
| issue-F16 | FIX | 익명 화면에서 변경 감지(3초 반복 401)를 시작하지 않는다 | 2ca92a2 | `IssueViewTest` |
| issue-F18 | SKIP | 방향키 이동은 전체 이동으로 정상 동작한다. 영역 교체가 아닌 것은 의도된 동작 | | |
| issue-F19 | FIX | 부모 이슈 후보에서 초안을 뺀다 | 7cebd0b | `IssueCreateTest` |
| issue-F21 | FIX | 번역 단추(이슈, 댓글)는 로그인한 사용자에게만 보인다 | 2ca92a2 | 템플릿 확인과 `IssueViewTest` |
| issue-F22 | FIX | "이 댓글로 새 이슈" 링크는 로그인한 사용자에게만 보인다 | 2ca92a2 | `IssueViewTest` |
| issue-F23 | FIX | 일괄 변경 응답의 이동 주소로 이 사이트가 아닌 Referer를 따르지 않고 목록으로 보낸다(같은 사이트 Referer는 그대로) | 96a317b | `IssueMassUpdateTest` |
| issue-F24 | ASK | 메뉴를 꺼도 화면이 열리는 것은 정책 판단(아래) | | |
| issue-F25 | SKIP | CSP를 도입할 때만 필요한 가정이다 | | |
| milestone-F1 | FIX | 보기, 수정 폼, 수정, 열기, 닫기, 삭제 모두 경로 프로젝트에 속하지 않으면 404(삭제는 500이었다) | 9d807f6 | `MilestoneTest` |
| milestone-F3 | SKIP | 본문은 선택 입력이다. 빈 본문 허용은 오동작이 아니다 | | |
| milestone-F5 | ASK | 죽은 코드(`a.issue-label[data-color]` 처리기) 삭제는 확인 대상 | | |
| milestone-F6 | SKIP | 비로그인이 `n`을 누르면 로그인 화면으로 가고 로그인하면 폼으로 돌아온다. 정상 동작 | | |
| milestone-F7 | FIX | 열기, 닫기 요청이 실패하면(404 등) `error.failedTo` 알림창을 띄운다 | ceaa5ce | `milestoneview.test.mjs`(새 파일) |
| milestone-F8 | SKIP | 폼 재표시와 오류 문구는 정상 검증이다. 대소문자만 다른 제목 허용은 설계 선택 | | |
| milestone-F9 | SKIP | 화면에 없는 정렬 서버 경로가 동작하는 것일 뿐 영향이 없다 | | |

## v1.6과 달라진 점 (총괄이 differences-from-v16.md로 옮길 것)

- 일괄 변경(`POST /:owner/:project/issues`): v1.6은 다른 프로젝트의 이슈, 라벨, 마일스톤을 경로와 무관하게 처리했고 다른 사이트 Referer로 리다이렉트했다. 지금은 다른 프로젝트 이슈는 404, 다른 프로젝트의 라벨과 마일스톤은 무시, 외부 Referer는 목록으로 이동.
- 이슈 작성, 수정: v1.6은 다른 프로젝트 마일스톤 번호를 그대로 붙였다. 지금은 마일스톤 없음으로 처리.
- 마일스톤 6개 경로: v1.6은 번호만 보고 처리(삭제만 500)했다. 지금은 모두 경로 프로젝트 소속이 아니면 404.
- 이슈 목록 라벨 관리 톱니바퀴: v1.6은 구성원에게도 보였다(누르면 403). 지금은 관리 권한자에게만.
- 이슈 보기: 가중치 단추는 수정 권한자에게만. 익명에게는 변경 감지 요청, 번역 단추, "이 댓글로 새 이슈"가 없다. 마감일을 지우면 상태 글자가 빈다. 이슈 별 즐겨찾기 실패는 알림으로 알린다.
- 부모 이슈 후보: 초안 제외.
- 마일스톤 보기: 열기, 닫기 실패 알림.

## 확인을 받을 항목 (ASK)

1. 죽은 코드 삭제(issue-F2 `#milestoneOption` 새로고침 처리기, issue-F13의 죽은 타이머와 `or true` 가지 등과 중복 id, milestone-F5 `data-color` 처리기). 선택지: (가) 지운다 (나) 둔다. 권장: (나) 후 일괄 정리 때 참조 확인을 거쳐 지운다. 이유: A-410 오삭제 전례가 있고 사용자가 겪는 증상이 없다.
2. issue-F7 `GET .../nextstate`가 상태를 바꾼다(미리 가져오기나 링크 확인이 상태를 바꿀 수 있다). 선택지: (가) 그대로 (나) `POST`를 더하고 화면 호출을 POST로, GET은 유지 (다) GET 제거. 권장: (나). 이유: 화면 동작과 기존 링크를 유지하며 화면의 변경만 안전하게 바꾼다. URL 계약 변경이라 확인이 필요하다.
3. issue-F9 남은 부분. (d) 일괄 변경이 `DRAFT`, `MERGED`, `REJECTED` 상태를 받는다(이슈가 초안이 되는 등), 같은 배타 분류 라벨을 둘 붙일 수 있다. 선택지: (가) 그대로 (나) 상태는 OPEN, CLOSED만, 라벨은 서버에서 배타 분류를 검사해 400. 권장: (나). 이유: UI는 그 값을 보내지 않으므로 화면 영향이 없고 API 직접 호출의 잘못된 값만 막는다. 요청 계약(허용 값)이 달라지므로 확인이 필요하다.
4. issue-F24 프로젝트 설정에서 이슈, 마일스톤 메뉴를 꺼도 주소로는 계속 열린다. 선택지: (가) 그대로 (나) 메뉴가 꺼진 프로젝트의 이슈, 마일스톤 화면을 404로. 권장: (가). 이유: 메뉴는 표시 설정이라는 해석이 가능하고 기존 데이터와 링크를 깨지 않는다. 정책 판단이다.
