package com.omits.social_api.generation.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.omits.social_api.config.ClaudeProperties;
import com.omits.social_api.generation.GeneratedDraft;
import com.omits.social_api.generation.GenerationRequest;
import com.omits.social_api.generation.exception.DraftGenerationException;
import com.omits.social_api.generation.exception.DraftRefusedException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The only test that exercises the real SDK call path, against a {@link MockWebServer} — the
 * same harness {@code BlueskyAdapterTest} uses for Bluesky.
 *
 * <p>It is deliberately narrow. Everything about <em>what</em> gets drafted is covered by
 * {@code GenerationServiceTest} and {@code PromptFactoryTest} without a network at all;
 * what is only verifiable here is that a structured response actually deserializes into
 * {@code GeneratedPost}, and that the three failure shapes map to the right exceptions.
 *
 * <p>No test in this class reaches the real API.
 */
class ClaudeDraftGeneratorTest {

    private static final GenerationRequest REQUEST =
            new GenerationRequest("be terse", "write about Go", null);

    private MockWebServer server;
    private ClaudeDraftGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        generator = generatorWith("test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // --- success ---------------------------------------------------------------

    @Test
    void parsesTheStructuredPostOutOfTheResponse() throws InterruptedException {
        server.enqueue(jsonResponse(messageBody("end_turn",
                "{\\\"content\\\":\\\"a short post\\\",\\\"rationale\\\":\\\"the angle\\\"}")));

        GeneratedDraft draft = generator.generate(REQUEST);

        assertThat(draft.content()).isEqualTo("a short post");
        assertThat(draft.rationale()).isEqualTo("the angle");

        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getPath()).isEqualTo("/v1/messages");
        assertThat(sent.getBody().readUtf8())
                .contains("write about Go")
                .contains("be terse");
    }

    /** Without the tool attached, nothing in the request should ask for a fetch. */
    @Test
    void doesNotAttachTheWebFetchToolWhenThereIsNoArticleUrl() throws InterruptedException {
        server.enqueue(jsonResponse(messageBody("end_turn",
                "{\\\"content\\\":\\\"post\\\",\\\"rationale\\\":\\\"angle\\\"}")));

        generator.generate(REQUEST);

        assertThat(server.takeRequest().getBody().readUtf8()).doesNotContain("web_fetch");
    }

    @Test
    void attachesTheWebFetchToolWhenAnArticleUrlIsGiven() throws InterruptedException {
        server.enqueue(jsonResponse(messageBody("end_turn",
                "{\\\"content\\\":\\\"post\\\",\\\"rationale\\\":\\\"angle\\\"}")));

        generator.generate(new GenerationRequest("be terse", "write about Go",
                "https://example.test/go"));

        assertThat(server.takeRequest().getBody().readUtf8()).contains("web_fetch");
    }

    // --- failures ---------------------------------------------------------------

    @Test
    void translatesAnApiErrorIntoADraftGenerationException() {
        server.enqueue(new MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}"));

        assertThatThrownBy(() -> generator.generate(REQUEST))
                .isInstanceOf(DraftGenerationException.class)
                .hasMessageContaining("500");
    }

    /**
     * A refusal arrives as a perfectly successful 200 with no usable content, which is why
     * {@code stop_reason} has to be read before the content rather than after.
     */
    @Test
    void translatesARefusalIntoItsOwnException() {
        server.enqueue(jsonResponse("""
                {
                  "id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5",
                  "content": [],
                  "stop_reason": "refusal",
                  "stop_details": {"type": "refusal", "category": "cyber", "explanation": "no"},
                  "usage": {"input_tokens": 10, "output_tokens": 0}
                }"""));

        assertThatThrownBy(() -> generator.generate(REQUEST))
                .isInstanceOf(DraftRefusedException.class)
                .hasMessageContaining("cyber");
    }

    @Test
    void rejectsAResponseTruncatedAtTheTokenCeiling() {
        server.enqueue(jsonResponse(messageBody("max_tokens",
                "{\\\"content\\\":\\\"half a po\\\",\\\"rationale\\\":\\\"\\\"}")));

        assertThatThrownBy(() -> generator.generate(REQUEST))
                .isInstanceOf(DraftGenerationException.class)
                .hasMessageContaining("output tokens");
    }

    @Test
    void rejectsAnEmptyPost() {
        server.enqueue(jsonResponse(messageBody("end_turn",
                "{\\\"content\\\":\\\"   \\\",\\\"rationale\\\":\\\"angle\\\"}")));

        assertThatThrownBy(() -> generator.generate(REQUEST))
                .isInstanceOf(DraftGenerationException.class)
                .hasMessageContaining("empty");
    }

    /** The context starts without a key so the rest of the service still runs; this is where
     *  that choice has to surface as a clear failure rather than a confusing 401. */
    @Test
    void failsWithAClearMessageWhenNoApiKeyIsConfigured() {
        assertThatThrownBy(() -> generatorWith("  ").generate(REQUEST))
                .isInstanceOf(DraftGenerationException.class)
                .hasMessageContaining("CLAUDE_API_KEY");
        assertThat(server.getRequestCount()).isZero();
    }

    // --- helpers -----------------------------------------------------------------

    /** Retries are off here so one enqueued error response is enough to assert against. */
    private ClaudeDraftGenerator generatorWith(String apiKey) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .timeout(Duration.ofSeconds(5))
                .maxRetries(0)
                .build();
        return new ClaudeDraftGenerator(client,
                new ClaudeProperties(apiKey, "claude-opus-5", false, 90));
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** {@code structuredJson} is the model's JSON payload, escaped to sit inside a text block. */
    private static String messageBody(String stopReason, String structuredJson) {
        return """
                {
                  "id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5",
                  "content": [{"type": "text", "text": "%s"}],
                  "stop_reason": "%s",
                  "usage": {"input_tokens": 10, "output_tokens": 20}
                }""".formatted(structuredJson, stopReason);
    }
}
