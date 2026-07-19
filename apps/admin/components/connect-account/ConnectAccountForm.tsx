"use client";

import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ControlledTextField } from "./ControlledTextField";
import { CredentialField } from "./CredentialField";
import { FormError } from "./FormError";
import { PlatformPicker } from "./PlatformPicker";
import { useConnectAccountForm } from "./useConnectAccountForm";

/**
 * Connect-account form, shared by the intercepted modal and the standalone
 * `/accounts/connect` page. All logic lives in {@link useConnectAccountForm};
 * this component only composes the presentational pieces.
 */
export function ConnectAccountForm({ onClose }: { onClose: () => void }) {
  const {
    control,
    platform,
    fields,
    redditTaken,
    isPlatformDisabled,
    selectPlatform,
    rules,
    onSubmit,
    isPending,
    rootError,
  } = useConnectAccountForm(onClose);

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <PlatformPicker
        value={platform}
        onSelect={selectPlatform}
        isDisabled={isPlatformDisabled}
        redditTaken={redditTaken}
      />

      <ControlledTextField
        control={control}
        name="handle"
        label={fields.handleLabel}
        placeholder={fields.handlePlaceholder}
        rules={rules.handle}
      />

      {fields.needsInstance && (
        <ControlledTextField
          control={control}
          name="instance"
          label="Instance"
          placeholder={fields.instancePlaceholder}
          rules={rules.instance}
        />
      )}

      <CredentialField
        control={control}
        label={fields.credentialLabel}
        hint={fields.credentialHint}
        placeholder={fields.credentialPlaceholder}
        rules={rules.credentialValue}
      />

      <FormError message={rootError} />

      <div className="mt-1 flex justify-end gap-2">
        <Button
          type="button"
          variant="ghost"
          onClick={onClose}
          disabled={isPending}
        >
          Cancel
        </Button>
        <Button type="submit" disabled={isPending}>
          {isPending && <Loader2 className="size-4 animate-spin" />}
          Connect account
        </Button>
      </div>
    </form>
  );
}
