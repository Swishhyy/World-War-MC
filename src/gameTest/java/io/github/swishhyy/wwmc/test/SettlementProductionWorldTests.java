package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.BronzeAnvilBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.*;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Actual workers, native recipes, paid work, save migration and settlement controls on a running server. */
public final class SettlementProductionWorldTests {
    private record Fixture(ServerLevel level,BlockPos start,List<ChunkPos> chunks,Settlement town) {
        CitizenEntity worker(Station station,boolean ai) {
            var worker=new CitizenEntity(WWMC.CITIZEN.get(),level); worker.join(town.id); worker.setNoAi(!ai);
            worker.setPos(station.position().getX()+.5,station.position().getY(),station.position().getZ()+1.5);
            worker.bag().offer(new ItemStack(Items.BREAD,4)); town.citizens.add(worker.getUUID());
            town.jobs.assign(worker.getUUID(),station.position()); level.addFreshEntity(worker); return worker;
        }
        void close() {
            for(var id:town.citizens) if(level.getEntity(id)!=null) level.getEntity(id).discard();
            SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
            CitizenNavigationTests.releaseTicking(level,start,chunks);
        }
    }
    private static Fixture fixture(ServerLevel level,BlockPos start,Station... stations) {
        var chunks=CitizenNavigationTests.pinTicking(level,start,2); CitizenNavigationTests.meadow(level,start,-8,36,-12,12);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Settlement production",start,96,List.of(),List.of(stations),"balanced");
        SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
        for(var station:stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
        return new Fixture(level,start,chunks,town);
    }
    private static Container barrel(ServerLevel level,BlockPos pos,ItemStack... contents) {
        level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState()); var box=(Container)level.getBlockEntity(pos);
        for(int i=0;i<contents.length;i++) box.setItem(i,contents[i]); return box;
    }
    private static int count(Container box,CitizenEntity worker,Item item) {
        return InventoryOps.count(List.of(box,worker.bag()),s -> s.is(item));
    }

