package com.sashplatonov.earnit.kids.service;

public record DatabaseCommandResult(int exitCode, String stdout, String stderr) {
}
