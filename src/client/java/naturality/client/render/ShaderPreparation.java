package naturality.client.render;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/** Cold pipelines must not queue behind terrain workers waiting for render-thread uploads. */
public final class ShaderPreparation {
    // Only on-demand preparation uses this worker. Resource reloads retain
    // Minecraft's parallel preparation, and GPU finalization stays on the caller.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().daemon().name("Naturality shader preparation").factory());
    private static final Executor PREPARATION = task -> {
        try {
            EXECUTOR.execute(task);
        } catch (RejectedExecutionException error) {
            if (!EXECUTOR.isShutdown()) throw error;
            // Minecraft still renders a final progress screen after CLIENT_STOPPING.
            // With gameplay stopped, finish any cold teardown shader on the caller.
            task.run();
        }
    };

    private ShaderPreparation() {}

    public static Executor executor() { return PREPARATION; }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> EXECUTOR.shutdown());
    }
}
