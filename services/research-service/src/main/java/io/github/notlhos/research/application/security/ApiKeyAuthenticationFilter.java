package io.github.notlhos.research.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String CLIENT_PRINCIPAL = "api-client";

    static final String ADMIN_PRINCIPAL = "admin";

    private final List<ApiKey> keys;

    ApiKeyAuthenticationFilter(SecurityProperties properties) {
        this.keys = List.of(
                new ApiKey(properties.adminApiKey(), ADMIN_PRINCIPAL, List.of(Roles.ADMIN, Roles.CLIENT)),
                new ApiKey(properties.apiKey(), CLIENT_PRINCIPAL, List.of(Roles.CLIENT)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional.ofNullable(request.getHeader(SecurityConfig.API_KEY_HEADER))
                .flatMap(this::authenticate)
                .ifPresent(ApiKeyAuthenticationFilter::setAuthentication);
        chain.doFilter(request, response);
    }

    /* ***** Private Methods ***** */

    private Optional<Authentication> authenticate(String presented) {
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        return keys.stream()
                .filter(key -> MessageDigest.isEqual(key.secret(), presentedBytes))
                .findFirst()
                .map(ApiKey::toAuthentication);
    }

    private static void setAuthentication(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private record ApiKey(byte[] secret, String principal, List<String> roles) {

        ApiKey(String secret, String principal, List<String> roles) {
            this(secret.getBytes(StandardCharsets.UTF_8), principal, roles);
        }

        Authentication toAuthentication() {
            return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    AuthorityUtils.createAuthorityList(roles.stream().map(role -> "ROLE_" + role).toList()));
        }

    }

}
