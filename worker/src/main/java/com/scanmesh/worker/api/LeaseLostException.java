package com.scanmesh.worker.api;

/** 이 워커가 더는 맡고 있지 않은 작업. 하던 일을 버리고 결과도 보내지 않는다 */
public class LeaseLostException extends RuntimeException {

    public LeaseLostException(Long taskId) {
        super("작업 " + taskId + "의 임대를 잃었습니다.");
    }
}
