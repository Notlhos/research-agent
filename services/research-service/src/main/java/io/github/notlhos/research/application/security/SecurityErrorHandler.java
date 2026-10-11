package io.github.notlhos.research.application.security;

import io.github.notlhos.research.application.exception.ErrorCode;
import io.github.notlhos.research.application.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
            throws IOException {
        write(response, ErrorResponse.of(HttpStatus.UNAUTHORIZED,
                "Missing or invalid " + SecurityConfig.API_KEY_HEADER + " header", List.of(ErrorCode.UNAUTHORIZED)));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
            throws IOException {
        write(response, ErrorResponse.of(HttpStatus.FORBIDDEN,
                "API key is not allowed to access this resource", List.of(ErrorCode.FORBIDDEN)));
    }

    /* ***** Private Methods ***** */

    private void write(HttpServletResponse response, ErrorResponse body) throws IOException {
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

}
