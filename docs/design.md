# ScanMesh 설계

1주차 설계 문서입니다. 2주차 이후 기능도 미리 잡아 두고, 구현하면서 바뀌는 부분은 이 문서를 고칩니다.

## 1. 전체 구조

```mermaid
flowchart LR
    user[사용자 브라우저] --> web[React 화면<br/>Vercel]
    web --> api[API 서버<br/>Render]
    api <--> db[(PostgreSQL<br/>Supabase<br/>작업 큐 포함)]
    subgraph pc[내 PC · Docker]
        light[가벼운 워커<br/>자체 규칙 · Nuclei]
        heavy[무거운 워커<br/>ZAP]
        target[연습용 대상 앱<br/>Juice Shop]
    end
    light -- 작업 가져가기 · 결과 보내기 --> api
    heavy -- 작업 가져가기 · 결과 보내기 --> api
    light -- 점검 --> target
    heavy -- 점검 --> target
```

| 모듈 | 하는 일 |
| --- | --- |
| `common` | API와 워커가 함께 쓰는 코드: 점검 엔진 종류, 작업 상태와 상태 전이 규칙 |
| `api` | 회원, 점검 대상, 점검 요청, 작업 큐, 결과 저장. 워커가 작업을 가져가는 API도 제공 |
| `worker` | API에서 작업을 가져와 점검 엔진을 실행하고 결과를 돌려줌 |

## 2. ERD

1주차에 `members`, `refresh_tokens`, 2주차에 `targets`를 만들었고 나머지는 해당 주차에 Flyway 마이그레이션으로 추가합니다.

```mermaid
erDiagram
    members ||--o{ refresh_tokens : "로그인 유지"
    members ||--o{ targets : "등록"
    targets ||--o{ scans : "점검"
    scans ||--|{ scan_tasks : "엔진별 작업"
    scan_tasks ||--o{ findings : "발견"
    workers ||--o{ scan_tasks : "실행"

    members {
        bigint id PK
        varchar email UK "소문자로 정규화"
        varchar password "BCrypt"
        varchar name
        varchar role "USER, ADMIN"
        timestamptz created_at
        timestamptz updated_at
    }
    refresh_tokens {
        bigint id PK
        bigint member_id FK
        varchar token_hash UK "SHA-256, 원문은 저장하지 않음"
        timestamptz created_at
        timestamptz expires_at
        timestamptz revoked_at
    }
    targets {
        bigint id PK
        bigint member_id FK
        varchar base_url
        varchar host "member_id와 함께 UK"
        varchar verification_method "DNS_TXT, WELL_KNOWN_FILE"
        varchar verification_token
        timestamptz verified_at "null이면 점검 불가"
        timestamptz last_verification_attempt_at "인증 확인 간격 제한"
    }
    scans {
        bigint id PK
        bigint target_id FK
        bigint requested_by FK
        varchar status "작업 상태에서 계산"
        timestamptz requested_at
        timestamptz finished_at
    }
    scan_tasks {
        bigint id PK
        bigint scan_id FK
        varchar engine "CUSTOM, NUCLEI, ZAP"
        varchar status "QUEUED, RUNNING, ..."
        int attempt
        int max_attempts
        bigint worker_id FK
        timestamptz lease_until "이 시간까지 하트비트가 없으면 워커가 죽은 것으로 봄"
        boolean cancel_requested
        int progress "0~100"
        varchar error_message
    }
    findings {
        bigint id PK
        bigint task_id FK
        varchar engine
        varchar rule_id "엔진의 규칙 ID"
        varchar title
        varchar severity "CRITICAL, HIGH, MEDIUM, LOW, INFO"
        int cwe_id
        varchar owasp_category
        varchar url
        text evidence
        varchar fingerprint "엔진 간 중복 제거용"
    }
    workers {
        bigint id PK
        varchar name
        varchar kind "LIGHT, HEAVY"
        varchar token_hash UK
        timestamptz last_seen_at "화면의 '워커 오프라인' 표시에 사용"
    }
```

## 3. 점검 상태 흐름

점검 하나(`scans`)는 엔진별 작업(`scan_tasks`)으로 나뉩니다. 가벼운 워커는 `CUSTOM`, `NUCLEI` 작업을, 무거운 워커는 `ZAP` 작업을 가져갑니다.
ZAP이 실패해도 다른 엔진의 결과는 남습니다.

### 작업 상태 (`TaskStatus`)

