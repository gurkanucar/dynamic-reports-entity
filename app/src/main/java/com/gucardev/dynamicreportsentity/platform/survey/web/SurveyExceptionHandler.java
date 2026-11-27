package com.gucardev.dynamicreportsentity.platform.survey.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SurveyExceptionHandler.class)
class SurveyExceptionHandler extends ApiExceptionHandlerSupport {
}
