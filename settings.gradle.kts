rootProject.name = "yona"

// P3-12(Mercurial 지원) — search5/hg4j가 아직 Maven Central/JitPack에 발행되지 않아(태그/릴리즈
// 0개, 자체 개발 중) 형제 디렉터리(../hg4j, github.com/search5/hg4j 클론)를 컴포짓 빌드로 직접
// 참조한다. hg4j의 group='io.github.search5.hg4j'/rootProject.name='hg4j'가 build.gradle.kts의
// implementation("io.github.search5.hg4j:hg4j") 좌표와 일치해 Gradle이 자동으로 소스 빌드로
// 치환한다(Maven Central 조회 없음). hg4j가 실제로 발행되면 이 줄을 지우고 일반 좌표 의존성으로
// 되돌리면 된다.
includeBuild("../hg4j")
