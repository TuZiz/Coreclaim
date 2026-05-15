package com.coreclaim.storage;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DatabaseAsyncExecutorTest {

    @Test
    void supplyRunsOnDedicatedDatabaseThread() throws Exception {
        String callerThread = Thread.currentThread().getName();
        String databaseThread;
        try (DatabaseAsyncExecutor executor = new DatabaseAsyncExecutor()) {
            databaseThread = executor.supply(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
        }

        assertNotEquals(callerThread, databaseThread);
        assertTrue(databaseThread.startsWith("CoreClaim-DatabaseAsync"));
    }
}
