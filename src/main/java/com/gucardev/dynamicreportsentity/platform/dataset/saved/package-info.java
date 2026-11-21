/**
 * Saved datasets: a dataset definition stored under a name, which other modules (e.g. flows) use like a variable.
 * Always computed live, with the permissions of whoever uses it. {@link SavedDatasetUsage} lets a module that refers
 * to one keep it from being deleted.
 */
@NamedInterface("saved")
package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import org.springframework.modulith.NamedInterface;
