"use client";

import {
  type Control,
  type FieldPath,
  type FieldValues,
  type RegisterOptions,
  useController,
} from "react-hook-form";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

/**
 * A single text input bound to react-hook-form via `useController` (the preferred
 * binding over `register`). Renders the label/error chrome through `Field`.
 *
 * Generic over the form's value type so any form can use it — see
 * `connect-account/ConnectAccountForm` and `review/EditDraftDialog`.
 */
export function ControlledTextField<
  TFieldValues extends FieldValues,
  Name extends FieldPath<TFieldValues>,
>({
  control,
  name,
  label,
  hint,
  placeholder,
  rules,
}: {
  control: Control<TFieldValues>;
  name: Name;
  label: string;
  hint?: string;
  placeholder?: string;
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
      <Input
        id={name}
        autoComplete="off"
        autoCapitalize="none"
        spellCheck={false}
        placeholder={placeholder}
        aria-invalid={fieldState.error ? true : undefined}
        {...field}
        value={field.value ?? ""}
      />
    </Field>
  );
}
