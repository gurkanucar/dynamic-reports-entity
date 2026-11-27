package com.gucardev.dynamicreportsentity.platform.flow.core;

final class Texts {

    private Texts() {
    }

    static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
