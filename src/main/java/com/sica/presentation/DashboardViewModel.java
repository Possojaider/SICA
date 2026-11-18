package com.sica.presentation;

import com.sica.domain.port.out.Store.Statistics;
import javafx.beans.property.*;

public final class DashboardViewModel {
    private final ReadOnlyLongWrapper inside = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper pending = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper approved = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper incidents = new ReadOnlyLongWrapper();

    public ReadOnlyLongProperty insideProperty() { return inside.getReadOnlyProperty(); }
    public ReadOnlyLongProperty pendingProperty() { return pending.getReadOnlyProperty(); }
    public ReadOnlyLongProperty approvedProperty() { return approved.getReadOnlyProperty(); }
    public ReadOnlyLongProperty incidentsProperty() { return incidents.getReadOnlyProperty(); }

    public void update(Statistics statistics) {
        inside.set(statistics.inside());
        pending.set(statistics.pending());
        approved.set(statistics.approved());
        incidents.set(statistics.incidents());
    }
}
