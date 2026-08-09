package com.omits.social_api.draft;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.AccountService;
import com.omits.social_api.account.exception.AccountNotActiveException;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.draft.dto.CreateDraftCommand;
import com.omits.social_api.draft.exception.PlatformMismatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Application service composing the {@code account} and {@code draft} slices: it resolves and
 * validates the target account before delegating to {@link DraftService#create}.
 *
 * <p>The check lives here rather than in {@link DraftService} so the draft slice keeps
 * referencing accounts by id alone and never holds an {@link Account}. This is the codebase's
 * seam for cross-slice orchestration — a domain service should not inject another slice's
 * service or repository.
 */
@Service
@RequiredArgsConstructor
public class DraftCreationService {

    private final AccountService accountService;
    private final DraftService draftService;

    /**
     * Creates a draft for an existing, connected account whose platform matches the draft's.
     *
     * @throws com.omits.social_api.account.exception.AccountNotFoundException if no such account
     * @throws AccountNotActiveException if the account has been disconnected
     * @throws PlatformMismatchException if the draft targets a different platform than the account
     */
    public Draft create(CreateDraftCommand command) {
        if (command.accountId() == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        Account account = accountService.get(command.accountId());
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
        if (account.getPlatform() != command.platform()) {
            throw new PlatformMismatchException(account.getId(), account.getPlatform(), command.platform());
        }
        return draftService.create(command.accountId(), command.platform(), command.content());
    }
}
