package com.gucardev.dynamicreportsentity.platform.flowreport;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepCheck;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Report steps are built (not run) when a flow is saved, for the editor and for the owner the flow runs as:
 * a field either of them may not see is refused with 403 {@code REPORT_FIELD_FORBIDDEN}.
 */
@Component
@EnableConfigurationProperties(FlowReportProperties.class)
class ReportStepType implements StepType<ReportStep> {

    private final ReportExecutor reports;
    private final FlowReportProperties properties;

    ReportStepType(ReportExecutor reports, FlowReportProperties properties) {
        this.reports = reports;
        this.properties = properties;
    }

    @Override
    public String name() {
        return ReportStep.TYPE;
    }

    @Override
    public Class<ReportStep> stepClass() {
        return ReportStep.class;
    }

    @Override
    public boolean producesFiles() {
        return true;
    }

    @Override
    public void validate(ReportStep step, StepCheck check) {
        if (step.maxRows() != null && step.maxRows() > properties.reportMaxRows()) {
            check.problem("maxRows", "MAX_ROWS_TOO_HIGH",
                    "At most " + properties.reportMaxRows() + " rows are allowed.");
        }
        try {
            reports.prepare(step.definition(), check.editor());
            if (check.owner() != null && check.owner() != check.editor()) {
                reports.prepare(step.definition(), check.owner());
            }
        } catch (InvalidReportException e) {
            check.problem("definition", "INVALID_REPORT_DEFINITION", e.getMessage());
        } catch (ReportAccessDeniedException e) {
            throw new ApiException(ApiException.Kind.FORBIDDEN, "REPORT_FIELD_FORBIDDEN",
                    "Step '" + step.key() + "': " + e.getMessage());
        }
    }
}
