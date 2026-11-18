package com.sica.presentation;

import javafx.application.Platform;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class BackgroundTasks implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sica-io");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public <T> void submit(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        executor.submit(() -> {
            try {
                T result = operation.call();
                ui(() -> success.accept(result));
            } catch (Exception error) {
                ui(() -> failure.accept(error));
            }
        });
    }

    public ScheduledFuture<?> poll(Runnable tick) {
        return executor.scheduleWithFixedDelay(() -> ui(tick), 5, 5, TimeUnit.SECONDS);
    }

    private void ui(Runnable action) {
        if (!closed) Platform.runLater(() -> {
            if (!closed) action.run();
        });
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
