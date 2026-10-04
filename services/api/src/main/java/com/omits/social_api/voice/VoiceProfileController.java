package com.omits.social_api.voice;

import com.omits.social_api.voice.dto.CreateVoiceProfileCommand;
import com.omits.social_api.voice.dto.UpdateVoiceProfileCommand;
import com.omits.social_api.voice.dto.VoiceProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/voice-profiles")
@RequiredArgsConstructor
public class VoiceProfileController {

    private final VoiceProfileService voiceProfileService;

    @GetMapping
    public List<VoiceProfileResponse> list() {
        return voiceProfileService.list().stream().map(VoiceProfileResponse::from).toList();
    }

    @GetMapping("/{id}")
    public VoiceProfileResponse get(@PathVariable UUID id) {
        return VoiceProfileResponse.from(voiceProfileService.get(id));
    }

    @PostMapping
    public ResponseEntity<VoiceProfileResponse> create(@RequestBody CreateVoiceProfileCommand command) {
        VoiceProfile profile = voiceProfileService.create(command);
        return ResponseEntity.created(URI.create("/voice-profiles/" + profile.getId()))
                .body(VoiceProfileResponse.from(profile));
    }

    // A sparse patch — see UpdateVoiceProfileCommand. A null field is left unchanged.
    @PatchMapping("/{id}")
    public VoiceProfileResponse update(@PathVariable UUID id,
                                       @RequestBody UpdateVoiceProfileCommand command) {
        return VoiceProfileResponse.from(voiceProfileService.update(id, command));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        voiceProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
