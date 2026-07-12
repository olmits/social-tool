package com.omits.social_api.account.exception;

public class RedditAccountLimitException extends RuntimeException {

    public RedditAccountLimitException() {
        super("Reddit only supports a single connected account; disconnect the existing one first");
    }
}
