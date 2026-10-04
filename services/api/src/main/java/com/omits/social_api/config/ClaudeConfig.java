package com.omits.social_api.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The Anthropic client used by the generation slice.
 *
 * <p>Its own {@code @Configuration} rather than a bean on {@link WebClientConfig}: an
 * {@link AnthropicClient} is not a {@code WebClient}, and nothing about the shared builder
 * there applies to it.
 *
 * <p>{@code @EnableConfigurationProperties} is not optional. This application has no
 * {@code @ConfigurationPropertiesScan}, so a properties record nobody registers binds nothing
 * and silently hands out nulls.
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

    /**
     * Built even when no API key is configured, so the context still starts on a machine that
     * has never set one and only drafting fails. The generator checks the key at call time.
     *
     * <p>The timeout is the first one in this service — the Bluesky adapter has none — and it
     * exists because the SDK's default would let a single draft request hold a browser
     * connection for ten minutes. Retries are lowered from the SDK's default of two as well:
     * a timeout is itself retried, so worst-case wall clock is the timeout times one plus the
     * retry count.
     */
    @Bean
    public AnthropicClient anthropicClient(ClaudeProperties claudeProperties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(claudeProperties.apiKey() == null ? "" : claudeProperties.apiKey())
                .timeout(Duration.ofSeconds(claudeProperties.timeoutSeconds()))
                .maxRetries(1)
                .build();
    }
}
