package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Actual inventory transfers, damage hooks, consent, timed capture and saved-state recovery in a server world. */
public final class MultiplayerWorldTests {
    /** Use ordinary damage and team permission behavior while retaining a network sink for the dedicated test server. */
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level,String name,BlockPos pos) {
            super(level,new GameProfile(UUID.randomUUID(),name)); setInvulnerable(false); setHealth(getMaxHealth());
            setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5); level.addNewPlayer(this);
            connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        }
        @Override public boolean canHarmPlayer(Player other) { return getTeam()==null || getTeam()!=other.getTeam() || getTeam().isAllowFriendlyFire(); }
    }
    private static final class Fixture {
        final ServerLevel level; final BlockPos start;
        final List<Settlement> towns=new ArrayList<>(); final List<TestPlayer> players=new ArrayList<>();
        final Map<BlockPos,List<ChunkPos>> chunks=new HashMap<>(); final List<CitizenEntity> citizens=new ArrayList<>();
        final List<ExpeditionData.Site> sites=new ArrayList<>();
        Fixture(ServerLevel level,BlockPos start) { this.level=level; this.start=start; MultiplayerData.get(level); }
        TestPlayer player(String name,BlockPos pos) { var player=new TestPlayer(level,name,pos); players.add(player); return player; }
        Settlement town(UUID owner,BlockPos pos,String name) {
            chunks.put(pos,CitizenNavigationTests.pinArea(level,pos,-10,10,-10,10)); CitizenNavigationTests.meadow(level,pos,-10,10,-10,10);
            Station warehouse=new Station(pos.west(3),StructureRole.WAREHOUSE);
            var town=new Settlement(UUID.randomUUID(),owner,name,pos,Settlement.MIN_RADIUS,List.of(),List.of(warehouse),"balanced");
            towns.add(town); SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
            level.setBlockAndUpdate(pos,WWMC.BANNER.get().defaultBlockState());
            level.setBlockAndUpdate(warehouse.position(),WWMC.STATIONS.get(warehouse.role()).get().defaultBlockState());
            level.setBlockAndUpdate(pos.west(4),Blocks.BARREL.defaultBlockState()); return town;
        }
        Container stock(Settlement town) { return (Container)level.getBlockEntity(town.center.west(4)); }
        void close() {
            var data=MultiplayerData.get(level); Set<UUID> ids=new HashSet<>(); towns.forEach(t -> { ids.add(t.id); TradeChunks.release(level,t.id); });
            data.contracts.removeIf(c -> ids.contains(c.issuer) || ids.contains(c.supplier));
            data.contests.removeIf(c -> ids.contains(c.outpost) || ids.contains(c.challenger) || ids.contains(c.defender));
            for(var player:players) { data.retiredDuels.removeIf(d -> d.includes(player.getUUID())); data.payments.remove(player.getUUID()); level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED); }
            data.setDirty(); citizens.forEach(CitizenEntity::discard);
            SettlementData.get(level).settlements.removeAll(towns); SettlementData.get(level).setDirty();
            ExpeditionData.get(level).sites.removeAll(sites); ExpeditionData.get(level).setDirty();
            chunks.forEach((pos,pinned) -> CitizenNavigationTests.release(level,pos,pinned));
        }
    }
    private static PlayerInteractEvent.RightClickBlock use(ServerPlayer player,BlockPos pos) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
    }
    @GameTest(timeoutTicks=120)
    @EmptyTemplate
    @TestHolder(description="The command posts one real reserved payment, another settlement accepts exclusively, public banner access grants no storage/research rights, partial delivery respects full storage, and a full recipient inventory retains payment without duplication.")
    static void playerContractEscrowAndDelivery(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(14000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer issuer=f.player("ContractIssuer",start.east()),supplier=f.player("ContractSupplier",start.east(601));
            Settlement a=f.town(issuer.getUUID(),start,"Requesting Town"),b=f.town(supplier.getUUID(),start.east(600),"Supplying Town");
            issuer.getInventory().setItem(0,new ItemStack(Items.BREAD)); issuer.getInventory().setItem(1,new ItemStack(Items.EMERALD,8));
            try { level.getServer().getCommands().getDispatcher().execute("wwmc playercontract post 10 4",issuer.createCommandSourceStack()); }
            catch(Exception error) { throw new AssertionError("Posting command failed",error); }
            var data=MultiplayerData.get(level); var offers=data.contracts.stream().filter(c -> c.issuer.equals(a.id)).toList();
            helper.assertTrue(offers.size()==1 && issuer.getInventory().countItem(Items.EMERALD)==4 && issuer.getInventory().countItem(Items.BREAD)==1,"Posting duplicated an offer/payment or spent the example");
            var order=offers.getFirst(); PlayerContracts.accept(level,b,supplier,order.id);
            helper.assertTrue(b.id.equals(order.supplier) && supplier.getUUID().equals(order.recipient),"Exclusive supplier or payment recipient was not reserved");
            PlayerContracts.accept(level,a,issuer,order.id); helper.assertTrue(b.id.equals(order.supplier),"A second accept stole a reserved contract");
            supplier.setPos(start.getX()+1.5,start.getY(),start.getZ()+1.5); supplier.getInventory().setItem(0,ItemStack.EMPTY);
            a.progress.research.add("iron_age");
            helper.assertTrue(!use(supplier,start).isCanceled() && MultiplayerViews.valid(supplier,start) && use(supplier,start.west(4)).isCanceled(),"Public banner access opened storage or blocked the board");
            helper.assertTrue(ClaimProtection.denied(level,supplier,start) && !TownAccess.manages(a,supplier.getUUID()) && !AgeProgression.allowed(supplier,new ItemStack(Items.IRON_SWORD)),"Public board granted permissions or shared research");
            PlayerContracts.cancel(level,issuer,order.id); helper.assertTrue(data.contract(order.id)==order,"Issuer cancelled an accepted contract and reclaimed payment");
            Container stock=f.stock(a); for(int slot=0;slot<stock.getContainerSize();slot++) stock.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            stock.setItem(0,new ItemStack(Items.BREAD,61)); supplier.getInventory().setItem(9,new ItemStack(Items.BREAD,10));
            MultiplayerViews.act(supplier,start,MultiplayerViews.ROW_ACTION,1,"act:contract-deliver:"+order.id);
            helper.assertTrue(order.delivered==3 && supplier.getInventory().countItem(Items.BREAD)==7 && stock.getItem(0).getCount()==64 && !data.payments.containsKey(supplier.getUUID()),"Full warehouse credited, lost or paid for undelivered goods");
            PlayerContracts.deliver(level,supplier,order.id); helper.assertTrue(order.delivered==3 && supplier.getInventory().countItem(Items.BREAD)==7,"Zero-capacity retry lost goods or advanced progress");
            stock.setItem(1,ItemStack.EMPTY); PlayerContracts.deliver(level,supplier,order.id);
            helper.assertTrue(data.contract(order.id)==null && supplier.getInventory().countItem(Items.BREAD)==0 && stock.getItem(1).is(Items.BREAD) && stock.getItem(1).getCount()==7
                    && data.payments.getOrDefault(supplier.getUUID(),0L)==4,"Completion did not conserve real goods and reserved payment");
            PlayerContracts.deliver(level,supplier,order.id); helper.assertTrue(data.payments.getOrDefault(supplier.getUUID(),0L)==4,"Duplicate delivery paid twice");
            for(int slot=0;slot<supplier.getInventory().getContainerSize();slot++) supplier.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            helper.assertTrue(PlayerContracts.collect(level,supplier)==0 && data.payments.getOrDefault(supplier.getUUID(),0L)==4,"Full inventory lost reserved payment");
            supplier.getInventory().setItem(9,ItemStack.EMPTY);
            helper.assertTrue(PlayerContracts.collect(level,supplier)==4 && PlayerContracts.collect(level,supplier)==0 && supplier.getInventory().countItem(Items.EMERALD)==4,"Collection duplicated or dropped the escrow");
            PlayerContracts.post(level,a,issuer,new ItemStack(Items.BREAD),1,2);
            var cancelled=data.contracts.stream().filter(c -> c.issuer.equals(a.id)).findFirst().orElseThrow(); PlayerContracts.cancel(level,issuer,cancelled.id);
            helper.assertTrue(data.payments.getOrDefault(issuer.getUUID(),0L)==2 && data.contract(cancelled.id)==null,"Unaccepted cancellation did not refund the original publisher");
            f.close(); helper.succeed();
        });
    }
    private static void checkpoint(Fixture f,Settlement town) {
        var station=new Station(town.center.east(2),StructureRole.TRADER);
        town.stations.add(station); f.level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
    }
    private static CitizenEntity trader(Fixture f,Settlement town) {
        var trader=new CitizenEntity(WWMC.CITIZEN.get(),f.level); trader.join(town.id);
        trader.setPos(town.center.getX()+2.5,town.center.getY(),town.center.getZ()+1.5);
        town.citizens.add(trader.getUUID()); town.jobs.assign(trader.getUUID(),TradeRoutes.checkpoint(town).position());
        f.citizens.add(trader); f.level.addFreshEntity(trader); SettlementData.get(f.level).setDirty(); return trader;
    }
    @GameTest(timeoutTicks=12000)
    @EmptyTemplate
    @TestHolder(description="An accepted settlement contract is fulfilled by a real trader travelling between two warehouses. Partial capacity credits only three goods, paid escrow survives a codec round trip, plain-item and home-stock reserves hold, and completion pays once while the trader returns.")
    static void settlementContractByCaravan(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var start=helper.absolutePos(new BlockPos(26000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer a=f.player("CaravanIssuer",start.east()),b=f.player("CaravanSupplier",start.east(601));
            CitizenNavigationTests.meadow(level,start,-10,610,-10,10);
            Settlement issuer=f.town(a.getUUID(),start,"Caravan Customer"),supplier=f.town(b.getUUID(),start.east(600),"Caravan Supplier");
            checkpoint(f,issuer); checkpoint(f,supplier);
            a.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT)); a.getInventory().setItem(1,new ItemStack(Items.EMERALD,4));
            PlayerContracts.post(level,issuer,a,new ItemStack(Items.IRON_INGOT),10,4);
            var data=MultiplayerData.get(level); var order=data.contracts.stream().filter(c -> c.issuer.equals(issuer.id)).findFirst().orElseThrow();
            PlayerContracts.accept(level,supplier,b,order.id);
            TradeRoutes.link(supplier,issuer,SettlementData.get(level).settlements,8192); TradeRoutes.link(issuer,supplier,SettlementData.get(level).settlements,8192);
            f.stock(supplier).setItem(0,new ItemStack(Items.IRON_INGOT,48)); f.stock(supplier).setItem(1,new ItemStack(Items.BREAD,64));
            var named=new ItemStack(Items.IRON_INGOT,12); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Museum iron")); f.stock(supplier).setItem(2,named);
            supplier.campaign.requests.put("minecraft:iron_ingot",48);
            var probe=new TradeShipment();
            helper.assertTrue(PlayerContracts.load(level,supplier,issuer,List.of(f.stock(supplier)),List.of(),probe,false)==0,"Contract loading ignored the supplier's own stock target");
            supplier.campaign.requests.put("minecraft:iron_ingot",24);
            supplier.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",24,0));
            helper.assertTrue(PlayerContracts.load(level,supplier,issuer,List.of(f.stock(supplier)),SupplyRequests.policy(supplier,issuer,false),probe,false)==0,"Contract loading resumed an explicitly paused export");
            supplier.trading.exports.clear();
            UUID incoming=UUID.randomUUID(); issuer.campaign.incoming.put(incoming,Map.of("minecraft:iron_ingot",10));
            helper.assertTrue(PlayerContracts.load(level,supplier,issuer,List.of(f.stock(supplier)),List.of(),probe,false)==0,"Contract loading duplicated goods already on the way");
            issuer.campaign.incoming.remove(incoming);
            var destination=f.stock(issuer); for(int n=0;n<destination.getContainerSize();n++) destination.setItem(n,new ItemStack(Items.COBBLESTONE,64));
            destination.setItem(0,new ItemStack(Items.IRON_INGOT,61)); var carrier=trader(f,supplier);
            var opened=new java.util.concurrent.atomic.AtomicBoolean();
            helper.succeedWhen(() -> {
                if(order.delivered==3 && !opened.get()) {
                    helper.assertTrue(!data.payments.containsKey(b.getUUID()) && carrier.tradeCargoCount()==7,"Partial caravan delivery paid early or lost retained cargo");
                    var saved=MultiplayerData.CODEC.parse(JsonOps.INSTANCE,MultiplayerData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
                    helper.assertTrue(saved.contract(order.id).remaining()==7 && saved.contract(order.id).payment==4,"Partial caravan progress or escrow did not save");
                    destination.setItem(1,ItemStack.EMPTY); opened.set(true);
                }
                helper.assertTrue(opened.get(),"Caravan has not made the partial warehouse handoff: "+supplier.trading.status);
                helper.assertTrue(data.contract(order.id)==null && data.payments.getOrDefault(b.getUUID(),0L)==4,"Trader has not completed the accepted contract: "+supplier.trading.status);
                helper.assertTrue(carrier.tradeCargoCount()==0 && carrier.blockPosition().distSqr(supplier.center)<64,"Carrier has not returned with an empty shipment");
                helper.assertTrue(InventoryOps.count(SettlementService.townStorage(level,issuer),x -> x.is(Items.IRON_INGOT))==71,"Warehouse gained goods that were not delivered");
                helper.assertTrue(f.stock(supplier).getItem(0).getCount()==38 && f.stock(supplier).getItem(2).getCount()==12,"Contract consumed protected home reserves or named items");
                helper.assertTrue(PlayerContracts.collect(level,b)==4 && PlayerContracts.collect(level,b)==0,"Caravan contract paid twice");
                f.close();
            });
        });
    }
    @GameTest(timeoutTicks=12000)
    @EmptyTemplate
    @TestHolder(description="Loaded neighbouring NPC settlements arrange a saved reciprocal route without occupying their primary player checkpoint, then an existing citizen physically carries surplus iron to meet the neighbour's target. Paused towns remain disconnected and route news appears at their banner journal.")
    static void npcNeighboursTradeRealSurplus(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var start=helper.absolutePos(new BlockPos(40000,2,-10000)); var f=new Fixture(level,start);
            CitizenNavigationTests.meadow(level,start,-10,610,-10,10);
            Settlement a=f.town(UUID.randomUUID(),start,"Iron Neighbour"),b=f.town(UUID.randomUUID(),start.east(600),"Timber Neighbour");
            checkpoint(f,a); checkpoint(f,b);
            a.trading.npc=true; b.trading.npc=true; a.trading.specialty="mining"; b.trading.specialty="timber"; b.trading.paused=true;
            a.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",16,16));
            f.stock(a).setItem(0,new ItemStack(Items.IRON_INGOT,48)); f.stock(a).setItem(1,new ItemStack(Items.BREAD,64));
            helper.runAfterDelay(5,() -> {
                NeighbourTrade.update(level,a); helper.assertTrue(!TradeRoutes.agreed(a,b),"Paused NPC arranged a route"); b.trading.paused=false;
                SupplyRequests.snapshotLoaded(level,a); SupplyRequests.snapshotLoaded(level,b); NeighbourTrade.update(level,a);
                helper.assertTrue(TradeRoutes.agreed(a,b) && a.trading.partner==null && b.trading.partner==null,"Neighbour trade did not keep the primary checkpoints free");
                var saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,a).getOrThrow()).getOrThrow();
                helper.assertTrue(TradeRoutes.agreed(saved,b) && saved.campaign.requests.get("minecraft:iron_ingot")==16,"NPC route or base demand did not survive save decoding");
                var carrier=trader(f,a);
                helper.succeedWhen(() -> {
                    helper.assertTrue(InventoryOps.count(SettlementService.townStorage(level,b),x -> x.is(Items.IRON_INGOT))==16,"No physical iron delivery yet: "+a.trading.status);
                    helper.assertTrue(carrier.tradeCargoCount()==0 && carrier.blockPosition().distSqr(a.center)<64,"Neighbour's trader has not returned");
                    helper.assertTrue(f.stock(a).getItem(0).getCount()==32,"NPC trade created goods or exhausted home reserves");
                    helper.assertTrue(a.campaign.journal.stream().anyMatch(e -> e.text().contains("Arranged neighbour trade")) && b.campaign.journal.stream().anyMatch(e -> e.text().contains("Received")),"Neighbour activity did not reach the quiet journal");
                    f.close();
                });
            });
        });
    }
    @GameTest(timeoutTicks=40)
    @EmptyTemplate
    @TestHolder(description="The neighbours banner shows public needs and NPC news without exposing another player's private journal, granting claim access or letting a visitor change the host's route. Removed duels have no command or menu entry.")
    static void neighbourBoardAndClaimPrivacy(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var start=helper.absolutePos(new BlockPos(36000,2,-18000)); var f=new Fixture(level,start);
            TestPlayer a=f.player("NewsOwner",start.east()),b=f.player("NewsVisitor",start.east(601));
            Settlement home=f.town(a.getUUID(),start,"News Home"),other=f.town(b.getUUID(),start.east(600),"Private Neighbour"),npc=f.town(UUID.randomUUID(),start.east(1200),"Public Neighbour");
            npc.trading.npc=true; npc.trading.specialty="timber"; NeighbourTrade.refreshRequests(npc);
            home.campaign.log(level.getGameTime(),"Our own journal"); other.campaign.log(level.getGameTime(),"Private neighbour journal"); npc.campaign.log(level.getGameTime(),"Public trade news");
            checkpoint(f,home); checkpoint(f,other); checkpoint(f,npc);
            var view=MultiplayerViews.view(a,start); String text=view.tabs().toString();
            helper.assertTrue(text.contains("Public trade news") && text.contains("Needs:") && !text.contains("Private neighbour journal") && view.tabs().stream().noneMatch(t -> t.name().getString().equals("Duels")),"Neighbour board leaked private journal data or retained duels");
            helper.assertTrue(level.getServer().getCommands().getDispatcher().getRoot().getChild("wwmc").getChild("duel")==null,"Removed duel command remains registered");
            b.setPos(start.getX()+1.5,start.getY(),start.getZ()+1.5); MultiplayerViews.act(b,start,MultiplayerViews.ROW_ACTION,0,"act:trade-link:"+npc.id);
            helper.assertTrue(home.trading.partner==null && ClaimProtection.denied(level,b,start.west(4)) && !MultiplayerCombat.claimException(level,b,a),"Public board changed another town's trade or combat permissions");
            f.close(); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=2850)
    @EmptyTemplate
    @TestHolder(description="An outpost never starts without defending-owner consent, its assembly period and defending presence block capture, and 60 seconds of real uncontested flag presence transfers the existing miner, stock, routes and saved ownership exactly once.")
    static void agreedOutpostCapture(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(18000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer a=f.player("OutpostDefender",start.east()),b=f.player("OutpostAttacker",start.east(601));
            Settlement defender=f.town(a.getUUID(),start,"Defending Town"),challenger=f.town(b.getUUID(),start.east(600),"Challenging Town"),outpost=f.town(a.getUUID(),start.east(1200),"Copper Outpost");
            challenger.campaign.projects.add("frontier"); outpost.campaign.parent=defender.id;
            defender.campaign.extraRoutes.add(outpost.id); outpost.campaign.extraRoutes.add(defender.id);
            outpost.progress.research.add("iron_age"); challenger.progress.research.add("bronze_age");
            f.stock(outpost).setItem(0,new ItemStack(Items.RAW_COPPER,31));
            var miner=new CitizenEntity(WWMC.CITIZEN.get(),level); miner.join(outpost.id); miner.setNoAi(true);
            miner.setPos(outpost.center.getX()+2.5,outpost.center.getY(),outpost.center.getZ()+2.5); miner.bag().offer(new ItemStack(Items.STONE_PICKAXE));
            outpost.citizens.add(miner.getUUID()); f.citizens.add(miner); level.addFreshEntity(miner);
            var site=new ExpeditionData.Site(UUID.randomUUID(),outpost.center,"mine","test"); site.spawned=true; site.cleared=true; site.claimed=outpost.id;
            ExpeditionData.get(level).sites.add(site); f.sites.add(site); ExpeditionData.get(level).setDirty();
            helper.runAfterDelay(5,() -> {
                String offered=OutpostContests.challenge(level,challenger,b,outpost.id); var data=MultiplayerData.get(level);
                helper.assertTrue(data.contests.stream().anyMatch(c -> c.outpost.equals(outpost.id)),"Outpost offer failed: "+offered);
                var contest=data.contests.stream().filter(c -> c.outpost.equals(outpost.id)).findFirst().orElseThrow();
                b.setPos(outpost.center.getX()+2.5,outpost.center.getY(),outpost.center.getZ()+0.5);
                a.setPos(outpost.center.getX()+0.5,outpost.center.getY(),outpost.center.getZ()+10.5);
                OutpostContests.tick(level); helper.assertTrue(outpost.owner.equals(a.getUUID()) && contest.progress==0 && !OutpostContests.allows(level,b,a),"Unaccepted challenge changed ownership or combat access");
                OutpostContests.accept(level,b,contest.id); helper.assertTrue(!contest.accepted,"Attacker accepted its own outpost offer");
                OutpostContests.accept(level,a,contest.id);
                helper.assertTrue(contest.accepted && contest.starts==level.getGameTime()+1200 && !OutpostContests.allows(level,b,a),"Defending owner could not consent or assembly allowed early combat");
                helper.runAfterDelay(1240,() -> {
                    helper.assertTrue(contest.progress==0 && outpost.owner.equals(a.getUUID()) && OutpostContests.allows(level,b,a),"Defender presence allowed capture or active consensual combat stayed blocked");
                    helper.assertTrue(use(b,outpost.center.west(4)).isCanceled(),"Outpost battle opened its warehouse to attackers");
                    a.setPos(outpost.center.getX()+40.5,outpost.center.getY(),outpost.center.getZ()+0.5);
                    helper.runAfterDelay(1160,() -> helper.assertTrue(outpost.owner.equals(a.getUUID()) && contest.progress<1200,"Outpost was captured before 60 seconds"));
                    helper.runAfterDelay(1260,() -> {
                        helper.assertTrue(outpost.owner.equals(b.getUUID()) && challenger.id.equals(outpost.campaign.parent) && site.claimed.equals(outpost.id) && data.contest(contest.id)==null,"Agreed capture did not transfer the existing saved outpost");
                        helper.assertTrue(!defender.campaign.extraRoutes.contains(outpost.id) && challenger.campaign.extraRoutes.contains(outpost.id) && outpost.campaign.extraRoutes.equals(Set.of(challenger.id)),"Capture retained the old supply route or failed to connect the new home");
                        helper.assertTrue(outpost.citizens.contains(miner.getUUID()) && miner.town(level)==outpost && f.stock(outpost).getItem(0).getCount()==31 && miner.bag().count(Items.STONE_PICKAXE)==1,"Capture generated, lost or duplicated existing citizens and supplies");
                        helper.assertTrue(TownAccess.manages(outpost,b.getUUID()) && !TownAccess.builds(outpost,a.getUUID()) && outpost.progress.research.contains("bronze_age") && !outpost.progress.research.contains("iron_age"),"Capture retained defeated permissions or bypassed the new owner's research");
                        var saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,outpost).getOrThrow()).getOrThrow();
                        helper.assertTrue(saved.id.equals(outpost.id) && saved.owner.equals(b.getUUID()) && saved.campaign.parent.equals(challenger.id),"Captured ownership or routes did not round-trip through the world save codec");
                        f.close(); helper.succeed();
                    });
                });
            });
        });
    }
    @GameTest(timeoutTicks=30)
    @EmptyTemplate
    @TestHolder(description="Save decoding preserves accepted partial contracts and pending payouts; restart recovery migrates retired preview duels by refunding both paid accepted stakes and only a pending challenger stake, cancels offline contests, and cannot refund twice.")
    static void multiplayerRestartRecovery(DynamicTest test) {
        test.onGameTest(helper -> {
            UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID(),d=UUID.randomUUID(),issuer=UUID.randomUUID(),supplier=UUID.randomUUID();
            var order=new MultiplayerData.Contract(UUID.randomUUID(),issuer,a,"minecraft:bread",10,3,4,Optional.of(supplier),Optional.of(b));
            var duel=new MultiplayerData.LegacyDuel(UUID.randomUUID(),a,b,5,BlockPos.ZERO,true,100,6100);
            var pending=new MultiplayerData.LegacyDuel(UUID.randomUUID(),c,d,4,BlockPos.ZERO,false,0,1200);
            var contest=new MultiplayerData.Contest(UUID.randomUUID(),UUID.randomUUID(),issuer,supplier,100,6100,true,600);
            var original=new MultiplayerData(List.of(order),List.of(duel,pending),List.of(contest),Map.of(a,2L));
            var saved=MultiplayerData.CODEC.parse(JsonOps.INSTANCE,MultiplayerData.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow()).getOrThrow(); saved.recover();
            var restored=saved.contract(order.id);
            helper.assertTrue(restored!=null && restored.remaining()==7 && restored.payment==4 && supplier.equals(restored.supplier) && b.equals(restored.recipient),"Restart discarded partial delivery, reserved payment or exclusive supplier");
            helper.assertTrue(saved.retiredDuels.isEmpty() && saved.contests.isEmpty() && saved.payments.getOrDefault(a,0L)==7 && saved.payments.getOrDefault(b,0L)==5 && saved.payments.getOrDefault(c,0L)==4 && saved.payments.getOrDefault(d,0L)==0,"Restart awarded a forfeit, lost a paid stake, or invented an unpaid stake");
            saved.recover(); helper.assertTrue(saved.payments.get(a)==7 && saved.payments.get(b)==5 && saved.payments.get(c)==4,"Repeated recovery duplicated refunds"); helper.succeed();
        });
    }
}
