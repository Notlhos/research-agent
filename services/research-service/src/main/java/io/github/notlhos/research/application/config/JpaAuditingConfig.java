package io.github.notlhos.research.application.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA auditing of entity creation and modification.
 *
 * <p>Changes made outside an authenticated request, by schedulers and Kafka listeners, are attributed to
 * {@value #SYSTEM_AUDITOR}. Timestamps come from the application {@link Clock}, so tests control them.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    public static final String SYSTEM_AUDITOR = "system";

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentAuditor());
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    /* ***** Private Methods ***** */

    private static String currentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM_AUDITOR;
        }
        return authentication.getName();
    }

}
