application.conf 설명
===

conf 디렉터리의 application.conf 를 통해 설정 가능한 기능들
----
- 사이트 이름
    - application.siteName = "Yona"
- 어플리케이션 루트 지정
    - application.context = /myroot
- 로그인 하지 않은 유저 접근 제한
    - application.allowsAnonymousAccess = true
- 게스트 사용자 접두사 설정. 해당 접두사의 아이디는 가입시 자동으로 Guest 계정이 됩니다.
    - application.guest.user.login.id.prefix = ""
- 가입 후 관리자가 승인을 해야만 활동 가능하도록 제한
    - signup.require.admin.confirm = true
- 메일 발송을 허용할 메일 도메인. 실수로 외부로 메일 발송이 되지 않도록 제한할 때 사용.
    - application.allowed.sending.mail.domains = ""
- 가입시 유효한 이메일인지 확인하는 기능 사용여부
    - application.use.email.verification = true
- 알림메일 발송 사용 여부
    - notification.bymail.enabled = true
- 서버 고유 보안키 (어드민 계정 리셋시에 필요함)
- 언어 표시 우선순위
    - application.langs="en-US, ko-KR, ja-JP"
- DB 접속설정
- HTTP 헤더에 표시할 서버 이름
- URL 구성 요소 설정
    - application.scheme="http"
    - application.hostname="www.yourdomain.com"
    - application.port="9000"
- Yona 시스템이 사용할 EMAIL 설정
- 사용자 업로드 임시파일의 정리 일정(초)
    - application.temporaryfiles.keep-up.time = 86400
- 알림메일 발송 지연시간
- Yona 페이지에서 링크로 타 사이트로 이동했을때 referer 헤더에서 Yona를 숨기는 기능
    - application.noreferrer = true
- 프로젝트 목록보기에서 private 프로젝트를 표시해 줄지 여부
    - application.displayPrivateRepositories = false
- 프로젝트 생성 시 기본 선택되는 공개 범위 지정
    - project.default.scope.when.create = "public"
- 공개 프로젝트 전체 목록을 볼 수 없도록 숨기는 기능    
    - application.hide.project.listing = false
- Github 으로 이전(Migration)기능 활성화 여부
    - github.allow.migration = false
    - github.client.id = "TYPE YOUR GITHUB CILENT ID"
    - github.client.secret = "TYPE YOUR GITHUB CILENT SECRET"
- 파일 업로드시에 단일 파일 최대 업로드 크기 제한(기본 2Gb)
    - application.maxFileSize = 2147483454
- 프로젝트 생성시 기본으로 선택되어서 표시할 메뉴
    - project.creation.default.menus = "code, issue, pullRequest, review, milestone, board"
- 오직 소셜 로그인(Github/Gmail)을 통한 가입/로그인만으로 제한 (자체 계정 생성 및 로그인 금지)
    - application.use.social.login.only = false
- 소셜 로그인 사용 시 사용자 이름 동기화 여부
    - application.use.social.login.name.sync = false
- 지원 소셜 로그인 제공자 설정
    - application.social.login.support = "github, google"
- Github Enterprise 연동시 추가 옵션
    - 로그인 버튼 이름 변경
      - application.social.login.github.name = "Github Enterprise"
- LDAP 로그인 지원
    - application.use.ldap.login.supoort = false

application.conf 기본 설정
-----

