package com.omits.social_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Outbound HTTP configuration. WebClient is used for calls to platform APIs only — the server
 * itself is MVC/Tomcat (see CLAUDE.md); do not return Mono/Flux from controllers.
 */
@Configuration
@EnableConfigurationProperties(BlueskyProperties.class)
public class WebClientConfig {

    private static final int MAX_IN_MEMORY_SIZE = 2 * 1024 * 1024;

    /**
     * Shared builder carrying settings common to every outbound client (raised in-memory buffer
     * for XRPC/JSON payloads). Clone it per platform so per-client settings don't leak.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE));
    }

    @Bean
    public WebClient blueskyWebClient(WebClient.Builder webClientBuilder, BlueskyProperties blueskyProperties) {
        return webClientBuilder.clone()
                .baseUrl(blueskyProperties.baseUrl())
                .build();
    }
}
