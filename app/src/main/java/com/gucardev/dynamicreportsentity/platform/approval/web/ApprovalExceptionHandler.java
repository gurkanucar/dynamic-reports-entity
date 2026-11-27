package com.gucardev.dynamicreportsentity.platform.approval.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ApprovalExceptionHandler.class)
class ApprovalExceptionHandler extends ApiExceptionHandlerSupport {
}
