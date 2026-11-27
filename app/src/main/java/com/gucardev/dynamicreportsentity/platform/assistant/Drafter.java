package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;

import java.util.List;

/** One kind of thing the assistant drafts (a dataset, a mail template, a flow) from a chat. */
interface Drafter {

    /** {@code dataset}, {@code mail-template}, {@code flow}. */
    String kind();

    /**
     * @param current what the user has on screen (also drafts that are not valid yet); null = start from scratch
     */
    SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer);
}
