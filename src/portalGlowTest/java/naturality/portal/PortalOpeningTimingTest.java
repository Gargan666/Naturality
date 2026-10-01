package naturality.portal;

public final class PortalOpeningTimingTest {
    public static void run() {
        check(PortalOpeningTiming.fireAlpha(0) == 1 && PortalOpeningTiming.fireAlpha(10) == 0, "Fire fades completely before placement");
        check(PortalOpeningTiming.reveal(10) == 0 && PortalOpeningTiming.reveal(30) == 1, "Reveal lasts exactly one second");
        check(!PortalOpeningTiming.ready(29.99F) && PortalOpeningTiming.ready(30), "Travel and particles remain locked throughout reveal");
        check(PortalOpeningTiming.pulse(29.99F) == 0 && PortalOpeningTiming.pulse(30) == 1
            && PortalOpeningTiming.pulse(70) == 0, "Pulse starts on reveal completion and ends exactly two seconds later");
        check(Math.abs(PortalOpeningTiming.expoIn(0.5F) - 0.03125F) < 0.00001F, "Reveal uses expo-in");
        check(Math.abs(PortalOpeningTiming.pulse(50) - 0.03125F) < 0.00001F, "Two-second pulse uses expo-out");
        check(PortalOpeningTiming.pulse(50) > 0 && PortalOpeningTiming.END_TICK == 70, "Pulse must not finish after one second");
        for (float luminance : new float[]{0, 0.1F, 0.5F, 1}) {
            check(PortalOpeningTiming.pixelAlpha(0, luminance) == 0, "All texels start transparent");
            check(PortalOpeningTiming.pixelAlpha(1, luminance) == 1, "All texels finish at source alpha");
            float previous = 0;
            for (int i = 0; i <= 100; i++) {
                float next = PortalOpeningTiming.pixelAlpha(i / 100F, luminance);
                check(next >= previous && next <= 1, "Reveal must be monotonic and bounded");
                previous = next;
            }
        }
        check(PortalOpeningTiming.pixelAlpha(0.5F, 0.8F) > PortalOpeningTiming.pixelAlpha(0.5F, 0.1F), "Lighter texels appear faster");
        for (float t : new float[]{0, 0.1F, 0.25F, 0.5F, 0.75F, 0.9F, 1}) {
            check(PortalOpeningTiming.pixelAlpha(t, 0) == PortalOpeningTiming.expoIn(t), "Darkest pixels use pure expo-in");
            check(PortalOpeningTiming.pixelAlpha(t, 0.5F) == t, "Middle brightness uses linear easing");
            check(PortalOpeningTiming.pixelAlpha(t, 1) == PortalOpeningTiming.expoOut(t), "Brightest pixels use pure expo-out");
            check(Math.abs(PortalOpeningTiming.pixelAlpha(t, 0.25F) - (PortalOpeningTiming.expoIn(t) + t) / 2) < 0.000001F,
                "Intermediate brightness blends continuously between curves");
        }
        System.out.println("Portal opening timing checks passed: fire, reveal, travel lock, texel order, and brightness pulse.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
