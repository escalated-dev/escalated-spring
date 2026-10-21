package dev.escalated.ui;

import java.util.Map;

/** The default renderer: the Inertia protocol, through {@link Inertia}. */
public class InertiaUiRenderer implements EscalatedUiRenderer {

    private final Inertia inertia;

    public InertiaUiRenderer(Inertia inertia) {
        this.inertia = inertia;
    }

    @Override
    public Object render(String page, Map<String, Object> props) {
        return inertia.render(page, props);
    }
}
