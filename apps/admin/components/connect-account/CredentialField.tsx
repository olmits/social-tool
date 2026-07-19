"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import {
  type Control,
  type RegisterOptions,
  useController,
} from "react-hook-form";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import type { ConnectFormValues } from "./useConnectAccountForm";

/**
 * Masked credential input (app password / token) with a show/hide toggle, bound
 * via `useController`. The value is never logged and defaults to `type=password`.
 */
export function CredentialField({
  control,
  label,
  hint,
  placeholder,
  rules,
}: {
  control: Control<ConnectFormValues>;
  label: string;
  hint?: string;
  placeholder?: string;
  rules?: RegisterOptions<ConnectFormValues, "credentialValue">;
}) {
  const { field, fieldState } = useController({
    control,
    name: "credentialValue",
    rules,
  });
  const [show, setShow] = useState(false);
  const toggle = () => setShow((v) => !v);

  return (
    <Field
      htmlFor="credential"
      label={label}
      hint={hint}
      error={fieldState.error?.message}
    >
      <div className="relative">
        <Input
          id="credential"
          type={show ? "text" : "password"}
          autoComplete="off"
          className="pr-9"
          placeholder={placeholder}
          aria-invalid={fieldState.error ? true : undefined}
          {...field}
        />
        <button
          type="button"
          onClick={toggle}
          title={show ? "Hide" : "Show"}
          className="absolute right-2 top-1/2 flex size-6 -translate-y-1/2 items-center justify-center rounded text-muted-foreground hover:text-foreground"
        >
          {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
    </Field>
  );
}
