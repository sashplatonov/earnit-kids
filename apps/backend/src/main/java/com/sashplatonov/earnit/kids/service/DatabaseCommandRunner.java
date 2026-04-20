package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class DatabaseCommandRunner {

    public DatabaseCommandResult run(List<String> command, String password) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PGPASSWORD", password);
        Process process = builder.start();
        int exitCode = process.waitFor();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new DatabaseCommandResult(exitCode, stderr);
    }
}
