package com.omits.social_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "social-api.aws")
public record AwsProperties(String region) {
}
