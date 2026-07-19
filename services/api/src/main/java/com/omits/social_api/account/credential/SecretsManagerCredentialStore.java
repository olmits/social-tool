package com.omits.social_api.account.credential;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.UUID;

@Component
public class SecretsManagerCredentialStore implements CredentialStore {

    private static final String SECRET_NAME_PREFIX = "social-api/accounts/";

    private final SecretsManagerClient secretsManagerClient;

    public SecretsManagerCredentialStore(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    @Override
    public String store(String credentialValue) {
        String secretName = SECRET_NAME_PREFIX + UUID.randomUUID();
        secretsManagerClient.createSecret(CreateSecretRequest.builder()
                .name(secretName)
                .secretString(credentialValue)
                .build());
        return secretName;
    }

    @Override
    public String resolve(String secretRef) {
        return secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
                        .secretId(secretRef)
                        .build())
                .secretString();
    }

    @Override
    public void delete(String secretRef) {
        secretsManagerClient.deleteSecret(DeleteSecretRequest.builder()
                .secretId(secretRef)
                .forceDeleteWithoutRecovery(true)
                .build());
    }
}
