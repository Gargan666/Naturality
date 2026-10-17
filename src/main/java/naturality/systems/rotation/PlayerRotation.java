package naturality.systems.rotation;

import com.mojang.brigadier.arguments.FloatArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import naturality.api.worlds.LoadedChunks;
import naturality.systems.gravity.EndGravity;
import naturality.systems.swimming.PlayerSwimming;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Player-only body orientation. Position remains vanilla's unrotated base; the pivot is its body centre.
 * Commands apply X then world Z. A local yaw compensation also represents arbitrary horizontal-axis
 * tilts without introducing body roll. View yaw/pitch remain independent; state is server owned.
 * No swimming policy, gravity change, camera roll or automatic yaw tracking is imposed by this API.
 */
@org.jspecify.annotations.NullMarked
public final class PlayerRotation {
    private PlayerRotation() { }
    public static final double EPSILON = 1.0e-7;
    private static final double CONTACT_TOLERANCE = EPSILON * 16;
    private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

    /** Implemented on Player; the cached value is nullable only during vanilla construction. */
    public interface Access {
        @Nullable Angles naturality$rotation();
        void naturality$rotation(Angles angles);
        void naturality$rotationUpdated();
        Angles naturality$renderRotation(float partial);
    }

    public record Angles(float x, float z, float yawCompensation) {
        public static final Angles ZERO = new Angles(0, 0);
        public Angles(float x, float z) { this(x, z, 0); }
        public Angles {
            if (!Float.isFinite(x) || !Float.isFinite(z) || !Float.isFinite(yawCompensation)) throw new IllegalArgumentException("Rotation must be finite");
            x = normalize(x);
            z = normalize(z);
            yawCompensation = normalize(yawCompensation);
        }
        private static float normalize(float angle) {
            float wrapped = angle % 360;
            return wrapped < 0 ? wrapped + 360 : wrapped == 0 ? 0 : wrapped;
        }
        public boolean active() { return x != 0 || z != 0 || yawCompensation != 0; }
        public Vec3 transform(Vec3 point) {
            double cy = Math.cos(Math.toRadians(yawCompensation)), sy = Math.sin(Math.toRadians(yawCompensation));
            double px = cy * point.x + sy * point.z, pz = -sy * point.x + cy * point.z;
            double cx = Math.cos(Math.toRadians(x)), sx = Math.sin(Math.toRadians(x));
            double cz = Math.cos(Math.toRadians(z)), sz = Math.sin(Math.toRadians(z));
            double py = cx * point.y - sx * pz;
            return new Vec3(cz * px - sz * py, sz * px + cz * py, sx * point.y + cx * pz);
        }
        public Quaterniond quaternion() {
            return new Quaterniond().rotationZ(Math.toRadians(z)).rotateX(Math.toRadians(x)).rotateY(Math.toRadians(yawCompensation));
        }
        public static Angles fromQuaternion(Quaterniondc rotation) {
            var q = new Quaterniond(rotation).normalize();
            var right = q.transform(new Vector3d(1, 0, 0));
            var up = q.transform(new Vector3d(0, 1, 0));
            var forward = q.transform(new Vector3d(0, 0, 1));
            double x = Math.asin(Math.clamp(up.z, -1, 1));
            double z, y;
            if (Math.abs(Math.cos(x)) < 1.0e-7) {
                z = Math.atan2(right.y, right.x);
                y = 0;
            } else {
                z = Math.atan2(-up.x, up.y);
                y = Math.atan2(-right.z, forward.z);
            }
            return new Angles((float)Math.toDegrees(x), (float)Math.toDegrees(z), (float)Math.toDegrees(y));
        }
        public Angles towards(Angles target, double fraction) {
            if (fraction <= 0) return this;
            if (fraction >= 1) return target;
            return fromQuaternion(quaternion().slerp(target.quaternion(), fraction));
        }
        public double radiansTo(Angles target) {
            return 2 * Math.acos(Math.clamp(Math.abs(quaternion().dot(target.quaternion())), 0, 1));
        }
    }

