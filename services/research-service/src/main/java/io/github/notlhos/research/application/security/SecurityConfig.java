package io.github.notlhos.research.application.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless API-key security.
 *
 * <p>Clients authenticate with the {@value #API_KEY_HEADER} header: the client key grants {@code /research/**},
 * the admin key additionally grants {@code /admins/**}. Health, metrics and API docs stay public; in production
 * they are reachable only inside the cluster, and the docs are switched off by the {@code prod} profile.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    public static final String API_KEY_HEADER = "X-API-Key";

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error"
    };

    private final SecurityProperties properties;

    private final JsonMapper jsonMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler(jsonMapper);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(properties), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/research/**").hasRole(Roles.CLIENT)
                        .requestMatchers("/admins/**").hasRole(Roles.ADMIN)
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }

}
