package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Offers lock up existing NPC warehouse goods. Only actual delivery advances an accepted order. */
public final class CampaignContracts {
    private CampaignContracts() {}
    public static SupplyContract offer(ServerLevel level,Settlement npc) {
        if(npc.campaign.contracts.size()>=4) return null;
        List<Container> stock=SettlementService.storage(level,npc);
        String wanted=InventoryOps.count(stock,FoodHealing::food)<32 ? "minecraft:bread"
                : switch(npc.trading.specialty) { case "farming" -> "minecraft:iron_ingot"; case "mining" -> "minecraft:oak_log"; default -> "minecraft:bread"; };
        var reward=InventoryOps.takeOne(stock,s -> !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(wanted)
                && (s.is(Items.EMERALD) || s.is(Items.IRON_INGOT) || s.is(Items.OAK_LOG) || s.is(Items.CARROT))
                && InventoryOps.count(stock,t -> t.is(s.getItem()))>32);
        if(reward.isEmpty()) return null;
        int n=reward.is(Items.EMERALD) ? 4 : 16;
        while(reward.getCount()<n) { ItemStack next=InventoryOps.takeOne(stock,s -> ItemStack.isSameItemSameComponents(s,reward)); if(next.isEmpty()) break; reward.grow(1); }
        SupplyContract order=new SupplyContract(UUID.randomUUID(),wanted,32,0,reward,Optional.empty(),level.getGameTime()+72000);
        npc.campaign.contracts.add(order);
        CampaignService.record(level,npc,"Requests 32 "+new ItemStack(SupplyRequests.item(wanted)).getHoverName().getString()+"; reward held in the contract board.");
        return order;
    }
    public static String accept(ServerLevel level,Settlement customer,Settlement issuer,UUID id) {
        if(!issuer.trading.npc || issuer.trading.relations.getOrDefault(customer.owner,0)<0) return "That town refuses your contracts.";
        SupplyContract order=issuer.campaign.contracts.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
        if(order==null || order.expires<=level.getGameTime() || order.complete()) return "That offer is no longer available.";
        if(order.customer!=null && !order.customer.equals(customer.id)) return "Another town already accepted this order.";
        order.customer=customer.id;
        refreshDemand(issuer,order.item);
        CampaignService.record(level,customer,"Accepted "+issuer.name+"'s order for "+order.amount+" "+new ItemStack(SupplyRequests.item(order.item)).getHoverName().getString()+". Deliver by trader or in person.");
        return "Contract accepted. Connect a supply route or bring the goods to that town.";
    }
    private static void refreshDemand(Settlement npc,String item) {
        int required=npc.campaign.contracts.stream().filter(c -> c.item.equals(item) && c.customer!=null).mapToInt(SupplyContract::remaining).sum();
        int target=Math.max(NeighbourTrade.baseTarget(npc,item),required>0 ? Math.min(4096,npc.campaign.stock.getOrDefault(item,0)+required) : 0);
        if(target>0) npc.campaign.requests.put(item,target); else npc.campaign.requests.remove(item);
    }
    public static void refreshDemands(Settlement npc) {
        npc.campaign.contracts.stream().map(c -> c.item).distinct().forEach(item -> refreshDemand(npc,item));
    }
    public static void delivered(ServerLevel level,Settlement source,Settlement destination,Map<String,Integer> delivered,Container rewards) {
        if(!destination.trading.npc) return;
        SupplyRequests.snapshotLoaded(level,destination);
        for(var entry:delivered.entrySet()) {
            int remaining=entry.getValue();
            for(SupplyContract order:destination.campaign.contracts) if(source.id.equals(order.customer) && order.item.equals(entry.getKey()) && !order.complete()) {
                int credited=Math.min(remaining,order.remaining()); order.delivered+=credited; remaining-=credited;
                if(order.complete()) {
                    CampaignService.record(level,source,"Completed "+destination.name+"'s supply contract. Its reward travels home with the carrier.");
                    destination.trading.relations.merge(source.owner,32,(a,b) -> Math.min(1000,a+b));
                }
                if(remaining==0) break;
            }
        }
        for(SupplyContract order:destination.campaign.contracts) if(source.id.equals(order.customer) && order.complete() && !order.reward.isEmpty())
            order.reward=InventoryOps.insert(rewards,order.reward);
        delivered.keySet().forEach(item -> refreshDemand(destination,item));
        SettlementData.get(level).setDirty();
    }
    public static void expire(ServerLevel level,Settlement npc) {
        Iterator<SupplyContract> orders=npc.campaign.contracts.iterator();
        while(orders.hasNext()) {
            SupplyContract order=orders.next();
            // Accepted contracts have no offline deadline. Completed rewards remain claimable until physically collected.
            if(order.customer!=null) { if(order.complete() && order.reward.isEmpty()) { orders.remove(); SettlementData.get(level).setDirty(); } continue; }
            if(order.expires>level.getGameTime()) continue;
            ItemStack rest=order.reward;
            for(Container warehouse:SettlementService.storage(level,npc)) rest=InventoryOps.insert(warehouse,rest);
            order.reward=rest;
            if(rest.isEmpty()) orders.remove();
            SettlementData.get(level).setDirty();
        }
    }
    public static Map<String,Integer> counts(Container container) {
        Map<String,Integer> result=new LinkedHashMap<>();
        for(int n=0;n<container.getContainerSize();n++) { ItemStack s=container.getItem(n); if(!s.isEmpty()) result.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.getCount(),Integer::sum); }
        return result;
    }
}
