package com.sashplatonov.earnit.kids.service;

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
