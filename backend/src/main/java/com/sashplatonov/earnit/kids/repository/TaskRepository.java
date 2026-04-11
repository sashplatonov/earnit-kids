package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TaskRepository implements PanacheRepositoryBase<TaskEntity, Long> { }
