package com.gucardev.dynamicreportsentity.personnel;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetField;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skill")
@DatasetEntity(label = "Skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    private long version;

    @DatasetField(label = "Name")
    @Column(nullable = false, unique = true)
    private String name;

    @DatasetField(label = "Category")
    private String category;

    public Skill(String name) {
        this.name = name;
    }

    public void define(String name, String category) {
        this.name = name;
        this.category = category;
    }
}
