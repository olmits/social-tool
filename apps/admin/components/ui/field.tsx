import { TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * Presentational field wrapper: label + control + help/error text. Validation is
 * owned by react-hook-form at the call site — this only renders the state it's
 * handed (pass `error` to show the message and flag the control via `aria-invalid`).
 */
export function Field({
  htmlFor,
  label,
  hint,
  error,
  children,
  className,
}: {
  htmlFor: string;
  label: string;
  /** Helper text shown when there's no error (e.g. where to find a token). */
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col", className)}>
      <label htmlFor={htmlFor} className="mb-1.5 text-[12.5px] font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p className="mt-1.5 flex items-start gap-1.5 text-[11.5px] text-destructive">
          <TriangleAlert className="mt-px size-3 shrink-0" />
          <span>{error}</span>
        </p>
      ) : hint ? (
        <p className="mt-1.5 text-[11.5px] text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
