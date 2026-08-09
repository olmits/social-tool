package com.omits.social_api.draft.dto;

/**
 * The editable body of a draft, as submitted from the review panel.
 *
 * <p>This is a <em>full replace</em> of the three editable fields rather than a sparse patch:
 * a null {@code affiliateLinks} means "no affiliate links", not "leave unchanged", so callers
 * must send all three fields. {@code disclosureIncluded} is a primitive, so an omitted JSON
 * property deserializes to {@code false}.
 */
public record EditDraftCommand(String content, String affiliateLinks, boolean disclosureIncluded) {
}
