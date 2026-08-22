package com.sashplatonov.earnit.kids.family.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportValidationErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String errorCode,
    List<ImportValidationErrorItem> errors
) {
    public ImportValidationErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ImportValidationErrorResponse of(String detail, List<ImportValidationErrorItem> errors) {
        return new ImportValidationErrorResponse(
            "urn:earnit-kids:problem:IMPORT_VALIDATION_ERROR",
            BackendMessages.statusTitle(400),
            400,
            detail,
            "IMPORT_VALIDATION_ERROR",
            errors == null ? List.of() : List.copyOf(errors)
        );
    }
}
