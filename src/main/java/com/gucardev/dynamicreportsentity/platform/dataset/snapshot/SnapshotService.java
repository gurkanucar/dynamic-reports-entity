package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.dataset.query.InvalidDatasetException;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetExecutor;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetQuery;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetResult;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetProperties;
import com.gucardev.dynamicreportsentity.platform.dataset.query.ResultColumn;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Saved datasets ("snapshots") that flows and other consumers read by name. Every user may save LIVE datasets and
 * change or delete their own; SNAPSHOT mode (computed as the system, every row) is for {@value #ADMIN} only.
 * <ul>
 *   <li><b>LIVE</b>: the saved definition runs on every read, as the reader – exactly like
 *       {@code /api/datasets/run}, with the reader's field and row permissions.</li>
 *   <li><b>SNAPSHOT</b>: the engine computes the dataset once (as the system: every row) into a snapshot
 *       table, every {@code refreshEveryHours} hours or on demand; reads are a simple paged SELECT.</li>
 * </ul>
 * <b>Snapshot security.</b> A snapshot holds every row, so it is only served to readers for whom the dataset
 * would be the same: allowed to use every field, and not narrowed by any row-level policy (e.g. users with
 * ALL_DEPARTMENTS, service accounts of flows). Everybody else gets 403 and can use a LIVE snapshot.
 * Checked on every read by building the dataset for the reader and for the system and comparing the SQL –
 * any row restriction changes it.
 * <p>
 * <b>Several app instances.</b> Every instance runs the scheduler; the database decides who refreshes:
 * a refresh starts only after an atomic {@link SnapshotRepository#claim claim} of the snapshot
 * (lease of {@code datasets.snapshot-refresh-lease}, renewed every quarter of it while the refresh runs).
 * If an instance dies mid-refresh its lease expires and the next scheduler run anywhere takes over.
 * Edits and finishing refreshes are guarded by optimistic locking and retried. Each instance runs at most
 * {@value #REFRESH_THREADS} refreshes at a time, so refreshes cannot crowd out the interactive datasets.
 * The instances' clocks must agree to well within the lease (NTP).
 */
@Slf4j
@Service
public class SnapshotService {

    static final int REFRESH_THREADS = 2;
    static final String ADMIN = "PLATFORM_ADMIN";
    private static final int SAVE_ATTEMPTS = 5;
    private static final Duration WAIT_STEP = Duration.ofMillis(250);
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{1,39}");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<ResultColumn>> COLUMNS = new TypeReference<>() {
    };

    private final SnapshotRepository repository;
    private List<SnapshotUsage> usages = List.of();
    private final DatasetExecutor executor;
    private final SnapshotStore store;
    private final DatasetProperties properties;
    private final Clock clock;
    /** Who holds a lease: unique per running instance. */
    private final String instanceId;
    private final ExecutorService refreshPool = Executors.newFixedThreadPool(REFRESH_THREADS,
            Thread.ofPlatform().name("snapshot-refresh-", 1).daemon().factory());
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("snapshot-lease").daemon().factory());
    /** Snapshots queued or refreshing on this instance (no point queueing them twice). */
    private final Set<String> queued = ConcurrentHashMap.newKeySet();

    @Autowired
    SnapshotService(SnapshotRepository repository, DatasetExecutor executor, SnapshotStore store,
                   DatasetProperties properties, Clock clock) {
        this(repository, executor, store, properties, clock, defaultInstanceId());
    }

    /** Tests create a second instance with its own id to play another node. */
    SnapshotService(SnapshotRepository repository, DatasetExecutor executor, SnapshotStore store,
                   DatasetProperties properties, Clock clock, String instanceId) {
        this.repository = repository;
        this.executor = executor;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.instanceId = instanceId;
    }

    private static String defaultInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown-host";
        }
        return host + "/" + ProcessHandle.current().pid() + "/" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Autowired(required = false)
    void setUsages(List<SnapshotUsage> usages) {
        this.usages = List.copyOf(usages);
    }

    @PreDestroy
    void shutdown() {
        refreshPool.shutdownNow();
        heartbeat.shutdownNow();
    }

    // ---- definitions ------------------------------------------------------------------------

    public List<SnapshotInfo> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::info).toList();
    }

    public SnapshotInfo get(String name) {
        return info(load(name));
    }

    /** A SNAPSHOT snapshot is built right away on this instance (or by the next scheduler run anywhere). */
    public SnapshotInfo create(SnapshotRequest request, Actor editor) {
        String name = request.name();
        if (name == null || !NAME.matcher(name).matches()) {
            throw new InvalidDatasetException("Snapshot name must be 2-40 lower case letters, digits or underscores, "
                    + "starting with a letter (e.g. department_headcount)");
        }
        requireModeAllowed(request, editor);
        validate(request, editor);
        if (repository.existsById(name)) {
            throw new SnapshotException(SnapshotException.Kind.CONFLICT, "Snapshot '" + name + "' already exists");
        }
        Snapshot snapshot = new Snapshot(name, editor.username(), clock.instant());
        snapshot.define(request.label(), request.description(), request.mode(), request.refreshEveryHours(),
                JSON.writeValueAsString(request.definition()), editor.username(), clock.instant());
        try {
            repository.saveAndFlush(snapshot);
        } catch (DataAccessException e) {
            // another instance created the same name in the meantime
            throw new SnapshotException(SnapshotException.Kind.CONFLICT, "Snapshot '" + name + "' already exists");
        }
        afterDefinitionChange(snapshot);
        return get(name);
    }

    public SnapshotInfo update(String name, SnapshotRequest request, Actor editor) {
        requireOwner(load(name), editor);
        requireModeAllowed(request, editor);
        validate(request, editor);
        String[] unused = new String[1];
        Snapshot saved = change(name, snapshot -> {
            snapshot.define(request.label(), request.description(), request.mode(), request.refreshEveryHours(),
                    JSON.writeValueAsString(request.definition()), editor.username(), clock.instant());
            unused[0] = request.mode() == SnapshotMode.LIVE ? snapshot.dropSnapshot() : null;
            return snapshot;
        }).orElseThrow(() -> notFound(name));
        store.drop(unused[0]);
        afterDefinitionChange(saved);
        return info(saved);
    }

    /**
     * Drops every snapshot table of the snapshot; a refresh still running elsewhere drops its own when it ends. Not
     * while something uses it (e.g. a flow step).
     */
    public void delete(String name, Actor editor) {
        requireOwner(load(name), editor);
        if (usages.stream().anyMatch(usage -> usage.usesSnapshot(name))) {
            throw new SnapshotException(SnapshotException.Kind.CONFLICT,
                    "Dataset '" + name + "' is used by a flow; change those flows first");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                repository.delete(load(name));
                break;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == SAVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
        store.dropOlderThan(name, Long.MAX_VALUE);
    }

    private static void requireModeAllowed(SnapshotRequest request, Actor editor) {
        if (request.mode() == SnapshotMode.SNAPSHOT && !editor.hasAuthority(ADMIN)) {
            throw new SnapshotException(SnapshotException.Kind.FORBIDDEN,
                    "Only admins may save SNAPSHOT datasets (they hold every row); save it as LIVE instead");
        }
    }

    private static void requireOwner(Snapshot snapshot, Actor editor) {
        if (!editor.hasAuthority(ADMIN) && !editor.username().equals(snapshot.getCreatedBy())) {
            throw new SnapshotException(SnapshotException.Kind.FORBIDDEN,
                    "Only " + snapshot.getCreatedBy() + " or an admin may change dataset '" + snapshot.getName() + "'");
        }
    }

    private void validate(SnapshotRequest request, Actor editor) {
        if (request.mode() == SnapshotMode.LIVE && request.refreshEveryHours() != null) {
            throw new InvalidDatasetException("'refreshEveryHours' only applies to SNAPSHOT snapshots");
        }
        executor.prepare(request.definition(), editor);                         // editor may use every field
        executor.prepare(request.definition(), Actor.system("snapshot")); // valid as the refresh runs it
    }

    private void afterDefinitionChange(Snapshot snapshot) {
        if (snapshot.getMode() == SnapshotMode.SNAPSHOT) {
            refreshInBackground(snapshot.getName());   // due now anyway, in case this instance goes away
        }
    }

    // ---- reading ----------------------------------------------------------------------------

    public SnapshotRows read(String name, Actor reader, int page, Integer requestedSize, boolean includeTotal) {
        Snapshot snapshot = load(name);
        DatasetDefinition definition = definition(snapshot);
        if (snapshot.getMode() == SnapshotMode.LIVE) {
            return new SnapshotRows(name, SnapshotMode.LIVE, null,
                    executor.run(definition, reader, page, requestedSize, includeTotal));
        }
        guardSnapshot(snapshot, definition, reader);
        int size = Math.clamp(requestedSize == null ? properties.defaultPageSize() : requestedSize,
                1, properties.maxPageSize());
        int pageNumber = Math.max(page, 0);
        try {
            return snapshotPage(snapshot, pageNumber, size);
        } catch (DataAccessException e) {
            // a refresh (maybe on another instance) switched to a new table and dropped this one meanwhile
            return snapshotPage(load(name), pageNumber, size);
        }
    }

    private SnapshotRows snapshotPage(Snapshot snapshot, int pageNumber, int size) {
        if (snapshot.getTableName() == null) {
            throw new SnapshotException(SnapshotException.Kind.CONFLICT, "Snapshot '" + snapshot.getName()
                    + "' has no snapshot yet (status " + snapshot.getStatus()
                    + (snapshot.getLastError() == null ? "" : ": " + snapshot.getLastError()) + ")");
        }
        List<ResultColumn> columns = JSON.readValue(snapshot.getColumnsJson(), COLUMNS);
        List<List<Object>> rows = store.page(snapshot.getTableName(), columns, size + 1, (long) pageNumber * size);
        boolean hasNext = rows.size() > size;
        List<List<Object>> pageRows = hasNext ? rows.subList(0, size) : rows;
        DatasetResult result = new DatasetResult(columns, pageRows, pageRows.size(), pageNumber, size, hasNext,
                snapshot.getRowCount(), List.of(), List.of());
        return new SnapshotRows(snapshot.getName(), SnapshotMode.SNAPSHOT, snapshot.getRefreshedAt(), result);
    }

    /** See the class comment: a snapshot is served only to readers who would get the very same dataset. */
    private void guardSnapshot(Snapshot snapshot, DatasetDefinition definition, Actor reader) {
        DatasetQuery forReader = executor.prepare(definition, reader);   // 403 for fields the reader may not use
        DatasetQuery forSystem = executor.prepare(definition, Actor.system("snapshot"));
        if (!forReader.sql().equals(forSystem.sql()) || !forReader.parameters().equals(forSystem.parameters())) {
            throw new DatasetAccessDeniedException("Snapshot '" + snapshot.getName() + "' holds every row, but your"
                    + " access is limited by row-level security. Use a LIVE snapshot or /api/datasets/run instead.");
        }
    }

    // ---- refreshing -------------------------------------------------------------------------

    /**
     * Rebuilds the snapshot now and answers when done. If a refresh is already running (on any instance),
     * waits for it and answers with its result instead of computing the same thing twice.
     */
    public SnapshotInfo refresh(String name) {
        Instant deadline = clock.instant().plus(properties.snapshotRefreshTimeout()).plus(lease());
        while (true) {
            if (claim(name)) {
                runClaimed(name, true);
                return get(name);
            }
            Snapshot snapshot = load(name);
            if (snapshot.getMode() != SnapshotMode.SNAPSHOT) {
                throw new InvalidDatasetException("Snapshot '" + name + "' is LIVE; only SNAPSHOT snapshots are refreshed");
            }
            while (snapshot.isBeingRefreshed(clock.instant())) {
                if (clock.instant().isAfter(deadline)) {
                    throw new SnapshotException(SnapshotException.Kind.CONFLICT, "Snapshot '" + name
                            + "' is still being refreshed by " + snapshot.getRefreshOwner());
                }
                sleep(WAIT_STEP);
                snapshot = load(name);
            }
            if (snapshot.getRefreshOwner() == null) {
                return info(snapshot);           // the other refresh finished: that is the fresh result
            }
            // the lease expired without being released (its instance died): loop and take over
        }
    }

    /** Called by {@link SnapshotScheduler} on every instance; the claims decide who refreshes what. */
    void refreshDue() {
        repository.findByModeAndNextRefreshAtLessThanEqual(SnapshotMode.SNAPSHOT, clock.instant())
                .forEach(snapshot -> refreshInBackground(snapshot.getName()));
    }

    private void refreshInBackground(String name) {
        if (!queued.add(name)) {
            return;
        }
        try {
            refreshPool.execute(() -> {
                try {
                    Instant now = clock.instant();
                    if (repository.claimDue(name, instanceId, now, now.plus(lease())) == 1) {
                        runClaimed(name, false);
                    }
                } catch (RuntimeException e) {
                    log.warn("Refresh of snapshot '{}' failed: {}", name, e.getMessage());
                } finally {
                    queued.remove(name);
                }
            });
        } catch (RuntimeException e) {
            queued.remove(name);   // pool shut down
        }
    }

    private boolean claim(String name) {
        Instant now = clock.instant();
        return repository.claim(name, instanceId, now, now.plus(lease())) == 1;
    }

    /** Builds the snapshot after a successful claim; the lease is renewed until the result is saved. */
    private void runClaimed(String name, boolean rethrow) {
        Snapshot snapshot = load(name);
        long version = snapshot.getSnapshotVersion();       // taken by the claim: unique for this refresh
        String table = SnapshotStore.tableName(name, version);
        long beatMillis = lease().toMillis() / 4;
        ScheduledFuture<?> beat = heartbeat.scheduleAtFixedRate(() -> {
            try {
                repository.renew(name, instanceId, clock.instant().plus(lease()));
            } catch (RuntimeException e) {
                log.warn("Could not renew the refresh lease of snapshot '{}': {}", name, e.getMessage());
            }
        }, beatMillis, beatMillis, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();
        try {
            DatasetQuery query = executor.prepare(definition(snapshot), Actor.system("snapshot " + name));
            long rows = store.build(table, query, properties.snapshotMaxRows(), properties.snapshotRefreshTimeout());
            long millis = (System.nanoTime() - start) / 1_000_000;
            String columnsJson = JSON.writeValueAsString(query.columns());
            beat.cancel(false);

            String[] previous = new String[1];
            boolean[] published = {false};
            change(name, current -> {
                if (!instanceId.equals(current.getRefreshOwner())) {
                    return current;               // lease lost to another instance: its result wins
                }
                if (current.getMode() != SnapshotMode.SNAPSHOT) {
                    current.refreshAbandoned(clock.instant());    // switched to LIVE meanwhile
                    return current;
                }
                previous[0] = current.refreshed(table, columnsJson, rows, millis, clock.instant());
                published[0] = true;
                return current;
            });
            if (published[0]) {
                store.drop(previous[0]);
                store.dropOlderThan(name, version);   // leftovers of refreshes that died with their instance
                log.info("Snapshot '{}' refreshed on {}: {} rows in {} ms", name, instanceId, rows, millis);
            } else {
                store.drop(table);                    // deleted, switched to LIVE, or taken over
            }
        } catch (RuntimeException e) {
            beat.cancel(false);
            store.drop(table);
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            change(name, current -> {
                if (instanceId.equals(current.getRefreshOwner())) {
                    current.refreshFailed(error, clock.instant());
                }
                return current;
            });
            if (rethrow) {
                throw e;
            }
            log.warn("Refresh of snapshot '{}' failed: {}", name, error);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    /**
     * Load, change, save – again from a fresh copy when another instance (or a lease renewal) saved the
     * snapshot in between. Empty if the snapshot no longer exists.
     */
    private Optional<Snapshot> change(String name, Function<Snapshot, Snapshot> change) {
        for (int attempt = 1; ; attempt++) {
            Optional<Snapshot> current = repository.findById(name);
            if (current.isEmpty()) {
                return current;
            }
            try {
                return Optional.of(repository.saveAndFlush(change.apply(current.get())));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == SAVE_ATTEMPTS) {
                    throw new SnapshotException(SnapshotException.Kind.CONFLICT,
                            "Snapshot '" + name + "' is being changed concurrently; try again");
                }
            }
        }
    }

    private Duration lease() {
        return properties.snapshotRefreshLease();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SnapshotException(SnapshotException.Kind.CONFLICT, "Interrupted while waiting for a refresh");
        }
    }

    private Snapshot load(String name) {
        return repository.findById(name).orElseThrow(() -> notFound(name));
    }

    private static SnapshotException notFound(String name) {
        return new SnapshotException(SnapshotException.Kind.NOT_FOUND, "Unknown snapshot: " + name);
    }

    private static DatasetDefinition definition(Snapshot snapshot) {
        return JSON.readValue(snapshot.getDefinitionJson(), DatasetDefinition.class);
    }

    private SnapshotInfo info(Snapshot snapshot) {
        boolean running = snapshot.isBeingRefreshed(clock.instant());
        return new SnapshotInfo(snapshot.getName(), snapshot.getLabel(), snapshot.getDescription(), snapshot.getMode(),
                snapshot.getRefreshEveryHours(), snapshot.getStatus(), snapshot.getRowCount(), snapshot.getRefreshedAt(),
                snapshot.getRefreshMillis(), snapshot.getNextRefreshAt(), running ? snapshot.getRefreshOwner() : null,
                snapshot.getLastError(), definition(snapshot), snapshot.getCreatedBy(), snapshot.getCreatedAt(),
                snapshot.getUpdatedBy(), snapshot.getUpdatedAt());
    }
}
