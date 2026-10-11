package Absolute_Control.historial;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Cola acotada de un solo escritor; nunca escribe en el hilo llamador. */
public final class HistorialAsync {
    @FunctionalInterface
    public interface CreadorServicio {
        ServicioHistorial crear() throws HistorialException;
    }

    private final ThreadPoolExecutor executor;
    private final CreadorServicio creator;
    private final Consumer<Throwable> onError;
    private volatile ServicioHistorial service;
    private volatile Throwable initializationError;

    public HistorialAsync(CreadorServicio creator, Consumer<Throwable> onError) {
        this.creator = creator;
        this.onError = onError;
        executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(128), task -> {
                    Thread thread = new Thread(task, "historial-persistencia");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        submit(this::initialize);
    }

    public static HistorialAsync predeterminado(Consumer<Throwable> onError) {
        return new HistorialAsync(
                () -> new ServicioHistorial(RepositorioHistorialJson.predeterminado()), onError);
    }

    public boolean registrar(EventoHistorial event) {
        return submit(() -> {
            ServicioHistorial current = requireService();
            current.registrar(event);
        });
    }

    public boolean recargar(Consumer<List<EventoHistorial>> onSuccess) {
        return submit(() -> onSuccess.accept(requireService().recargar()));
    }

    public boolean listar(Consumer<List<EventoHistorial>> onSuccess) {
        return submit(() -> onSuccess.accept(requireService().listar()));
    }

    public boolean esperarPendientes(Duration timeout) {
        if (executor.isShutdown()) return executor.isTerminated();
        try {
            Future<?> barrier = executor.submit(() -> {});
            barrier.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception error) {
            onError.accept(error);
            return false;
        }
    }

    /** Debe invocarse fuera del EDT; espera de forma acotada y cancela lo restante. */
    public boolean cerrar(Duration timeout) {
        executor.shutdown();
        boolean finished = false;
        try {
            finished = executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            onError.accept(error);
        }
        if (!finished) {
            int discarded = executor.shutdownNow().size();
            onError.accept(new HistorialException(
                    "Cierre histórico incompleto; tareas descartadas: " + discarded));
        }
        return finished;
    }

    private void initialize() {
        try {
            service = creator.crear();
        } catch (Throwable error) {
            initializationError = error;
            onError.accept(error);
        }
    }

    private ServicioHistorial requireService() throws HistorialException {
        if (service != null) return service;
        throw new HistorialException("El historial no está disponible", initializationError);
    }

    private boolean submit(CheckedTask task) {
        try {
            executor.execute(() -> {
                try { task.run(); }
                catch (Throwable error) { onError.accept(error); }
            });
            return true;
        } catch (RejectedExecutionException error) {
            onError.accept(new HistorialException("La cola del historial está llena o cerrada", error));
            return false;
        }
    }

    @FunctionalInterface
    private interface CheckedTask {
        void run() throws Exception;
    }
}
