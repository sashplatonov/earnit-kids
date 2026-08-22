package com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog;

public record TaskContentCommand(
    String name,
    int coins,
    String groupName,
    String comment,
    String cueWhen,
    String cueAction,
    String icon
) {
    public TaskContentCommand(String name, int coins, String groupName, String comment,
                              String cueWhen, String cueAction) {
        this(name, coins, groupName, comment, cueWhen, cueAction, null);
    }
}
