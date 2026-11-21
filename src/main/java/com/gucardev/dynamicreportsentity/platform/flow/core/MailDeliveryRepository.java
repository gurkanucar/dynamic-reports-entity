package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailDeliveryRepository extends JpaRepository<MailDelivery, Long> {

    Optional<MailDelivery> findByStepRunId(Long stepRunId);
}
