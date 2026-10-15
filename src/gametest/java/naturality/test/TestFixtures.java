package naturality.test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Shared fixture requirements: missing setup must fail a test, never skip its assertions. */
public final class TestFixtures {
    private TestFixtures() {}

    public static final net.minecraft.client.input.KeyEvent CONFIRM = new net.minecraft.client.input.KeyEvent(40, 0, 0);

    @SuppressWarnings("deprecation") // Vanilla explicitly exposes this setter for tests; there is no replacement setter.
    public static void disableDragonFight(net.minecraft.server.level.ServerLevel level) {
        level.setDragonFight(null);
    }

    @SuppressWarnings("deprecation") // Vanilla debug-only brain inspection is needed in failed AI fixture diagnostics.
    public static String brainActivity(net.minecraft.world.entity.LivingEntity entity) {
        var brain = entity.getBrain();
        return brain.getActiveActivities() + " " + brain.getRunningBehaviors().stream().map(b -> b.debugString()).toList();
    }

    @SuppressWarnings("null") // Vanilla leaves this field null outside the Overworld despite its package null default.
    public static void clearSkyColor(net.minecraft.client.renderer.state.level.SkyRenderState state) {
        state.skyColor = null;
    }

    public static net.minecraft.client.multiplayer.@NonNull ClientLevel level(net.minecraft.client.Minecraft client) {
        return require(client.level, "client world");
    }

    public static net.minecraft.client.player.@NonNull LocalPlayer player(net.minecraft.client.Minecraft client) {
        return require(client.player, "client player");
    }

    public static net.minecraft.client.gui.screens.@NonNull Screen screen(net.minecraft.client.Minecraft client) {
        return require(client.gui.screen(), "current screen");
    }

    public static <T> @NonNull T require(@Nullable T value, String description) {
        if (value == null) throw new AssertionError("Missing test fixture: " + description);
        return value;
    }
}
