package naturality.test;

import java.util.ArrayList;
import java.util.List;
import naturality.weather.EndGravity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

public final class EndMovementGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Object invoke(Class<?> owner, Object target, String name, Class<?>[] types, Object... args) {
        try {
            var method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void expect(List<String> failures, boolean value, String message) { if (!value) failures.add(message); }
    private static void stepChecks(ServerLevel end, ServerLevel normal, List<String> failures) {
        var inverted = EntityTypes.COW.create(end, EntitySpawnReason.COMMAND);
        DistortionFixtures.invert(inverted);
        var upright = EntityTypes.COW.create(normal, EntitySpawnReason.COMMAND);
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 4; z++) {
            normal.setBlockAndUpdate(new BlockPos(x, 100, z), Blocks.END_STONE.defaultBlockState());
            end.setBlockAndUpdate(new BlockPos(x, 145, z), Blocks.END_STONE.defaultBlockState());
        }
        for (boolean full : new boolean[]{false, true}) {
            for (int z = 0; z <= 4; z++) {
                normal.setBlockAndUpdate(new BlockPos(4, 101, z), full ? Blocks.END_STONE.defaultBlockState()
                    : Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                end.setBlockAndUpdate(new BlockPos(4, 144, z), full ? Blocks.END_STONE.defaultBlockState()
                    : Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
            upright.setPos(3.4, 101, 2); upright.setOnGround(true);
            inverted.setPos(3.4, 145.0 - inverted.getBbHeight(), 2); inverted.setOnGround(true);
            var movement = new Vec3(.8, -.08, .15);
            var types = new Class<?>[]{Vec3.class};
            var expected = (Vec3) invoke(Entity.class, upright, "collide", types, movement);
            var actual = (Vec3) invoke(Entity.class, inverted, "collide", types, new Vec3(movement.x, -movement.y, movement.z));
            expect(failures, actual.distanceTo(new Vec3(expected.x, -expected.y, expected.z)) < 1e-6,
                "Ceiling stepping differs from mirrored floor stepping (full block=" + full + "): " + actual + " vs " + expected);
        }
    }
    private static void sneakChecks(ServerPlayer player, List<String> failures) {
        player.setPos(0, 145.0 - player.getBbHeight(), 0); player.setOnGround(true); player.setShiftKeyDown(true);
        var types = new Class<?>[]{Vec3.class, MoverType.class};
        var requested = new Vec3(.2, .08, .1);
        var result = (Vec3) invoke(Player.class, player, "maybeBackOffFromEdge", types, requested, MoverType.SELF);
        expect(failures, result.distanceTo(requested) < 1e-6, "Sneaking must move freely under a continuous ceiling: " + result);
        player.setPos(12.8, 145.0 - player.getBbHeight(), 0); player.setOnGround(true);
        requested = new Vec3(.6, .08, 0);
        result = (Vec3) invoke(Player.class, player, "maybeBackOffFromEdge", types, requested, MoverType.SELF);
        expect(failures, result.x < .5, "Sneaking must stop at a ceiling edge: " + result);
        requested = new Vec3(.6, -.3, 0);
        result = (Vec3) invoke(Player.class, player, "maybeBackOffFromEdge", types, requested, MoverType.SELF);
        expect(failures, result.equals(requested), "Jumping away from a ceiling must not be edge-clamped");
        player.setShiftKeyDown(false);
    }
    private static void packetMove(ServerPlayer player, double dy, boolean grounded) {
        invoke(ServerGamePacketListenerImpl.class, player.connection, "handlePlayerPositionChange",
            new Class<?>[]{double.class, double.class, double.class, float.class, float.class, boolean.class, boolean.class},
            player.getX(), player.getY() + dy, player.getZ(), 0F, 0F, grounded, false);
    }
    private static void packetChecks(ServerPlayer player, List<String> failures) {
        player.setPos(0, 145.0 - player.getBbHeight(), 0); player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO); player.connection.resetPosition();
        packetMove(player, -.2, false);
        expect(failures, player.getDeltaMovement().y < -.3, "Server must recognize a downward packet as an inverted jump");
        player.setOnGround(false); player.setDeltaMovement(0, .1, 0); player.fallDistance = 1;
        player.connection.resetPosition(); packetMove(player, .1, false);
        expect(failures, Math.abs(player.fallDistance - 1.1) < 1e-5, "Server must keep upward fall distance: " + player.fallDistance);
        player.connection.resetPosition(); packetMove(player, .1, true);
        expect(failures, player.fallDistance == 0 && player.onGround(), "Ceiling landing must clear fall distance and ground the player");
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(s -> s.getLevel(Level.END).setDragonFight(null));
            server.runCommand("gamerule minecraft:advance_weather false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 140 0 0 0");
            context.waitTicks(40);
            server.runCommand("execute in minecraft:the_end run fill -12 130 -12 12 150 12 air");
            server.runCommand("execute in minecraft:the_end run fill -12 138 -12 12 138 12 end_stone");
            server.runCommand("execute in minecraft:the_end run fill -12 145 -12 12 145 12 end_stone");
            server.runCommand("execute in minecraft:the_end run tp @a 0 139 0 0 0");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a naturality:distortion infinite 0 true");
            context.waitTicks(120);
            var failures = new ArrayList<String>();
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var player = end.players().getFirst();
                check(EndGravity.inverted(player) && player.onGround(), "Movement fixture starts grounded upside down");
                stepChecks(end, s.overworld(), failures);
                sneakChecks(player, failures);
                packetChecks(player, failures);
            });
            check(failures.isEmpty(), String.join("\n", failures));
            server.runCommand("execute in minecraft:the_end run tp @a 0 143.2 0 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.runOnClient(c -> { c.options.autoJump().set(false); c.options.keyUp.setDown(true); c.options.keyJump.setDown(true); });
            context.waitTicks(3);
            context.runOnClient(c -> {
                check(c.player.getDeltaMovement().y < 0 && !c.player.onGround(), "Real jump key leaves the ceiling");
                c.options.keyJump.setDown(false);
            });
            context.waitTicks(25);
            context.runOnClient(c -> {
                c.options.keyUp.setDown(false);
                check(c.player.onGround() && Math.abs(c.player.getBoundingBox().maxY - 145) < .01, "Moving jump lands back on the ceiling");
                check(c.level.noCollision(c.player, c.player.getBoundingBox().deflate(1e-7)), "Walking jump remains outside solid blocks");
                check(c.player.getZ() > 1, "Walking continues through the jump");
            });
            server.runOnServer(s -> check(s.getLevel(Level.END).players().getFirst().onGround(), "Server agrees with ceiling landing"));
        }
    }
}
