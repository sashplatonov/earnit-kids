package com.sashplatonov.earnit.kids.repository.command;

public record TaskContentCommand(
    String name,
    int coins,
    String groupName,
    String comment,
    String cueWhen,
    String cueAction,
    String icon
) {
    // EXPLAIN: Convenience constructor preserving the pre-icon call signature.
    public TaskContentCommand(String name, int coins, String groupName, String comment,
                              String cueWhen, String cueAction) {
        this(name, coins, groupName, comment, cueWhen, cueAction, null);
    }
}
