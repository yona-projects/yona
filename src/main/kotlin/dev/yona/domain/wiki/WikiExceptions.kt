package dev.yona.domain.wiki

/** 위키 작업이 규칙을 어겨 실패했음을 나타내는 예외의 뿌리이다. 웹과 REST 계층이 상태 코드로 바꾼다. */
sealed class WikiException(message: String) : RuntimeException(message)

/** 페이지 제목이 규칙을 어겼다(비어 있거나 금지 문자, 예약 이름, 너무 김). 400에 해당한다. */
class WikiTitleInvalidException(message: String) : WikiException(message)

/** 같은 이름의 페이지가 이미 있다. 409에 해당한다. */
class WikiPageExistsException(val name: String) : WikiException("이미 있는 위키 페이지입니다: $name")

/** 페이지나 리비전을 찾을 수 없다. 404에 해당한다. */
class WikiPageNotFoundException(message: String) : WikiException(message)

/** 위키를 쓸 수 없는 프로젝트이다(저장소 위치가 다른 프로젝트의 코드 저장소와 겹친다). 409에 해당한다. */
class WikiUnavailableException(message: String) : WikiException(message)

/** 저장하는 사이에 다른 커밋이 먼저 들어와 반영하지 못했다. 409에 해당한다. */
class WikiConflictException(message: String) : WikiException(message)
