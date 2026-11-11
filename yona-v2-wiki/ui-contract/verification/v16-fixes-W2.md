---
title: v1.6에서 물려받은 결함 수정 결과 W2 (코드, 코드 주고받기, 프로젝트)
type: ui-contract-verification
updated: 2026-11-11
---

# 요약

담당 35건(code 11, project 17, pullrequest 7)을 모두 분류했다. 근거는 `B-code-pullrequest.md`와 `E-project.md`의 재현 결과이다.

| 분류 | 개수 | 항목 |
|---|---|---|
| FIX(고침) | 20 | code-F2, F3, F8, F15 / project-F1, F6, F7, F8, F9, F10, F14, F18, F19, F20, F22, F24 / pullrequest-F6, F9, F17, F18 |
| SKIP(그대로 둠) | 9 | code-F9, F10, F11, F14, F17 / project-F11, F23 / pullrequest-F11, F16 |
| ASK(확인 받을 것) | 6 | code-F4, F12 / project-F12, F17, F21 / pullrequest-F5 |

- 시험은 먼저 실패하는 것을 확인했다(JS는 원본 파일로 바꿔 끼워 실패 확인, Kotlin은 수정한 원본 13개 파일을 HEAD 것으로 바꿔 끼워 13건 실패 확인 뒤 복원). 이후 같은 클래스를 다시 돌려 통과했다.
- 수정 커밋은 13개이다(JS 4개, 서버와 템플릿 9개). 표의 해시는 앞 7자리이다.

# 항목별 결과

