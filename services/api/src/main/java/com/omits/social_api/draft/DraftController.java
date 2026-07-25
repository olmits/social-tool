package com.omits.social_api.draft;

import com.omits.social_api.draft.dto.CreateDraftCommand;
import com.omits.social_api.draft.dto.DraftResponse;
import com.omits.social_api.draft.dto.FailDraftCommand;
import com.omits.social_api.draft.dto.PublishDraftCommand;
import com.omits.social_api.draft.dto.ScheduleDraftCommand;
import com.omits.social_api.draft.model.DraftStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @GetMapping
    public List<DraftResponse> list(@RequestParam(required = false) UUID accountId,
                                    @RequestParam(required = false) DraftStatus status) {
        return draftService.list(accountId, status).stream().map(DraftResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DraftResponse get(@PathVariable UUID id) {
        return DraftResponse.from(draftService.get(id));
    }

    @PostMapping
    public ResponseEntity<DraftResponse> create(@RequestBody CreateDraftCommand command) {
        Draft draft = draftService.create(command.accountId(), command.platform(), command.content());
        return ResponseEntity.created(URI.create("/drafts/" + draft.getId()))
                .body(DraftResponse.from(draft));
    }

    @PatchMapping("/{id}/approve")
    public DraftResponse approve(@PathVariable UUID id) {
        return DraftResponse.from(draftService.approve(id));
    }

    @PatchMapping("/{id}/schedule")
    public DraftResponse schedule(@PathVariable UUID id, @RequestBody ScheduleDraftCommand command) {
        return DraftResponse.from(draftService.schedule(id, command.scheduledAt()));
    }

    // Called by the Go publisher after a successful publish.
    @PatchMapping("/{id}/published")
    public DraftResponse markPublished(@PathVariable UUID id, @RequestBody PublishDraftCommand command) {
        return DraftResponse.from(draftService.markPublished(id, command.remoteId()));
    }

    // Called by the Go publisher when a publish attempt fails.
    @PatchMapping("/{id}/failed")
    public DraftResponse markFailed(@PathVariable UUID id, @RequestBody FailDraftCommand command) {
        return DraftResponse.from(draftService.markFailed(id, command.reason()));
    }
}
