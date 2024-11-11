package com.limechain.utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class AsyncExecutor {

    private final ExecutorService executorService;

    private AsyncExecutor(int poolSize) {
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    public static AsyncExecutor withPoolSize(int poolSize) {
        return new AsyncExecutor(poolSize);
    }

    public static AsyncExecutor withSingleThread() {
        return new AsyncExecutor(1);
    }

    public <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executorService);
    }

    public void executeAndForget(Runnable task) {
        executorService.execute(task);
    }

    //TODO Yordan: Create a centralized retry function here.
}