| 항목 | 분류 | 한 일 또는 이유 | 커밋 | 시험 |
|---|---|---|---|---|
| code-F2 | FIX | v1.6은 Subversion 비교 화면의 리비전 링크가 `undefined/1/a.txt`였다. 지금은 `compare-svn.html`이 `sCodeURL`(`filesUrl`)을 넘겨 `/<소유자>/<프로젝트>/code/1/a.txt`가 된다 | 5611845 | `SvnCommitViewWebTest` |
| code-F3 | FIX | v1.6은 코드 접근을 구성원에게만 연 프로젝트에서 `files/...`와 `compare/...`가 비구성원에게 200이었다. 지금은 두 화면에 `@CodeAccessCheck`를 걸어 403이다 | 5611845 | `CodeBrowserWebTest` |
| code-F4 | ASK | 존재하지 않는 `#review-editor`를 겨냥한 `yona.Mention` 호출은 죽은 코드이다. 지우는 일은 확인 대상(아래) | | |
| code-F8 | FIX | v1.6은 커밋 화면에서 파일 머리를 눌러도 접히지 않았다(처리기가 PR 변경 탭에만 있음). 지금은 `code/diff.html`에도 같은 처리기가 있다 | 1ae288b | `TemplateContractRegressionTest` |
| code-F9 | SKIP | 서버가 쓰지 않는 필드를 함께 보낼 뿐 사용자에게 보이는 오동작이 없다 | | |
| code-F10 | SKIP | 스레드 단추와 답글 폼의 표시 차이는 영향 없는 잔재이고 동작에 문제가 없다 | | |
| code-F11 | SKIP | Git 커밋 화면에 미니맵이 없는 것은 기능 차이이지 오동작이 아니다(v1.6도 Subversion에만 있음) | | |
| code-F12 | ASK | 같은 스레드가 커밋 화면 카드에는 2, PR 변경 탭 카드에는 1로 보인다. 어느 쪽을 기준으로 맞출지는 정책 판단(아래) | | |
| code-F14 | SKIP | 파일 단위 스레드의 삭제가 확인 없이 지워지는 것은 두 화면의 설계 차이이고 요청은 정상 동작한다 | | |
| code-F15 | FIX | v1.6 일본어 번들에는 코드 화면 문구 13개가 없어 영어로 나왔다. 지금은 일본어로 채웠다 | 1fbfce2 | `MessageParityTest` |
| code-F17 | SKIP | 링크 인코딩 방식은 새 UI가 따라야 할 URL 계약이라 바꾸지 않는다 | | |
| project-F1 | FIX | v1.6 한국어 번들은 대문자 `IncludeGitPush`만 있어 웹훅 화면의 `includeGitPush`가 영어로 나왔다(일본어, 러시아어, 우즈베크어도 없음). 지금은 네 번들에 `project.webhook.includeGitPush`를 더했다 | 1fbfce2 | `MessageParityTest` |
| project-F6 | FIX | v1.6은 코드 접근이 막힌 사용자에게도 `c`, `p` 단축키를 주어 눌러 보면 권한 없음 화면으로 갔다. 지금은 탭과 같은 조건(`codeAccessible`)일 때만 단축키가 있다 | 1ae288b | `TemplateContractRegressionTest` |
| project-F7 | FIX | v1.6은 일반 구성원에게 구성원 추가 링크가 보였지만 누르면 403이었다. 지금은 프로젝트 수정 권한이 있을 때만 보인다 | 4e6d417 | `ProjectHomeTest` |
| project-F8 | FIX | v1.6은 구성원이 아닌 사이트 관리자에게도 탈퇴 단추가 보였고 눌러도 아무 일이 없었다. 지금은 역할을 가진 구성원일 때만 보인다 | 4e6d417 | `ProjectHomeTest` |
| project-F9 | FIX | v1.6은 `data-request-method` 단추의 요청이 실패해도 알림이 없었다(웹훅 삭제 등). 지금은 `request:error`를 취소하지 않은 요소가 상태에 맞는 오류 알림(권한 없음, 없음, 서버 오류)을 띄운다. 직접 알리는 쪽은 `preventDefault()`한다 | 1ec2ff0 | `requestas.test.mjs` |
| project-F10 | FIX | v1.6은 탈퇴 단추를 누를 때마다 "예" 처리기가 쌓여 아니오 뒤 다시 예를 누르면 DELETE가 여러 번 나갔다. 지금은 이전 처리기를 떼고 단다 | 4ecead5 | `projecthome.test.mjs` |
| project-F11 | SKIP | "Under Construction" 자리 화면은 링크가 없고 기능이 아직 없는 것이다(오동작 아님) | | |
| project-F12 | ASK | 영어 고정 문구의 else 가지는 어떤 설정에서도 그려지지 않는 죽은 가지이다. 정리는 확인 대상(아래) | | |
| project-F14 | FIX | v1.6은 설명 입력란에서 Enter를 눌러도 저장되지 않았고, 저장 요청이 실패하면 콘솔 기록뿐이었다. 지금은 Enter가 저장이고 실패하면 오류 알림을 띄우며 편집 상태를 유지한다 | 4ecead5 | `projecthome.test.mjs` |
| project-F17 | ASK | 화면에서 쓰지 않는 라벨 조회 경로이다. 정리(삭제)는 확인 대상(아래) | | |
| project-F18 | FIX | v1.6은 라벨 삭제 요청이 실패해도 알림이 없었다. 지금은 `Failed to Delete.` 오류 알림을 띄우고 행은 그대로 둔다 | 1657148 | `labeleditor.test.mjs` |
| project-F19 | FIX | v1.6은 프로젝트 이전의 "예"가 `.one`이라 한 번 실패하면 다시 눌러도 반응이 없었다. 지금은 요청 중에만 중복을 막고 실패하면 다시 보낼 수 있다 | 9babb1c | `projectsettings.test.mjs` |
| project-F20 | FIX | v1.6은 웹훅 형식 JSON을 고를 때 푸시 포함 체크를 `attr('checked')`로 바꿔, 체크를 한 번 눌러 본 뒤에는 체크가 꺼진 채 잠겼고 `gitPush=false`로 저장되었다. 지금은 `prop`으로 바꾼다 | 1ae288b | `TemplateContractRegressionTest` |
| project-F21 | ASK | 웹훅 비밀 값이 목록에 평문으로 보인다. 가릴지 말지는 정책 판단(아래) | | |
| project-F22 | FIX | v1.6은 `GET /users?query=`가 사용자 이름을 이스케이프 없이 HTML 조각에 담아 이름에 든 태그가 자동 완성 목록에 요소로 삽입되었다. 지금은 이름과 아이디를 이스케이프한다 | d5bcd4c | `UserApiTest` |
| project-F23 | SKIP | 설정 저장 뒤 성공 안내가 없는 것은 v1.6의 화면 흐름(리다이렉트)이고 값은 정상 저장된다. 오류 화면이 입력값 대신 저장된 값을 다시 그리는 것은 폼 전체의 재바인딩이 필요한 구조 변경이라 이번에는 건드리지 않는다 | | |
| project-F24 | FIX | v1.6은 권한을 라벨이 속한 프로젝트로 판정하고 경로의 프로젝트는 보지 않아, 두 프로젝트의 구성원이 내 프로젝트 경로로 다른 프로젝트의 라벨을 지울 수 있었다. 지금은 경로의 프로젝트가 라벨의 프로젝트와 다르면 404이다(삭제와 수정 둘 다) | fe83631 | `IssueLabelTest` |
| pullrequest-F5 | ASK | 병합된 요청의 보낸 사람(받는 프로젝트의 비구성원)에게 보이는 `브랜치 삭제` 단추가 항상 403이다. 해결 방식이 권한 정책이라 확인 대상(아래) | | |
| pullrequest-F6 | FIX | v1.6은 이미 병합되었거나 닫힌 요청에도 받아들이기를 다시 받아 처리하려 했고, 병합된 요청을 닫으면 상태가 `닫힘`이 되었다. 지금은 열려 있지 않은 요청의 받아들이기와 닫기는 400이다(다시 열기와 같은 규칙) | 8a1cda1 | `PullRequestActionTest` |
| pullrequest-F9 | FIX | v1.6은 최근 푸시 알림의 닫기 단추가 누구에게나 보였고 눌러도 403이었다. 지금은 포크 프로젝트를 지울 수 있는 사용자에게만 보인다(알림 자체는 그대로 보인다) | 756dd82 | `PullRequestListTest` |
| pullrequest-F11 | SKIP | 필터를 바꾼 뒤 제목을 누르면 두 단 보기 대신 전체 이동하는 것은 UI 개편(두 단 보기 개선) 때 함께 다루기로 한 항목이다. 이동 자체는 정상 동작한다 | | |
| pullrequest-F16 | SKIP | 번역 키가 없어도 앞선 검사가 먼저 알림을 내므로 사용자에게 보이지 않는다 | | |
| pullrequest-F17 | FIX | v1.6은 포크 이름 규칙 위반, 빈 이름, 잘못된 공개 범위에서 `/fork`와 `/clone`이 모두 500이었다. 지금은 `/fork`가 400 오류 화면, `/clone`은 다른 실패와 같은 `{"status":"failed",...}` 응답이다 | a4e0131 | `ForkTest` |
| pullrequest-F18 | FIX | v1.6은 새 포크 화면의 소유자 라벨이 없는 `inputOwner`를 가리켰다. 지금은 선택 상자 `project-owner`를 가리킨다 | a4e0131 | `ForkTest` |

