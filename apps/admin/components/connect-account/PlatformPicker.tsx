"use client";

import { PLATFORM_META } from "@/lib/mock-data";
import {
  PLATFORM_CONNECT_FIELDS,
  PLATFORM_CONNECT_ORDER,
} from "@/lib/platform-connect";
import type { Platform } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Segmented platform selector. Disabled options (Reddit when taken) are inert. */
export function PlatformPicker({
  value,
  onSelect,
  isDisabled,
  redditTaken,
}: {
  value: Platform;
  onSelect: (platform: Platform) => void;
  isDisabled: (platform: Platform) => boolean;
  redditTaken: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[12.5px] font-medium">Platform</span>
      <div className="grid grid-cols-3 gap-1.5">
        {PLATFORM_CONNECT_ORDER.map((p) => {
          const meta = PLATFORM_META[p];
          const active = p === value;
          const disabled = isDisabled(p);
          return (
            <button
              key={p}
              type="button"
              onClick={() => onSelect(p)}
              disabled={disabled}
              title={
                disabled ? "Single account — already connected" : undefined
              }
              className={cn(
                "flex items-center justify-center gap-1.5 rounded-lg border px-2 py-2 text-[12.5px] font-medium transition-colors",
                active
                  ? "border-neutral-400 bg-muted dark:border-neutral-600"
                  : "border-border hover:bg-muted",
                disabled &&
                  "cursor-not-allowed opacity-40 hover:bg-transparent",
              )}
            >
              <span
                className="size-2 rounded-full"
                style={{ background: meta.dot }}
              />
              {PLATFORM_CONNECT_FIELDS[p].label}
            </button>
          );
        })}
      </div>
      {redditTaken && (
        <p className="text-[11px] text-muted-foreground">
          Reddit allows a single connected account.
        </p>
      )}
    </div>
  );
}
