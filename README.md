# ScanMesh

등록한 웹사이트를 여러 오픈소스 점검 엔진(ZAP, Nuclei, 자체 규칙)으로 점검하고,
결과를 하나로 정리해 위험도와 조치 방법을 보여 주는 웹 취약점 점검 서비스입니다.

> 소유를 인증한 사이트만 점검합니다. 허락받지 않은 사이트 점검은 불법입니다.

## 구조

| 모듈 | 하는 일 |
| --- | --- |
| `common` | API와 워커가 함께 쓰는 코드: 점검 엔진 종류, 작업 상태 전이 규칙 |
| `api` | 회원, 점검 대상, 점검 요청, 작업 큐, 결과 저장 (Spring Boot, 포트 8090) |
| `worker` | API에서 작업을 가져와 점검 엔진을 실행 (내 PC Docker에서 실행) |
| `lab` | 0주차 점검 엔진 실험 환경과 측정 결과 |

설계(ERD, 상태 흐름, API, 주요 결정)는 [docs/design.md](docs/design.md)에 있습니다.

## 로컬 실행

Java 21과 Docker가 필요합니다.

```bash
docker compose up -d          # Postgres (localhost:5434)
./gradlew :api:bootRun        # API 서버 (http://localhost:8090/swagger-ui.html)
./gradlew :worker:bootRun     # 워커
./gradlew build               # 전체 빌드와 테스트 (테스트는 Testcontainers로 Postgres를 띄움)
```

## 진행 상황

- [x] 0주차: 점검 엔진 시험 → [lab/README.md](lab/README.md)
- [x] 1주차: 설계, 멀티 모듈 셋업, CI, 회원 기능 (가입, 로그인, 리프레시 토큰 회전과 탈취 감지)
- [ ] 2주차: 점검 대상 등록, 도메인 소유 인증, SSRF 방어
