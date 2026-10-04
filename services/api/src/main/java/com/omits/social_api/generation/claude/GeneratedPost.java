package com.omits.social_api.generation.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured-output shape: the SDK derives a JSON schema from this record and the model is
 * constrained to return exactly it.
 *
 * <p>That constraint is the point. Asked for plain text, a model wraps the post in "Here's a
 * draft:" often enough that the panel would need to strip preambles by heuristic.
 *
 * <p><strong>The import matters.</strong> {@link JsonPropertyDescription} here must be the
 * Jackson 2 annotation, because Jackson 2 is what the SDK derives schemas with — while the
 * rest of this service serializes with Jackson 3 ({@code tools.jackson}). The two coexist on
 * the classpath, but a Jackson 3 serializer would ignore these annotations entirely. That is
 * why this record stays inside {@code generation.claude} and is never returned from a
 * controller; {@code generation.GeneratedDraft} is the slice's own outward shape.
 *
 * <p>There is deliberately no {@code characterCount} field. Models are unreliable at counting
 * their own output, and the service measures graphemes server-side regardless.
 */
record GeneratedPost(

        @JsonPropertyDescription("The post text exactly as it should be published, with no "
                + "surrounding quotes, labels, or explanation.")
        String content,

        @JsonPropertyDescription("One sentence naming the angle taken, for the author's "
                + "reference. Not published.")
        String rationale) {
}
