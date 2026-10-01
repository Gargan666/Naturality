package naturality.test;

import naturality.client.portal.end.EndEyeGlow;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exercises real item use, the vanilla client event, lifetime and independence. */
public final class EndEyeGlowGameTest implements FabricClientGameTest {
    public static int smokeCount;
    @Override public void runTest(ClientGameTestContext context) {
        boolean previous = NaturalityConfig.get().portalChanges.glowEffect;
        boolean[] previousTransparency = new boolean[1];
        context.runOnClient(client -> previousTransparency[0] = client.options.improvedTransparency().get());
        NaturalityConfig.get().portalChanges.glowEffect = false;
        BlockPos pos = new BlockPos(0, 100, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -4 99 -4 4 99 4 smooth_quartz");
            server.runCommand("setblock 0 100 0 end_portal_frame");
            server.runCommand("setblock 2 100 0 end_portal_frame[eye=true]");
            server.runCommand("tp @a 0.5 101 -2.5 0 35");
            server.runCommand("tick freeze");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                check(!EndEyeGlow.isActive(new BlockPos(2, 100, 0)), "Existing filled frames must not glow");
                smokeCount = 0;
                for (int i = 0; i < 32; i++) Blocks.END_PORTAL.animateTick(Blocks.END_PORTAL.defaultBlockState(),
                    client.level, pos, net.minecraft.util.RandomSource.create(i));
                check(smokeCount == 0, "End portal display ticks must not emit smoke");
                check(EndEyeGlow.envelope(0) == 1 && EndEyeGlow.envelope(20) == 0.5f
                    && EndEyeGlow.envelope(40) == 0, "Reach, alpha and saturation must fade to exactly zero");
            });
            server.runOnServer(s -> {
                var player = world.getConnection().getServerPlayer();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
                Items.ENDER_EYE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
                check(s.overworld().getBlockState(pos).getValue(EndPortalFrameBlock.HAS_EYE), "Real eye use must fill the frame");
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> EndEyeGlow.isActive(pos));
            long start = server.computeOnServer(s -> s.overworld().getGameTime());
            context.waitTicks(3);
            context.runOnClient(client -> check(smokeCount == 0, "Eye glow must replace the insertion smoke puff"));
            context.takeScreenshot("end-eye-glow-start");
            server.runCommand("tick step 20");
            context.waitFor(client -> client.level.getGameTime() >= start + 20);
            context.takeScreenshot("end-eye-glow-half");
            server.runCommand("tick step 20");
            context.waitFor(client -> client.level.getGameTime() >= start + 40 && !EndEyeGlow.isActive(pos));
            context.takeScreenshot("end-eye-glow-expired");
            // A second genuine insertion can restart, and breaking its frame cancels it.
            context.runOnClient(client -> client.options.improvedTransparency().set(true));
            server.runCommand("setblock 0 100 0 end_portal_frame");
            server.runOnServer(s -> {
                var player = world.getConnection().getServerPlayer();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
                Items.ENDER_EYE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> EndEyeGlow.isActive(pos));
            context.waitTicks(3);
            context.takeScreenshot("end-eye-glow-improved-transparency");
            server.runCommand("setblock 0 100 0 air");
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> !EndEyeGlow.isActive(pos));
            server.runCommand("tick unfreeze");
        } finally {
            NaturalityConfig.get().portalChanges.glowEffect = previous;
            context.runOnClient(client -> client.options.improvedTransparency().set(previousTransparency[0]));
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
