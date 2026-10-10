package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Each expedition outpost battle is offered and accepted by its two main settlement owners. Ordinary claims stay closed. */
public final class OutpostContests {
    public static final int CAPTURE_TICKS=1200,ARENA=32;
    private OutpostContests() {}
    private static ServerPlayer online(ServerLevel level,UUID id) {
        for(ServerPlayer player:level.players()) if(player.getUUID().equals(id)) return player;
        return level.getServer().getPlayerList().getPlayer(id);
    }
    public static boolean resourceOutpost(ServerLevel level,Settlement outpost) {
        return outpost!=null && !outpost.trading.npc && outpost.campaign.parent!=null
                && ExpeditionData.get(level).sites.stream().anyMatch(s -> s.cleared && outpost.id.equals(s.claimed) && !s.kind.equals("raid"));
    }
    public static boolean member(Settlement town,Player player) {
        return town!=null && (town.owner.equals(player.getUUID()) || town.campaign.members.containsKey(player.getUUID()));
    }
    private static int team(Settlement defender,Settlement challenger,Player player) {
        boolean a=member(defender,player),b=member(challenger,player);
        return a==b ? 0 : a ? 1 : 2; // A member of both settlements is neutral in this battle.
    }
    private static boolean playing(ServerLevel level,Player player) {
        return player!=null && player.level()==level && player.isAlive() && !player.isSpectator() && !player.getAbilities().instabuild;
    }
    private static boolean seesFlag(ServerLevel level,Player player,Settlement outpost) {
        var hit=level.clip(new ClipContext(player.getEyePosition(),Vec3.atCenterOf(outpost.center),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(outpost.center);
    }
    public static String challenge(ServerLevel level,Settlement challenger,ServerPlayer player,UUID target) {
        var towns=SettlementData.get(level); Settlement outpost=towns.byId(target);
        Settlement defender=outpost==null || outpost.campaign.parent==null ? null : towns.byId(outpost.campaign.parent);
        if(challenger==null || challenger.trading.npc || challenger.campaign.parent!=null || !TownAccess.owner(challenger,player.getUUID())) return "Only a main settlement owner can propose an outpost battle.";
        if(!resourceOutpost(level,outpost) || defender==null || challenger.owner.equals(defender.owner) || TownAccess.allied(challenger,defender)) return "Choose a non-allied settlement's expedition resource outpost.";
        if(!challenger.campaign.projects.contains("frontier") || challenger.campaign.extraRoutes.size()>=4) return "Complete a Frontier Charter and leave room for an outpost supply route.";
        if(!playing(level,online(level,defender.owner)) || !playing(level,player)) return "Both owners must be online in the Overworld.";
        if(!level.getGameRules().get(GameRules.PVP)) return "This server has player combat disabled.";
        var data=MultiplayerData.get(level);
        if(data.contests.size()>=32 || data.contests.stream().anyMatch(c -> c.outpost.equals(target))) return "This outpost already has a battle offer, or the battle list is full.";
        var contest=new MultiplayerData.Contest(UUID.randomUUID(),target,defender.id,challenger.id,0,level.getGameTime()+12000,false,0);
        data.contests.add(contest); data.setDirty();
        CampaignService.record(level,defender,challenger.name+" offered a battle for "+outpost.name+". Accept or decline at the banner's Neighbours board; nothing changes without acceptance.");
        SettlementService.notify(online(level,defender.owner),challenger.name+" challenged your outpost. Review at a banner's Neighbours board.");
        WWMC.LOGGER.info("[WWMC] [outposts] {} offered battle {} for {}, defender {}",challenger.id,contest.id,target,defender.id);
        return "Challenge offered for ten minutes. The defending owner must accept before any outpost PvP or capture begins.";
    }
    public static String accept(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var contest=data.contest(id); var towns=SettlementData.get(level);
        Settlement defender=contest==null ? null : towns.byId(contest.defender),challenger=contest==null ? null : towns.byId(contest.challenger);
        if(contest==null || contest.accepted || !TownAccess.owner(defender,player.getUUID())) return "Only the defending settlement owner can accept this offer.";
        Settlement outpost=towns.byId(contest.outpost);
        if(level.getGameTime()>=contest.deadline || !validTeams(level,contest,outpost,defender,challenger)) return "The offer is no longer valid; both owners and the original outpost must be present.";
        contest.accepted=true; contest.starts=level.getGameTime()+1200; contest.deadline=contest.starts+6000; data.setDirty();
        CampaignService.record(level,defender,"Accepted a battle for "+outpost.name+". One minute to assemble; defend the flag for five minutes. Only players fight within 32 blocks of it.");
        CampaignService.record(level,challenger,"Battle accepted at "+outpost.center.toShortString()+". Assemble for one minute, then hold within six blocks of the flag for 60 seconds with no defender within 16 blocks.");
        WWMC.LOGGER.info("[WWMC] [outposts] Battle {} accepted, starts {}",id,contest.starts);
        return "Accepted. One minute to assemble, five-minute battle; 60 seconds of uncontested flag presence captures the existing outpost. Normal player death and drops apply.";
    }
    public static String decline(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var contest=data.contest(id); var towns=SettlementData.get(level);
        if(contest==null || contest.accepted || !TownAccess.owner(towns.byId(contest.defender),player.getUUID()) && !TownAccess.owner(towns.byId(contest.challenger),player.getUUID())) return "Only an unaccepted challenge can be declined or withdrawn by its owners.";
        data.contests.remove(contest); data.setDirty(); return "Outpost challenge closed.";
    }
    private static boolean validTeams(ServerLevel level,MultiplayerData.Contest contest,Settlement outpost,Settlement defender,Settlement challenger) {
        return resourceOutpost(level,outpost) && defender!=null && challenger!=null && outpost.owner.equals(defender.owner)
                && defender.id.equals(outpost.campaign.parent) && challenger.campaign.parent==null
                && !TownAccess.allied(defender,challenger) && challenger.campaign.projects.contains("frontier") && challenger.campaign.extraRoutes.size()<4
                && playing(level,online(level,defender.owner)) && playing(level,online(level,challenger.owner));
    }
    public static boolean allows(ServerLevel level,Player attacker,Player target) {
        if(!playing(level,attacker) || !playing(level,target) || !level.getGameRules().get(GameRules.PVP)) return false;
        var towns=SettlementData.get(level);
        for(var contest:MultiplayerData.get(level).contests) if(contest.accepted && level.getGameTime()>=contest.starts && level.getGameTime()<contest.deadline) {
            Settlement outpost=towns.byId(contest.outpost),a=towns.byId(contest.defender),b=towns.byId(contest.challenger);
            if(outpost!=null && attacker.distanceToSqr(Vec3.atCenterOf(outpost.center))<=ARENA*ARENA && target.distanceToSqr(Vec3.atCenterOf(outpost.center))<=ARENA*ARENA
                    && validTeams(level,contest,outpost,a,b) && team(a,b,attacker)!=0 && team(a,b,target)!=0 && team(a,b,attacker)!=team(a,b,target)) return true;
        }
        return false;
    }
    /** Transfer the existing settlement, its miners and real stores; no generated reward or duplicate outpost. */
    public static void capture(ServerLevel level,MultiplayerData.Contest contest,Settlement outpost,Settlement challenger) {
        var towns=SettlementData.get(level); Settlement defender=towns.byId(contest.defender);
        for(Settlement town:towns.settlements) {
            town.campaign.extraRoutes.remove(outpost.id); town.campaign.allies.remove(outpost.id); town.campaign.allianceOffers.remove(outpost.id);
            if(outpost.id.equals(town.trading.partner)) { town.trading.partner=null; town.trading.paused=true; }
        }
        outpost.owner=challenger.owner; outpost.campaign.parent=challenger.id;
        outpost.campaign.members.clear(); outpost.campaign.members.putAll(challenger.campaign.members);
        outpost.campaign.invitations.clear(); outpost.campaign.allies.clear(); outpost.campaign.allianceOffers.clear(); outpost.campaign.squads.clear();
        outpost.campaign.extraRoutes.clear(); outpost.campaign.extraRoutes.add(challenger.id); challenger.campaign.extraRoutes.add(outpost.id);
        outpost.trading.partner=null; outpost.trading.paused=false;
        // Ownership carries the new settlement's progression, rather than granting its players the defeated town's ages.
        TownProgress previous=outpost.progress;
        outpost.progress=new TownProgress(); outpost.progress.research.addAll(challenger.progress.research);
        outpost.progress.legacyGearTier=challenger.progress.legacyGearTier; outpost.progress.color=previous.color;
        outpost.progress.traps.addAll(previous.traps);
        towns.setDirty();
        CampaignService.record(level,challenger,"Captured "+outpost.name+". Its existing miners, warehouse and ore vein now supply this settlement.");
        if(defender!=null) CampaignService.record(level,defender,"Lost "+outpost.name+" after its agreed outpost battle.");
        CampaignService.record(level,outpost,"Captured by "+challenger.name+"; the supply route now leads to its main settlement.");
        WWMC.LOGGER.info("[WWMC] [outposts] Battle {} captured existing outpost {} for {}",contest.id,outpost.id,challenger.id);
    }
    public static void tick(ServerLevel level) {
        var data=MultiplayerData.get(level); var towns=SettlementData.get(level); long now=level.getGameTime();
        for(var contest:new ArrayList<>(data.contests)) {
            Settlement outpost=towns.byId(contest.outpost),defender=towns.byId(contest.defender),challenger=towns.byId(contest.challenger);
            if(!validTeams(level,contest,outpost,defender,challenger) || !level.getGameRules().get(GameRules.PVP) || now>=contest.deadline) {
                String reason=now>=contest.deadline ? contest.accepted ? "The defenders held the flag until time ran out." : "Offer expired." : "Battle cancelled: an owner went offline, permissions changed or the outpost was removed.";
                if(defender!=null) CampaignService.record(level,defender,"Outpost challenge ended. "+reason);
                if(challenger!=null) CampaignService.record(level,challenger,"Outpost challenge ended. "+reason);
                data.contests.remove(contest); data.setDirty(); WWMC.LOGGER.info("[WWMC] [outposts] Battle {} ended: {}",contest.id,reason); continue;
            }
            if(!contest.accepted || now<contest.starts || !level.hasChunkAt(outpost.center)) continue;
            boolean attacking=false,defending=false;
            for(ServerPlayer player:level.players()) if(playing(level,player)) {
                double distance=player.distanceToSqr(Vec3.atCenterOf(outpost.center));
                if(team(defender,challenger,player)==2 && distance<=6*6 && seesFlag(level,player,outpost)) attacking=true;
                if(team(defender,challenger,player)==1 && distance<=16*16) defending=true;
            }
            int before=contest.progress;
            if(attacking && !defending) contest.progress=Math.min(CAPTURE_TICKS,contest.progress+20);
            else if(!attacking) contest.progress=Math.max(0,contest.progress-20);
            if(before!=contest.progress) data.setDirty();
            if(contest.progress>=CAPTURE_TICKS) {
                capture(level,contest,outpost,challenger); data.contests.remove(contest); data.setDirty();
            }
        }
    }
}
