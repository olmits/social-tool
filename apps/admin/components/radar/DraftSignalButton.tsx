import { Button } from "@/components/ui/button";

/**
 * Turning a signal into a draft — the page's intended primary action, and not yet
 * wired: the API's `drafting/` slice is empty, and `CreateDraftCommand` carries no
 * `signalId`, so a draft could not record which signal it came from even if it
 * were written by hand (RADAR_PLAN task 15).
 *
 * Disabled with the reason on it, rather than a button that looks live and does
 * nothing when clicked.
 */
export function DraftSignalButton({ className }: { className?: string }) {
  return (
    <Button
      size="sm"
      disabled
      title="Drafting from a signal isn't wired up yet"
      className={className}
    >
      Draft
    </Button>
  );
}
