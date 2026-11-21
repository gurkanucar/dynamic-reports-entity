package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.HttpSupport;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.security.core.Authentication;

import java.time.Instant;

/** The shared {@link HttpSupport} conventions, under the names the flow controllers use. */
final class WebSupport {

    private WebSupport() {
    }

    static Actor viewer(Authentication authentication) {
        return Actor.of(authentication);
    }

    static String value(Enum<?> value) {
        return HttpSupport.value(value);
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, String parameter) {
        return HttpSupport.parse(type, value, parameter);
    }

    static int[] paging(Integer page, Integer size) {
        return HttpSupport.paging(page, size);
    }

    static String etag(long version) {
        return HttpSupport.etag(version);
    }

    static long expectedVersion(String ifMatch) {
        return HttpSupport.expectedVersion(ifMatch);
    }

    static String time(Instant instant) {
        return HttpSupport.time(instant);
    }
}
