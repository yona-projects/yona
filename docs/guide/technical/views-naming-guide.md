# 템플릿 파일 명명 규칙

legacy Yona의 `docs/ko/technical/views-naming-guide.md`를 옮김. 이 규칙은 yona의
`src/main/resources/templates/`에도 그대로 적용되어 있다 — 예를 들어 `templates/issue/`에
`create.html`/`edit.html`/`list.html`/`view.html`이 legacy 명명 그대로 존재하고,
`templates/error/`도 공통 에러 템플릿 모음으로 그대로 유지되어 있다(파일 확장자만
`.scala.html`→`.html`).

- 파일 이름은 패키지(디렉터리)명을 제외한 그 페이지의 기능으로 정하며, 아래 공통 표현을
  사용한다.
  - 신규: `create`
  - 편집: `edit`
  - 조회: `view`
  - 목록: `list`
  - 삭제: `delete`
- 신규(create)와 편집(edit)을 한 파일로 작성할 수 있으면 `write`로 한다.
- 파일명은 카멜 표기법(CamelCase)을 따른다.
- 오류 페이지에 쓰이는 템플릿은 `error` 패키지(디렉터리) 아래 공통으로 둔다.
