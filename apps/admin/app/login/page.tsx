"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";

export default function LoginPage() {
  const router = useRouter();

  return (
    <div
      className="flex h-screen w-full items-center justify-center bg-muted/40"
      style={{
        backgroundImage:
          "radial-gradient(rgba(128,128,128,0.2) 1px, transparent 1px)",
        backgroundSize: "22px 22px",
      }}
    >
      <div className="w-full max-w-[392px] px-5">
        <div className="mb-6 flex items-center gap-2.5">
          <div className="flex size-8 items-center justify-center rounded-lg bg-primary">
            <div className="size-2.5 rounded-full border-2 border-primary-foreground" />
          </div>
          <span className="text-[19px] font-semibold tracking-tight">
            Cadence
          </span>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            router.push("/radar");
          }}
          className="rounded-2xl border border-border bg-background p-7 shadow-sm"
        >
          <h1 className="mb-1 text-xl font-semibold tracking-tight">Sign in</h1>
          <p className="mb-6 text-[13.5px] text-muted-foreground">
            Admin access · single user
          </p>

          <label
            className="mb-1.5 block text-[12.5px] font-medium"
            htmlFor="email"
          >
            Email
          </label>
          <input
            id="email"
            type="email"
            defaultValue="maya@cadence.internal"
            className="mb-4 h-9 w-full rounded-lg border border-border bg-background px-3 text-[13.5px] outline-none focus:border-neutral-400 focus:ring-3 focus:ring-ring/20 dark:focus:border-neutral-500"
          />

          <label
            className="mb-1.5 block text-[12.5px] font-medium"
            htmlFor="password"
          >
            Password
          </label>
          <input
            id="password"
            type="password"
            defaultValue="cadence-admin"
            className="mb-5 h-9 w-full rounded-lg border border-border bg-background px-3 text-[13.5px] outline-none focus:border-neutral-400 focus:ring-3 focus:ring-ring/20 dark:focus:border-neutral-500"
          />

          <Button type="submit" className="w-full">
            Sign in
          </Button>
        </form>

        <p className="mt-5 text-center font-mono text-xs text-muted-foreground">
          cadence · internal publishing tool
        </p>
      </div>
    </div>
  );
}
