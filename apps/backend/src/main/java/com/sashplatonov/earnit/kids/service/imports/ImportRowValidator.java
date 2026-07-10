package com.sashplatonov.earnit.kids.service.imports;

import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorItem;

import java.util.List;

@FunctionalInterface
public interface ImportRowValidator<T> {

    void validate(List<T> rows, List<ImportValidationErrorItem> errors);
}
