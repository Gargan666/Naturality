package naturality.test;

import naturality.client.glint.GlintContours;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Exercise actual enchanted item/armor geometry, shaders and mask compositing. */
public final class GlintContourGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("fill -5 99 -5 5 99 5 stone");
            server.runCommand("summon armor_stand 0 100 0 {NoGravity:1b,Tags:[\"glint_test\"]}");
            server.runCommand("item replace entity @e[tag=glint_test] armor.chest with minecraft:diamond_chestplate[minecraft:enchantments={\"minecraft:protection\":1}]");
            server.runCommand("item replace entity @e[tag=glint_test] armor.head with minecraft:diamond_helmet[minecraft:enchantments={\"minecraft:protection\":1},minecraft:trim={material:\"minecraft:gold\",pattern:\"minecraft:coast\"}]");
            server.runCommand("summon item 1 100.5 0 {NoGravity:1b,Item:{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":1}}}}");
            server.runCommand("tp @a 0.5 101 -3 0 10");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(client -> {
                if (GlintContours.lastItemDraws == 0 || GlintContours.lastArmorDraws == 0)
                    throw new AssertionError("Expected item and armor contours; draws="
                        + GlintContours.lastItemDraws + "/" + GlintContours.lastArmorDraws);
            });
            verifyMask(context, 2000, "world-glint");
            context.takeScreenshot("enchanted-contours");
            server.runCommand("kill @e[type=minecraft:item]");
            server.runCommand("item replace entity @e[tag=glint_test] armor.chest with minecraft:diamond_chestplate");
            server.runCommand("item replace entity @e[tag=glint_test] armor.head with minecraft:diamond_helmet");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (GlintContours.lastItemDraws != 0 || GlintContours.lastArmorDraws != 0)
                    throw new AssertionError("Unenchanted equipment must not produce contours");
            });
            // Mob-worn armor and third-person held items share the world silhouette pass.
            server.runCommand("summon zombie 0 100 0 {NoAI:1b,Tags:[\"glint_mob\"]}");
            server.runCommand("item replace entity @e[tag=glint_mob] armor.chest with minecraft:diamond_chestplate[minecraft:enchantments={\"minecraft:protection\":1}]");
            server.runCommand("item replace entity @e[tag=glint_mob] weapon.mainhand with minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":1}]");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(client -> {
                if (GlintContours.lastItemDraws == 0 || GlintContours.lastArmorDraws == 0)
                    throw new AssertionError("Expected mob held-item and armor contours; draws="
                        + GlintContours.lastItemDraws + "/" + GlintContours.lastArmorDraws);
            });
            verifyMask(context, 100, "mob-glint");
            context.takeScreenshot("enchanted-mob-contours");
            server.runCommand("kill @e[tag=glint_mob]");
            world.getConnection().waitForClientboundPackets();

            server.runCommand("setblock -1 101 1 stone");
            server.runCommand("summon item_frame -1 101 0 {block_pos:[I;-1,101,0],Facing:2b,Fixed:1b,Item:{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":1}}}}");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (GlintContours.lastItemDraws == 0) throw new AssertionError("Enchanted frame item must have a contour");
            });
            verifyMask(context, 50, "item-frame-glint");
            context.takeScreenshot("enchanted-item-frame");
            server.runCommand("gamemode survival @a");
            server.runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate[minecraft:enchantments={\"minecraft:protection\":1}]");
            world.getConnection().waitForClientboundPackets();
            server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":1}]");
            world.getConnection().waitForClientboundPackets();
            server.runCommand("item replace entity @a hotbar.1 with minecraft:diamond_helmet[minecraft:enchantments={\"minecraft:protection\":1}]");
            server.runCommand("item replace entity @a hotbar.2 with minecraft:netherite_chestplate[minecraft:enchantments={\"minecraft:protection\":1}]");
            world.getConnection().waitForClientboundPackets();
            int[] savedGui = new int[3];
            context.runOnClient(client -> {
                savedGui[0] = client.options.guiScale().get();
                savedGui[1] = client.getWindow().getWidth();
                savedGui[2] = client.getWindow().getHeight();
                client.getWindow().setWindowed(1280, 800);
                client.options.guiScale().set(3);
                client.resizeGui();
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (client.getWindow().getGuiScale() != 3) throw new AssertionError("GUI scale 3 regression coverage required");
                client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (GlintContours.lastArmorDraws == 0) throw new AssertionError("Inventory player armor must have a contour");
            });
            verifyMask(context, 50, "inventory-glint");
            context.takeScreenshot("enchanted-inventory-preview");
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                client.options.guiScale().set(savedGui[0]);
                client.getWindow().setWindowed(savedGui[1], savedGui[2]);
                client.resizeGui();
            });
            for (int yaw : new int[] {-60, 120}) {
                server.runCommand("tp @a 0.5 101 -3 " + yaw + " -25");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(20);
                verifyMask(context, 2000, "held-glint-" + yaw);
                context.takeScreenshot("enchanted-held-" + yaw);
            }
        }
    }

    private static void verifyMask(ClientGameTestContext context, int minimumPixels, String label) {
            var maskRead = new java.util.concurrent.CompletableFuture<Integer>();
            context.runOnClient(client -> {
                try {
                    var field = GlintContours.class.getDeclaredField(
                        label.equals("inventory-glint") ? "previewMask" : "worldMask");
                    field.setAccessible(true);
                    var target = (com.mojang.blaze3d.pipeline.RenderTarget) field.get(null);
                    var texture = target.getColorTexture();
                    int count = target.width * target.height;
                    var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
                    var buffer = device.createBuffer(() -> "Glint mask verification", 9, (long) count * 16);
                    device.createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
                        try (var read = buffer.map(true, false)) {
                            var data = read.data().order(java.nio.ByteOrder.nativeOrder());
                            int covered = 0, bright = 0;
                            var maskImage = new com.mojang.blaze3d.platform.NativeImage(target.width, target.height, false);
                            float max = 0, near = 10000, far = 0;
                            for (int i = 0; i < count; i++) {
                                float a = data.getFloat(i * 16 + 12);
                                float r = data.getFloat(i * 16);
                                maskImage.setPixel(i % target.width, target.height - 1 - i / target.width, a > 0 ? 0xFFFFFFFF : 0xFF000000);
                                if (a > 0) { covered++; near = Math.min(near,a); far = Math.max(far,a); }
                                if (r > 0.001f) bright++;
                                max = Math.max(max,r);
                            }
                            try (maskImage) { maskImage.writeToFile(java.nio.file.Path.of("screenshots/" + label + "-mask.png")); } catch (java.io.IOException e) { throw new RuntimeException(e); }
                            System.out.println("GLINT MASK covered="+covered+" bright="+bright+" max="+max+" depth="+near+".."+far);
                                                        if (covered < minimumPixels || covered > count / 2 || bright < 50) {
                                maskRead.completeExceptionally(new AssertionError(
                                    "Expected a bounded, colored silhouette with empty background; covered=" + covered + " bright=" + bright));
                            } else maskRead.complete(covered);
                        } finally { buffer.close(); }
                    }, 0);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            context.waitFor(client -> maskRead.isDone());
            if (maskRead.join() < minimumPixels) throw new AssertionError("Silhouette mask has no visible geometry");
    }
}



