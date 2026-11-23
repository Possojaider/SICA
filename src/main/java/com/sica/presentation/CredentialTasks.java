package com.sica.presentation;

import javafx.application.Platform;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Aísla las llamadas nativas al llavero y limita cuánto espera la interfaz. */
final class CredentialTasks implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sica-keychain");
        thread.setDaemon(true);
        return thread;
    });

    <T> void submit(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        CompletableFuture.supplyAsync(() -> {
            try { return operation.call(); }
            catch (Exception error) { throw new CompletionException(error); }
        }, executor).orTimeout(3, TimeUnit.SECONDS).whenComplete((value, error) -> Platform.runLater(() -> {
            if (error == null) success.accept(value);
            else failure.accept(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
        }));
    }

    @Override public void close() { executor.shutdownNow(); }
}
