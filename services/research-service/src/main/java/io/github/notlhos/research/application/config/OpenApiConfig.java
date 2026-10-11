package io.github.notlhos.research.application.config;

import io.github.notlhos.research.application.security.SecurityConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    @Bean
    OpenAPI researchOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Research Service API")
                        .version("v1")
                        .description("Turns a topic into a literature report with citations verified against "
                                + "the retrieved arXiv papers."))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(SecurityConfig.API_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }

    @Bean
    OpenApiCustomizer securityErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations()
                .forEach(operation -> operation.getResponses()
                        .addApiResponse("401", errorResponse("Missing or invalid API key"))
                        .addApiResponse("403", errorResponse("API key is not allowed to call this operation"))));
    }

    /* ***** Private Methods ***** */

    private static ApiResponse errorResponse(String description) {
        Content content = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));
        return new ApiResponse().description(description).content(content);
    }

}
