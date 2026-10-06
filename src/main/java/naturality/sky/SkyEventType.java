package naturality.sky;

import java.util.List;

/** Dimension pools are explicit. */
public enum SkyEventType {
    METEOR_SHOWER("meteor_shower", "Meteor Shower"),
    AURORA_BOREALIS("aurora_borealis", "Aurora Borealis"),
    RAINBOW("rainbow", "Rainbow"),
    END_FLASHES("end_flashes", "End Flashes"),
    END_AURORA("end_aurora", "End Aurora");
    public final String id, label;
    SkyEventType(String id, String label) { this.id = id; this.label = label; }
    public static List<SkyEventType> pool(String dimension) {
        if (dimension.equals("minecraft:overworld")) return List.of(METEOR_SHOWER, AURORA_BOREALIS, RAINBOW);
        if (dimension.equals("minecraft:the_end")) return List.of(END_FLASHES, END_AURORA);
        return List.of();
    }
    public static boolean supported(String dimension) {
        return dimension.equals("minecraft:overworld") || dimension.equals("minecraft:the_end");
    }
}
