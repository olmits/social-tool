"use client";

import { ChevronDown } from "lucide-react";
import type { VoiceProfileResponse } from "@/lib/api/types";

/**
 * Picks the voice a draft is written in. A styled native select, matching
 * {@link TopicFilter} — there is no select primitive in `components/ui`, and one
 * short list does not justify introducing one.
 */
export function VoicePicker({
  profiles,
  value,
  onChange,
  disabled,
}: {
  /** Enabled profiles only; a disabled one is rejected by the API. */
  profiles: VoiceProfileResponse[];
  value: string | null;
  onChange: (voiceProfileId: string | null) => void;
  disabled: boolean;
}) {
  return (
    <div className="relative">
      <select
        aria-label="Voice"
        value={value ?? ""}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value || null)}
        className="h-9 w-full appearance-none rounded-lg border border-border bg-background pl-2.5 pr-7 text-[13px] outline-none focus:border-neutral-400 disabled:opacity-50 dark:focus:border-neutral-500"
      >
        {profiles.map((profile) => (
          <option key={profile.id} value={profile.id}>
            {profile.name}
            {profile.isDefault ? " (default)" : ""}
          </option>
        ))}
      </select>
      <ChevronDown className="pointer-events-none absolute right-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
    </div>
  );
}
