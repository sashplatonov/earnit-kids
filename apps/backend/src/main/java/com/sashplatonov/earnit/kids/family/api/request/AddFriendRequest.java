package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.Positive;

public record AddFriendRequest(
    @Positive(message = "{validation.friend.id.positive}")
    int friendId
) { }
