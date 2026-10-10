package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Bounded world opportunities and a persistent journal; it never simulates block access on background threads. */
public final class CampaignService {
    private int cursor;
    public static void record(ServerLevel level,Settlement town,String text) {
        journal(level,town,text);
        for(ServerPlayer player:level.players()) if(TownAccess.manages(town,player.getUUID())) SettlementService.notify(player,town.name+": "+text);
    }
    /** Routine citizen updates stay in the banner's Campaign / Journal tab, without chat or hotbar spam. */
    public static void journal(ServerLevel level,Settlement town,String text) {
        town.campaign.log(level.getGameTime(),text); SettlementData.get(level).setDirty();
    }
    public static Settlement local(ServerPlayer player) {
        ServerLevel level=(ServerLevel)player.level(); SettlementData data=SettlementData.get(level);
        Settlement local=data.at(player.blockPosition());
        if(TownAccess.manages(local,player.getUUID())) return local;
        return data.settlements.stream().filter(s -> TownAccess.manages(s,player.getUUID()))
                .min(Comparator.comparingDouble(s -> s.center.distSqr(player.blockPosition()))).orElse(null);
    }
    public static List<Settlement> manageable(ServerLevel level,UUID player) { return SettlementData.get(level).settlements.stream().filter(t -> TownAccess.manages(t,player)).toList(); }
    public static void chooseSpecialty(ServerLevel level,Settlement town,String specialty) {
        if(!Specialization.NAMES.contains(specialty)) return;
        town.campaign.specialty=specialty;
        if(Specialization.terrain(level,town,specialty)) town.campaign.projects.add("terrain_bonus"); else town.campaign.projects.remove("terrain_bonus");
        record(level,town,"Industry set to "+specialty+(specialty.equals("balanced") ? "; all jobs use normal production speed."
                : town.campaign.projects.contains("terrain_bonus") ? "; matching terrain gives 25% faster work." : "; matching jobs work 10% faster."));
    }
    public static String extraRoute(ServerLevel level,Settlement source,Settlement destination) {
        if(!source.campaign.projects.contains("depot")) return "Complete the Transport Depot project first.";
        if(!TownAccess.allied(source,destination)) return "Extra supply routes require a reciprocal alliance or shared owner.";
        if(TradeRoutes.checkpoint(source)==null || TradeRoutes.checkpoint(destination)==null) return "Both towns need a Trader Block.";
        if(source.center.distSqr(destination.center)>(double)Config.TRADE_DISTANCE.get()*Config.TRADE_DISTANCE.get()) return "Destination exceeds the trade distance limit.";
        if(source.campaign.extraRoutes.size()>=4 || destination.campaign.extraRoutes.size()>=4) return "Each checkpoint supports four extra supply routes.";
        if(TradeRoutes.agreed(source,destination)) return "Already connected.";
        source.campaign.extraRoutes.add(destination.id); destination.campaign.extraRoutes.add(source.id);
        record(level,source,"Added an allied supply route to "+destination.name+"."); record(level,destination,"Added an allied supply route to "+source.name+".");
        return "Supply route connected; the trader visits available destinations in turn.";
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%200!=0) return;
        var data=SettlementData.get(level);
        if(data.settlements.isEmpty()) return;
        // A single town per check keeps inventory snapshots and NPC events bounded as the world grows.
        Settlement town=data.settlements.get(Math.floorMod(cursor++,data.settlements.size()));
        if(!level.hasChunkAt(town.center) || !level.isPositionEntityTicking(town.center)) return;
        SupplyRequests.snapshotLoaded(level,town);
        data.setDirty();
        if(!town.trading.npc || town.trading.buildIndex>=0) return;
        NeighbourTrade.update(level,town);
        CampaignContracts.expire(level,town);
        if(town.campaign.nextEvent==0) { town.campaign.nextEvent=level.getGameTime()+6000; return; }
        if(level.getGameTime()<town.campaign.nextEvent) return;
        town.campaign.nextEvent=level.getGameTime()+12000+level.getRandom().nextInt(12000);
        SupplyContract offer=CampaignContracts.offer(level,town);
        for(Settlement playerTown:data.settlements) if(!playerTown.trading.npc && playerTown.center.distSqr(town.center)<4096.0*4096.0) {
            if(offer!=null) journal(level,playerTown,town.name+" requests "+offer.amount+" "+offer.item.replace("minecraft:","")+". Check Neighbours at the banner.");
        }
    }
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(event.getEntity() instanceof CitizenEntity citizen && citizen.level() instanceof ServerLevel level) {
            Settlement town=citizen.town(level); if(town==null) return;
            SquadService.remove(level,town,citizen.getUUID());
            record(level,town,citizen.getName().getString()+" died while "+citizen.activity().toLowerCase(Locale.ROOT)+".");
        }
    }
}
