package com.omits.social_api.topic.dto;

import com.omits.social_api.signal.model.SignalSource;

import java.util.Map;

/**
 * A <strong>sparse</strong> patch: a null field is left unchanged.
 *
 * <p>This deliberately differs from {@code EditDraftCommand}, which replaces a draft's whole
 * editable body. The difference is in how each is used: a draft is edited in one form that
 * always holds every field, whereas a topic's enabled toggle is its own interaction in the
 * topic list, and making it resend the name and every query to flip a boolean invites a
 * concurrent edit to be silently overwritten.
 *
 * <p>{@code queries} is replace-not-merge when present: a map of two entries leaves the topic
 * with exactly those two, and an empty map clears them. Merging would make it impossible to
 * ever remove a source's query.
 */
public record UpdateTopicCommand(String name, Boolean enabled, Map<SignalSource, String> queries) {
}
