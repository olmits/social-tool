"use client";

import { useRouter } from "next/navigation";
import { ConnectAccountForm } from "./ConnectAccountForm";

/**
 * Full-page connect form — the hard-navigation / refresh / deep-link target for
 * `/accounts/connect` (the intercepting route shows the modal instead on soft
 * nav). Closing or finishing returns to the workspace home.
 */
export function ConnectStandalone() {
  const router = useRouter();

  return (
    <div
      className="flex min-h-full w-full items-center justify-center p-6"
      style={{
        backgroundImage:
          "radial-gradient(rgba(128,128,128,0.15) 1px, transparent 1px)",
        backgroundSize: "22px 22px",
      }}
    >
      <div className="w-full max-w-[440px]">
        <div className="rounded-2xl border border-border bg-background p-6 shadow-sm">
          <div className="mb-4 flex flex-col gap-1">
            <h1 className="text-lg font-semibold tracking-tight">
              Connect account
            </h1>
            <p className="text-[13px] text-muted-foreground">
              Link a social account to start drafting and scheduling posts.
            </p>
          </div>
          <ConnectAccountForm onClose={() => router.push("/radar")} />
        </div>
      </div>
    </div>
  );
}
