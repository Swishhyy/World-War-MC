package io.github.swishhyy.wwmc.block;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.menu.AlloyFurnaceMenu;
import io.github.swishhyy.wwmc.settlement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.state.BlockState;

/** Inputs, fuel, output and progress are real saved inventory; a full output never consumes another batch. */
public final class AlloyFurnaceEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int FIRST=0,SECOND=1,FUEL=2,OUTPUT=3;
    private NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    private int burn,burnTotal,progress,required=400,status;
    private String recipe="";
    public final ContainerData data=new ContainerData() {
        public int get(int index) { return switch(index) { case 0 -> Math.min(32767,burn); case 1 -> Math.min(32767,burnTotal); case 2 -> progress; case 3 -> required; case 4 -> status; default -> 0; }; }
        public void set(int index,int value) {}
        public int getCount() { return 5; }
    };
    public AlloyFurnaceEntity(BlockPos pos,BlockState state) { super(WWMC.ALLOY_FURNACE_ENTITY.get(),pos,state); }
    @Override public int getContainerSize() { return 4; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) { items=value; }
    @Override protected Component getDefaultName() { return Component.translatable("block.wwmc.alloy_furnace"); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory) { return new AlloyFurnaceMenu(id,inventory,this,data); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) {
        return slot==FIRST ? AlloyWorkshop.first(stack) : slot==SECOND ? AlloyWorkshop.second(stack)
                : slot==FUEL && level instanceof ServerLevel server && ProcessingService.fuel(server,stack);
    }
    @Override public int[] getSlotsForFace(Direction side) { return side==Direction.DOWN ? new int[]{OUTPUT} : side==Direction.UP ? new int[]{FIRST,SECOND} : new int[]{FUEL}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return slot==OUTPUT; }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); ContainerHelper.saveAllItems(output,items);
        output.putInt("BurnTime",burn); output.putInt("BurnTotal",burnTotal); output.putInt("AlloyProgress",progress);
        output.putString("AlloyRecipe",recipe);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); items=NonNullList.withSize(4,ItemStack.EMPTY); ContainerHelper.loadAllItems(input,items);
        burn=Math.clamp(input.getIntOr("BurnTime",0),0,200000); burnTotal=Math.clamp(input.getIntOr("BurnTotal",0),0,200000);
        progress=Math.clamp(input.getIntOr("AlloyProgress",0),0,799); recipe=input.getStringOr("AlloyRecipe","");
    }
    public static void tick(Level world,BlockPos pos,BlockState state,AlloyFurnaceEntity furnace) {
        if(!(world instanceof ServerLevel level)) return;
        boolean lit=furnace.burn>0;
        if(lit) furnace.burn--;
        var plan=AlloyWorkshop.match(furnace.getItem(FIRST),furnace.getItem(SECOND));
        String key=plan==null ? "" : plan.id();
        if(!key.equals(furnace.recipe)) { furnace.recipe=key; furnace.progress=0; }
        furnace.required=plan==null ? 400 : plan.ticks();
        Settlement town=SettlementData.get(level).at(pos);
        boolean unlocked=plan!=null && Research.has(town,plan.research());
        ItemStack output=furnace.getItem(OUTPUT);
        boolean room=plan!=null && (output.isEmpty() || ItemStack.isSameItemSameComponents(output,plan.result())
                && output.getCount()+plan.result().getCount()<=Math.min(output.getMaxStackSize(),furnace.getMaxStackSize()));
        furnace.status=plan==null ? 0 : !unlocked ? plan.id().equals("steel") ? 4 : 3 : !room ? 5 : 1;
        if(unlocked && room) {
            if(furnace.burn==0 && ProcessingService.fuel(level,furnace.getItem(FUEL))) {
                furnace.burn=furnace.getItem(FUEL).getBurnTime(RecipeType.SMELTING,level.fuelValues());
                furnace.burnTotal=furnace.burn; furnace.removeItem(FUEL,1);
            }
            if(furnace.burn>0) {
                furnace.status=2; furnace.progress++;
                if(furnace.progress>=furnace.required) {
                    furnace.removeItem(FIRST,plan.firstCount()); furnace.removeItem(SECOND,plan.secondCount());
                    if(output.isEmpty()) furnace.setItem(OUTPUT,plan.result().copy()); else { output.grow(plan.result().getCount()); furnace.setChanged(); }
                    furnace.progress=0;
                }
            }
        }
        if(lit!=(furnace.burn>0)) level.setBlockAndUpdate(pos,state.setValue(AbstractFurnaceBlock.LIT,furnace.burn>0));
        if(lit || furnace.burn>0 || !key.equals("")) furnace.setChanged();
    }
}
