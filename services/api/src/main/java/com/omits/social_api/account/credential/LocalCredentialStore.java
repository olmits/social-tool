package com.omits.social_api.account.credential;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Local-profile stand-in for {@link SecretsManagerCredentialStore}. Persists each credential as a
 * file in a gitignored directory so that {@code store → resolve → delete} works end-to-end without
 * AWS and survives API restarts (a DB {@code credential_ref} keeps resolving).
 *
 * <p>Two kinds of reference are understood by {@link #resolve}:
 * <ul>
 *   <li>refs minted by {@link #store} (backed by a file on disk), and
 *   <li>{@code env:NAME} refs, resolved from the {@code NAME} environment variable — a convenience
 *       for pre-seeding a credential (e.g. a throwaway Bluesky app password) without the connect
 *       flow, and the "fall back to environment variables" behaviour described in CLAUDE.md.
 * </ul>
 *
 * <p>Stores plaintext secrets on disk: acceptable for local dev with throwaway test-account
 * credentials only. The directory is gitignored. Never active outside the {@code local} profile.
 */
@Component
@Profile("local")
public class LocalCredentialStore implements CredentialStore {

    private static final String REF_PREFIX = "local/accounts/";
    private static final String ENV_REF_PREFIX = "env:";
    private static final String FILE_SUFFIX = ".secret";

    private final Path credentialsDir;

    public LocalCredentialStore(
            @Value("${social-api.local.credentials-dir:${user.dir}/.local-secrets}") String credentialsDir) {
        this.credentialsDir = Path.of(credentialsDir);
    }

    @Override
    public String store(String credentialValue) {
        String ref = REF_PREFIX + UUID.randomUUID();
        try {
            Files.createDirectories(credentialsDir);
            Files.writeString(fileFor(ref), credentialValue);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store local credential", e);
        }
        return ref;
    }

    @Override
    public String resolve(String secretRef) {
        if (secretRef.startsWith(ENV_REF_PREFIX)) {
            String envVar = secretRef.substring(ENV_REF_PREFIX.length());
            String value = System.getenv(envVar);
            if (value == null) {
                throw new IllegalStateException("Local credential env var not set: " + envVar);
            }
            return value;
        }
        Path file = fileFor(secretRef);
        if (!Files.exists(file)) {
            throw new IllegalStateException("No local credential stored for ref '" + secretRef
                    + "' (stored under a different credentials-dir or profile, or removed?)");
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read local credential " + secretRef, e);
        }
    }

    @Override
    public void delete(String secretRef) {
        if (secretRef.startsWith(ENV_REF_PREFIX)) {
            return; // env-backed refs are not ours to delete
        }
        try {
            Files.deleteIfExists(fileFor(secretRef));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete local credential " + secretRef, e);
        }
    }

    private Path fileFor(String ref) {
        return credentialsDir.resolve(ref.replaceAll("[^a-zA-Z0-9.-]", "_") + FILE_SUFFIX);
    }
}
