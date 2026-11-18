package com.scanmesh.worker.api;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.scan.Engine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 워커가 API 서버에 보내는 요청. 테스트에서 실제 서버 대신 바꿔 끼운다 */
public interface WorkerApi {

    /** 가져갈 작업이 없으면 빈 값 */
    Optional<ClaimedTask> claim(Collection<Engine> engines);

    /**
     * @return 사용자가 취소를 요청했으면 true
     * @throws LeaseLostException 임대가 끝나 다른 워커에게 넘어갔거나 이미 끝난 작업
     */
    boolean heartbeat(Long taskId, int progress);

    /** 발견 항목은 작업 완료와 함께 한 번에 저장된다. 재시도한 작업이 결과를 두 번 남기지 않는다 */
    void complete(Long taskId, List<Finding> findings);

    void fail(Long taskId, String errorMessage);
}
