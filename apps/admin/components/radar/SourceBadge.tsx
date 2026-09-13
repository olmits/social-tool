import { SOURCE_META, sourceLabel } from "@/lib/api/mappers";
import type { SignalSource } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/** The source chip on a signal — dot, name, source colours. */
export function SourceBadge({ source }: { source: SignalSource }) {
  const meta = SOURCE_META[source];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-[10.5px] font-semibold",
        meta.badgeBg,
        meta.badgeText,
      )}
    >
      <span
        className="size-1.5 rounded-full"
        style={{ background: meta.dot }}
      />
      {sourceLabel(source)}
    </span>
  );
}
