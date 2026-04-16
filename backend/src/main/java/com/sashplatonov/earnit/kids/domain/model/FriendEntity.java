package com.sashplatonov.earnit.kids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "friends")
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendEntity extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "child_id", nullable = false)
    private int childId;

    @Column(name = "friend_child_id", nullable = false)
    private int friendChildId;
}
