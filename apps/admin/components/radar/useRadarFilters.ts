"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { toastManager } from "@/components/ui/toast";
import { refreshSignalsAction } from "@/lib/api/actions";
import type { SignalSource } from "@/lib/api/types";

export type RadarLayout = "table" | "cards";

/**
 * The radar's filter and refresh behaviour.
 *
 * Topic and source live in the URL, so a filtered view is linkable and the
 * narrowing happens server-side in `listSignals`. The search box does not: the
 * API has no text filter, so it sifts the rows already fetched. The current
 * values arrive as props from the server rather than through `useSearchParams`,
 * which keeps this out of a Suspense boundary it would otherwise need.
 */
export function useRadarFilters(
  topicId: string | null,
  source: SignalSource | null,
) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [query, setQuery] = useState("");
  const [layout, setLayout] = useState<RadarLayout>("table");

  const navigate = (next: {
    topicId?: string | null;
    source?: SignalSource | null;
  }) => {
    const params = new URLSearchParams();
    const nextTopic = next.topicId === undefined ? topicId : next.topicId;
    const nextSource = next.source === undefined ? source : next.source;
    if (nextTopic) params.set("topicId", nextTopic);
    if (nextSource) params.set("source", nextSource);
    const search = params.toString();
    startTransition(() => router.push(search ? `/radar?${search}` : "/radar"));
  };

  const selectTopic = (next: string | null) => navigate({ topicId: next });
  const selectSource = (next: SignalSource | null) =>
    navigate({ source: next });

  // The server action expires the signals cache, and returning from it re-renders
  // the page with what the poller has stored since. It does not start a poll.
  const refresh = () => {
    startTransition(async () => {
      const result = await refreshSignalsAction();
      if (!result.ok) {
        toastManager.add({
          title: "Couldn't refresh signals",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  return {
    query,
    setQuery,
    layout,
    setLayout,
    selectTopic,
    selectSource,
    refresh,
    isPending,
  };
}
