package com.gucardev.dynamicreportsentity.platform.notification.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = NotificationExceptionHandler.class)
class NotificationExceptionHandler extends ApiExceptionHandlerSupport {
}
