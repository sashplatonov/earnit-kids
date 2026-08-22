package com.sashplatonov.earnit.kids.family.application.dashboard;

record FamilyDashboardHistoryDetails(
    String title,
    String description,
    Long taskId,
    String taskName,
    Long itemId,
    String itemName,
    String groupName,
    String comment
) { }
