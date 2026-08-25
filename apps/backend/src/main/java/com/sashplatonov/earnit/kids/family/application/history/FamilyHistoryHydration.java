package com.sashplatonov.earnit.kids.family.application.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Supplier;

@ApplicationScoped
public final class FamilyHistoryHydration {
  private final Supplier<ObjectMapper> objectMapper;
  private final RelatedEntityHydrator relatedEntityHydrator;

  @Inject
  public FamilyHistoryHydration(
      ObjectMapper objectMapper, RelatedEntityHydrator relatedEntityHydrator) {
    this.objectMapper = () -> objectMapper;
    this.relatedEntityHydrator = relatedEntityHydrator;
  }

  ObjectMapper objectMapper() {
    return objectMapper.get();
  }

  RelatedEntityHydrator relatedEntityHydrator() {
    return relatedEntityHydrator;
  }
}
