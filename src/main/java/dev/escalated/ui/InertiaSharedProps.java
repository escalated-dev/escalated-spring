package dev.escalated.ui;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Props every page receives, merged under the page's own (Laravel's {@code HandleInertiaRequests}
 * share plus Escalated's {@code shareInertiaData}).
 *
 * <p>Every bean of this type contributes, in {@code @Order}; a later contributor's key replaces an
 * earlier one's. {@link EscalatedSharedProps} supplies {@code auth}, {@code flash}, {@code errors}
 * and {@code escalated}. A host adds its own keys with another bean, or replaces those by defining
 * a bean of type {@code EscalatedSharedProps}.
 */
public interface InertiaSharedProps {

    Map<String, Object> share(HttpServletRequest request);
}
