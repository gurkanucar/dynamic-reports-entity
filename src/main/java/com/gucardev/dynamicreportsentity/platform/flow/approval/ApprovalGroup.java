package com.gucardev.dynamicreportsentity.platform.flow.approval;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * People who may decide approval steps, e.g. "HR managers". Managed in the application for now; a directory sync
 * (LDAP, Keycloak) can fill the same table later without changing how approvals work.
 */
@Entity
@Table(name = "approval_group", uniqueConstraints = @UniqueConstraint(name = "uk_approval_group_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalGroup {

    /** A member; {@code email} (optional) receives the notifications. */
    @Embeddable
    public record Member(@Column(name = "username", nullable = false, length = 100) String username,
                         @Column(name = "email", length = 254) String email) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approval_group_member", joinColumns = @JoinColumn(name = "group_id"))
    private List<Member> members = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 100)
    private String updatedBy;

    public ApprovalGroup(Instant now) {
        this.createdAt = now;
    }

    public void define(String name, String description, List<Member> members, String editor, Instant now) {
        this.name = name;
        this.description = description;
        this.members.clear();
        this.members.addAll(members);
        this.updatedBy = editor;
        this.updatedAt = now;
    }

    public boolean hasMember(String username) {
        return members.stream().anyMatch(member -> member.username().equals(username));
    }
}
