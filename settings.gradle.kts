rootProject.name = "yona"

// 임시(X-7 8e, decisions.md D-89): hg4j 1.1.2가 배포되기 전까지 형제 디렉터리의 hg4j 소스를 쓴다. 배포되면 이 줄을 지우고 build.gradle.kts의 hg4j 버전을 올린다.
// 지금은 꺼 둔다: hg4j가 java-gradle-plugin의 gradleApi()를 api로 노출해, includeBuild로 참조하면 Gradle의 slf4j 구현이 Yona의 실행 클래스패스로 새어
// Spring 시험이 모두 깨진다(hg-da에 수정을 요청함). 고쳐지면 아래 줄의 주석을 푼다.
// includeBuild("/home/jiho/yona-convert/hg4j")
