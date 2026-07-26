import { statusLabel } from "@/lib/api/mappers";
import type { DraftStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { STATUS_META } from "./draftDisplay";

export function StatusBadge({
  status,
  size = "sm",
}: {
  status: DraftStatus;
  size?: "sm" | "lg";
}) {
  const meta = STATUS_META[status];
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full font-semibold",
        meta.badgeBg,
        meta.badgeText,
        size === "lg"
          ? "px-2.5 py-1 text-[11.5px]"
          : "px-2 py-0.5 text-[10.5px]",
      )}
    >
      {statusLabel(status)}
    </span>
  );
}
