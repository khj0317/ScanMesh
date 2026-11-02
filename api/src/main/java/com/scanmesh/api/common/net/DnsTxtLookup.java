package com.scanmesh.api.common.net;

import java.io.IOException;
import java.util.List;

/** DNS TXT 레코드를 조회한다. 테스트에서 실제 DNS 대신 정해 둔 값을 돌려주도록 바꿔 끼운다 */
public interface DnsTxtLookup {

    /**
     * @return 레코드 값 목록. 이름이 없거나 TXT 레코드가 없으면 빈 목록
     * @throws IOException DNS 서버가 응답하지 않을 때
     */
    List<String> lookup(String name) throws IOException;
}
