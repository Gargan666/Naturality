package naturality.client.portal;

import org.jspecify.annotations.Nullable;

/** Clip metadata belongs to this submission, never to its structural record value. */
public interface PortalClippedSubmit {
    PortalClippedCollector.@Nullable Plane naturality$clipPlane();
    void naturality$clipPlane(PortalClippedCollector.@Nullable Plane plane);
}
