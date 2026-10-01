package naturality.client.particle;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Runs without launching Minecraft; failures stop Gradle's check/build task. */
public final class PortalGlowGeometryTest {
    public static void main(String[] args) {
        EndPortalViewTest.run();
        naturality.portal.PortalOpeningTimingTest.run();
        PortalGlowOcclusionTest.run();
        PortalModelSectionTest.run();
        WaterIntersectionGeometryTest.run();
        require(PortalGlowGeometry.visibleDepthSide(4) == 1 && PortalGlowGeometry.visibleDepthSide(-4) == -1,
            "Only glow on the camera's side of the portal surface may render");
        for (double depth : new double[]{-0.125, -0.1, 0, 0.1, 0.125}) {
            require(PortalGlowGeometry.visibleDepthSide(depth) == 0, "Glow must not render inside the portal slab");
        }
        for (double offset : new double[] {-3, -0.1, 0, 0.1, 3}) {
            require(Math.abs(PortalMoteGeometry.spawnOffset(offset)) - PortalGlowGeometry.PORTAL_SURFACE > 0.3,
                "Spawn must be strictly farther than 0.3 blocks from the portal surface");
        }
        require(PortalMoteGeometry.arrivalAlpha(0.1) == 1 && PortalMoteGeometry.arrivalAlpha(0) == 0,
            "Arrival fade must span precisely the final 0.1 blocks");
        require(Math.abs(PortalMoteGeometry.arrivalAlpha(0.05) - 0.5F) < 0.00001,
            "Arrival fade must halve opacity halfway to the surface");
        require(PortalMoteGeometry.paletteU(0) == 255.5F / 256 && PortalMoteGeometry.paletteU(1) == 0.5F / 256,
            "Motes must progress from darkest to lightest palette entry");
        require(PortalMoteGeometry.crossWidth(0) == 0.25F && PortalMoteGeometry.crossWidth(1) == 1,
            "Distant rectangles must become squares near the surface");
        require(PortalMoteGeometry.growth(2, 2) == 0, "Motes start at zero size");
        require(PortalMoteGeometry.growth(0, 2) == 1, "Motes reach maximum size at the portal");
        require(PortalMoteGeometry.growth(0.5, 2) > PortalMoteGeometry.growth(1.5, 2),
            "Motes grow as their distance to the portal decreases");
        require(PortalMoteGeometry.growth(3, 2) == 0, "Moving farther away cannot create negative size");
        for (Vec3 motion : new Vec3[] {new Vec3(1, 2, 3), new Vec3(0, 0, -1), new Vec3(0, 1, 0)}) {
            Vector3f facing = new Vector3f(1, 0, 0).rotate(PortalMoteGeometry.orientation(motion, new Vec3(3, 4, 5)));
            Vec3 expected = motion.normalize();
            require(facing.distance(new Vector3f((float) expected.x, (float) expected.y, (float) expected.z)) < 0.00001,
                "Texture X axis must follow velocity independently of the camera");
        }
        require(Float.isFinite(PortalMoteGeometry.orientation(Vec3.ZERO, Vec3.ZERO).w), "Zero velocity must be safe");
        Vec3 travel = new Vec3(0, 0, 1);
        Vector3f headOn = new Vector3f(0, 0, 1).rotate(PortalMoteGeometry.orientation(travel, travel));
        require(Math.abs(headOn.z) < 0.00001, "Head-on view must see the square edge-on");
        Vector3f sideOn = new Vector3f(0, 0, 1).rotate(PortalMoteGeometry.orientation(travel, new Vec3(1, 0, 0)));
        require(sideOn.distance(new Vector3f(1, 0, 0)) < 0.00001, "Side view must see the full face");
        int[] palette = PortalGlowPalette.create(new int[] {0xFF220044, 0xFFCC66FF, 0xFF661ACC, 0x00FFFFFF});
        require(palette[0] == 0xFFCC66FF, "Palette starts with lightest nontransparent source color");
        require(palette[palette.length - 1] == 0xFF220044, "Palette ends with darkest source color");
        boolean hasMiddle = false;
        for (int color : palette) {
            require(color == 0xFF220044 || color == 0xFFCC66FF || color == 0xFF661ACC,
                "Every gradient shade must come from the source texture");
            hasMiddle |= color == 0xFF661ACC;
        }
        require(hasMiddle, "Gradient includes intermediate texture colors");
        require(PortalGlowPalette.create(new int[] {0x00FFFFFF})[0] == 0,
            "Fully transparent pixels must not contribute colors");
        require(PortalGlowGeometry.INSET > 0 && PortalGlowGeometry.INSET <= 1.0 / 1024,
            "Glow must sit just above its supporting face with a bounded sub-pixel separation");
        require(PortalGlowGeometry.expoOut(0) == 0, "Fade starts transparent");
        require(PortalGlowGeometry.expoOut(1) == 1, "Fade finishes exactly opaque");
        require(PortalGlowGeometry.expoOut(-1) == 0 && PortalGlowGeometry.expoOut(2) == 1,
            "Fade clamps outside its lifetime");
        require(Math.abs(PortalGlowGeometry.expoOut(0.5F) - 0.96875F) < 0.00001F,
            "Fade must use expoOut, not linear interpolation");
        float previousFade = 0;
        for (int i = 0; i <= 10000; i++) {
            float fade = PortalGlowGeometry.expoOut(i / 10000.0F);
            require(fade >= previousFade, "Fade must increase monotonically");
            previousFade = fade;
        }
        require(PortalGlowGeometry.SLICES_PER_BLOCK == 16, "Use one slice per standard texture pixel");
        int shortestTip = Integer.MAX_VALUE;
        int longestTip = Integer.MIN_VALUE;
        for (int sample = 0; sample < 1000; sample++) {
            int tip = (int) Math.floor((PortalGlowGeometry.BASE_REACH
                + PortalGlowGeometry.WAVE_AMPLITUDE * Math.sin(sample * Math.PI * 2 / 1000))
                * PortalGlowGeometry.SLICES_PER_BLOCK);
            shortestTip = Math.min(shortestTip, tip);
            longestTip = Math.max(longestTip, tip);
        }
        require(longestTip - shortestTip == 3, "Pixel-snapped wave must span three pixels");
        require(PortalGlowGeometry.BASE_REACH + PortalGlowGeometry.WAVE_AMPLITUDE < 1,
            "Wave must fade fully before the geometry ends");
        require(PortalGlowGeometry.BASE_REACH - PortalGlowGeometry.WAVE_AMPLITUDE
            > PortalGlowGeometry.PORTAL_SURFACE, "Fade interval must always be positive");
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            for (Direction outward : PortalGlowGeometry.edges(axis)) {
                Direction inward = outward.getOpposite();
                Quaternionf rotation = PortalGlowGeometry.rotation(axis, inward);
                Vector3f expectedNormal = new Vector3f(inward.getStepX(), inward.getStepY(), inward.getStepZ());
                require(new Vector3f(0, 0, 1).rotate(rotation).distance(expectedNormal) < 0.00001F,
                    axis + " / " + outward + ": quad winding must face into opening");
                require(new Vector3f(0, 1, 0).rotate(rotation).distance(PortalGlowGeometry.depth(axis)) < 0.00001F,
                    axis + " / " + outward + ": texture gradient must follow portal depth");
                require(Math.abs(rotation.lengthSquared() - 1) < 0.00001F, "Rotation must preserve size");

                // Deliberately far from world origin: visibility must be camera-relative.
                double x = 12000.5;
                double y = -30;
                double z = -24000.5;
                require(PortalGlowGeometry.isFront(x + inward.getStepX(), y + inward.getStepY(), z + inward.getStepZ(),
                    x, y, z, inward), "Inward side must be visible");
                require(!PortalGlowGeometry.isFront(x - inward.getStepX(), y - inward.getStepY(), z - inward.getStepZ(),
                    x, y, z, inward), "Outward side must be invisible");
                require(!PortalGlowGeometry.isFront(x, y, z, x, y, z, inward), "Edge-on face must be hidden");
            }
            // Stand beyond one side of a two-block-wide opening. Only the opposite
            // wall should render, from either front/back approach to the portal.
            for (int side : new int[] {-1, 1}) {
                for (int approach : new int[] {-1, 1}) {
                    double horizontal = side * 3;
                    double cameraX = axis == Direction.Axis.X ? horizontal : approach * 4;
                    double cameraZ = axis == Direction.Axis.Z ? horizontal : approach * 4;
                    Direction positive = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
                    Direction nearOutward = side > 0 ? positive : positive.getOpposite();
                    Direction farOutward = nearOutward.getOpposite();
                    require(!PortalGlowGeometry.isFront(cameraX, 1.5, cameraZ,
                        nearOutward.getStepX(), 1.5, nearOutward.getStepZ(), nearOutward.getOpposite()),
                        "Near wall back face must disappear");
                    require(PortalGlowGeometry.isFront(cameraX, 1.5, cameraZ,
                        farOutward.getStepX(), 1.5, farOutward.getStepZ(), farOutward.getOpposite()),
                        "Far wall inward face must remain visible");
                }
            }
        }
        require(!PortalGlowGeometry.isFront(0, -1, 0, 0, 0, 0, Direction.UP), "Floor hidden from below");
        require(PortalGlowGeometry.isFront(0, -1, 0, 0, 3, 0, Direction.DOWN), "Ceiling visible from below");
        require(PortalGlowGeometry.isFront(0, 4, 0, 0, 0, 0, Direction.UP), "Floor visible from above");
        require(!PortalGlowGeometry.isFront(0, 4, 0, 0, 3, 0, Direction.DOWN), "Ceiling hidden from above");
        System.out.println("Portal glow geometry checks passed: both axes, all edges, front/back/edge-on views.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

