package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import io.github.swishhyy.wwmc.core.StructureRole;

/** Stock targets turn exports into demand-driven shipments; the source's own targets are always reserved. */
public final class SupplyRequests {
    private SupplyRequests() {}
    public static Item item(String key) {
        Identifier id=Identifier.tryParse(key); Item item=id==null ? null : BuiltInRegistries.ITEM.getValue(id); return item==null ? Items.AIR : item;
    }
    public static boolean set(Settlement town,String key,int target) {
        Item item=item(key); if(item==null || item==Items.AIR || target<0 || target>4096) return false;
        key=BuiltInRegistries.ITEM.getKey(item).toString();
        if(target==0) town.campaign.requests.remove(key);
        else if(town.campaign.requests.size()<24 || town.campaign.requests.containsKey(key)) town.campaign.requests.put(key,target);
        else return false;
        return true;
    }
    public static void snapshot(Settlement town,List<Container> stock) {
        town.campaign.stock.clear();
        for(Container box:stock) for(int slot=0;slot<box.getContainerSize();slot++) {
            var stack=box.getItem(slot); if(!stack.isEmpty()) town.campaign.stock.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
        }
        if(town.trading.npc) { NeighbourTrade.refreshRequests(town); CampaignContracts.refreshDemands(town); }
    }
    public static void snapshotLoaded(ServerLevel level,Settlement town) {
        var warehouses=town.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE).toList();
        if(warehouses.isEmpty() || warehouses.stream().anyMatch(s -> !level.hasChunkAt(s.position()))) return;
        var stock=SettlementService.storage(level,town); if(!stock.isEmpty()) snapshot(town,stock);
    }
    public static List<TradeSettings.Export> policy(Settlement source,Settlement destination,boolean depot) {
        Map<String,TradeSettings.Export> result=new LinkedHashMap<>();
        for(var export:source.trading.exports) {
            int reserve=Math.max(export.reserve(),source.campaign.requests.getOrDefault(export.item(),0));
            int load=Math.min(depot ? 128 : 64,export.load()*(depot ? 2 : 1));
            if(destination.campaign.requests.containsKey(export.item())) load=Math.min(load,deficit(destination,export.item()));
            result.put(export.item(),new TradeSettings.Export(export.item(),reserve,load));
        }
        for(var request:destination.campaign.requests.entrySet()) if(!result.containsKey(request.getKey())) {
            Item material=item(request.getKey());
            int reserve=Math.max(source.campaign.requests.getOrDefault(request.getKey(),0),
                    FoodHealing.food(new net.minecraft.world.item.ItemStack(material)) ? Math.max(16,source.citizens.size()*2) : 16);
            result.put(request.getKey(),new TradeSettings.Export(request.getKey(),reserve,Math.min(depot ? 128 : 64,deficit(destination,request.getKey()))));
        }
        // Accepted NPC contracts belong to their customer; cap that customer's physical delivery at the order remainder.
        if(destination.trading.npc) for(var entry:new ArrayList<>(result.entrySet())) {
            boolean contractItem=destination.campaign.contracts.stream().anyMatch(c -> c.item.equals(entry.getKey()) && c.customer!=null && !c.complete());
            if(contractItem) {
                int needed=destination.campaign.contracts.stream().filter(c -> c.item.equals(entry.getKey()) && source.id.equals(c.customer)).mapToInt(SupplyContract::remaining).sum();
                var old=entry.getValue(); result.put(entry.getKey(),new TradeSettings.Export(old.item(),old.reserve(),Math.min(old.load(),needed)));
            }
        }
        return List.copyOf(result.values());
    }
    public static int deficit(Settlement town,String item) {
        int arriving=town.campaign.incoming.values().stream().mapToInt(load -> Math.max(0,load.getOrDefault(item,0))).sum();
        return Math.max(0,town.campaign.requests.getOrDefault(item,0)-town.campaign.stock.getOrDefault(item,0)-arriving);
    }
}
