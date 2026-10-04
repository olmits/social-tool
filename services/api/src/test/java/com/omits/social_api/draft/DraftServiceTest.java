package com.omits.social_api.draft;

import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.exception.DisclosureRequiredException;
import com.omits.social_api.draft.exception.DraftNotFoundException;
import com.omits.social_api.draft.exception.InvalidStateTransitionException;
import com.omits.social_api.draft.model.DraftStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftServiceTest {

    private final DraftRepository draftRepository = mock(DraftRepository.class);
    private final DraftService draftService = new DraftService(draftRepository);

    // --- create ---------------------------------------------------------------

    @Test
    void createsDraftInDraftStatus() {
        stubSaveEchoesArgument();
        UUID accountId = UUID.randomUUID();

        Draft draft = draftService.create(accountId, null, Platform.BLUESKY, "hello world", false);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(draft.getAccountId()).isEqualTo(accountId);
        assertThat(draft.getPlatform()).isEqualTo(Platform.BLUESKY);
        assertThat(draft.getContent()).isEqualTo("hello world");
        assertThat(draft.getSignalId()).isNull();
        assertThat(draft.isAiGenerated()).isFalse();
        assertThat(draft.isDisclosureIncluded()).isFalse();
    }

    @Test
    void createsAnAiGeneratedDraftLinkedToItsSignal() {
        stubSaveEchoesArgument();
        UUID accountId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();

        Draft draft = draftService.create(accountId, signalId, Platform.BLUESKY, "written", true);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(draft.getSignalId()).isEqualTo(signalId);
        assertThat(draft.isAiGenerated()).isTrue();
    }

    @Test
    void createRejectsNullAccountId() {
        assertThatThrownBy(() -> draftService.create(null, null, Platform.BLUESKY, "hello", false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void createRejectsNullPlatform() {
        assertThatThrownBy(() -> draftService.create(UUID.randomUUID(), null, null, "hello", false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void createRejectsBlankContent() {
        assertThatThrownBy(() -> draftService.create(UUID.randomUUID(), null, Platform.BLUESKY, " ", false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(draftRepository, never()).save(any());
    }

    // --- edit -------------------------------------------------------------------

    @Test
    void editsDraftBody() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.DRAFT);

        Draft draft = draftService.edit(draftId, "revised", "https://example.com/ref?tag=me", true);

        assertThat(draft.getContent()).isEqualTo("revised");
        assertThat(draft.getAffiliateLinks()).isEqualTo("https://example.com/ref?tag=me");
        assertThat(draft.isDisclosureIncluded()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
    }

    @Test
    void editClearsAffiliateLinksWhenNull() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        Draft existing = existingDraft(draftId, DraftStatus.DRAFT);
        existing.setAffiliateLinks("https://example.com/ref?tag=me");

        Draft draft = draftService.edit(draftId, "revised", null, false);

        assertThat(draft.getAffiliateLinks()).isNull();
    }

    @Test
    void editRevertsApprovedDraftToDraftForReReview() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.APPROVED);

        Draft draft = draftService.edit(draftId, "revised after approval", null, false);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(draft.getContent()).isEqualTo("revised after approval");
    }

    @Test
    void editRejectsBlankContent() {
        UUID draftId = UUID.randomUUID();

        assertThatThrownBy(() -> draftService.edit(draftId, " ", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(draftRepository, never()).findById(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void editRejectsDraftThatHasLeftReview() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.SCHEDULED);

        assertThatThrownBy(() -> draftService.edit(draftId, "too late", null, false))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void editThrowsWhenDraftNotFound() {
        UUID draftId = UUID.randomUUID();
        when(draftRepository.findById(draftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> draftService.edit(draftId, "revised", null, false))
                .isInstanceOf(DraftNotFoundException.class);
    }

    // --- discard ----------------------------------------------------------------

    @Test
    void discardsDraft() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.DRAFT);

        Draft draft = draftService.discard(draftId);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DISCARDED);
    }

    @Test
    void discardsApprovedDraft() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.APPROVED);

        Draft draft = draftService.discard(draftId);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DISCARDED);
    }

    @Test
    void discardRejectsDraftThatIsQueuedToPublish() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.SCHEDULED);

        assertThatThrownBy(() -> draftService.discard(draftId))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void discardRejectsPublishedDraft() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.PUBLISHED);

        assertThatThrownBy(() -> draftService.discard(draftId))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    // --- approve ---------------------------------------------------------------

    @Test
    void approvesDraftWithoutAffiliateLinks() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.DRAFT);

        Draft draft = draftService.approve(draftId);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.APPROVED);
    }

    @Test
    void approvesDraftWithAffiliateLinksWhenDisclosureIncluded() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        Draft draft = existingDraft(draftId, DraftStatus.DRAFT);
        draft.setAffiliateLinks("https://example.com/ref?tag=me");
        draft.setDisclosureIncluded(true);

        Draft result = draftService.approve(draftId);

        assertThat(result.getStatus()).isEqualTo(DraftStatus.APPROVED);
    }

    @Test
    void approveRejectsAffiliateLinksWithoutDisclosure() {
        UUID draftId = UUID.randomUUID();
        Draft draft = existingDraft(draftId, DraftStatus.DRAFT);
        draft.setAffiliateLinks("https://example.com/ref?tag=me");

        assertThatThrownBy(() -> draftService.approve(draftId))
                .isInstanceOf(DisclosureRequiredException.class);
        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void approveRejectsTransitionFromNonDraftStatus() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.APPROVED);

        assertThatThrownBy(() -> draftService.approve(draftId))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void approveThrowsWhenDraftNotFound() {
        UUID draftId = UUID.randomUUID();
        when(draftRepository.findById(draftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> draftService.approve(draftId))
                .isInstanceOf(DraftNotFoundException.class);
    }

    // --- schedule --------------------------------------------------------------

    @Test
    void schedulesApprovedDraft() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.APPROVED);
        Instant scheduledAt = Instant.parse("2026-08-01T12:00:00Z");

        Draft draft = draftService.schedule(draftId, scheduledAt);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.SCHEDULED);
        assertThat(draft.getScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    void scheduleRejectsNullScheduledAt() {
        UUID draftId = UUID.randomUUID();

        assertThatThrownBy(() -> draftService.schedule(draftId, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(draftRepository, never()).findById(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void scheduleRejectsTransitionFromNonApprovedStatus() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.DRAFT);

        assertThatThrownBy(() -> draftService.schedule(draftId, Instant.parse("2026-08-01T12:00:00Z")))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    // --- markPublished ---------------------------------------------------------

    @Test
    void marksScheduledDraftPublished() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.SCHEDULED);

        Draft draft = draftService.markPublished(draftId, "at://did:plc:xyz/app.bsky.feed.post/123");

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.PUBLISHED);
        assertThat(draft.getRemoteId()).isEqualTo("at://did:plc:xyz/app.bsky.feed.post/123");
    }

    @Test
    void markPublishedRejectsBlankRemoteId() {
        UUID draftId = UUID.randomUUID();

        assertThatThrownBy(() -> draftService.markPublished(draftId, " "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(draftRepository, never()).findById(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void markPublishedRejectsTransitionFromNonScheduledStatus() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.APPROVED);

        assertThatThrownBy(() -> draftService.markPublished(draftId, "remote-1"))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    // --- markFailed ------------------------------------------------------------

    @Test
    void marksScheduledDraftFailed() {
        stubSaveEchoesArgument();
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.SCHEDULED);

        Draft draft = draftService.markFailed(draftId, "rate limited by platform");

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.FAILED);
        assertThat(draft.getFailureReason()).isEqualTo("rate limited by platform");
    }

    @Test
    void markFailedRejectsTransitionFromNonScheduledStatus() {
        UUID draftId = UUID.randomUUID();
        existingDraft(draftId, DraftStatus.PUBLISHED);

        assertThatThrownBy(() -> draftService.markFailed(draftId, "boom"))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(draftRepository, never()).save(any());
    }

    // --- get -------------------------------------------------------------------

    @Test
    void getThrowsWhenDraftNotFound() {
        UUID draftId = UUID.randomUUID();
        when(draftRepository.findById(draftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> draftService.get(draftId))
                .isInstanceOf(DraftNotFoundException.class);
    }

    // --- helpers ---------------------------------------------------------------

    private void stubSaveEchoesArgument() {
        when(draftRepository.save(any(Draft.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Draft existingDraft(UUID draftId, DraftStatus status) {
        Draft draft = new Draft(UUID.randomUUID(), null, Platform.BLUESKY, "hello world", false);
        draft.setStatus(status);
        when(draftRepository.findById(draftId)).thenReturn(Optional.of(draft));
        return draft;
    }
}
