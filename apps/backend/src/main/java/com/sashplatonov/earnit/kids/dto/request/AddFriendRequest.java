package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Positive;

public record AddFriendRequest(
    @Positive(message = "{validation.friend.id.positive}")
    int friendId
) { }
