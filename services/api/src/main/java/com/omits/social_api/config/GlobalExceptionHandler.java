package com.omits.social_api.config;

import com.omits.social_api.account.exception.AccountNotActiveException;
import com.omits.social_api.account.exception.AccountNotFoundException;
import com.omits.social_api.account.exception.DuplicateAccountException;
import com.omits.social_api.account.exception.RedditAccountLimitException;
import com.omits.social_api.adapter.exception.PlatformApiException;
import com.omits.social_api.draft.exception.DisclosureRequiredException;
import com.omits.social_api.draft.exception.DraftNotFoundException;
import com.omits.social_api.draft.exception.InvalidStateTransitionException;
import com.omits.social_api.draft.exception.PlatformMismatchException;
import com.omits.social_api.signal.exception.SignalNotFoundException;
import com.omits.social_api.topic.exception.DuplicateTopicException;
import com.omits.social_api.topic.exception.TopicNotEnabledException;
import com.omits.social_api.topic.exception.TopicNotFoundException;
import com.omits.social_api.generation.exception.DraftGenerationException;
import com.omits.social_api.generation.exception.DraftRefusedException;
import com.omits.social_api.voice.exception.DuplicateVoiceProfileException;
import com.omits.social_api.voice.exception.NoVoiceProfileException;
import com.omits.social_api.voice.exception.VoiceProfileNotEnabledException;
import com.omits.social_api.voice.exception.VoiceProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, DraftNotFoundException.class,
            TopicNotFoundException.class, VoiceProfileNotFoundException.class,
            SignalNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({DuplicateAccountException.class, RedditAccountLimitException.class,
            InvalidStateTransitionException.class, AccountNotActiveException.class,
            PlatformMismatchException.class, DuplicateTopicException.class,
            TopicNotEnabledException.class, DuplicateVoiceProfileException.class,
            VoiceProfileNotEnabledException.class, NoVoiceProfileException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Understood, well-formed, and still not processable: an affiliate link without its
     * disclosure, or a model that declined to write the post it was asked for. A refusal is
     * not a 502 — nothing upstream failed, it answered 200 and said no.
     */
    @ExceptionHandler({DisclosureRequiredException.class, DraftRefusedException.class})
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * A body that does not fit the endpoint's shape — a wrong JSON type, a malformed
     * document, an unparseable timestamp. Jackson's own message names the offending field
     * and is not echoed back, since it exposes internal type names; the status is what the
     * caller needs to know it sent the request wrong.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("request body could not be read: check field types against the endpoint's contract"));
    }

    /** This service is fine; something it depends on is not. */
    @ExceptionHandler({PlatformApiException.class, DraftGenerationException.class})
    public ResponseEntity<ErrorResponse> handleUpstreamError(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
    }
}