```
# This is the main configuration file for the application.
# ~~~~~

# Site Name
# ~~~~~~~~~
# The name of your website
application.siteName="Yona"

# Application Context
# ~~~~~~~~~~~~~~~~~~~
# If you want your own application context root, you can set it.
# Don't miss first / (slash) letter!
# application.context = /myroot

# Anonymous access
# ~~~~~~~~~~~~~~~~
# This site allows anonymous access. (default: true)
# If this is false, Yona refuses anonymous access to any page except for the
# ones to be needed for login, login and creating accout.
# NOTE: Even if this is false, anyone can create a account freely. If you don't
# want to allow that, set signup.require.confirm to true.
application.allowsAnonymousAccess=true

# Guest User Id Rule
# ~~~~~~~~~~~~~
# If login id is created with following prefixes,
# Yona treat that user is Guest User.
# Guest user is extremely restricted in use of Yona.
# They can not see any project listing of instance and
# only create own account's projects.
# In other words, they cannot create organization.
# If multiple prefixes are needed, user , (comma)
#
# eg.
# "PT_, GUEST_"

application.guest.user.login.id.prefix = ""

#
# Signup options
# ~~~~~~~~~~~~~~

# If you wants to make the user available to use yona
# after the server administrator approved,uncomment below
#
# signup.require.admin.confirm = true

# If you only want to allow for signing up in specific email domains,
# use the following option.
# application.allowed.sending.mail.domains = "gmail.com, your-company.com"
# And "" is option for no restriction.
#
# application.allowed.sending.mail.domains = ""

# If following email verification option is true, all user will be locked when it sign-up,
# until user click the verification link of verification confirm mail
#
# application.use.email.verification = true

# If you enable to use social login or email verification, set followings
play-easymail {
  from {
    # Mailing from address
    email="projects.yona@gmail.com"

    # Mailing name
    name="yona-no-reply"

    # Seconds between sending mail through Akka (defaults to 1)
    # delay=1
  }
}

# Notification
# ~~~~~
# Notfication email is delivered by default.
# If you want to disable the delivery, set the 'notification.bymail.enabled' to 'false'.
notification.bymail.enabled = true

# Secret key
# ~~~~~
# The secret key is used to secure cryptographics functions.
# If you deploy your application to several instances be sure to use the same key!
#
# If you want to reset admin account, set this value to default.
# Default: "VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew"
application.secret="VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew"

# The application languages
# ~~~~~
application.langs="en-US, ko-KR, ja-JP"

# Global object class
# ~~~~~
# Define the Global object class for this application.
# Default to Global in the root package.
application.global=Global

# Database configuration
# ~~~~~
# You can declare as many datasources as you want.
# By convention, the default datasource is named `default`
#
ebeanconfig.datasource.default=default
# H2 Configuration
# db.default.driver=org.h2.Driver
# db.default.url="jdbc:h2:mem:yona;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
# db.default.url="jdbc:h2:file:./yona;MODE=PostgreSQL;MV_STORE=FALSE;MVCC=FALSE;CACHE_SIZE=131072;AUTO_SERVER=TRUE"
# db.default.user=sa
# db.default.password=sa
# db.default.logStatements=true
# db.default.partitionCount=20
# db.default.maxConnectionsPerPartition=10
# db.default.minConnectionsPerPartition=2
# db.default.acquireIncrement=4
# db.default.acquireRetryAttempts=3
# db.default.acquireRetryDelay=1 seconds
# db.default.connectionTimeout=3 second
# db.default.statementsCacheSize=1000

# MySQL Configuration
# db.default.driver=com.mysql.jdbc.Driver
# db.default.url="jdbc:mysql://127.0.0.1:3306/yona?characterEncoding=utf-8"
# db.default.user=yona
# db.default.password=""

# MariaDB
db.default.driver=org.mariadb.jdbc.Driver
db.default.url="jdbc:mariadb://127.0.0.1:3306/yona?useServerPrepStmts=true"
db.default.user=yona
db.default.password="password"

# Local
# db.default.url="jdbc:postgresql://localhost:5432/yona"
# db.default.user=postgres
# db.default.password=password

# You can expose this datasource via JNDI if needed (Useful for JPA)
# db.default.jndiName=DefaultDS

# Ebean configuration
# ~~~~~
# You can declare as many Ebean servers as you want.
# By convention, the default server is named `default`
#
ebean.default="models.*"

# Evolutions
# ~~~~~
# You can disable evolutions if needed
# evolutionplugin=disabled
applyEvolutions.default=true

# Logger
# ~~~~~
# You can also configure logback (http://logback.qos.ch/), by providing a logger.xml file in the conf directory .

# Server
# ~~~~~

# Server name used by servlet, as the value of "Server" field in HTTP response message.
application.server="Play/2.3"

# Components used to construct the URL to this application.
application.scheme="http"
# application.hostname="www.yourdomain.com"
# application.port="8080"

# Application feedback url at top layout menu. You can remove feedback menu by commenting it.
application.feedback.url="https://github.com/yona-projects/yona/issues"

# Mailer
# ~~~~~~
# You have to configure SMTP to send mails.
# Example settings, it assume that you use gmail smtp
smtp.host = smtp.gmail.com
smtp.port = 465
smtp.ssl = true
smtp.user = yourGmailId
# Be careful!!! Not to leak password
smtp.password = yourGmailPassword
smtp.domain = gmail.com
#true to use mock mailer for testing, false for using real mail server
smtp.mock = true
# optional, size of mail archive for tests, default: 5
smtp.archive.size = 5

# Mailbox Service
# ~~~~~~~~~~~~~~~
#
# Mailbox Service fetches and process mails from IMAP server. For example, if
# the service fetches an email to a project, it posts the email as an issue of
# the project.
#
# If you want to use this feature, your IMAP server has to be configured to
# support the address alias using '+' sign, also known as 'subaddressing' or
# 'detailed addressing'. For example, emails to 'yona+issue@yourmail.com' have
# to be delivered to 'yona@yourmail.com'.
#
# SECURITY WARNING: Yona believes the email address in From header of the
# received email is truthful and use it for authentication without doubt. To
# avoid this problem, your imap server must deny every email whose From header
# is forged.
#
# Here is an example if you use Gmail.
#
# Set imap.use to true if you want to use this feature.
imap.use = false
imap.host = imap.googlemail.com
imap.ssl = true
imap.user = "your-yona-email-address@gmail.com"
# The email address of Yona. Mailbox Service processes an email only if its
# address is as follows.
imap.address = "your-yona-email-address@gmail.com"
# Be careful!!!
imap.password = yourGmailPassword
imap.folder = inbox

# Production configuration
%prod.http.port=80
%prod.application.log=INFO
%prod.application.mode=prod

# User uploaded temporary files cleanup schedule (sec, default 24hour: 24*60*60 = 86400)
# application.temporaryfiles.keep-up.time = 86400

# Notification
# ~~~~~~~~~~~~
# Check mails to send every this seconds.
application.notification.bymail.interval = 60s
# Sending a notification mail delays this seconds.
application.notification.bymail.delay = 180s
# Split notification emails by the recipient limit.
# The value is number of maximum recipients per an email and inclusive.
# (default: 0, This means there is no limitation.)
application.notification.bymail.recipientLimit = 100
# Hide recipients of notification email by using bcc. (default: true)
application.notification.bymail.hideAddress = true
# A new event notification can be merged if possible with previous one which is
# not older than this seconds.
application.notification.draft-time = 30s
# Delete notifications which are older than this days.
# If this value is undefined or not positive number, notifications will remain forever.
# application.notification.keep-time = 60

# Software Update
# ~~~~~~~~~~~~~~~
# Check for updates of Yona at this interval if it is grater than 0.
application.update.notification.interval = 6h
# A url to the git repository for Yona releases.
application.update.check.use = true
application.update.repositoryUrl = "https://github.com/yona-projects/yona"
# A format to construct the url to latest Yona release. "%s" is a format
# specifier for Yona version to download like "0.5.7".
application.update.releaesUrlFormat = "https://github.com/yona-projects/yona/releases/tag/v%s"

# customize play default thread pool size
# see: https://www.playframework.com/documentation/2.3.x/ThreadPools
play {
  akka {
    event-handlers = ["akka.event.Logging$DefaultLogger", "akka.event.slf4j.Slf4jEventHandler"]
    loglevel = WARNING
    actor {
      default-dispatcher = {
        fork-join-executor {
          parallelism-min = 50
          parallelism-max = 300
        }
      }
    }
  }
}

# customize akka thread pool size
akka {
  loggers = ["akka.event.Logging$DefaultLogger", "akka.event.slf4j.Slf4jLogger"]
  loglevel = WARNING
  actor {
    default-dispatcher = {
      fork-join-executor {
        # Min number of threads to cap factor-based parallelism number to
        parallelism-min = 8

        # The parallelism factor is used to determine thread pool size using the
        # following formula: ceil(available processors * factor). Resulting size
        # is then bounded by the parallelism-min and parallelism-max values.
        parallelism-factor = 3.0

        # Max number of threads to cap factor-based parallelism number to
        parallelism-max = 64
      }
    }
  }
}

# No referrer information is to be leaked when following the link from yona pages. If you don't want, set it false
application.noreferrer = true

# Display private repositories in the list
application.displayPrivateRepositories = false

# Hide project listing for security
application.hide.project.listing = false

# choice: "public" or "private"
# default: "public"
project.default.scope.when.create = "public"

# Google Analytics
# ~~~~~~~~~~~~~~~~~
# This data is used to better understand how users interact with the Web UI which gives us valuable information
# in improving Yona user experience. To disable this for any reason, set the following option to false.
application.send.yona.usage = true

# Github Migration
# ~~~~~~~~~~~~~~~~~
# User can migrate their own projects to github
#
github.allow.migration = false
github.client.id = "TYPE YOUR GITHUB CILENT ID"
github.client.secret = "TYPE YOUR GITHUB CILENT SECRET"

# Attachment Upload File Size Limit
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# 2,147,483,454 bytes = 2Gb
application.maxFileSize = 2147483454

# Project Creation Menu Settings
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Default: "code, issue, pullRequest, review, milestone, board"
project.creation.default.menus = "issue, milestone, board"

# Social Login Support
# ~~~~~~~~~~~~~~~~~~~~
# Social login settings for Yona
# Detail settings are described at social-login.conf

# Prevent using Yona's own login system
application.use.social.login.only = false

# If true, update local user name with social login account name
application.use.social.login.name.sync = false

# Allowed OAuth social login provider
# choice: github, google
application.social.login.support = "github, google"

# LDAP Login Support
# ~~~~~~~~~~~~~~~~~
#
application.use.ldap.login.supoort = false
ldap {
    host = "ldap.forumsys.com"
    # default: ldap.port=389, ldaps.port=636
    port = 389
    # protocol: ldap or ldaps. If you want to use SSL/TLS, use 'ldaps'
    protocol = "ldap"
    baseDN = "ou=scientists,dc=example,dc=com"
    # If your ldap service's distinguishedName is 'CN=username,OU=user,DC=abc,DC=com', postfix is 'OU=xxx,DC=abc,DC=com'
    distinguishedNamePostfix = "OU=user,DC=abc,DC=com"
    loginProperty = "sAMAccountName"
    displayNameProperty = "displayName"
    userNameProperty = "CN"
    emailProperty = "mail"
    options {
      # If your LDAP configuration support email login
      useEmailBaseLogin = false
      fallbackToLocalLogin = false
    }
}

include "social-login.conf"
```
