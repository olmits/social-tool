package com.omits.social_api.voice.exception;

/**
 * A draft was requested without naming a voice profile, and there is no default to fall back
 * on — which on a fresh install means no profile has been created at all.
 *
 * <p>Deliberately an error rather than a built-in neutral voice. A silent fallback would make
 * the first drafts sound like nobody, and would quietly make the voice screen skippable; the
 * panel instead disables the Draft button and says why, the same way the radar teaches that
 * it polls nothing until a topic exists. 409 rather than 404 because nothing is missing that
 * the caller named — a precondition of the system is unmet.
 */
public class NoVoiceProfileException extends RuntimeException {

    public NoVoiceProfileException() {
        super("No voice profile to draft with; create one, or mark an existing one as default");
    }
}
