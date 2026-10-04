package com.omits.social_api.generation;

/**
 * What the model produced.
 *
 * <p>Distinct from {@code claude.GeneratedPost}, which is the structured-output shape the SDK
 * derives a JSON schema from and which carries Jackson 2 annotations. This one is the slice's
 * own vocabulary, so nothing outside {@code generation.claude} depends on the SDK's.
 *
 * @param content   the post text, exactly as it would be published
 * @param rationale one sentence on the angle taken. Never persisted and never shown to the
 *                  author — it exists so the model has somewhere to put framing other than
 *                  the post itself, and so a confusing draft can be explained from the logs
 */
public record GeneratedDraft(String content, String rationale) {
}
