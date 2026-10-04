import type { SignalResponse } from "@/lib/api/types";
import { SignalCard } from "./SignalCard";

/** The card grid: one column on mobile, two from `md` up. */
export function SignalCards({
  signals,
  now,
  onDraft,
  draftDisabledReason,
}: {
  signals: SignalResponse[];
  now: number;
  onDraft: (signal: SignalResponse) => void;
  draftDisabledReason: string | null;
}) {
  return (
    <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
      {signals.map((signal) => (
        <SignalCard
          key={signal.id}
          signal={signal}
          now={now}
          onDraft={onDraft}
          draftDisabledReason={draftDisabledReason}
        />
      ))}
    </div>
  );
}
