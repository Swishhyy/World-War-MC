package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.BronzeAnvilBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Blacksmith stock orders use real recipe ingredients, fuel, an anvil and heat. No output is granted by a GUI click. */
public final class ForgeWorkshop {
    public static final int BRONZE_SPEED_PERCENT=35;
    public record Plan(Workshop.Order order,List<Ingredient> ingredients,ItemStack result,
            Workshop.Plan crafting,RecipeHolder<SmithingRecipe> upgrade,int ticks) {
        public boolean uses(ItemStack stack) { return fuel(stack) || ingredients.stream().anyMatch(i -> i.test(stack)); }
    }
    private static final net.minecraft.tags.TagKey<Item> FORGED=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            Identifier.fromNamespaceAndPath("wwmc","forged_equipment"));
    private ForgeWorkshop() {}
    public static boolean fuel(ItemStack stack) { return stack.is(Items.COAL) || stack.is(Items.CHARCOAL); }
    public static boolean forged(ItemStack stack) {
        return !stack.isEmpty() && stack.is(FORGED);
    }
    public static List<BlockPos> heat(ServerLevel level,Settlement town,Station station) {
        if(station.role()!=StructureRole.BLACKSMITH || !SettlementService.active(level,station)) return List.of();
        var result=new ArrayList<BlockPos>();
        for(BlockPos p:SettlementService.cells(station)) if(town.contains(p) && level.hasChunkAt(p)
                && (level.getBlockState(p).is(Blocks.FURNACE) || level.getBlockState(p).is(Blocks.BLAST_FURNACE))
                && SettlementService.ownsBlock(level,town,station,p)) result.add(p.immutable());
        return result;
    }
    public static List<Item> catalogue() {
        var result=new ArrayList<Item>();
        for(Item item:BuiltInRegistries.ITEM) if(forged(new ItemStack(item))) result.add(item);
        return result;
    }
    public static List<Plan> plans(ServerLevel level,Workshop.Order order) {
        Item item=order.resolve();
        if(!forged(new ItemStack(item))) return List.of();
        var result=new ArrayList<Plan>();
        for(Workshop.Plan recipe:Workshop.plans(Workshop.Recipes.of(level),order))
            result.add(new Plan(order,recipe.ingredients(),recipe.result(),recipe,null,200+AgeProgression.required(recipe.result())*40));
        String id=BuiltInRegistries.ITEM.getKey(item).getPath();
        if(id.startsWith("netherite_")) {
            Item base=BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("diamond_"+id.substring(10)));
            if(base!=null && base!=Items.AIR) {
                var input=new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),new ItemStack(base),new ItemStack(Items.NETHERITE_INGOT));
                var recipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING,input,level);
                if(recipe.isPresent() && recipe.get().value().assemble(input).is(item)) {
                    SmithingRecipe smith=recipe.get().value();
                    if(smith.templateIngredient().isPresent() && smith.additionIngredient().isPresent())
                        result.add(new Plan(order,List.of(smith.templateIngredient().get(),smith.baseIngredient(),smith.additionIngredient().get()),
                                smith.assemble(input),null,recipe.get(),400));
                }
            }
        }
        return result;
    }
    private static SimpleContainer copy(List<Container> stock) {
        int size=stock.stream().mapToInt(Container::getContainerSize).sum();
        var copy=new SimpleContainer(size); int next=0;
        for(var box:stock) for(int slot=0;slot<box.getContainerSize();slot++) copy.setItem(next++,box.getItem(slot).copy());
        return copy;
    }
    public static boolean ready(Plan plan,List<Container> stock) {
        var copy=copy(stock);
        if(InventoryOps.takeOne(List.of(copy),ForgeWorkshop::fuel).isEmpty()) return false;
        for(var ingredient:plan.ingredients()) if(InventoryOps.takeOne(List.of(copy),ingredient::test).isEmpty()) return false;
        return true;
    }
    public static Plan choose(ServerLevel level,Settlement town,List<Container> stock,List<Container> supplies,boolean bronzeAnvil) {
        for(var order:town.progress.forgeOrders) if(order.target()>0 && Workshop.stock(stock,order)<order.target())
            for(Plan plan:plans(level,order)) if(AgeProgression.allowed(town,plan.result())
                    && (!bronzeAnvil || AgeProgression.required(plan.result())<=2) && ready(plan,supplies)) return plan;
        return null;
    }
    public static boolean fetch(Plan plan,List<Container> stock,CitizenInventory bag) {
        if(!ready(plan,stock)) return false;
        for(var ingredient:plan.ingredients()) bag.offer(InventoryOps.takeOne(stock,ingredient::test));
        bag.offer(InventoryOps.takeOne(stock,ForgeWorkshop::fuel));
        return true;
    }
    /** Validate and consume the original stacks in one server step. Native upgrades keep enchantments and other components. */
    public static boolean craft(ServerLevel level,Plan plan,CitizenInventory bag) {
        if(!ready(plan,List.of(bag))) return false;
        if(plan.crafting()!=null) {
            if(!Workshop.craft(level,bag,plan.crafting(),bag::offer)) return false;
        } else {
            var preview=copy(List.of(bag));
            var input=new ArrayList<ItemStack>();
            for(var ingredient:plan.ingredients()) input.add(InventoryOps.takeOne(List.of(preview),ingredient::test));
            ItemStack output=plan.result().copy();
            if(plan.upgrade()!=null) {
                var smithing=new SmithingRecipeInput(input.get(0),input.get(1),input.get(2));
                if(!plan.upgrade().value().matches(smithing,level)) return false;
                output=plan.upgrade().value().assemble(smithing);
                if(output.isEmpty() || !output.is(plan.result().getItem())) return false;
            }
            for(var ingredient:plan.ingredients()) InventoryOps.takeOne(List.of(bag),ingredient::test);
            bag.offer(output);
        }
        InventoryOps.takeOne(List.of(bag),ForgeWorkshop::fuel);
        return true;
    }
    public static boolean order(ServerLevel level,Settlement town,String id,int target) {
        var parsed=Identifier.tryParse(id);
        Item item=parsed==null ? null : BuiltInRegistries.ITEM.getValue(parsed);
        if(item==null || !catalogue().contains(item)) return false;
        var order=new Workshop.Order(id,target);
        if(plans(level,order).isEmpty()) return false;
        for(int i=0;i<town.progress.forgeOrders.size();i++) if(town.progress.forgeOrders.get(i).item().equals(id)) {
            town.progress.forgeOrders.set(i,order); return true;
        }
        if(town.progress.forgeOrders.size()>=Workshop.MAX_ORDERS) return false;
        town.progress.forgeOrders.add(order); return true;
    }
    /** Move previously learned metal equipment orders to the smith, retaining their saved stock targets. */
    public static boolean migrate(ServerLevel level,Settlement town) {
        boolean changed=false;
        var old=town.craftOrders.iterator();
        while(old.hasNext()) {
            var order=old.next();
            if(!forged(new ItemStack(order.resolve()))) continue;
            boolean exists=town.progress.forgeOrders.stream().anyMatch(o -> o.item().equals(order.item()));
            if(exists || order(level,town,order.item(),order.target())) { old.remove(); changed=true; }
        }
        return changed;
    }
    /** Bronze does 35% of iron's work per second; extend the target rather than rounding away worker bonuses. */
    public static int workTicks(BlockState anvil,int baseTicks) {
        return anvil.getBlock() instanceof BronzeAnvilBlock ? Math.ceilDiv(baseTicks*100,BRONZE_SPEED_PERCENT) : baseTicks;
    }
    /** Both materials use vanilla's twelve-percent chance of advancing one wear stage per completed operation. */
    public static void wear(ServerLevel level,BlockPos anvil) {
        wear(level,anvil,level.getRandom());
    }
    public static void wear(ServerLevel level,BlockPos anvil,RandomSource random) {
        if(anvil==null || !level.hasChunkAt(anvil)) return;
        var state=level.getBlockState(anvil);
        if(!(state.getBlock() instanceof BronzeAnvilBlock || state.getBlock() instanceof AnvilBlock) || random.nextFloat()>=0.12F) return;
        BlockState next;
        if(state.getBlock() instanceof BronzeAnvilBlock) {
            int stage=state.getValue(BronzeAnvilBlock.WEAR)/4;
            next=stage==2 ? null : state.setValue(BronzeAnvilBlock.WEAR,(stage+1)*4);
        } else next=AnvilBlock.damage(state);
        if(next==null) { level.removeBlock(anvil,false); level.levelEvent(1029,anvil,0); }
        else if(!next.equals(state)) level.setBlockAndUpdate(anvil,next);
    }
}
