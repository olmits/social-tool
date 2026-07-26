import { platformLabel } from "@/lib/api/mappers";
import type { DraftResponse } from "@/lib/api/types";
import { PLATFORM_META } from "@/lib/mock-data";
import { cn } from "@/lib/utils";
import { StatusBadge } from "./StatusBadge";

/**
 * The draft detail header: platform swatch, account name + platform line, and
 * the status badge. `accountName` is resolved by the caller (the draft DTO only
 * carries `accountId`).
 */
export function DetailHeader({
  draft,
  accountName,
}: {
  draft: DraftResponse;
  accountName: string;
}) {
  const meta = PLATFORM_META[draft.platform];
  return (
    <>
      <span
        className={cn(
          "flex size-9.5 shrink-0 items-center justify-center rounded-xl",
          meta.badgeBg,
        )}
      >
        <span
          className="size-2.5 rounded-full"
          style={{ background: meta.dot }}
        />
      </span>
      <div className="flex-1">
        <div className="text-[15px] font-semibold">{accountName}</div>
        <div className="text-[12.5px] text-muted-foreground">
          {platformLabel(draft.platform)} ·{" "}
          {draft.platform === "REDDIT" ? "single account" : "switchable"}
        </div>
      </div>
      <StatusBadge status={draft.status} size="lg" />
    </>
  );
}
