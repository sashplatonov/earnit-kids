package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.ChildTelegramConnectionResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface TelegramChildConnectionService {
    OperationResult<ChildTelegramConnectionResponse> connection(String familyId, int childId);

    OperationResult<TelegramLinkLaunchResponse> invite(String familyId, int childId);

    OperationResult<Void> unlink(String familyId, int childId);
}
