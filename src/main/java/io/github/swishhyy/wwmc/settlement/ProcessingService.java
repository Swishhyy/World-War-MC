package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/** Workers supply real vanilla appliances; those blocks own cooking progress, fuel and recipe results. */
public final class ProcessingService {
    public static final int INPUT_LOAD=16,FUEL_LOAD=4;
    private ProcessingService() {}
    public static boolean rawMetal(ItemStack stack) {
        return stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.ORES)
                || stack.is(Items.RAW_IRON) || stack.is(Items.RAW_COPPER) || stack.is(Items.RAW_GOLD);
    }
    public static boolean buildingMaterial(ItemStack stack) {
        return stack.is(Items.SAND) || stack.is(Items.RED_SAND) || stack.is(Items.CLAY_BALL) || stack.is(Items.CLAY)
                || stack.is(net.minecraft.tags.ItemTags.LOGS);
    }
    public static boolean rawFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.is(Items.KELP);
    }
    public static boolean cookedFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && !stack.is(Tags.Items.FOODS_RAW_MEAT)
                && !stack.is(Tags.Items.FOODS_RAW_FISH) && !stack.is(Items.POTATO)
                && !stack.is(Items.ROTTEN_FLESH) && !stack.is(Items.SPIDER_EYE) && !stack.is(Items.PUFFERFISH);
    }
    public static RecipeType<? extends AbstractCookingRecipe> type(ServerLevel level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(state.is(Blocks.BLAST_FURNACE)) return RecipeType.BLASTING;
        if(state.is(Blocks.SMOKER)) return RecipeType.SMOKING;
        if(state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock) return RecipeType.CAMPFIRE_COOKING;
        return RecipeType.SMELTING;
    }
    public static boolean input(ServerLevel level,StructureRole role,BlockPos pos,ItemStack stack) {
        return input(level,role,type(level,pos),stack);
    }
    public static boolean input(ServerLevel level,StructureRole role,RecipeType<? extends AbstractCookingRecipe> type,ItemStack stack) {
        return ingredient(role,stack)
                && level.getServer().getRecipeManager().getRecipeFor(type,new SingleRecipeInput(stack),level).isPresent();
    }
    public static boolean ingredient(StructureRole role,ItemStack stack) {
        return !stack.isEmpty() && (role==StructureRole.SMELTERY ? rawMetal(stack) || buildingMaterial(stack) : role==StructureRole.COOK && rawFood(stack));
    }
    public static boolean fuel(ServerLevel level,ItemStack stack) {
        return fuel(level.fuelValues(),stack);
    }
    public static boolean fuel(FuelValues fuels,ItemStack stack) {
        // Bucket fuels would also require hauling the empty remainder; use ordinary consumed fuels here.
        // Tools, weapons and bows burn in vanilla but are worth far more as equipment, so they are never fuel.
        return !stack.isEmpty() && stack.getCraftingRemainder()==null && !stack.isDamageableItem()
                && stack.getBurnTime(RecipeType.SMELTING,fuels)>0;
    }
    public static boolean supply(ServerLevel level,StructureRole role,ItemStack stack) {
        return supply(level.fuelValues(),role,stack);
    }
    /** Finished metal is a supply only at a station that can actually alloy it. */
    public static boolean supply(ServerLevel level,Settlement town,Station station,ItemStack stack) {
        if(station==null || !supply(level,station.role(),stack)) return false;
        if(station.role()!=StructureRole.SMELTERY || !AlloyWorkshop.material(stack)
                || ingredient(station.role(),stack) || fuel(level,stack)) return true;
        boolean bronze=AlloyWorkshop.copper(stack) || AlloyWorkshop.tin(stack);
        var output=bronze ? io.github.swishhyy.wwmc.WWMC.BRONZE_INGOT.get() : io.github.swishhyy.wwmc.WWMC.STEEL_INGOT.get();
        return Research.has(town,bronze ? "bronze_age" : "steel_working") && AlloyWorkshop.target(town,output)>0
                && SettlementService.processingDevices(level,town,station).stream()
                    .anyMatch(p -> level.getBlockEntity(p) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity);
    }
    public static boolean supply(FuelValues fuels,StructureRole role,ItemStack stack) {
        return role==StructureRole.SMELTERY && (rawMetal(stack) || buildingMaterial(stack) || AlloyWorkshop.material(stack) || fuel(fuels,stack))
                || role==StructureRole.COOK && (stack.is(Items.WHEAT) || fuel(fuels,stack)
                    || rawFood(stack) && !cookedFood(stack));
    }
    public static boolean hasInputs(ServerLevel level,StructureRole role,BlockPos pos,List<Container> sources) {
        if(level.getBlockEntity(pos) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity furnace) {
            Settlement town=SettlementData.get(level).at(pos);
            if(town==null) return false;
            var all=new java.util.ArrayList<>(sources); all.add(furnace);
            return AlloyWorkshop.choose(level,town,furnace,all)!=null;
        }
        return InventoryOps.count(sources,s -> input(level,role,pos,s))>0;
    }
    public static boolean busy(ServerLevel level,BlockPos pos) {
        if(level.getBlockEntity(pos) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity furnace)
            return !furnace.getItem(0).isEmpty() || !furnace.getItem(1).isEmpty() || !furnace.getItem(3).isEmpty();
        if(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) return !furnace.getItem(0).isEmpty();
        if(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) return campfire.getItems().stream().anyMatch(s -> !s.isEmpty());
        return false;
    }
    public static boolean needsFuel(ServerLevel level,BlockPos pos) {
        if(level.getBlockEntity(pos) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity furnace)
            return AlloyWorkshop.match(furnace.getItem(0),furnace.getItem(1))!=null && furnace.getItem(2).isEmpty() && furnace.data.get(0)==0;
        return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && !furnace.getItem(0).isEmpty()
                && furnace.getItem(1).isEmpty() && !level.getBlockState(pos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
    }
    /** Haul a bounded load of ingredients and fuel, preserving any bag overflow. */
    public static void fetch(ServerLevel level,StructureRole role,BlockPos pos,List<Container> storage,CitizenInventory bag) {
        Settlement town=SettlementData.get(level).at(pos);
        if(level.getBlockEntity(pos) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity furnace) {
            if(town!=null) AlloyWorkshop.fetch(level,town,furnace,storage,bag);
            return;
        }
        ItemStack loaded=level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace ? furnace.getItem(0) : ItemStack.EMPTY;
        Predicate<ItemStack> ingredients=s -> input(level,role,pos,s) && belowTarget(level,town,role,pos,s)
                && (loaded.isEmpty() || ItemStack.isSameItemSameComponents(loaded,s));
        carry(storage,bag,ingredients,INPUT_LOAD-InventoryOps.count(List.of(bag),ingredients));
        if(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity)
            carry(storage,bag,s -> fuel(level,s) && !ingredient(role,s),FUEL_LOAD-InventoryOps.count(List.of(bag),s -> fuel(level,s) && !ingredient(role,s)));
    }
    private static void carry(List<Container> sources,CitizenInventory bag,Predicate<ItemStack> eligible,int maximum) {
        for(int i=0;i<maximum && !bag.needsDelivery();i++) {
            ItemStack next=InventoryOps.takeOne(sources,eligible);
            if(next.isEmpty()) break;
            bag.offer(next);
        }
    }
    /** Return the number of finished items collected; never synthesize the recipe's result. */
    public static int service(ServerLevel level,StructureRole role,BlockPos pos,CitizenInventory bag,LivingEntity worker) {
        if(!level.hasChunkAt(pos) || !StationDetection.processingBlock(role,level.getBlockState(pos))) return 0;
        Settlement town=SettlementData.get(level).at(pos);
        if(level.getBlockEntity(pos) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity furnace)
            return town==null ? 0 : AlloyWorkshop.service(level,town,furnace,bag);
        if(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            ItemStack output=furnace.removeItemNoUpdate(2);
            int collected=output.getCount();
            bag.offer(output);
            InventoryOps.moveToSlot(List.of(bag),furnace,0,s -> input(level,role,pos,s) && belowTarget(level,town,role,pos,s),INPUT_LOAD);
            if(!furnace.getItem(0).isEmpty() && input(level,role,pos,furnace.getItem(0)))
                InventoryOps.moveToSlot(List.of(bag),furnace,1,s -> fuel(level,s) && !ingredient(role,s),Math.max(0,FUEL_LOAD-furnace.getItem(1).getCount()));
            furnace.setChanged();
            return collected;
        }
        if(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
            int collected=0;
            // Campfires eject their own cooked stacks. Collect them before supplying the next batch.
            for(ItemEntity drop:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5,1,1.5),
                    e -> e.isAlive() && cookedFood(e.getItem()))) {
                collected+=drop.getItem().getCount(); bag.offer(drop.getItem()); drop.discard();
            }
            for(int i=0;i<4;i++) {
                ItemStack held=InventoryOps.takeOne(List.of(bag),s -> input(level,role,pos,s));
                if(held.isEmpty()) break;
                boolean placed=campfire.placeFood(level,worker,held);
                if(!held.isEmpty()) bag.offer(held);
                if(!placed) break;
            }
            return collected;
        }
        return 0;
    }
    private static boolean belowTarget(ServerLevel level,Settlement town,StructureRole role,BlockPos pos,ItemStack ingredient) {
        if(town==null || role!=StructureRole.SMELTERY) return true;
        var recipe=level.getServer().getRecipeManager().getRecipeFor(type(level,pos),new SingleRecipeInput(ingredient),level);
        if(recipe.isEmpty()) return false;
        ItemStack output=recipe.get().value().assemble(new SingleRecipeInput(ingredient));
        int target=AlloyWorkshop.target(town,output.getItem());
        return InventoryOps.count(SettlementService.townStorage(level,town),s -> s.is(output.getItem()))<target;
    }
}
