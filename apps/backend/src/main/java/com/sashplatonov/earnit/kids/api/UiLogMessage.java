package com.sashplatonov.earnit.kids.api;

// EXPLAIN: Simple DTO for UI log messages sent from the frontend.
// EXPLAIN: The frontend sends JSON with two fields:
// EXPLAIN:   - level: one of "debug", "info", "warn", "error"
// EXPLAIN:   - message: arbitrary string
public class UiLogMessage {
    // EXPLAIN: Level of the log message (debug, info, warn, error).
    public String level;
    // EXPLAIN: The actual log message content.
    public String message;
}