```mermaid
stateDiagram-v2
    [*] --> QUEUED : 점검 요청
    QUEUED --> RUNNING : 워커가 가져감
    QUEUED --> CANCELLED : 사용자 취소
    RUNNING --> SUCCEEDED : 결과 제출
    RUNNING --> FAILED : 엔진 오류, 대상 응답 없음
    RUNNING --> QUEUED : 하트비트 끊김, 재시도 남음
    RUNNING --> FAILED : 하트비트 끊김, 재시도 소진
    RUNNING --> CANCELLED : 취소 요청을 워커가 확인
    SUCCEEDED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

- 상태 전이 규칙은 `common` 모듈의 `TaskStatus`에 있고, 허용되지 않은 전이는 예외로 막습니다.
- 워커는 작업을 실행하는 동안 하트비트를 보내 `lease_until`을 늘립니다. 하트비트 응답에 취소 요청 여부가 실려 옵니다.

### 점검 상태 (`ScanStatus`)

점검 상태는 따로 바꾸지 않고 작업 상태들에서 계산합니다. 상태를 두 군데에서 따로 바꾸면 서로 어긋날 수 있기 때문입니다.

| 작업 상태들 | 점검 상태 |
| --- | --- |
| 모두 `QUEUED` | `QUEUED` |
| 하나라도 `QUEUED` 또는 `RUNNING`이고 시작된 작업이 있음 | `RUNNING` |
| 모두 `SUCCEEDED` | `COMPLETED` |
| 끝났고 일부만 `SUCCEEDED` | `PARTIAL` |
| 끝났고 `SUCCEEDED`가 없고 `FAILED`가 있음 | `FAILED` |
| 모두 `CANCELLED` | `CANCELLED` |

## 4. API

### 1주차 (구현 완료)

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입 | - |
| POST | `/api/auth/login` | 로그인, 액세스·리프레시 토큰 발급 | - |
| POST | `/api/auth/refresh` | 리프레시 토큰으로 새 토큰 발급 (기존 토큰은 폐기) | - |
| POST | `/api/auth/logout` | 리프레시 토큰 폐기 | - |
| GET | `/api/members/me` | 내 정보 | 사용자 |
| GET | `/api/health` | 서버 상태 | - |

### 2주차 (구현 완료)

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/targets` | 점검 대상 등록, 소유 인증 토큰 발급 | 사용자 |
| GET | `/api/targets` | 내 점검 대상 목록 | 사용자 |
| POST | `/api/targets/{id}/verify` | 소유 인증 확인. 실패해도 200으로 `verified: false`와 고칠 점을 돌려줌. 10초에 한 번 | 사용자 |
| DELETE | `/api/targets/{id}` | 점검 대상 삭제 | 사용자 |

다른 회원의 대상은 403 대신 404로 응답해서 대상 ID가 있는지 알 수 없게 합니다.

### 3주차 이후 (예정)

| 메서드 | 경로 | 설명 | 주차 |
| --- | --- | --- | --- |
| POST | `/api/targets/{id}/scans` | 점검 요청 | 3 |
| GET | `/api/scans/{id}` | 점검 상태와 진행률 | 3 |
| POST | `/api/scans/{id}/cancel` | 점검 취소 | 3 |
| GET | `/api/scans/{id}/findings` | 점검 결과 | 5 |
| POST | `/api/worker/tasks/claim` | 워커가 작업 가져가기 | 3 |
| POST | `/api/worker/tasks/{id}/heartbeat` | 하트비트, 진행률 보고, 취소 여부 확인 | 3 |
| POST | `/api/worker/tasks/{id}/complete` | 결과 제출 | 3 |
| POST | `/api/worker/tasks/{id}/fail` | 실패 보고 | 3 |

워커 API는 사용자 JWT 대신 워커 전용 토큰(`X-Worker-Token`)으로 인증합니다.

### 에러 응답

모든 에러는 같은 형식으로 응답합니다.

```json
{ "status": 409, "code": "DUPLICATE_EMAIL", "message": "이미 가입된 이메일입니다." }
```

## 5. 점검 대상과 SSRF 방어

점검 서비스는 사용자가 입력한 주소로 서버가 직접 요청을 보내므로, 그 주소가 내부망을 가리키면
우리 서버를 통해 바깥에서 보이지 않는 곳(DB, 클라우드 메타데이터 `169.254.169.254` 등)을 건드릴 수 있습니다(SSRF).

### 소유 인증

