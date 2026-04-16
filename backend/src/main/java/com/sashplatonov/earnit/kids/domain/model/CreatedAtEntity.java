package com.sashplatonov.earnit.kids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@MappedSuperclass
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class CreatedAtEntity {

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    protected Instant createdAt;
}