package com.omits.social_api.signal;

import com.omits.social_api.signal.dto.IngestSignalsCommand;
import com.omits.social_api.signal.dto.IngestSignalsResponse;
import com.omits.social_api.signal.dto.SignalResponse;
import com.omits.social_api.signal.model.SignalSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/signals")
@RequiredArgsConstructor
public class SignalController {

    private final RadarService radarService;

    /** Stored signals ranked by score, narrowed by topic and/or source (both optional). */
    @GetMapping
    public List<SignalResponse> list(@RequestParam(required = false) UUID topicId,
                                     @RequestParam(required = false) SignalSource source) {
        return radarService.list(topicId, source);
    }

    /**
     * Called by the Go trend-radar poller once per run. Idempotent: re-posting a batch
     * refreshes the stored signals instead of duplicating them, so there is no created-vs-
     * conflict distinction to express in the status code — 200 with the counts, not 201.
     */
    @PostMapping
    public IngestSignalsResponse ingest(@RequestBody IngestSignalsCommand command) {
        return radarService.ingest(command);
    }
}
