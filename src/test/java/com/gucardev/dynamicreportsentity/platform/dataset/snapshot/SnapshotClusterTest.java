package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetExecutor;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Several app instances sharing one database. The Spring bean plays node A; {@link #nodeB} is a second
 * {@link SnapshotService} with its own instance id, as another JVM would have.
 */
@SpringBootTest
class SnapshotClusterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final String HEADCOUNT = """
            {"root": "Personnel",
             "columns": [{"field": "department.name"}, {"aggregate": "COUNT"}],
             "sort": [{"field": "department.name"}]}
            """;

    @Autowired SnapshotService nodeA;
    @Autowired SnapshotRepository repository;
    @Autowired DatasetExecutor executor;
    @Autowired SnapshotStore store;
    @Autowired DatasetProperties properties;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbcTemplate;

    SnapshotService nodeB;
    String name;

    @BeforeEach
    void setUp() {
        nodeB = new SnapshotService(repository, executor, store, properties, clock, "node-b");
        name = "c_cluster_" + NAMES.incrementAndGet();
        // saved directly (not through a node), so no node starts a refresh on its own
        Snapshot snapshot = new Snapshot(name, "test", Instant.now());
        snapshot.define("Cluster test", null, SnapshotMode.SNAPSHOT, null,
                JSON.writeValueAsString(JSON.readValue(HEADCOUNT, DatasetDefinition.class)), "test", Instant.now());
        repository.saveAndFlush(snapshot);
    }

    @AfterEach
    void tearDown() {
        nodeB.shutdown();
    }

    private boolean tableExists(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = ?",
                Integer.class, table) > 0;
    }

    @Test
    void onlyOneInstanceCanClaimARefreshUntilTheLeaseExpires() {
        Instant now = Instant.now();
        assertThat(repository.claim(name, "node-x", now, now.plusSeconds(120))).isEqualTo(1);
        assertThat(repository.claim(name, "node-y", now, now.plusSeconds(120))).isZero();
        // two minutes later node-x has not renewed: it is considered dead, node-y takes over
        assertThat(repository.claim(name, "node-y", now.plusSeconds(121), now.plusSeconds(241))).isEqualTo(1);

        Snapshot snapshot = repository.findById(name).orElseThrow();
        assertThat(snapshot.getRefreshOwner()).isEqualTo("node-y");
        assertThat(snapshot.getSnapshotVersion()).isEqualTo(2);   // each claim its own snapshot table
    }

    @Test
    void manualRefreshWaitsForTheInstanceAlreadyRefreshingAndReturnsItsResult() throws Exception {
        Instant now = Instant.now();
        repository.claim(name, "node-x", now, now.plusSeconds(120));

        CompletableFuture<SnapshotInfo> refresh = CompletableFuture.supplyAsync(() -> nodeB.refresh(name));
        Thread.sleep(700);
        assertThat(refresh).isNotDone();

        // node-x finishes (here: fails) and releases its lease
        Snapshot snapshot = repository.findById(name).orElseThrow();
        snapshot.refreshFailed("node-x: out of memory", Instant.now());
        repository.saveAndFlush(snapshot);

        SnapshotInfo info = refresh.get(10, TimeUnit.SECONDS);
        assertThat(info.status()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(info.lastError()).contains("node-x");
        assertThat(repository.findById(name).orElseThrow().getSnapshotVersion()).isEqualTo(1);   // not built twice
    }

    @Test
    void anotherInstanceTakesOverWhenTheRefreshingOneDiedAndCleansUpAfterIt() {
        // node-x claimed, created its table and died: its lease ran out a second ago
        Instant now = Instant.now();
        repository.claim(name, "node-x", now.minusSeconds(180), now.minusSeconds(1));
        jdbcTemplate.execute("CREATE TABLE rds_" + name + "_1 (row_no BIGINT)");

        SnapshotInfo info = nodeB.refresh(name);

        assertThat(info.status()).isEqualTo(SnapshotStatus.READY);
        assertThat(info.rowCount()).isEqualTo(7);
        assertThat(info.refreshingOn()).isNull();
        Snapshot snapshot = repository.findById(name).orElseThrow();
        assertThat(snapshot.getTableName()).isEqualTo("rds_" + name + "_2");
        assertThat(tableExists("rds_" + name + "_2")).isTrue();
        assertThat(tableExists("rds_" + name + "_1")).isFalse();   // dead instance's leftover dropped
    }

    @Test
    void aDueSnapshotIsRefreshedByOneInstanceOnlyWhenAllSchedulersPickItUp() {
        nodeA.refreshDue();
        nodeB.refreshDue();

        await(() -> repository.findById(name).orElseThrow().getStatus() == SnapshotStatus.READY);
        await(() -> !repository.findById(name).orElseThrow().isBeingRefreshed(Instant.now()));
        sleep(500);   // give the slower node time to try (and skip: no longer due)

        Snapshot snapshot = repository.findById(name).orElseThrow();
        assertThat(snapshot.getSnapshotVersion()).isEqualTo(1);
        assertThat(snapshot.getNextRefreshAt()).isNull();   // no refreshEveryHours: on demand only from now on
        assertThat(snapshot.getRowCount()).isEqualTo(7);
    }

    @Test
    void aManualRefreshWhoseInstanceDiedIsCompletedByTheSchedulerOfAnother() {
        // refreshed once, then on demand only (not due any more)
        nodeA.refresh(name);
        assertThat(repository.findById(name).orElseThrow().getNextRefreshAt()).isNull();

        // node-x starts a manual refresh and dies; its lease has expired
        Instant now = Instant.now();
        repository.claim(name, "node-x", now.minusSeconds(180), now.minusSeconds(1));

        nodeB.refreshDue();
        await(() -> repository.findById(name).orElseThrow().getStatus() == SnapshotStatus.READY
                && repository.findById(name).orElseThrow().getSnapshotVersion() == 3);
        assertThat(repository.findById(name).orElseThrow().getTableName()).isEqualTo("rds_" + name + "_3");
    }

    @Test
    void aStaleCopyCannotOverwriteANewerSave() {
        Snapshot stale = repository.findById(name).orElseThrow();
        Instant now = Instant.now();
        repository.claim(name, "node-x", now, now.plusSeconds(120));    // another instance saves meanwhile

        stale.refreshFailed("would wipe node-x's lease", now);
        assertThatThrownBy(() -> repository.saveAndFlush(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(repository.findById(name).orElseThrow().getRefreshOwner()).isEqualTo("node-x");
    }

    @Test
    void deletingDropsEveryTableOfTheSnapshot() {
        nodeA.refresh(name);
        String table = repository.findById(name).orElseThrow().getTableName();
        jdbcTemplate.execute("CREATE TABLE rds_" + name + "_77 (row_no BIGINT)");   // e.g. a dead refresh

        nodeB.delete(name, Actor.system("test"));

        assertThat(repository.existsById(name)).isFalse();
        assertThat(tableExists(table)).isFalse();
        assertThat(tableExists("rds_" + name + "_77")).isFalse();
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition within 15 s").isLessThan(deadline);
            sleep(50);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
