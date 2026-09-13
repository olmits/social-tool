import type { SignalResponse } from "@/lib/api/types";
import { SignalRow } from "./SignalRow";

/** The desktop table. An empty list is handled a level up, by `RadarView`. */
export function SignalTable({
  signals,
  now,
}: {
  signals: SignalResponse[];
  now: number;
}) {
  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <div className="flex min-w-[760px] items-center gap-3.5 border-b border-border bg-muted/40 px-4.5 py-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        <div className="min-w-[180px] flex-1">Signal</div>
        <div className="w-[120px] shrink-0">Topic</div>
        <div className="w-[158px] shrink-0">Engagement</div>
        <div className="w-[128px] shrink-0">Match</div>
        <div className="w-[60px] shrink-0">Age</div>
        <div className="w-[80px] shrink-0" />
      </div>
      {signals.map((signal) => (
        <SignalRow key={signal.id} signal={signal} now={now} />
      ))}
    </div>
  );
}
