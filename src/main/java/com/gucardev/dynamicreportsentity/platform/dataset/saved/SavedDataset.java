package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A dataset definition saved under a name. */
@Entity
@Table(name = "saved_dataset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SavedDataset {

    @Id
    @Column(length = 40)
    private String name;

    @Version
    private long version;

    private String label;

    @Column(length = 1000)
    private String description;

    @Column(name = "definition_json", nullable = false, length = 100_000)
    private String definitionJson;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt;

    SavedDataset(String name, String owner, Instant now) {
        this.name = name;
        this.createdBy = owner;
        this.createdAt = now;
    }

    void define(String label, String description, String definitionJson, String editor, Instant now) {
        this.label = label;
        this.description = description;
        this.definitionJson = definitionJson;
        this.updatedBy = editor;
        this.updatedAt = now;
    }
}
