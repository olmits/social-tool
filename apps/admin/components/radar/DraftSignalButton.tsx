"use client";

import { Button } from "@/components/ui/button";

/**
 * Turning a signal into a draft — the page's primary action. Opens the generate
 * dialog; the actual call and its pending state live there, since generation
 * takes tens of seconds and needs somewhere to show that.
 *
 * When it can't be used, it says why on the button rather than looking live and
 * doing nothing on click. There are exactly two such reasons — no connected
 * account, and no voice to write in — and both are fixed on another screen.
 */
export function DraftSignalButton({
  onDraft,
  disabledReason,
  className,
}: {
  onDraft: () => void;
  /** Why drafting is unavailable, or null when it is available. */
  disabledReason: string | null;
  className?: string;
}) {
  return (
    <Button
      size="sm"
      onClick={onDraft}
      disabled={disabledReason !== null}
      title={disabledReason ?? "Draft a post from this signal"}
      className={className}
    >
      Draft
    </Button>
  );
}
