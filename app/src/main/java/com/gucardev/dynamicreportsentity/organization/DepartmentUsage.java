package com.gucardev.dynamicreportsentity.organization;

import java.util.Collection;
import java.util.Map;

/**
 * Implemented by modules whose records point at departments (personnel, projects): the admin screens show how many
 * use each department, and a department still in use cannot be deleted.
 */
public interface DepartmentUsage {

    /** What is counted, for people: "çalışan", "proje". */
    String label();

    /** Count per department id; departments without any are left out. */
    Map<Long, Long> countByDepartment(Collection<Long> departmentIds);
}
