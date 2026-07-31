package com.culltag;

import net.minecraft.network.chat.Component;

/**
 * Every string CullTag shows a player. Nothing outside this class builds player text.
 *
 * <p>All of it is {@link Component#translatableWithFallback}, never
 * {@link Component#literal} for prose and never {@code translatable} alone. CullTag's whole
 * premise is that players connect with unmodified clients, so a bare key would render to
 * them as the literal text {@code culltag.command.enable}. Sending key and English together
 * means a vanilla client draws the English and a client carrying a CullTag language file
 * draws the translation, with no branching on either side.
 *
 * <p>Every fallback here has to stay byte-identical to its entry in
 * {@code assets/culltag/lang/en_us.json}; {@code scripts/audit.py} fails the build if the two
 * drift, because otherwise the mod says one thing to a vanilla client and another to a
 * translated one.
 */
public final class CullTagText {

    /** A proper noun, so it stays literal rather than becoming a translatable key. */
    private static final Component BRAND = Component.literal("CullTag");

    private CullTagText() {}

    /** The brand prefix and the message body are two pieces of text plus a piece of layout,
     *  so the join is a key of its own rather than a hardcoded separator. */
    private static Component line(Component body) {
        return Component.translatableWithFallback("culltag.chat.line", "%1$s | %2$s", BRAND, body);
    }

    public static Component enabled() {
        return line(Component.translatableWithFallback(
                "culltag.command.enable",
                "Nametag culling enabled."));
    }

    public static Component disabled(int losRestored, int crouchRestored) {
        return line(Component.translatableWithFallback(
                "culltag.command.disable",
                "Nametag culling disabled. Line-of-sight overrides restored: %1$s. Crouch overrides restored: %2$s.",
                losRestored, crouchRestored));
    }

    /** {@code settingsSummary} is operator diagnostics whose labels are the config file's own
     *  key names, so it is passed through as one opaque argument rather than being broken
     *  into translatable pieces. */
    public static Component reloaded(String settingsSummary) {
        return line(Component.translatableWithFallback(
                "culltag.command.reload",
                "Config reloaded: %1$s",
                settingsSummary));
    }

    public static Component stats(long sweeps, long raycasts, String lastSweepMs, String avgSweepMs,
                                  int losHidden, int crouchHidden) {
        return line(Component.translatableWithFallback(
                "culltag.command.stats",
                "Sweeps: %1$s, raycasts: %2$s, last sweep: %3$s ms, average sweep: %4$s ms, hidden by line of sight: %5$s, hidden by crouch: %6$s",
                sweeps, raycasts, lastSweepMs, avgSweepMs, losHidden, crouchHidden));
    }
}
