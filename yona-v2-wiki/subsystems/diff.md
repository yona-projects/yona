---
title: diff 엔진
type: subsystem
sources: [app/playRepository/GitRepository.java:731-775, app/playRepository/GitRepository.java:1451-1730, app/playRepository/FileDiff.java, app/playRepository/Hunk.java, app/playRepository/DiffLine.java]
updated: 2026-10-23
---

# diff 엔진

커밋 사이의 파일별 차이를 계산한다. 커밋 보기, 비교, 풀 리퀘스트 변경 내용, 코드 리뷰 댓글 위치가 이것을 쓴다. 구현: `dev.yona.repository.diff.DiffService`, `FileDiff`, `Hunk`, `DiffLine`.

| 메서드 | 동작 |
|---|---|
| `commitDiff(project, rev)` | 커밋이 첫 부모와 비교해 바꾼 파일들. 첫 커밋이면 전부 추가. 리비전을 풀 수 없으면 `null` |
| `diff(project, revA, revB)` | 같은 저장소의 두 리비전 비교 |
| `diff(projectA, revA, projectB, revB)` | 서로 다른 저장소(포크) 사이의 비교. 두 저장소의 객체를 차례로 읽는 판독기를 쓴다(`DiffFormatter.setReader`) |

## 계산 규칙

- 이름 바꿈과 복사를 찾는다(`DiffFormatter.setDetectRenames`).
- 텍스트가 바뀐 파일은 히스토그램 알고리즘(저장소 설정 `diff.algorithm`이 있으면 그것)으로 바뀐 곳 목록을 구하고, 이진 파일은 표시만 한다. 문자 집합은 내용으로 추정한다.
- 크기 한도: 파일 하나는 500KB, 5,000줄, 전체 차이는 그 3배를 넘으면 이후 파일은 내용을 싣지 않고(`OTHERS_SIZE_EXCEEDED`), 파일은 2,000개를 넘으면 멈춘다. 파일이 너무 크면 `A_SIZE_EXCEEDED`, `B_SIZE_EXCEEDED`, 차이가 너무 크면 `DIFF_SIZE_EXCEEDED`.
- 헝크: 바뀐 곳마다 앞뒤 3줄 문맥을 붙이고, 가까운 변경(문맥의 두 배 이내)은 한 헝크로 합친다. `interestLine`과 `interestSide`를 정하면 그 줄이 든 헝크 하나만 만든다(코드 리뷰 댓글 위치 표시).
- 줄 번호는 0부터이다(화면에서 1을 더해 보여 준다).

## 테스트

`DiffServiceTest` 9건: 첫 커밋, 헝크와 문맥, 가까운 변경 합치기, 삭제와 이름 바꿈과 이진, 두 리비전, 서로 다른 저장소, 관심 줄, 큰 파일, 풀 수 없는 리비전.
