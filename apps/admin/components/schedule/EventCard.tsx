import type { DraftResponse } from "@/lib/api/types";
import { PLATFORM_META } from "@/lib/mock-data";
import { formatTimeUtc } from "./scheduleWeek";

/** A single scheduled draft on the calendar: time, content excerpt, account. */
export function EventCard({
  draft,
  accountName,
}: {
  draft: DraftResponse;
  accountName: string;
}) {
  const meta = PLATFORM_META[draft.platform];
  return (
    <div
      className="cursor-pointer rounded-lg border border-border border-l-[3px] bg-background p-2.5 hover:shadow-sm"
      style={{ borderLeftColor: meta.dot }}
    >
      <div className="mb-1.5 flex items-center gap-1.5">
        <span className="text-[11px] font-bold tabular-nums">
          {draft.scheduledAt ? formatTimeUtc(draft.scheduledAt) : ""}
        </span>
        <div className="flex-1" />
        <span className="size-2 rounded-sm bg-blue-500" />
      </div>
      <div className="line-clamp-3 text-[11.5px] leading-snug text-neutral-600 dark:text-neutral-400">
        {draft.content.replace(/\n+/g, " ")}
      </div>
      <div className="mt-2 flex items-center gap-1.5">
        <span
          className="size-1.5 rounded-full"
          style={{ background: meta.dot }}
        />
        <span className="text-[10.5px] text-muted-foreground">
          {accountName}
        </span>
      </div>
    </div>
  );
}
