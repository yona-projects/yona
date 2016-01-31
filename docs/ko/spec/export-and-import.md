이 문서는 데이터 Export와 Import 기능에 대해 설명한다.

개요
---

데이터 Export 기능은 요비가 사용하는 H2 데이터베이스에 들어있는 모든 데이터를 파일로 추출하는 기능이다.
데이터 Import 기능은 추출한 요비 데이터 파일을 업로드하여 기존 데이터베이스의 데이터를 교체하는 기능이다.

주의사항
------

이 기능을 사용할 때는 반드시 다른 사용자의 접근을 막은 상태에서 오직 사이트 관리자만 접근하여 작업할 것을 권장한다.
데이터를 Export하거나 Import하는 중에 다른 요청 처리로 인해 데이터가 변경되면 데이터 정합성이 깨질 위험이 매우 크기 때문이다.

데이터 Import 기능을 사용할 경우 기존 데이터를 모두 손실할 수 있으니 반드시 데이터베이스를 백업해 두거나, 
데이터 Export 기능을 먼저 사용하여 현재 요비의 모든 데이터를 파일로 추출해 둘 것을 권장한다. 

데이터 Export
-------------

* 데이터 Export 기능은 오직 사이트 관리자만 사용할 수 있다.
* 데이터 Export 버튼을 클릭하면 데이터 파일 다운로드가 진행된다.
* 다운받는 파일에 들어있는 데이터는 JSON 형식이며 모든 문자열은 UTF-8로 인코딩한다.
* 다운로드가 완료되면 데이터 Export가 끝난다.
* 파일 이름은 yobi-data-yyyyMMddHHmm.json 형식이다.
* 지원하는 데이터베이스: MySQL, H2

데이터 Import
-----------------

* 데이터 Import 기능은 오직 사이트 관리자만 사용할 수 있다.
* 데이터 Export 기능을 사용하여 추출된 요비 데이터 파일을 선택하고 Import 버튼을 클릭하면 데이터 Import가 진행된다.
* 성능 문제로 인해 Truncate Table을 사용하기 때문에 DB 트랜잭션을 보장할 수 없다.
 * 즉, 데이터가 유실 되거나 데이터 정합성이 깨질 수 있으니 반드시 주의사항을 참고하기 바란다.
* 데이터 Import 기능이 완료되면 요비 첫 페이지(/)로 이동한다.
* 지원하는 데이터베이스: MySQL, H2

Use-cases
--------

이 기능을 다음과 같은 경우에 사용할 수 있다. 

### 새로운 DB를 만들어 데이터 이전하기
1. Export 기능을 사용하여 요비 데이터를 로컬에 파일로 내려받는다.
2. `application.conf`에서 새로운 DB를 사용하도록 설정을 변경한다.
3. 요비를 실행하면 새로운 DB에 스키마가 생성되고 기본 데이터가 들어가있는 상태가 된다.
4. 사이트 어드민 기본 계정(admin)으로 로그인한다.
5. Export 했던 파일을 Import 한다.

2번 작업 이후에 build.sbt에서 h2 의존성의 버전을 1.7.176으로 변경하면 H2 DB 버전을 변경하는 것도 가능하다.
이때 반드시 db.default.url에 추가하는 파라미터가 해당 버전에서 유효한 값인지 확인해야 한다.

2번 작업 이후에 `conf/evolution.default`에 있는 모든 `sql` 파일을 삭제한 뒤 이후 과정을 수행하면
PlayFramework가 자동으로 생성해주는 스키마로 생성한 DB로 데이터를 이관할 수 있다.

### 이전 데이터로 돌아가기
1. Export 기능을 사용해서 요비 데이터를 로컬에 파일로 내려받는다.
2. 요비를 멈추고 데이터베이스를 백업해둔다. (예, 기존 데이터베이스 파일을 다른 이름의 파일로 복사 해둔다.)
3. 요비를 실행하고 돌아가고 싶은 시점에 Export 했던 파일을 Import 한다.

이 방법은 기존 DB를 그대로 사용하면 데이터만 이전 상태로 되돌린다.
이 과정중에 반드시 데이터베이스를 백업해둘 것을 권장한다.

### H2에서 MySQL로 이전하기
1. H2 디비를 사용하는 상태에서 Export 기능으로 요비 데이터를 로컬에 파일로 내려받는다.
2. 요비를 멈추고 `application.conf`에서 db.default.* 설정을 주석 처리한다.

```
# db.default.url="jdbc:h2:file:./yobi;MODE=PostgreSQL"
# db.default.driver=org.h2.Driver
# db.default.user=sa
# db.default.password=sa
# db.default.logStatements=false
```

3. `application.conf`에 db.mysql.* 설정을 추가한다.

```
db.mysql.driver=com.mysql.jdbc.Driver
db.mysql.url="jdbc:mysql://127.0.0.1:3306/yobi?characterEncoding=utf-8&useUnicode=true"
db.mysql.user=yobi
db.mysql.pass=yobi
```

4. `application.conf`에서 ebean.default 설정을 ebean.mysql로 변경한다. 

```
# ebean.default="models.*"
 ebean.mysql="models.*"
```

5. `application.conf`에 다음 설정을 추가한다.

```
ebeanconfig.datasource.default=mysql
```

6. `db.mysql.*` 설정대로 MySQL에 유저와 데이베이스를 생성한다. 이때 반드시 캐릭터셋은 utf8mb4 콜랫은 utf8mb8_bin을 사용한다.

```
CREATE DATABASE 'yobi'
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_bin
;
```

그리고 다음 SQL을 사용해서 UTF8MB4 인코딩에서도 767byte를 넘는 크기의 인덱스가 생성될 수 있도록 SQL을 실행해 줍니다

```
set global innodb_file_format = BARRACUDA;
set global innodb_large_prefix = ON;
```

참고: http://mechanics.flite.com/blog/2014/07/29/using-innodb-large-prefix-to-avoid-error-1071/

7. 요비를 실행하면 위에서 설정한 DB에 스키마가 생성되고 기본 데이터가 들어가있는 상태가 된다.
8. 사이트 어드민 기본 계정(admin)으로 로그인한다.
9. Export 했던 파일을 Import 한다.

Tip
___

데이터를 Export 또는 Import 할때 다음 옵션을 db.url에 주면 훨씬 속도가 빠르다.
`LOG=0;UNDO_LOG=0;CACHE_SIZE=65536`

