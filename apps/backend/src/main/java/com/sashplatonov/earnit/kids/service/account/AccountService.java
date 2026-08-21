package com.sashplatonov.earnit.kids.service.account;

import com.sashplatonov.earnit.kids.dto.response.AccountConnectionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface AccountService {
    OperationResult<AccountConnectionResponse> connection(String familyId, String email);
    default OperationResult<AccountConnectionResponse> connectionByParentId(String familyId, Integer parentAccountId,
                                                                              String legacyEmail) {
        return connection(familyId, legacyEmail);
    }

    OperationResult<Void> changeEmail(String familyId, String currentEmail, String newEmail);

    OperationResult<Void> unlinkEmail(String familyId, String email);
}
