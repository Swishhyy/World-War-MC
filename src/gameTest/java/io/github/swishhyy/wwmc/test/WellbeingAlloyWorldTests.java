package io.github.swishhyy.wwmc.test;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.AlloyFurnaceEntity;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.*;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Loaded world behavior, real meals, native child aging, saved alloy inventory and automated supply chains. */
public final class WellbeingAlloyWorldTests {
    private record Fixture(ServerLevel level,BlockPos start,List<ChunkPos> chunks,Settlement town) {
        CitizenEntity citizen(BlockPos pos,Station job,boolean ai) {
            var c=new CitizenEntity(WWMC.CITIZEN.get(),level); c.join(town.id); c.setNoAi(!ai);
            c.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); c.bag().offer(new ItemStack(Items.BREAD,4));
            town.citizens.add(c.getUUID()); if(job!=null) town.jobs.assign(c.getUUID(),job.position()); level.addFreshEntity(c); return c;
        }
        void close() {
            for(UUID id:town.citizens) if(level.getEntity(id)!=null) level.getEntity(id).discard();
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks);
        }
    }
    private static Fixture fixture(ServerLevel level,BlockPos start,Station... stations) {
        var chunks=CitizenNavigationTests.pinTicking(level,start,2); CitizenNavigationTests.meadow(level,start,-8,42,-14,14);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Wellbeing and alloys",start,96,List.of(),List.of(stations),"balanced");
        SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
        for(Station s:stations) level.setBlockAndUpdate(s.position(),WWMC.STATIONS.get(s.role()).get().defaultBlockState());
        return new Fixture(level,start,chunks,town);
    }
    private static Container barrel(ServerLevel level,BlockPos pos,ItemStack... stacks) {
        level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState()); var box=(Container)level.getBlockEntity(pos);
        for(int i=0;i<stacks.length;i++) box.setItem(i,stacks[i]); return box;
    }
    private static BlockPos bed(ServerLevel level,BlockPos foot) {
        level.setBlockAndUpdate(foot,Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,BedPart.FOOT));
        BlockPos head=foot.north(); level.setBlockAndUpdate(head,Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,BedPart.HEAD)); return head;
    }
    private static CompoundTag save(CitizenEntity c) {
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,c.level().registryAccess()); c.saveWithoutId(out); return out.buildResult();
    }
    private static void load(CitizenEntity c,CompoundTag tag) { c.load(TagValueInput.create(ProblemReporter.DISCARDING,c.level().registryAccess(),tag)); }
    private static void diet(CitizenEntity c,String... meals) {
        CompoundTag tag=save(c); tag.put("wwmc_recent_meals",com.mojang.serialization.Codec.STRING.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,List.of(meals)).getOrThrow()); load(c,tag);
    }
    private static void step(ServerLevel level,BlockPos pos,AlloyFurnaceEntity furnace,int ticks) {
        for(int i=0;i<ticks;i++) AlloyFurnaceEntity.tick(level,pos,level.getBlockState(pos),furnace);
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Citizens choose actual varied food with bowl returns; housing and unique amenities lift gradual happiness, while hunger and alarms lower it. Saved happiness and meal history survive.")
    static void mealsAmenitiesAndSavedHappiness(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-23000));
            Station housing=new Station(start.east(12),StructureRole.HOUSING); var f=fixture(level,start,housing);
            bed(level,housing.position().north(2)); var c=f.citizen(housing.position().south(),null,false);
            var food=new SimpleContainer(new ItemStack(Items.BREAD,8),new ItemStack(Items.COOKED_BEEF,8),new ItemStack(Items.MUSHROOM_STEW));
            List<String> history=new ArrayList<>(); List<ItemStack> returns=new ArrayList<>();
            for(int i=0;i<3;i++) { ItemStack meal=FoodHealing.take(List.of(food),returns::add,history); history=MealVariety.remember(history,MealVariety.id(meal)); }
            helper.assertTrue(MealVariety.distinct(history)==3 && food.getItem(0).getCount()==7 && food.getItem(1).getCount()==7 && food.getItem(2).isEmpty(),"Available meal types were not rotated or actual meals were not charged");
            helper.assertTrue(returns.size()==1 && returns.getFirst().is(Items.BOWL),"Variety selection lost a stew bowl");
            diet(c,history.toArray(String[]::new)); c.setHappiness(50);
            level.setBlockAndUpdate(housing.position().east(2),Blocks.BOOKSHELF.defaultBlockState());
            level.setBlockAndUpdate(housing.position().west(2),Blocks.POPPY.defaultBlockState());
            level.setBlockAndUpdate(housing.position().west(3),Blocks.DANDELION.defaultBlockState());
            var conditions=CitizenWellbeing.conditions(level,f.town); var outlook=CitizenWellbeing.outlook(c,conditions);
            helper.assertTrue(conditions.amenities().size()==2 && outlook.target()==85,"Duplicate flowers multiplied amenities or healthy varied housing has the wrong happiness target");
            // Entity manager registration completes after the current world tick.
            helper.runAtTickTime(5,() -> {
            helper.assertTrue(CitizenWellbeing.loaded(level,f.town).size()==1,"Citizen was not registered in the loaded town"); c.setHappiness(50);
            CitizenWellbeing.tick(level,f.town); helper.assertTrue(c.happiness()==52,"Happiness should change gradually");
            var restored=new CitizenEntity(WWMC.CITIZEN.get(),level); load(restored,save(c));
            helper.assertTrue(restored.happiness()==52 && restored.recentMeals().equals(c.recentMeals()),"Citizen wellbeing was lost on native save/load");
            var tag=save(c); tag.putInt("wwmc_meal_ticks",0); load(c,tag);
            helper.assertTrue(CitizenWellbeing.outlook(c,new CitizenWellbeing.Conditions(1,1,conditions.amenities(),true)).target()==40,"Hunger and danger did not lower happiness");
            level.removeBlock(housing.position().east(2),false);
            helper.assertTrue(!CitizenWellbeing.conditions(level,f.town).amenities().contains("Books"),"Cached destroyed amenity still increased happiness");
            f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="A loaded-minute birth consumes real spare food, respects disabled growth, caps, beds, happy adults and parent cooldowns; persisted attempts prevent restart rerolls and children reserve population places.")
    static void safeFoodFundedBirthAndSave(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-23400));
            Station housing=new Station(start.east(12),StructureRole.HOUSING),warehouse=new Station(start.east(30),StructureRole.WAREHOUSE);
            var f=fixture(level,start,housing,warehouse);
            for(int x=-2;x<=2;x+=2) bed(level,housing.position().offset(x,0,-2));
            Container stock=barrel(level,warehouse.position().south(2),new ItemStack(Items.BREAD,32));
            var a=f.citizen(housing.position().south(2),null,true); var b=f.citizen(housing.position().south(2).east(2),null,true);
            for(var parent:List.of(a,b)) { diet(parent,"minecraft:bread","minecraft:cooked_beef","minecraft:carrot"); parent.setHappiness(80); }
            helper.runAtTickTime(5,() -> {
            helper.assertTrue(CitizenWellbeing.loaded(level,f.town).size()==2,"Parents were not registered in the loaded town");
            f.town.progress.growthEnabled=false; helper.assertTrue(!PopulationGrowth.birth(level,f.town) && stock.getItem(0).getCount()==32,"Paused growth consumed food or created a child");
            f.town.progress.growthEnabled=true; a.setHappiness(50); helper.assertTrue(!PopulationGrowth.birth(level,f.town),"Unhappy parent created a child"); a.setHappiness(80);
            long seed=0; while(RandomSource.create(seed).nextInt(100)>=5) seed++;
            PopulationGrowth.tick(level,f.town,RandomSource.create(seed)); f.town.progress.birthWaitTicks=0; PopulationGrowth.tick(level,f.town,RandomSource.create(seed));
            helper.assertTrue(f.town.citizens.size()==3 && f.town.progress.children.size()==1 && stock.getItem(0).getCount()==26,"Scheduled successful birth did not consume exactly six meals or register one child: "+PopulationGrowth.pause(level,f.town));
            });
            helper.runAtTickTime(7,() -> {
            var baby=(CitizenEntity)level.getEntity(f.town.progress.children.iterator().next());
            helper.assertTrue(baby!=null && baby.isBaby() && baby.getAge()>=-PopulationGrowth.CHILD_TICKS && baby.getAge()<=-PopulationGrowth.CHILD_TICKS+3 && baby.homeBed()!=null
                    && a.getAge()>=PopulationGrowth.PARENT_COOLDOWN-3 && b.getAge()>=PopulationGrowth.PARENT_COOLDOWN-3,"Birth skipped real childhood, housing or cooldowns");
            var loaded=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,f.town).getOrThrow()).getOrThrow();
            helper.assertTrue(loaded.progress.children.equals(f.town.progress.children) && loaded.progress.birthWaitTicks==f.town.progress.birthWaitTicks,"Growth state was lost on settlement save/load");
            PopulationGrowth.tick(level,f.town,RandomSource.create(0)); helper.assertTrue(f.town.citizens.size()==3 && stock.getItem(0).getCount()==26,"Same-minute reload rerolled a birth");
            a.setAge(0); b.setAge(0); helper.assertTrue(!PopulationGrowth.birth(level,f.town),"Birth ignored the absence of a fourth housing bed");
            helper.assertTrue(TownJobs.assess(level,f.town).unassigned()==2,"Baby was counted as an available worker");
            f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=600) @EmptyTemplate
    @TestHolder(description="A native baby wanders safely near its housing, cannot fill or defend a vacant guard post, retains childhood on reload, and takes an adult job only after actual loaded aging.")
    static void childPlaysAndGrowsIntoAdultJobs(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-23800));
            Station housing=new Station(start.east(12),StructureRole.HOUSING),guard=new Station(start.east(26),StructureRole.GUARD);
            var f=fixture(level,start,housing,guard); BlockPos home=bed(level,housing.position().north(2));
            var child=f.citizen(housing.position().south(2),guard,true); child.setAge(-300); child.setHomeBed(home);
            f.town.progress.children.add(child.getUUID()); f.town.jobs.prune(f.town,s -> SettlementService.workerLimit(f.town,s));
            helper.assertTrue(!child.isGuard() && f.town.jobs.home(child.getUUID())==null && TownJobs.assess(level,f.town).unassigned()==0,"A baby filled the guard vacancy");
            helper.runAtTickTime(120,() -> {
                helper.assertTrue(child.isBaby() && child.getAge()>-300 && child.getAge()<0 && !child.isGuard() && f.town.jobs.home(child.getUUID())==null,"Baby worked or failed to age while loaded");
                helper.assertTrue(child.blockPosition().distSqr(home)<100 && child.activity().contains("Playing"),"Baby did not play near its real home: "+child.activity());
                var restored=new CitizenEntity(WWMC.CITIZEN.get(),level); load(restored,save(child));
                helper.assertTrue(restored.isBaby() && restored.getAge()==child.getAge() && restored.homeBed().equals(home),"Reload promoted a baby or lost its home");
            });
            helper.runAtTickTime(450,() -> {
                helper.assertTrue(!child.isBaby() && !f.town.progress.children.contains(child.getUUID()) && guard.position().equals(f.town.jobs.home(child.getUUID())) && child.isGuard(),"Adult transition did not release childhood and fill a genuine job: "+child.activity());
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="A real alloy furnace charges two inputs plus distinct fuel, blocks unresearchable steel and full output, resumes saved partial work, and preserves original inputs and drops on destruction.")
    static void alloysResearchInventoryAndPersistence(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-24200)); var f=fixture(level,start);
            BlockPos pos=start.east(4); level.setBlockAndUpdate(pos,WWMC.ALLOY_FURNACE.get().defaultBlockState());
            var furnace=(AlloyFurnaceEntity)level.getBlockEntity(pos);
            furnace.setItem(0,new ItemStack(Items.RAW_COPPER,3)); furnace.setItem(1,new ItemStack(WWMC.RAW_TIN.get())); furnace.setItem(2,new ItemStack(Items.COAL));
            step(level,pos,furnace,80); helper.assertTrue(furnace.getItem(3).isEmpty() && furnace.getItem(2).getCount()==1 && furnace.data.get(2)==0,"Stone Age furnace bypassed research or burned locked fuel");
            f.town.progress.research.add("bronze_age"); step(level,pos,furnace,120);
            CompoundTag saved=furnace.saveWithFullMetadata(level.registryAccess());
            var resumed=(AlloyFurnaceEntity)BlockEntity.loadStatic(pos,level.getBlockState(pos),saved,level.registryAccess()); level.setBlockEntity(resumed);
            helper.assertTrue(resumed.data.get(2)==120 && resumed.getItem(0).getCount()==3 && resumed.getItem(1).getCount()==1,"Partial alloy work did not survive native save/load");
            step(level,pos,resumed,279); helper.assertTrue(resumed.getItem(3).isEmpty(),"Saving skipped alloy work");
            step(level,pos,resumed,1); helper.assertTrue(resumed.getItem(3).is(WWMC.BRONZE_INGOT.get()) && resumed.getItem(3).getCount()==4 && resumed.getItem(0).isEmpty() && resumed.getItem(1).isEmpty(),"Bronze did not directly yield four ingots for exact raw inputs");
            resumed.clearContent(); resumed.setItem(0,new ItemStack(Items.IRON_INGOT)); resumed.setItem(1,new ItemStack(Items.CHARCOAL));
            step(level,pos,resumed,650); helper.assertTrue(resumed.getItem(3).isEmpty() && resumed.getItem(1).getCount()==1,"Iron Age or existing heat bypassed Steelworking");
            f.town.progress.research.add("iron_age"); helper.assertTrue(!AgeProgression.allowed(f.town,new ItemStack(WWMC.STEEL_PICKAXE.get())),"Iron Age alone unlocked steel equipment");
            f.town.progress.research.add("steel_working");
            resumed.setItem(3,new ItemStack(WWMC.BRONZE_INGOT.get(),64)); resumed.setItem(2,new ItemStack(Items.COAL));
            step(level,pos,resumed,650); helper.assertTrue(resumed.getItem(0).getCount()==1 && resumed.getItem(1).getCount()==1 && resumed.getItem(2).getCount()==1,"Blocked output consumed another alloy batch or fresh fuel");
            resumed.setItem(3,ItemStack.EMPTY); step(level,pos,resumed,600);
            helper.assertTrue(resumed.getItem(3).is(WWMC.STEEL_INGOT.get()) && resumed.getItem(3).getCount()==1 && resumed.getItem(0).isEmpty() && resumed.getItem(1).isEmpty() && resumed.getItem(2).isEmpty(),"Steel did not consume iron, carbon and separate fuel exactly once");
            helper.assertTrue(AgeProgression.allowed(f.town,new ItemStack(WWMC.STEEL_PICKAXE.get())) && ForgeWorkshop.plans(level,new Workshop.Order("wwmc:steel_pickaxe",1)).size()==1,"Steelworking did not unlock real forged steel equipment");
            resumed.setItem(0,new ItemStack(Items.IRON_INGOT,2)); level.destroyBlock(pos,true);
            int iron=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).mapToInt(e -> e.getItem().getCount()).sum();
            int steel=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream().filter(e -> e.getItem().is(WWMC.STEEL_INGOT.get())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(iron==2 && steel==1,"Breaking the alloy furnace lost or duplicated contents");
            f.close(); helper.succeed();
        });
    }

    @GameTest(timeoutTicks=2200) @EmptyTemplate
    @TestHolder(description="Actual couriers deliver copper, tin and fuel to a smelter, who fills and empties a real alloy furnace; the warehouse receives bronze and surplus ingots remain collectable.")
    static void courierAndSmelterAutomateBronze(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-24600));
            Station warehouse=new Station(start.east(5),StructureRole.WAREHOUSE),smelter=new Station(start.east(26),StructureRole.SMELTERY),courier=new Station(start.east(14),StructureRole.COURIER);
            var f=fixture(level,start,warehouse,smelter,courier); f.town.progress.research.add("bronze_age"); AlloyWorkshop.order(f.town,"wwmc:bronze_ingot",4);
            Container stock=barrel(level,warehouse.position().south(2),new ItemStack(Items.COPPER_INGOT,24),new ItemStack(WWMC.TIN_INGOT.get(),8),new ItemStack(Items.COAL,8));
            Container local=barrel(level,smelter.position().south(2)); BlockPos appliance=smelter.position().north(2);
            level.setBlockAndUpdate(appliance,WWMC.ALLOY_FURNACE.get().defaultBlockState());
            var worker=f.citizen(smelter.position().south(),smelter,true); var carrier=f.citizen(courier.position().south(),courier,true);
            helper.succeedWhen(() -> {
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> s.is(WWMC.BRONZE_INGOT.get()))>=4,"No real warehouse alloy delivery: smelter="+worker.activity()+", courier="+carrier.activity());
                helper.assertTrue(InventoryOps.count(List.of(stock,local,worker.bag(),carrier.bag()),s -> s.is(Items.COPPER_INGOT))==21
                        && InventoryOps.count(List.of(stock,local,worker.bag(),carrier.bag()),s -> s.is(WWMC.TIN_INGOT.get()))==7,"Automated alloys synthesized or overconsumed metal");
                local.setItem(20,new ItemStack(Items.COPPER_INGOT,64));
                helper.assertTrue(JobStorage.collectable(JobStorage.Supplies.of(level),f.town,StructureRole.SMELTERY,List.of(local)).stream()
                        .anyMatch(p -> p.container().getItem(p.slot()).is(Items.COPPER_INGOT)),"Alloy input reserves hoarded every surplus copper ingot");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=180) @EmptyTemplate
    @TestHolder(description="The real furnace menu validates and transfers inputs, carbon and fuel; native hoppers insert from top/side and extract only output without stealing unprocessed materials.")
    static void alloyMenuAndNativeHopperAutomation(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-25400)); var f=fixture(level,start);
            BlockPos pos=start.east(8); level.setBlockAndUpdate(pos,WWMC.ALLOY_FURNACE.get().defaultBlockState()); var furnace=(AlloyFurnaceEntity)level.getBlockEntity(pos);
            var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AlloyMenu"));
            var menu=new AlloyFurnaceMenu(1,player.getInventory(),furnace,furnace.data);
            player.getInventory().setItem(9,new ItemStack(Items.RAW_IRON)); player.getInventory().setItem(10,new ItemStack(Items.COAL,2));
            menu.quickMoveStack(player,4); menu.quickMoveStack(player,5);
            helper.assertTrue(furnace.getItem(0).is(Items.RAW_IRON) && furnace.getItem(1).getCount()==1 && furnace.getItem(2).getCount()==1,"Shift-click failed to separate steel carbon from fuel");
            helper.assertTrue(!menu.getSlot(3).mayPlace(new ItemStack(Items.IRON_INGOT)),"Output slot accepts ingredients");
            player.getInventory().setItem(11,new ItemStack(Items.DIAMOND_SWORD)); menu.quickMoveStack(player,6);
            helper.assertTrue(player.getInventory().getItem(11).is(Items.DIAMOND_SWORD),"Invalid gear was consumed or burned");
            furnace.clearContent();
            BlockPos top=pos.above(),side=pos.east(),bottom=pos.below();
            level.setBlockAndUpdate(top,Blocks.HOPPER.defaultBlockState()); level.setBlockAndUpdate(side,Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,Direction.WEST));
            level.setBlockAndUpdate(bottom,Blocks.HOPPER.defaultBlockState());
            var inputs=(HopperBlockEntity)level.getBlockEntity(top); var fuel=(HopperBlockEntity)level.getBlockEntity(side); var output=(HopperBlockEntity)level.getBlockEntity(bottom);
            inputs.setItem(0,new ItemStack(Items.COPPER_INGOT,3)); inputs.setItem(1,new ItemStack(WWMC.TIN_INGOT.get())); fuel.setItem(0,new ItemStack(Items.COAL,2));
            helper.runAtTickTime(90,() -> {
                helper.assertTrue(furnace.getItem(0).getCount()==3 && furnace.getItem(1).getCount()==1 && furnace.getItem(2).getCount()==2 && output.isEmpty(),"Native hopper slot rules lost materials or failed to load inputs/fuel");
                furnace.setItem(3,new ItemStack(WWMC.BRONZE_INGOT.get(),4));
            });
            helper.runAtTickTime(150,() -> {
                helper.assertTrue(output.countItem(WWMC.BRONZE_INGOT.get())==4 && furnace.getItem(0).getCount()==3 && furnace.getItem(1).getCount()==1,"Hopper extracted unfinished materials or duplicated output");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=4000) @EmptyTemplate
    @TestHolder(description="A courier and real smelter alloy steel from iron and carbon with separate fuel, then a real blacksmith forges a steel pickaxe from warehouse stock using the native recipe.")
    static void steelAlloyToForgedEquipmentChain(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-25800));
            Station warehouse=new Station(start.east(5),StructureRole.WAREHOUSE),courier=new Station(start.east(14),StructureRole.COURIER),smelter=new Station(start.east(26),StructureRole.SMELTERY),smith=new Station(start.east(39),StructureRole.BLACKSMITH);
            var f=fixture(level,start,warehouse,courier,smelter,smith); f.town.progress.research.addAll(List.of("bronze_age","iron_age","steel_working"));
            AlloyWorkshop.order(f.town,"wwmc:steel_ingot",3); AlloyWorkshop.order(f.town,"wwmc:bronze_ingot",0);
            helper.assertTrue(ForgeWorkshop.order(level,f.town,"wwmc:steel_pickaxe",1),"Real steel forge order rejected");
            // Supply one pickaxe's metal; stock targets normally replenish ingots after the smith uses them.
            Container stock=barrel(level,warehouse.position().south(2),new ItemStack(Items.IRON_INGOT,3),new ItemStack(Items.COAL,16),new ItemStack(Items.STICK,2));
            Container alloyBarrel=barrel(level,smelter.position().south(2)),forgeBarrel=barrel(level,smith.position().south(2));
            BlockPos pos=smelter.position().north(2); level.setBlockAndUpdate(pos,WWMC.ALLOY_FURNACE.get().defaultBlockState()); var furnace=(AlloyFurnaceEntity)level.getBlockEntity(pos);
            level.setBlockAndUpdate(smith.position().north(2),Blocks.ANVIL.defaultBlockState()); level.setBlockAndUpdate(smith.position().east(2),Blocks.FURNACE.defaultBlockState());
            var maker=f.citizen(smelter.position().south(),smelter,true); var carrier=f.citizen(courier.position().south(),courier,true); var forger=f.citizen(smith.position().south(),smith,true);
            helper.succeedWhen(() -> {
                var all=List.of(stock,alloyBarrel,forgeBarrel,maker.bag(),carrier.bag(),forger.bag(),furnace);
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> s.is(WWMC.STEEL_PICKAXE.get()))==1,"Steel chain unfinished: smelter="+maker.activity()+", courier="+carrier.activity()+", blacksmith="+forger.activity()
                        +"; iron="+InventoryOps.count(all,s -> s.is(Items.IRON_INGOT))+", steel="+InventoryOps.count(all,s -> s.is(WWMC.STEEL_INGOT.get()))+", coal="+InventoryOps.count(all,s -> s.is(Items.COAL))
                        +"; furnace progress="+furnace.data.get(2)+", status="+furnace.data.get(4)+", smith fuel="+InventoryOps.count(List.of(forgeBarrel,forger.bag()),s -> s.is(Items.COAL)));
                helper.assertTrue(InventoryOps.count(all,s -> s.is(Items.IRON_INGOT))==0 && InventoryOps.count(all,s -> s.is(WWMC.STEEL_INGOT.get()))==0
                        && InventoryOps.count(all,s -> s.is(Items.STICK))==0,"Steel pickaxe did not consume exactly three alloyed iron ingots and two sticks");
                helper.assertTrue(InventoryOps.count(all,s -> s.is(Items.COAL))==10,"Steel carbon, furnace fuel or forge fuel was synthesized or incorrectly charged");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Native tool tiers including bronze and steel affect real citizen work steps; unsuitable held gear gives no bonus and miners retain their existing physical pickaxe timing.")
    static void properToolTiersImproveCitizenWork(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-25000));
            Station farm=new Station(start.east(10),StructureRole.FARM); var f=fixture(level,start,farm);
            var worker=f.citizen(farm.position().south(),farm,false);
            List<ItemStack> hoes=List.of(new ItemStack(Items.WOODEN_HOE),new ItemStack(Items.STONE_HOE),new ItemStack(WWMC.BRONZE_HOE.get()),new ItemStack(Items.IRON_HOE),new ItemStack(WWMC.STEEL_HOE.get()),new ItemStack(Items.DIAMOND_HOE),new ItemStack(Items.NETHERITE_HOE));
            int previous=-1,wood=0,diamond=0;
            for(var hoe:hoes) {
                worker.setItemSlot(EquipmentSlot.MAINHAND,hoe); int bonus=worker.speedBonus();
                helper.assertTrue(bonus>previous,"A higher proper hoe tier did not improve farming speed: "+hoe); previous=bonus;
                worker.getRandom().setSeed(42); int work=0; for(int i=0;i<10000;i++) work+=worker.workStep();
                if(hoe.is(Items.WOODEN_HOE)) wood=work; if(hoe.is(Items.DIAMOND_HOE)) diamond=work;
            }
            helper.assertTrue(diamond>wood*1.3 && diamond<wood*1.5,"Tool speed did not affect real worker progress within the balanced range");
            worker.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD)); helper.assertTrue(worker.speedBonus()==0,"An unrelated weapon sped up farming");
            helper.assertTrue(WorkerTools.bonus(StructureRole.MINE,new ItemStack(Items.DIAMOND_PICKAXE))==0,"Miners received a second tier bonus on top of physical break speed");
            helper.assertTrue(new ItemStack(WWMC.STEEL_PICKAXE.get()).getMaxDamage()>new ItemStack(Items.IRON_PICKAXE).getMaxDamage()
                    && new ItemStack(WWMC.STEEL_PICKAXE.get()).getDestroySpeed(Blocks.STONE.defaultBlockState())<new ItemStack(Items.DIAMOND_PICKAXE).getDestroySpeed(Blocks.STONE.defaultBlockState()),"Steel does not sit between iron and diamond");
            f.close(); helper.succeed();
        });
    }
}
