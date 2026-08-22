package com.sashplatonov.earnit.kids.family.domain.model.catalog;

import com.sashplatonov.earnit.kids.platform.domain.persistence.CreatedAtEntity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskEntity extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "family_id", nullable = false)
    private int familyId;

    @Column(name = "child_id", nullable = false)
    private int childId;

    @Column(name = "task_id", nullable = false)
    private long taskId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "coins")
    private int coins;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "icon")
    private String icon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frequency", columnDefinition = "jsonb")
    private JsonNode frequency;

    @Column(name = "comment")
    private String comment;

    @Column(name = "cue_when")
    private String cueWhen;

    @Column(name = "cue_action")
    private String cueAction;

    @Column(name = "money_limit")
    private Integer moneyLimit;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "source_catalog_item_id")
    private Long sourceCatalogItemId;
}
