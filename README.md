<a name="korean"></a>[[English]](#english)
[![Build Status](https://travis-ci.org/yona-projects/yona.svg?branch=master)](https://travis-ci.org/yona-projects/yona)

Yona
====
21세기 협업 개발 플랫폼

DEMO: [http://repo.yona.io](http://repo.yona.io)

Official Site: [http://yona.io](http://yona.io)

Yona?
--
- 설치형 이슈트래커 + Git 코드 저장소
- Yona는 팀이 업무와 개발을 진행하는데 그 속도와 효율을 증가시키기 위해 만들어졌습니다.

주요기능
---
- 서비스 종료나 데이터 종속 걱정없는 설치형
- 프로젝트 기반의 유연한 이슈트래커와 게시판
   - 프로젝별 이슈 이동 가능
   - 서브 태스크 지원
   - 본문 변경이력 보기
   - 이슈 템플릿 기능
- 자체 내장된 코드 저장소
   - Git/SVN 선택 가능
   - 온라인 수정 및 커밋 지원
   - 프로젝트 멤버만 코드에 접근 가능 기능 등
- 블럭기반 코드리뷰 
   - 코드 블럭 및 리뷰 스레드 지원
   - 리뷰 점수 지원
- 그룹 기능
   - 그룹 이슈 및 게시글 통합관리
   - 그룹 프로젝트, 그룹 멤버
- 한글 기반
   - 프로젝트 이름 및 그룹 이름에 한글을 사용가능
- LDAP 지원
- Github 마이그레이션 제공
- 로그인 보안을 위한 소셜로그인 지원
- 무료/오픈소스

등을 비롯하여 일상적인 업무에서 개발 전반에 이르는 다양한 기능을 포함하고 있습니다.

왜 Yona를 써야 하나요? (Why Yona?)
---
- [왜 Yona를 써야 하나요? (Why Yona?)](https://repo.yona.io/yona-projects/yona/post/3)
- [기본 워크플로우](https://repo.yona.io/yona-projects/yona-help/post/2)


라이선스
--
Yona는 Apache 2.0 라이선스로 제공됩니다.

**이어지는 설치 및 실행, 백업 등등에 대한 자세한 설명은 [Wiki](https://github.com/yona-projects/yona/wiki)에 따로 세분화되어 정리되어 있습니다.**

Yona 설치 및 실행
===

Yona 배포판
---
현재 Yona는 버전별로 두 개의 배포판을 [릴리즈 메뉴](https://github.com/yona-projects/yona/releases)를 통해 제공하고 있습니다.

- MariaDB 버전
  - 기본 권장 버전
  - yona-v1.3.0-bin.zip 같은 형식으로 파일로 배포
  - DB 설치에 약간의 시간이 필요하지만 안정적으로 운영이 가능
- H2 DB 내장형
  - DB 설정없이 내려받아서 바로 실행해서 쓸 수 있는 버전
  - yona-h2-v1.3.0-bin.zip 같은 형식으로 파일로 배포
  - USB 등에 담아서 이동해가면서 사용하거나 작업후 통째로 zip으로 묶어서 들고 다니는 것이 가능함
  - 대규모 사이트에서 사용하기에는 적합하지 않음. 참고: [Yona가 MariaDB를 기본 DB로 사용하게 된 이유](https://repo.yona.io/yona-projects/yona/post/4)

Yona 설치
---
Yona는 기본적으로 다음 2단계로 설치됩니다.

- [MariaDB 설치](docs/ko/install-mariadb.md)
- [Yona 설치](docs/ko/install-yona-server.md)

#### Docker를 이용한 설치
[Docker](https://www.docker.com/)를 이용해 설치하실분은 [pokev25](https://github.com/pokev25) 님의 https://github.com/pokev25/docker-yona 를 이용해주세요. 

#### Amazon AWS 에 설치 
https://okdevtv.com/mib/yona by [Kenu](https://www.facebook.com/kenu.heo)님

Yona 실행 및 업그레이드/백업 및 복구/문제 해결
---
- [실행 및 재시작 방법](docs/ko/yona-run-and-restart.md)
- 안정적인 운영을 위한 [실행 옵션들](docs/ko/yona-run-options.md)
- [업그레이드](docs/ko/yona-upgrade.md)
- [백업 및 복구](docs/ko/yona-backup-restore.md)
- [알림메일 발송 기능 설정](docs/ko/yona-mail-settings.md)
- [발생 가능한 문제상황들과 해결방법](docs/ko/trouble-shootings.md)


소스코드를 직접 내려 받아서 빌드 실행하기
---
자신의 입맛에 맛게 코드를 직접 수정해서 작업하거나 코드를 기여하고 싶을 경우에는 코드 저장소로부터 코드를 직접 내려받아서 빌드/실행하는 것도 가능합니다.
[소스코드를 직접 내려 받아서 실행하기](https://repo.yona.io/yona-projects/yona/post/5)를 참고해 주세요

서버 관련 설정들
---
- [application.conf 설명](docs/ko/application-conf-desc.md)
- [소셜 로그인 설정](docs/ko/yona-social-login-settings.md)

<br/>

<a name="english"></a>[[한국어]](#korean)


Yona
=======
Yona is a web-based project hosting software.

What is Yona?
--
Yona is designed to increase the speed and efficiency of your team's work and development.

- Issue tracker to manage bugs and issues
- Bulletin board to share documents
- Git/SVN support embedded
- Pull-request for collaborative coding
- Block based code review
- Online Commit
- LDAP support


Yona Distribution
---
Currently, Yona offers two distributions per version.

#### MariaDB Version
- Recommended version.
- Distributed as a file in the similar format as yona-v1.3.0-bin.zip
- It takes a little effort to install DB, but it can be operated stably.

#### Embedded H2 DB Version
- Portable version that can be downloaded and run immediately. No need to setting a DB.
- Distributed as a file in the similar format as yona-h2-v1.3.0-bin.zip
- It can be used portable. For example, along with USB etc. And it can be carried around with zip as a whole of work.
 - Not suitable for large-scale team. (over 500 people)


How to install
---
Basically, Yona installation is in two steps:

- [MariaDB install](docs/install-mariadb.md)
- [Yona install](docs/install-yona-server.md)


If you want to use [Docker](https://www.docker.com/), See https://github.com/pokev25/docker-yona by [pokev25](https://github.com/pokev25)


Yona Start/Upgrade/Backup/Trouble Shootings
---
- [Start and Restart](docs/yona-run-and-restart.md)
- [Start Options](docs/yona-run-options.md) for stable operation
- [Upgrade](docs/yona-upgrade.md)
- [Backup/Restore](docs/yona-backup-restore.md)
- [Mail settings for Notification](docs/yona-mail-settings.md)
- [Trouble Shootings](docs/trouble-shootings.md)

Server Settings
---
- [application.conf Settings](docs/application-conf-desc.md)
- [Social Login Settings](docs/yona-social-login-settings.md)

Contribution
---
Welcome anytime!


License
--
Copyright Yona Authors and NAVER Corp. under the Apache License, Version 2.0
