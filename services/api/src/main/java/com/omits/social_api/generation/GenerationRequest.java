package com.omits.social_api.generation;

/**
 * One prompt, ready to send — the whole interface between prompt building and the model.
 *
 * <p>Keeping it to two strings is what lets {@link PromptFactory} be tested with no
 * mocks and {@link DraftGenerator} be faked with no knowledge of prompts.
 *
 * @param systemPrompt the invariant rules plus the author's voice guidance
 * @param userMessage  everything specific to this request: the signal, the platform, the limit
 * @param articleUrl   the signal's URL, or null when the model should not try to read it.
 *                     Separate from {@code userMessage} because it decides whether the
 *                     request carries a web-fetch tool at all, which is a request shape
 *                     question rather than a prompt one
 */
public record GenerationRequest(String systemPrompt, String userMessage, String articleUrl) {
}
