package com.omits.social_api.voice.dto;

/**
 * @param enabled  null means enabled — a profile is created to be used, and requiring the
 *                 flag on every create would be ceremony for the default case
 * @param isDefault null means false. Only meaningful as true, which moves the default off
 *                 whichever profile currently holds it; the very first profile created is
 *                 <em>not</em> made default implicitly, because "the author chose this one"
 *                 and "it was the only one at the time" should not look identical later
 */
public record CreateVoiceProfileCommand(String name, String instructions, Boolean enabled,
                                        Boolean isDefault) {
}
