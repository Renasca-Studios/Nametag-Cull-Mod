package com.culltag;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Sweeps the online players and pushes per-viewer nametag overrides when the line of sight to
 * something with a visible nametag changes.
 *
 * <p>The engine keeps no visibility state of its own. What a viewer's client has been told
 * lives in exactly one place, the hidden-entity set on that viewer's connection
 * ({@link NametagController}), and every decision is made by asking that set whether the
 * override is already there. 1.1.1 kept two parallel maps keyed by UUID alongside that set;
 * they were allowed to disagree, and each way they disagreed was a nametag stuck on or stuck
 * off. Deriving from one set also makes reconnects correct for free: a new connection has an
 * empty set, which is exactly the state a fresh client is in.
 *
 * <p>The shape of a sweep is one pass per viewer, and it ends in a
 * {@link #reconcile reconcile} step that is the whole defence against stuck overrides. Every
 * earlier version released overrides at the points where it stopped caring about a pair, and
 * every one of those releases that got missed shipped as a bug: out of range, in another
 * dimension, disconnected, respawned. Reconcile inverts that. Anything still marked hidden
 * that this sweep did not positively re-confirm gets revealed, so a reason to stop tracking
 * something does not have to be enumerated in advance to be handled.
 *
 * <p>Two things about the raycasting are deliberate and easy to undo by accident:
 * <ul>
 *   <li><b>The ray ends at the nametag, not at the eyes.</b> The tag floats above the head, so
 *       eye to eye answers the wrong question in both directions: a player crouched behind a
 *       one-block wall whose tag is plainly visible was hidden, and one whose eyes cleared a
 *       ledge but whose tag did not was shown. That makes the test asymmetric, which is why
 *       there are two rays per player pair rather than the one 1.1.1 used.</li>
 *   <li><b>There is no cache between sweeps.</b> Everything in range is recast every time. The
 *       1.1.0 "skip the ray if this pair was blocked and neither endpoint moved" rule was
 *       unsound, because a door opening or a block breaking restores sight without either
 *       endpoint moving, and pairs got stuck as blocked forever.</li>
 * </ul>
 */
public final class LineOfSightEngine {

    private static int tickAccum = 0;

    // Perf counters ───────────────────────────────────────────────────────────
    private static long totalSweeps = 0;
    private static long totalRays   = 0;
    private static double lastSweepMs = 0;
    private static final double[] sweepRing = new double[100];
    private static int sweepRingIdx = 0;

    public record PerfStats(long totalSweeps, long totalRays, double lastSweepMs, double avgSweepMs) {}

    public static PerfStats getStats() {
        double sum = 0;
        int count = 0;
        for (double v : sweepRing) { if (v > 0) { sum += v; count++; } }
        return new PerfStats(totalSweeps, totalRays, lastSweepMs, count == 0 ? 0 : sum / count);
    }

    private LineOfSightEngine() {}

    public static void tick(List<ServerPlayer> allPlayers) {
        tickAccum++;
        if (tickAccum % CullTagConfig.checkIntervalTicks != 0) return;
        if (!CullTagConfig.enabled) return;

        long startNs = System.nanoTime();
        int rays = 0;

        // Spectators render no nametag and are shown none, so they are neither viewer nor
        // target. Filtering once here keeps them out of the quadratic loop entirely.
        List<ServerPlayer> players = new ArrayList<>(allPlayers.size());
        for (ServerPlayer p : allPlayers) {
            if (!p.isSpectator()) players.add(p);
        }

        int n = players.size();
        double reach = CullTagConfig.maxDistance;
        double reachSq = reach * reach;

        // Both anchors for every player, computed once instead of once per pair. The inner
        // loop used to call getEyePosition() on the same player n/2 times per sweep.
        Vec3[] eyes = new Vec3[n];
        Vec3[] tags = new Vec3[n];
        for (int i = 0; i < n; i++) {
            eyes[i] = players.get(i).getEyePosition();
            tags[i] = nameTagAnchor(players.get(i));
        }

        Set<Integer> confirmed = new HashSet<>();

        for (int viewerIdx = 0; viewerIdx < n; viewerIdx++) {
            ServerPlayer viewer = players.get(viewerIdx);
            ServerLevel level = viewer.level();
            Set<Integer> hidden = NametagManager.controller(viewer).culltag_getHiddenEntityIds();
            confirmed.clear();

            for (int targetIdx = 0; targetIdx < n; targetIdx++) {
                if (targetIdx == viewerIdx) continue;
                ServerPlayer target = players.get(targetIdx);

                // Out of range, in another dimension, or not something this viewer can see at
                // all. No decision is made, and reconcile below takes the override off.
                if (target.level() != level) continue;
                if (viewer.distanceToSqr(target) > reachSq) continue;
                if (target.isInvisibleTo(viewer)) continue;

                rays++;
                if (!SightTest.clear(level, eyes[viewerIdx], tags[targetIdx])) {
                    hide(viewer, target, hidden, confirmed);
                }
            }

            if (CullTagConfig.cullEntityNametags) {
                rays += sweepEntities(viewer, eyes[viewerIdx], level, reach, hidden, confirmed);
            }

            reconcile(viewer, level, hidden, confirmed);
        }

        double ms = (System.nanoTime() - startNs) / 1_000_000.0;
        lastSweepMs = ms;
        totalSweeps++;
        totalRays += rays;
        sweepRing[sweepRingIdx] = ms;
        sweepRingIdx = (sweepRingIdx + 1) % sweepRing.length;
    }

    /**
     * Culls the nametags of named mobs and armour stands for one viewer. Vanilla draws those
     * through walls exactly as it draws a player's, and a named mob visible through terrain
     * gives away a base just as well as a player does.
     *
     * <p>Invisible entities are skipped, which is deliberate rather than incidental: a named
     * invisible armour stand is almost always a hologram the server wants everybody to read,
     * and skipping them also keeps the cost proportional to the number of real named mobs
     * rather than to the number of decorations.
     */
    private static int sweepEntities(ServerPlayer viewer, Vec3 eye, ServerLevel level,
                                     double reach, Set<Integer> hidden, Set<Integer> confirmed) {
        AABB box = viewer.getBoundingBox().inflate(reach);
        List<Entity> candidates = level.getEntities((Entity) null, box,
                e -> !(e instanceof Player)
                        && e.shouldShowName()
                        && !e.isInvisibleTo(viewer)
                        && e.distanceToSqr(viewer) <= reach * reach);

        for (Entity target : candidates) {
            if (!SightTest.clear(level, eye, nameTagAnchor(target))) {
                hide(viewer, target, hidden, confirmed);
            }
        }
        return candidates.size();
    }

    /**
     * Reveals everything this viewer is still hiding that the sweep did not re-confirm.
     *
     * <p>This is what makes "stop tracking it" and "put it back" the same action. A target
     * that walked out of range, changed dimension, turned invisible, died, despawned or
     * disconnected all arrive here identically, and none of them needs its own release call at
     * the point it happened. An entity that no longer resolves is dropped without a packet,
     * because the client has already removed it.
     */
    private static void reconcile(ServerPlayer viewer, ServerLevel level,
                                  Set<Integer> hidden, Set<Integer> confirmed) {
        if (hidden.isEmpty()) return;
        for (Iterator<Integer> it = hidden.iterator(); it.hasNext(); ) {
            Integer id = it.next();
            if (confirmed.contains(id)) continue;
            it.remove();
            Entity target = level.getEntity(id);
            if (target != null) NametagManager.reveal(viewer, target);
        }
    }

    private static void hide(ServerPlayer viewer, Entity target,
                             Set<Integer> hidden, Set<Integer> confirmed) {
        Integer id = target.getId();
        confirmed.add(id);
        if (hidden.add(id)) NametagManager.hide(viewer, target);
    }

    /**
     * Drops every override online viewers hold against a player who is leaving. Reconcile
     * would catch this on the next sweep anyway; doing it now closes the window in which the
     * mixin is still rewriting packets for an ID the server has moved on from.
     */
    public static void forgetPlayer(List<ServerPlayer> viewers, ServerPlayer gone) {
        forgetEntity(viewers, gone.getId());
    }

    /**
     * Drops every override recorded against an entity ID that no longer exists. A respawning
     * player keeps their connection but becomes a brand new entity with a new ID.
     */
    public static void forgetEntity(List<ServerPlayer> viewers, int staleEntityId) {
        Integer id = staleEntityId;
        for (ServerPlayer viewer : viewers) {
            NametagManager.controller(viewer).culltag_getHiddenEntityIds().remove(id);
        }
    }

    /**
     * Where the nametag actually floats. Read from the entity's own {@code NAME_TAG}
     * attachment, which is what the client renders against, with the vanilla default as a
     * fallback for anything that does not declare one.
     */
    private static Vec3 nameTagAnchor(Entity entity) {
        Vec3 offset = entity.getAttachments().getNullable(
                EntityAttachment.NAME_TAG, 0, entity.getYRot());
        return offset != null
                ? entity.position().add(offset)
                : entity.position().add(0.0, entity.getBbHeight() + 0.5, 0.0);
    }
}
