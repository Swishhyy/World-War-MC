package io.github.swishhyy.wwmc;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Learned crafting, job barrels and couriers, ore veins and screen data, checked against a real server's recipes and tags. */
public final class LogisticsChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static Settlement town() { return new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Town",BlockPos.ZERO,240,List.of(),List.of(),"balanced"); }
    private static SimpleContainer box(ItemStack... stacks) {
        SimpleContainer box=new SimpleContainer(27);
        for(int i=0;i<stacks.length;i++) box.setItem(i,stacks[i]);
        return box;
    }

    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void learnedCrafting(MinecraftServer server) {
        // The test server loads recipes but no world; shaped and shapeless recipes do not need one.
        Workshop.Recipes level=new Workshop.Recipes(server.getRecipeManager().recipeMap(),null);
        Map<Item,List<Workshop.Plan>> plans=Workshop.plans(level);
        for(Item item:List.of(Items.TORCH,Items.LADDER,Items.STICK,Items.OAK_PLANKS,Items.STONE_PICKAXE,Items.CHEST,Items.CAKE,Items.IRON_BLOCK,Items.IRON_INGOT,Items.ARROW,Items.BOW))
            check(plans.containsKey(item),"Minecraft's crafting recipe for "+item+" is followed");
        check(!plans.containsKey(Items.DIAMOND_ORE),"Items no crafting recipe makes are absent");

        Settlement town=town();
        check(town.craftOrders.size()==9 && town.craftOrders.getFirst().item().equals("minecraft:stone_pickaxe"),"New towns start with the default orders");
        for(Workshop.Order order:town.craftOrders) check(!Workshop.plans(level,order).isEmpty(),"Every default order has a recipe: "+order.item());
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow().getAsJsonObject();
        json.remove("craft_orders");
        JsonArray disabled=new JsonArray(); disabled.add("torches"); json.add("disabled_recipes",disabled);
        Settlement legacy=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(legacy.craftOrders.stream().anyMatch(o -> o.item().equals("minecraft:torch") && o.target()==0),"An order switched off in an older save stays off");
        town.craftOrders.set(0,town.craftOrders.getFirst().withTarget(5));
        Settlement reloaded=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
        check(reloaded.craftOrders.getFirst().target()==5 && reloaded.craftOrders.size()==9,"Orders and amounts survive a restart");
        check(new Workshop.Order("othermod:gizmo",4).resolve()==Items.AIR && !new Workshop.Order("othermod:gizmo",4).product(new ItemStack(Items.STONE)),"An order for a removed mod's item stays idle");

        Settlement shop=town(); shop.craftOrders.clear();
        check(Workshop.learn(level,shop,new ItemStack(Items.CHEST)).startsWith("Learned") && shop.craftOrders.size()==1,"Any crafting-table item can be taught");
        check(shop.craftOrders.getFirst().target()==16,"Stackable items start with a target of 16");
        check(Workshop.learn(level,shop,new ItemStack(Items.CHEST)).startsWith("Craftsmen already"),"An item is taught once");
        check(Workshop.learn(level,shop,new ItemStack(Items.DIAMOND_ORE)).startsWith("No crafting"),"Items without a crafting recipe cannot be taught");
        check(Workshop.learn(level,shop,new ItemStack(Items.SPRUCE_PLANKS)).contains("any wood"),"A planks order accepts any wood");
        check(Workshop.learn(level,shop,new ItemStack(Items.OAK_PLANKS)).startsWith("Craftsmen already"),"One planks order covers every wood");
        check(Workshop.learn(level,shop,new ItemStack(Items.STONE_SHOVEL)).startsWith("Learned") && shop.craftOrders.getLast().target()==1,"Tools start with a target of one");

        List<Workshop.Order> planks=List.of(new Workshop.Order("minecraft:oak_planks",8));
        SimpleContainer logs=box(new ItemStack(Items.BIRCH_LOG,5)),bag=new SimpleContainer(36);
        Workshop.Job job=Workshop.choose(level,planks,List.of(logs),List.of(logs));
        check(job!=null && job.plan().result().is(Items.BIRCH_PLANKS),"A planks order uses whichever logs the town has");
        check(Workshop.fetch(level,planks,job,List.of(logs),List.of(logs),bag)==2 && logs.getItem(0).getCount()==3,"Only the logs the shortage needs are carried");
        List<ItemStack> made=new ArrayList<>();
        check(Workshop.craft(null,bag,job.plan(),made::add) && made.getFirst().is(Items.BIRCH_PLANKS) && made.getFirst().getCount()==4,"One birch log becomes four birch planks");

        List<Workshop.Order> ladders=List.of(new Workshop.Order("minecraft:ladder",3));
        SimpleContainer sticks=box(new ItemStack(Items.STICK,7)),bench=new SimpleContainer(36);
        Workshop.Job ladder=Workshop.choose(level,ladders,List.of(sticks),List.of(sticks));
        check(ladder!=null && Workshop.fetch(level,ladders,ladder,List.of(sticks),List.of(sticks),bench)==1 && sticks.isEmpty(),"Seven sticks go to the bench for one batch");
        List<ItemStack> rungs=new ArrayList<>();
        check(Workshop.craft(null,bench,ladder.plan(),rungs::add) && rungs.getFirst().is(Items.LADDER) && rungs.getFirst().getCount()==3 && bench.isEmpty(),
                "A shaped recipe is laid out and checked like a crafting table");
        SimpleContainer stocked=box(new ItemStack(Items.LADDER,3),new ItemStack(Items.STICK,7));
        check(Workshop.choose(level,ladders,List.of(stocked),List.of(stocked))==null,"A stocked order is not overproduced");
        List<Workshop.Order> paused=List.of(new Workshop.Order("minecraft:ladder",0));
        check(Workshop.choose(level,paused,List.of(sticks),List.of(box(new ItemStack(Items.STICK,7))))==null,"A zero target pauses an order");

        List<Workshop.Order> cake=List.of(new Workshop.Order("minecraft:cake",1));
        SimpleContainer kitchen=box(new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR,2),new ItemStack(Items.EGG),new ItemStack(Items.WHEAT,3)),tray=new SimpleContainer(36);
        Workshop.Job baking=Workshop.choose(level,cake,List.of(kitchen),List.of(kitchen));
        check(baking!=null && Workshop.fetch(level,cake,baking,List.of(kitchen),List.of(kitchen),tray)==1,"Cake ingredients are gathered");
        List<ItemStack> out=new ArrayList<>();
        check(Workshop.craft(null,tray,baking.plan(),out::add) && out.stream().anyMatch(s -> s.is(Items.CAKE))
                && out.stream().filter(s -> s.is(Items.BUCKET)).count()==3,"Milk buckets come back empty");

