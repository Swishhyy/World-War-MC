package io.github.swishhyy.wwmc.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import io.github.swishhyy.wwmc.settlement.CampaignViews;
import io.github.swishhyy.wwmc.settlement.RelationshipViews;
import io.github.swishhyy.wwmc.settlement.MultiplayerViews;

/** The town overview (opened at the banner) or a station's panel. */
public final class PanelMenu extends SettlementMenu {
    public enum Kind { TOWN, STATION, CAMPAIGN, ARMY, RELATIONSHIPS, MULTIPLAYER, PEOPLE, PRODUCTION, RESEARCH }
    public final Kind kind;
    public final BlockPos pos;
    public PanelMenu(int id,Kind kind,BlockPos pos,ServerPlayer viewer,PanelView view) {
        super(WwmcMenus.PANEL.get(),id,viewer,view); this.kind=kind; this.pos=pos.immutable();
    }
    public static PanelMenu read(int id,Inventory inventory,RegistryFriendlyByteBuf buf) {
        Kind kind=buf.readEnum(Kind.class);
        BlockPos pos=buf.readBlockPos();
        return new PanelMenu(id,kind,pos,null,PanelView.STREAM_CODEC.decode(buf));
    }
    public static void write(RegistryFriendlyByteBuf buf,Kind kind,BlockPos pos,PanelView view) {
        buf.writeEnum(kind); buf.writeBlockPos(pos); PanelView.STREAM_CODEC.encode(buf,view);
    }
    @Override protected PanelView build() {
        return switch(kind) { case TOWN -> Panels.town(viewer,pos); case STATION -> Panels.station(viewer,pos);
            case CAMPAIGN,ARMY -> CampaignViews.view(viewer,pos,kind==Kind.ARMY); case RELATIONSHIPS -> RelationshipViews.view(viewer,pos);
            case MULTIPLAYER -> MultiplayerViews.view(viewer,pos);
            case PEOPLE,PRODUCTION,RESEARCH -> TownViews.view(viewer,pos,kind); };
    }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return viewer==null || switch(kind) {
        case CAMPAIGN,ARMY -> CampaignViews.valid(viewer,pos,kind==Kind.ARMY);
        case RELATIONSHIPS -> RelationshipViews.valid(viewer,pos);
        case MULTIPLAYER -> MultiplayerViews.valid(viewer,pos);
        case PEOPLE,PRODUCTION,RESEARCH -> TownViews.valid(viewer,pos);
        default -> Panels.valid(viewer,pos,kind==Kind.TOWN);
    }; }
    @Override public void act(ServerPlayer player,int action,int index,int value,String key) {
        if(kind==Kind.TOWN) Panels.townAction(player,pos,action,value,key);
        else if(kind==Kind.STATION) Panels.stationAction(player,pos,action);
        else if(kind==Kind.RELATIONSHIPS) RelationshipViews.act(player,pos,action,value,key);
        else if(kind==Kind.MULTIPLAYER) MultiplayerViews.act(player,pos,action,value,key);
        else if(TownViews.focused(kind)) TownViews.act(player,pos,kind,action,value,key);
        else CampaignViews.act(player,pos,kind==Kind.ARMY,action,value,key);
        refresh();
    }
}
