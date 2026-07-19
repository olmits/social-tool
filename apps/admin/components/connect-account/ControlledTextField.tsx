"use client";

import {
  type Control,
  type FieldPath,
  type RegisterOptions,
  useController,
} from "react-hook-form";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import type { ConnectFormValues } from "./useConnectAccountForm";

/**
 * A single text input bound to react-hook-form via `useController` (the preferred
 * binding over `register`). Renders the label/error chrome through `Field`.
 */
export function ControlledTextField<Name extends FieldPath<ConnectFormValues>>({
  control,
  name,
  label,
  hint,
  placeholder,
  rules,
}: {
  control: Control<ConnectFormValues>;
  name: Name;
  label: string;
  hint?: string;
  placeholder?: string;
  rules?: RegisterOptions<ConnectFormValues, Name>;
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
      />
    </Field>
  );
}
