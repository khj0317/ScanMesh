package com.scanmesh.api.common.exception;

public record ErrorResponse(int status, String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse validation(String message) {
        return new ErrorResponse(400, "VALIDATION_ERROR", message);
    }
}
