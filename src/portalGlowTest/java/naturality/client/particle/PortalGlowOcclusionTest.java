package naturality.client.particle;

import java.util.List;
import naturality.client.portal.PortalGlowOcclusion;
import naturality.client.portal.PortalGlowOcclusion.Interval;

public final class PortalGlowOcclusionTest {
    public static void run() {
        for (int side : new int[] {-1, 1}) {
            double surface = side * 0.125;
            double halo = surface + side * PortalGlowOcclusion.HALO_OFFSET;
            double ray = surface + side * PortalGlowOcclusion.RAY_ROOT_OFFSET;
            check((halo - surface) * side > 0 && (ray - halo) * side > 0,
                "Portal, halo and outward rays must occupy separate depth planes on both sides");
            check(Math.abs(ray - surface) < 1.0 / 256, "Separation must remain far smaller than a texture pixel");
        }
        var exposed = PortalGlowOcclusion.uncovered(0, 1, List.of(new Interval(0.25, 0.75)));
        check(exposed.equals(List.of(new Interval(0, 0.25), new Interval(0.75, 1))), "Feet must cut only the covered floor span");
        exposed = PortalGlowOcclusion.uncovered(0, 1, List.of(new Interval(0.2, 0.6), new Interval(0.4, 0.8)));
        check(exposed.equals(List.of(new Interval(0, 0.2), new Interval(0.8, 1))), "Overlapping hitboxes must cast one combined shadow");
        check(PortalGlowOcclusion.uncovered(0, 1, List.of(new Interval(-1, 2))).isEmpty(), "Fully blocked frame strips must disappear");
        check(PortalGlowOcclusion.uncovered(0, 1, List.of()).equals(List.of(new Interval(0, 1))), "Removing the blocker must restore the complete frame");
        var box = PortalGlowOcclusion.pixels(0.21, 0, 0.79, 1.81);
        check(box.left() == 3 / 16.0 && box.right() == 13 / 16.0 && box.top() == 29 / 16.0, "Silhouettes must share the world pixel grid");
        double side = PortalGlowOcclusion.distance(box, box.right() + 1 / 32.0, 1);
        double corner = PortalGlowOcclusion.distance(box, box.right() + 1 / 32.0, box.top() + 1 / 32.0);
        check(side == corner && corner == 1 / 32.0, "Corner cells must connect to side cells with the same gradient");
        System.out.println("Portal glow occlusion checks passed: frame cuts, unions, restoration, pixel alignment, and connected corners.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
