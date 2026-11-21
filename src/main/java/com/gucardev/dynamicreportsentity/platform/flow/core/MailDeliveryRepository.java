package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailDeliveryRepository extends JpaRepository<MailDelivery, UUID> {

    Optional<MailDelivery> findByStepRunId(UUID stepRunId);
}
