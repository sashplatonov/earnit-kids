package com.sashplatonov.earnit.kids.service;

record FamilyDashboardRequestDetails(
    String title,
    String description,
    String groupName,
    String comment,
    String taskName,
    String itemName,
    String taskGroup,
    String itemGroup,
    String taskComment,
    String itemComment
) { }
