package com.scanmesh.api.target.service;

import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetUrlTest {

    @ParameterizedTest
    @CsvSource({
        "https://Example.COM,                        https://example.com/,          example.com",
        "https://example.com:443/a/b?x=1#frag,        https://example.com/a/b,       example.com",
        "http://example.com:80,                       http://example.com/,           example.com",
        "http://shop.example.com:8080/app/,           http://shop.example.com:8080/app/, shop.example.com",
        "https://example.com./,                       https://example.com/,          example.com",
        "https://한글.com,                             https://xn--bj0bj06e.com/,     xn--bj0bj06e.com",
    })
    void 주소를_정규화한다(String input, String baseUrl, String host) {
        TargetUrl url = TargetUrl.parse(input);

        assertThat(url.baseUrl()).isEqualTo(baseUrl);
        assertThat(url.host()).isEqualTo(host);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "example.com", "ftp://example.com", "file:///etc/passwd", "javascript:alert(1)",
        "http://127.0.0.1", "http://[::1]/", "http://2130706433", "http://0x7f.1",
        "http://localhost", "http://app.localhost", "http://intranet", "http://db.internal", "http://printer.local",
        "http://router.home.arpa", "http://site.test",
        "https://user:pw@example.com", "https://example.com@evil.com",
        "http://exa_mple.com", "http://-bad.example.com", "http://example.com:0", "http://example.com:70000",
    })
    void 받을_수_없는_주소는_막는다(String input) {
        assertThatThrownBy(() -> TargetUrl.parse(input))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TARGET_URL);
    }
}