    public static Angles angles(Entity entity) {
        if (entity instanceof Player player && naturality.systems.swimming.WaterSurface.riptiding(player)) return Angles.ZERO;
        if (entity instanceof Player && entity instanceof Access access) {
            var value = access.naturality$rotation();
            if (value != null) return value;
        }
        return Angles.ZERO;
    }
    public static boolean active(Entity entity) { return angles(entity).active(); }
    public static boolean terrainActive(Entity entity) {
        return active(entity) || entity instanceof Player player && PlayerSwimming.compactTerrain(player);
    }
    public static Angles interpolatedAngles(Entity entity, float partial) {
        if (entity instanceof Player player && naturality.systems.swimming.WaterSurface.riptiding(player)) return Angles.ZERO;
        return entity instanceof Access access ? access.naturality$renderRotation(Math.clamp(partial, 0, 1)) : Angles.ZERO;
    }
    public static Box body(Player player) { return body(player, player.position(), player.getDimensions(player.getPose()), angles(player)); }
    /** Subtract the union of suffocating solids, then test the remaining space against the actual OBB. */
    public static boolean fullyInsideSolids(Player player) {
        if (player.noPhysics) return false;
        var body = body(player);
        var bounds = body.bounds();
        var uncovered = net.minecraft.world.phys.shapes.Shapes.create(bounds);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (!LoadedChunks.has(player.level(), pos)) return false;
            var block = player.level().getBlockState(pos);
            if (!block.isSuffocating(player.level(), pos)) continue;
            uncovered = net.minecraft.world.phys.shapes.Shapes.join(uncovered,
                block.getCollisionShape(player.level(), pos).move(pos), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        }
        for (var gap : uncovered.toAabbs()) if (body.separation(gap) < -EPSILON) return false;
        return true;
    }
    public static Box body(Player player, Vec3 position, EntityDimensions dimensions, Angles orientation) {
        return new Box(position.add(0, dimensions.height() / 2.0, 0),
            new Vec3(dimensions.width() / 2.0, dimensions.height() / 2.0, dimensions.width() / 2.0), orientation, PlayerSwimming.compactTerrain(player));
    }
    public static Vec3 eyeOffset(Player player, float eyeHeight) {
        return eyeOffset(player, eyeHeight, angles(player));
    }
    public static Vec3 eyeOffset(Player player, float eyeHeight, Angles angles) {
        double halfHeight = player.getBbHeight() / 2.0;
        return angles.transform(new Vec3(0, eyeHeight - halfHeight, 0)).add(0, halfHeight, 0);
    }
    public static Vec3 feet(Player player) {
        var body = body(player);
        return body.center.add(body.orientation.transform(new Vec3(0, -body.half.y, 0)));
    }

    /** Terrain-only envelope. Targeting, fluids, damage contacts and feet retain the oriented body. */
    public static Box terrain(Box body) {
        // One vanilla-sized swimming cube at the body centre, independent of body rotation.
        if (body.compactTerrain) {
            return new Box(body.center, new Vec3(body.half.x, body.half.x, body.half.x), Angles.ZERO);
        }
        return envelope(body);
    }

    /** Full body envelope for water-mode sampling and non-swimming rotation. */
    public static Box envelope(Box body) {
        var bounds = body.bounds();
        var up = body.orientation.transform(WORLD_AXES[1]);
        double x = terrainRadius(body, WORLD_AXES[0], bounds.getXsize() / 2);
        double z = terrainRadius(body, WORLD_AXES[2], bounds.getZsize() / 2);
        // Preserve the entire head/feet centreline, including eye positions, with a small margin.
        double margin = Math.min(0.04, Math.min(body.half.x, body.half.z) * 0.1);
        x = Math.max(x, Math.min(bounds.getXsize() / 2, Math.abs(up.x) * body.half.y + margin));
        z = Math.max(z, Math.min(bounds.getZsize() / 2, Math.abs(up.z) * body.half.y + margin));
        return new Box(body.center, new Vec3(x, bounds.getYsize() / 2, z), Angles.ZERO);
    }

    private static double terrainRadius(Box body, Vec3 axis, double enclosingRadius) {
        double a = body.half.x * body.axes[0].dot(axis);
        double b = body.half.y * body.axes[1].dot(axis);
        double c = body.half.z * body.axes[2].dot(axis);
        double excess = Math.max(0, enclosingRadius - Math.sqrt(a * a + b * b + c * c));
        // Zero for axis-aligned faces; smoothly approaches an eight-centimetre maximum inset.
        return enclosingRadius - 0.08 * excess / (0.08 + excess);
    }

