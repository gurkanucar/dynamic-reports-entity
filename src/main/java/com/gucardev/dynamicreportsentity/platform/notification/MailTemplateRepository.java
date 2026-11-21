package com.gucardev.dynamicreportsentity.platform.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    boolean existsByNameIgnoreCase(String name);

    /** {@code q}: a LIKE pattern over name, subject and description; null = all. */
    @Query("""
            select t from MailTemplate t
             where :q is null or lower(t.name) like :q escape '\\' or lower(t.subject) like :q escape '\\'
                or lower(t.description) like :q escape '\\'""")
    Page<MailTemplate> search(@Param("q") String q, Pageable pageable);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
