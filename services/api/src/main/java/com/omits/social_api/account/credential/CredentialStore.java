package com.omits.social_api.account.credential;

public interface CredentialStore {

    String store(String credentialValue);

    void delete(String secretRef);
}
