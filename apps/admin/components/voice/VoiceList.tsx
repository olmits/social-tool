"use client";

import type { VoiceProfileResponse } from "@/lib/api/types";
import { VoiceRow } from "./VoiceRow";

/** The voice rows in a bordered card. Empty handled by the caller (`VoiceEmpty`). */
export function VoiceList({
  profiles,
  onToggle,
  onMakeDefault,
  onEdit,
  onDelete,
  pendingId,
}: {
  profiles: VoiceProfileResponse[];
  onToggle: (profile: VoiceProfileResponse, enabled: boolean) => void;
  onMakeDefault: (profile: VoiceProfileResponse) => void;
  onEdit: (profile: VoiceProfileResponse) => void;
  onDelete: (profile: VoiceProfileResponse) => void;
  pendingId: string | null;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-border">
      {profiles.map((profile) => (
        <VoiceRow
          key={profile.id}
          profile={profile}
          onToggle={(enabled) => onToggle(profile, enabled)}
          onMakeDefault={() => onMakeDefault(profile)}
          onEdit={() => onEdit(profile)}
          onDelete={() => onDelete(profile)}
          pending={pendingId === profile.id}
        />
      ))}
    </div>
  );
}