# 확인을 받을 항목 (ASK)

## code-F4, project-F12, project-F17 (죽은 코드 정리)

- code-F4: `code/diff.html`의 `yona.Mention({target:'review-editor'})`는 대상 요소가 없는 호출이다.
- project-F12: `project/list.html`의 "권한이 없습니다" else 가지는 어떤 설정에서도 그려지지 않는다.
- project-F17: `issue/label/categories`, `issue/label/category/:id` 조회 경로와 분류 삭제 경로는 화면이 쓰지 않는다.
- 선택지: (A) 세 곳 모두 삭제, (B) 주석으로 "쓰지 않음"만 표시하고 남김, (C) 그대로.
- 권장: (B). 동작에 영향이 없고 삭제 오류(A-410 사례) 위험을 피하며 다음 UI 개편 때 일괄 판단한다. 지금 지우는 이득이 거의 없다.

## code-F12 (스레드 댓글 수 2 대 1)

- 선택지: (A) 커밋 화면을 PR 변경 탭과 같이 답글 수(첫 댓글 제외)로, (B) PR 변경 탭을 커밋 화면과 같이 전체 댓글 수로, (C) 그대로.
- 권장: (B). 사용자가 읽는 숫자는 "이 스레드의 댓글 수"가 자연스럽고 GitHub도 전체 개수를 보인다.

## project-F21 (웹훅 비밀 값 평문 표시)

- 선택지: (A) 목록에서 가린다(`****`, 등록 때만 보임), (B) 그대로 둔다.
- 권장: (A). 비밀 값은 서명 키라 목록에서 다시 볼 일이 거의 없고 어깨너머 노출을 막는다. 다만 v1.6 화면 계약이 바뀌므로 승인이 필요하다.

## pullrequest-F5 (보낸 사람의 브랜치 삭제 단추)

- 현재: 병합된 요청의 보낸 사람(받는 프로젝트 비구성원)에게 `브랜치 삭제`, `복원` 단추가 보이지만 서버는 받는 프로젝트의 수정 권한을 요구해 403이다.
- 선택지: (A) 서버 권한을 "보낸 사람이 보내는 포크의 소유자/구성원이면 허용"으로 바꾼다(단추가 의도대로 동작), (B) 단추를 수정 권한자에게만 보인다(단추 사라짐), (C) 그대로.
- 권장: (A). 이 단추의 원래 의도가 보낸 사람이 자기 브랜치를 정리하는 것이고, 권한 변경 범위는 자기 포크의 브랜치로 한정된다. 권한 정책이라 승인이 필요하다.

# 시험 실행 기록

- Gradle(H2): `SvnCommitViewWebTest`, `CodeBrowserWebTest`, `CommitViewTest`, `IssueLabelTest`, `UserApiTest`, `TemplateContractRegressionTest`, `ProjectHomeTest`, `ProjectLayoutTest`, `PullRequestListTest`, `PullRequestActionTest`, `PullRequestViewTest`, `ForkTest`, `MessageParityTest`, `MessagesTest` 통과(마지막 두 클래스는 재실행으로 통과).
- JS: 전체 `node src/test/js/run.mjs` 643건 통과.