        List<Workshop.Order> metal=List.of(new Workshop.Order("minecraft:iron_block",10),new Workshop.Order("minecraft:iron_ingot",64));
        SimpleContainer vault=box(new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.IRON_INGOT,36));
        Workshop.Job blocks=Workshop.choose(level,metal,List.of(vault),List.of(vault));
        check(blocks!=null && blocks.order().item().equals("minecraft:iron_block"),"Surplus ingots become blocks");
        check(Workshop.fetch(level,metal,blocks,List.of(vault),List.of(vault),new SimpleContainer(36))==4
                && InventoryOps.count(List.of(vault),s -> s.is(Items.IRON_INGOT))==64,"Blocks only use ingots above the ingot order's target");
        SimpleContainer blockStock=box(new ItemStack(Items.IRON_BLOCK,9));
        check(Workshop.choose(level,metal,List.of(blockStock),List.of(blockStock))==null,"Blocks below their own target are never broken back into ingots");
        System.out.println("Passed "+checks+" learned crafting checks.");
    }

    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void jobBarrelsAndCouriers(MinecraftServer server) {
        JobStorage.Supplies level=new JobStorage.Supplies(FuelValues.vanillaBurnTimes(server.registryAccess(),FeatureFlags.DEFAULT_FLAGS,200),server.getRecipeManager(),null);
        Settlement town=town();
        SimpleContainer smeltery=box(new ItemStack(Items.RAW_IRON,10),new ItemStack(Items.COAL,5),new ItemStack(Items.IRON_INGOT,12));
        var pickups=JobStorage.collectable(level,town,StructureRole.SMELTERY,List.of(smeltery));
        check(JobStorage.goods(pickups)==12 && pickups.getFirst().slot()==2,"Smelters keep ore and fuel; couriers take the ingots");
        SimpleContainer mine=box(new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.COBBLESTONE,40),new ItemStack(Items.RAW_COPPER,7));
        check(JobStorage.goods(JobStorage.collectable(level,town,StructureRole.MINE,List.of(mine)))==31,"Miners keep pickaxes and sixteen floor blocks");
        ItemStack worn=new ItemStack(Items.IRON_PICKAXE); worn.setDamageValue(worn.getMaxDamage()-1);
        mine.setItem(3,worn);
        check(JobStorage.goods(JobStorage.collectable(level,town,StructureRole.MINE,List.of(mine)))==32,"Worn pickaxes go to the warehouse for repair");
        SimpleContainer workshop=box(new ItemStack(Items.OAK_LOG,10),new ItemStack(Items.TORCH,20));
        check(JobStorage.goods(JobStorage.collectable(level,town,StructureRole.CRAFTSMAN,List.of(workshop)))==20,"Craftsmen keep materials; their products are collected");
        SimpleContainer farm=box(new ItemStack(Items.WHEAT,30),new ItemStack(Items.WHEAT_SEEDS,12));
        CitizenInventory courier=new CitizenInventory(player -> true);
        check(JobStorage.collect(level,town,StructureRole.FARM,List.of(farm),courier)==42 && farm.isEmpty() && courier.count(Items.WHEAT)==30,"A courier empties a farm barrel into its bag");
        SimpleContainer warehouse=box(new ItemStack(Items.RAW_GOLD,40),new ItemStack(Items.CHARCOAL,20)),empty=new SimpleContainer(27);
        check(JobStorage.needsSupplies(level,StructureRole.SMELTERY,List.of(empty),List.of(warehouse)),"An empty smeltery barrel is restocked");
        check(!JobStorage.needsSupplies(level,StructureRole.FARM,List.of(empty),List.of(warehouse)),"Only smelters and cooks are stocked");
        SimpleContainer packed=new SimpleContainer(27);
        for(int slot=0;slot<27;slot++) packed.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        check(!JobStorage.needsSupplies(level,StructureRole.SMELTERY,List.of(packed),List.of(warehouse)),"A full barrel is not restocked, so supplies never shuttle back and forth");
        CitizenInventory load=new CitizenInventory(player -> true);
        check(JobStorage.load(level,StructureRole.SMELTERY,List.of(empty),List.of(warehouse),load)==48
                && load.count(Items.RAW_GOLD)==32 && load.count(Items.CHARCOAL)==16,"A load is two furnace batches of ore and sixteen fuel");
        check(JobStorage.input(level,StructureRole.COOK,new ItemStack(Items.BEEF)) && JobStorage.input(level,StructureRole.COOK,new ItemStack(Items.WHEAT))
                && !JobStorage.input(level,StructureRole.COOK,new ItemStack(Items.COOKED_BEEF)),"Kitchens are stocked with raw food and wheat, not meals");
        System.out.println("Passed "+checks+" job barrel checks.");
    }

    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void oreVeins(MinecraftServer server) {
        check(OreVeins.rarity(Blocks.IRON_ORE.defaultBlockState())==1 && OreVeins.rarity(Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState())==2
                && OreVeins.rarity(Blocks.DIAMOND_ORE.defaultBlockState())==6 && OreVeins.rarity(Blocks.ANCIENT_DEBRIS.defaultBlockState())==8,"Rarer ores replenish more slowly");
        check(OreVeins.interval(Blocks.COAL_ORE.defaultBlockState(),15)==300,"A common vein yields every fifteen seconds by default");
        BlockPos mine=new BlockPos(0,64,0);
        Map<BlockPos,BlockState> world=new HashMap<>();
        world.put(mine.offset(1,1,1),Blocks.DIAMOND_ORE.defaultBlockState());
        world.put(mine.east(),Blocks.IRON_ORE.defaultBlockState());
        world.put(mine.below(2),Blocks.GOLD_ORE.defaultBlockState());
        world.put(mine.west(3),Blocks.COPPER_ORE.defaultBlockState());
        java.util.function.Function<BlockPos,BlockState> blocks=pos -> world.getOrDefault(pos,Blocks.AIR.defaultBlockState());
        Station station=new Station(mine,StructureRole.MINE);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Vein town",BlockPos.ZERO,240,List.of(),List.of(station),"balanced");
        check(mine.east().equals(OreVeins.find(pos -> true,blocks,town,station)),"An ore touching the station's face is its vein");
        world.remove(mine.east());
        check(mine.offset(1,1,1).equals(OreVeins.find(pos -> true,blocks,town,station)),"The nearest exposed ore is the vein");
        check(OreVeins.find(pos -> !pos.equals(mine.offset(1,1,1)) && !pos.equals(mine.below(2)),blocks,town,station)==null,"Unloaded blocks are never read");
        world.remove(mine.offset(1,1,1));
        check(mine.below(2).equals(OreVeins.find(pos -> true,blocks,town,station)),"Ore two blocks from the station is still its vein");
        world.remove(mine.below(2));
        check(OreVeins.find(pos -> true,blocks,town,station)==null,"Ore three blocks away is not a vein, so the mine digs tunnels as before");
        world.put(mine.east(),Blocks.IRON_ORE.defaultBlockState());
        check(OreVeins.find(pos -> true,blocks,town,new Station(mine,StructureRole.QUARRY))==null,"Only mine stations work veins");
        world.remove(mine.east());
        BlockPos buried=mine.west();
        world.put(buried,Blocks.COAL_ORE.defaultBlockState());
        for(net.minecraft.core.Direction side:net.minecraft.core.Direction.values()) world.put(buried.relative(side),Blocks.STONE.defaultBlockState());
        check(OreVeins.find(pos -> true,blocks,town,station)==null,"Ore buried on every side is not a vein, so older mines keep tunnelling");
        world.put(buried.north(),Blocks.TORCH.defaultBlockState());
        check(buried.equals(OreVeins.find(pos -> true,blocks,town,station)),"One open side, even with a torch in it, exposes the vein");
        System.out.println("Passed "+checks+" ore vein checks.");
    }

    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void screenData(MinecraftServer server) {
        PanelView view=new PanelView(Component.literal("Town"),Component.literal("12 citizens"),
                List.of(new PanelView.Tab("Overview",List.of(new PanelView.Row(new ItemStack(Items.BREAD),"Food","12 meals").bar(0.5F,Panels.GREEN).value(32))),
                        new PanelView.Tab("Citizens",List.of())),
                List.of(new PanelView.Action(Panels.ALARM,"Sound the alarm",true)));
        RegistryFriendlyByteBuf buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess(),ConnectionType.NEOFORGE);
        PanelView.STREAM_CODEC.encode(buf,view);
        PanelView copy=PanelView.STREAM_CODEC.decode(buf);
        PanelView.Row row=copy.tabs().getFirst().rows().getFirst();
        check(copy.title().getString().equals("Town") && copy.tabs().size()==2 && row.icon().is(Items.BREAD) && row.value()==32
                && Math.abs(row.bar()-0.5F)<1.0E-6 && row.color()==Panels.GREEN && copy.actions().getFirst().id()==Panels.ALARM && buf.readableBytes()==0,
                "Screen data survives the trip to the client");
        System.out.println("Passed "+checks+" screen data checks.");
    }
}
