/**
 * How other modules plug step types into the flow engine: a {@link com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep}
 * record (the JSON a flow stores), a {@link com.gucardev.dynamicreportsentity.platform.flow.spi.StepType} bean (name,
 * validation, references) and a {@link com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler} bean (runs it).
 * Steps that wait for something outside the engine resume through
 * {@link com.gucardev.dynamicreportsentity.platform.flow.spi.WaitingSteps}. The engine's own steps use the same
 * contract, so the engine knows no step type by name.
 */
@NamedInterface("spi")
package com.gucardev.dynamicreportsentity.platform.flow.spi;

import org.springframework.modulith.NamedInterface;
