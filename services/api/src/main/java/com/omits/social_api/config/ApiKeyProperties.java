package com.omits.social_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "social-api.security")
public record ApiKeyProperties(String apiKey) {
}
