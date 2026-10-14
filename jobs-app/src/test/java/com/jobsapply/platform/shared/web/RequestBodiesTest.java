package com.jobsapply.platform.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class RequestBodiesTest {

    private static MockHttpServletRequest request(byte[] body, boolean declareLength) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return declareLength ? body.length : -1; // -1 = chunked / unknown
            }
        };
        request.setContent(body);
        return request;
    }

    @Test
    void readsBodiesWithinTheLimit() throws Exception {
        assertThat(RequestBodies.readText(request("héllo".getBytes(StandardCharsets.UTF_8), true))).isEqualTo("héllo");
    }

    @Test
    void rejectsDeclaredOversizedBodiesBeforeReading() {
        byte[] body = new byte[RequestBodies.MAX_UPLOAD_BYTES + 1];
        assertThatThrownBy(() -> RequestBodies.readText(request(body, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }

    @Test
    void rejectsUndeclaredOversizedBodiesWhileReading() {
        byte[] body = new byte[RequestBodies.MAX_UPLOAD_BYTES + 1];
        assertThatThrownBy(() -> RequestBodies.readText(request(body, false)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }
}
