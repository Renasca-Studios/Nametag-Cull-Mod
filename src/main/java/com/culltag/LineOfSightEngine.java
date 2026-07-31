package com.culltag;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Sweeps every pair of online players and pushes per-viewer nametag overrides when the line
 * of sight between them changes.
 *
 * <p>The engine keeps no visibility state of its own. What a viewer's client has been told
 * lives in exactly one place, the hidden-entity set on that viewer's connection
 * ({@link NametagController}), and every decision is made by asking that set whether the
 * override is already there. 1.1.1 kept two parallel maps keyed by UUID alongside that set;
 * they were allowed to disagree, and each way they disagreed was a nametag stuck on or stuck
 * off. Deriving from one set also means a reconnecting client, which has a fresh connection
 * and therefore an empty set, is correctly treated as being in the vanilla state.
 *
 * <p>Optimisations:
 * <ul>
 *   <li>Symmetric LOS: one raycast per unordered pair, result applied both ways.</li>
 *   <li>No temporal cache. Every in-range pair is recast every sweep. The 1.1.0 "skip the
 *       raycast if this pair was blocked and neither endpoint moved" rule was unsound,
 *       because a door opening or a block breaking restores sight without either endpoint
 *       moving, and pairs got stuck as blocked forever. Sweeps measure well under a
 *       millisecond, so correctness wins.</li>
 * </ul>
 */
public final class LineOfSightEngine {

    private static int tickAccum = 0;

    // Perf counters ───────────────────────────────────────────────────────────
    private static long totalSweeps   = 0;
    private static long totalRaycasts = 0;
    private static double lastSweepMs = 0;
    private static final double[] sweepRing = new double[100];
    private static int sweepRingIdx = 0;

    public record PerfStats(long totalSweeps, long totalRaycasts, double lastSweepMs, double avgSweepMs) {}

    public static PerfStats getStats() {
        double sum = 0;
        int count = 0;
        for (double v : sweepRing) { if (v > 0) { sum += v; count++; } }
        return new PerfStats(totalSweeps, totalRaycasts, lastSweepMs, count == 0 ? 0 : sum / count);
    }

    private LineOfSightEngine() {}

    public static void tick(List<ServerPlayer> players) {
        tickAccum++;
        if (tickAccum % CullTagConfig.checkIntervalTicks != 0) return;
        if (!CullTagConfig.enabled) return;

        long startNs = System.nanoTime();
        int raycasts = 0;

        double maxDistSq = (double) CullTagConfig.maxDistance * CullTagConfig.maxDistance;
        boolean crouchHiding = CullTagConfig.crouchHidesNametag;

        // Process each unordered pair exactly once.
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer a = players.get(i);
            Vec3 eyeA = a.getEyePosition();

            for (int j = i + 1; j < players.size(); j++) {
                ServerPlayer b = players.get(j);

                // In another dimension, or too far apart for the nametag to matter: drop any
                // override we are holding rather than leaving it stuck on. 1.1.1 dropped the
                // cache row here and left the client force-sneaked at range forever, so a
                // player who walked past max_distance stayed crouched and nameless.
                if (a.level() != b.level() || a.distanceToSqr(b) > maxDistSq) {
                    release(a, b);
                    release(b, a);
                    continue;
                }

                // Crouch-hide uses a per-viewer team override rather than the sneak flag,
                // because vanilla still draws a sneaking player's nametag at close range with
                // clear sight, which is exactly where the feature has to work.
                boolean aCrouchHidden = crouchHiding && a.isCrouching();
                boolean bCrouchHidden = crouchHiding && b.isCrouching();
                CrouchHider.setHidden(a, b, bCrouchHidden);
                CrouchHider.setHidden(b, a, aCrouchHidden);

                // Both nametags are already team-hidden, so the ray cannot change anything
                // the players can see.
                if (aCrouchHidden && bCrouchHidden) continue;

                raycasts++;
                boolean visible = castRay(a, eyeA, b.getEyePosition());

                apply(a, b, visible);
                apply(b, a, visible);
            }
        }

        double ms = (System.nanoTime() - startNs) / 1_000_000.0;
        lastSweepMs = ms;
        totalSweeps++;
        totalRaycasts += raycasts;
        sweepRing[sweepRingIdx] = ms;
        sweepRingIdx = (sweepRingIdx + 1) % sweepRing.length;
    }

    /**
     * Drops every override online viewers are holding against a player who is leaving.
     *
     * <p>The sneak override needs no restore packet: the client is about to remove the entity
     * anyway. The crouch override does, because scoreboard team membership is keyed by name
     * and a client keeps its team roster across a player leaving, so without an explicit
     * removal that player would come back already hidden.
     */
    public static void forgetPlayer(List<ServerPlayer> viewers, ServerPlayer gone) {
        Integer goneId = gone.getId();
        for (ServerPlayer viewer : viewers) {
            if (viewer == gone) continue;
            NametagManager.controller(viewer).culltag_getHiddenEntityIds().remove(goneId);
            CrouchHider.setHidden(viewer, gone, false);
        }
    }

    /**
     * Drops every override recorded against an entity ID that no longer exists. A respawning
     * player keeps their connection but becomes a brand new entity with a new ID, so the old
     * ID would otherwise sit in every viewer's hidden set for the rest of the session.
     */
    public static void forgetEntity(List<ServerPlayer> viewers, int staleEntityId) {
        Integer id = staleEntityId;
        for (ServerPlayer viewer : viewers) {
            NametagManager.controller(viewer).culltag_getHiddenEntityIds().remove(id);
        }
    }

    /** Clears both overrides {@code viewer} holds against {@code target}. */
    private static void release(ServerPlayer viewer, ServerPlayer target) {
        apply(viewer, target, true);
        CrouchHider.setHidden(viewer, target, false);
    }

    /**
     * Brings the viewer's client into line with {@code visible}, sending a packet only when
     * that is a change. The hidden set is the record of what was sent, so adding to it and
     * sending cannot come apart.
     */
    private static void apply(ServerPlayer viewer, ServerPlayer target, boolean visible) {
        Set<Integer> hidden = NametagManager.controller(viewer).culltag_getHiddenEntityIds();
        Integer id = target.getId();
        if (visible) {
            if (hidden.remove(id)) NametagManager.reveal(viewer, target);
        } else {
            if (hidden.add(id)) NametagManager.hide(viewer, target);
        }
    }

    private static boolean castRay(ServerPlayer viewer, Vec3 from, Vec3 to) {
        BlockHitResult hit = viewer.level().clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                viewer));
        return hit.getType() == HitResult.Type.MISS;
    }
}
