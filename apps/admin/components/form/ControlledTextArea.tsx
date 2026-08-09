"use client";

import {
  type Control,
  type FieldPath,
  type FieldValues,
  type RegisterOptions,
  useController,
} from "react-hook-form";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";

/** Multi-line counterpart to {@link ControlledTextField}, on the same binding. */
export function ControlledTextArea<
  TFieldValues extends FieldValues,
  Name extends FieldPath<TFieldValues>,
>({
  control,
  name,
  label,
  hint,
  placeholder,
  rows = 6,
  rules,
}: {
  control: Control<TFieldValues>;
  name: Name;
  label: string;
  hint?: string;
  placeholder?: string;
  rows?: number;
  rules?: RegisterOptions<TFieldValues, Name>;
}) {
  const { field, fieldState } = useController({ control, name, rules });

  return (
    <Field
      htmlFor={name}
      label={label}
      hint={hint}
      error={fieldState.error?.message}
    >
      <Textarea
        id={name}
        rows={rows}
        placeholder={placeholder}
        aria-invalid={fieldState.error ? true : undefined}
        {...field}
        value={field.value ?? ""}
      />
    </Field>
  );
}
