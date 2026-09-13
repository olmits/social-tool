import { TriangleAlert } from "lucide-react";

/** Inline banner for a form-level (server) error. Renders nothing when empty. */
export function FormError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <div className="flex items-start gap-1.5 rounded-lg border border-red-200 bg-red-50 px-2.5 py-2 text-[12px] text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-400">
      <TriangleAlert className="mt-px size-3.5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}
