package com.omits.social_api.generation.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.WebFetchTool20260209;
import com.omits.social_api.config.ClaudeProperties;
import com.omits.social_api.generation.DraftGenerator;
import com.omits.social_api.generation.GeneratedDraft;
import com.omits.social_api.generation.GenerationRequest;
import com.omits.social_api.generation.exception.DraftGenerationException;
import com.omits.social_api.generation.exception.DraftRefusedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The only class in the service that knows the Anthropic SDK exists.
 *
 * <p>Everything above it speaks {@link GenerationRequest} and {@link GeneratedDraft}, which is
 * what lets the orchestration be unit-tested and the controller be integration-tested without
 * a paid API call — the same arrangement {@code PlatformAdapter} gives the publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeDraftGenerator implements DraftGenerator {

    /**
     * Non-streaming, so this stays under the HTTP timeout. Generous because thinking tokens
     * count against it and a cramped ceiling truncates mid-post, which costs a whole retry to
     * discover.
     */
    private static final long MAX_TOKENS = 16_000L;

    /**
     * One fetch, not several. The model has exactly one URL worth reading, and an unbounded
     * allowance is how a draft request turns into a crawl.
     */
    private static final long MAX_FETCHES = 1L;

    private final AnthropicClient anthropicClient;
    private final ClaudeProperties claudeProperties;

    @Override
    public boolean articleFetchEnabled() {
        return claudeProperties.fetchArticle();
    }

    @Override
    public GeneratedDraft generate(GenerationRequest request) {
        requireApiKey();

        StructuredMessage<GeneratedPost> message;
        try {
            message = anthropicClient.messages().create(paramsFor(request));
        } catch (AnthropicServiceException e) {
            throw new DraftGenerationException(
                    "Claude draft generation failed (%d): %s".formatted(e.statusCode(), e.getMessage()), e);
        } catch (RuntimeException e) {
            // Connection failures and timeouts, which the SDK does not model as service errors.
            throw new DraftGenerationException(
                    "Claude draft generation failed: " + e.getMessage(), e);
        }

        requireUsableStopReason(message);
        warnIfArticleFetchFailed(message);

        GeneratedPost post = firstPost(message);
        if (post.content() == null || post.content().isBlank()) {
            throw new DraftGenerationException("Claude returned an empty post");
        }
        log.debug("Claude draft rationale: {}", post.rationale());
        return new GeneratedDraft(post.content().strip(), post.rationale());
    }

    private MessageCreateParams.Builder baseParams(GenerationRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(claudeProperties.model())
                .maxTokens(MAX_TOKENS)
                .system(request.systemPrompt())
                .addUserMessage(request.userMessage());

        // The tool only ever fetches URLs already present in the conversation, so attaching it
        // without the signal's URL in the message would do nothing.
        if (request.articleUrl() != null) {
            builder.addTool(WebFetchTool20260209.builder().maxUses(MAX_FETCHES).build());
        }
        return builder;
    }

    /**
     * Thinking is intentionally not configured: it is on by default on this model family, and
     * passing a budget is rejected outright. Effort is dropped to medium because writing one
     * short post is not a hard reasoning problem and the default is high.
     */
    private StructuredMessageCreateParams<GeneratedPost> paramsFor(GenerationRequest request) {
        StructuredOutputConfig<GeneratedPost> output = StructuredOutputConfig.<GeneratedPost>builder()
                .format(GeneratedPost.class)
                .effort(OutputConfig.Effort.MEDIUM)
                .build();
        return baseParams(request).outputConfig(output).build();
    }

    private void requireApiKey() {
        String apiKey = claudeProperties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new DraftGenerationException(
                    "Claude API key is not configured; set CLAUDE_API_KEY to enable AI drafting");
        }
    }

    /**
     * A refusal is a successful HTTP 200 with nothing usable in it, so {@code stop_reason} has
     * to be read before the content rather than after. {@code stopDetails()} is populated only
     * for a refusal, which is why it is not consulted anywhere else.
     */
    private void requireUsableStopReason(StructuredMessage<GeneratedPost> message) {
        StopReason stopReason = message.stopReason().orElse(null);
        if (stopReason == null) {
            return;
        }
        if (stopReason.equals(StopReason.REFUSAL)) {
            String category = message.stopDetails()
                    .flatMap(details -> details.category().map(Object::toString))
                    .orElse(null);
            throw new DraftRefusedException(category);
        }
        if (stopReason.equals(StopReason.PAUSE_TURN)) {
            // Server-side tool use ran long enough for the API to hand the turn back. With a
            // single permitted fetch this is close to unreachable; resuming it properly means
            // replaying the assistant turn, which is untestable here without a recorded
            // fixture. Failing loudly beats returning a half-written post.
            throw new DraftGenerationException(
                    "Claude paused mid-request while reading the article; try again");
        }
        if (stopReason.equals(StopReason.MAX_TOKENS)) {
            throw new DraftGenerationException("Claude ran out of output tokens mid-post");
        }
    }

    /**
     * A failed fetch is <em>not</em> an exception: the API answers 200 with an error object
     * inside the result block. A paywalled or script-rendered page is common enough that
     * failing the request over it would be wrong — the model still has the headline, the
     * source and the topic, and the author still reviews whatever comes out. Worth a log line
     * so a run of bland drafts has a visible cause.
     */
    private void warnIfArticleFetchFailed(StructuredMessage<GeneratedPost> message) {
        message.content().stream()
                .flatMap(block -> block.webFetchToolResult().stream())
                .forEach(result -> {
                    String raw = result._content().toString();
                    if (raw.contains("error_code")) {
                        log.warn("Claude could not read the article; drafted from the headline: {}", raw);
                    }
                });
    }

    private static GeneratedPost firstPost(StructuredMessage<GeneratedPost> message) {
        Optional<GeneratedPost> post = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst();
        return post.orElseThrow(() -> new DraftGenerationException(
                "Claude returned no post in its response"));
    }
}
