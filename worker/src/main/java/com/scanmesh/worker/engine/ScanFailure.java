package com.scanmesh.worker.engine;

import java.io.IOException;

/**
 * 사용자에게 그대로 보여 줘도 되는 점검 실패. 메시지가 작업의 오류 메시지로 API에 저장되어 화면에 보인다.
 * 이것이 아닌 예외는 Docker 출력이나 워커 PC의 경로 같은 내부 정보가 섞일 수 있어 일반 문구로 바꿔 보낸다(TaskExecutor).
 */
public class ScanFailure extends IOException {

    public ScanFailure(String message) {
        super(message);
    }
}
