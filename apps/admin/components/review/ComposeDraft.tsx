"use client";

import { Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { accountLabel, platformLabel } from "@/lib/api/mappers";
import { useComposeDraft } from "./useComposeDraft";

/** "New draft" button + compose dialog for the selected account. */
export function ComposeDraft() {
  const [open, setOpen] = useState(false);
  const { account, content, setContent, pending, submit } = useComposeDraft();

  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)} disabled={!account}>
        <Plus className="size-3.5" strokeWidth={2.5} />
        New draft
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogPortal>
          <DialogBackdrop />
          <DialogPopup>
            <div className="mb-4 flex flex-col gap-1">
              <DialogTitle>New draft</DialogTitle>
              <DialogDescription>
                {account
                  ? `For ${accountLabel(account)} · ${platformLabel(account.platform)}`
                  : "Select an account first."}
              </DialogDescription>
            </div>
            <Textarea
              value={content}
              onChange={(event) => setContent(event.target.value)}
              rows={6}
              placeholder="What do you want to post?"
            />
            <div className="mt-5 flex justify-end gap-2.5">
              <Button variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button
                onClick={() => submit(() => setOpen(false))}
                disabled={pending || !content.trim()}
              >
                Create draft
              </Button>
            </div>
          </DialogPopup>
        </DialogPortal>
      </Dialog>
    </>
  );
}
