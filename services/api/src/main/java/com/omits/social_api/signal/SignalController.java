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

@RestController
@RequestMapping("/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    @GetMapping
    public List<SignalResponse> list(@RequestParam(required = false) SignalSource source) {
        return signalService.list(source).stream().map(SignalResponse::from).toList();
    }

    /**
     * Called by the Go trend-radar poller once per run. Idempotent: re-posting a batch
     * refreshes the stored signals instead of duplicating them, so there is no created-vs-
     * conflict distinction to express in the status code — 200 with the counts, not 201.
     */
    @PostMapping
    public IngestSignalsResponse ingest(@RequestBody IngestSignalsCommand command) {
        return signalService.ingest(command);
    }
}
