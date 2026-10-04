"use client";

import { Pencil, Star, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import type { VoiceProfileResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/** The instructions are free text and can be long; the row shows the opening. */
const PREVIEW_LENGTH = 150;

/** One voice: its name, the start of its instructions, and its controls. */
export function VoiceRow({
  profile,
  onToggle,
  onMakeDefault,
  onEdit,
  onDelete,
  pending,
}: {
  profile: VoiceProfileResponse;
  onToggle: (enabled: boolean) => void;
  onMakeDefault: () => void;
  onEdit: () => void;
  onDelete: () => void;
  pending: boolean;
}) {
  const preview = profile.instructions.replace(/\s+/g, " ").trim();

  return (
    <div
      className={cn(
        "flex flex-wrap items-center gap-x-3.5 gap-y-2.5 border-b border-border px-4.5 py-3.5 last:border-b-0 hover:bg-muted/30",
        !profile.enabled && "opacity-60",
      )}
    >
      <div className="min-w-[200px] flex-1">
        <div className="flex items-center gap-2">
          <span className="text-[13.5px] font-semibold leading-snug">
            {profile.name}
          </span>
          {profile.isDefault && (
            <span className="inline-flex items-center gap-1 rounded bg-muted px-1.5 py-0.5 text-[10.5px] font-semibold text-muted-foreground">
              <Star className="size-2.5 fill-current" />
              Default
            </span>
          )}
        </div>
        <p className="mt-1.5 line-clamp-2 text-[11.5px] text-muted-foreground">
          {preview.length > PREVIEW_LENGTH
            ? `${preview.slice(0, PREVIEW_LENGTH)}…`
            : preview}
        </p>
      </div>

      <div className="flex items-center gap-2.5">
        <div className="flex items-center gap-2 text-[11.5px] text-muted-foreground">
          <Switch
            checked={profile.enabled}
            onCheckedChange={onToggle}
            disabled={pending}
            aria-label={`Use ${profile.name} for drafting`}
          />
          {profile.enabled ? "On" : "Off"}
        </div>
        {/* Only offered where it does something: a disabled profile cannot be
            the default, and re-defaulting the current one is a no-op. */}
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onMakeDefault}
          disabled={pending || profile.isDefault || !profile.enabled}
          title={
            profile.isDefault
              ? "Already the default"
              : profile.enabled
                ? "Make default"
                : "Switch it on to make it the default"
          }
        >
          <Star className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onEdit}
          title="Edit voice"
        >
          <Pencil className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onDelete}
          title="Delete voice"
          className="text-muted-foreground hover:text-destructive"
        >
          <Trash2 className="size-3.5" />
        </Button>
      </div>
    </div>
  );
}
