package com.gucardev.dynamicreportsentity.platform.flow.core;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A flow as its owner defined it. Runs execute as the {@link #owner}: their reports see exactly what the owner
 * may see. {@link #version} is the ETag of the flow API (optimistic locking).
 */
@Entity
@Table(name = "flow_definition", indexes = @Index(name = "ix_flow_definition_owner", columnList = "owner"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 100)
    private String owner;

    /** {@link FlowDefinition} as JSON. */
    @Column(name = "definition_json", nullable = false, length = 200_000)
    private String definitionJson;

    /** Mail templates used by the steps; a template in use cannot be deleted. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flow_mail_template", joinColumns = @JoinColumn(name = "flow_id"))
    @Column(name = "template_id", nullable = false)
    private Set<Long> templateIds = new HashSet<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 100)
    private String updatedBy;

    Flow(String owner, Instant now) {
        this.owner = owner;
        this.createdAt = now;
    }

    void define(String name, String description, FlowDefinition definition, String editor, Instant now) {
        this.name = name;
        this.description = description;
        this.definitionJson = definition.toJson();
        this.templateIds.clear();
        definition.steps().stream()
                .filter(MailStep.class::isInstance)
                .map(step -> ((MailStep) step).templateId())
                .forEach(templateIds::add);
        this.updatedBy = editor;
        this.updatedAt = now;
    }

    public FlowDefinition definition() {
        return FlowDefinition.fromJson(definitionJson);
    }
}
