package io.github.notlhos.research.application.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * API keys bound from {@code research.security.*}; the service refuses to start without them.
 */
@Validated
@ConfigurationProperties("research.security")
public record SecurityProperties(@NotBlank String apiKey, @NotBlank String adminApiKey) {
}
