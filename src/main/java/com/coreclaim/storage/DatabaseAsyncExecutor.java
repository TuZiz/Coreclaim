package com.coreclaim.storage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

public final class DatabaseAsyncExecutor implements AutoCloseable {

    private final ExecutorService executor;

    public DatabaseAsyncExecutor() {
        this.executor = Executors.newSingleThreadExecutor(new DatabaseThreadFactory());
    }

    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public CompletableFuture<Void> run(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return CompletableFuture.runAsync(runnable, executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static final class DatabaseThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CoreClaim-DatabaseAsync");
            thread.setDaemon(true);
            return thread;
        }
    }
}
