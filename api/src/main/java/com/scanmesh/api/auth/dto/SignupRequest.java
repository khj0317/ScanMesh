package com.scanmesh.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank @Email @Size(max = 254) String email,
    // BCrypt는 72바이트까지만 비교한다. 공백 없는 ASCII 문자만 받아 64자 = 64바이트로 그 안에 들어가게 한다
    @NotBlank @Size(min = 8, max = 64)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$", message = "영문과 숫자를 모두 포함하고, 공백 없이 영문·숫자·특수문자만 쓸 수 있습니다.")
    String password,
    @NotBlank @Size(max = 50) String name
) {

    /** 복사해 붙인 이메일의 앞뒤 공백 때문에 형식 검사에서 떨어지지 않게, 검사 전에 정리한다 */
    public SignupRequest {
        email = email == null ? null : email.strip();
    }
}
