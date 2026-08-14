package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.TelegramAccountConnectionResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface TelegramAccountConnectionService {
    OperationResult<TelegramAccountConnectionResponse> connection(String familyId, String email);

    OperationResult<TelegramLinkLaunchResponse> start(String familyId, String email);

    OperationResult<Void> complete(String token, String initData);

    OperationResult<Void> unlink(String familyId, String email);
}
