package naturality.client.menu;

/** Main-thread attach/connect operations, each bounded to one client tick. */
@org.jspecify.annotations.NullMarked
public interface MenuPreviewConnection {
    void naturality$startPreview(MenuPreviewLoader.Prepared prepared);
    void naturality$connectPreview();
}
