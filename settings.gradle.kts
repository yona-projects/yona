rootProject.name = "yona"

// 임시(X-7 8e, decisions.md D-89): hg4j 1.1.2가 배포되기 전까지 형제 디렉터리의 hg4j 소스를 쓴다. 배포되면 이 줄을 지우고 build.gradle.kts의 hg4j 버전을 올린다.
includeBuild("/home/jiho/yona-convert/hg4j")
