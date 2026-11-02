package com.scanmesh.worker.engine;

/** 엔진이 실행 중에 진행률을 알리고 취소 여부를 확인하는 창구 */
public interface TaskContext {

    /** 0~100. 다음 하트비트 때 API로 보낸다 */
    void reportProgress(int progress);

    /** 사용자가 취소했거나 임대를 잃었으면 true. 엔진은 가능한 빨리 멈춘다 */
    boolean isCancelled();
}
