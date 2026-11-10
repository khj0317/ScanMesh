package com.scanmesh.worker.engine;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;

/**
 * 점검 엔진 하나를 실행한다. 구현을 빈으로 등록하면 워커가 그 엔진의 작업을 가져가기 시작한다.
 */
public interface EngineRunner {

    Engine engine();

    /**
     * 끝날 때까지 막고 있다가 돌아온다. 취소되면 스레드 인터럽트가 걸리므로, 오래 기다리는 곳에서
     * InterruptedException을 그대로 던지거나 {@link TaskContext#isCancelled()}를 확인해 멈춘다.
     *
     * @throws Exception 엔진 실패. 메시지가 작업의 오류 메시지로 남는다
     */
    void run(ClaimedTask task, TaskContext context) throws Exception;
}
