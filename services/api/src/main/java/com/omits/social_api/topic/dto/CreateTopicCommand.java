package com.omits.social_api.topic.dto;

import com.omits.social_api.signal.model.SignalSource;

import java.util.Map;

/**
 * @param enabled null means enabled — a topic is created to be polled, and requiring the flag
 *                on every create would be ceremony for the default case
 * @param queries per-source queries; null or empty creates a topic that is polled nowhere
 *                until queries are added, which is a legitimate half-configured state
 */
public record CreateTopicCommand(String name, Boolean enabled, Map<SignalSource, String> queries) {
}
