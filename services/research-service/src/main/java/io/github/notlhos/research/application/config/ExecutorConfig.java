package io.github.notlhos.research.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;

@Configuration
class ExecutorConfig {

    @Bean
    TaskExecutor searchExecutor() {
        return new VirtualThreadTaskExecutor("arxiv-search-");
    }

}
