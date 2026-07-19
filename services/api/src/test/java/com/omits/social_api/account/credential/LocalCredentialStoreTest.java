package com.omits.social_api.account.credential;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCredentialStoreTest {

    @TempDir
    Path credentialsDir;

    private LocalCredentialStore store() {
        return new LocalCredentialStore(credentialsDir.toString());
    }

    @Test
    void storeThenResolveReturnsTheValue() {
        LocalCredentialStore store = store();

        String ref = store.store("app-password");

        assertThat(ref).startsWith("local/accounts/");
        assertThat(store.resolve(ref)).isEqualTo("app-password");
    }

    @Test
    void distinctStoresGetDistinctRefsAndValues() {
        LocalCredentialStore store = store();

        String first = store.store("secret-one");
        String second = store.store("secret-two");

        assertThat(first).isNotEqualTo(second);
        assertThat(store.resolve(first)).isEqualTo("secret-one");
        assertThat(store.resolve(second)).isEqualTo("secret-two");
    }

    @Test
    void resolveSurvivesANewStoreInstanceOnTheSameDir() {
        String ref = store().store("app-password");

        // A fresh instance simulates an API restart: the file-backed value still resolves.
        assertThat(store().resolve(ref)).isEqualTo("app-password");
    }

    @Test
    void deleteRemovesTheCredential() {
        LocalCredentialStore store = store();
        String ref = store.store("app-password");

        store.delete(ref);

        assertThatThrownBy(() -> store.resolve(ref)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteIsIdempotentForUnknownRef() {
        LocalCredentialStore store = store();

        // Should not throw even though nothing was stored.
        store.delete("local/accounts/never-stored");
    }

    @Test
    void resolveUnknownRefThrows() {
        assertThatThrownBy(() -> store().resolve("local/accounts/missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No local credential");
    }

    @Test
    void resolveMissingEnvVarRefThrows() {
        assertThatThrownBy(() -> store().resolve("env:SOCIAL_API_DEFINITELY_MISSING_VAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("env var not set");
    }
}
