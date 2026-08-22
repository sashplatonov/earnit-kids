package com.sashplatonov.earnit.kids.telegram.application.connection;

import com.sashplatonov.earnit.kids.telegram.api.response.ChildTelegramConnectionResponse;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface TelegramChildConnectionService {
    OperationResult<ChildTelegramConnectionResponse> connection(String familyId, int childId);

    OperationResult<TelegramLinkLaunchResponse> invite(String familyId, int childId);

    OperationResult<Void> unlink(String familyId, int childId);
}
