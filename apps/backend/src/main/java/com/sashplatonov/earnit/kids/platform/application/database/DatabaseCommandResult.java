package com.sashplatonov.earnit.kids.platform.application.database;

public record DatabaseCommandResult(int exitCode, String stdout, String stderr) {
}
