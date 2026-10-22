---
title: 프로젝트 설정 (GET settingform, POST setting)
type: web
sources: [app/controllers/ProjectApp.java:160-166, app/controllers/ProjectApp.java:235-330, app/views/project/setting.scala.html, app/views/project/partial_settingmenu.scala.html, app/models/Project.java:236-242, app/models/Project.java:676-700, app/models/ProjectMenuSetting.java:56-64, app/utils/LogoUtil.java, app/playRepository/GitRepository.java:851-863, app/playRepository/GitRepository.java:1843-1863, app/playRepository/GitRepository.java:1917-1950]
updated: 2026-10-22
---

# 프로젝트 설정

프로젝트를 고칠 수 있는 사용자만 쓸 수 있다(`@IsAllowed(UPDATE)`, 프로젝트 자체는 관리자와 조직 관리자). 관리자가 아닌 구성원과 익명은 403이다.

## 폼 (`GET /:user/:project/settingform`)

- 제목 `Project settings - 소유자/이름`, 상단에 설정 하위 메뉴 7개(설정, 구성원, 이슈 라벨, 웹훅, 이전, 삭제, 저장소 종류 변경). 현재 주소와 같은 항목이 `active`이다. 가입 신청이 있으면 구성원 메뉴에 개수 배지가 붙는다. 저장소 종류 변경 메뉴는 코드 메뉴가 꺼져 있으면 숨긴다.
- 로고 영역(현재 로고, 안내, 파일 선택), 이름(이전 위치가 있으면 안내), 설명.
- 공개 범위 라디오: 공개, (조직 프로젝트만) 그룹 공개, 비공개.
- Git 프로젝트만: 이슈 템플릿 편집 링크, 리뷰어 수 사용 여부와 기본 리뷰어 수(1 ~ 구성원 수), 기본 브랜치 선택(브랜치만, 현재 기본 브랜치 선택됨). 리뷰어 수와 기본 브랜치 패널은 코드 메뉴가 꺼져 있으면 숨긴다.
- 코드 접근을 구성원으로 제한할지 라디오.
- 메뉴 체크박스 6개(풀 리퀘스트는 Git만).

## 저장 (`POST /:user/:project/setting`, multipart)

검증이 실패하면 `400`으로 같은 폼을 **원래 값으로** 다시 보여 주고(v1.6 템플릿이 입력값이 아니라 프로젝트의 현재 값을 그린다) 안내를 알림으로 띄운다. 성공하면 `303`으로 `/:user/:이름/settingform`(바뀐 이름)에 돌아간다.

### 검증과 안내

| 순서 | 검사 | 안내 메시지 |
|---|---|---|
| 1 | 같은 소유자에 같은 이름이 이미 있음(대소문자 무시, 자기 자신 제외) | `project.name.duplicate` |
| 2 | 로고가 있고 확장자가 jpg, jpeg, png, gif, bmp가 아님 | `project.logo.alert` |
| 2 | 로고가 5MB(5,120,000바이트) 초과 | `project.logo.fileSizeAlert` |
| 3 | 이름이 형식 위반이면 `project.name.alert`, 예약어(`.`, `..`, `.git`)이면 `project.name.reserved.alert` | (앞의 안내를 덮어쓴다) |

안내는 마지막에 정한 하나만 남는다(v1.6의 `flash` 키가 하나라서). 이름이 비어 있거나 공개 범위, 불리언, 숫자 값이 잘못된 경우는 거부하지만 안내는 없다.

### 저장 순서와 효과

1. 로고를 골랐으면 프로젝트의 기존 첨부(로고)를 모두 지우고 새로 저장한다. 파일을 고르지 않았으면(파일 이름이 빈 값) 바꾸지 않는다.
2. 기본 브랜치를 받았으면 저장소의 `HEAD`를 그 참조로 바꾼다.
3. 이름이 바뀌면(대소문자까지 다를 때) 이전 위치를 기록하고 저장소 디렉터리를 옮긴다. 이전 위치(소유자, 이름, 시각)는 **이전 기록이 없거나 마지막 변경이 25시간 이상 지났을 때만** 새로 기록하고, 그 안에 다시 바꾸면 처음의 이전 위치를 유지한다(v1.6의 `getStandardHours() > 24`). 디렉터리를 옮기지 못하면 예외이고 트랜잭션이 취소된다.
4. 받은 항목만 저장한다: 이름, 설명, 공개 범위, 코드 접근 제한, 리뷰어 수 사용, 기본 리뷰어 수. 보내지 않은 항목은 그대로 둔다.
5. 메뉴 설정: **체크하지 않은 메뉴는 꺼진다**(SVN 프로젝트는 풀 리퀘스트 체크박스를 그리지 않으므로 저장하면 꺼진다. v1.6과 같다).
6. 이 프로젝트를 즐겨찾기한 행의 소유자와 이름을 새 값으로 바꾼다.

## v1.6과 달라진 점

- v1.6은 요청 파라미터를 `Project` 전체에 묶어서 폼이 보내지 않는 `owner`, `vcs`, `siteurl`이나 다른 프로젝트의 `id`도 요청으로 바꿀 수 있었다(권한이 있는 관리자 한정). v2는 폼이 보내는 항목만 받고 프로젝트는 주소로 정한다([../decisions.md](../decisions.md) A-24).

## 검증

`ProjectSettingTest` 34건(접근 2, 폼 12, 저장 11, 검증 5, 로고 5, 보안 1 등).
