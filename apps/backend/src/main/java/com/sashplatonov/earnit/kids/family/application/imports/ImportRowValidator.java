package com.sashplatonov.earnit.kids.family.application.imports;

import com.sashplatonov.earnit.kids.family.api.response.ImportValidationErrorItem;

import java.util.List;

@FunctionalInterface
public interface ImportRowValidator<T> {

    void validate(List<T> rows, List<ImportValidationErrorItem> errors);
}
