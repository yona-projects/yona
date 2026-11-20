---
title: Helm 차트(X-66) 설계
type: plan
updated: 2026-11-20
---

# Helm 차트(X-66) 설계

> **상태: 구현 완료(2026-11-20).** 구현은 `deploy/helm/yona/`이고 시험 결과와 못 한 것은 [install-scripts.md](install-scripts.md)에 있다. 아래는 설계 당시의 계획이다. 기본 설치 방법은 [X-63 설치 스크립트](../roadmap.md)이고, 이 문서는 쿠버네티스에 올리려는 사이트를 위한 **별도 방법**이다. 이미지는 [container-image-plan.md](container-image-plan.md)(X-64)에 의존한다.

## 1. 목적과 범위

- Yona v2를 쿠버네티스에 올리는 Helm 차트를 제공한다.
- **서버를 한 개만** 띄우는 것을 전제로 한다. 데이터 디렉터리에 저장소, 첨부, 비밀 키, SSH 호스트 키가 있고, 내장 검색(X-60)이 단일 노드 전용이기 때문이다.
- DB는 **외부 DB를 기본**으로 한다. 차트가 DB를 직접 운영하지 않는다(백업, 복구, 업그레이드의 책임이 커진다). 필요하면 DB 운영자(예: PostgreSQL 오퍼레이터)를 따로 쓰도록 안내한다.
- 신규 설치만 다룬다. v1.6 이관은 X-63의 이관 모드로 한다.

## 2. 차트 구성 제안

| 리소스 | 내용 |
|---|---|
| 워크로드 | **StatefulSet, 복제본 1개 고정**(또는 `Deployment`에 `strategy: Recreate`와 단일 읽기 쓰기 볼륨). 두 개가 같은 데이터 볼륨에 동시에 쓰면 안 된다 |
| 볼륨 | `PersistentVolumeClaim`을 `yona.data-dir`에 붙인다. 크기와 `storageClass`는 값으로 받는다. **지우지 않는다**(`helm uninstall`로 지워지지 않게 보존 어노테이션) |
| 서비스 | 웹(8080), 선택으로 내장 SSH(2222). SSH는 `LoadBalancer`나 `NodePort`로 열 수 있게 값으로 받는다 |
| 인그레스 | 선택. HTTPS 뒤 운영이면 `yona.scheme=https`와 `server.forward-headers-strategy=framework`를 함께 준다([fresh-install-guide.md](fresh-install-guide.md) 5절) |
| 설정 | `ConfigMap`(비밀이 아닌 것)과 `Secret`(DB 비밀번호, `yona.secret`, SMTP). `Secret`은 외부 비밀 관리자 연동(`existingSecret`)도 받는다 |
| 프로브 | 시작 프로브와 liveness는 `/-_-actuator/health`, readiness는 `/-_-actuator/health/readiness`(DB와 데이터 디렉터리만 본다) |
| 자원 | 요청과 한도를 값으로 받는다. JVM 힙은 컨테이너 한도 비율로 정한다(X-64) |
| 보안 | 비루트, 읽기 전용 루트 파일 시스템(쓰는 곳은 데이터 볼륨과 임시 폴더만), 권한 상승 금지 |

## 3. 값(`values.yaml`) 제안

- `image.repository`, `image.tag`(기본은 차트의 `appVersion`)
- `database.type`(`postgresql` 권장 기본, `mariadb`, `h2`, `mssql`, `cubrid`), `database.url`, `database.username`, `database.existingSecret`
- `yona.dataDir`, `yona.scheme`, `yona.hostname`, `yona.port`
- `ssh.mina.enabled`, `ssh.mina.port`, `ssh.publicPort` (**시스템 sshd 연동은 없다**)
- `persistence.size`, `persistence.storageClass`, `persistence.existingClaim`
- `ingress.*`, `resources`, `nodeSelector`, `tolerations`

H2는 개발과 시험용으로만 안내하고 운영 값으로 쓰지 않도록 차트 설명에 적는다.

## 4. 구현 전에 확인할 것 (아직 확인하지 않았다)

1. X-64 이미지가 먼저 있어야 한다.
2. ~~복제본 1개와 업데이트 때의 끊김~~ **결정됨(2026-11-20 선생님)**: 복제본 1개로 고정하고, **업데이트 때 서비스가 잠깐 끊기는 것을 받아들인다.** 기존 파드를 내린 뒤 새 파드를 올리므로 시작(스키마 갱신 포함)하는 동안 수 초에서 수 분 끊긴다. 대신 데이터 디렉터리에 두 서버가 동시에 쓰는 일과, 스키마 갱신이 두 버전 사이에서 부딪치는 일을 원천적으로 막는다.
3. 환영 화면에서 관리자를 만든 뒤 **서버를 다시 시작**해야 한다. 쿠버네티스에서는 파드 재시작이 되므로 안내 문구와 방법(예: `kubectl rollout restart`)을 적는다. `yona.secret`을 미리 주면 환영 화면이 나오지 않고 관리자 초기 비밀번호를 알 수 없으므로 신규 설치에서는 주지 않는다([fresh-install-guide.md](fresh-install-guide.md) 4절).
4. ~~읽기 전용 루트 파일 시스템의 쓰는 경로~~ **확인함(2026-11-20)**: 쓰는 곳은 데이터 볼륨과 `/tmp`뿐이다(Tomcat의 `tomcat-docbase.*`, `tomcat.*`). `/tmp`에 `emptyDir`를 붙인다. **비루트일 때 볼륨 소유권이 맞지 않으면 시작할 때 로그 파일을 만들지 못하므로 `fsGroup`(또는 `runAsUser`와 소유권)을 반드시 맞춘다.** 자세한 결과는 [container-image-plan.md](container-image-plan.md) 4절.
5. ~~차트 게시 방식~~ **결정됨(2026-11-20 선생님)**: **OCI 레지스트리**(이미지와 같은 GitHub Container Registry)에 올린다(`helm install oci://...`, Helm 3.8 이상). 정적 `index.yaml` 저장소는 두지 않는다. 서명은 이미지와 같이 처음에는 하지 않는다.
6. 폐쇄 환경에서 이미지와 차트를 내부 레지스트리로 옮기는 방법을 안내한다.

## 5. 시험 기준 (구현 때)

- `helm lint`와 `helm template`이 오류 없이 끝난다(값 조합별).
- 로컬 클러스터(kind 등)에 외부 PostgreSQL로 설치하고 readiness가 통과한다.
- 파드를 지워도(볼륨 유지) 데이터, 비밀 키, SSH 호스트 키가 그대로다.
- `helm uninstall` 뒤에도 볼륨이 남는다.
- `helm upgrade`로 새 이미지 태그를 올렸을 때 스키마가 시작 때 맞춰진다.
