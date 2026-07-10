package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationLogsResponse(
    List<ApplicationLogEntry> logs
) {
    public ApplicationLogsResponse {
        logs = logs == null ? List.of() : List.copyOf(logs);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationLogEntry(
        String ts,
        String level,
        String msg,
        String module,
        String reqId
    ) { }
}
