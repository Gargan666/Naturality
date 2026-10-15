package naturality.test;

import naturality.navigation.NavigationInstruments;
import naturality.client.navigation.NavigationClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

public final class NavigationGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public void runTest(ClientGameTestContext context) {
        check(NavigationInstruments.time(0).equals("Day 0 | 06:00 AM (06:00)"), "Tick zero is six AM");
        check(NavigationInstruments.time(6000).equals("Day 0 | 12:00 PM (12:00)"), "Noon switches to PM");
        check(NavigationInstruments.time(12000).equals("Day 0 | 06:00 PM (18:00)"), "Both twelve and twenty-four hour time");
        check(NavigationInstruments.time(18000).equals("Day 0 | 12:00 AM (00:00)"), "Midnight wraps correctly");
        check(NavigationInstruments.time(24000).equals("Day 1 | 06:00 AM (06:00)"), "Day count follows celestial ticks");
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a -12 100 -24");
            server.runCommand("time set 6000");
            server.runCommand("gamerule advance_time false");
            server.runCommand("setblock 30 100 40 minecraft:red_bed");
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                player.setRespawnPosition(null, false);
                check(NavigationInstruments.target(player).equals(s.overworld().getRespawnData().globalPos()), "Default overworld spawn");
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, new BlockPos(30, 100, 40), 0, 0), false), false);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLOCK));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
            });
            context.waitTicks(70);
            context.runOnClient(client -> {
                check(NavigationClient.target(client.level).pos().equals(new BlockPos(30, 100, 40)), "Bed target synchronized");
                String message = NavigationClient.message().getString();
                check(message.contains("12:00 PM") && message.contains("X: -12") && message.contains("Z: -24"), "Both hands combine time and negative coordinates");
                client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("Reputation: 51"), false);
            });
            context.takeScreenshot("navigation-reputation-priority");
            context.waitTicks(65);
            context.takeScreenshot("navigation-clock-compass");
            server.runCommand("execute in minecraft:the_nether run tp @a 10 100 20");
            context.waitTicks(20);
            context.runOnClient(client -> check(NavigationClient.target(client.level).equals(GlobalPos.of(Level.NETHER, BlockPos.ZERO)), "Nether falls back to origin when spawn is elsewhere"));
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                var anchor = new BlockPos(24, 100, 32);
                player.level().setBlockAndUpdate(anchor, net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE, 1));
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.NETHER, anchor, 0, 0), false), false);
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                check(NavigationClient.target(client.level).pos().equals(new BlockPos(24, 100, 32)), "Anchor target synchronized in Nether");
                var compass = TestFixtures.player(client).getOffhandItem();
                var kind = net.minecraft.client.renderer.item.properties.numeric.CompassAngleState.CompassTarget.SPAWN;
                var spawnAngle = new net.minecraft.client.renderer.item.properties.numeric.CompassAngle(false, kind);
                float actual = spawnAngle.get(compass, client.level, client.player, 0);
                var comparison = new ItemStack(Items.COMPASS);
                comparison.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(java.util.Optional.of(NavigationClient.target(client.level)), false));
                var lodestoneAngle = new net.minecraft.client.renderer.item.properties.numeric.CompassAngle(false, net.minecraft.client.renderer.item.properties.numeric.CompassAngleState.CompassTarget.LODESTONE);
                check(Math.abs(actual - lodestoneAngle.get(comparison, client.level, client.player, 0)) < .00001, "Actual compass needle points to the anchor rather than spinning");
                compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(java.util.Optional.of(GlobalPos.of(Level.NETHER, new BlockPos(60, 100, 70))), false));
                check(TestFixtures.require(net.minecraft.client.renderer.item.properties.numeric.CompassAngleState.CompassTarget.LODESTONE.get(TestFixtures.level(client), compass, client.player), "lodestone compass target").pos().equals(new BlockPos(60, 100, 70)), "Lodestone still overrides target");
                TestFixtures.player(client).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                TestFixtures.player(client).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                check(NavigationClient.message() == null, "Readout disappears as soon as neither item is held");
            });
            server.runCommand("execute in minecraft:the_end run tp @a 10 100 20");
            context.waitTicks(20);
            context.runOnClient(client -> check(NavigationClient.target(client.level).equals(GlobalPos.of(Level.END, BlockPos.ZERO)), "End falls back to its own origin"));
        }
    }
}
