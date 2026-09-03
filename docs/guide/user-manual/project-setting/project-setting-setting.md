# 프로젝트 설정하기

legacy `userManual`에는 `projectSetting/project-setting-setting.md`와
`projectSettings/project-setting-setting.md`(오타로 폴더명이 둘로 갈라진 사실상 중복 문서)가
있었다 — 더 자세한 쪽 내용으로 합쳐서 하나로 옮겼다.

프로젝트 관리자 권한이 있으면 프로젝트 설정을 바꿀 수 있다.

로고, 이름, 설명, 공개 범위, 멤버를 설정할 수 있다.

1. 권한이 있는 프로젝트로 이동한다.
1. 프로젝트 화면 오른쪽 위의 톱니바퀴 `Project Settings` 버튼을 클릭한다.
1. `Setting` 탭을 클릭해서 로고, 이름, 설명, 공개 범위를 변경한다.

## 로고

프로젝트 로고는 설정을 마치면 프로젝트 화면 상단과 프로젝트가 나열되는 모든 곳에 표시된다.

## 이름

프로젝트 이름을 바꿀 수 있다. 이름을 바꾸면 아래에 영향을 준다.

- 소스 코드 저장소와 관련된 `URL`이 바뀐다.
- 프로젝트 이름이 표시되는 모든 곳이 바뀐다.

## 설명

프로젝트 설명을 바꿀 수 있다. 바뀐 설명은 프로젝트 메인 페이지, `Project List`, `Group Page`에
표시된다.

## 공개 범위(Share Option)

공개 범위(public, protected, private)를 바꿀 수 있다. `protected`는 그룹 프로젝트일 때만
표시된다.

- `public`: 모든 사용자가 프로젝트의 모든 항목에 접근하거나 Watch할 수 있다.
- `private`: 프로젝트 멤버가 아니면 접근할 수 없다.
- `protected`: 프로젝트 멤버와 프로젝트가 속한 그룹의 멤버가 아니면 접근할 수 없다.

자세한 내용은 [technical/access-control.md](../../technical/access-control.md) 참고.

## 리뷰어 수(Reviewer)

Pull Request를 병합하기 위한 최소 리뷰어 수를 설정하는 값이다.

각 Pull Request는 이 값 이상의 사용자에게 리뷰를 받아야 병합(Accept)할 수 있다.

## 기본 브랜치(Default Branch)

저장소 종류가 Git일 때만 표시되는 옵션이다.

이 값은 git 저장소의 HEAD가 가리키는 브랜치를 의미한다. `Code` 메뉴의 기본 브랜치이자, 새
Pull Request 화면의 기본 `to` 브랜치이기도 하다.

## 메뉴 설정(Menu Setting)

메뉴 설정으로 화면에 표시할 메뉴를 고를 수 있다.

code, issue, pullRequest, review, milestone, board 옵션 중에서 선택할 수 있다.

일부 옵션을 선택하지 않아도 관련 데이터가 삭제되지는 않는다. 화면에서 보이지 않을 뿐이다.
