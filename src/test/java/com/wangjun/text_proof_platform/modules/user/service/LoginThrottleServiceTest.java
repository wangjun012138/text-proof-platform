package com.wangjun.text_proof_platform.modules.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleServiceTest {

    private final LoginThrottleService service = new LoginThrottleService();

    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("X-Real-IP", "198.51.100.21");

        assertThat(service.extractClientIp(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void shouldUseFirstUntrustedClientIpBeforeTrustedProxyChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.20, 10.10.0.8, 127.0.0.1");

        assertThat(service.extractClientIp(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void shouldFallbackToRemoteAddressWhenTrustedProxyHasNoForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThat(service.extractClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldUseValidatedRealIpWhenForwardedForIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.21");

        assertThat(service.extractClientIp(request)).isEqualTo("198.51.100.21");
    }

    @Test
    void shouldIgnoreInvalidForwardedAndRealIpValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        request.addHeader("X-Real-IP", "example.com");

        assertThat(service.extractClientIp(request)).isEqualTo("127.0.0.1");
    }
}
