package com.culltag;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The occlusion test: is the straight line from {@code from} to {@code to} interrupted by
 * something a player cannot see through.
 *
 * <p>This is not {@code Level.clip}, and the difference is the point. {@code clip} with
 * {@code ClipContext.Block.COLLIDER} asks "would I walk into this", which is the wrong
 * question: glass, panes and iron bars all have collision and all of them are see-through, so
 * a player standing behind a glass wall in full view used to lose their nametag. That is the
 * exact opposite of what the mod is for.
 *
 * <p>Vanilla has no single "can you see through this" predicate that survives contact with
 * reality. {@code canOcclude} is false for closed wooden doors, and light opacity is zero for
 * them too, so both would let a nametag through a shut door. Rather than guess, the rule is a
 * datapack block tag: a block occludes if it has a collision shape and is not in
 * {@code #culltag:transparent}. Servers that disagree can change the tag without waiting for
 * a release, and a tag that fails to load resolves to empty, which degrades to the old
 * collision-only behaviour rather than to letting everything through.
 */
public final class SightTest {

    public static final TagKey<Block> TRANSPARENT = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("culltag", "transparent"));

    private SightTest() {}

    /** How many blocks the tag currently resolves to. Zero means the tag did not load, which
     *  is otherwise completely silent: nothing throws, and every pane of glass quietly goes
     *  back to hiding nametags. Logged at startup and after every datapack reload so the
     *  failure is visible without having to go and stand behind a window. */
    public static int transparentBlockCount() {
        int count = 0;
        for (Holder<Block> ignored : BuiltInRegistries.BLOCK.getTagOrEmpty(TRANSPARENT)) {
            count++;
        }
        return count;
    }

    /** True when nothing opaque stands between the two points. */
    public static boolean clear(Level level, Vec3 from, Vec3 to) {
        Boolean blocked = BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            BlockState state = lvl.getBlockState(pos);
            if (state.isAir()) return null;
            if (state.is(TRANSPARENT, s -> true)) return null;

            VoxelShape shape = state.getCollisionShape(lvl, pos);
            if (shape.isEmpty()) return null;

            // A non-null return stops the traversal, so only report an actual intersection.
            return shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, lvl -> Boolean.FALSE);

        return blocked != Boolean.TRUE;
    }
}
