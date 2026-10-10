package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public final class CampaignChecks {
    private static Settlement town(UUID owner,int x) { return new Settlement(UUID.randomUUID(),owner,"Test Town",new BlockPos(x,64,0),240,List.of(),List.of(),"balanced"); }
    @Test void membershipRequiresAcceptanceAndNeverTransfersOwnership() {
        UUID owner=UUID.randomUUID(),friend=UUID.randomUUID(),stranger=UUID.randomUUID(); Settlement town=town(owner,0);
        assertFalse(TownAccess.manages(town,friend)); TownAccess.invite(town,stranger,friend,"steward"); assertTrue(town.campaign.invitations.isEmpty());
        TownAccess.invite(town,owner,friend,"steward"); assertFalse(TownAccess.manages(town,friend)); assertTrue(TownAccess.accept(town,friend));
        assertTrue(TownAccess.manages(town,friend)); assertFalse(TownAccess.owner(town,friend)); assertFalse(TownAccess.accept(town,friend));
        TownAccess.invite(town,friend,stranger,"steward"); assertFalse(town.campaign.invitations.containsKey(stranger));
        town.campaign.members.put(friend,"builder"); assertTrue(TownAccess.builds(town,friend)); assertFalse(TownAccess.manages(town,friend));
        town.campaign.members.remove(friend); assertFalse(TownAccess.builds(town,friend)); assertEquals(owner,town.owner);
    }
    @Test void alliancesRequireBothOwnersAndGrantNoManagement() {
        Settlement a=town(UUID.randomUUID(),0),b=town(UUID.randomUUID(),1000); UUID steward=UUID.randomUUID(); a.campaign.members.put(steward,"steward");
        TownAccess.alliance(a,b,steward); assertTrue(b.campaign.allianceOffers.isEmpty());
        TownAccess.alliance(a,b,a.owner); assertFalse(TownAccess.allied(a,b)); assertTrue(b.campaign.allianceOffers.contains(a.id));
        TownAccess.alliance(b,a,b.owner); assertTrue(TownAccess.allied(a,b)); assertFalse(TownAccess.manages(b,a.owner));
        a.campaign.extraRoutes.add(b.id); b.campaign.extraRoutes.add(a.id); assertTrue(TradeRoutes.agreed(a,b));
        TownAccess.leaveAlliance(a,b); assertFalse(TownAccess.allied(a,b)); assertFalse(TradeRoutes.agreed(a,b));
    }
    @Test void stockTargetsReserveHomeSupplyAndStopAtTheDestinationTarget(MinecraftServer server) {
        Settlement source=town(UUID.randomUUID(),0),destination=town(source.owner,1000);
        source.campaign.requests.put("minecraft:bread",32); source.trading.exports.add(new TradeSettings.Export("minecraft:bread",8,64));
        destination.campaign.requests.put("minecraft:bread",16);
        SimpleContainer pantry=new SimpleContainer(3); pantry.setItem(0,new ItemStack(Items.BREAD,64));
        TradeShipment load=new TradeShipment(); assertEquals(16,TradeGoods.load(List.of(pantry),SupplyRequests.policy(source,destination,false),load));
        assertEquals(48,pantry.getItem(0).getCount()); destination.campaign.stock.put("minecraft:bread",16);
        assertEquals(0,TradeGoods.load(List.of(pantry),SupplyRequests.policy(source,destination,false),new TradeShipment()));
        destination.campaign.stock.put("minecraft:bread",0); destination.campaign.requests.put("minecraft:bread",64);
        assertEquals(16,TradeGoods.load(List.of(pantry),SupplyRequests.policy(source,destination,false),new TradeShipment()));
        assertEquals(32,pantry.getItem(0).getCount());
    }
    @Test void concurrentShipmentsReserveDemandUntilDeliveryOrLoss() {
        Settlement town=town(UUID.randomUUID(),0); town.campaign.requests.put("minecraft:iron_ingot",64); town.campaign.stock.put("minecraft:iron_ingot",16);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(); town.campaign.incoming.put(first,Map.of("minecraft:iron_ingot",32));
        assertEquals(16,SupplyRequests.deficit(town,"minecraft:iron_ingot")); town.campaign.incoming.put(second,Map.of("minecraft:iron_ingot",16)); assertEquals(0,SupplyRequests.deficit(town,"minecraft:iron_ingot"));
        town.campaign.stock.put("minecraft:iron_ingot",48); town.campaign.incoming.remove(first); assertEquals(0,SupplyRequests.deficit(town,"minecraft:iron_ingot"));
        town.campaign.incoming.remove(second); assertEquals(16,SupplyRequests.deficit(town,"minecraft:iron_ingot"));
    }
    @Test void pausedExportsRemainPausedEvenWhenAnotherTownRequestsThem(MinecraftServer server) {
        Settlement source=town(UUID.randomUUID(),0),destination=town(source.owner,1000); destination.campaign.requests.put("minecraft:iron_ingot",64);
        source.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",0,0));
        assertEquals(0,SupplyRequests.policy(source,destination,true).getFirst().load());
        assertFalse(SupplyRequests.set(source,"minecraft:air",16)); assertFalse(SupplyRequests.set(source,"missing:gone_item",16));
        assertTrue(SupplyRequests.set(source,"minecraft:bread",32)); assertTrue(SupplyRequests.set(source,"minecraft:bread",0)); assertFalse(source.campaign.requests.containsKey("minecraft:bread"));
        assertTrue(SupplyRequests.set(source,"bread",32)); assertEquals(32,source.campaign.requests.get("minecraft:bread")); assertFalse(source.campaign.requests.containsKey("bread"));
    }
    @Test void contractDemandTracksRemainingDeliveryAfterStockChanges(MinecraftServer server) {
        Settlement npc=town(UUID.randomUUID(),1000),customer=town(UUID.randomUUID(),0); npc.trading.npc=true;
        SupplyContract order=new SupplyContract(UUID.randomUUID(),"minecraft:bread",32,0,new ItemStack(Items.EMERALD,4),Optional.of(customer.id),72000);
        npc.campaign.contracts.add(order); SimpleContainer pantry=new SimpleContainer(2); pantry.setItem(0,new ItemStack(Items.BREAD,16));
        SupplyRequests.snapshot(npc,List.of(pantry)); assertEquals(32,SupplyRequests.deficit(npc,"minecraft:bread"));
        order.delivered=16; pantry.getItem(0).grow(16); SupplyRequests.snapshot(npc,List.of(pantry)); assertEquals(16,SupplyRequests.deficit(npc,"minecraft:bread"));
        pantry.getItem(0).shrink(8); SupplyRequests.snapshot(npc,List.of(pantry)); assertEquals(16,SupplyRequests.deficit(npc,"minecraft:bread"));
        order.delivered=32; SupplyRequests.snapshot(npc,List.of(pantry)); assertEquals(16,npc.campaign.requests.get("minecraft:bread")); assertEquals(0,SupplyRequests.deficit(npc,"minecraft:bread"));
    }
    @Test void projectsSpendMaterialsAtomicallyAndOnlyOnce(MinecraftServer server) {
        var stock=new SimpleContainer(3); stock.setItem(0,new ItemStack(Items.IRON_INGOT,24)); stock.setItem(1,new ItemStack(Items.BIRCH_PLANKS,31));
        var costs=TownProjects.byId("armory").costs(); assertFalse(TownProjects.pay(List.of(stock),costs)); assertEquals(24,stock.getItem(0).getCount());
        stock.getItem(1).grow(1); assertTrue(TownProjects.pay(List.of(stock),costs)); assertTrue(stock.isEmpty()); assertFalse(TownProjects.pay(List.of(stock),costs));
    }
    @Test void depotCapacityAndContractRewardsAreIsolatedAndSurviveReload(MinecraftServer server) {
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var source=new SimpleContainer(3); source.setItem(0,new ItemStack(Items.IRON_INGOT,64)); source.setItem(1,new ItemStack(Items.IRON_INGOT,64));
        TradeShipment shipment=new TradeShipment(); shipment.capacity=12;
        assertEquals(128,TradeGoods.load(List.of(source),List.of(new TradeSettings.Export("minecraft:iron_ingot",0,128)),shipment));
        ItemStack reward=new ItemStack(Items.EMERALD,4); reward.set(DataComponents.CUSTOM_NAME,Component.literal("Contract payment")); shipment.rewards.setItem(0,reward);
        shipment.destination=UUID.randomUUID(); shipment.stage="return";
        var copy=TradeShipment.CODEC.parse(ops,TradeShipment.CODEC.encodeStart(ops,shipment).getOrThrow()).getOrThrow();
        assertEquals(12,copy.capacity); assertTrue(ItemStack.matches(reward,copy.rewards.getItem(0))); assertEquals(128,InventoryOps.count(List.of(copy),s -> s.is(Items.IRON_INGOT)));
        copy.clearContent(); assertThrows(IllegalStateException.class,copy::finish);
        var home=new SimpleContainer(1); assertEquals(4,TradeGoods.unload(copy.rewards,List.of(home))); copy.finish(); assertFalse(copy.travelling());
    }
    @Test void campaignStateAndOldSavesRoundTripWithoutChangingTownIdentity(MinecraftServer server) {
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE); Settlement original=town(UUID.randomUUID(),0); UUID leader=UUID.randomUUID(),guard=UUID.randomUUID();
        original.campaign.members.put(leader,"steward"); original.campaign.invitations.put(UUID.randomUUID(),"builder"); original.campaign.allies.add(UUID.randomUUID());
        original.campaign.playerNames.put(leader,"OfflineFriend");
        original.campaign.requests.put("minecraft:bread",32); original.campaign.projects.addAll(List.of("armory","depot")); original.campaign.specialty="mining";
        original.campaign.squads.add(new CampaignState.Squad(leader,List.of(guard),"escort",original.center,Optional.of(UUID.randomUUID())));
        original.citizenPlaces.put(guard,original.center.east(80));
        original.campaign.parent=UUID.randomUUID(); original.campaign.nextEvent=12345; original.campaign.routeCursor=3;
        original.campaign.incoming.put(guard,Map.of("minecraft:bread",16)); original.campaign.extraRoutes.add(UUID.randomUUID()); original.campaign.log(20,"Shipment received");
        original.campaign.contracts.add(new SupplyContract(UUID.randomUUID(),"minecraft:bread",32,8,new ItemStack(Items.EMERALD,4),Optional.of(original.id),12000));
        var json=Settlement.CODEC.encodeStart(ops,original).getOrThrow(); Settlement copy=Settlement.CODEC.parse(ops,json).getOrThrow();
        assertEquals(original.id,copy.id); assertEquals(original.owner,copy.owner); assertEquals(original.campaign.members,copy.campaign.members);
        assertEquals(original.campaign.playerNames,copy.campaign.playerNames);
        assertEquals(original.campaign.incoming,copy.campaign.incoming); assertEquals(original.campaign.squads,copy.campaign.squads);
        assertEquals(original.citizenPlaces,copy.citizenPlaces);
        assertEquals(original.campaign.requests,copy.campaign.requests); assertEquals(original.campaign.projects,copy.campaign.projects); assertEquals(original.campaign.parent,copy.campaign.parent);
        assertEquals(8,copy.campaign.contracts.getFirst().delivered); assertEquals(4,copy.campaign.contracts.getFirst().reward.getCount());
        json.getAsJsonObject().remove("campaign"); Settlement old=Settlement.CODEC.parse(ops,json).getOrThrow();
        assertEquals(original.id,old.id); assertTrue(old.campaign.members.isEmpty()); assertTrue(old.campaign.squads.isEmpty()); assertTrue(old.campaign.projects.isEmpty());
        assertTrue(TownAccess.manages(old,old.owner));
    }
    @Test void aFullTownRetainsAnInvitationUntilThereIsRoom() {
        Settlement town=town(UUID.randomUUID(),0); UUID friend=UUID.randomUUID();
        for(int n=0;n<32;n++) town.campaign.members.put(UUID.randomUUID(),"builder");
        town.campaign.invitations.put(friend,"steward");
        assertFalse(TownAccess.accept(town,friend)); assertTrue(TownAccess.invited(town,friend)); assertFalse(TownAccess.builds(town,friend));
        town.campaign.members.remove(town.campaign.members.keySet().iterator().next());
        assertTrue(TownAccess.accept(town,friend)); assertTrue(TownAccess.manages(town,friend)); assertFalse(TownAccess.invited(town,friend));
    }
    @Test void journalAndSquadSizesAreBoundedAndIndustryDoesNotDisableOtherJobs() {
        var state=new CampaignState(); for(int n=0;n<100;n++) state.log(n,"Event "+n); assertEquals(64,state.journal.size()); assertEquals(36,state.journal.getFirst().time());
        var ids=new ArrayList<UUID>(); for(int n=0;n<20;n++) ids.add(UUID.randomUUID());
        assertEquals(6,new CampaignState.Squad(UUID.randomUUID(),ids,"follow",BlockPos.ZERO,Optional.empty()).guards().size());
        Settlement town=town(UUID.randomUUID(),0); town.campaign.specialty="mining"; assertEquals(100,Specialization.ticks(town,StructureRole.FARM,100));
        assertEquals(90,Specialization.ticks(town,StructureRole.MINE,100)); town.campaign.projects.add("terrain_bonus"); assertEquals(80,Specialization.ticks(town,StructureRole.MINE,100));
        assertEquals(4,SquadService.limit(town)); town.campaign.projects.add("training"); assertEquals(6,SquadService.limit(town));
    }
}
