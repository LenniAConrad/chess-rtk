package application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chess.debug.LogService;
import chess.model.Record;
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
        final List<Record> out = new ArrayList<>(records.size());
        final List<Callable<Void>> jobs = new ArrayList<>(records.size());

        for (Record rec : records) {
            jobs.add(() -> {
                Engine eng = engines.take();
                try {
                    eng.analyse(rec, accelerate, maxNodes, maxDurationMs);
                } catch (IOException ioe) {
                    LogService.error(ioe, "Engine analyse failed.");
                } finally {
                    engines.put(eng);
                }
                synchronized (out) {
                    out.add(rec);
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
        return out;
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