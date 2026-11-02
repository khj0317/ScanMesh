# ScanMesh

등록한 웹사이트를 여러 오픈소스 점검 엔진(ZAP, Nuclei, 자체 규칙)으로 점검하고,
결과를 하나로 정리해 위험도와 조치 방법을 보여 주는 웹 취약점 점검 서비스입니다.

> 소유를 인증한 사이트만 점검합니다. 허락받지 않은 사이트 점검은 불법입니다.

## 구조

| 모듈 | 하는 일 |
| --- | --- |
| `common` | API와 워커가 함께 쓰는 코드: 점검 엔진 종류, 작업 상태 전이 규칙, SSRF 방어용 주소 검사 |
| `api` | 회원, 점검 대상, 점검 요청, 작업 큐, 결과 저장 (Spring Boot, 포트 8090) |
| `worker` | API에서 작업을 가져와 점검 엔진을 실행 (내 PC Docker에서 실행) |
| `lab` | 0주차 점검 엔진 실험 환경과 측정 결과 |

설계(ERD, 상태 흐름, API, SSRF 방어, 주요 결정)는 [docs/design.md](docs/design.md)에 있습니다.

## 로컬 실행

Java 21과 Docker가 필요합니다.

```bash
docker compose up -d          # Postgres (localhost:5434)
./gradlew :api:bootRun        # API 서버 (http://localhost:8090/swagger-ui.html)
./gradlew :worker:bootRun     # 워커
./gradlew build               # 전체 빌드와 테스트 (테스트는 Testcontainers로 Postgres를 띄움)
```

### 워커 등록

워커는 관리자가 등록해서 받은 토큰으로 API에 접속합니다. 관리자 가입 화면은 없으므로 가입한 계정의 권한을 DB에서 바꿉니다.

```bash
docker compose exec postgres psql -U scanmesh -c "update members set role = 'ADMIN' where email = '내 이메일'"
```

다시 로그인해서 받은 토큰으로 `POST /api/admin/workers`에 `{"name": "my-pc-light", "kind": "LIGHT"}`를 보내면
워커 토큰이 한 번만 나옵니다. 워커를 실행할 때 환경 변수로 넣습니다.

```bash
WORKER_TOKEN=받은토큰 WORKER_KIND=LIGHT ./gradlew :worker:bootRun
```

4주차에 엔진이 들어오기 전까지 워커는 실행할 엔진이 없어서 작업을 가져가지 않습니다.

## 진행 상황

- [x] 0주차: 점검 엔진 시험 → [lab/README.md](lab/README.md)
- [x] 1주차: 설계, 멀티 모듈 셋업, CI, 회원 기능 (가입, 로그인, 리프레시 토큰 회전과 탈취 감지)
- [x] 2주차: 점검 대상 등록, 도메인 소유 인증(DNS TXT, 인증 파일), SSRF 방어
- [x] 3주차: 점검 요청·취소, PostgreSQL 작업 큐(`SKIP LOCKED`), 워커 API와 작업 루프, 임대·하트비트·재시도
- [ ] 4주차: 점검 엔진 연동 (자체 규칙, Nuclei, ZAP)
