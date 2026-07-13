package com.example.prospera.Exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceExceptionHandlerTest {

    @Test
    void rateLimitResponseIsGenericAndIncludesRetryAfter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");

        ResponseEntity<StandardError> response = new ResourceExceptionHandler()
                .rateLimitExceeded(new RateLimitExceededException(123), request);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("123", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Too many requests. Please try again later.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("email"));
        assertFalse(response.getBody().getMessage().contains("IP"));
    }
}
