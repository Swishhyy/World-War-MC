package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.PanelMenu;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import io.github.swishhyy.wwmc.settlement.JobBoard;
import io.github.swishhyy.wwmc.settlement.CampaignViews;
import io.github.swishhyy.wwmc.settlement.RelationshipViews;
import io.github.swishhyy.wwmc.settlement.MultiplayerViews;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The town overview and station panels: tabs of rows that refresh every second, and the owner's buttons, two to a row.
 * Rows of the Jobs tab carry buttons to lower or raise that job's priority.
 */
public final class PanelScreen extends AbstractContainerScreen<PanelMenu> {
    private static final int WIDTH=256,HEIGHT=214,ROW=22,LIST_TOP=48;
    private int tab,scroll;
    private PanelView shown;
    private String layout="";
    private EditBox townName;
    private String nameDraft;
    public PanelScreen(PanelMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,menu.kind!=PanelMenu.Kind.TOWN && menu.kind!=PanelMenu.Kind.STATION ? Math.min(360,Minecraft.getInstance().getWindow().getGuiScaledWidth()-8) : WIDTH,
                menu.kind!=PanelMenu.Kind.TOWN && menu.kind!=PanelMenu.Kind.STATION ? Math.min(270,Minecraft.getInstance().getWindow().getGuiScaledHeight()-8) : HEIGHT);
    }
    private PanelView view() { return menu.view(); }
    private void openHelp() {
        if(menu.kind==PanelMenu.Kind.STATION && minecraft.level!=null && minecraft.level.getBlockState(menu.pos).getBlock() instanceof io.github.swishhyy.wwmc.block.StationBlock station) { GuideScreen.openStation(this,station.role()); return; }
        GuideScreen.open(this,switch(menu.kind) { case MULTIPLAYER -> "multiplayer"; case RELATIONSHIPS -> "relationships"; case CAMPAIGN,ARMY -> "frontier"; default -> "start"; });
    }
    @Override protected void init() {
        if(townName!=null) nameDraft=townName.getValue();
        townName=null;
        super.init();
        PanelView view=view();
        Button help=Button.builder(Component.literal("?"),b -> openHelp()).bounds(leftPos+imageWidth-23,topPos+5,16,16).build();
        help.setTooltip(Tooltip.create(Component.literal("Open illustrated help for this screen"))); addRenderableWidget(help);
        List<PanelView.Tab> tabs=view.tabs();
        tab=Math.min(tab,Math.max(0,tabs.size()-1));
        int tabWidth=Math.min(80,(imageWidth-14)/Math.max(1,tabs.size()));
        for(int i=0;i<tabs.size();i++) {
            int index=i;
            Button button=Button.builder(tabs.get(i).name(),b -> { tab=index; scroll=0; rebuildWidgets(); })
                    .bounds(leftPos+7+i*tabWidth,topPos+27,tabWidth-2,18).build();
            button.active=i!=tab;
            addRenderableWidget(button);
        }
        if(naming()) {
            boolean allowed=view.actions().stream().anyMatch(a -> a.id()==RelationshipViews.RENAME && a.enabled());
            String saved=view.tabs().get(3).rows().stream().filter(r -> r.key().equals("town:name")).map(r -> r.detail().getString()).findFirst().orElse("");
            townName=new EditBox(font,imageWidth-90,18,Component.literal("Town name"));
            townName.setPosition(leftPos+8,topPos+LIST_TOP);
            townName.setMaxLength(48); townName.setValue(nameDraft==null ? saved : nameDraft); townName.setEditable(allowed);
            addRenderableWidget(townName);
            Button save=Button.builder(Component.literal("Save name"),b -> rename()).bounds(leftPos+imageWidth-78,topPos+LIST_TOP,70,18).build();
            save.active=allowed; addRenderableWidget(save);
        }
        List<PanelView.Action> actions=footerActions();
        int rows=actionRows(),half=(imageWidth-14)/2;
        for(int i=0;i<actions.size();i++) {
            PanelView.Action action=actions.get(i);
            int row=i/2,column=i%2;
            // A last button alone on its row spans the whole width.
            boolean alone=column==0 && i==actions.size()-1;
            Button button=Button.builder(action.label(),b -> ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,action.id(),0,0,"")))
                    .bounds(leftPos+7+column*half,topPos+imageHeight-6-(rows-row)*22,alone ? half*2-2 : half-2,20).build();
            button.active=action.enabled();
            if(!action.tooltip().getString().isEmpty()) button.setTooltip(Tooltip.create(action.tooltip()));
            addRenderableWidget(button);
        }
        List<PanelView.Row> listed=rows();
        int visible=visibleRows(),top=topPos+listTop(),right=leftPos+imageWidth-13;
        scroll=Math.clamp(scroll,0,Math.max(0,listed.size()-visible));
        for(int i=0;i<visible && scroll+i<listed.size();i++) {
            PanelView.Row row=listed.get(scroll+i);
            if(!control(row)) continue;
            int index=scroll+i,rowY=top+2+i*ROW+3;
            if(row.key().equals("color:town")) {
                Button cycle=Button.builder(Component.literal("Next color"),b -> { b.active=false; send(index,(row.value()+1)%16,row.key()); }).bounds(right-77,rowY,76,15).build();
                cycle.setTooltip(Tooltip.create(Component.literal("Choose the color of your town flag and its four corner banners")));
                addRenderableWidget(cycle); continue;
            }
            if(row.key().startsWith("permission:")) {
                String label=row.value()==0 ? "Denied" : row.value()==1 ? "Builder" : "Steward";
                Button cycle=Button.builder(Component.literal(label),b -> { b.active=false; send(index,(row.value()+1)%3,row.key()); }).bounds(right-77,rowY,60,15).build();
                cycle.setTooltip(Tooltip.create(Component.literal("Next: "+(row.value()==0 ? "invite Builder" : row.value()==1 ? "Steward" : "revoke access")+". New invitations must be accepted on the Invitations tab.")));
                Button revoke=Button.builder(Component.literal("×"),b -> { b.active=false; send(index,0,row.key()); }).bounds(right-15,rowY,14,15).build();
                revoke.active=row.value()>0; revoke.setTooltip(Tooltip.create(Component.literal("Revoke access and cancel any pending invitation")));
                addRenderableWidget(cycle); addRenderableWidget(revoke); continue;
            }
            if(row.key().startsWith("act:")) {
                String label=row.key().startsWith("act:show:") ? "Show" : row.key().startsWith("act:research:") ? "Study" : row.key().startsWith("act:project:") ? "Build" : row.key().startsWith("act:accept:") ? "Accept" : row.key().startsWith("act:decline:") ? "Decline"
                        : row.key().startsWith("act:unally:") ? "End / cancel" : row.key().startsWith("act:ally:") ? "Ally" : "Use";
                if(menu.kind==PanelMenu.Kind.MULTIPLAYER) label=row.key().contains("-accept") ? "Accept" : row.key().contains("-challenge") ? "Challenge" : row.key().contains("-deliver") ? "Deliver"
                        : row.key().contains("-release") ? "Release" : row.key().contains("-post") ? "Post" : row.key().contains("-decline") ? "Decline" : "Cancel";
                int width=menu.kind==PanelMenu.Kind.RELATIONSHIPS || menu.kind==PanelMenu.Kind.MULTIPLAYER ? 76 : 32;
                Button use=Button.builder(Component.literal(label),b -> { b.active=false; send(index,1,row.key()); })
                        .bounds(right-width-1,rowY,width,15).build();
                use.active=row.value()==0; use.setTooltip(Tooltip.create(row.detail())); addRenderableWidget(use); continue;
            }
            if(menu.kind==PanelMenu.Kind.MULTIPLAYER && row.key().startsWith("choice:")) {
                int step=row.key().equals("choice:amount") ? 16 : 1,min=row.key().equals("choice:stake") ? 0 : 1,max=row.key().equals("choice:amount") ? 256 : 64;
                Button lower=Button.builder(Component.literal("-"),b -> { b.active=false; send(index,Math.max(min,row.value()-step),row.key()); }).bounds(right-31,rowY,14,15).build();
                Button raise=Button.builder(Component.literal("+"),b -> { b.active=false; send(index,Math.min(max,row.value()+step),row.key()); }).bounds(right-15,rowY,14,15).build();
                lower.active=row.value()>min; raise.active=row.value()<max;
                lower.setTooltip(Tooltip.create(Component.literal("Lower by "+step))); raise.setTooltip(Tooltip.create(Component.literal("Raise by "+step)));
                addRenderableWidget(lower); addRenderableWidget(raise); continue;
            }
            boolean request=row.key().startsWith("request:"),member=row.key().startsWith("member:");
            int step=request ? row.icon().getMaxStackSize()==1 ? 1 : 16 : 1;
            // A row button works once per refresh, so a double click cannot also hit the row that moves into its place.
            Button lower=Button.builder(Component.literal("-"),b -> { b.active=false; send(index,member ? row.value()-1 : Math.max(0,row.value()-step),row.key()); }).bounds(right-31,rowY,14,15).build();
            lower.active=member || row.value()>JobBoard.OFF;
            lower.setTooltip(Tooltip.create(Component.literal(request ? "Lower warehouse target by "+step : member ? "Steward to builder; builder to removed" : "Lower priority")));
            Button raise=Button.builder(Component.literal("+"),b -> { b.active=false; send(index,Math.min(request ? 4096 : member ? 1 : JobBoard.HIGH,row.value()+step),row.key()); }).bounds(right-15,rowY,14,15).build();
            raise.active=row.value()<(request ? 4096 : member ? 1 : JobBoard.HIGH);
            raise.setTooltip(Tooltip.create(Component.literal(request ? "Raise warehouse target by "+step : member ? "Promote builder to steward" : "Raise priority")));
            addRenderableWidget(lower); addRenderableWidget(raise);
        }
        shown=view; layout=layout(view);
    }
    /** A row whose priority the owner sets from the list. */
    private static boolean control(PanelView.Row row) { return !row.key().isEmpty() && row.value()>=0; }
    private void send(int index,int value,String key) {
        ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,
                menu.kind==PanelMenu.Kind.MULTIPLAYER ? MultiplayerViews.ROW_ACTION : menu.kind==PanelMenu.Kind.RELATIONSHIPS ? RelationshipViews.ROW_ACTION : menu.kind==PanelMenu.Kind.CAMPAIGN || menu.kind==PanelMenu.Kind.ARMY ? CampaignViews.ROW_ACTION : Panels.JOB,index,value,key));
    }
    private boolean naming() { return menu.kind==PanelMenu.Kind.RELATIONSHIPS && tab==3; }
    private int listTop() { return naming() ? LIST_TOP+24 : LIST_TOP; }
    private List<PanelView.Action> footerActions() { return view().actions().stream().filter(a -> a.id()!=RelationshipViews.RENAME).toList(); }
    private int controlWidth(PanelView.Row row) { return !control(row) ? 0 : menu.kind==PanelMenu.Kind.RELATIONSHIPS || menu.kind==PanelMenu.Kind.MULTIPLAYER && row.key().startsWith("act:") ? 80 : 34; }
    private void rename() { if(townName!=null) ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,RelationshipViews.RENAME,0,0,townName.getValue())); }
    @Override public boolean keyPressed(KeyEvent event) {
        if(townName!=null && townName.isFocused() && event.key()!=256) {
            if(event.key()==257 || event.key()==335) { rename(); return true; }
            return townName.keyPressed(event);
        }
        return super.keyPressed(event);
    }
    /** Buttons are rebuilt only when their labels or the rows' priorities change, not for every refresh. */
    private static String layout(PanelView view) {
        StringBuilder key=new StringBuilder();
        for(PanelView.Tab tab:view.tabs()) {
            key.append(tab.name().getString()).append('|');
            for(PanelView.Row row:tab.rows()) if(control(row)) key.append(row.key()).append('=').append(row.value()).append('|');
        }
        for(PanelView.Action action:view.actions()) key.append(action.label().getString()).append(action.enabled()).append(action.tooltip().getString()).append('|');
        return key.toString();
    }
    private List<PanelView.Row> rows() {
        List<PanelView.Tab> tabs=view().tabs();
        return tabs.isEmpty() ? List.of() : tabs.get(Math.min(tab,tabs.size()-1)).rows();
    }
    private int actionRows() { return (footerActions().size()+1)/2; }
    private int listBottom() { return topPos+imageHeight-8-actionRows()*22; }
    private int visibleRows() { return Math.max(1,(listBottom()-(topPos+listTop())-2)/ROW); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int x=leftPos,y=topPos;
        Ui.window(g,x,y,imageWidth,imageHeight);
        g.text(font,Ui.fit(font,view().title().getString(),imageWidth-36),x+8,y+7,Ui.TEXT,false);
        g.text(font,Ui.fit(font,view().subtitle().getString(),imageWidth-16),x+8,y+17,Ui.MUTED,false);
        int top=y+listTop(),bottom=listBottom();
        Ui.inset(g,x+7,top,imageWidth-14,bottom-top);
        List<PanelView.Row> rows=rows();
        int visible=visibleRows();
        scroll=Math.clamp(scroll,0,Math.max(0,rows.size()-visible));
        for(int i=0;i<visible && scroll+i<rows.size();i++) {
            PanelView.Row row=rows.get(scroll+i);
            int rowY=top+2+i*ROW,left=x+9,width=imageWidth-22;
            boolean hover=mouseX>=left && mouseX<left+width && mouseY>=rowY && mouseY<rowY+ROW-1;
            g.fill(left,rowY,left+width,rowY+ROW-1,hover ? Ui.ROW_HOVER : Ui.ROW);
            if(!row.icon().isEmpty()) g.item(row.icon(),left+2,rowY+2);
            int textX=left+22,textWidth=width-26-controlWidth(row);
            g.text(font,Ui.fit(font,row.text().getString(),textWidth),textX,rowY+2,row.color()!=0 ? row.color() : Ui.TEXT,false);
            g.text(font,Ui.fit(font,row.detail().getString(),textWidth),textX,rowY+11,Ui.MUTED,false);
            if(row.bar()>=0) Ui.bar(g,textX,rowY+ROW-4,textWidth,row.bar(),row.color());
        }
        if(rows.size()>visible) {
            int track=bottom-top-4,thumb=Math.max(12,track*visible/rows.size());
            int thumbY=top+2+(track-thumb)*scroll/Math.max(1,rows.size()-visible);
            g.fill(x+imageWidth-11,top+2,x+imageWidth-9,bottom-2,Ui.SLOT_SHADE);
            g.fill(x+imageWidth-11,thumbY,x+imageWidth-9,thumbY+thumb,Ui.LIGHT);
        }
        if(rows.isEmpty()) g.text(font,"Nothing to show yet",x+12,top+6,Ui.MUTED,false);
    }
    /** Labels are part of the background; the default title and inventory labels do not apply. */
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        // A refresh that changes button labels (such as the alarm toggle) rebuilds them before anything is drawn.
        if(view()!=shown) { if(!layout(view()).equals(layout)) rebuildWidgets(); shown=view(); }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        List<PanelView.Row> rows=rows();
        int top=topPos+listTop(),left=leftPos+9;
        for(int i=0;i<visibleRows() && scroll+i<rows.size();i++) {
            int rowY=top+2+i*ROW;
            if(mouseY<rowY || mouseY>=rowY+ROW-1 || mouseX<left || mouseX>=left+imageWidth-22) continue;
            PanelView.Row row=rows.get(scroll+i);
            if(mouseX<left+20 && !row.icon().isEmpty()) g.setTooltipForNextFrame(font,row.icon(),mouseX,mouseY);
            else if(Ui.truncated(font,row.text(),imageWidth-48-controlWidth(row)) || Ui.truncated(font,row.detail(),imageWidth-48-controlWidth(row)))
                g.setComponentTooltipForNextFrame(font,Ui.tooltip(font,row),mouseX,mouseY);
        }
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        if(scrollY!=0) {
            scroll-=(int)Math.signum(scrollY);
            // Row buttons follow their rows.
            if(rows().stream().anyMatch(PanelScreen::control)) rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
}
