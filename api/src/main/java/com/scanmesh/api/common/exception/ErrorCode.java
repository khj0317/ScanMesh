package com.scanmesh.api.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    // 이메일이 없는지 비밀번호가 틀렸는지 구분하지 않는다. 구분하면 가입된 이메일을 알아낼 수 있다
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "같은 정보로 이미 처리된 요청이 있어요. 잠시 후 다시 확인해주세요."),

    INVALID_TARGET_URL(HttpStatus.BAD_REQUEST, "http:// 또는 https://로 시작하는 도메인 주소를 입력해주세요."),
    TARGET_HOST_NOT_FOUND(HttpStatus.BAD_REQUEST, "도메인을 찾을 수 없습니다. 주소를 확인해주세요."),
    TARGET_ADDRESS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "내부망이나 예약된 IP 주소를 가리키는 도메인은 등록할 수 없습니다."),
    DUPLICATE_TARGET(HttpStatus.CONFLICT, "이미 등록한 도메인입니다."),
    TARGET_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "점검 대상을 더 등록할 수 없습니다. 쓰지 않는 대상을 삭제해주세요."),
    // 다른 회원의 대상도 같은 응답을 준다. 403으로 구분하면 다른 회원의 대상 ID가 있는지 알아낼 수 있다
    TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 점검 대상입니다."),
    VERIFICATION_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "소유 인증 확인은 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
