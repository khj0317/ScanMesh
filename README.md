# ScanMesh

등록한 웹사이트를 여러 오픈소스 점검 엔진(ZAP, Nuclei, 자체 규칙)으로 점검하고,
결과를 하나로 정리해 위험도와 조치 방법을 보여 주는 웹 취약점 점검 서비스입니다.

> 소유를 인증한 사이트만 점검합니다. 허락받지 않은 사이트 점검은 불법입니다.

## 구조

| 모듈 | 하는 일 |
| --- | --- |
| `common` | API와 워커가 함께 쓰는 코드: 점검 엔진 종류, 작업 상태 전이 규칙, SSRF 방어용 주소 검사 |
| `api` | 회원, 점검 대상, 점검 요청, 작업 큐, 결과 저장 (Spring Boot, 포트 8190) |
| `worker` | API에서 작업을 가져와 점검 엔진을 실행 (내 PC에서 실행, Nuclei와 ZAP은 Docker 컨테이너로 띄움) |
| `web` | 화면: 로그인, 점검 대상 등록과 소유 인증, 점검 요청과 진행 상황, 결과 (React, 포트 5181) |
| `lab` | 0주차 점검 엔진 실험 환경과 측정 결과 |

설계(ERD, 상태 흐름, API, SSRF 방어, 주요 결정)는 [docs/design.md](docs/design.md)에, 배포 순서는 [docs/deploy.md](docs/deploy.md)에 있습니다.

## 로컬 실행

Java 21, Node.js 24, Docker가 필요합니다.

```bash
docker compose up -d          # Postgres (localhost:5434)
./gradlew :api:bootRun        # API 서버 (http://localhost:8190/swagger-ui.html)
./gradlew :worker:bootRun     # 워커
./gradlew build               # 전체 빌드와 테스트 (테스트는 Testcontainers로 Postgres를 띄움)
```

화면은 따로 띄웁니다. API 주소는 `VITE_API_URL`로 바꿀 수 있고 기본값은 `http://localhost:8190`입니다.

```bash
cd web
npm install
npm run dev                   # http://localhost:5181
npm test                      # API 클라이언트 테스트
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

LIGHT 워커는 자체 규칙과 Nuclei를, HEAVY 워커는 ZAP을 실행하므로 전체 엔진을 쓰려면 두 대를 띄웁니다. Docker가 켜져 있어야 합니다.

Nuclei 템플릿은 처음 한 번 받아 둡니다([lab/README.md](lab/README.md)의 템플릿 받기 명령). 기본 볼륨은 `scanmesh-lab_nuclei-templates`이고,
다른 볼륨을 쓰면 `WORKER_NUCLEI_TEMPLATES_VOLUME`으로 지정합니다.

엔진 결과는 공통 형식으로 바꿔 작업 완료 보고에 실어 API에 저장하고, `GET /api/scans/{id}/findings`로 봅니다.
Nuclei와 ZAP의 원본 결과 파일은 확인용으로 `worker/worker-results/scan-{점검 ID}/`에 남고, 7일(`WORKER_RESULTS_RETENTION`)이 지나면 워커가 지웁니다.

### 연습용 대상 앱 점검

`localhost`나 Docker 서비스 이름은 SSRF 방어 때문에 대상으로 등록할 수 없습니다. 개발할 때만 API와 워커 모두에
`DEV_ALLOWED_HOSTS`를 넣으면, 그 호스트는 주소 검사 없이 등록되고 바로 인증됩니다. 배포(prod)에서 값이 있으면 API 서버가 뜨지 않습니다.

```bash
DEV_ALLOWED_HOSTS=localhost,juice-shop,host.docker.internal ./gradlew :api:bootRun
```

대상 주소는 워커가 어디서 도는지에 따라 다릅니다. Nuclei와 ZAP은 컨테이너 안에서 돌기 때문에 `localhost`로는 Juice Shop에 닿지 않습니다.

| 워커 실행 위치 | Juice Shop 주소 |
| --- | --- |
| 내 PC에서 `./gradlew :worker:bootRun` (기본) | `http://host.docker.internal:3000` |
| 워커도 Juice Shop과 같은 Docker 네트워크 안 (`WORKER_DOCKER_NETWORK` 지정) | `http://juice-shop:3000` |

## 진행 상황

- [x] 0주차: 점검 엔진 시험 → [lab/README.md](lab/README.md)
- [x] 1주차: 설계, 멀티 모듈 셋업, CI, 회원 기능 (가입, 로그인, 리프레시 토큰 회전과 탈취 감지)
- [x] 2주차: 점검 대상 등록, 도메인 소유 인증(DNS TXT, 인증 파일), SSRF 방어
- [x] 3주차: 점검 요청·취소, PostgreSQL 작업 큐(`SKIP LOCKED`), 워커 API와 작업 루프, 임대·하트비트·재시도
- [x] 4주차: 점검 엔진 연동 (자체 규칙, Nuclei, ZAP), DNS 고정, 대상 무응답 시 중단
- [x] 5주차: 엔진 결과를 공통 형식으로 통합, 엔진 간 중복 묶기, OWASP Top 10 분류, 결과 조회 API
- [x] 6주차: 화면 (로그인, 대상 등록과 소유 인증, 점검 요청·진행 상황·취소, 결과), 워커 오프라인 표시
- [x] 7주차: 배포 준비 (API Docker 이미지, Render·Vercel 설정, 배포 안내), 배포와 같은 구조를 내 PC에서 확인
- [ ] 실제 배포: [docs/deploy.md](docs/deploy.md) 순서대로 Supabase, Render, Vercel 계정에서 진행
