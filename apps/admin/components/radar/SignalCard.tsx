import { engagementLabel } from "@/lib/api/mappers";
import type { SignalResponse } from "@/lib/api/types";
import { DraftSignalButton } from "./DraftSignalButton";
import { MatchMeter } from "./MatchMeter";
import { signalAge } from "./radarDisplay";
import { SourceBadge } from "./SourceBadge";
import { TopicChip } from "./TopicChip";

/** One signal as a card — the cards layout on desktop, and the only layout on mobile. */
export function SignalCard({
  signal,
  now,
  onDraft,
  draftDisabledReason,
}: {
  signal: SignalResponse;
  now: number;
  onDraft: (signal: SignalResponse) => void;
  draftDisabledReason: string | null;
}) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-background p-4.5 hover:shadow-sm">
      <div className="flex items-center gap-2">
        <SourceBadge source={signal.source} />
        <TopicChip name={signal.topicName} />
        <div className="flex-1" />
        <span className="font-mono text-[11.5px] text-muted-foreground">
          {signalAge(signal, now)}
        </span>
      </div>
      <a
        href={signal.url}
        target="_blank"
        rel="noreferrer noopener"
        className="text-[14.5px] font-semibold leading-snug hover:underline"
      >
        {signal.title}
      </a>
      <div className="text-[12.5px] text-muted-foreground">
        {engagementLabel(signal.source, signal.nativeScore)}
      </div>
      <div className="mt-auto flex items-center gap-2.5">
        <MatchMeter score={signal.score} className="flex-1" suffix=" match" />
        <DraftSignalButton
          onDraft={() => onDraft(signal)}
          disabledReason={draftDisabledReason}
        />
      </div>
    </div>
  );
}
