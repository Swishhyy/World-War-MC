package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.AlloyFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Recipes remain visible while the furnace is idle, with an explicit live pause reason. */
public final class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {
    public AlloyFurnaceScreen(AlloyFurnaceMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,AlloyFurnaceMenu.WIDTH,AlloyFurnaceMenu.HEIGHT); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int x=leftPos,y=topPos; Ui.window(g,x,y,imageWidth,imageHeight);
        g.text(font,title,x+8,y+7,Ui.TEXT,false);
        Ui.slot(g,x+38,y+28); Ui.slot(g,x+62,y+28); Ui.slot(g,x+50,y+56); Ui.slot(g,x+124,y+36);
        g.text(font,"+",x+55,y+32,Ui.TEXT,false); g.text(font,"→",x+93,y+39,Ui.TEXT,false);
        Ui.bar(g,x+88,y+52,24,menu.data.get(2)/(float)Math.max(1,menu.data.get(3)),0xFFAA702C);
        if(menu.data.get(0)>0) Ui.bar(g,x+50,y+75,16,menu.data.get(0)/(float)Math.max(1,menu.data.get(1)),0xFFDD8128);
        g.text(font,"Fuel",x+13,y+61,Ui.MUTED,false);
        g.text(font,Ui.fit(font,menu.status(),160),x+8,y+82,Ui.TEXT,false);
        Ui.inventory(g,x+8,y+AlloyFurnaceMenu.INVENTORY_Y);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        if(mouseX>=leftPos+8 && mouseX<leftPos+168 && mouseY>=topPos+78 && mouseY<topPos+94)
            g.setComponentTooltipForNextFrame(font,java.util.List.of(Component.literal(menu.status()),
                    Component.literal("Bronze: 3 copper + 1 tin → 4 ingots (20 s)"),Component.literal("Steel: 1 iron + 1 coal/charcoal → 1 ingot (30 s)"),
                    Component.literal("Metal ingots, raw metals and ores work. Add separate fuel."),Component.literal("Place inside your researched settlement.")),mouseX,mouseY);
    }
}
