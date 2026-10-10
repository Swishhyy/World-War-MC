package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Real researchers, recipe clicks, armor, ore drops and generated ruin layouts on a dedicated server. */
public final class ResearchAgeWorldTests {
    private static ResourceKey<Recipe<?>> recipeKey(String name) {
        return ResourceKey.create(Registries.RECIPE,Identifier.fromNamespaceAndPath("wwmc",name));
    }
    /** Keep the native discovery listener and recipe rewards; FakePlayer's default advancement tracker ignores them. */
    private static final class RecipePlayer extends FakePlayer {
        private PlayerAdvancements progress;
        RecipePlayer(ServerLevel level,GameProfile profile) {
            super(level,profile);
            var server=level.getServer();
            progress=new PlayerAdvancements(server.getFixerUpper(),server.getPlayerList(),server.getAdvancements(),
                    Path.of("build","gametest-advancements",profile.id()+".json"),this);
        }
        @Override public PlayerAdvancements getAdvancements() { return progress==null ? super.getAdvancements() : progress; }
    }

    @GameTest(timeoutTicks=800)
    @EmptyTemplate
    @TestHolder(description="Copper discovers the alloy furnace; bronze comes directly from copper and tin after research. Previously made blends still smelt, and native equipment discovery cannot bypass the blacksmith.")
    static void bronzeDiscoveryCraftingAndSmelting(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-12600));
            var chunks=CitizenNavigationTests.pinTicking(level,start,1); CitizenNavigationTests.meadow(level,start,-8,16,-8,8);
            var owner=new RecipePlayer(level,new GameProfile(UUID.randomUUID(),"BronzeOwner"));
            WWMC.TAB.get().buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),false,level.registryAccess()));
            for(var material:List.of(WWMC.RAW_TIN.get(),WWMC.TIN_INGOT.get(),WWMC.BRONZE_INGOT.get(),WWMC.ALLOY_FURNACE_ITEM.get()))
                helper.assertTrue(WWMC.TAB.get().getDisplayItems().stream().filter(v -> v.is(material)).count()==1,"A current bronze ingredient or appliance is missing from the creative tab");
            helper.assertTrue(WWMC.TAB.get().getDisplayItems().stream().noneMatch(v -> v.is(WWMC.BRONZE_BLEND.get())),"Legacy blend is still advertised as the new production route");
            owner.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);
            var town=new Settlement(UUID.randomUUID(),owner.getUUID(),"Bronze production",start,32,List.of(),List.of(),"balanced");
            SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
            ItemStack copper=new ItemStack(Items.COPPER_INGOT); owner.getInventory().setItem(9,copper);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(owner,owner.getInventory(),copper);
            helper.assertTrue(owner.getRecipeBook().contains(recipeKey("alloy_furnace")),"Collecting copper did not discover the alloy furnace");
            ItemStack rawTin=new ItemStack(WWMC.RAW_TIN.get()); owner.getInventory().setItem(10,rawTin);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(owner,owner.getInventory(),rawTin);
            helper.assertTrue(owner.getRecipeBook().contains(recipeKey("tin_from_raw_smelting")) && owner.getRecipeBook().contains(recipeKey("tin_from_raw_blasting")),"Raw tin did not discover native refining recipes");
            var menu=owner.inventoryMenu; owner.containerMenu=menu;
            for(int slot=1;slot<=4;slot++) menu.getSlot(slot).setByPlayer(new ItemStack(slot==4 ? WWMC.TIN_INGOT.get() : Items.COPPER_INGOT));
            menu.slotsChanged(menu.getSlot(1).container); helper.assertTrue(menu.getSlot(0).getItem().isEmpty(),"Manual blend crafting is still enabled");
            BlockPos alloyPos=start.east(3),legacyPos=start.east(6);
            level.setBlockAndUpdate(alloyPos,WWMC.ALLOY_FURNACE.get().defaultBlockState()); level.setBlockAndUpdate(legacyPos,Blocks.FURNACE.defaultBlockState());
            var alloy=(io.github.swishhyy.wwmc.block.AlloyFurnaceEntity)level.getBlockEntity(alloyPos);
            alloy.setItem(0,new ItemStack(Items.COPPER_INGOT,3)); alloy.setItem(1,new ItemStack(WWMC.TIN_INGOT.get())); alloy.setItem(2,new ItemStack(Items.COAL));
            var legacy=(AbstractFurnaceBlockEntity)level.getBlockEntity(legacyPos); legacy.setItem(0,new ItemStack(WWMC.BRONZE_BLEND.get())); legacy.setItem(1,new ItemStack(Items.COAL));
            helper.runAtTickTime(40,() -> {
                helper.assertTrue(alloy.getItem(3).isEmpty() && alloy.getItem(0).getCount()==3 && alloy.getItem(2).getCount()==1,"An unresearched furnace consumed inputs or fuel"); town.progress.research.add("bronze_age");
            });
            helper.runAtTickTime(650,() -> {
                helper.assertTrue(alloy.getItem(3).is(WWMC.BRONZE_INGOT.get()) && alloy.getItem(3).getCount()==4 && alloy.getItem(0).isEmpty() && alloy.getItem(1).isEmpty(),"Actual alloy furnace failed direct bronze production");
                helper.assertTrue(legacy.getItem(2).is(WWMC.BRONZE_INGOT.get()) && legacy.getItem(0).isEmpty(),"Legacy blends stopped smelting");
                owner.getInventory().add(alloy.removeItem(3,4)); owner.getInventory().add(legacy.removeItem(2,1));
                ItemStack ingot=new ItemStack(WWMC.BRONZE_INGOT.get());
                CriteriaTriggers.INVENTORY_CHANGED.trigger(owner,owner.getInventory(),ingot);
                for(String part:List.of("sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots"))
                    helper.assertTrue(owner.getRecipeBook().contains(recipeKey("bronze_"+part)),"Bronze ingot did not discover equipment: "+part);
                var table=new CraftingMenu(1,owner.getInventory(),ContainerLevelAccess.create(level,start)); owner.containerMenu=table;
                for(int slot=0;slot<owner.getInventory().getContainerSize();slot++) if(owner.getInventory().getItem(slot).is(WWMC.BRONZE_INGOT.get())) {
                    ItemStack metal=owner.getInventory().removeItem(slot,3);
                    for(int input=1;input<=3;input++) table.getSlot(input).setByPlayer(metal.split(1));
                    break;
                }
                table.getSlot(5).setByPlayer(new ItemStack(Items.STICK)); table.getSlot(8).setByPlayer(new ItemStack(Items.STICK));
                table.slotsChanged(table.getSlot(1).container);
                helper.assertTrue(table.getSlot(0).getItem().is(WWMC.BRONZE_PICKAXE.get()),"Smelted bronze did not match the real pickaxe recipe");
                table.clicked(0,0,ContainerInput.QUICK_MOVE,owner);
                helper.assertTrue(owner.getInventory().countItem(WWMC.BRONZE_PICKAXE.get())==0
                        && table.getSlot(1).getItem().getCount()==1 && table.getSlot(5).getItem().getCount()==1,
                        "Researched bronze equipment bypassed the blacksmith or consumed blocked crafting ingredients");
                SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks);
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=550)
    @EmptyTemplate
    @TestHolder(description="Paid research waits for a real citizen at a lectern, resumes its saved progress, pauses with the worker and finishes without paying twice.")
    static void researchRequiresRealWork(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-10400));
            var chunks=CitizenNavigationTests.pinTicking(level,start,2); CitizenNavigationTests.meadow(level,start,-8,30,-12,12);
            Station warehouse=new Station(start.east(6),StructureRole.WAREHOUSE),station=new Station(start.east(20),StructureRole.RESEARCHER);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Age research",start,96,List.of(),List.of(warehouse,station),"balanced");
            SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
            for(var s:town.stations) level.setBlockAndUpdate(s.position(),WWMC.STATIONS.get(s.role()).get().defaultBlockState());
            BlockPos desk=station.position().north(2),chest=warehouse.position().south(2);
            level.setBlockAndUpdate(desk,Blocks.LECTERN.defaultBlockState()); level.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
            Container stock=(Container)level.getBlockEntity(chest);
            stock.setItem(0,new ItemStack(Items.COPPER_INGOT,24)); stock.setItem(1,new ItemStack(WWMC.TIN_INGOT.get(),8));
            stock.setItem(2,new ItemStack(Items.COAL,8)); stock.setItem(3,new ItemStack(Items.PAPER,8));
            var worker=new CitizenEntity(WWMC.CITIZEN.get(),level); worker.join(town.id); worker.setNoAi(true);
            worker.setPos(station.position().getX()+.5,station.position().getY(),station.position().getZ()+1.5);
            worker.bag().offer(new ItemStack(Items.BREAD)); town.citizens.add(worker.getUUID()); town.jobs.assign(worker.getUUID(),station.position()); level.addFreshEntity(worker);
            // This fixture represents a project paid before the scroll update.
            for(int slot=0;slot<4;slot++) stock.setItem(slot,ItemStack.EMPTY);
            town.progress.project="bronze_age"; town.progress.projectTicks=0;
            helper.assertTrue(Research.study(level,town,"bronze_age").contains("first"),"Starting twice must retain the original paid project");
            helper.runAtTickTime(80,() -> {
                helper.assertTrue(town.progress.projectTicks==0,"Research advanced without active AI");
                var saved=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
                town.progress=Settlement.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow().progress;
                worker.setNoAi(false);
            });
            helper.runAtTickTime(260,() -> {
                helper.assertTrue(town.progress.projectTicks>0 && !Research.has(town,"bronze_age"),"Researcher did not work at the lectern: "+worker.activity());
                helper.assertTrue(worker.blockPosition().distSqr(desk)<16,"Research happened remotely");
                worker.setNoAi(true);
                int progress=town.progress.projectTicks;
                helper.runAfterDelay(50,() -> {
                    helper.assertTrue(town.progress.projectTicks==progress,"Paused researcher kept advancing");
                    town.progress.projectTicks=Research.byId("bronze_age").ticks()-10; worker.setNoAi(false);
                });
            });
            helper.runAtTickTime(430,() -> {
                helper.assertTrue(Research.has(town,"bronze_age") && town.progress.project.isEmpty(),"Final work did not finish the project: "+worker.activity());
                helper.assertTrue(Research.study(level,town,"bronze_age").startsWith("Already"),"Completed research was charged again");
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> !s.isEmpty())==0,"Research did not spend exact supplies once");
                worker.discard(); SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="Locked loot stays in inventory, armor returns intact, and a real shift-click recipe keeps its ingredients until accepted settlement research unlocks it.")
    static void equipmentAndRecipeLocks(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-10600));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,16,-8,8); CitizenNavigationTests.meadow(level,start,-8,16,-8,8);
            var owner=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"AgeOwner"));
            var friend=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"AgeFriend"));
            owner.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);
            var town=new Settlement(UUID.randomUUID(),owner.getUUID(),"Age locks",start,96,List.of(),List.of(),"balanced");
            SettlementData.get(level).settlements.add(town);
            var starterMenu=new CraftingMenu(2,owner.getInventory(),ContainerLevelAccess.create(level,start));
            for(var entry:Map.of(Items.LECTERN,StructureRole.RESEARCHER,Items.WOODEN_SWORD,StructureRole.GUARD,
                    Items.STONE_SWORD,StructureRole.BARRACKS,Items.WOODEN_AXE,StructureRole.BUTCHER,Items.STONE_AXE,StructureRole.LUMBER).entrySet()) {
                for(int slot=1;slot<=9;slot++) starterMenu.getSlot(slot).setByPlayer(new ItemStack(slot==5 ? entry.getKey() : Items.OAK_PLANKS));
                starterMenu.slotsChanged(starterMenu.getSlot(5).container);
                ItemStack stationResult=starterMenu.getSlot(0).getItem();
                helper.assertTrue(stationResult.is(WWMC.STATION_ITEMS.get(entry.getValue()).get()) && AgeProgression.allowed(owner,stationResult),
                        "Starter station recipe is ambiguous or age-locked: "+entry.getValue());
            }
            ItemStack sword=new ItemStack(Items.IRON_SWORD),helmet=new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(17);
            helper.assertTrue(!AgeProgression.allowed(owner,sword) && !AgeProgression.allowed(owner,new ItemStack(WWMC.BRONZE_PICKAXE.get())),"Stone Age permits metal equipment");
            owner.getInventory().add(sword.copy()); owner.setItemSlot(EquipmentSlot.HEAD,helmet); AgeProgression.unequip(owner);
            helper.assertTrue(owner.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && owner.getInventory().countItem(Items.IRON_SWORD)==1
                    && owner.getInventory().countItem(Items.IRON_HELMET)==1,"Locked loot or armor was lost");
            boolean intact=false;
            for(int n=0;n<owner.getInventory().getContainerSize();n++) {
                ItemStack stored=owner.getInventory().getItem(n);
                if(stored.is(Items.IRON_HELMET) && stored.getDamageValue()==17) intact=true;
            }
            helper.assertTrue(intact,"Armor damage was changed");
            var menu=new CraftingMenu(1,owner.getInventory(),ContainerLevelAccess.create(level,start));
            owner.containerMenu=menu;
            menu.getSlot(2).setByPlayer(new ItemStack(Items.IRON_INGOT)); menu.getSlot(5).setByPlayer(new ItemStack(Items.IRON_INGOT)); menu.getSlot(8).setByPlayer(new ItemStack(Items.STICK));
            menu.slotsChanged(menu.getSlot(2).container);
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_SWORD),"Real sword recipe did not load");
            menu.clicked(0,0,ContainerInput.QUICK_MOVE,owner);
            helper.assertTrue(menu.getSlot(2).getItem().getCount()==1 && menu.getSlot(5).getItem().getCount()==1 && menu.getSlot(8).getItem().getCount()==1
                    && owner.getInventory().countItem(Items.IRON_SWORD)==1,"Locked recipe consumed materials or produced an item");
            town.progress.research.add("bronze_age"); town.progress.research.add("iron_age"); town.campaign.invitations.put(friend.getUUID(),"builder");
            helper.assertTrue(AgeProgression.allowed(owner,sword) && !AgeProgression.allowed(friend,sword),"An invitation grants age research before acceptance");
            TownAccess.accept(town,friend.getUUID()); helper.assertTrue(AgeProgression.allowed(friend,sword),"Accepted member did not share research");
            town.campaign.members.remove(friend.getUUID()); helper.assertTrue(!AgeProgression.allowed(friend,sword),"Former member kept settlement research");
            menu.clicked(0,0,ContainerInput.QUICK_MOVE,owner);
            helper.assertTrue(owner.getInventory().countItem(Items.IRON_SWORD)==1 && menu.getSlot(2).getItem().getCount()==1,
                    "Research incorrectly enabled hand-crafting forged equipment");
            helper.assertTrue(!AgeProgression.allowed(owner,new ItemStack(Items.DIAMOND_PICKAXE)),"Iron Age skipped Gemcraft");
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.release(level,start,chunks); helper.succeed();
        });
    }

    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="The registered tin feature replaces both stone and deepslate, drops raw tin with a stone pick, and its real furnace recipe loads.")
    static void tinGenerationAndDrops(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,32,-10800));
            var chunks=CitizenNavigationTests.pinArea(level,start,-12,24,-12,12);
            var feature=level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
                    .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE,Identifier.fromNamespaceAndPath("wwmc","tin_ore"))).value();
            for(int n=0;n<2;n++) {
                BlockPos center=start.east(n*16);
                for(BlockPos p:BlockPos.betweenClosed(center.offset(-6,-6,-6),center.offset(6,6,6))) level.setBlockAndUpdate(p,(n==0 ? Blocks.STONE : Blocks.DEEPSLATE).defaultBlockState());
                helper.assertTrue(feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(4+n),center),"Tin feature did not place");
                BlockPos ore=null;
                for(BlockPos p:BlockPos.betweenClosed(center.offset(-6,-6,-6),center.offset(6,6,6)))
                    if(level.getBlockState(p).is(n==0 ? WWMC.TIN_ORE.get() : WWMC.DEEPSLATE_TIN_ORE.get())) { ore=p.immutable(); break; }
                helper.assertTrue(ore!=null,"Missing tin ore variant "+n);
                var drops=Block.getDrops(level.getBlockState(ore),level,ore,null,null,new ItemStack(Items.STONE_PICKAXE));
                helper.assertTrue(drops.stream().anyMatch(s -> s.is(WWMC.RAW_TIN.get()) && s.getCount()==1),"Tin ore did not drop raw tin");
            }
            helper.assertTrue(level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(new ItemStack(WWMC.RAW_TIN.get())),level).isPresent(),"Tin smelting recipe failed to load");
            CitizenNavigationTests.release(level,start,chunks); helper.succeed();
        });
    }

    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="A castle checks its entire footprint for construction, generates distinct furnished stations with usable beds, and is not rebuilt or duplicated.")
    static void castleUsesRealStations(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-11200));
            var chunks=CitizenNavigationTests.pinArea(level,start,-16,16,-16,16); CitizenNavigationTests.meadow(level,start,-16,16,-16,16);
            BlockPos protectedPos=start.east(10).above(); level.setBlockAndUpdate(protectedPos,Blocks.GOLD_BLOCK.defaultBlockState());
            String region="castle-test-"+UUID.randomUUID();
            helper.assertTrue(ExpeditionService.discover(level,start,"fort",region)==null,"Castle overwrote construction at its outer edge");
            level.setBlockAndUpdate(protectedPos,Blocks.AIR.defaultBlockState());
            var site=ExpeditionService.discover(level,start,"fort",region);
            helper.assertTrue(site!=null,"Castle did not generate on clear flat terrain: "+ExpeditionService.siteIssue(level,start));
            BlockPos c=site.pos;
            helper.assertTrue(level.getBlockState(c.offset(8,0,-7)).is(WWMC.STATIONS.get(StructureRole.RESEARCHER).get())
                    && level.getBlockState(c.offset(8,0,-9)).is(Blocks.LECTERN),"Missing research example room");
            Station housing=new Station(c.offset(-8,0,-7),StructureRole.HOUSING),warehouse=new Station(c.offset(8,0,7),StructureRole.WAREHOUSE),mine=new Station(c.offset(-8,0,7),StructureRole.MINE);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Castle example",c,96,List.of(),List.of(housing,warehouse,mine),"balanced");
            helper.assertTrue(SettlementService.beds(level,town,housing).size()==2 && level.getBlockState(c.offset(-8,1,-9)).isAir(),"Castle beds have obstructed halves");
            helper.assertTrue(!SettlementService.jobBarrels(level,town,mine).isEmpty(),"Castle warehouse swallows the mine's job barrel");
            level.setBlockAndUpdate(c.east(3),Blocks.GOLD_BLOCK.defaultBlockState());
            helper.assertTrue(ExpeditionService.discover(level,start,"fort",region)==null && level.getBlockState(c.east(3)).is(Blocks.GOLD_BLOCK),"A revisit rebuilt the ruin");
            ExpeditionData.get(level).sites.remove(site); ExpeditionData.get(level).setDirty();
            CitizenNavigationTests.release(level,start,chunks); helper.succeed();
        });
    }
}
