package com.sashplatonov.earnit.kids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "children")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildEntity extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "family_id", nullable = false)
    private Integer familyDbId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "token", unique = true)
    private String token;

    @Column(name = "balance")
    @Builder.Default
    private int balance = 0;

    @Column(name = "monthly_limit")
    @Builder.Default
    private int monthlyLimit = 10000;

    @Column(name = "daily_coin_limit")
    @Builder.Default
    private int dailyCoinLimit = 0;

    @Column(name = "theme")
    @Builder.Default
    private String theme = "ocean";
}
