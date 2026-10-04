package com.omits.social_api.generation.exception;

/**
 * The model declined to write this draft.
 *
 * <p>Deliberately not a {@link DraftGenerationException}, and so not a 502. A refusal arrives
 * as a perfectly successful HTTP 200 with no usable content — nothing upstream failed, the
 * model simply would not answer this input. 422 says exactly that, and puts it next to
 * {@code DisclosureRequiredException}: understood, well-formed, and still not processable.
 */
public class DraftRefusedException extends RuntimeException {

    public DraftRefusedException(String category) {
        super(category == null
                ? "Claude declined to draft a post from this signal"
                : "Claude declined to draft a post from this signal (" + category + ")");
    }
}
