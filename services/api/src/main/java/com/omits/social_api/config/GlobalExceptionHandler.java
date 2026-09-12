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
import com.omits.social_api.topic.exception.DuplicateTopicException;
import com.omits.social_api.topic.exception.TopicNotEnabledException;
import com.omits.social_api.topic.exception.TopicNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, DraftNotFoundException.class,
            TopicNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({DuplicateAccountException.class, RedditAccountLimitException.class,
            InvalidStateTransitionException.class, AccountNotActiveException.class,
            PlatformMismatchException.class, DuplicateTopicException.class,
            TopicNotEnabledException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DisclosureRequiredException.class)
    public ResponseEntity<ErrorResponse> handleDisclosureRequired(DisclosureRequiredException e) {
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

    @ExceptionHandler(PlatformApiException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamPlatformError(PlatformApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
    }
}
