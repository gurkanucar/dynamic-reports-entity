package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface RunArtifactRepository extends JpaRepository<RunArtifact, UUID> {

    List<RunArtifact> findByRunIdOrderById(UUID runId);

    List<RunArtifact> findByStepRunIdAndDeletedAtIsNull(UUID stepRunId);

    /**
     * Expired files not deleted yet, locked for this transaction; rows another instance is deleting right now are
     * skipped, so instances share the cleanup without waiting for each other.
     */
    @Query(value = """
            SELECT * FROM flow_artifact
             WHERE deleted_at IS NULL AND expires_at < :now
             ORDER BY expires_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<RunArtifact> lockExpired(@Param("now") Instant now, @Param("limit") int limit);
}
