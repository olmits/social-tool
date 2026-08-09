"use client";

import { Checkbox as CheckboxPrimitive } from "@base-ui/react/checkbox";
import { Check } from "lucide-react";

import { cn } from "@/lib/utils";

/**
 * Token-styled wrapper over Base UI's Checkbox, rendering the box and its tick as
 * one component — unlike `Dialog`, there is no case here for composing the parts
 * separately.
 */
export function Checkbox({
  className,
  ...props
}: CheckboxPrimitive.Root.Props) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        "flex size-4.5 shrink-0 items-center justify-center rounded border border-border bg-background transition-colors",
        "focus-visible:ring-3 focus-visible:ring-ring/20 focus-visible:outline-none",
        "data-[checked]:border-primary data-[checked]:bg-primary data-[checked]:text-primary-foreground",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center">
        <Check className="size-3" strokeWidth={3} />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}
