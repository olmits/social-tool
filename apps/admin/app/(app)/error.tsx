"use client";

import { RefreshCw, ServerCrash } from "lucide-react";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";

// Catches failures from the (app) layout's account fetch (and any page below it).
// The common case is the core API being down/unreachable. NB: Next 16 passes
// `unstable_retry` here, not the classic `reset`.
export default function AppError({
  error,
  unstable_retry,
}: {
  error: Error & { digest?: string };
  unstable_retry: () => void;
}) {
  useEffect(() => {
    console.error("App layout error:", error);
  }, [error]);

  return (
    <div className="flex h-screen w-full items-center justify-center p-6">
      <div className="flex max-w-[400px] flex-col items-center text-center">
        <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-muted">
          <ServerCrash className="size-6 text-muted-foreground" />
        </div>
        <h1 className="mb-1.5 text-lg font-semibold tracking-tight">
          Can't reach the core API
        </h1>
        <p className="mb-5 text-[13.5px] text-muted-foreground">
          The admin panel couldn't load your accounts. Make sure the core
          service is running, then try again.
        </p>
        <Button onClick={() => unstable_retry()}>
          <RefreshCw className="size-4" />
          Try again
        </Button>
      </div>
    </div>
  );
}
