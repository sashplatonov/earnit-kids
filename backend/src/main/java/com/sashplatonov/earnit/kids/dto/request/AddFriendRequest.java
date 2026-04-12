package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Positive;

public record AddFriendRequest(
    @Positive(message = "Friend id must be a positive number")
    int friendId
) { }