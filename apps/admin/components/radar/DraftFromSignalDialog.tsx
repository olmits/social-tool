"use client";

import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { accountLabel, charLimit } from "@/lib/api/mappers";
import type { SignalResponse, VoiceProfileResponse } from "@/lib/api/types";
import { useDraftFromSignal } from "./useDraftFromSignal";
import { VoicePicker } from "./VoicePicker";

/**
 * Confirms what a draft will be written from, then writes it.
 *
 * Deliberately a dialog rather than a one-click action on the card: the voice is
 * a real choice, and the call is slow and costs money, so a step that shows what
 * is about to happen is worth the extra click.
 *
 * Mount only while open, keyed by signal, so the voice selection resets rather
 * than carrying over from the last signal.
 */
export function DraftFromSignalDialog({
  signal,
  voiceProfiles,
  onClose,
}: {
  signal: SignalResponse;
  voiceProfiles: VoiceProfileResponse[];
  onClose: () => void;
}) {
  const { account, usableVoices, voiceId, setVoiceId, pending, generate } =
    useDraftFromSignal(voiceProfiles);

  return (
    <Dialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>Draft a post</DialogTitle>
            <DialogDescription>
              Claude reads the linked article and writes a post in the voice you
              pick. It lands in Review as a draft — nothing is published.
            </DialogDescription>
          </div>

          <div className="flex flex-col gap-4">
            <div className="rounded-lg border border-border bg-muted/30 px-3.5 py-3">
              <div className="text-[13.5px] font-semibold leading-snug">
                {signal.title}
              </div>
              <a
                href={signal.url}
                target="_blank"
                rel="noreferrer noopener"
                className="mt-1 block truncate font-mono text-[11.5px] text-muted-foreground hover:underline"
              >
                {signal.url}
              </a>
            </div>

            {account && (
              <Field
                htmlFor="draft-account"
                label="Account"
                hint={`Posts here are capped at ${charLimit(account.platform)} characters.`}
              >
                <div
                  id="draft-account"
                  className="flex h-9 items-center rounded-lg border border-border bg-muted/40 px-2.5 text-[13px] text-muted-foreground"
                >
                  {accountLabel(account)}
                </div>
              </Field>
            )}

            <Field
              htmlFor="draft-voice"
              label="Voice"
              hint="Switch accounts or voices on their own screens."
            >
              <VoicePicker
                profiles={usableVoices}
                value={voiceId}
                onChange={setVoiceId}
                disabled={pending}
              />
            </Field>
          </div>

          <div className="mt-5 flex items-center justify-end gap-2.5">
            {pending && (
              <span className="mr-auto text-[11.5px] text-muted-foreground">
                Reading the article and writing — this takes a moment.
              </span>
            )}
            <Button variant="ghost" onClick={onClose} disabled={pending}>
              Cancel
            </Button>
            <Button
              onClick={() => generate(signal, onClose)}
              disabled={pending || !account}
            >
              {pending && <Loader2 className="size-4 animate-spin" />}
              Write draft
            </Button>
          </div>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
