package io.github.notlhos.research.application.integration.arxiv;

import io.github.notlhos.research.application.config.ResearchProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
class ArxivConfig {

    private static final String USER_AGENT = "research-service";

    @Bean
    ArxivApi arxivApi(ResearchProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.arxiv().timeout());
        requestFactory.setReadTimeout(properties.arxiv().timeout());
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.arxiv().baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ArxivApi.class);
    }

}
