import { forwardRef } from "react";

import { cn } from "@/lib/utils";

/**
 * Styled native `<textarea>`, the multi-line counterpart to {@link Input} and
 * styled to match it. Native (not base-ui's) so `react-hook-form` bindings plug
 * straight in; invalid styling is driven by `aria-invalid`.
 */
export const Textarea = forwardRef<
  HTMLTextAreaElement,
  React.ComponentProps<"textarea">
>(function Textarea({ className, ...props }, ref) {
  return (
    <textarea
      ref={ref}
      className={cn(
        "w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-[13.5px] outline-none transition-colors",
        "placeholder:text-muted-foreground",
        "focus:border-neutral-400 focus:ring-3 focus:ring-ring/20 dark:focus:border-neutral-500",
        "disabled:cursor-not-allowed disabled:opacity-50",
        "aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 aria-invalid:focus:border-destructive",
        className,
      )}
      {...props}
    />
  );
});