    @GameTest(timeoutTicks=800) @EmptyTemplate
    @TestHolder(description="A real researcher pays for a scroll once, resumes saved paid work, holds it while the warehouse is full, then delivers it even after its production target is lowered to zero.")
    static void scrollWorkSurvivesSavingAndFullStorage(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-15000));
            var warehouse=new Station(start.east(6),StructureRole.WAREHOUSE); var research=new Station(start.east(20),StructureRole.RESEARCHER);
            var f=fixture(level,start,warehouse,research); BlockPos desk=research.position().north(2);
            level.setBlockAndUpdate(desk,Blocks.LECTERN.defaultBlockState());
            Container stock=barrel(level,warehouse.position().south(2),new ItemStack(Items.PAPER,2),new ItemStack(Items.CHARCOAL));
            f.town.progress.scrollTarget=1; var worker=f.worker(research,true);
            helper.runAtTickTime(100,() -> {
                helper.assertTrue(f.town.progress.scrollPaid && f.town.progress.scrollTicks>0,"Researcher did not begin a real paid scroll: "+worker.activity());
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> s.is(Items.PAPER) || s.is(Items.CHARCOAL))==0,"Scroll supplies were not paid exactly once");
                worker.setNoAi(true);
                f.town.progress=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,f.town).getOrThrow()).getOrThrow().progress;
                helper.assertTrue(f.town.progress.scrollPaid && f.town.progress.scrollTicks>0,"Reload lost the paid scroll");
                f.town.progress.scrollTicks=Research.SCROLL_TICKS-10; f.town.progress.scrollTarget=0;
                for(int i=0;i<stock.getContainerSize();i++) stock.setItem(i,new ItemStack(Items.COBBLESTONE,64));
                worker.setNoAi(false);
            });
            helper.runAtTickTime(160,() -> {
                helper.assertTrue(f.town.progress.scrollPaid && f.town.progress.scrollTicks==Research.SCROLL_TICKS-10
                        && Research.status(level,f.town).detail().contains("full"),"A full warehouse lost the paid scroll or hid its pause reason");
                stock.setItem(stock.getContainerSize()-1,ItemStack.EMPTY);
            });
            helper.runAtTickTime(260,() -> {
                helper.assertTrue(Research.scrolls(level,f.town)==1 && !f.town.progress.scrollPaid && f.town.progress.scrollTarget==0,
                        "The paid scroll did not finish once with its target lowered: "+worker.activity());
                helper.assertTrue(worker.blockPosition().distSqr(desk)<16,"Scroll production happened remotely");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=2800) @EmptyTemplate
    @TestHolder(description="A real early blacksmith repairs first and forges one bronze pickaxe at the slower bronze-anvil speed, preserving the original gear and consuming actual alloy ingots and fuel.")
    static void blacksmithRepairsThenForgesEquipment(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-15400));
            var smith=new Station(start.east(20),StructureRole.BLACKSMITH); var f=fixture(level,start,smith);
            f.town.progress.research.add("bronze_age"); BlockPos anvil=smith.position().north(2);
            level.setBlockAndUpdate(anvil,WWMC.BRONZE_ANVIL.get().defaultBlockState()); level.setBlockAndUpdate(smith.position().east(2),Blocks.FURNACE.defaultBlockState());
            var repair=new ItemStack(WWMC.BRONZE_HOE.get()); repair.setDamageValue(24); repair.set(DataComponents.CUSTOM_NAME,Component.literal("Old hoe"));
            Container local=barrel(level,smith.position().south(2),repair,new ItemStack(WWMC.BRONZE_INGOT.get(),8),new ItemStack(Items.STICK,2),
                    new ItemStack(Items.COPPER_INGOT,3),new ItemStack(WWMC.TIN_INGOT.get()),new ItemStack(Items.COAL,4));
            helper.assertTrue(ForgeWorkshop.order(level,f.town,"wwmc:bronze_pickaxe",1) && !ForgeWorkshop.order(level,f.town,"wwmc:bronze_ingot",8),"Forge catalogue could not accept orders");
            var worker=f.worker(smith,true);
            helper.succeedWhen(() -> {
                helper.assertTrue(InventoryOps.count(List.of(local),s -> s.is(WWMC.BRONZE_PICKAXE.get()))==1,"The blacksmith has not delivered its pickaxe: "+worker.activity());
                helper.assertTrue(InventoryOps.count(List.of(local),s -> s.is(WWMC.BRONZE_HOE.get()) && s.getDamageValue()==0
                        && s.get(DataComponents.CUSTOM_NAME).getString().equals("Old hoe"))==1,"Repair replaced or lost the original hoe");
                helper.assertTrue(count(local,worker,WWMC.BRONZE_INGOT.get())==4 && count(local,worker,Items.COPPER_INGOT)==3
                        && count(local,worker,WWMC.TIN_INGOT.get())==1 && count(local,worker,Items.COAL)==3,"Equipment forging changed unused alloy materials or charged the wrong costs");
                helper.assertTrue(InventoryOps.count(List.of(local),s -> s.is(WWMC.BRONZE_PICKAXE.get()))==1,"Pickaxe was not physically delivered");
                // Finished metallurgy output exceeds the active equipment reserve and can be hauled away.
                var pickups=JobStorage.collectable(JobStorage.Supplies.of(level),f.town,StructureRole.BLACKSMITH,List.of(local));
                helper.assertTrue(pickups.stream().anyMatch(p -> p.container().getItem(p.slot()).is(WWMC.BRONZE_INGOT.get())),"Blacksmith hoarded all metallurgy output from couriers");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=1600) @EmptyTemplate
    @TestHolder(description="Two equally supplied newcomer blacksmiths forge real pickaxes: iron finishes first, bronze cannot finish at iron speed, and both eventually deliver the same equipment for the same ingredient and fuel costs.")
    static void bronzeAnvilSlowsActualBlacksmithWork(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var fixtures=new ArrayList<Fixture>();
            var stock=new ArrayList<Container>(); var workers=new ArrayList<CitizenEntity>();
            for(int i=0;i<2;i++) {
                BlockPos start=helper.absolutePos(new BlockPos(0,2,-18600-i*400));
                var station=new Station(start.east(20),StructureRole.BLACKSMITH); var f=fixture(level,start,station); fixtures.add(f);
                f.town.progress.research.add("bronze_age");
                level.setBlockAndUpdate(station.position().north(2),i==0 ? Blocks.ANVIL.defaultBlockState() : WWMC.BRONZE_ANVIL.get().defaultBlockState());
                level.setBlockAndUpdate(station.position().east(2),Blocks.FURNACE.defaultBlockState());
                stock.add(barrel(level,station.position().south(2),new ItemStack(WWMC.BRONZE_INGOT.get(),3),new ItemStack(Items.STICK,2),new ItemStack(Items.COAL)));
                helper.assertTrue(ForgeWorkshop.order(level,f.town,"wwmc:bronze_pickaxe",1),"Equipment order was rejected");
                workers.add(f.worker(station,true));
            }
            boolean[] checked={false};
            helper.runAtTickTime(400,() -> {
                helper.assertTrue(count(stock.get(0),workers.get(0),WWMC.BRONZE_PICKAXE.get())==1,"Iron anvil lost its normal work speed: "+workers.get(0).activity());
                helper.assertTrue(count(stock.get(1),workers.get(1),WWMC.BRONZE_PICKAXE.get())==0,"Bronze forging completed before the slower work time");
                checked[0]=true;
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(checked[0],"Waiting for the speed comparison");
                for(int i=0;i<2;i++) {
                    helper.assertTrue(InventoryOps.count(List.of(stock.get(i)),s -> s.is(WWMC.BRONZE_PICKAXE.get()))==1,"Blacksmith has not delivered its real pickaxe: "+workers.get(i).activity());
                    helper.assertTrue(count(stock.get(i),workers.get(i),WWMC.BRONZE_INGOT.get())==0 && count(stock.get(i),workers.get(i),Items.STICK)==0
                            && count(stock.get(i),workers.get(i),Items.COAL)==0,"Slower work changed the batch costs");
                }
                fixtures.forEach(Fixture::close);
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Saved bronze anvil wear survives relocation, normal wear rolls match iron through all three stages, and native netherite upgrading preserves the original equipment components.")
    static void anvilWearAndNativeUpgradeKeepState(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-15800)); var f=fixture(level,start);
            BlockPos anvil=start.east(4); level.setBlockAndUpdate(anvil,WWMC.BRONZE_ANVIL.get().defaultBlockState().setValue(BronzeAnvilBlock.WEAR,7));
            var drops=Block.getDrops(level.getBlockState(anvil),level,anvil,null,null,new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops.size()==1 && drops.getFirst().is(WWMC.BRONZE_ANVIL_ITEM.get())
                    && drops.getFirst().get(DataComponents.BLOCK_STATE).properties().get("wear").equals("7"),"Moving an anvil refreshed its durability");
            ForgeWorkshop.wear(level,anvil,RandomSource.create(0));
            helper.assertTrue(level.getBlockState(anvil).getValue(BronzeAnvilBlock.WEAR)==7,"A non-wear roll damaged a saved bronze anvil");
            long wearSeed=0; while(RandomSource.create(wearSeed).nextFloat()>=0.12F) wearSeed++;
            ForgeWorkshop.wear(level,anvil,RandomSource.create(wearSeed));
            helper.assertTrue(level.getBlockState(anvil).getValue(BronzeAnvilBlock.WEAR)==8,"Saved chipped wear did not advance to damaged");
            ForgeWorkshop.wear(level,anvil,RandomSource.create(wearSeed));
            helper.assertTrue(level.getBlockState(anvil).isAir(),"Damaged bronze anvil did not break on a wear roll");
            BlockPos iron=anvil.east(2);
            // Normal wear may break before or after twelve jobs; both outcomes must match iron.
            for(long seed:new long[]{0,42}) {
                level.setBlockAndUpdate(anvil,WWMC.BRONZE_ANVIL.get().defaultBlockState());
                level.setBlockAndUpdate(iron,Blocks.ANVIL.defaultBlockState());
                var bronzeRandom=RandomSource.create(seed); var ironRandom=RandomSource.create(seed); int operations=0;
                do {
                    ForgeWorkshop.wear(level,anvil,bronzeRandom); ForgeWorkshop.wear(level,iron,ironRandom); operations++;
                    var bronzeState=level.getBlockState(anvil); var ironState=level.getBlockState(iron);
                    helper.assertTrue(bronzeState.isAir()==ironState.isAir(),"Bronze and iron broke on different wear rolls");
                    if(!bronzeState.isAir()) {
                        int ironStage=ironState.is(Blocks.ANVIL) ? 0 : ironState.is(Blocks.CHIPPED_ANVIL) ? 1 : 2;
                        helper.assertTrue(bronzeState.getValue(BronzeAnvilBlock.WEAR)/4==ironStage,"Bronze and iron have different wear stages");
                    }
                } while(!level.getBlockState(anvil).isAir() && operations<200);
                helper.assertTrue(level.getBlockState(anvil).isAir() && (seed==0 ? operations>12 : operations<12),"Bronze still has a fixed twelve-operation lifetime");
            }
            var order=new Workshop.Order("minecraft:netherite_pickaxe",1); var plans=ForgeWorkshop.plans(level,order);
            helper.assertTrue(plans.size()==1 && plans.getFirst().upgrade()!=null,"Native netherite recipe is unavailable to the blacksmith");
            var original=new ItemStack(Items.DIAMOND_PICKAXE); original.setDamageValue(42); original.set(DataComponents.CUSTOM_NAME,Component.literal("Family pick"));
            var worker=new CitizenEntity(WWMC.CITIZEN.get(),level); worker.bag().offer(original); worker.bag().offer(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            worker.bag().offer(new ItemStack(Items.NETHERITE_INGOT)); worker.bag().offer(new ItemStack(Items.CHARCOAL));
            helper.assertTrue(ForgeWorkshop.craft(level,plans.getFirst(),worker.bag()),"Paid native upgrade failed");
            var made=worker.bag().first(s -> s.is(Items.NETHERITE_PICKAXE));
            helper.assertTrue(!made.isEmpty() && made.getDamageValue()==42 && made.get(DataComponents.CUSTOM_NAME).getString().equals("Family pick"),"Upgrading erased original equipment components");
            helper.assertTrue(worker.bag().count(Items.DIAMOND_PICKAXE)==0 && worker.bag().count(Items.NETHERITE_INGOT)==0
                    && worker.bag().count(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)==0 && worker.bag().count(Items.CHARCOAL)==0,"Upgrade ingredients were duplicated or charged incorrectly");
            f.close(); helper.succeed();
        });
    }

    @GameTest(timeoutTicks=1100) @EmptyTemplate
    @TestHolder(description="A gatherer with a real shovel harvests cane tops and clay, leaves the renewable cane base, and refuses protected gravel construction.")
    static void gathererKeepsPlantsAndConstruction(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-16200));
            var gatherer=new Station(start.east(20),StructureRole.GATHERER); var f=fixture(level,start,gatherer);
            BlockPos root=gatherer.position().east(2),clay=gatherer.position().south(2),protectedGravel=gatherer.position().north(2);
            level.setBlockAndUpdate(root.below(),Blocks.SAND.defaultBlockState()); level.setBlockAndUpdate(root.east().below(),Blocks.WATER.defaultBlockState());
            for(int y=0;y<3;y++) level.setBlockAndUpdate(root.above(y),Blocks.SUGAR_CANE.defaultBlockState());
            level.setBlockAndUpdate(clay,Blocks.CLAY.defaultBlockState()); level.setBlockAndUpdate(protectedGravel,Blocks.GRAVEL.defaultBlockState());
            WorldWorkData.get(level).protect(protectedGravel);
            Container local=barrel(level,gatherer.position().west(2),new ItemStack(Items.STONE_SHOVEL)); var worker=f.worker(gatherer,true);
            helper.succeedWhen(() -> {
                helper.assertTrue(count(local,worker,Items.SUGAR_CANE)>=1 && count(local,worker,Items.CLAY_BALL)==4,"Gatherer has not produced real cane and clay: "+worker.activity());
                helper.assertTrue(level.getBlockState(root).is(Blocks.SUGAR_CANE),"Gatherer removed the cane base");
                helper.assertTrue(level.getBlockState(protectedGravel).is(Blocks.GRAVEL),"Gatherer mined player construction");
                helper.assertTrue(worker.getMainHandItem().is(Items.STONE_SHOVEL) && worker.getMainHandItem().getDamageValue()>0,"Gathering did not use and wear its actual shovel");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=50) @EmptyTemplate
    @TestHolder(description="A powered vanilla crafter cannot produce an iron sword or spend its inputs, while ordinary paper crafting stays available.")
    static void redstoneCrafterCannotBypassForge(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-17800)); var f=fixture(level,start);
            BlockPos pos=start.east(8); level.setBlockAndUpdate(pos,Blocks.CRAFTER.defaultBlockState());
            Container crafter=(Container)level.getBlockEntity(pos);
            crafter.setItem(1,new ItemStack(Items.IRON_INGOT)); crafter.setItem(4,new ItemStack(Items.IRON_INGOT)); crafter.setItem(7,new ItemStack(Items.STICK));
            level.setBlockAndUpdate(pos.above(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(12,() -> {
                helper.assertTrue(crafter.getItem(1).getCount()==1 && crafter.getItem(4).getCount()==1 && crafter.getItem(7).getCount()==1,"Redstone crafter spent blocked forge ingredients");
                helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(6),
                        e -> e.getItem().is(Items.IRON_SWORD)).isEmpty(),"Redstone crafter produced forged equipment");
                var paper=net.minecraft.world.item.crafting.CraftingInput.of(3,1,List.of(new ItemStack(Items.SUGAR_CANE),new ItemStack(Items.SUGAR_CANE),new ItemStack(Items.SUGAR_CANE)));
                helper.assertTrue(net.minecraft.world.level.block.CrafterBlock.getPotentialResults(level,paper).isPresent(),"Forge restriction disabled unrelated crafting");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="New research requires real scrolls and staff, Bronze Age needs a functioning population, and Iron Age needs a staffed forge without creating a circular iron-anvil prerequisite.")
    static void researchNeedsScrollsAndSettlementInfrastructure(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-16600));
            var warehouse=new Station(start.east(6),StructureRole.WAREHOUSE); var research=new Station(start.east(20),StructureRole.RESEARCHER);
            var smith=new Station(start.offset(20,0,8),StructureRole.BLACKSMITH); var f=fixture(level,start,warehouse,research,smith);
            level.setBlockAndUpdate(research.position().north(2),Blocks.LECTERN.defaultBlockState());
            var stock=barrel(level,warehouse.position().south(2),new ItemStack(Items.COPPER_INGOT,24),new ItemStack(WWMC.TIN_INGOT.get(),8),new ItemStack(Items.COAL,24));
            f.worker(research,false);
            // Entity registration completes on the server's following tick, just as it does during normal recruitment.
            helper.runAtTickTime(5,() -> {
                String missing=Research.missing(level,f.town,Research.byId("bronze_age"));
                helper.assertTrue(missing.contains("3 citizens"),"Bronze Age has no population requirement: "+missing);
                f.worker(smith,false); f.worker(research,false);
            });
            helper.runAtTickTime(10,() -> {
                helper.assertTrue(Research.study(level,f.town,"bronze_age").contains("scrolls") && stock.getItem(0).getCount()==24,"Missing scrolls consumed research supplies");
                stock.setItem(3,new ItemStack(WWMC.RESEARCH_SCROLL.get(),18)); helper.assertTrue(Research.study(level,f.town,"bronze_age").contains("researched"),"Earned scrolls did not unlock Bronze Age");
                stock.setItem(4,new ItemStack(WWMC.BRONZE_INGOT.get(),16)); stock.setItem(5,new ItemStack(Items.IRON_INGOT,16));
                helper.assertTrue(Research.missing(level,f.town,Research.byId("iron_age")).contains("blacksmith"),"Iron Age ignored infrastructure");
                level.setBlockAndUpdate(smith.position().east(2),WWMC.BRONZE_ANVIL.get().defaultBlockState());
                level.setBlockAndUpdate(smith.position().west(2),Blocks.FURNACE.defaultBlockState());
            });
            helper.runAtTickTime(40,() -> {
                // Station furniture positions are shared for one second; allow the empty anvil scan to refresh.
                String result=Research.study(level,f.town,"iron_age");
                helper.assertTrue(result.contains("researched") && Research.has(f.town,"iron_age"),"Bronze forge could not bootstrap the Iron Age: "+result);
                helper.assertTrue(Research.scrolls(level,f.town)==0 && Research.study(level,f.town,"iron_age").startsWith("Already"),"Research duplicated scroll costs");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=5200) @EmptyTemplate
    @TestHolder(description="A real smelter makes glass, bricks and charcoal in native furnaces without burning its charcoal ingredients, and a courier delivers the finished goods and surplus charcoal to the warehouse.")
    static void buildingMaterialsReachTheWarehouse(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-18200));
            var warehouse=new Station(start.east(6),StructureRole.WAREHOUSE);
            var smelter=new Station(start.east(20),StructureRole.SMELTERY);
            var courier=new Station(start.east(34),StructureRole.COURIER);
            var f=fixture(level,start,warehouse,smelter,courier);
            Container stock=barrel(level,warehouse.position().south(2));
            Container local=barrel(level,smelter.position().south(2),new ItemStack(Items.SAND,4),new ItemStack(Items.CLAY_BALL,4),
                    new ItemStack(Items.OAK_LOG,16),new ItemStack(Items.COAL,12));
            var containers=new ArrayList<Container>(List.of(stock,local));
            for(BlockPos device:List.of(smelter.position().north(2),smelter.position().east(2),smelter.position().west(2))) {
                level.setBlockAndUpdate(device,Blocks.FURNACE.defaultBlockState()); containers.add((Container)level.getBlockEntity(device));
            }
            var worker=f.worker(smelter,true); var hauler=f.worker(courier,true);
            containers.add(worker.bag()); containers.add(hauler.bag());
            helper.succeedWhen(() -> {
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> s.is(Items.GLASS))==4
                        && InventoryOps.count(List.of(stock),s -> s.is(Items.BRICK))==4
                        && InventoryOps.count(List.of(stock),s -> s.is(Items.CHARCOAL))>=4,
                        "Building materials have not reached the warehouse: "+worker.activity()+"; "+hauler.activity());
                helper.assertTrue(InventoryOps.count(containers,s -> s.is(Items.CHARCOAL))==16
                        && InventoryOps.count(containers,s -> s.is(Items.OAK_LOG) || s.is(Items.SAND) || s.is(Items.CLAY_BALL))==0,
                        "The smelter burned ingredients or invented processing output");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="The banner has four main destinations; production orders are saved, consume no resources at the GUI, and reject builders without management access.")
    static void focusedPanelsKeepOrdersAndPermissions(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-17000)); var f=fixture(level,start);
            var owner=new FakePlayer(level,new GameProfile(f.town.owner,"ProductionOwner")); owner.setPos(start.getX()+1.5,start.getY(),start.getZ()+.5);
            var view=Panels.town(level,f.town,owner);
            helper.assertTrue(view.actions().size()==4 && view.actions().stream().map(a -> a.label().getString()).toList().equals(List.of("People","Production","Research","Neighbours")),"Banner is still overloaded with top-level actions");
            var production=new PanelMenu(1,PanelMenu.Kind.PRODUCTION,start,owner,TownViews.build(level,f.town,owner,PanelMenu.Kind.PRODUCTION));
            production.act(owner,TownViews.ROW_ACTION,0,2,"forge:wwmc:bronze_pickaxe");
            production.act(owner,TownViews.ROW_ACTION,0,3,"craft:wwmc:housing_station");
            helper.assertTrue(f.town.progress.forgeOrders.size()==1 && Workshop.find(f.town,"wwmc:housing_station")>=0,"Production needs an example item to place a stock order");
            var loaded=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,f.town).getOrThrow()).getOrThrow();
            helper.assertTrue(loaded.progress.forgeOrders.getFirst().target()==2 && loaded.craftOrders.get(Workshop.find(loaded,"wwmc:housing_station")).target()==3,"Production orders lost their target after saving");
            helper.assertTrue(TownViews.build(level,f.town,owner,PanelMenu.Kind.PRODUCTION).tabs().stream().flatMap(t -> t.rows().stream())
                    .anyMatch(r -> r.key().equals("forge:wwmc:bronze_pickaxe") && r.detail().getString().startsWith("Target 2")),"Production hides the selected target");
            var builder=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ProductionBuilder")); builder.setPos(owner.position()); f.town.campaign.members.put(builder.getUUID(),"builder");
            production.act(builder,TownViews.ROW_ACTION,0,200,"forge:wwmc:bronze_pickaxe");
            helper.assertTrue(f.town.progress.forgeOrders.getFirst().target()==2 && InventoryOps.count(List.of(owner.getInventory()),s -> s.is(WWMC.BRONZE_PICKAXE.get()))==0,"Unauthorized management or GUI clicking produced free gear");
            f.close(); helper.succeed();
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Ruined town halls generate actual settlement furniture, finite scroll salvage and no automatic settlement claim; rediscovery never rebuilds or duplicates the loot.")
    static void ruinedTownhallShowsAndSalvagesSettlementBlocks(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-17400));
            var chunks=CitizenNavigationTests.pinArea(level,start,-12,12,-12,12); CitizenNavigationTests.meadow(level,start,-12,12,-12,12);
            var site=ExpeditionService.discover(level,start,"townhall","production-test-townhall");
            helper.assertTrue(site!=null && site.title().equals("Ruined Town Hall"),"Town hall site did not generate safely");
            helper.assertTrue(level.getBlockState(site.pos.north(3)).is(WWMC.BANNER.get())
                    && level.getBlockState(site.pos.offset(5,0,-4)).is(WWMC.BRONZE_ANVIL.get())
                    && level.getBlockState(site.pos.offset(4,0,-4)).is(WWMC.STATIONS.get(StructureRole.BLACKSMITH).get()),"Town hall does not show usable settlement blocks");
            Container cache=(Container)level.getBlockEntity(site.pos.east(3));
            helper.assertTrue(InventoryOps.count(List.of(cache),s -> s.is(WWMC.RESEARCH_SCROLL.get()))==2 && SettlementData.get(level).at(site.pos)==null,"Salvage is wrong or ruin automatically claimed a settlement");
            cache.setItem(5,ItemStack.EMPTY); helper.assertTrue(ExpeditionService.discover(level,start,"townhall","production-test-townhall")==null
                    && cache.getItem(5).isEmpty(),"Rediscovering the town hall regenerated loot");
            ExpeditionData.get(level).sites.remove(site); ExpeditionData.get(level).setDirty(); CitizenNavigationTests.release(level,start,chunks); helper.succeed();
        });
    }
}
