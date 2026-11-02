package com.pelypets.todolist.server.adapter.in.web.security;

import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 with the API's error body and a Bearer challenge, for a missing, invalid or expired access token. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;
    private final String challenge;

    public JsonAuthenticationEntryPoint(ErrorResponseWriter writer, String realm) {
        this.writer = writer;
        this.challenge = "Bearer realm=\"" + realm + "\"";
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        writer.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Access token is missing, invalid or expired");
    }
}
