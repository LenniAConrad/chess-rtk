package application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import application.console.Bar;
import chess.debug.LogService;
import chess.struct.Record;
import chess.uci.Filter;
import chess.uci.Engine;
import chess.uci.Protocol;

/**
 * Used for a fixed-size pool of engine workers that lease {@link Engine}
 * instances to tasks. Ensures each task gets exclusive access to an engine.
 * Engines are created from a protocol path and reused across tasks.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Pool implements AutoCloseable {

    /**
     * Used for executing background tasks with a fixed thread pool.
     */
    private final ExecutorService executor;

    /**
     * Used for leasing {@link Engine} instances to workers in a thread-safe FIFO
     * manner.
     */
    private final BlockingQueue<Engine> engines;

    /**
     * Used for constructing a pool with a fixed number of {@link Engine} instances
     * created from a shared protocol configuration.
     *
     * @param instances    number of engines/threads
     * @param protocolPath path to the TOML protocol configuration file
     * @throws IOException when protocol initialization or engine creation fails
     */
    private Pool(int instances, String protocolPath) throws IOException {
        this.executor = Executors.newFixedThreadPool(instances, r -> {
            Thread t = new Thread(r, "engine-worker");
            t.setDaemon(true);
            return t;
        });
        this.engines = new ArrayBlockingQueue<>(instances);

        final Protocol protocol;
        try {
            String toml = Files.readString(Paths.get(protocolPath));
            protocol = new Protocol().fromToml(toml);
        } catch (Exception e) {
            LogService.error(e, "Failed to create Protocol from: " + protocolPath);
            if (e instanceof IOException ioexception) {
                throw ioexception;
            }
            throw new IOException("Protocol initialization failed.", e);
        }

        for (int i = 0; i < instances; i++) {
            try {
                engines.add(new Engine(protocol));
            } catch (Exception e) {
                LogService.error(e, "Failed to create Engine instance.");
            }
        }

        if (engines.isEmpty()) {
            executor.shutdownNow();
            throw new IOException("No engine instances could be created for protocol: " + protocolPath);
        }

        if (engines.size() < instances) {
            LogService.warn(String.format("Only %d/%d engine instances were created; continuing with reduced capacity.",
                    engines.size(), instances));
        }
    }

    /**
     * Used for constructing an engine pool with {@code instances} workers.
     *
     * @param instances    number of engines/threads
     * @param protocolPath path to the engine protocol configuration
     * @return constructed pool
     * @throws IOException when engine creation fails
     */
    public static Pool create(int instances, String protocolPath) throws IOException {
        return new Pool(instances, protocolPath);
    }

    /**
     * Used for analysing all records with shared {@link Filter}, node cap, and
     * duration cap. Each task leases one {@link Engine} from the pool for exclusive
     * use.
     * If the underlying engine process terminates unexpectedly, the {@link Engine}
     * implementation revives/restarts it and continues work; no task handoff to
     * another
     * engine occurs at the pool level.
     *
     * @param records       input records to analyse
     * @param accelerate    prefilter arguments
     * @param maxNodes      maximum nodes per position
     * @param maxDurationMs maximum time per position (milliseconds)
     * @return list of analysed records in the same order
     */
    public List<Record> analyseAll(
            List<Record> records,
            Filter accelerate,
            long maxNodes,
            long maxDurationMs) {
        final List<Record> out = new ArrayList<>(java.util.Collections.nCopies(records.size(), null));
        final List<Callable<Void>> jobs = new ArrayList<>(records.size());
        final Bar progressBar = new Bar(records.size());

        for (int idx = 0; idx < records.size(); idx++) {
            final int recIndex = idx;
            final Record rec = records.get(idx);
            jobs.add(() -> {
                Engine eng = null;
                try {
                    eng = engines.take();
                    try {
                        eng.analyse(rec, accelerate, maxNodes, maxDurationMs);
                    } catch (IOException ioe) {
                        LogService.error(ioe, "Engine analyse failed.");
                    } finally {
                        if (eng != null) {
                            engines.put(eng);
                        }
                    }
                    out.set(recIndex, rec);
                } finally {
                    progressBar.step();
                }
                return null;
            });
        }

        try {
            executor.invokeAll(jobs).forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ee) {
                    LogService.error(ee, "Worker crashed.");
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        progressBar.finish();
        return out;
    }

    /**
     * Analyses all records and invokes {@code onDone} for each record as soon as its
     * analysis completes (completion order is not guaranteed).
     *
     * <p>
     * The callback runs on the caller thread (the thread calling this method), not
     * on worker threads.
     * </p>
     *
     * @param records       input records to analyse
     * @param accelerate    prefilter arguments
     * @param maxNodes      maximum nodes per position
     * @param maxDurationMs maximum time per position (milliseconds)
     * @param onDone        called once per analysed record
     */
    public void analyseEach(
            List<Record> records,
            Filter accelerate,
            long maxNodes,
            long maxDurationMs,
            Consumer<Record> onDone) {
        final CompletionService<Record> cs = new ExecutorCompletionService<>(executor);

        for (Record rec : records) {
            cs.submit(() -> {
                try {
                    Engine eng = engines.take();
                    analyseWithEngine(eng, rec, accelerate, maxNodes, maxDurationMs);
                    return rec;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return rec;
                }
            });
        }

        for (int i = 0; i < records.size(); i++) {
            try {
                Record rec = cs.take().get();
                if (onDone != null) {
                    onDone.accept(rec);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ee) {
                LogService.error(ee, "Worker crashed.");
            }
        }
    }

    private void analyseWithEngine(
            Engine eng,
            Record rec,
            Filter accelerate,
            long maxNodes,
            long maxDurationMs) throws InterruptedException {
        try {
            eng.analyse(rec, accelerate, maxNodes, maxDurationMs);
        } catch (IOException ioe) {
            LogService.error(ioe, "Engine analyse failed.");
        } finally {
            engines.put(eng);
        }
    }

    /**
     * Used for releasing engine resources and shutting down worker threads.
     */
    @Override
    public void close() {
        executor.shutdownNow();
        Engine e;
        while ((e = engines.poll()) != null) {
            try {
                if (e instanceof AutoCloseable) {
                    e.close();
                }
            } catch (Exception ex) {
                LogService.error(ex, "Failed to close engine.");
            }
        }
    }
}
