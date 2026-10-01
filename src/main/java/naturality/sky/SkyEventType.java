package naturality.sky;

import java.util.List;

/** Dimension pools are explicit; the End is ready for its own future events. */
public enum SkyEventType {
    METEOR_SHOWER("meteor_shower", "Meteor Shower"),
    AURORA_BOREALIS("aurora_borealis", "Aurora Borealis"),
    RAINBOW("rainbow", "Rainbow");
    public final String id, label;
    SkyEventType(String id, String label) { this.id = id; this.label = label; }
    public static List<SkyEventType> pool(String dimension) {
        return dimension.equals("minecraft:overworld") ? List.of(METEOR_SHOWER, AURORA_BOREALIS, RAINBOW) : List.of();
    }
    public static boolean supported(String dimension) {
        return dimension.equals("minecraft:overworld") || dimension.equals("minecraft:the_end");
    }
}
