package com.omits.social_api.config;

import com.omits.social_api.account.exception.AccountNotFoundException;
import com.omits.social_api.account.exception.DuplicateAccountException;
import com.omits.social_api.account.exception.RedditAccountLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({DuplicateAccountException.class, RedditAccountLimitException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }
}
