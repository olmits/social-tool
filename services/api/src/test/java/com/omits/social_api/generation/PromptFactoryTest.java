package com.omits.social_api.generation;

import com.omits.social_api.account.model.Platform;
import com.omits.social_api.signal.Signal;
import com.omits.social_api.signal.model.SignalSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No mocks at all — which is the point of keeping prompt building pure. The prompt is the part
 * of this feature most likely to be edited, so its tests have to be cheap to run and obvious
 * to read.
 */
class PromptFactoryTest {

    private static final String VOICE = "Dry, concrete, first person. No emoji.";

    private final PromptFactory factory = new PromptFactory();

    // --- build ----------------------------------------------------------------

    @Test
    void includesTheSignalsTitleSourceAndUrl() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        assertThat(request.userMessage())
                .contains("Shipping Go services")
                .contains("HACKER_NEWS")
                .contains("https://example.test/go");
    }

    @Test
    void putsTheVoiceGuidanceInTheSystemPrompt() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        assertThat(request.systemPrompt()).contains(VOICE);
        assertThat(request.userMessage()).doesNotContain(VOICE);
    }

    /** Untrusted-data framing is the cheap half of the prompt-injection mitigation. */
    @Test
    void tellsTheModelToTreatFetchedPagesAsData() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        assertThat(request.systemPrompt()).contains("untrusted data");
    }

    @Test
    void includesTheTopicWhenTheSignalHasOne() {
        GenerationRequest request = factory.build(signal(UUID.randomUUID()), "Runtimes", VOICE,
                Platform.BLUESKY, true);

        assertThat(request.userMessage()).contains("Runtimes");
    }

    /** A signal polled before topics existed must not produce the literal word "null". */
    @Test
    void omitsTheTopicLineEntirelyWhenThereIsNone() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        assertThat(request.userMessage())
                .doesNotContain("Topic:")
                .doesNotContain("null");
    }

    /**
     * The headroom is what keeps the corrective retry rare: a model told the hard limit lands
     * just over it often enough to matter.
     */
    @Test
    void asksForFewerCharactersThanThePlatformActuallyAllows() {
        int budget = PromptFactory.budgetFor(Platform.BLUESKY);

        assertThat(budget).isLessThan(Platform.BLUESKY.maxPostLength());
        assertThat(factory.build(signal(null), null, VOICE, Platform.BLUESKY, true).userMessage())
                .contains(String.valueOf(budget))
                .contains(String.valueOf(Platform.BLUESKY.maxPostLength()));
    }

    @Test
    void budgetsPerPlatform() {
        assertThat(PromptFactory.budgetFor(Platform.BLUESKY))
                .isLessThan(PromptFactory.budgetFor(Platform.MASTODON));
    }

    /** A popularity integer says nothing about what to write, and invites "trending at 87/100". */
    @Test
    void doesNotSendTheScore() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        assertThat(request.userMessage()).doesNotContain("90").doesNotContain("312");
    }

    // --- article fetch --------------------------------------------------------

    @Test
    void carriesTheArticleUrlWhenFetchingIsOn() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        assertThat(request.articleUrl()).isEqualTo("https://example.test/go");
        assertThat(request.userMessage()).contains("Read the article");
    }

    /**
     * With fetching off the URL is still shown — it is part of what the signal is — but the
     * prompt must not ask for something the request carries no tool to do.
     */
    @Test
    void doesNotAskTheModelToReadTheArticleWhenFetchingIsOff() {
        GenerationRequest request = factory.build(signal(null), null, VOICE, Platform.BLUESKY, false);

        assertThat(request.articleUrl()).isNull();
        assertThat(request.userMessage())
                .doesNotContain("Read the article")
                .contains("https://example.test/go");
    }

    // --- shorten --------------------------------------------------------------

    @Test
    void shortenEchoesTheOverlongAttemptAndItsLength() {
        GenerationRequest original = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        GenerationRequest retry = factory.shorten(original, "far too long", 412, Platform.BLUESKY);

        assertThat(retry.userMessage())
                .contains("far too long")
                .contains("412")
                .contains(String.valueOf(PromptFactory.budgetFor(Platform.BLUESKY)));
    }

    @Test
    void shortenKeepsTheSystemPromptAndFetchSettingOfTheOriginal() {
        GenerationRequest original = factory.build(signal(null), null, VOICE, Platform.BLUESKY, true);

        GenerationRequest retry = factory.shorten(original, "far too long", 412, Platform.BLUESKY);

        assertThat(retry.systemPrompt()).isEqualTo(original.systemPrompt());
        assertThat(retry.articleUrl()).isEqualTo(original.articleUrl());
    }

    // --- helpers --------------------------------------------------------------

    private static Signal signal(UUID topicId) {
        return new Signal(SignalSource.HACKER_NEWS, "42", "Shipping Go services", topicId,
                "https://example.test/go", 90, 312, "{}", Instant.now());
    }
}
