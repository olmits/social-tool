package com.omits.social_api.generation;

import com.omits.social_api.draft.Draft;
import com.omits.social_api.draft.dto.DraftResponse;
import com.omits.social_api.generation.dto.GenerateDraftCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * {@code POST /drafts/generate} — the AI-authoring route.
 *
 * <p>Its own controller in the generation package rather than another method on
 * {@code DraftController}, which keeps that class the state-machine controller and avoids a
 * package cycle: {@code generation} already depends on {@code draft}, and putting this route
 * there would point the dependency both ways. The whole slice can be deleted as a unit.
 *
 * <p>The response is a {@code DraftResponse}, identical to {@code POST /drafts} — the point
 * of this endpoint is that what comes out is an ordinary draft, subject to exactly the same
 * review and approval as one typed by hand.
 */
@RestController
@RequestMapping("/drafts/generate")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    /**
     * Synchronous, and slow by the standards of everything else here — the model has to read
     * an article and write a post, which takes tens of seconds. Callers need a timeout to
     * match and something on screen in the meantime.
     */
    @PostMapping
    public ResponseEntity<DraftResponse> generate(@RequestBody GenerateDraftCommand command) {
        Draft draft = generationService.generate(command);
        return ResponseEntity.created(URI.create("/drafts/" + draft.getId()))
                .body(DraftResponse.from(draft));
    }
}
