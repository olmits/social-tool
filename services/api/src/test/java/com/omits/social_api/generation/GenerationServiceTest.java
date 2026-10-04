package com.omits.social_api.generation;

import com.omits.social_api.account.exception.AccountNotActiveException;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.Draft;
import com.omits.social_api.draft.DraftCreationService;
import com.omits.social_api.generation.dto.GenerateDraftCommand;
import com.omits.social_api.generation.exception.DraftGenerationException;
import com.omits.social_api.signal.Signal;
import com.omits.social_api.signal.SignalService;
import com.omits.social_api.signal.exception.SignalNotFoundException;
import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.TopicService;
import com.omits.social_api.voice.VoiceProfile;
import com.omits.social_api.voice.VoiceProfileService;
import com.omits.social_api.voice.exception.NoVoiceProfileException;
import com.omits.social_api.voice.exception.VoiceProfileNotEnabledException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID SIGNAL_ID = UUID.randomUUID();
    private static final String VOICE = "Dry, concrete, first person.";

    private final DraftCreationService draftCreationService = mock(DraftCreationService.class);
    private final SignalService signalService = mock(SignalService.class);
    private final TopicService topicService = mock(TopicService.class);
    private final VoiceProfileService voiceProfileService = mock(VoiceProfileService.class);
    private final DraftGenerator draftGenerator = mock(DraftGenerator.class);

    private final GenerationService generationService = new GenerationService(
            draftCreationService, signalService, topicService, voiceProfileService,
            new PromptFactory(), draftGenerator);

    // --- generate -------------------------------------------------------------

    @Test
    void generatesADraftFromASignal() {
        stubSignal(null);
        stubDefaultVoice();
        stubGenerated("a short post");
        when(draftCreationService.createGenerated(any(), any(), any(), anyString()))
                .thenReturn(draft("a short post"));

        generationService.generate(command(null));

        verify(draftCreationService).createGenerated(
                ACCOUNT_ID, SIGNAL_ID, Platform.BLUESKY, "a short post");
    }

    @Test
    void passesTheSignalsTopicNameIntoThePrompt() {
        UUID topicId = UUID.randomUUID();
        stubSignal(topicId);
        when(topicService.namesByIds(Set.of(topicId))).thenReturn(Map.of(topicId, "Runtimes"));
        stubDefaultVoice();
        stubGenerated("post");

        generationService.generate(command(null));

        assertThat(captureRequest().userMessage()).contains("Runtimes");
    }

    @Test
    void doesNotLookUpATopicForASignalThatHasNone() {
        stubSignal(null);
        stubDefaultVoice();
        stubGenerated("post");

        generationService.generate(command(null));

        verifyNoInteractions(topicService);
    }

    // --- the account gate runs first -------------------------------------------

    /**
     * The whole reason {@code requireDraftable} exists: a disconnected account must cost
     * milliseconds, not a paid round trip followed by a 409.
     */
    @Test
    void validatesTheAccountBeforeSpendingAnythingOnGeneration() {
        doThrow(new AccountNotActiveException(ACCOUNT_ID, AccountStatus.DISCONNECTED))
                .when(draftCreationService).requireDraftable(ACCOUNT_ID, Platform.BLUESKY);

        assertThatThrownBy(() -> generationService.generate(command(null)))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoInteractions(draftGenerator);
        verifyNoInteractions(signalService);
    }

    @Test
    void rejectsAnUnknownSignalBeforeGenerating() {
        when(signalService.get(SIGNAL_ID)).thenThrow(new SignalNotFoundException(SIGNAL_ID));

        assertThatThrownBy(() -> generationService.generate(command(null)))
                .isInstanceOf(SignalNotFoundException.class);
        verifyNoInteractions(draftGenerator);
    }

    @Test
    void rejectsANullSignalId() {
        assertThatThrownBy(() -> generationService.generate(
                new GenerateDraftCommand(ACCOUNT_ID, null, Platform.BLUESKY, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(draftGenerator);
    }

    // --- voice resolution -------------------------------------------------------

    @Test
    void usesTheDefaultVoiceWhenNoneIsNamed() {
        stubSignal(null);
        stubDefaultVoice();
        stubGenerated("post");

        generationService.generate(command(null));

        assertThat(captureRequest().systemPrompt()).contains(VOICE);
    }

    @Test
    void usesTheNamedVoiceWhenOneIsGiven() {
        UUID voiceId = UUID.randomUUID();
        stubSignal(null);
        when(voiceProfileService.getEnabled(voiceId))
                .thenReturn(new VoiceProfile("Punchy", "Short sentences.", true, false));
        stubGenerated("post");

        generationService.generate(command(voiceId));

        assertThat(captureRequest().systemPrompt()).contains("Short sentences.");
        verify(voiceProfileService, never()).findDefault();
    }

    @Test
    void rejectsADisabledVoiceProfile() {
        UUID voiceId = UUID.randomUUID();
        stubSignal(null);
        when(voiceProfileService.getEnabled(voiceId))
                .thenThrow(new VoiceProfileNotEnabledException(voiceId));

        assertThatThrownBy(() -> generationService.generate(command(voiceId)))
                .isInstanceOf(VoiceProfileNotEnabledException.class);
        verifyNoInteractions(draftGenerator);
    }

    /**
     * No built-in neutral fallback, on purpose: a voice nobody chose produces drafts that
     * sound like nobody, and it would make the voice screen quietly skippable.
     */
    @Test
    void failsRatherThanInventingAVoiceWhenThereIsNoDefault() {
        stubSignal(null);
        when(voiceProfileService.findDefault()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generationService.generate(command(null)))
                .isInstanceOf(NoVoiceProfileException.class);
        verifyNoInteractions(draftGenerator);
    }

    // --- character limits -------------------------------------------------------

    @Test
    void acceptsAPostWithinTheLimitWithoutRetrying() {
        stubSignal(null);
        stubDefaultVoice();
        stubGenerated("short enough");

        generationService.generate(command(null));

        verify(draftGenerator, times(1)).generate(any());
    }

    @Test
    void retriesOnceWhenThePostIsOverTheHardLimit() {
        stubSignal(null);
        stubDefaultVoice();
        when(draftGenerator.generate(any()))
                .thenReturn(new GeneratedDraft("x".repeat(400), "first"))
                .thenReturn(new GeneratedDraft("now short", "second"));
        when(draftCreationService.createGenerated(any(), any(), any(), anyString()))
                .thenReturn(draft("now short"));

        generationService.generate(command(null));

        verify(draftGenerator, times(2)).generate(any());
        verify(draftCreationService).createGenerated(ACCOUNT_ID, SIGNAL_ID, Platform.BLUESKY, "now short");
    }

    /**
     * Kept rather than truncated or thrown away: review is mandatory anyway, the panel shows
     * an over-limit counter, and cutting a 300-character post by machine destroys its ending.
     */
    @Test
    void keepsAnOverlongDraftWhenTheRetryAlsoOvershoots() {
        String stillTooLong = "x".repeat(350);
        stubSignal(null);
        stubDefaultVoice();
        when(draftGenerator.generate(any()))
                .thenReturn(new GeneratedDraft("x".repeat(400), "first"))
                .thenReturn(new GeneratedDraft(stillTooLong, "second"));
        when(draftCreationService.createGenerated(any(), any(), any(), anyString()))
                .thenReturn(draft(stillTooLong));

        generationService.generate(command(null));

        verify(draftGenerator, times(2)).generate(any());
        verify(draftCreationService).createGenerated(
                ACCOUNT_ID, SIGNAL_ID, Platform.BLUESKY, stillTooLong);
    }

    /** Mastodon allows 500, so the same post that overshoots on Bluesky must not retry here. */
    @Test
    void appliesThePlatformsOwnLimit() {
        stubSignal(null);
        stubDefaultVoice();
        stubGenerated("x".repeat(400));
        when(draftCreationService.createGenerated(any(), any(), any(), anyString()))
                .thenReturn(draft("x".repeat(400)));

        generationService.generate(new GenerateDraftCommand(
                ACCOUNT_ID, SIGNAL_ID, Platform.MASTODON, null));

        verify(draftGenerator, times(1)).generate(any());
    }

    // --- failures leave nothing behind ------------------------------------------

    @Test
    void persistsNothingWhenGenerationFails() {
        stubSignal(null);
        stubDefaultVoice();
        when(draftGenerator.generate(any()))
                .thenThrow(new DraftGenerationException("Claude draft generation failed (500)"));

        assertThatThrownBy(() -> generationService.generate(command(null)))
                .isInstanceOf(DraftGenerationException.class);
        verify(draftCreationService, never()).createGenerated(any(), any(), any(), anyString());
    }

    // --- helpers ----------------------------------------------------------------

    private static GenerateDraftCommand command(UUID voiceProfileId) {
        return new GenerateDraftCommand(ACCOUNT_ID, SIGNAL_ID, Platform.BLUESKY, voiceProfileId);
    }

    private void stubSignal(UUID topicId) {
        when(signalService.get(SIGNAL_ID)).thenReturn(new Signal(SignalSource.HACKER_NEWS, "42",
                "Shipping Go services", topicId, "https://example.test/go", 90, 312, "{}",
                Instant.now()));
    }

    private void stubDefaultVoice() {
        when(voiceProfileService.findDefault())
                .thenReturn(Optional.of(new VoiceProfile("Technical", VOICE, true, true)));
    }

    private void stubGenerated(String content) {
        when(draftGenerator.generate(any())).thenReturn(new GeneratedDraft(content, "an angle"));
    }

    private static Draft draft(String content) {
        return new Draft(ACCOUNT_ID, SIGNAL_ID, Platform.BLUESKY, content, true);
    }

    private GenerationRequest captureRequest() {
        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(draftGenerator, org.mockito.Mockito.atLeastOnce()).generate(captor.capture());
        return captor.getValue();
    }
}
