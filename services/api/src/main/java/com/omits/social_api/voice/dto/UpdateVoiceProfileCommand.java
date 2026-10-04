package com.omits.social_api.voice.dto;

/**
 * A <strong>sparse</strong> patch: a null field is left unchanged, matching
 * {@code UpdateTopicCommand} and deliberately unlike {@code EditDraftCommand}.
 *
 * <p>The reason is the same as for topics: the enabled and default toggles are their own
 * interactions in the profile list, and making them resend the name and the whole
 * instructions body to flip a boolean invites a concurrent edit to be silently overwritten.
 *
 * <p>{@code isDefault = false} on the profile that currently holds the default simply clears
 * it, leaving no default at all. That is a legitimate state — generation then requires the
 * caller to name a profile explicitly.
 */
public record UpdateVoiceProfileCommand(String name, String instructions, Boolean enabled,
                                        Boolean isDefault) {
}
