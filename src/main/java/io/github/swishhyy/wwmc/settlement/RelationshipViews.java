package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.menu.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

/** Dedicated flag controls for player access, reciprocal alliances, invitations and the town's name. */
public final class RelationshipViews {
    public static final int OPEN=80,ROW_ACTION=81,BACK=82,RENAME=83,RECOVER=84;
    private RelationshipViews() {}
    private static ServerLevel level(ServerPlayer player) { return (ServerLevel)player.level(); }
    private static PanelView.Row row(ItemLike icon,String title,String detail,String key,int value) {
        return new PanelView.Row(new ItemStack(icon),Component.literal(title),Component.literal(detail),0,-1,value,key);
    }
    private static PanelView.Row action(ItemLike icon,String title,String detail,String key,boolean enabled) {
        return row(icon,title,detail,"act:"+key,enabled ? 0 : 1);
    }
    public static String playerName(ServerLevel level,Settlement town,UUID id) {
        var online=level.getServer().getPlayerList().getPlayer(id);
        return online!=null ? online.getName().getString() : town.campaign.playerNames.getOrDefault(id,id.toString().substring(0,8))+" (offline)";
    }
    public static boolean valid(ServerPlayer player,BlockPos flag) {
        if(!player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(flag))>64 || !level(player).hasChunkAt(flag)
                || !level(player).getBlockState(flag).is(WWMC.BANNER.get())) return false;
        Settlement town=SettlementData.get(level(player)).at(flag);
        return TownAccess.builds(town,player.getUUID()) || TownAccess.invited(town,player.getUUID());
    }
    public static void open(ServerPlayer player,Settlement town) { open(player,town,town.center); }
    public static void open(ServerPlayer player,Settlement town,BlockPos flag) {
        if(!valid(player,flag) || SettlementData.get(level(player)).at(flag)!=town) return;
        PanelView view=build(level(player),town,player);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,PanelMenu.Kind.RELATIONSHIPS,flag,player,view),view.title()),
                buf -> PanelMenu.write(buf,PanelMenu.Kind.RELATIONSHIPS,flag,view));
    }
    public static PanelView view(ServerPlayer player,BlockPos flag) {
        return valid(player,flag) ? build(level(player),SettlementData.get(level(player)).at(flag),player) : null;
    }
    public static PanelView build(ServerLevel level,Settlement town,ServerPlayer viewer) {
        boolean owner=TownAccess.owner(town,viewer.getUUID());
        List<PanelView.Row> players=new ArrayList<>(),towns=new ArrayList<>(),invitations=new ArrayList<>(),settings=new ArrayList<>();
        players.add(row(Items.GOLDEN_HELMET,"Owner: "+playerName(level,town,town.owner),"Only the owner sets permissions, approves alliances and renames this town","",-1));
        players.add(row(Items.IRON_DOOR,"Default access: Denied","Uninvited players cannot build, open storage, use blocks or interact with entities inside this claim","",-1));
        Set<UUID> roster=new LinkedHashSet<>(town.campaign.members.keySet()); roster.addAll(town.campaign.invitations.keySet());
        for(ServerPlayer player:level.getServer().getPlayerList().getPlayers()) if(!player.getUUID().equals(town.owner)) roster.add(player.getUUID());
        for(UUID id:roster.stream().sorted(Comparator.comparing(id -> playerName(level,town,id))).limit(200).toList()) {
            String accepted=town.campaign.members.get(id),pending=town.campaign.invitations.get(id);
            String role=accepted==null ? pending : accepted;
            int permission=role==null ? 0 : role.equals("builder") ? 1 : 2;
            String detail=accepted!=null ? role.equals("builder") ? "Builder: build and interact; no town management" : "Steward: build, interact and manage workers, supplies and squads"
                    : pending!=null ? "Invited as "+pending+"; access stays denied until they accept" : "Denied; choose Builder or Steward to invite";
            players.add(row(Items.PLAYER_HEAD,playerName(level,town,id),detail,owner ? "permission:"+id : "",permission));
        }
        for(Settlement other:SettlementData.get(level).settlements) if(other!=town) {
            if(other.trading.npc) {
                int goodwill=other.trading.relations.getOrDefault(town.owner,0);
                towns.add(row(Items.COMPASS,other.name,(goodwill<0 ? "Hostile" : goodwill>0 ? "Friendly" : "Neutral")+" NPC town at "+other.center.toShortString()+"; deliveries build goodwill","",-1));
                continue;
            }
            boolean shared=town.owner.equals(other.owner),allied=TownAccess.allied(town,other),incoming=town.campaign.allianceOffers.contains(other.id),outgoing=other.campaign.allianceOffers.contains(town.id);
            String relation=shared ? "Your town" : allied ? "Allied" : incoming ? "Alliance offered: accept" : outgoing ? "Alliance proposed: awaiting their owner" : "Neutral: propose an alliance";
            towns.add(action(Items.BANNER.blue(),other.name,relation+"; flag at "+other.center.toShortString()+". Alliances do not grant claim access.",
                    "ally:"+other.id,owner && !allied && !outgoing));
            if(!shared && (allied || incoming || outgoing)) towns.add(action(Items.BANNER.red(),(allied ? "End alliance: " : "Decline / cancel offer: ")+other.name,
                    "Stops extra allied routes safely; existing carriers retain their real cargo","unally:"+other.id,owner));
        }
        for(Settlement offered:SettlementData.get(level).settlements) if(TownAccess.invited(offered,viewer.getUUID())) {
            invitations.add(action(Items.PAPER,"Accept: "+offered.name,"Join as "+offered.campaign.invitations.get(viewer.getUUID())+"; flag at "+offered.center.toShortString(),"accept:"+offered.id,true));
            invitations.add(action(Items.DYE.red(),"Decline: "+offered.name,"Remove this invitation without joining","decline:"+offered.id,true));
        }
        if(invitations.isEmpty()) invitations.add(row(Items.PAPER,"No invitations waiting","Owners invite friends on the Players tab. Friends accept here at the flag.","",-1));
        settings.add(row(WWMC.BANNER_ITEM.get(),"Town name",town.name,"town:name",-1));
        settings.add(row(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(TownBorders.color(town).getName()+"_dye")),"Town color: "+TownBorders.title(town),"The town flag and four corner banners share this color",owner ? "color:town" : "",TownBorders.color(town).getId()));
        settings.add(row(Items.MAP,"Flag location",town.center.toShortString()+"; restore a missing flag here without creating a new town","",-1));
        settings.add(row(Items.COMPASS,"Claim borders","X "+(town.center.getX()-town.radius)+" to "+(town.center.getX()+town.radius)+", Z "+(town.center.getZ()-town.radius)+" to "+(town.center.getZ()+town.radius)+"; all heights","",-1));
        settings.add(row(Items.PAPER,"Entry notice","You are now entering "+town.name,"",-1));
        settings.add(row(Items.IRON_DOOR,"Player permissions","Builder can build and interact. Steward also manages workers, supplies and squads. Revoke removes invitations and access.","",-1));
        boolean flagLoaded=level.hasChunkAt(town.center),intact=flagLoaded && level.getBlockState(town.center).is(WWMC.BANNER.get());
        return new PanelView(Component.literal(town.name+" · Relationships"),Component.literal(owner ? "Set access, approve alliances and name your town" : "Your permissions and invitations; the owner controls relationships"),
                List.of(new PanelView.Tab("Players",players),new PanelView.Tab("Settlements",towns),new PanelView.Tab("Invitations",invitations),new PanelView.Tab("Town",settings)),
                List.of(new PanelView.Action(BACK,"Town overview",TownAccess.manages(town,viewer.getUUID()) && intact && viewer.distanceToSqr(Vec3.atCenterOf(town.center))<=64),
                        new PanelView.Action(MultiplayerViews.OPEN,"Neighbours",MultiplayerViews.valid(viewer,town.center),"Nearby towns, settlement trade, supply contracts and quiet news"),
                        new PanelView.Action(RENAME,"Save name",owner,"Only the owner may rename the town"),
                        new PanelView.Action(RECOVER,intact ? "Flag intact" : "Restore flag",TownAccess.builds(town,viewer.getUUID()) && flagLoaded && !intact,"Consumes one Settlement Banner from your inventory; restores the saved location without replacing other blocks")));
    }
    public static String rename(ServerLevel level,Settlement town,UUID owner,String name) {
        if(!TownAccess.owner(town,owner)) return "Only the owner may rename this town.";
        String value=name.strip();
        if(value.isEmpty() || value.length()>48 || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c==0xA7)) return "Use a town name of 1 to 48 plain characters.";
        if(town.name.equals(value)) return "This is already the town's name.";
        town.name=value; CampaignService.record(level,town,"Town renamed to "+value+"."); return "Town name saved.";
    }
    public static void act(ServerPlayer player,BlockPos flag,int action,int value,String key) {
        if(!valid(player,flag)) return;
        ServerLevel level=level(player); Settlement town=SettlementData.get(level).at(flag); String message="";
        if(action==MultiplayerViews.OPEN) { MultiplayerViews.open(player,town); return; }
        if(action==BACK) { if(TownAccess.manages(town,player.getUUID()) && Panels.valid(player,town.center,true)) Panels.openTown(player,town); return; }
        if(action==RENAME) message=rename(level,town,player.getUUID(),key);
        else if(action==RECOVER) message=SettlementService.recoverBanner(level,player,town);
        else if(action==ROW_ACTION && key.length()<=256) {
            try {
                if(key.equals("color:town")) message=TownBorders.choose(level,town,player.getUUID(),value);
                else if(key.startsWith("permission:")) message=permission(level,town,player,UUID.fromString(key.substring(11)),value);
                else if(key.startsWith("act:")) {
                    String[] parts=key.substring(4).split(":",2); if(parts.length!=2) return;
                    Settlement other=SettlementData.get(level).byId(UUID.fromString(parts[1])); if(other==null) return;
                    switch(parts[0]) {
                        case "ally" -> { message=TownAccess.alliance(town,other,player.getUUID()); SettlementData.get(level).setDirty(); }
                        case "unally" -> {
                            if(!TownAccess.owner(town,player.getUUID())) return;
                            TownAccess.leaveAlliance(town,other); CampaignService.record(level,town,"Alliance or offer ended with "+other.name+"."); message="Alliance / offer ended.";
                        }
                        case "accept" -> {
                            if(!TownAccess.accept(other,player.getUUID())) return;
                            other.campaign.playerNames.put(player.getUUID(),player.getName().getString());
                            CampaignService.record(level,other,player.getName().getString()+" joined as "+other.campaign.members.get(player.getUUID())+"."); message="Joined "+other.name+".";
                        }
                        case "decline" -> { other.campaign.invitations.remove(player.getUUID()); SettlementData.get(level).setDirty(); message="Invitation declined."; }
                        default -> { return; }
                    }
                }
            } catch(IllegalArgumentException ignored) { return; }
        }
        if(!message.isEmpty()) SettlementService.tell(player,message);
    }
    private static String permission(ServerLevel level,Settlement town,ServerPlayer owner,UUID id,int value) {
        if(!TownAccess.owner(town,owner.getUUID()) || id.equals(town.owner) || value<0 || value>2) return "Only the owner may set another player's permissions.";
        var friend=level.getServer().getPlayerList().getPlayer(id);
        if(friend==null && !town.campaign.members.containsKey(id) && !town.campaign.invitations.containsKey(id)) return "Invite that player while they are online.";
        if(friend!=null) town.campaign.playerNames.put(id,friend.getName().getString());
        if(value==0) {
            town.campaign.members.remove(id); town.campaign.invitations.remove(id); town.campaign.playerNames.remove(id);
            if(friend!=null) friend.closeContainer();
            CampaignService.record(level,town,"Claim access revoked for "+(friend==null ? id.toString().substring(0,8) : friend.getName().getString())+"."); return "Access revoked immediately.";
        }
        String role=value==1 ? "builder" : "steward";
        if(town.campaign.members.containsKey(id)) {
            town.campaign.members.put(id,role); if(friend!=null) friend.closeContainer();
            CampaignService.record(level,town,"Permissions for "+playerName(level,town,id)+": "+role+"."); return "Permissions saved.";
        }
        String result=TownAccess.invite(town,owner.getUUID(),id,role);
        if(friend!=null && TownAccess.invited(town,id)) SettlementService.tell(friend,"Invited to "+town.name+" as "+role+". Right-click its flag at "+town.center.toShortString()+" and accept on Relationships -> Invitations.");
        SettlementData.get(level).setDirty(); return result;
    }
}