| 방법 | 사용자가 할 일 |
| --- | --- |
| `DNS_TXT` | `_scanmesh-verification.{host}`에 TXT 레코드로 인증 토큰을 추가 |
| `WELL_KNOWN_FILE` | `{origin}/.well-known/scanmesh-verification.txt`에 인증 토큰 한 줄을 올림 |

인증하지 않은 대상은 점검을 요청할 수 없습니다(3주차에 적용).

### 검사 단계

| 단계 | 검사 | 위치 |
| --- | --- | --- |
| 등록 | http/https만, 도메인만(IP 주소 불가), 아이디·비밀번호(@) 불가, `localhost`·`.internal`·`.local` 같은 내부용 이름 불가 | `TargetUrl` |
| 등록 | DNS로 찾은 주소가 하나라도 내부망·예약 주소면 거부 | `TargetService` |
| 인증 파일 요청 | 연결 직전에 다시 DNS를 조회해 검사하고, 검사한 주소로 그대로 연결 (DNS 리바인딩 방어) | `SafeHttpClient` |
| 인증 파일 요청 | 리다이렉트는 같은 호스트 안에서 3번까지, 전체 5초, 본문 4KB까지, 프록시 사용 안 함 | `SafeHttpClient` |
| 점검 (4주차) | 워커가 점검 시작 전에 같은 규칙으로 다시 검사 | `common`의 `PublicAddressPolicy` |

막는 주소 범위(사설망, 루프백, 링크 로컬, 통신사 NAT, 멀티캐스트, 문서용, IPv6 ULA·NAT64·6to4, IPv4 매핑 주소 등)는
`PublicAddressPolicy`에 있고, API와 워커가 함께 쓰도록 `common` 모듈에 둡니다.

## 6. 주요 결정

| 결정 | 이유 | 대안과 버린 이유 |
| --- | --- | --- |
| 작업 큐를 PostgreSQL로 구현 (`FOR UPDATE SKIP LOCKED`) | 점검·작업 상태와 같은 트랜잭션으로 바꿀 수 있고, 무료 인프라에 서버를 하나 덜 둠 | Redis, Kafka: 무료 플랜에서 서버가 늘고, DB와 큐 사이의 일관성을 따로 맞춰야 함 |
| 워커가 먼저 접속해 작업을 가져가는 방식 | 내 PC의 포트를 열 필요가 없고, 워커가 꺼져 있으면 작업이 큐에 쌓여 기다림 | API가 워커를 호출하는 방식: 집 PC에 외부 접속을 열어야 함 |
| 작업 임대(`lease_until`)와 하트비트 | 워커가 죽으면 임대가 끝난 작업을 다시 큐로 돌려 재시도 | 실행 중 표시만 하기: 워커가 죽으면 작업이 영원히 실행 중으로 남음 |
| 엔진별 작업 분리 | 가벼운·무거운 워커 분리, 한 엔진이 실패해도 나머지 결과는 남음 | 점검 하나를 통째로 실행: ZAP 하나가 죽으면 전부 실패 |
| 엔진별 요청 속도 제한, 대상 응답 없으면 중단 | 0주차 실험에서 제한 없는 Nuclei 점검에 Juice Shop이 메모리 부족으로 죽음 | - |
| 이메일 로그인, 액세스 토큰 30분 + 리프레시 토큰 14일 회전 | 리프레시 토큰은 쓸 때마다 바꾸고, 버린 토큰이 다시 오면 탈취로 보고 모든 로그인을 끊음 | 세션: 프론트(Vercel)와 API(Render)가 다른 도메인이라 쿠키 설정이 까다로움 |
| 검사한 IP로 그대로 연결하는 HTTP 클라이언트 (Apache HttpClient의 DNS 조회 단계에서 검사) | 검사할 때와 연결할 때 DNS를 따로 조회하면 그 사이에 응답을 내부 주소로 바꿀 수 있음 | 요청 전에 `InetAddress`로 한 번 검사: DNS 리바인딩에 뚫림. JDK `HttpClient`: DNS 조회를 바꿔 끼울 수 없음 |
| 인증 확인 간격 제한을 조건부 UPDATE 한 문장으로 | 동시에 눌러도 한 번만 통과하고, 외부 요청을 기다리는 동안 행을 잠그지 않음 | `SELECT ... FOR UPDATE`: 최대 5초 걸리는 외부 요청 동안 DB 연결과 잠금을 붙잡음 |
| JWT subject에 회원 ID 사용 | 요청마다 DB를 조회하지 않고 인증, 이메일이 바뀌어도 토큰이 유효 | 이메일: 바뀔 수 있는 값 |
