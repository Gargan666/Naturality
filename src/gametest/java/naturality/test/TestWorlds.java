package naturality.test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import naturality.api.worlds.DisposableWorlds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.jspecify.annotations.Nullable;

/** Small world fixtures. Persistence is an explicit choice, never enabled by an ordinary test. */
public final class TestWorlds {
    public static Builder disposable(ClientGameTestContext context) { return new Builder(context, false); }
    public static Builder persistent(ClientGameTestContext context) { return new Builder(context, true); }

    public static final class Builder {
        private final TestWorldBuilder delegate;
        private final boolean persistent;
        private Consumer<WorldCreationUiState> settings = _ -> {};
        private boolean used;
        private Builder(ClientGameTestContext context, boolean persistent) {
            this.delegate = context.worldBuilder();
            this.persistent = persistent;
        }
        public Builder setUseConsistentSettings(boolean value) { delegate.setUseConsistentSettings(value); return this; }
        public Builder adjustSettings(Consumer<WorldCreationUiState> adjust) { settings = adjust; return this; }
        public World create() {
            if (used) throw new IllegalStateException("Create a fresh builder for each world");
            used = true;
            var scope = new AtomicReference<DisposableWorlds.Scope>();
            delegate.adjustSettings(state -> {
                settings.accept(state);
                if (!persistent) {
                    // Fabric runs the adjuster before pressing Create; no world data has been written yet.
                    Path root = Minecraft.getInstance().getLevelSource().getBaseDir().resolve(state.getTargetFolder());
                    scope.set(DisposableWorlds.open(root));
                }
            });
            // Failed startups retain the barrier: workers can outlive the client/server attachment.
            return new World(delegate.create(), scope.get());
        }
    }

    public static final class World implements AutoCloseable {
        private final TestSingleplayerContext delegate;
        private final DisposableWorlds.@Nullable Scope scope;
        private boolean closed;
        private World(TestSingleplayerContext delegate, DisposableWorlds.@Nullable Scope scope) {
            this.delegate = delegate;
            this.scope = scope;
        }
        public TestServerContext getServer() { return delegate.getServer(); }
        public TestServerConnection getConnection() { return delegate.getConnection(); }
        public Path directory() { return delegate.getWorldSave().getSaveDirectory(); }
        public TestWorldSave getWorldSave() {
            if (scope != null) throw new IllegalStateException("Disposable worlds cannot reopen; use TestWorlds.persistent for persistence checks");
            return delegate.getWorldSave();
        }
        @Override public void close() {
            if (closed) return;
            delegate.close(); // Waits until the server and storage have stopped, including queued close work.
            closed = true;
            if (scope != null) scope.close();
        }
    }

    private TestWorlds() {}
}
