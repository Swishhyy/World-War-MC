package io.github.swishhyy.wwmc.settlement;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-thread transfers of real stacks. Full inventories and stock reserves never discard or create goods. */
public final class TradeGoods {
    private TradeGoods() {}
    public static int load(List<Container> warehouse,List<TradeSettings.Export> policy,TradeShipment shipment) {
        return load(warehouse,policy,shipment,(item,stack) -> true);
    }
    public static int load(List<Container> warehouse,List<TradeSettings.Export> policy,TradeShipment shipment,java.util.function.BiPredicate<Item,ItemStack> accepts) {
        int moved=0;
        for(var order:policy) {
            Item item=order.resolve();
            if(item==null || item==Items.AIR || order.load()==0) continue;
            int surplus=Math.max(0,InventoryOps.count(warehouse,s -> s.is(item) && accepts.test(item,s))-order.reserve());
            int remaining=Math.min(surplus,order.load());
            for(Container source:warehouse) for(int slot=0;slot<source.getContainerSize() && remaining>0;slot++) {
                ItemStack original=source.getItem(slot);
                if(!original.is(item) || !accepts.test(item,original)) continue;
                int amount=Math.min(remaining,original.getCount());
                ItemStack rest=InventoryOps.insert(shipment,original.copyWithCount(amount));
                int accepted=amount-rest.getCount();
                if(accepted>0) { source.removeItem(slot,accepted); source.setChanged(); moved+=accepted; remaining-=accepted; }
            }
        }
        return moved;
    }
    public static int unload(Container shipment,List<Container> warehouse) {
        int moved=0;
        for(int slot=0;slot<shipment.getContainerSize();slot++) {
            ItemStack original=shipment.getItem(slot),rest=original.copy();
            for(Container destination:warehouse) rest=InventoryOps.insert(destination,rest);
            moved+=original.getCount()-rest.getCount(); shipment.setItem(slot,rest);
        }
        return moved;
    }
}
