package com.sashplatonov.earnit.kids.repository.command;

public record TaskContentCommand(
    String name,
    int coins,
    String groupName,
    String comment,
    String cueWhen,
    String cueAction
) {
}
