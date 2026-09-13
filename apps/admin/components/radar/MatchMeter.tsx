import { cn } from "@/lib/utils";

/**
 * How well a signal matched its topic, 0-100.
 *
 * Normalized within its own (topic, source) batch, so 100 means "top of this
 * topic on that source" — it does not rank one source against another.
 */
export function MatchMeter({
  score,
  className,
  suffix,
}: {
  score: number;
  className?: string;
  /** e.g. " match" on the cards, where there is no column header to say so. */
  suffix?: string;
}) {
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-primary"
          style={{ width: `${score}%` }}
        />
      </div>
      <span className="shrink-0 text-xs font-semibold tabular-nums">
        {score}%{suffix}
      </span>
    </div>
  );
}
