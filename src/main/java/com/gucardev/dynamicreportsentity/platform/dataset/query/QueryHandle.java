package com.gucardev.dynamicreportsentity.platform.dataset.query;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lets another thread stop a running background query: {@link #cancel()} calls {@link Statement#cancel()}
 * on the statement being executed (the database stops working on it) and makes the row loop give up.
 * <p>
 * A cancel that arrives just before the statement is sent may be ignored by the driver; callers that keep
 * calling {@link #cancel()} (e.g. from a heartbeat) cover that window.
 */
public final class QueryHandle {

    private final AtomicReference<Statement> statement = new AtomicReference<>();
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
        Statement running = statement.get();
        if (running != null) {
            try {
                running.cancel();
            } catch (SQLException ignored) {
                // already finished or closed: nothing left to stop
            }
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    void attach(Statement running) {
        statement.set(running);
        if (cancelled) {
            cancel();
        }
    }

    void detach() {
        statement.set(null);
    }
}
