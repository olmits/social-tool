"use client";

import { useRouter } from "next/navigation";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import { ConnectAccountForm } from "./ConnectAccountForm";

/**
 * Intercepting-route modal. Mounted by `@modal/(.)accounts/connect` on soft
 * navigation, so it opens immediately and closing it (Esc, backdrop, Cancel, or
 * post-success) navigates back to the underlying page via `router.back()`.
 */
export function ConnectModal() {
  const router = useRouter();

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) router.back();
      }}
    >
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>Connect account</DialogTitle>
            <DialogDescription>
              Link a social account to start drafting and scheduling posts.
            </DialogDescription>
          </div>
          <ConnectAccountForm onClose={() => router.back()} />
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
