package com.sashplatonov.earnit.kids.exception;

import com.sashplatonov.earnit.kids.family.api.response.ImportValidationErrorResponse;

public class ImportValidationException extends RuntimeException {

    private final ImportValidationErrorResponse response;

    public ImportValidationException(ImportValidationErrorResponse response) {
        super(response.detail());
        this.response = response;
    }

    public ImportValidationErrorResponse response() {
        return response;
    }
}
