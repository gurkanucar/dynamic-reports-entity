package com.gucardev.dynamicreportsentity.platform.reporting.dataset;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportDefinition;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportExecutor;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportQuery;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportResult;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportingProperties;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ResultColumn;
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
 * Saved reports ("datasets") that flows and other consumers read by name.
 * <ul>
 *   <li><b>LIVE</b>: the saved definition runs on every read, as the reader – exactly like
 *       {@code /api/reports/run}, with the reader's field and row permissions.</li>
 *   <li><b>SNAPSHOT</b>: the engine computes the report once (as the system: every row) into a snapshot
 *       table, every {@code refreshEveryHours} hours or on demand; reads are a simple paged SELECT.</li>
 * </ul>
 * <b>Snapshot security.</b> A snapshot holds every row, so it is only served to readers for whom the report
 * would be the same: allowed to use every field, and not narrowed by any row-level policy (e.g. users with
 * ALL_DEPARTMENTS, service accounts of flows). Everybody else gets 403 and can use a LIVE dataset.
 * Checked on every read by building the report for the reader and for the system and comparing the SQL –
 * any row restriction changes it.
 * <p>
 * <b>Several app instances.</b> Every instance runs the scheduler; the database decides who refreshes:
 * a refresh starts only after an atomic {@link ReportDatasetRepository#claim claim} of the dataset
 * (lease of {@code reporting.dataset-refresh-lease}, renewed every quarter of it while the refresh runs).
 * If an instance dies mid-refresh its lease expires and the next scheduler run anywhere takes over.
 * Edits and finishing refreshes are guarded by optimistic locking and retried. Each instance runs at most
 * {@value #REFRESH_THREADS} refreshes at a time, so refreshes cannot crowd out the interactive reports.
 * The instances' clocks must agree to well within the lease (NTP).
 */
@Slf4j
@Service
public class DatasetService {

    static final int REFRESH_THREADS = 2;
    private static final int SAVE_ATTEMPTS = 5;
    private static final Duration WAIT_STEP = Duration.ofMillis(250);
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{1,39}");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<ResultColumn>> COLUMNS = new TypeReference<>() {
    };

    private final ReportDatasetRepository repository;
    private final ReportExecutor executor;
    private final SnapshotStore store;
    private final ReportingProperties properties;
    private final Clock clock;
    /** Who holds a lease: unique per running instance. */
    private final String instanceId;
    private final ExecutorService refreshPool = Executors.newFixedThreadPool(REFRESH_THREADS,
            Thread.ofPlatform().name("dataset-refresh-", 1).daemon().factory());
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("dataset-lease").daemon().factory());
    /** Datasets queued or refreshing on this instance (no point queueing them twice). */
    private final Set<String> queued = ConcurrentHashMap.newKeySet();

    @Autowired
    DatasetService(ReportDatasetRepository repository, ReportExecutor executor, SnapshotStore store,
                   ReportingProperties properties, Clock clock) {
        this(repository, executor, store, properties, clock, defaultInstanceId());
    }

    /** Tests create a second instance with its own id to play another node. */
    DatasetService(ReportDatasetRepository repository, ReportExecutor executor, SnapshotStore store,
                   ReportingProperties properties, Clock clock, String instanceId) {
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

    @PreDestroy
    void shutdown() {
        refreshPool.shutdownNow();
        heartbeat.shutdownNow();
    }

    // ---- definitions ------------------------------------------------------------------------

    public List<DatasetInfo> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::info).toList();
    }

    public DatasetInfo get(String name) {
        return info(load(name));
    }

    /** A SNAPSHOT dataset is built right away on this instance (or by the next scheduler run anywhere). */
    public DatasetInfo create(DatasetRequest request, Actor editor) {
        String name = request.name();
        if (name == null || !NAME.matcher(name).matches()) {
            throw new InvalidReportException("Dataset name must be 2-40 lower case letters, digits or underscores, "
                    + "starting with a letter (e.g. department_headcount)");
        }
        validate(request, editor);
        if (repository.existsById(name)) {
            throw new DatasetException(DatasetException.Kind.CONFLICT, "Dataset '" + name + "' already exists");
        }
        ReportDataset dataset = new ReportDataset(name, editor.username(), clock.instant());
        dataset.define(request.label(), request.description(), request.mode(), request.refreshEveryHours(),
                JSON.writeValueAsString(request.definition()), editor.username(), clock.instant());
        try {
            repository.saveAndFlush(dataset);
        } catch (DataAccessException e) {
            // another instance created the same name in the meantime
            throw new DatasetException(DatasetException.Kind.CONFLICT, "Dataset '" + name + "' already exists");
        }
        afterDefinitionChange(dataset);
        return get(name);
    }

    public DatasetInfo update(String name, DatasetRequest request, Actor editor) {
        validate(request, editor);
        String[] unused = new String[1];
        ReportDataset saved = change(name, dataset -> {
            dataset.define(request.label(), request.description(), request.mode(), request.refreshEveryHours(),
                    JSON.writeValueAsString(request.definition()), editor.username(), clock.instant());
            unused[0] = request.mode() == DatasetMode.LIVE ? dataset.dropSnapshot() : null;
            return dataset;
        }).orElseThrow(() -> notFound(name));
        store.drop(unused[0]);
        afterDefinitionChange(saved);
        return info(saved);
    }

    /** Drops every snapshot table of the dataset; a refresh still running elsewhere drops its own when it ends. */
    public void delete(String name) {
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

    private void validate(DatasetRequest request, Actor editor) {
        if (request.mode() == DatasetMode.LIVE && request.refreshEveryHours() != null) {
            throw new InvalidReportException("'refreshEveryHours' only applies to SNAPSHOT datasets");
        }
        executor.prepare(request.definition(), editor);                         // editor may use every field
        executor.prepare(request.definition(), Actor.system("dataset")); // valid as the refresh runs it
    }

    private void afterDefinitionChange(ReportDataset dataset) {
        if (dataset.getMode() == DatasetMode.SNAPSHOT) {
            refreshInBackground(dataset.getName());   // due now anyway, in case this instance goes away
        }
    }

    // ---- reading ----------------------------------------------------------------------------

    public DatasetRows read(String name, Actor reader, int page, Integer requestedSize, boolean includeTotal) {
        ReportDataset dataset = load(name);
        ReportDefinition definition = definition(dataset);
        if (dataset.getMode() == DatasetMode.LIVE) {
            return new DatasetRows(name, DatasetMode.LIVE, null,
                    executor.run(definition, reader, page, requestedSize, includeTotal));
        }
        guardSnapshot(dataset, definition, reader);
        int size = Math.clamp(requestedSize == null ? properties.defaultPageSize() : requestedSize,
                1, properties.maxPageSize());
        int pageNumber = Math.max(page, 0);
        try {
            return snapshotPage(dataset, pageNumber, size);
        } catch (DataAccessException e) {
            // a refresh (maybe on another instance) switched to a new table and dropped this one meanwhile
            return snapshotPage(load(name), pageNumber, size);
        }
    }

    private DatasetRows snapshotPage(ReportDataset dataset, int pageNumber, int size) {
        if (dataset.getTableName() == null) {
            throw new DatasetException(DatasetException.Kind.CONFLICT, "Dataset '" + dataset.getName()
                    + "' has no snapshot yet (status " + dataset.getStatus()
                    + (dataset.getLastError() == null ? "" : ": " + dataset.getLastError()) + ")");
        }
        List<ResultColumn> columns = JSON.readValue(dataset.getColumnsJson(), COLUMNS);
        List<List<Object>> rows = store.page(dataset.getTableName(), columns, size + 1, (long) pageNumber * size);
        boolean hasNext = rows.size() > size;
        List<List<Object>> pageRows = hasNext ? rows.subList(0, size) : rows;
        ReportResult result = new ReportResult(columns, pageRows, pageRows.size(), pageNumber, size, hasNext,
                dataset.getRowCount(), List.of(), List.of());
        return new DatasetRows(dataset.getName(), DatasetMode.SNAPSHOT, dataset.getRefreshedAt(), result);
    }

    /** See the class comment: a snapshot is served only to readers who would get the very same report. */
    private void guardSnapshot(ReportDataset dataset, ReportDefinition definition, Actor reader) {
        ReportQuery forReader = executor.prepare(definition, reader);   // 403 for fields the reader may not use
        ReportQuery forSystem = executor.prepare(definition, Actor.system("dataset"));
        if (!forReader.sql().equals(forSystem.sql()) || !forReader.parameters().equals(forSystem.parameters())) {
            throw new ReportAccessDeniedException("Snapshot '" + dataset.getName() + "' holds every row, but your"
                    + " access is limited by row-level security. Use a LIVE dataset or /api/reports/run instead.");
        }
    }

    // ---- refreshing -------------------------------------------------------------------------

    /**
     * Rebuilds the snapshot now and answers when done. If a refresh is already running (on any instance),
     * waits for it and answers with its result instead of computing the same thing twice.
     */
    public DatasetInfo refresh(String name) {
        Instant deadline = clock.instant().plus(properties.datasetRefreshTimeout()).plus(lease());
        while (true) {
            if (claim(name)) {
                runClaimed(name, true);
                return get(name);
            }
            ReportDataset dataset = load(name);
            if (dataset.getMode() != DatasetMode.SNAPSHOT) {
                throw new InvalidReportException("Dataset '" + name + "' is LIVE; only SNAPSHOT datasets are refreshed");
            }
            while (dataset.isBeingRefreshed(clock.instant())) {
                if (clock.instant().isAfter(deadline)) {
                    throw new DatasetException(DatasetException.Kind.CONFLICT, "Dataset '" + name
                            + "' is still being refreshed by " + dataset.getRefreshOwner());
                }
                sleep(WAIT_STEP);
                dataset = load(name);
            }
            if (dataset.getRefreshOwner() == null) {
                return info(dataset);           // the other refresh finished: that is the fresh result
            }
            // the lease expired without being released (its instance died): loop and take over
        }
    }

    /** Called by {@link DatasetScheduler} on every instance; the claims decide who refreshes what. */
    void refreshDue() {
        repository.findByModeAndNextRefreshAtLessThanEqual(DatasetMode.SNAPSHOT, clock.instant())
                .forEach(dataset -> refreshInBackground(dataset.getName()));
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
                    log.warn("Refresh of dataset '{}' failed: {}", name, e.getMessage());
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
        ReportDataset dataset = load(name);
        long version = dataset.getSnapshotVersion();       // taken by the claim: unique for this refresh
        String table = SnapshotStore.tableName(name, version);
        long beatMillis = lease().toMillis() / 4;
        ScheduledFuture<?> beat = heartbeat.scheduleAtFixedRate(() -> {
            try {
                repository.renew(name, instanceId, clock.instant().plus(lease()));
            } catch (RuntimeException e) {
                log.warn("Could not renew the refresh lease of dataset '{}': {}", name, e.getMessage());
            }
        }, beatMillis, beatMillis, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();
        try {
            ReportQuery query = executor.prepare(definition(dataset), Actor.system("dataset " + name));
            long rows = store.build(table, query, properties.datasetMaxRows(), properties.datasetRefreshTimeout());
            long millis = (System.nanoTime() - start) / 1_000_000;
            String columnsJson = JSON.writeValueAsString(query.columns());
            beat.cancel(false);

            String[] previous = new String[1];
            boolean[] published = {false};
            change(name, current -> {
                if (!instanceId.equals(current.getRefreshOwner())) {
                    return current;               // lease lost to another instance: its result wins
                }
                if (current.getMode() != DatasetMode.SNAPSHOT) {
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
                log.info("Dataset '{}' refreshed on {}: {} rows in {} ms", name, instanceId, rows, millis);
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
            log.warn("Refresh of dataset '{}' failed: {}", name, error);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    /**
     * Load, change, save – again from a fresh copy when another instance (or a lease renewal) saved the
     * dataset in between. Empty if the dataset no longer exists.
     */
    private Optional<ReportDataset> change(String name, Function<ReportDataset, ReportDataset> change) {
        for (int attempt = 1; ; attempt++) {
            Optional<ReportDataset> current = repository.findById(name);
            if (current.isEmpty()) {
                return current;
            }
            try {
                return Optional.of(repository.saveAndFlush(change.apply(current.get())));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == SAVE_ATTEMPTS) {
                    throw new DatasetException(DatasetException.Kind.CONFLICT,
                            "Dataset '" + name + "' is being changed concurrently; try again");
                }
            }
        }
    }

    private Duration lease() {
        return properties.datasetRefreshLease();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatasetException(DatasetException.Kind.CONFLICT, "Interrupted while waiting for a refresh");
        }
    }

    private ReportDataset load(String name) {
        return repository.findById(name).orElseThrow(() -> notFound(name));
    }

    private static DatasetException notFound(String name) {
        return new DatasetException(DatasetException.Kind.NOT_FOUND, "Unknown dataset: " + name);
    }

    private static ReportDefinition definition(ReportDataset dataset) {
        return JSON.readValue(dataset.getDefinitionJson(), ReportDefinition.class);
    }

    private DatasetInfo info(ReportDataset dataset) {
        boolean running = dataset.isBeingRefreshed(clock.instant());
        return new DatasetInfo(dataset.getName(), dataset.getLabel(), dataset.getDescription(), dataset.getMode(),
                dataset.getRefreshEveryHours(), dataset.getStatus(), dataset.getRowCount(), dataset.getRefreshedAt(),
                dataset.getRefreshMillis(), dataset.getNextRefreshAt(), running ? dataset.getRefreshOwner() : null,
                dataset.getLastError(), definition(dataset), dataset.getCreatedBy(), dataset.getCreatedAt(),
                dataset.getUpdatedBy(), dataset.getUpdatedAt());
    }
}
