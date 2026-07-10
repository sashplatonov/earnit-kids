package com.sashplatonov.earnit.kids.service.database;

public record DatabaseCommandResult(int exitCode, String stdout, String stderr) {
}
