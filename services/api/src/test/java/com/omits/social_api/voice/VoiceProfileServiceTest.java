package com.omits.social_api.voice;

import com.omits.social_api.voice.dto.CreateVoiceProfileCommand;
import com.omits.social_api.voice.dto.UpdateVoiceProfileCommand;
import com.omits.social_api.voice.exception.DuplicateVoiceProfileException;
import com.omits.social_api.voice.exception.VoiceProfileNotEnabledException;
import com.omits.social_api.voice.exception.VoiceProfileNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceProfileServiceTest {

    private static final String INSTRUCTIONS = "Dry, concrete, first person. No emoji.";

    private final VoiceProfileRepository voiceProfileRepository = mock(VoiceProfileRepository.class);
    private final VoiceProfileService voiceProfileService = new VoiceProfileService(voiceProfileRepository);

    // --- create ---------------------------------------------------------------

    @Test
    void createsAnEnabledProfileByDefault() {
        stubNameFree();
        stubSaveEchoesArgument();

        voiceProfileService.create(new CreateVoiceProfileCommand("Technical", INSTRUCTIONS, null, null));

        assertThat(captureSaved().isEnabled()).isTrue();
    }

    /**
     * The first profile created is not silently made default: "the author chose this one" and
     * "it happened to be the only one" should not look identical a month later.
     */
    @Test
    void doesNotMakeAProfileDefaultUnlessAsked() {
        stubNameFree();
        stubSaveEchoesArgument();

        voiceProfileService.create(new CreateVoiceProfileCommand("Technical", INSTRUCTIONS, null, null));

        assertThat(captureSaved().isDefaultProfile()).isFalse();
        verify(voiceProfileRepository, never()).clearDefault();
    }

    @Test
    void clearsThePreviousDefaultWhenCreatingADefaultProfile() {
        stubNameFree();
        stubSaveEchoesArgument();

        voiceProfileService.create(new CreateVoiceProfileCommand("Technical", INSTRUCTIONS, null, true));

        InOrder order = inOrder(voiceProfileRepository);
        order.verify(voiceProfileRepository).clearDefault();
        order.verify(voiceProfileRepository).save(any());
        assertThat(captureSaved().isDefaultProfile()).isTrue();
    }

    @Test
    void trimsNameAndInstructions() {
        stubNameFree();
        stubSaveEchoesArgument();

        voiceProfileService.create(
                new CreateVoiceProfileCommand("  Technical  ", "  " + INSTRUCTIONS + "  ", null, null));

        VoiceProfile saved = captureSaved();
        assertThat(saved.getName()).isEqualTo("Technical");
        assertThat(saved.getInstructions()).isEqualTo(INSTRUCTIONS);
    }

    // --- validation -----------------------------------------------------------

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> voiceProfileService.create(
                new CreateVoiceProfileCommand("   ", INSTRUCTIONS, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        verifyNothingSaved();
    }

    @Test
    void rejectsBlankInstructions() {
        assertThatThrownBy(() -> voiceProfileService.create(
                new CreateVoiceProfileCommand("Technical", "  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions");
        verifyNothingSaved();
    }

    /** A pasted article must not quietly become the system prompt. */
    @Test
    void rejectsInstructionsOverTheMaximumLength() {
        String tooLong = "x".repeat(VoiceProfile.MAX_INSTRUCTIONS_LENGTH + 1);

        assertThatThrownBy(() -> voiceProfileService.create(
                new CreateVoiceProfileCommand("Technical", tooLong, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions");
        verifyNothingSaved();
    }

    /**
     * The name check has to match the {@code lower(name)} unique index, or the database
     * rejects what the service accepted.
     */
    @Test
    void rejectsADuplicateNameRegardlessOfCase() {
        when(voiceProfileRepository.findByNameIgnoreCase("technical"))
                .thenReturn(Optional.of(profile("Technical", true, false)));

        assertThatThrownBy(() -> voiceProfileService.create(
                new CreateVoiceProfileCommand("technical", INSTRUCTIONS, null, null)))
                .isInstanceOf(DuplicateVoiceProfileException.class);
        verifyNothingSaved();
    }

    // --- read -----------------------------------------------------------------

    @Test
    void listsProfilesSortedByNameIgnoringCase() {
        when(voiceProfileRepository.findAll()).thenReturn(List.of(
                profile("terse", true, false),
                profile("Analytical", true, false)));

        assertThat(voiceProfileService.list())
                .extracting(VoiceProfile::getName)
                .containsExactly("Analytical", "terse");
    }

    @Test
    void getThrowsWhenTheProfileDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceProfileService.get(id))
                .isInstanceOf(VoiceProfileNotFoundException.class);
    }

    @Test
    void findDefaultIsEmptyWhenNoProfileHoldsTheFlag() {
        when(voiceProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.empty());

        assertThat(voiceProfileService.findDefault()).isEmpty();
    }

    // --- getEnabled -----------------------------------------------------------

    @Test
    void getEnabledReturnsASwitchedOnProfile() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(profile("Technical", true, false)));

        assertThat(voiceProfileService.getEnabled(id).getName()).isEqualTo("Technical");
    }

    /** Disabling is the author saying "stop writing like this"; honouring it anyway is wrong. */
    @Test
    void getEnabledRejectsADisabledProfile() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(profile("Retired", false, false)));

        assertThatThrownBy(() -> voiceProfileService.getEnabled(id))
                .isInstanceOf(VoiceProfileNotEnabledException.class);
    }

    // --- update ---------------------------------------------------------------

    @Test
    void updateLeavesNullFieldsUnchanged() {
        UUID id = UUID.randomUUID();
        VoiceProfile existing = profile("Technical", true, false);
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(existing));
        stubSaveEchoesArgument();

        voiceProfileService.update(id, new UpdateVoiceProfileCommand(null, null, false, null));

        VoiceProfile saved = captureSaved();
        assertThat(saved.getName()).isEqualTo("Technical");
        assertThat(saved.getInstructions()).isEqualTo(INSTRUCTIONS);
        assertThat(saved.isEnabled()).isFalse();
    }

    /**
     * Clear-then-set, in that order. The partial unique index indexes only rows where the
     * flag is true, so setting first would momentarily present two and be rejected.
     */
    @Test
    void clearsThePreviousDefaultBeforeMarkingANewOne() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(profile("Technical", true, false)));
        stubSaveEchoesArgument();

        voiceProfileService.update(id, new UpdateVoiceProfileCommand(null, null, null, true));

        InOrder order = inOrder(voiceProfileRepository);
        order.verify(voiceProfileRepository).clearDefault();
        order.verify(voiceProfileRepository).save(any());
        assertThat(captureSaved().isDefaultProfile()).isTrue();
    }

    /** Re-marking the profile that already holds it must not clear its own flag. */
    @Test
    void markingTheCurrentDefaultAgainIsANoOp() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(profile("Technical", true, true)));
        stubSaveEchoesArgument();

        voiceProfileService.update(id, new UpdateVoiceProfileCommand(null, null, null, true));

        verify(voiceProfileRepository, never()).clearDefault();
        assertThat(captureSaved().isDefaultProfile()).isTrue();
    }

    @Test
    void clearingTheDefaultLeavesNoneAtAll() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(profile("Technical", true, true)));
        stubSaveEchoesArgument();

        voiceProfileService.update(id, new UpdateVoiceProfileCommand(null, null, null, false));

        verify(voiceProfileRepository, never()).clearDefault();
        assertThat(captureSaved().isDefaultProfile()).isFalse();
    }

    @Test
    void renamingAProfileToItsOwnNameIsNotAConflict() {
        UUID id = UUID.randomUUID();
        VoiceProfile existing = profile("Technical", true, false);
        setId(existing, id);
        when(voiceProfileRepository.findById(id)).thenReturn(Optional.of(existing));
        when(voiceProfileRepository.findByNameIgnoreCase("Technical")).thenReturn(Optional.of(existing));
        stubSaveEchoesArgument();

        voiceProfileService.update(id, new UpdateVoiceProfileCommand("Technical", null, null, null));

        assertThat(captureSaved().getName()).isEqualTo("Technical");
    }

    // --- delete ---------------------------------------------------------------

    @Test
    void rejectsDeletingAProfileThatDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(voiceProfileRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> voiceProfileService.delete(id))
                .isInstanceOf(VoiceProfileNotFoundException.class);
        verify(voiceProfileRepository, never()).deleteById(any());
    }

    // --- helpers --------------------------------------------------------------

    /** Every profile a repository hands back is persisted, so it always has an id. */
    private static VoiceProfile profile(String name, boolean enabled, boolean defaultProfile) {
        VoiceProfile profile = new VoiceProfile(name, INSTRUCTIONS, enabled, defaultProfile);
        setId(profile, UUID.randomUUID());
        return profile;
    }

    private void stubNameFree() {
        when(voiceProfileRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    private void stubSaveEchoesArgument() {
        when(voiceProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private VoiceProfile captureSaved() {
        ArgumentCaptor<VoiceProfile> captor = ArgumentCaptor.forClass(VoiceProfile.class);
        verify(voiceProfileRepository).save(captor.capture());
        return captor.getValue();
    }

    private void verifyNothingSaved() {
        verify(voiceProfileRepository, never()).save(any());
    }

    private static void setId(VoiceProfile profile, UUID id) {
        try {
            var field = VoiceProfile.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(profile, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
