package com.scanmesh.api.target.entity;

/** 도메인 소유를 확인하는 방법 */
public enum VerificationMethod {

    /** _scanmesh-verification.{host} 에 TXT 레코드로 인증 토큰을 올린다 */
    DNS_TXT,
    /** {origin}/.well-known/scanmesh-verification.txt 파일에 인증 토큰을 올린다 */
    WELL_KNOWN_FILE
}
