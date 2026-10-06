package naturality.test;

import naturality.client.particle.BlockFragmentPixels;
import naturality.client.particle.SandParticleSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

final class SandParticleChecks {
    static void run(Minecraft client) {
        var pos = new BlockPos(0, 105, 8);
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.SAND, Blocks.RED_SAND}) {
            var state = block.defaultBlockState();
            var sand = new TerrainParticle(client.level, 0, 105, 8, 0.2, 0.4, -0.3, state, pos);
            var stone = new TerrainParticle(client.level, 0, 105, 8, 0.2, 0.4, -0.3, Blocks.STONE.defaultBlockState(), pos);
            require(((BlockFragmentPixels) sand).naturality$cropWidth() == 0, "Sand bypasses block texture fragments");
            require(((BlockFragmentPixels) stone).naturality$cropWidth() > 0, "Stone retains block texture fragments");
            require(sand.getLayer() == SingleQuadParticle.Layer.OPAQUE, "Sand uses falling dust atlas");
            var sprite = (net.minecraft.client.renderer.texture.TextureAtlasSprite) field(sand, "sprite");
            require(uv(sand, "getU0") == sprite.getU0() && uv(sand, "getU1") == sprite.getU1(), "Full falling dust U range");
            require(uv(sand, "getV0") == sprite.getV0() && uv(sand, "getV1") == sprite.getV1(), "Full falling dust V range");
            int color = ((net.minecraft.world.level.block.FallingBlock) block).getDustColor(state, client.level, pos);
            require(field(sand, "rCol").equals((color >> 16 & 255) / 255F), "Correct sand color");
            require(field(sand, "gCol").equals((color >> 8 & 255) / 255F), "Correct sand color");
            require(field(sand, "bCol").equals((color & 255) / 255F), "Correct sand color");
            sand.setLifetime(25);
            stone.setLifetime(25);
            sand.setParticleSpeed(0.2, 0.4, -0.3);
            stone.setParticleSpeed(0.2, 0.4, -0.3);
            sand.setPower(0.2F);
            stone.setPower(0.2F);
            for (int tick = 0; tick < 20; tick++) {
                for (String name : new String[]{"x", "y", "z", "xd", "yd", "zd"})
                    require(field(sand, name).equals(field(stone, name)), "Vanilla terrain motion retained: " + name);
                require(field(sand, "sprite") == SandParticleSprites.sprites.get(tick, 25), "Falling dust animation follows age");
                sand.tick();
                stone.tick();
            }
            var pillar = client.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), 0, 105, 8, 0, 0.8, 0);
            require(pillar instanceof TerrainParticle && ((BlockFragmentPixels) pillar).naturality$cropWidth() == 0,
                "Mace dust pillar uses animated sand");
            require(pillar.getLifetime() >= 20 && pillar.getLifetime() < 40, "Mace retains provider lifetime");
            var step = client.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), 0, 105, 8, -0.2, 0.1, 0.3);
            require(step instanceof TerrainParticle && ((BlockFragmentPixels) step).naturality$cropWidth() == 0,
                "Walking block provider uses animated sand");
            client.particleEngine.clearParticles();
            client.level.levelEvent(0x4E5301, pos, net.minecraft.world.level.block.Block.getId(state));
            var queued = (java.util.Queue<?>) field(client.particleEngine, "particlesToAdd");
            require(queued.size() == 64, "Placement event retains the 64-particle breaking burst");
            var faces = new java.util.HashSet<String>();
            for (Object particle : queued) {
                double x = (Double) field(particle, "x") - pos.getX() - 0.5;
                double y = (Double) field(particle, "y") - pos.getY() - 0.5;
                double z = (Double) field(particle, "z") - pos.getZ() - 0.5;
                require(Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == 0.75,
                    "Every placement particle clears its block face by 0.25");
                if (Math.abs(x) == 0.75) faces.add("x" + Math.signum(x));
                if (Math.abs(y) == 0.75) faces.add("y" + Math.signum(y));
                if (Math.abs(z) == 0.75) faces.add("z" + Math.signum(z));
                require(!field(particle, "pos").equals(pos), "Placement lighting samples outside the sand block");
                require(field(particle, "x").equals(field(particle, "xo"))
                    && field(particle, "y").equals(field(particle, "yo"))
                    && field(particle, "z").equals(field(particle, "zo")), "First frame starts outside without interpolation through the block");
            }
            require(faces.size() == 6, "Placement burst covers all six block faces");
            client.level.addDestroyBlockEffect(pos, state);
        }
    }

    private static float uv(TerrainParticle particle, String name) {
        try {
            var method = TerrainParticle.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return (Float) method.invoke(particle);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        throw new AssertionError(name);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
