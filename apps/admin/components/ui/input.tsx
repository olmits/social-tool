import { forwardRef } from "react";

import { cn } from "@/lib/utils";

/**
 * Styled native `<input>`. Native (not base-ui's `Input`) so `react-hook-form`'s
 * `register()` ref/onChange/onBlur plug straight in. Invalid styling is driven by
 * `aria-invalid`, which the form sets from RHF's error state.
 */
export const Input = forwardRef<
  HTMLInputElement,
  React.ComponentProps<"input">
>(function Input({ className, ...props }, ref) {
  return (
    <input
      ref={ref}
      className={cn(
        "h-9 w-full rounded-lg border border-border bg-background px-3 text-[13.5px] outline-none transition-colors",
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
