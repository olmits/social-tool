"use client";

import { Toast } from "@base-ui/react/toast";
import { CheckCircle2, X } from "lucide-react";

/**
 * Global toast manager. Lives outside React so `toastManager.add(...)` can be
 * called from a component that unmounts as it fires (the connect form navigates
 * the modal closed on success) while the toast still renders in the persistent
 * layout-level {@link ToastProvider}.
 */
export const toastManager = Toast.createToastManager();

function ToastList() {
  const { toasts } = Toast.useToastManager();
  return toasts.map((toast) => (
    <Toast.Root
      key={toast.id}
      toast={toast}
      className="flex items-start gap-3 rounded-xl border border-border bg-popover p-3 shadow-lg transition-[opacity,transform] duration-200 data-[starting-style]:translate-y-2 data-[starting-style]:opacity-0 data-[ending-style]:opacity-0"
    >
      <CheckCircle2 className="mt-px size-4 shrink-0 text-emerald-600 dark:text-emerald-500" />
      <Toast.Content className="flex min-w-0 flex-1 flex-col gap-0.5">
        <Toast.Title className="text-[13px] font-semibold" />
        <Toast.Description className="text-[12px] text-muted-foreground" />
      </Toast.Content>
      <Toast.Close
        aria-label="Dismiss"
        className="flex size-5 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
      >
        <X className="size-3.5" />
      </Toast.Close>
    </Toast.Root>
  ));
}

/** Wraps the app so toasts fired from anywhere render in one fixed viewport. */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  return (
    <Toast.Provider toastManager={toastManager}>
      {children}
      <Toast.Portal>
        <Toast.Viewport className="fixed bottom-4 right-4 z-[60] flex w-[340px] max-w-[calc(100vw-2rem)] flex-col gap-2">
          <ToastList />
        </Toast.Viewport>
      </Toast.Portal>
    </Toast.Provider>
  );
}
