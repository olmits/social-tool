"use client";

import { Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { VoiceProfileResponse } from "@/lib/api/types";
import { DeleteVoiceDialog } from "./DeleteVoiceDialog";
import { useVoiceMutations } from "./useVoiceMutations";
import { VoiceDialog } from "./VoiceDialog";
import { VoiceEmpty } from "./VoiceEmpty";
import { VoiceList } from "./VoiceList";

/**
 * Voices screen: the list, its two dialogs, and the header. A voice is the third
 * input to drafting, alongside the signal and the account's platform — with none
 * configured the radar's Draft button stays off, which is why this sits in the
 * nav next to Topics rather than somewhere under settings.
 */
export function VoiceView({ profiles }: { profiles: VoiceProfileResponse[] }) {
  // `null` closed; `{ profile: null }` creating; `{ profile }` editing. A plain
  // `VoiceProfileResponse | null` couldn't tell "create" from "closed".
  const [form, setForm] = useState<{
    profile: VoiceProfileResponse | null;
  } | null>(null);
  const [deleting, setDeleting] = useState<VoiceProfileResponse | null>(null);
  const { setEnabled, makeDefault, remove, pendingId } = useVoiceMutations();

  const enabledCount = profiles.filter((profile) => profile.enabled).length;

  return (
    <div>
      <header className="sticky top-0 z-20 flex items-end justify-between gap-4 border-b border-border bg-background/85 px-4 py-4.5 backdrop-blur-sm md:px-7">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Voice</h1>
          <p className="mt-1 hidden text-[13px] text-muted-foreground sm:block">
            How drafts should sound. Pick one each time you draft from a signal.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="hidden font-mono text-[11.5px] text-muted-foreground sm:block">
            {enabledCount} of {profiles.length} on
          </span>
          <Button size="sm" onClick={() => setForm({ profile: null })}>
            <Plus className="size-3.5" />
            <span className="hidden sm:inline">New voice</span>
          </Button>
        </div>
      </header>

      <div className="max-w-[1180px] px-4 py-5.5 pb-10 md:px-7">
        {profiles.length === 0 ? (
          <VoiceEmpty onCreate={() => setForm({ profile: null })} />
        ) : (
          <VoiceList
            profiles={profiles}
            onToggle={setEnabled}
            onMakeDefault={makeDefault}
            onEdit={(profile) => setForm({ profile })}
            onDelete={setDeleting}
            pendingId={pendingId}
          />
        )}
      </div>

      {/* Mounted only while open, and keyed by profile, so the form takes fresh
          defaults rather than the previously edited profile's values. */}
      {form && (
        <VoiceDialog
          key={form.profile?.id ?? "new"}
          profile={form.profile}
          onClose={() => setForm(null)}
        />
      )}

      {deleting && (
        <DeleteVoiceDialog
          name={deleting.name}
          onCancel={() => setDeleting(null)}
          onConfirm={() => remove(deleting, () => setDeleting(null))}
          pending={pendingId === deleting.id}
        />
      )}
    </div>
  );
}
