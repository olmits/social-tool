package com.omits.social_api.generation;

import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.Draft;
import com.omits.social_api.draft.DraftCreationService;
import com.omits.social_api.generation.dto.GenerateDraftCommand;
import com.omits.social_api.signal.Signal;
import com.omits.social_api.signal.SignalService;
import com.omits.social_api.topic.TopicService;
import com.omits.social_api.voice.VoiceProfile;
import com.omits.social_api.voice.VoiceProfileService;
import com.omits.social_api.voice.exception.NoVoiceProfileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Application service turning a radar signal into a draft: the thing the whole radar exists to
 * feed.
 *
 * <p>Composes five slices — account, signal, topic, voice and draft — which is allowed here
 * and nowhere lower down; this is the cross-slice seam {@code DraftCreationService} and
 * {@code RadarService} established. All but one of those are reads, and the single write goes
 * through {@link DraftCreationService#createGenerated} rather than touching
 * {@code DraftService} directly, so "nothing but {@code DraftService} writes
 * {@code drafts.status}" stays true with one caller to check instead of two.
 *
 * <p><strong>Deliberately not {@code @Transactional}</strong>, unlike every other service in
 * this codebase. A generation round trip takes tens of seconds, and the connection pool holds
 * ten connections; wrapping this in a transaction would park one of them on a network call and
 * exhaust the pool under the mildest concurrency. The reads are independent of each other and
 * the single write is atomic on its own, so there is nothing a transaction here would buy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final DraftCreationService draftCreationService;
    private final SignalService signalService;
    private final TopicService topicService;
    private final VoiceProfileService voiceProfileService;
    private final PromptFactory promptFactory;
    private final DraftGenerator draftGenerator;

    /**
     * Writes a post about a signal and stores it as a {@code DRAFT} for review.
     *
     * <p>Nothing is persisted unless generation succeeds — the draft row is created from the
     * result, so a failed or refused call leaves no trace to clean up and the author simply
     * clicks again.
     */
    public Draft generate(GenerateDraftCommand command) {
        if (command.signalId() == null) {
            throw new IllegalArgumentException("signalId must not be null");
        }
        if (command.platform() == null) {
            throw new IllegalArgumentException("platform must not be null");
        }

        // Before anything expensive: a disconnected account should cost milliseconds, not a
        // paid round trip followed by a 409.
        draftCreationService.requireDraftable(command.accountId(), command.platform());

        Signal signal = signalService.get(command.signalId());
        String topicName = resolveTopicName(signal);
        String voice = resolveVoice(command.voiceProfileId()).getInstructions();

        GeneratedDraft generated = writeWithinLimit(signal, topicName, voice, command.platform());

        return draftCreationService.createGenerated(command.accountId(), command.signalId(),
                command.platform(), generated.content());
    }

    /**
     * One attempt, then at most one corrective attempt if it came back over the platform's
     * hard limit.
     *
     * <p>If the second is still over, the draft is kept anyway. Every draft passes through
     * manual review before it can be approved, the panel already shows an over-limit counter,
     * and trimming a post by machine destroys its ending — which on a 300-character post is
     * the part the post was written for. Throwing it away instead would discard a paid call
     * and a probably-good draft over something the author fixes in seconds.
     */
    private GeneratedDraft writeWithinLimit(Signal signal, String topicName, String voice,
                                            Platform platform) {
        GenerationRequest request = promptFactory.build(signal, topicName, voice, platform,
                articleFetchEnabled());
        GeneratedDraft generated = draftGenerator.generate(request);

        int length = PostLength.graphemes(generated.content());
        if (length <= platform.maxPostLength()) {
            return generated;
        }

        log.debug("Draft came back at {} characters, over {}'s limit of {}; retrying once",
                length, platform, platform.maxPostLength());
        GeneratedDraft shorter = draftGenerator.generate(
                promptFactory.shorten(request, generated.content(), length, platform));

        int retryLength = PostLength.graphemes(shorter.content());
        if (retryLength > platform.maxPostLength()) {
            log.warn("Draft still {} characters after one retry, over {}'s limit of {}; "
                            + "keeping it for review rather than truncating",
                    retryLength, platform, platform.maxPostLength());
        }
        return shorter;
    }

    /**
     * Null for a signal polled before topics existed, or one whose topic was since deleted.
     * The prompt omits the line entirely rather than sending the word "null".
     */
    private String resolveTopicName(Signal signal) {
        if (signal.getTopicId() == null) {
            return null;
        }
        return topicService.namesByIds(Set.of(signal.getTopicId())).get(signal.getTopicId());
    }

    /**
     * An explicitly named profile must exist and be switched on; otherwise the default is
     * used. With neither, this fails rather than inventing a neutral voice — see
     * {@link NoVoiceProfileException}.
     */
    private VoiceProfile resolveVoice(UUID voiceProfileId) {
        if (voiceProfileId != null) {
            return voiceProfileService.getEnabled(voiceProfileId);
        }
        return voiceProfileService.findDefault().orElseThrow(NoVoiceProfileException::new);
    }

    /**
     * Whether the model may read the article behind the signal. Asked of the generator rather
     * than read from configuration here, so the one class that talks to the model owns every
     * decision about what the request looks like.
     */
    private boolean articleFetchEnabled() {
        return draftGenerator.articleFetchEnabled();
    }
}
