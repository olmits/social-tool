package com.omits.social_api.account.credential;

public interface CredentialStore {

    String store(String credentialValue);

    /**
     * Reads back the stored credential value for the given reference. Adapters call this at
     * request time to obtain the secret needed to authenticate with a platform.
     */
    String resolve(String secretRef);

    void delete(String secretRef);
}
