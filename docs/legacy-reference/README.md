# legacy-reference

legacy Yona(및 그 전신인 Yobi/nFORGE) 저장소에만 있던, **역사적·설계 의도 참고용** 자료를
원문 그대로 옮겨온 디렉터리. `docs/guide/`(운영 문서)나 `docs/parity/index.md`(이식 진행)와
달리, 여기 있는 문서들은 **yona 기준으로 다시 쓰지 않고 원문 그대로** 보존했다 — 설계 의도나
과거 이력 자체가 가치이기 때문이다. 따라서 여기 적힌 설정 키·화면 이름·기술 스택 서술은
legacy(Play/Java) 기준이며 yona의 현재 구현과 반드시 일치하지는 않는다.

## design-specs/

Yobi 시절 작성된 기능 설계 문서. 지금도 유효한 설계 의도 참고 자료다 — 특히
`project-transfer.md`는 `docs/parity/index.md`의 P0-09(프로젝트 이전 수락 인가 검증)의
근거가 된 원 설계이고, `export-and-import.md`는 지금 yona의 `/site/export`·`/site/import`
(`DataBackupService`)로 이어지는 기능의 원 설계다.

- [export-and-import.md](design-specs/export-and-import.md) — 데이터 Export/Import 설계
- [project-transfer.md](design-specs/project-transfer.md) — 프로젝트 이관 기능 설계
- [yobi-organization-plan.md](design-specs/yobi-organization-plan.md) — 그룹(조직) 기능 설계

## history/

nFORGE(Yobi/Yona의 전신) 시절의 비전 문서·기능 목록. 순수 역사 자료.

- [nforge_vision_doc.md](history/nforge_vision_doc.md)
- [nforge4_feature_list.md](history/nforge4_feature_list.md)

## relnotes/

legacy Yona(Yobi 포함)의 버전별 릴리즈노트(0.5.2 ~ 0.8.2). yona는 별도의 버전 체계를 쓰므로
이 릴리즈노트들이 yona 자체의 변경 이력은 아니다 — "이 프로젝트가 어디서 왔는지"의 기록으로
보존한다.
