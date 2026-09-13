"use client";

import { Switch as SwitchPrimitive } from "@base-ui/react/switch";

import { cn } from "@/lib/utils";

/**
 * Token-styled wrapper over Base UI's Switch, rendering the track and its thumb as
 * one component — like `Checkbox`, there is no case for composing the parts
 * separately.
 */
export function Switch({ className, ...props }: SwitchPrimitive.Root.Props) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        "flex h-5 w-9 shrink-0 rounded-full border border-border bg-muted p-0.5 transition-colors",
        "focus-visible:ring-3 focus-visible:ring-ring/20 focus-visible:outline-none",
        "data-[checked]:border-primary data-[checked]:bg-primary",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="size-3.5 rounded-full bg-background shadow-sm transition-transform data-[checked]:translate-x-4" />
    </SwitchPrimitive.Root>
  );
}
