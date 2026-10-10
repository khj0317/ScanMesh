package com.secuscan.common.scan;

import java.util.Arrays;
import java.util.List;

/**
 * 점검 엔진. 점검 하나는 엔진마다 작업 하나로 나뉘고, 엔진 종류에 맞는 워커가 작업을 가져간다.
 * 0주차 실험 기준 최대 메모리: Nuclei 약 0.9GB, ZAP 약 1GB.
 */
public enum Engine {

    /** 보안 헤더, 쿠키 설정, TLS, 민감 파일 노출을 직접 확인하는 규칙 */
    CUSTOM(WorkerKind.LIGHT),
    NUCLEI(WorkerKind.LIGHT),
    ZAP(WorkerKind.HEAVY);

    private final WorkerKind workerKind;

    Engine(WorkerKind workerKind) {
        this.workerKind = workerKind;
    }

    public WorkerKind getWorkerKind() {
        return workerKind;
    }

    public static List<Engine> runnableBy(WorkerKind kind) {
        return Arrays.stream(values())
            .filter(engine -> engine.workerKind == kind)
            .toList();
    }
}
