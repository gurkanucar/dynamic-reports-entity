package com.gucardev.dynamicreportsentity.platform.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SurveyInvitationRepository extends JpaRepository<SurveyInvitation, String> {

    Optional<SurveyInvitation> findBySourceTypeAndSourceId(String sourceType, String sourceId);
}
