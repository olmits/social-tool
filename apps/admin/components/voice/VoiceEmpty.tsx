"use client";

import { MessageSquareQuote, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * The second thing a new install sees, after Topics. Drafting needs a voice —
 * the Draft button on the radar stays disabled until one exists — so this says
 * that outright rather than leaving that button looking broken.
 */
export function VoiceEmpty({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-border px-6 py-12 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-muted">
        <MessageSquareQuote
          className="size-6 text-muted-foreground"
          strokeWidth={1.8}
        />
      </div>
      <h2 className="mb-1.5 text-[15px] font-semibold tracking-tight">
        No voices yet
      </h2>
      <p className="mb-5 max-w-[440px] text-[13px] text-muted-foreground">
        Drafting from a signal needs a voice to write in, so the radar’s Draft
        button stays off until there is one. Describe how you write — or paste a
        couple of your own posts — and it becomes the prompt.
      </p>
      <Button onClick={onCreate}>
        <Plus className="size-4" />
        New voice
      </Button>
    </div>
  );
}
