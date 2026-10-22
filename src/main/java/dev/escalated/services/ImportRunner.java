package dev.escalated.services;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Runs an import outside the request that started it, as escalated-laravel queues
 * {@code ImportService::run}: one at a time, on a thread of its own. A host with a job queue
 * declares its own {@code ImportDispatcher} bean and calls {@link ImportJobService#run} from its
 * worker instead.
 */
@Component
public class ImportRunner implements ImportDispatcher {

    private final ImportJobService imports;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "escalated-import");
        thread.setDaemon(true);
        return thread;
    });
    private final List<CompletableFuture<Void>> running = new ArrayList<>();

    public ImportRunner(ImportJobService imports) {
        this.imports = imports;
    }

    @Override
    public void dispatch(String jobId) {
        CompletableFuture<Void> run = CompletableFuture.runAsync(() -> imports.run(jobId), executor);
        synchronized (running) {
            running.add(run);
        }
    }

    /** Waits for every run started so far (tests, graceful shutdown). */
    public void awaitIdle() {
        List<CompletableFuture<Void>> pending;
        synchronized (running) {
            pending = new ArrayList<>(running);
            running.clear();
        }
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
