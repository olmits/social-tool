"use client";

import { useEffect, useRef, useState } from "react";
import {
  Dialog,
  DialogBackdrop,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import type { DraftResponse } from "@/lib/api/types";
import { DetailHeader } from "./DetailHeader";
import { DraftDetail } from "./DraftDetail";
import { DraftDetailActions } from "./DraftDetailActions";

interface DraftDetailPanelProps {
  /** The open draft, or null when nothing is selected. */
  draft: DraftResponse | null;
  /** Display label for the draft's account (resolved by the caller). */
  accountName: string;
  /** Bumped by the caller on each selection; opens the mobile dialog. */
  selectSignal: number;
  onApprove?: () => void;
  onSchedule?: () => void;
  onSaveEdits?: () => void;
  onDiscard?: () => void;
  /** Disables the action buttons while a mutation is in flight. */
  pending?: boolean;
}

/**
 * The draft detail view. On desktop it's an always-visible side pane; on mobile
 * it's a dialog opened from the list. The panel owns the mobile open/close state
 * — the caller only reports selections via `selectSignal`.
 */
export function DraftDetailPanel({
  draft,
  accountName,
  selectSignal,
  onApprove,
  onSchedule,
  onSaveEdits,
  onDiscard,
  pending,
}: DraftDetailPanelProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const mounted = useRef(false);

  // Open the dialog on each selection, but only on mobile — desktop shows the
  // inline pane. Keyed on selectSignal so re-selecting the same draft reopens it.
  // biome-ignore lint/correctness/useExhaustiveDependencies: keyed on the selection signal; draft is read fresh.
  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      return;
    }
    if (draft && window.matchMedia("(max-width: 767px)").matches) {
      setMobileOpen(true);
    }
  }, [selectSignal]);

  return (
    <>
      {/* Desktop: always-visible side pane. */}
      <div className="hidden flex-1 overflow-auto bg-muted/30 md:block">
        {draft ? (
          <div className="mx-auto max-w-[720px] px-8 py-6.5 pb-11">
            <div className="mb-4.5 flex items-center gap-3">
              <DetailHeader draft={draft} accountName={accountName} />
            </div>

            <DraftDetail draft={draft} />

            <DraftDetailActions
              onApprove={onApprove}
              onSchedule={onSchedule}
              onSaveEdits={onSaveEdits}
              onDiscard={onDiscard}
              pending={pending}
            />
          </div>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
            Select a draft to review.
          </div>
        )}
      </div>

      {/* Mobile: dialog opened from the list. */}
      <Dialog open={mobileOpen} onOpenChange={setMobileOpen}>
        <DialogPortal>
          <DialogBackdrop />
          <DialogPopup className="max-h-[85vh] overflow-auto">
            {draft && (
              <>
                <DialogTitle className="sr-only">{accountName}</DialogTitle>
                <div className="mb-4 flex items-center gap-3">
                  <DetailHeader draft={draft} accountName={accountName} />
                </div>

                <DraftDetail draft={draft} />

                <DraftDetailActions
                  onApprove={onApprove}
                  onSchedule={onSchedule}
                  onSaveEdits={onSaveEdits}
                  onDiscard={onDiscard}
                  pending={pending}
                />
              </>
            )}
          </DialogPopup>
        </DialogPortal>
      </Dialog>
    </>
  );
}
