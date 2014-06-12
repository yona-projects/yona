템플릿 파일 명명 규칙
====================

* 파일 이름은 패키지(package)명을 제외한 해당 페이지의 기능으로 정하며 아래의 공통된 표현을 사용한다

  신규: create
  편집: edit
  조회: view
  목록: list
  삭제: delete

* 신규(create)와 편집(edit)을 하나의 파일로 작성할 수 있을 때에는 write로 한다
* 파일명은 카멜 표기법 ([CamelCase](http://en.wikipedia.org/wiki/CamelCase))을 준수한다
* 오류 페이지에 사용되는 템플릿 파일은 error 패키지 아래에 공통적으로 위치시킨다
