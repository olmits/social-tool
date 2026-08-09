"use client";

import {
  type Control,
  type FieldPath,
  type FieldValues,
  useController,
} from "react-hook-form";
import { Checkbox } from "@/components/ui/checkbox";

/**
 * A checkbox bound through `useController`. Unlike the text fields it renders its
 * own inline label rather than going through `Field`, whose label sits above the
 * control — wrong shape for a tickbox.
 */
export function ControlledCheckbox<
  TFieldValues extends FieldValues,
  Name extends FieldPath<TFieldValues>,
>({
  control,
  name,
  label,
  hint,
}: {
  control: Control<TFieldValues>;
  name: Name;
  label: string;
  hint?: string;
}) {
  const { field } = useController({ control, name });

  return (
    <div className="flex items-start gap-2.5">
      <Checkbox
        id={name}
        checked={Boolean(field.value)}
        onCheckedChange={field.onChange}
        onBlur={field.onBlur}
        inputRef={field.ref}
      />
      <label htmlFor={name} className="cursor-pointer select-none">
        <span className="block text-[12.5px] font-medium">{label}</span>
        {hint && (
          <span className="block text-xs text-muted-foreground">{hint}</span>
        )}
      </label>
    </div>
  );
}
