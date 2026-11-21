package com.gucardev.dynamicreportsentity.platform.notification;

/**
 * Implemented by modules that refer to mail templates (e.g. flows), so a template in use cannot be deleted without
 * this module knowing who uses it.
 */
public interface MailTemplateUsage {

    /** Whether something of this module still uses the template. */
    boolean usesTemplate(long templateId);
}
