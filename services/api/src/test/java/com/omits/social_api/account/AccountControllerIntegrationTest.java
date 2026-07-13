package com.omits.social_api.account;

import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.dto.AccountResponse;
import com.omits.social_api.account.dto.ConnectAccountCommand;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("social-api.security.api-key", () -> API_KEY);
    }

    @MockitoBean
    private CredentialStore credentialStore;

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", API_KEY)
                .build();
    }

    private static HttpStatus statusOf(Throwable e) {
        return HttpStatus.valueOf(((HttpClientErrorException) e).getStatusCode().value());
    }

    @Test
    void connectsBlueskyAccount() {
        when(credentialStore.store("app-password")).thenReturn("social-api/accounts/ref-1");

        AccountResponse response = client().post()
                .uri("/accounts")
                .body(new ConnectAccountCommand(Platform.BLUESKY, "user.bsky.social", "app-password", null))
                .retrieve()
                .body(AccountResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.platform()).isEqualTo(Platform.BLUESKY);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.instance()).isNull();
    }

    @Test
    void connectsMastodonAccountAndEchoesInstance() {
        when(credentialStore.store("token")).thenReturn("social-api/accounts/ref-1");

        AccountResponse response = client().post()
                .uri("/accounts")
                .body(new ConnectAccountCommand(Platform.MASTODON, "user", "token", "mastodon.social"))
                .retrieve()
                .body(AccountResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.instance()).isEqualTo("mastodon.social");
    }

    @Test
    void rejectsSecondActiveRedditAccount() {
        when(credentialStore.store("token")).thenReturn("social-api/accounts/ref-1", "social-api/accounts/ref-2");

        client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.REDDIT, "redditor", "token", null))
                .retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.REDDIT, "redditor-2", "token", null))
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsMastodonAccountWithBlankInstance() {
        assertThatThrownBy(() -> client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.MASTODON, "user", "token", " "))
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsBlankHandle() {
        assertThatThrownBy(() -> client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.BLUESKY, " ", "app-password", null))
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void listsAccountsIncludingMastodonInstance() {
        when(credentialStore.store("token")).thenReturn("social-api/accounts/ref-1");

        client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.MASTODON, "list-user", "token", "mastodon.social"))
                .retrieve().toBodilessEntity();

        List<AccountResponse> accounts = client().get().uri("/accounts?platform=MASTODON")
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountResponse>>() {
                });

        assertThat(accounts).isNotNull();
        assertThat(accounts).anySatisfy(a -> {
            assertThat(a.handle()).isEqualTo("list-user");
            assertThat(a.instance()).isEqualTo("mastodon.social");
        });
    }

    @Test
    void getReturnsAccountAndUnknownIdReturns404() {
        when(credentialStore.store("app-password")).thenReturn("social-api/accounts/ref-1");

        AccountResponse created = client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.BLUESKY, "get-user.bsky.social", "app-password", null))
                .retrieve().body(AccountResponse.class);

        assertThat(created).isNotNull();
        AccountResponse fetched = client().get().uri("/accounts/" + created.id())
                .retrieve().body(AccountResponse.class);
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());

        assertThatThrownBy(() -> client().get().uri("/accounts/" + UUID.randomUUID())
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disconnectMarksAccountDisconnected() {
        when(credentialStore.store("app-password")).thenReturn("social-api/accounts/ref-1");

        AccountResponse created = client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.BLUESKY, "disconnect-user.bsky.social", "app-password", null))
                .retrieve().body(AccountResponse.class);

        assertThat(created).isNotNull();
        client().delete().uri("/accounts/" + created.id())
                .retrieve().toBodilessEntity();

        AccountResponse afterDisconnect = client().get().uri("/accounts/" + created.id())
                .retrieve().body(AccountResponse.class);
        assertThat(afterDisconnect).isNotNull();
        assertThat(afterDisconnect.status()).isEqualTo(AccountStatus.DISCONNECTED);
    }

    @Test
    void rejectsRequestWithoutApiKey() {
        RestClient anonymousClient = RestClient.create("http://localhost:" + port);

        assertThatThrownBy(() -> anonymousClient.get().uri("/accounts")
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
