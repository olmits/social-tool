package com.omits.social_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsConfig {

    @Bean
    public SecretsManagerClient secretsManagerClient(AwsProperties awsProperties) {
        return SecretsManagerClient.builder()
                .region(Region.of(awsProperties.region()))
                .build();
    }
}
