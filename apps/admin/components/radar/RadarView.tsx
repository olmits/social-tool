"use client";

import { useMemo, useState } from "react";
import type {
  SignalResponse,
  SignalSource,
  TopicResponse,
  VoiceProfileResponse,
} from "@/lib/api/types";
import { DraftFromSignalDialog } from "./DraftFromSignalDialog";
import { RadarEmpty } from "./RadarEmpty";
import { RadarFilters } from "./RadarFilters";
import { RadarHeader } from "./RadarHeader";
import { RadarStats } from "./RadarStats";
import { lastUpdatedLabel, radarEmptyReason, radarStats } from "./radarDisplay";
import { SignalCards } from "./SignalCards";
import { SignalTable } from "./SignalTable";
import { useDraftFromSignal } from "./useDraftFromSignal";
import { useRadarFilters } from "./useRadarFilters";

export interface RadarViewProps {
  /** Already narrowed by `topicId`/`source` server-side. */
  signals: SignalResponse[];
  /** Topic-scoped but not source-scoped, so the tiles don't collapse to one source. */
  statsSignals: SignalResponse[];
  topics: TopicResponse[];
  /** Enabled or not; the dialog filters, and the count decides the Draft button. */
  voiceProfiles: VoiceProfileResponse[];
  topicId: string | null;
  source: SignalSource | null;
  /** Request time, so the relative ages render the same on the server and after hydration. */
  now: number;
}

export function RadarView({
  signals,
  statsSignals,
  topics,
  voiceProfiles,
  topicId,
  source,
  now,
}: RadarViewProps) {
  // Mounted only while open, keyed by signal, so the dialog takes fresh state
  // rather than the previously drafted signal's.
  const [drafting, setDrafting] = useState<SignalResponse | null>(null);

  // Only for the button's enabled state and its reason; the dialog runs its own
  // instance of this hook for the call itself.
  const { disabledReason } = useDraftFromSignal(voiceProfiles);

  const {
    query,
    setQuery,
    layout,
    setLayout,
    selectTopic,
    selectSource,
    refresh,
    isPending,
  } = useRadarFilters(topicId, source);

  // The API has no text filter, so search sifts the rows already fetched. Order is
  // the server's ranking by score; filtering preserves it.
  const visible = useMemo(
    () =>
      signals.filter((signal) =>
        signal.title.toLowerCase().includes(query.trim().toLowerCase()),
      ),
    [signals, query],
  );

  return (
    <div>
      <RadarHeader
        updatedLabel={lastUpdatedLabel(signals, now)}
        onRefresh={refresh}
        pending={isPending}
      />

      <div className="max-w-[1180px] px-4 py-5.5 pb-10 md:px-7">
        <RadarStats stats={radarStats(statsSignals, now)} />

        <RadarFilters
          topics={topics}
          topicId={topicId}
          source={source}
          query={query}
          onQueryChange={setQuery}
          onTopicChange={selectTopic}
          onSourceChange={selectSource}
          layout={layout}
          onLayoutChange={setLayout}
          pending={isPending}
        />

        {visible.length === 0 ? (
          <RadarEmpty
            reason={radarEmptyReason(signals, statsSignals, topics.length)}
          />
        ) : (
          <>
            <div className="hidden md:block">
              {layout === "table" ? (
                <SignalTable
                  signals={visible}
                  now={now}
                  onDraft={setDrafting}
                  draftDisabledReason={disabledReason}
                />
              ) : (
                <SignalCards
                  signals={visible}
                  now={now}
                  onDraft={setDrafting}
                  draftDisabledReason={disabledReason}
                />
              )}
            </div>
            <div className="md:hidden">
              <SignalCards
                signals={visible}
                now={now}
                onDraft={setDrafting}
                draftDisabledReason={disabledReason}
              />
            </div>
          </>
        )}
      </div>

      {drafting && (
        <DraftFromSignalDialog
          key={drafting.id}
          signal={drafting}
          voiceProfiles={voiceProfiles}
          onClose={() => setDrafting(null)}
        />
      )}
    </div>
  );
}
