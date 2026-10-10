# 0주차 실험: 점검 엔진 시험

내 PC의 Docker에서 연습용 대상 앱(OWASP Juice Shop)을 띄우고 점검 엔진을 돌려,
엔진별 메모리, 실행 시간, 결과 형태를 확인한 기록입니다.

## 실행 방법

```bash
cd lab
docker compose up -d juice-shop                                    # 대상 앱 (http://localhost:3000)
docker compose --profile scan run --rm --no-deps nuclei -update-templates   # Nuclei 템플릿 받기 (처음 한 번)
./measure.sh nuclei                                                # 실행 시간과 최대 메모리 측정
./measure.sh zap
docker compose down
```

결과 파일은 `lab/results/`에 저장되고 git에는 올리지 않습니다.

## 결과 (2026-10-10, Docker 메모리 7.4GB / 16스레드)

| 대상 | 최대 메모리 | 실행 시간 | 결과 |
| --- | --- | --- | --- |
| Juice Shop (대기 상태) | 158MiB | - | - |
| Nuclei 전체 템플릿 14,366개 | 781MiB | 334초 | **대상 앱이 메모리 부족으로 죽어서 0건** |
| Nuclei `-as -rl 50 -c 10` | 866MiB | 253초 | 4건, 모두 정보(info): 기술 식별 |
| ZAP baseline (탐색 2분) | 1,057MiB | 50초 | 11종: 중간 2, 낮음 5, 정보 4 (모두 CWE 번호 포함) |

## 알게 된 것

1. **대상 서버 보호 장치가 필수**: 제한 없이 템플릿을 전부 돌리자 Juice Shop이 2GB 힙을 넘겨 죽었습니다.
   서비스에 엔진별 초당 요청 수 제한과 동시 실행 수 제한을 넣고, 점검 중 대상이 응답하지 않으면
   점검을 멈추는 기능을 넣어야 합니다.
2. **엔진마다 잘 찾는 영역이 다름**: Nuclei는 알려진 CVE와 설정 실수용이라 Juice Shop 같은 앱 로직 취약점은
   못 찾았고, ZAP은 보안 헤더 같은 응답 문제를 주로 찾았습니다. 여러 엔진을 함께 쓰는 이유가 됩니다.
3. **메모리**: ZAP 약 1GB, Nuclei 약 0.9GB. 둘을 동시에 돌려도 내 PC에서는 여유가 있지만
   Render 무료(512MB)에서는 어느 쪽도 돌릴 수 없습니다. 워커를 내 PC에 두는 구조가 맞습니다.
4. **ZAP 탐색 한계**: 기본 탐색기는 JavaScript로 화면을 그리는 SPA를 깊이 돌지 못해 URL을 적게 찾았습니다.
   깊은 점검(2차)에는 Ajax 탐색기나 로그인 후 탐색이 필요합니다.
5. **결과 형식**: ZAP JSON은 위험도, 신뢰도, CWE를 주고 Nuclei JSONL은 심각도와 템플릿 ID를 줍니다.
   5주차 결과 통합 때 두 형식을 공통 모델로 바꾸는 작업이 필요합니다.
