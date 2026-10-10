package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.AlloyFurnaceEntity;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Copper + tin makes bronze; iron + coal/charcoal makes steel. Fuel is always a separate slot. */
public final class AlloyWorkshop {
    public record Plan(String id,String research,int firstCount,int secondCount,ItemStack firstInput,ItemStack secondInput,ItemStack result,int ticks) {}
    private AlloyWorkshop() {}
    public static boolean copper(ItemStack s) { return s.is(Items.COPPER_INGOT) || s.is(Items.RAW_COPPER) || s.is(Items.COPPER_ORE) || s.is(Items.DEEPSLATE_COPPER_ORE); }
    public static boolean tin(ItemStack s) { return s.is(WWMC.TIN_INGOT.get()) || s.is(WWMC.RAW_TIN.get()) || s.is(WWMC.TIN_ORE_ITEM.get()) || s.is(WWMC.DEEPSLATE_TIN_ORE_ITEM.get()); }
    public static boolean iron(ItemStack s) { return s.is(Items.IRON_INGOT) || s.is(Items.RAW_IRON) || s.is(Items.IRON_ORE) || s.is(Items.DEEPSLATE_IRON_ORE); }
    public static boolean first(ItemStack s) { return copper(s) || iron(s); }
    public static boolean second(ItemStack s) { return tin(s) || ForgeWorkshop.fuel(s); }
    public static boolean material(ItemStack s) { return first(s) || second(s); }
    public static Plan match(ItemStack first,ItemStack second) {
        if(copper(first) && first.getCount()>=3 && tin(second) && second.getCount()>=1)
            return new Plan("bronze","bronze_age",3,1,first.copyWithCount(3),second.copyWithCount(1),new ItemStack(WWMC.BRONZE_INGOT.get(),4),400);
        if(iron(first) && first.getCount()>=1 && ForgeWorkshop.fuel(second) && second.getCount()>=1)
            return new Plan("steel","steel_working",1,1,first.copyWithCount(1),second.copyWithCount(1),new ItemStack(WWMC.STEEL_INGOT.get()),600);
        return null;
    }
    public static List<Item> catalogue() { return List.of(WWMC.BRONZE_INGOT.get(),WWMC.STEEL_INGOT.get(),WWMC.TIN_INGOT.get(),Items.COPPER_INGOT,Items.IRON_INGOT,Items.GOLD_INGOT); }
    public static int target(Settlement town,Item item) {
        return town.progress.forgeOrders.stream().filter(o -> o.resolve()==item).mapToInt(Workshop.Order::target).findFirst()
                .orElse(item==WWMC.BRONZE_INGOT.get() || item==WWMC.STEEL_INGOT.get() ? 32 : Integer.MAX_VALUE);
    }
    public static boolean order(Settlement town,String id,int target) {
        Item item=SupplyRequests.item(id); if(!catalogue().contains(item) || target<0 || target>256) return false;
        var order=new Workshop.Order(id,target);
        for(int i=0;i<town.progress.forgeOrders.size();i++) if(town.progress.forgeOrders.get(i).item().equals(id)) { town.progress.forgeOrders.set(i,order); return true; }
        if(town.progress.forgeOrders.size()>=Workshop.MAX_ORDERS) return false;
        town.progress.forgeOrders.add(order); return true;
    }
    /** Choose a whole batch against stock targets, including in-flight worker and appliance inventory. */
    public static Plan choose(ServerLevel level,Settlement town,AlloyFurnaceEntity furnace,List<Container> supplies) {
        Set<Container> unique=Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(SettlementService.townStorage(level,town)); unique.addAll(supplies); unique.add(furnace);
        var stored=new ArrayList<>(unique);
        for(Item item:List.of(WWMC.BRONZE_INGOT.get(),WWMC.STEEL_INGOT.get())) {
            boolean bronze=item==WWMC.BRONZE_INGOT.get();
            if(!Research.has(town,bronze ? "bronze_age" : "steel_working") || InventoryOps.count(stored,s -> s.is(item))>=target(town,item)) continue;
            var a=furnace.getItem(0).isEmpty() ? InventoryOps.takeBest(List.of(copy(supplies)),bronze ? AlloyWorkshop::copper : AlloyWorkshop::iron,s -> s.getCount()) : furnace.getItem(0).copy();
            var b=furnace.getItem(1).isEmpty() ? InventoryOps.takeBest(List.of(copy(supplies)),bronze ? AlloyWorkshop::tin : ForgeWorkshop::fuel,s -> s.getCount()) : furnace.getItem(1).copy();
            if(a.isEmpty() || b.isEmpty() || !furnace.getItem(0).isEmpty() && !ItemStack.isSameItemSameComponents(furnace.getItem(0),a)
                    || !furnace.getItem(1).isEmpty() && !ItemStack.isSameItemSameComponents(furnace.getItem(1),b)) continue;
            int primary=InventoryOps.count(supplies,s -> ItemStack.isSameItemSameComponents(s,a));
            int secondary=InventoryOps.count(supplies,s -> ItemStack.isSameItemSameComponents(s,b));
            if(primary< (bronze ? 3 : 1) || secondary<1) continue;
            a.setCount(bronze ? 3 : 1); b.setCount(1);
            Plan plan=match(a,b); if(plan!=null) return plan;
        }
        return null;
    }
    private static net.minecraft.world.SimpleContainer copy(List<Container> supplies) {
        var copy=new net.minecraft.world.SimpleContainer(supplies.stream().mapToInt(Container::getContainerSize).sum()); int next=0;
        for(var c:supplies) for(int i=0;i<c.getContainerSize();i++) copy.setItem(next++,c.getItem(i).copy());
        return copy;
    }
    public static void fetch(ServerLevel level,Settlement town,AlloyFurnaceEntity furnace,List<Container> storage,CitizenInventory bag) {
        var sources=new ArrayList<>(storage); sources.add(bag); sources.add(furnace);
        Plan plan=choose(level,town,furnace,sources); if(plan==null) return;
        boolean bronze=plan.id().equals("bronze");
        java.util.function.Predicate<ItemStack> first=s -> ItemStack.isSameItemSameComponents(s,plan.firstInput());
        java.util.function.Predicate<ItemStack> second=s -> ItemStack.isSameItemSameComponents(s,plan.secondInput());
        // One bounded batch keeps stock targets responsive and avoids filling both input slots indefinitely.
        carry(storage,bag,first,Math.max(0,plan.firstCount()-furnace.getItem(0).getCount()-InventoryOps.count(List.of(bag),first)));
        carry(storage,bag,second,Math.max(0,plan.secondCount()-furnace.getItem(1).getCount()-InventoryOps.count(List.of(bag),second)));
        carry(storage,bag,s -> ProcessingService.fuel(level,s) && !first.test(s),Math.max(0,2-furnace.getItem(2).getCount()-InventoryOps.count(List.of(bag),s -> ProcessingService.fuel(level,s) && !first.test(s))));
    }
    private static void carry(List<Container> storage,CitizenInventory bag,java.util.function.Predicate<ItemStack> material,int count) {
        for(int i=0;i<count && !bag.needsDelivery();i++) { ItemStack s=InventoryOps.takeOne(storage,material); if(s.isEmpty()) break; bag.offer(s); }
    }
    public static int service(ServerLevel level,Settlement town,AlloyFurnaceEntity furnace,CitizenInventory bag) {
        ItemStack output=furnace.removeItemNoUpdate(3); int count=output.getCount(); bag.offer(output);
        var sources=new ArrayList<Container>(); sources.add(bag); sources.add(furnace);
        var plan=choose(level,town,furnace,sources);
        if(plan!=null) {
            boolean bronze=plan.id().equals("bronze");
            InventoryOps.moveToSlot(List.of(bag),furnace,0,s -> ItemStack.isSameItemSameComponents(s,plan.firstInput()),Math.max(0,plan.firstCount()-furnace.getItem(0).getCount()));
            InventoryOps.moveToSlot(List.of(bag),furnace,1,s -> ItemStack.isSameItemSameComponents(s,plan.secondInput()),Math.max(0,plan.secondCount()-furnace.getItem(1).getCount()));
            InventoryOps.moveToSlot(List.of(bag),furnace,2,s -> ProcessingService.fuel(level,s),Math.max(0,2-furnace.getItem(2).getCount()));
        }
        furnace.setChanged(); return count;
    }
}
