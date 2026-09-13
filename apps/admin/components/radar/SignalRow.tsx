import { engagementLabel } from "@/lib/api/mappers";
import type { SignalResponse } from "@/lib/api/types";
import { DraftSignalButton } from "./DraftSignalButton";
import { MatchMeter } from "./MatchMeter";
import { signalAge } from "./radarDisplay";
import { SourceBadge } from "./SourceBadge";
import { TopicChip } from "./TopicChip";

/** One signal as a table row (desktop). */
export function SignalRow({
  signal,
  now,
}: {
  signal: SignalResponse;
  now: number;
}) {
  return (
    <div className="flex min-w-[760px] items-center gap-3.5 border-b border-border px-4.5 py-3.5 last:border-b-0 hover:bg-muted/30">
      <div className="min-w-[180px] flex-1">
        <SourceBadge source={signal.source} />
        <a
          href={signal.url}
          target="_blank"
          rel="noreferrer noopener"
          className="mt-1.5 block text-[13.5px] font-semibold leading-snug hover:underline"
        >
          {signal.title}
        </a>
      </div>
      <div className="w-[120px] shrink-0">
        <TopicChip name={signal.topicName} />
      </div>
      <div className="w-[158px] shrink-0 text-[12.5px] tabular-nums text-muted-foreground">
        {engagementLabel(signal.source, signal.nativeScore)}
      </div>
      <MatchMeter score={signal.score} className="w-[128px] shrink-0" />
      <div className="w-[60px] shrink-0 font-mono text-xs text-muted-foreground">
        {signalAge(signal, now)}
      </div>
      <div className="flex w-[80px] shrink-0 justify-end">
        <DraftSignalButton />
      </div>
    </div>
  );
}
