import { RadarView } from "@/components/radar/RadarView";
import { SIGNAL_SOURCES } from "@/lib/api/mappers";
import { listSignals } from "@/lib/api/signals";
import { listTopics } from "@/lib/api/topics";
import type { SignalSource } from "@/lib/api/types";

// Signals are re-polled continuously; render on demand. Reads are tag-cached
// ("signals") so the Refresh action re-reads the store via updateTag.
export const dynamic = "force-dynamic";

/** Only a source the worker actually polls; anything else is dropped, not passed on. */
function parseSource(
  value: string | string[] | undefined,
): SignalSource | null {
  return SIGNAL_SOURCES.find((source) => source === value) ?? null;
}

function parseTopicId(value: string | string[] | undefined): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

// No try/catch — an unreachable core API throws into error.tsx, same as review.
export default async function RadarPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const params = await searchParams;
  const topicId = parseTopicId(params.topicId);
  const source = parseSource(params.source);

  const [signals, topics] = await Promise.all([
    listSignals({
      topicId: topicId ?? undefined,
      source: source ?? undefined,
    }),
    listTopics(),
  ]);

  // The stat tiles describe the topic you're watching, not the source chip you
  // happen to have clicked — "Sources active: 1" is a tautology, not a statistic.
  // Without a source filter the two lists are the same fetch.
  const statsSignals = source
    ? await listSignals({ topicId: topicId ?? undefined })
    : signals;

  return (
    <RadarView
      signals={signals}
      statsSignals={statsSignals}
      topics={topics}
      topicId={topicId}
      source={source}
      now={Date.now()}
    />
  );
}
