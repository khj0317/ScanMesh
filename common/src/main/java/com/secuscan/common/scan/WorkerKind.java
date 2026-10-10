package com.secuscan.common.scan;

/** 워커 종류. 메모리를 많이 쓰는 엔진은 무거운 워커에서만 돌린다 */
public enum WorkerKind {
    LIGHT,
    HEAVY
}
