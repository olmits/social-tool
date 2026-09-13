"use client";

import { SIGNAL_SOURCES, SOURCE_META, sourceLabel } from "@/lib/api/mappers";
import type { SignalSource } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/**
 * Source filter chips. Driven by `SIGNAL_SOURCES` — the sources the worker
 * actually polls — not by every value of the enum, so the row never offers a
 * filter that can only ever return nothing.
 */
export function SourceChips({
  value,
  onChange,
  disabled,
}: {
  value: SignalSource | null;
  onChange: (source: SignalSource | null) => void;
  disabled: boolean;
}) {
  return (
    <div className="flex w-full flex-nowrap gap-1.5 overflow-x-auto sm:w-auto md:flex-wrap md:overflow-visible">
      <button
        type="button"
        onClick={() => onChange(null)}
        disabled={disabled}
        className={cn(
          "flex h-7.5 shrink-0 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium disabled:opacity-60",
          value === null
            ? "border-foreground bg-foreground text-background"
            : "border-border bg-background text-foreground/80 hover:bg-muted",
        )}
      >
        <span
          className="size-1.5 rounded-full"
          style={{ background: value === null ? "currentColor" : "#a3a3a3" }}
        />
        All
      </button>
      {SIGNAL_SOURCES.map((source) => {
        const on = value === source;
        return (
          <button
            type="button"
            key={source}
            onClick={() => onChange(source)}
            disabled={disabled}
            className={cn(
              "flex h-7.5 shrink-0 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium disabled:opacity-60",
              on
                ? "border-foreground bg-foreground text-background"
                : "border-border bg-background text-foreground/80 hover:bg-muted",
            )}
          >
            <span
              className="size-1.5 rounded-full"
              style={{
                background: on ? "currentColor" : SOURCE_META[source].dot,
              }}
            />
            {sourceLabel(source)}
          </button>
        );
      })}
    </div>
  );
}
