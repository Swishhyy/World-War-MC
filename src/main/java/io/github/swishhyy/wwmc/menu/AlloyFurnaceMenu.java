package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.settlement.AlloyWorkshop;
import io.github.swishhyy.wwmc.settlement.ProcessingService;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

/** Four server-owned slots; shift-click and hoppers obey the same material and fuel rules. */
public final class AlloyFurnaceMenu extends AbstractContainerMenu {
    public static final int WIDTH=176,HEIGHT=180,INVENTORY_Y=98;
    private final Container furnace;
    public final ContainerData data;
    public AlloyFurnaceMenu(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(4),new SimpleContainerData(5)); }
    public AlloyFurnaceMenu(int id,Inventory inventory,Container furnace,ContainerData data) {
        super(WwmcMenus.ALLOY_FURNACE.get(),id); checkContainerSize(furnace,4); checkContainerDataCount(data,5);
        this.furnace=furnace; this.data=data;
        addSlot(input(furnace,0,38,28)); addSlot(input(furnace,1,62,28)); addSlot(input(furnace,2,50,56));
        addSlot(new Slot(furnace,3,124,36) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        addStandardInventorySlots(inventory,8,INVENTORY_Y); addDataSlots(data);
    }
    private Slot input(Container furnace,int slot,int x,int y) {
        return new Slot(furnace,slot,x,y) { @Override public boolean mayPlace(ItemStack s) { return slot==0 ? AlloyWorkshop.first(s) : slot==1 ? AlloyWorkshop.second(s)
                : furnace instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity entity ? entity.canPlaceItem(slot,s) : true; } };
    }
    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); if(!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index<4) { if(!moveItemStackTo(stack,4,slots.size(),true)) return ItemStack.EMPTY; }
        else {
            boolean moved=false;
            if(AlloyWorkshop.first(stack)) moved=moveItemStackTo(stack,0,1,false);
            else if(AlloyWorkshop.second(stack)) {
                if(io.github.swishhyy.wwmc.settlement.ForgeWorkshop.fuel(stack)) {
                    ItemStack carbon=stack.copyWithCount(1);
                    if(moveItemStackTo(carbon,1,2,false)) { stack.shrink(1-carbon.getCount()); moved=true; }
                } else moved=moveItemStackTo(stack,1,2,false);
            }
            if(!stack.isEmpty() && player.level() instanceof ServerLevel level && ProcessingService.fuel(level,stack)) moved|=moveItemStackTo(stack,2,3,false);
            if(!moved) return ItemStack.EMPTY;
        }
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack); return original;
    }
    public String status() { return switch(data.get(4)) { case 1 -> "Needs fuel"; case 2 -> "Alloying"; case 3 -> "Needs Bronze Age research"; case 4 -> "Needs Steelworking research"; case 5 -> "Output is full"; default -> "Add both alloy materials"; }; }
}
