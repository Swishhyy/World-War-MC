package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Player supply orders: payment up front, exclusive acceptance, and credit only for goods the destination accepts. */
public final class PlayerContracts {
    private PlayerContracts() {}
    public static Item item(MultiplayerData.Contract order) {
        Identifier id=Identifier.tryParse(order.item); Item item=id==null ? null : BuiltInRegistries.ITEM.getValue(id);
        return item==null ? Items.AIR : item;
    }
    public static boolean withdraw(ServerPlayer player,int emeralds) {
        if(emeralds<0 || emeralds>64 || InventoryOps.count(List.of(player.getInventory()),s -> s.is(Items.EMERALD))<emeralds) return false;
        for(int n=0;n<emeralds;n++) InventoryOps.takeOne(List.of(player.getInventory()),s -> s.is(Items.EMERALD));
        return true;
    }
    public static String post(ServerLevel level,Settlement issuer,ServerPlayer publisher,ItemStack example,int amount,int payment) {
        if(issuer==null || issuer.trading.npc || issuer.campaign.parent!=null || !TownAccess.manages(issuer,publisher.getUUID())) return "Post contracts from a main settlement you manage.";
        if(publisher.level()!=level || publisher.distanceToSqr(Vec3.atCenterOf(issuer.center))>64 || !MultiplayerViews.valid(publisher,issuer.center)) return "Post the offer at your settlement banner.";
        if(example.isEmpty() || !ItemStack.isSameItemSameComponents(example,new ItemStack(example.getItem()))) return "Hold one plain, undamaged example of the requested item.";
        if(amount<1 || amount>256 || payment<1 || payment>64) return "Request 1–256 items and reserve 1–64 emeralds.";
        var data=MultiplayerData.get(level);
        if(data.contracts.size()>=64 || data.contracts.stream().filter(c -> c.issuer.equals(issuer.id)).count()>=8) return "Finish or cancel an existing offer first (eight per settlement, 64 per world).";
        if(SettlementService.townStorage(level,issuer).isEmpty()) return "Your warehouse needs loaded storage for deliveries.";
        if(!withdraw(publisher,payment)) return "Carry the full emerald payment; it is reserved when you post.";
        var order=new MultiplayerData.Contract(UUID.randomUUID(),issuer.id,publisher.getUUID(),BuiltInRegistries.ITEM.getKey(example.getItem()).toString(),amount,0,payment,Optional.empty(),Optional.empty());
        data.contracts.add(order); data.setDirty();
        CampaignService.record(level,issuer,"Posted a player contract: "+amount+" "+example.getHoverName().getString()+" for "+payment+" emeralds (reserved).");
        WWMC.LOGGER.info("[WWMC] [contracts] {} posted {}: {} x{}, payment {}",publisher.getUUID(),order.id,order.item,amount,payment);
        return "Contract posted; payment reserved. Other settlements can accept it on a banner's Neighbours board.";
    }
    public static String accept(ServerLevel level,Settlement supplier,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement issuer=order==null ? null : SettlementData.get(level).byId(order.issuer);
        if(order==null || issuer==null || order.supplier!=null) return "This contract is no longer available.";
        if(supplier==null || supplier.trading.npc || supplier.campaign.parent!=null || !TownAccess.manages(supplier,player.getUUID())
                || supplier.id.equals(issuer.id) || supplier.owner.equals(issuer.owner)) return "Accept for a different main settlement you manage.";
        order.supplier=supplier.id; order.recipient=player.getUUID(); data.setDirty();
        CampaignService.record(level,supplier,"Accepted "+issuer.name+"'s supply contract. Deliver by trader or at its banner; payment goes to "+player.getName().getString()+".");
        WWMC.LOGGER.info("[WWMC] [contracts] {} accepted {} for settlement {}",player.getUUID(),id,supplier.id);
        return "Accepted. Connect a trade route to "+issuer.name+" and your trader delivers warehouse surplus, or carry plain goods to its banner at "+issuer.center.toShortString()+". Reserved payment goes to you after delivery.";
    }
    private static boolean plain(ItemStack stack) { return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack,new ItemStack(stack.getItem())); }
    private static List<MultiplayerData.Contract> orders(ServerLevel level,Settlement source,Settlement destination) {
        return MultiplayerData.get(level).contracts.stream().filter(c -> destination.id.equals(c.issuer) && source.id.equals(c.supplier)).toList();
    }
    /** Accepted orders add real demand without altering the destination owner's normal stock targets. */
    public static int load(ServerLevel level,Settlement source,Settlement destination,List<Container> warehouse,List<TradeSettings.Export> policy,TradeShipment shipment,boolean depot) {
        Map<String,TradeSettings.Export> exports=new LinkedHashMap<>(); policy.forEach(e -> exports.put(e.item(),e));
        Map<String,Integer> needs=new LinkedHashMap<>(); orders(level,source,destination).forEach(c -> needs.merge(c.item,c.remaining(),Integer::sum));
        for(var need:needs.entrySet()) {
            if(source.trading.exports.stream().anyMatch(e -> e.item().equals(need.getKey()) && e.load()==0)) continue;
            Item wanted=SupplyRequests.item(need.getKey()); if(wanted==null || wanted==Items.AIR) continue;
            int incoming=destination.campaign.incoming.values().stream().mapToInt(c -> Math.max(0,c.getOrDefault(need.getKey(),0))).sum();
            var existing=exports.get(need.getKey());
            int reserve=existing==null ? Math.max(source.campaign.requests.getOrDefault(need.getKey(),0),FoodHealing.food(new ItemStack(wanted)) ? Math.max(16,source.citizens.size()*2) : 16) : existing.reserve();
            int load=Math.max(existing==null ? 0 : existing.load(),Math.min(depot ? 128 : 64,Math.max(0,need.getValue()-incoming)));
            exports.put(need.getKey(),new TradeSettings.Export(need.getKey(),reserve,load));
        }
        return TradeGoods.load(warehouse,List.copyOf(exports.values()),shipment,(item,stack) -> !needs.containsKey(BuiltInRegistries.ITEM.getKey(item).toString()) || plain(stack));
    }
    public static Map<String,Integer> plainCounts(Container cargo) {
        Map<String,Integer> result=new LinkedHashMap<>();
        for(int slot=0;slot<cargo.getContainerSize();slot++) { ItemStack stack=cargo.getItem(slot); if(plain(stack)) result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum); }
        return result;
    }
    /** Called only after the physical warehouse handoff. The saved offer is removed before a second callback can pay. */
    public static void delivered(ServerLevel level,Settlement source,Settlement destination,Map<String,Integer> items) {
        var data=MultiplayerData.get(level);
        for(var entry:items.entrySet()) {
            int remaining=Math.max(0,entry.getValue());
            for(var order:orders(level,source,destination)) if(order.item.equals(entry.getKey()) && remaining>0) {
                int credit=Math.min(remaining,order.remaining()); order.delivered+=credit; remaining-=credit; data.setDirty();
                if(order.remaining()==0) complete(level,order,source,destination);
            }
        }
    }
    private static void complete(ServerLevel level,MultiplayerData.Contract order,Settlement supplier,Settlement issuer) {
        var data=MultiplayerData.get(level);
        if(!data.contracts.remove(order)) return;
        data.pay(order.recipient,order.payment); data.setDirty();
        CampaignService.record(level,issuer,"Supply contract completed by "+supplier.name+"; "+order.payment+" reserved emeralds paid.");
        CampaignService.record(level,supplier,"Completed "+issuer.name+"'s supply contract. Collect "+order.payment+" emeralds at the banner.");
        WWMC.LOGGER.info("[WWMC] [contracts] {} completed {}; released {} emeralds to {}",supplier.id,order.id,order.payment,order.recipient);
    }
    public static String routeStatus(ServerLevel level,Settlement source,Settlement destination) {
        if(source==null) return "Accept for a main settlement you manage";
        if(!TradeRoutes.agreed(source,destination)) return "Connect a trade route, or deliver at the issuer's banner";
        if(source.trading.paused || destination.trading.paused) return "Trade paused; resume at the Trader Block";
        if(!TradeRoutes.canDepart(level,source,destination)) return "Repair the Trader Blocks at both towns";
        var checkpoint=TradeRoutes.checkpoint(source);
        if(source.citizens.stream().noneMatch(id -> checkpoint.position().equals(source.jobs.home(id)))) return "Assign a citizen to the Trader Block";
        return source.trading.status+"; uses warehouse surplus after home reserves";
    }
    /** Moving from an arbitrary real container also lets the same delivery path be verified with warehouse fixtures. */
    public static int handoff(List<Container> sources,List<Container> destinations,Item wanted,int maximum) {
        int delivered=0;
        if(wanted==Items.AIR || sources.stream().anyMatch(destinations::contains)) return 0;
        for(Container source:sources) for(int slot=0;slot<source.getContainerSize() && delivered<maximum;slot++) {
            ItemStack held=source.getItem(slot);
            if(!held.is(wanted) || !ItemStack.isSameItemSameComponents(held,new ItemStack(wanted))) continue;
            ItemStack offered=held.copyWithCount(Math.min(held.getCount(),maximum-delivered));
            int before=offered.getCount();
            for(Container destination:destinations) { offered=InventoryOps.insert(destination,offered); if(offered.isEmpty()) break; }
            int accepted=before-offered.getCount();
            if(accepted>0) { source.removeItem(slot,accepted); source.setChanged(); delivered+=accepted; }
        }
        return delivered;
    }
    public static String deliver(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement issuer=order==null ? null : SettlementData.get(level).byId(order.issuer);
        Settlement supplier=order==null || order.supplier==null ? null : SettlementData.get(level).byId(order.supplier);
        if(order==null || issuer==null || !TownAccess.manages(supplier,player.getUUID())) return "Only the accepted supplier's owner or stewards may deliver.";
        if(player.level()!=level || player.distanceToSqr(Vec3.atCenterOf(issuer.center))>64 || !MultiplayerViews.valid(player,issuer.center)) return "Bring the goods to the issuing settlement's banner.";
        int moved=handoff(List.of(player.getInventory()),SettlementService.townStorage(level,issuer),item(order),order.remaining());
        if(moved==0) return "No goods moved: carry plain requested items and leave room in the issuer's loaded warehouse.";
        order.delivered+=moved; data.setDirty();
        if(order.remaining()>0) return "Delivered "+moved+". "+order.remaining()+" still needed; the payment remains reserved.";
        complete(level,order,supplier,issuer);
        return "Contract completed. Reserved emeralds are ready to collect at a banner.";
    }
    public static String cancel(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement issuer=order==null ? null : SettlementData.get(level).byId(order.issuer);
        if(order==null || order.supplier!=null || !TownAccess.manages(issuer,player.getUUID())) return "Only an unaccepted offer can be cancelled by its issuing settlement.";
        data.pay(order.publisher,order.payment); data.contracts.remove(order); data.setDirty();
        WWMC.LOGGER.info("[WWMC] [contracts] Cancelled {}; refunded {} emeralds to {}",id,order.payment,order.publisher);
        return "Offer cancelled. Reserved emeralds returned to the publisher's payment balance.";
    }
    public static String abandon(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement supplier=order==null || order.supplier==null ? null : SettlementData.get(level).byId(order.supplier);
        if(order==null || !TownAccess.manages(supplier,player.getUUID())) return "Only the accepted supplier can release this contract.";
        // Already delivered goods stay at the issuer; a new supplier finishes only the remaining amount.
        order.supplier=null; order.recipient=null; data.setDirty();
        return "Contract released. Delivered goods remain credited; the remaining request is available again.";
    }
    public static int collect(ServerLevel level,ServerPlayer player) {
        var data=MultiplayerData.get(level); long balance=data.payments.getOrDefault(player.getUUID(),0L); int paid=0;
        while(balance>0 && paid<1024) {
            ItemStack offered=new ItemStack(Items.EMERALD,(int)Math.min(64,balance));
            ItemStack left=InventoryOps.insert(player.getInventory(),offered);
            int moved=offered.getCount()-left.getCount(); if(moved==0) break;
            balance-=moved; paid+=moved;
        }
        if(balance==0) data.payments.remove(player.getUUID()); else data.payments.put(player.getUUID(),balance);
        if(paid>0) data.setDirty(); return paid;
    }
    /** Missing towns refund reserved offers instead of silently deleting the payment. Accepted offers do not expire offline. */
    public static void cleanup(ServerLevel level) {
        var data=MultiplayerData.get(level); var towns=SettlementData.get(level);
        for(var order:new ArrayList<>(data.contracts)) {
            if(towns.byId(order.issuer)==null) { data.pay(order.publisher,order.payment); data.contracts.remove(order); data.setDirty(); }
            else if(order.supplier!=null && towns.byId(order.supplier)==null) { order.supplier=null; order.recipient=null; data.setDirty(); }
        }
    }
}
