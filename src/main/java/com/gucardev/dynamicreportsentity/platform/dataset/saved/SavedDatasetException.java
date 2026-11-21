package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;

/** A saved dataset request that cannot be served (not found, name taken, in use, not the owner). */
public class SavedDatasetException extends ApiException {

    SavedDatasetException(Kind kind, String code, String message) {
        super(kind, code, message);
    }
}