    /** Sets an absolute orientation; swimming rotates freely, manual poses stop before obstacles.
     * Call on the logical server. The accepted orientation is synchronized to the player and observers.
     * Passenger/sleeping poses are deliberately excluded until their attachment rules are implemented.
     */
    public static RotationResult rotate(Player player, Angles target) {
        var previous = angles(player);
        if (player.level().isClientSide() || player.isPassenger() || player.isSleeping()) return new RotationResult(previous, false);
        if (previous.equals(target)) return new RotationResult(previous, true);
        var start = body(player);
        if (start.compactTerrain) {
            ((Access)player).naturality$rotation(target);
            return new RotationResult(target, true);
        }
        double radius = rotationRadius(start);
        var area = AABB.ofSize(start.center, radius * 2, radius * 2, radius * 2);
        var colliders = colliders(player, area);
        if (colliders == null) return new RotationResult(previous, false);
        double terrainFraction = safeTerrainRotationFraction(start, target, colliders);
        double fraction = Math.min(terrainFraction, safeRotationFraction(start, target, entityColliders(player, area)));
        if (fraction < 1 && previous.radiansTo(target) * fraction < 1.0e-5) fraction = 0;
        var accepted = fraction == 1 ? target : previous.towards(target, fraction);
        ((Access)player).naturality$rotation(accepted);
        if (!accepted.equals(previous)) player.setOnGround(false);
        return new RotationResult(accepted, fraction == 1);
    }
    public record RotationResult(Angles accepted, boolean complete) { }
    private static double rotationRadius(Box body) {
        return body.half.length();
    }

