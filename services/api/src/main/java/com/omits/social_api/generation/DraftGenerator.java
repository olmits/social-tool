package com.omits.social_api.generation;

/**
 * The port between generation orchestration and whatever actually writes the text.
 *
 * <p>An interface with a single implementation, mirroring {@code PlatformAdapter} →
 * {@code BlueskyAdapter}. It earns its place by being the seam tests replace: without it,
 * {@code GenerationServiceTest} and {@code GenerationControllerIntegrationTest} would have to
 * call the real, paid API.
 */
public interface DraftGenerator {

    /**
     * @throws com.omits.social_api.generation.exception.DraftGenerationException if the model
     *         could not be reached, timed out, or returned nothing usable
     * @throws com.omits.social_api.generation.exception.DraftRefusedException if the model
     *         declined the request
     */
    GeneratedDraft generate(GenerationRequest request);

    /**
     * Whether this generator can read the article behind a signal's URL.
     *
     * <p>Lives here rather than being read from configuration by the caller so that one class
     * owns every decision about the shape of a request — the prompt has to say "read the
     * article" only when the request will actually carry the tool to do it, and keeping those
     * two in different classes is how they drift apart.
     */
    boolean articleFetchEnabled();
}
