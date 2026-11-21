package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Cron schedules of flows; visible and changeable by whoever may see the flow. */
@Service
public class ScheduleService {

    /** A schedule as entered by the user. */
    public record ScheduleInput(String cron, String timeZone, boolean enabled) {
    }

    private static final int CHECKED_FIRE_TIMES = 20;

    private final FlowScheduleRepository schedules;
    private final FlowService flows;
    private final FlowProperties properties;
    private final Clock clock;

    ScheduleService(FlowScheduleRepository schedules, FlowService flows, FlowProperties properties, Clock clock) {
        this.schedules = schedules;
        this.flows = flows;
        this.properties = properties;
        this.clock = clock;
    }

    public List<FlowSchedule> list(long flowId, Actor viewer) {
        flows.get(flowId, viewer);
        return schedules.findByFlowIdOrderById(flowId);
    }

    public FlowSchedule get(long id, Actor viewer) {
        FlowSchedule schedule = schedules.findById(id).orElseThrow(() -> notFound(id));
        try {
            flows.get(schedule.getFlowId(), viewer);
        } catch (FlowException e) {
            throw notFound(id);
        }
        return schedule;
    }

    @Transactional
    public FlowSchedule create(long flowId, ScheduleInput input, Actor viewer) {
        flows.get(flowId, viewer);
        if (schedules.countByFlowId(flowId) >= properties.maxSchedulesPerFlow()) {
            throw new FlowException(Kind.CONFLICT, "SCHEDULE_LIMIT_REACHED",
                    "A flow may have at most " + properties.maxSchedulesPerFlow() + " schedules.");
        }
        Instant now = clock.instant();
        FlowSchedule schedule = new FlowSchedule(flowId, viewer.username(), now);
        schedule.define(input.cron().strip(), input.timeZone(), input.enabled(), check(input, now), now);
        return schedules.saveAndFlush(schedule);
    }

    @Transactional
    public FlowSchedule update(long id, long expectedVersion, ScheduleInput input, Actor viewer) {
        FlowSchedule schedule = get(id, viewer);
        if (schedule.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        Instant now = clock.instant();
        schedule.define(input.cron().strip(), input.timeZone(), input.enabled(), check(input, now), now);
        try {
            return schedules.saveAndFlush(schedule);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        }
    }

    @Transactional
    public void delete(long id, Actor viewer) {
        schedules.delete(get(id, viewer));
    }

    /** Validates cron and zone, enforces the minimum interval; returns the first fire time. */
    private Instant check(ScheduleInput input, Instant now) {
        List<FieldProblem> problems = new ArrayList<>();
        ZoneId zone = null;
        try {
            zone = ZoneId.of(input.timeZone());
        } catch (DateTimeException e) {
            problems.add(new FieldProblem("timeZone", "INVALID_TIME_ZONE",
                    "Unknown time zone; use an IANA name such as Europe/Istanbul."));
        }
        CronExpression cron = null;
        try {
            cron = CronSchedules.parse(input.cron());
        } catch (IllegalArgumentException e) {
            problems.add(new FieldProblem("cron", "INVALID_CRON", "Not a valid cron expression: " + e.getMessage()));
        }
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        Instant first = CronSchedules.next(cron, zone, now);
        if (first == null) {
            throw FlowException.invalid("cron", "CRON_NEVER_FIRES", "The expression never fires.");
        }
        Instant previous = first;
        for (int i = 0; i < CHECKED_FIRE_TIMES; i++) {
            Instant next = CronSchedules.next(cron, zone, previous);
            if (next == null) {
                break;
            }
            if (Duration.between(previous, next).compareTo(properties.minScheduleInterval()) < 0) {
                throw FlowException.invalid("cron", "SCHEDULE_TOO_FREQUENT", "Runs may start at most every "
                        + properties.minScheduleInterval().toMinutes() + " minutes.");
            }
            previous = next;
        }
        return first;
    }

    private static FlowException notFound(long id) {
        return FlowException.notFound("SCHEDULE_NOT_FOUND", "Schedule " + id + " does not exist.");
    }

    private static FlowException versionMismatch() {
        return new FlowException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The schedule was changed meanwhile (maybe it just fired); reload it and try again.");
    }
}