    /** Preserve vanilla's distinction between an existing overlap and a newly introduced collision. */
    public static boolean introducesCollision(Player player, AABB oldBounds, Vec3 targetPosition) {
        var current = body(player);
        if (current.compactTerrain) current = terrain(current);
        var before = current.move(oldBounds.getCenter().subtract(current.center));
        var after = current.move(targetPosition.subtract(player.position()));
        if (before.center.distanceToSqr(after.center) < 1.0e-20) return false;
        var obstacles = colliders(player, before.bounds().minmax(after.bounds()));
        return obstacles == null || introducesCollision(terrain(before), terrain(after), obstacles)
            || introducesCollision(before, after, entityColliders(player, before.bounds().minmax(after.bounds())));
    }
    public static boolean introducesCollision(Box before, Box after, List<AABB> obstacles) {
        for (var obstacle : obstacles) {
            double previous = before.separation(obstacle), next = after.separation(obstacle);
            if (next < -CONTACT_TOLERANCE && (previous >= -CONTACT_TOLERANCE || next < previous - CONTACT_TOLERANCE)) return true;
        }
        return false;
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            var root = Commands.literal("rotate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            for (String axis : List.of("x", "z")) {
                root.then(Commands.literal(axis).then(Commands.argument("degrees", FloatArgumentType.floatArg(0, 360))
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        float degrees = FloatArgumentType.getFloat(context, "degrees");
                        if (!Float.isFinite(degrees)) return 0;
                        var current = angles(player);
                        var target = axis.equals("x") ? new Angles(degrees, current.z) : new Angles(current.x, degrees);
                        var result = rotate(player, target);
                        var message = Component.literal(String.format(Locale.ROOT,
                            "Rotation: X %.3f°, Z %.3f°%s", result.accepted.x, result.accepted.z,
                            result.complete ? "" : " (blocked by collision, unavailable terrain, or a riding/sleeping pose)"));
                        context.getSource().sendSuccess(() -> message, false);
                        return result.complete ? 1 : 0;
                    })));
            }
            dispatcher.register(root);
        });
    }

    /** Swimming uses its separate cube for all physical contacts; manual rotation uses the body. */
    public static boolean fits(Player player, Box box) {
        var colliders = colliders(player, box.bounds());
        if (colliders == null) return false;
        var terrain = terrain(box);
        for (var collider : colliders) if (terrain.separation(collider) < -CONTACT_TOLERANCE) return false;
        var physical = box.compactTerrain ? terrain : box;
        for (var collider : entityColliders(player, physical.bounds())) if (physical.separation(collider) < -CONTACT_TOLERANCE) return false;
        return true;
    }

    private static boolean available(Player player, AABB area) {
        if (!Double.isFinite(area.getSize()) || area.getXsize() * area.getYsize() * area.getZsize() > 32768) return false;
        int x0 = (int)Math.floor(area.minX) >> 4, x1 = (int)Math.floor(area.maxX) >> 4;
        int z0 = (int)Math.floor(area.minZ) >> 4, z1 = (int)Math.floor(area.maxZ) >> 4;
        if ((long)(x1 - x0 + 1) * (z1 - z0 + 1) > 256) return false;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (!LoadedChunks.has(player.level(), new BlockPos(x << 4, 0, z << 4))) return false;
        }
        return true;
    }
    private static @Nullable List<AABB> colliders(Player player, AABB area) {
        if (!available(player, area.inflate(1))) return null;
        var result = new ArrayList<AABB>();
        for (var shape : player.level().getBlockCollisions(player, area.inflate(EPSILON))) result.addAll(shape.toAabbs());
        var border = player.level().getWorldBorder();
        if (border.isInsideCloseToBorder(player, area)) result.addAll(border.getCollisionShape().toAabbs());
        return result;
    }

    private static List<AABB> entityColliders(Player player, AABB area) {
        var result = new ArrayList<AABB>();
        for (var shape : player.level().getEntityCollisions(player, area)) result.addAll(shape.toAabbs());
        return result;
    }

    /** Continuous translation with one-way contact planes and bounded stepping. */
    public static Vec3 collide(Player player, Vec3 requested) {
        var body = body(player);
        if (body.compactTerrain) body = terrain(body);
        double up = EndGravity.inverted(player) ? -1 : 1;
        // Underwater contact should slide/turn, not perform a full walking step.
        double step = player.isSwimming() ? 0.0 : player.maxUpStep();
        // Contact projection can redirect movement along a tilted face, outside the original axis sweep.
        // Every leg is non-expansive; this radius includes the entire possible slide/step path.
        double pathBound = Math.abs(requested.x) + Math.abs(requested.y) + Math.abs(requested.z) + step * 2;
        var obstacles = colliders(player, body.bounds().inflate(pathBound + EPSILON));
        if (obstacles == null) return Vec3.ZERO;
        var entities = entityColliders(player, body.bounds().inflate(pathBound + EPSILON));
        var resolved = slide(terrain(body), requested, obstacles, body, entities);
        boolean grounded = player.onGround() || requested.y * up < 0 && requested.y != resolved.y;
        if (step > 0 && grounded && resolved.horizontalDistanceSqr() + EPSILON < requested.horizontalDistanceSqr()) {
            var raised = sweepStraight(terrain(body), new Vec3(0, up * step, 0), obstacles, body, entities);
            var across = slide(terrain(body).move(raised), new Vec3(requested.x, 0, requested.z), obstacles, body.move(raised), entities);
            var offset = raised.add(across);
            var lowered = sweepStraight(terrain(body).move(offset), new Vec3(0, requested.y - offset.y, 0), obstacles, body.move(offset), entities);
            var stepped = raised.add(across).add(lowered);
            if (stepped.horizontalDistanceSqr() > resolved.horizontalDistanceSqr() + EPSILON) resolved = stepped;
        }
        return resolved;
    }

    /** A step's vertical lift/settle must not manufacture lateral movement on an oblique contact. */
    private static Vec3 sweepStraight(Box body, Vec3 requested, List<AABB> obstacles, Box physical, List<AABB> entities) {
        double length = requested.length();
        if (length == 0) return requested;
        double fraction = 1;
        for (var obstacle : obstacles) fraction = Math.min(fraction, body.sweep(obstacle, requested).fraction);
        for (var obstacle : entities) fraction = Math.min(fraction, physical.sweep(obstacle, requested).fraction);
        return fraction == 1 ? requested : requested.scale(Math.max(0, fraction - EPSILON / length));
    }

    public static Vec3 slide(Box body, Vec3 requested, List<AABB> obstacles) {
        return slide(body, requested, obstacles, body, List.of());
    }
    private static Vec3 slide(Box body, Vec3 requested, List<AABB> obstacles, Box physical, List<AABB> entities) {
        Vec3 resolved = Vec3.ZERO;
        Vec3 remaining = requested;
        var contacts = new ArrayList<Vec3>();
        for (int iteration = 0; iteration < 12 && remaining.lengthSqr() > 1.0e-20; iteration++) {
            var current = body.move(resolved);
            double fraction = 1;
            Vec3 normal = Vec3.ZERO;
            for (var obstacle : obstacles) {
                var hit = current.sweep(obstacle, remaining);
                if (hit.fraction < fraction) { fraction = hit.fraction; normal = hit.normal; }
            }
            for (var obstacle : entities) {
                var hit = physical.move(resolved).sweep(obstacle, remaining);
                if (hit.fraction < fraction) { fraction = hit.fraction; normal = hit.normal; }
            }
            if (fraction == 1) return resolved.add(remaining);
            // Do not deliberately enter the collider. The old shrunken SAT radius left a tiny overlap
            // which rounded into an all-axis obstruction on the very next movement component.
            double travel = Math.max(0, fraction - EPSILON / remaining.length());
            resolved = resolved.add(remaining.scale(travel));
            Vec3 rest = remaining.scale(1 - travel);
            boolean duplicate = false;
            for (var contact : contacts) if (contact.dot(normal) > 1 - 1.0e-10) { duplicate = true; break; }
            if (!duplicate) contacts.add(normal);
            remaining = alongContacts(rest, contacts);
            if (travel == 0 && remaining.distanceToSqr(rest) < 1.0e-24) break;
        }
        return resolved;
    }

    /** Closest allowed displacement in the contact cone: planes resist only their inward side.
     * Face, edge and corner candidates cover the possible active constraints in three dimensions.
     * Releasing a contact never pulls the body back or adds speed, including with combined X/Z tilt.
     */
    private static Vec3 alongContacts(Vec3 requested, List<Vec3> contacts) {
        if (allowed(requested, contacts)) return requested;
        Vec3 best = Vec3.ZERO;
        double error = requested.lengthSqr();
        for (int i = 0; i < contacts.size(); i++) {
            var normal = contacts.get(i);
            var face = requested.subtract(normal.scale(requested.dot(normal)));
            double faceError = face.distanceToSqr(requested);
            if (faceError < error && allowed(face, contacts)) { best = face; error = faceError; }
            for (int j = 0; j < i; j++) {
                var edge = normal.cross(contacts.get(j));
                double lengthSquared = edge.lengthSqr();
                if (lengthSquared < 1.0e-14) continue;
                var candidate = edge.scale(requested.dot(edge) / lengthSquared);
                double edgeError = candidate.distanceToSqr(requested);
                if (edgeError < error && allowed(candidate, contacts)) { best = candidate; error = edgeError; }
            }
        }
        return best;
    }

    private static boolean allowed(Vec3 movement, List<Vec3> contacts) {
        for (var normal : contacts) if (movement.dot(normal) < -1.0e-12) return false;
        return true;
    }

    /** A foot-face probe, independent of gravity and the entity's grounded flag. Distance is in blocks. */
    public static Optional<Contact> feetContact(Player player, double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > 4) throw new IllegalArgumentException("Foot probe range must be 0..4");
        var body = body(player);
        double thickness = Math.min(0.025, body.half.y);
        var probe = new Box(body.center.add(body.orientation.transform(new Vec3(0, -body.half.y + thickness, 0))),
            new Vec3(body.half.x, thickness, body.half.z), body.orientation);
        return blockContact(player, probe, body.orientation.transform(new Vec3(0, -(distance + EPSILON * 4), 0)));
    }

    public static Optional<Contact> support(Player player, double distance) {
        return blockContact(player, terrain(body(player)), new Vec3(0, EndGravity.inverted(player) ? distance : -distance, 0));
    }

    /** The normal points out of the obstacle; touching is distinct from a nearby positive-distance hit. */
    public record Contact(BlockPos block, BlockState state, Vec3 normal, double distance) { }

    private static Optional<Contact> blockContact(Player player, Box probe, Vec3 delta) {
        AABB area = probe.bounds().expandTowards(delta).inflate(EPSILON * 4);
        if (!available(player, area.inflate(1))) return Optional.empty();
        Contact closest = null;
        double nearest = 1;
        var context = CollisionContext.of(player);
        // A neighbouring block may own a collision shape extending beyond its own cell (e.g. fences).
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(area.minX - 1, area.minY - 1, area.minZ - 1),
                BlockPos.containing(area.maxX + 1, area.maxY + 1, area.maxZ + 1))) {
            var state = player.level().getBlockState(pos);
            for (var obstacle : state.getCollisionShape(player.level(), pos, context).toAabbs()) {
                var hit = probe.sweep(obstacle.move(pos), delta);
                if (hit.fraction < nearest) {
                    nearest = hit.fraction;
                    closest = new Contact(pos.immutable(), state, hit.normal, delta.length() * nearest);
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    /** Certifies the entire angular path, not just endpoints. Subdivision stops conservatively on exhaustion. */
    public static double safeRotationFraction(Box start, Angles target, List<AABB> obstacles) {
        return safeRotationFraction(start, target, obstacles, false);
    }
    public static double safeTerrainRotationFraction(Box start, Angles target, List<AABB> obstacles) {
        if (start.compactTerrain) return 1;
        return safeRotationFraction(start, target, obstacles, true);
    }
    private static double safeRotationFraction(Box start, Angles target, List<AABB> obstacles, boolean terrain) {
        if (start.orientation.equals(target)) return 1;
        var shape = terrain ? terrain(start) : start;
        for (var obstacle : obstacles) if (shape.separation(obstacle) < -EPSILON) return 0;
        return rotationInterval(start, target, obstacles, 0, 1, 0, new int[]{8192}, terrain);
    }
    private static double rotationInterval(Box start, Angles target, List<AABB> obstacles,
            double from, double to, int depth, int[] budget, boolean terrain) {
        if (--budget[0] < 0) return from;
        double middle = (from + to) / 2;
        var sample = new Box(start.center, start.half, start.orientation.towards(target, middle), start.compactTerrain);
        if (terrain) sample = terrain(sample);
        // Enclosing support and the inset's excess projection both vary with orientation.
        // Four circumradii bound their combined rate, including centreline protection.
        double radius = terrain && start.compactTerrain ? Math.max(0, start.half.y - start.half.x)
            : (terrain ? 4 : 1) * start.half.length();
        double travelBound = radius * start.orientation.radiansTo(target) * (to - from) / 2;
        boolean clear = true;
        for (var obstacle : obstacles) if (sample.separation(obstacle) < travelBound - EPSILON) { clear = false; break; }
        if (clear) return to;
        if (depth == 24) return from;
        double first = rotationInterval(start, target, obstacles, from, middle, depth + 1, budget, terrain);
        if (first < middle) return first;
        return rotationInterval(start, target, obstacles, middle, to, depth + 1, budget, terrain);
    }

    /** Exact convex box geometry shared by body, terrain and camera clearance. */
    public static final class Box {
        public final Vec3 center;
        public final Vec3 half;
        public final Angles orientation;
        public final boolean compactTerrain;
        private final Vec3[] axes;
        public Box(Vec3 center, Vec3 half, Angles orientation) {
            this(center, half, orientation, false);
        }
        public Box(Vec3 center, Vec3 half, Angles orientation, boolean compactTerrain) {
            this.center = center;
            this.half = half;
            this.orientation = orientation;
            this.compactTerrain = compactTerrain;
            axes = new Vec3[]{orientation.transform(WORLD_AXES[0]), orientation.transform(WORLD_AXES[1]), orientation.transform(WORLD_AXES[2])};
        }
        public Box move(Vec3 delta) { return new Box(center.add(delta), half, orientation, compactTerrain); }
        public AABB bounds() {

            double x = radius(WORLD_AXES[0]), y = radius(WORLD_AXES[1]), z = radius(WORLD_AXES[2]);
            return new AABB(center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
        }
        private double radius(Vec3 axis) {
            return half.x * Math.abs(axes[0].dot(axis)) + half.y * Math.abs(axes[1].dot(axis)) + half.z * Math.abs(axes[2].dot(axis));
        }
        private List<Vec3> separatingAxes() {
            var result = new ArrayList<Vec3>(15);
            result.addAll(List.of(WORLD_AXES));
            result.addAll(List.of(axes));
            for (var local : axes) for (var world : WORLD_AXES) {
                var cross = local.cross(world);
                if (cross.lengthSqr() > 1.0e-14) result.add(cross.normalize());
            }
            return result;
        }
        private static double radius(AABB box, Vec3 axis) {
            return (box.getXsize() * Math.abs(axis.x) + box.getYsize() * Math.abs(axis.y) + box.getZsize() * Math.abs(axis.z)) / 2;
        }
        /** Positive means separated, negative means overlap. A conservative lower bound on clearance. */
        public double separation(AABB other) {

            Vec3 delta = center.subtract(other.getCenter());
            double result = -Double.MAX_VALUE;
            for (var axis : separatingAxes()) result = Math.max(result, Math.abs(delta.dot(axis)) - radius(axis) - radius(other, axis));
            return result;
        }
        public Sweep sweep(AABB other, Vec3 movement) {

            Vec3 offset = center.subtract(other.getCenter());
            double enter = 0, exit = 1;
            Vec3 normal = Vec3.ZERO;
            var separatingAxes = separatingAxes();
            double separation = -Double.MAX_VALUE;
            Vec3 outward = Vec3.ZERO;
            boolean releasingContact = false;
            for (var axis : separatingAxes) {
                double distance = offset.dot(axis);
                double gap = Math.abs(distance) - radius(axis) - radius(other, axis);
                var axisOutward = axis.scale(distance < 0 ? -1 : 1);
                if (gap >= -CONTACT_TOLERANCE && movement.dot(axisOutward) >= -1.0e-12) releasingContact = true;
                if (gap > separation) {
                    separation = gap;
                    outward = axisOutward;
                }
            }
            if (separation <= EPSILON && separation >= -CONTACT_TOLERANCE) {
                // Treat roundoff at a resting contact as a one-way plane. Tangent and outward
                // movement are allowed, but inward movement still reports a blocking normal.
                return releasingContact ? Sweep.CLEAR : new Sweep(0, outward);
            }
            if (separation < -CONTACT_TOLERANCE) {
                // Existing penetration (e.g. a block placed into the player) may be escaped only
                // along the nearest exit; do not reinterpret it as permission to move deeper.
                return movement.dot(outward) > 1.0e-12 ? Sweep.CLEAR : new Sweep(0, outward);
            }
            for (var axis : separatingAxes) {
                double distance = offset.dot(axis), velocity = movement.dot(axis);
                double radius = radius(axis) + radius(other, axis);
                if (Math.abs(velocity) < 1.0e-12) {
                    if (Math.abs(distance) >= radius) return Sweep.CLEAR;
                    continue;
                }
                double a = (-radius - distance) / velocity, b = (radius - distance) / velocity;
                double near = Math.min(a, b), far = Math.max(a, b);
                if (near > enter) { enter = near; normal = axis.scale(-Math.signum(velocity)); }
                exit = Math.min(exit, far);
                if (enter >= exit) return Sweep.CLEAR;
            }
            return new Sweep(Math.clamp(enter, 0, 1), normal);
        }
        public Optional<Vec3> clip(Vec3 from, Vec3 to, double margin) {

            Vec3 a = from.subtract(center), b = to.subtract(center);
            Vec3 localFrom = new Vec3(a.dot(axes[0]), a.dot(axes[1]), a.dot(axes[2]));
            Vec3 localTo = new Vec3(b.dot(axes[0]), b.dot(axes[1]), b.dot(axes[2]));
            var local = new AABB(-half.x - margin, -half.y - margin, -half.z - margin, half.x + margin, half.y + margin, half.z + margin);
            if (local.contains(localFrom)) return Optional.of(from);
            return local.clip(localFrom, localTo).map(point -> center.add(orientation.transform(point)));
        }
    }
    public record Sweep(double fraction, Vec3 normal) {
        private static final Sweep CLEAR = new Sweep(1, Vec3.ZERO);
    }
}
