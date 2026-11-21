package com.gucardev.dynamicreportsentity.personnel;

/** A person was deleted: modules holding rows about them (training records, project assignments) drop them. */
public record PersonnelRemoved(long personnelId, String fullName) {
}
