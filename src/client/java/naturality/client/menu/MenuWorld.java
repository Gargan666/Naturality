package naturality.client.menu;

/** The generated menu world uses native rendering; only orbit state is separate. */
public final class MenuWorld {
    private static final MenuWorldCamera CAMERA = new MenuWorldCamera();
    public static MenuWorldCamera camera() { return CAMERA; }
    public static void initialize() { MenuPreview.initialize(); }
    private MenuWorld() { }
}
