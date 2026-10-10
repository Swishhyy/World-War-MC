package io.github.swishhyy.wwmc.settlement;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Command equivalents make the banner actions available without guessing a UUID or relying on chat prompts. */
public final class MultiplayerCommands {
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException { return c.getSource().getPlayerOrException(); }
    private static ServerLevel level(CommandContext<CommandSourceStack> c) { return c.getSource().getLevel(); }
    private static int say(CommandContext<CommandSourceStack> c,String text) { c.getSource().sendSuccess(() -> Component.literal(text),false); return 1; }
    private static boolean overworld(CommandSourceStack source) { return source.getEntity() instanceof ServerPlayer && source.getLevel().dimension().equals(Level.OVERWORLD); }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwmc")
            .then(Commands.literal("neighbours").requires(MultiplayerCommands::overworld).executes(MultiplayerCommands::open))
            .then(Commands.literal("multiplayer").requires(MultiplayerCommands::overworld).executes(MultiplayerCommands::open))
            .then(Commands.literal("payment").requires(MultiplayerCommands::overworld)
                .then(Commands.literal("collect").executes(c -> say(c,"Collected "+PlayerContracts.collect(level(c),player(c))+" emeralds. Remaining balance: "+MultiplayerData.get(level(c)).payments.getOrDefault(player(c).getUUID(),0L)+"."))))
            .then(Commands.literal("playercontract").requires(MultiplayerCommands::overworld)
                .then(Commands.literal("list").executes(c -> {
                    StringBuilder text=new StringBuilder("Player supply contracts:");
                    for(var order:MultiplayerData.get(level(c)).contracts) {
                        var issuer=SettlementData.get(level(c)).byId(order.issuer); if(issuer==null) continue;
                        text.append("\n").append(order.id).append(" · ").append(issuer.name).append(" · ").append(order.remaining()).append(" ").append(order.item)
                                .append(" · ").append(order.payment).append(" emeralds · ").append(order.supplier==null ? "available" : "accepted").append(" · ").append(issuer.center.toShortString());
                    }
                    return say(c,text.toString());
                }))
                .then(Commands.literal("post").then(Commands.argument("amount",IntegerArgumentType.integer(1,256))
                    .then(Commands.argument("emeralds",IntegerArgumentType.integer(1,64)).executes(MultiplayerCommands::post))))
                .then(Commands.literal("accept").then(Commands.argument("id",UuidArgument.uuid()).executes(c -> say(c,PlayerContracts.accept(level(c),MultiplayerViews.home(level(c),player(c)),player(c),UuidArgument.getUuid(c,"id"))))))
                .then(Commands.literal("deliver").then(Commands.argument("id",UuidArgument.uuid()).executes(c -> say(c,PlayerContracts.deliver(level(c),player(c),UuidArgument.getUuid(c,"id"))))))
                .then(Commands.literal("cancel").then(Commands.argument("id",UuidArgument.uuid()).executes(c -> say(c,PlayerContracts.cancel(level(c),player(c),UuidArgument.getUuid(c,"id"))))))
                .then(Commands.literal("release").then(Commands.argument("id",UuidArgument.uuid()).executes(c -> say(c,PlayerContracts.abandon(level(c),player(c),UuidArgument.getUuid(c,"id")))))))
            .then(Commands.literal("outpost").then(Commands.literal("battle").requires(MultiplayerCommands::overworld)
                .then(Commands.literal("challenge").then(Commands.argument("outpost",UuidArgument.uuid()).executes(c -> say(c,OutpostContests.challenge(level(c),MultiplayerViews.home(level(c),player(c)),player(c),UuidArgument.getUuid(c,"outpost"))))))
                .then(Commands.literal("accept").then(Commands.argument("id",UuidArgument.uuid()).executes(c -> say(c,OutpostContests.accept(level(c),player(c),UuidArgument.getUuid(c,"id"))))))
                .then(Commands.literal("decline").then(Commands.argument("id",UuidArgument.uuid()).executes(c -> say(c,OutpostContests.decline(level(c),player(c),UuidArgument.getUuid(c,"id"))))))
                .then(Commands.literal("list").executes(c -> {
                    StringBuilder text=new StringBuilder("Outpost battles:");
                    for(var battle:MultiplayerData.get(level(c)).contests) text.append("\n").append(battle.id).append(" · outpost ").append(battle.outpost).append(" · ")
                            .append(battle.accepted ? "accepted" : "awaiting consent").append(" · capture ").append(battle.progress/20).append("/60s");
                    return say(c,text.toString());
                })))));
    }
    private static int open(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var player=player(c); var town=SettlementData.get(level(c)).at(player.blockPosition());
        if(town==null || !MultiplayerViews.valid(player,town.center)) return say(c,"Open Neighbours at a player settlement banner, within eight blocks.");
        MultiplayerViews.open(player,town); return 1;
    }
    private static int post(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return say(c,PlayerContracts.post(level(c),SettlementData.get(level(c)).at(player(c).blockPosition()),player(c),player(c).getMainHandItem(),IntegerArgumentType.getInteger(c,"amount"),IntegerArgumentType.getInteger(c,"emeralds")));
    }
}
