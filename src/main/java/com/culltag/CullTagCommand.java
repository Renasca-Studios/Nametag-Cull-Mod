package com.culltag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;
import java.util.Locale;

public final class CullTagCommand {

    private CullTagCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("culltag")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("enable")
                    .executes(CullTagCommand::enable))
                .then(Commands.literal("disable")
                    .executes(CullTagCommand::disable))
                .then(Commands.literal("reload")
                    .executes(CullTagCommand::reload))
                .then(Commands.literal("stats")
                    .executes(CullTagCommand::stats))
        );
    }

    private static int enable(CommandContext<CommandSourceStack> ctx) {
        CullTagConfig.enabled = true;
        CullTagConfig.save(CullTagMod.LOGGER);
        ctx.getSource().sendSuccess(CullTagText::enabled, true);
        return 1;
    }

    private static int disable(CommandContext<CommandSourceStack> ctx) {
        CullTagConfig.enabled = false;
        CullTagConfig.save(CullTagMod.LOGGER);
        List<ServerPlayer> players = ctx.getSource().getServer().getPlayerList().getPlayers();
        int restored = NametagManager.restoreAll(players);
        ctx.getSource().sendSuccess(() -> CullTagText.disabled(restored), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        boolean wasEnabled = CullTagConfig.enabled;
        CullTagConfig.reload(CullTagMod.LOGGER);
        if (wasEnabled && !CullTagConfig.enabled) {
            NametagManager.restoreAll(ctx.getSource().getServer().getPlayerList().getPlayers());
        }
        ctx.getSource().sendSuccess(() -> CullTagText.reloaded(CullTagConfig.summary()), true);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        LineOfSightEngine.PerfStats s = LineOfSightEngine.getStats();
        List<ServerPlayer> players = ctx.getSource().getServer().getPlayerList().getPlayers();
        Component message = CullTagText.stats(
                s.totalSweeps(),
                s.totalRays(),
                millis(s.lastSweepMs()),
                millis(s.avgSweepMs()),
                NametagManager.countHidden(players));
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    /** Locale.ROOT so the decimal separator does not follow the server's locale; the number
     *  is a positional argument that a translation slots in unchanged. */
    private static String millis(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
