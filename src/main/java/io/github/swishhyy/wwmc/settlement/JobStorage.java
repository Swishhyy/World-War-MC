package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FuelValues;

/** Job barrels are the only production interface. Couriers move real supplies and goods through the warehouse. */
public final class JobStorage {
    public static final int SUPPORT_RESERVE=16,SAPLING_RESERVE=32,FUEL_RESERVE=8,WHEAT_RESERVE=9,LAPIS_RESERVE=9;
    public static final int COLLECT_LOAD=32,PANTRY_LOW=16;
    public record Pickup(Container container,int slot,int amount) {}
    public record Supplies(FuelValues fuels,RecipeManager recipes,Level level) {
        public static Supplies of(ServerLevel level) { return new Supplies(level.fuelValues(),level.getServer().getRecipeManager(),level); }
        public boolean fuel(ItemStack stack) { return ProcessingService.fuel(fuels,stack); }
        public boolean ingredient(StructureRole role,ItemStack stack) {
            if(!ProcessingService.ingredient(role,stack)) return false;
            var input=new SingleRecipeInput(stack);
            return role==StructureRole.SMELTERY ? recipes.getRecipeFor(RecipeType.SMELTING,input,level).isPresent()
                    : recipes.getRecipeFor(RecipeType.SMOKING,input,level).isPresent();
        }
        public Workshop.Recipes crafting() { return new Workshop.Recipes(recipes.recipeMap(),level); }
    }
    private record Demand(Predicate<ItemStack> accepts,int target) {}
    private JobStorage() {}
    public static boolean tool(StructureRole role,ItemStack stack) {
        return role==StructureRole.FARM && stack.is(ItemTags.HOES) || role==StructureRole.GATHERER && stack.is(ItemTags.SHOVELS) || role==StructureRole.LUMBER && stack.is(ItemTags.AXES) || role.excavates() && stack.is(ItemTags.PICKAXES)
                || (role==StructureRole.HUNTER || role==StructureRole.ANIMAL_KEEPER) && AnimalWork.weapon(stack)
                || role==StructureRole.FISHERMAN && stack.is(Items.FISHING_ROD)
                || role==StructureRole.BUTCHER && stack.is(ItemTags.AXES);
    }
    private static boolean supply(Supplies supplies,Settlement town,StructureRole role,ItemStack stack) {
        if(role==StructureRole.HOSPITAL) return stack.is(Items.PAPER) || FoodHealing.food(stack);
        if(role.processes()) return ProcessingService.supply(supplies.fuels(),role,stack);
        if(role.animalJob()) return AnimalWork.supply(role,stack);
        if(role==StructureRole.ENCHANTER) return Enchanting.lapis(stack) || Enchanting.candidate(stack);
        if(role==StructureRole.BLACKSMITH) return BlacksmithRepair.damaged(stack) || repairMaterial(stack) || forgeInput(supplies,town,stack);
        if(role==StructureRole.GUARD) return !GuardEquipment.worn(stack) && (GuardWeapons.weapon(stack) || GuardWeapons.arrow(stack)
                || Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> GuardEquipment.armor(stack,slot)));
        return role==StructureRole.CRAFTSMAN && (!Workshop.product(town,stack) || maintenanceInput(supplies,town,stack));
    }
    public static List<Pickup> collectable(Supplies supplies,Settlement town,StructureRole role,List<Container> barrels) {
        int support=SUPPORT_RESERVE,saplings=SAPLING_RESERVE,processingFuel=FUEL_RESERVE,meals=role.foodJob() ? 0 : FoodSharing.PERSONAL_LIMIT;
        List<Pickup> result=new ArrayList<>();
        List<Container> warehouse=role==StructureRole.BLACKSMITH && town!=null && supplies.level() instanceof ServerLevel level
                ? SettlementService.storage(level,town) : List.of();
        List<Demand> smithNeeds=role==StructureRole.BLACKSMITH || role==StructureRole.SMELTERY ? demands(supplies,town,role,null,barrels,warehouse) : List.of();
        Map<Demand,Integer> reserved=new IdentityHashMap<>();
        for(Container barrel:barrels) for(int slot=0;slot<barrel.getContainerSize();slot++) {
            ItemStack stack=barrel.getItem(slot);
            if(stack.isEmpty() || tool(role,stack) && !GuardEquipment.worn(stack)
                    || role!=StructureRole.BLACKSMITH && !(role==StructureRole.SMELTERY && AlloyWorkshop.material(stack)) && supply(supplies,town,role,stack)
                        && !(role.processes() && supplies.fuel(stack) && !supplies.ingredient(role,stack)) || role==StructureRole.BLACKSMITH && BlacksmithRepair.damaged(stack)) continue;
            int keep=0;
            if(role.processes() && supplies.fuel(stack) && !supplies.ingredient(role,stack)) {
                keep=Math.min(processingFuel,stack.getCount()); processingFuel-=keep;
            }
            else if(role==StructureRole.BLACKSMITH || role==StructureRole.SMELTERY && AlloyWorkshop.material(stack)) {
                for(var demand:smithNeeds) if(demand.accepts().test(stack)) keep=Math.max(keep,Math.min(stack.getCount(),Math.max(0,demand.target()-reserved.getOrDefault(demand,0))));
                for(var demand:smithNeeds) if(demand.accepts().test(stack)) reserved.merge(demand,keep,Integer::sum);
                if(FoodHealing.food(stack)) { keep=Math.max(keep,Math.min(meals,stack.getCount())); meals-=Math.min(meals,stack.getCount()); }
            }
            else if(role.excavates() && ExcavationService.supportMaterial(stack)) { keep=Math.min(support,stack.getCount()); support-=keep; }
            else if(role==StructureRole.LUMBER && stack.is(ItemTags.SAPLINGS)) { keep=Math.min(saplings,stack.getCount()); saplings-=keep; }
            else if(FoodHealing.food(stack)) { keep=Math.min(meals,stack.getCount()); meals-=keep; }
            if(stack.getCount()>keep) result.add(new Pickup(barrel,slot,stack.getCount()-keep));
        }
        return result;
    }
    public static int goods(List<Pickup> pickups) { return pickups.stream().mapToInt(Pickup::amount).sum(); }
    public static boolean food(List<Pickup> pickups) {
        return pickups.stream().anyMatch(p -> FoodHealing.food(p.container().getItem(p.slot()))
                || Carcasses.carcass(p.container().getItem(p.slot())) || ProcessingService.rawFood(p.container().getItem(p.slot())));
    }
    public static boolean worthCollecting(List<Pickup> pickups,int freeSlots,int pantry) {
        int goods=goods(pickups);
        return goods>=COLLECT_LOAD || goods>0 && (freeSlots<=2 || pantry<PANTRY_LOW && food(pickups));
    }
    public static int collect(Supplies supplies,Settlement town,StructureRole role,List<Container> barrels,CitizenInventory bag) {
        int moved=0;
        for(Pickup pickup:collectable(supplies,town,role,barrels)) {
            if(bag.needsDelivery()) break;
            ItemStack taken=pickup.container().removeItem(pickup.slot(),pickup.amount());
            moved+=taken.getCount(); bag.offer(taken); pickup.container().setChanged();
        }
        return moved;
    }
    public static int freeSlots(List<Container> containers) {
        int free=0;
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) if(container.getItem(slot).isEmpty()) free++;
        return free;
    }
    private static boolean forgeInput(Supplies supplies,Settlement town,ItemStack stack) {
        return town!=null && supplies.level() instanceof ServerLevel level && town.progress.forgeOrders.stream().filter(o -> o.target()>0)
                .flatMap(o -> ForgeWorkshop.plans(level,o).stream()).anyMatch(p -> p.uses(stack));
    }
    private static boolean repairMaterial(ItemStack stack) {
        return stack.is(io.github.swishhyy.wwmc.WWMC.STEEL_INGOT.get()) || stack.is(io.github.swishhyy.wwmc.WWMC.BRONZE_INGOT.get()) || stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.GOLD_INGOT) || stack.is(Items.DIAMOND)
                || stack.is(Items.NETHERITE_INGOT) || stack.is(Items.LEATHER) || stack.is(ItemTags.PLANKS) || stack.is(Items.COBBLESTONE);
    }
    public static boolean input(Supplies supplies,StructureRole role,ItemStack stack) { return input(supplies,null,role,stack); }
    public static boolean input(Supplies supplies,Settlement town,StructureRole role,ItemStack stack) {
        if(stack.isEmpty()) return false;
        if(role==StructureRole.HOSPITAL) return stack.is(Items.PAPER) || FoodHealing.food(stack);
        if(tool(role,stack)) return !GuardEquipment.worn(stack);
        if(role.animalJob()) return AnimalWork.supply(role,stack);
        if(role==StructureRole.ENCHANTER) return Enchanting.lapis(stack) || Enchanting.candidate(stack);
        if(role==StructureRole.BLACKSMITH) return BlacksmithRepair.damaged(stack) || repairMaterial(stack) || forgeInput(supplies,town,stack);
        if(role==StructureRole.GUARD) return supply(supplies,town,role,stack);
        if(role==StructureRole.CRAFTSMAN && town!=null) return maintenanceInput(supplies,town,stack) || town.craftOrders.stream().filter(o -> o.target()>0)
                .flatMap(o -> Workshop.plans(supplies.crafting(),o).stream()).anyMatch(p -> p.uses(stack));
        if(role.excavates() && ExcavationService.supportMaterial(stack) || role==StructureRole.LUMBER && stack.is(ItemTags.SAPLINGS)) return true;
        return role.processes() && (supplies.fuel(stack) || role==StructureRole.SMELTERY && AlloyWorkshop.material(stack) || role==StructureRole.COOK && stack.is(Items.WHEAT) || supplies.ingredient(role,stack));
    }
    /** Supply one usable tool per assigned worker, counting tools already in their hands or bags. */
    private static int toolTarget(Supplies supplies,Settlement town,Station station,StructureRole role) {
        if(town==null || station==null) return role==StructureRole.MINE ? 1 : 2;
        int target=Math.max(1,Math.min(town.jobs.assigned(station.position()),SettlementService.workerLimit(town,station)));
        if(supplies.level() instanceof ServerLevel level) for(UUID id:town.citizens) {
            if(!station.position().equals(town.jobs.home(id)) || !(level.getEntity(id) instanceof CitizenEntity worker) || !worker.isAlive()) continue;
            if(tool(role,worker.getMainHandItem()) && !GuardEquipment.worn(worker.getMainHandItem())) target--;
            target-=InventoryOps.count(List.of(worker.bag()),s -> tool(role,s) && !GuardEquipment.worn(s));
        }
        return Math.max(0,target);
    }
    private static int fuelTarget(Settlement town) {
        if(town==null) return FUEL_RESERVE*2;
        long consumers=town.stations.stream().filter(s -> s.role().processes() || s.role()==StructureRole.BLACKSMITH).count();
        // Shared fuel must reach the smith as well as the smelter; one station cannot hoard a whole small delivery.
        return Math.max(2,FUEL_RESERVE*2/(int)Math.max(1,consumers));
    }
    private static List<Demand> demands(Supplies supplies,Settlement town,StructureRole role,Station station,List<Container> barrels,List<Container> warehouse) {
        List<Demand> result=new ArrayList<>();
        Predicate<ItemStack> tools=s -> tool(role,s) && !GuardEquipment.worn(s);
        if(role.excavates() || role==StructureRole.FARM || role==StructureRole.GATHERER || role==StructureRole.LUMBER || role.animalJob()) {
            int target=toolTarget(supplies,town,station,role);
            if(target>0) result.add(new Demand(tools,target));
        }
        if(role.processes()) {
            result.add(new Demand(s -> !s.is(Items.WHEAT) && supplies.ingredient(role,s),ProcessingService.INPUT_LOAD*2));
            result.add(new Demand(s -> supplies.fuel(s) && (!supplies.ingredient(role,s) || role==StructureRole.SMELTERY && ForgeWorkshop.fuel(s)),fuelTarget(town)));
            if(role==StructureRole.COOK) result.add(new Demand(s -> s.is(Items.WHEAT),WHEAT_RESERVE*2));
            if(role==StructureRole.SMELTERY && town!=null && supplies.level() instanceof ServerLevel level
                    && town.stations.stream().filter(s -> s.role()==StructureRole.SMELTERY && (station==null || s.position().equals(station.position()))).anyMatch(s -> SettlementService.processingDevices(level,town,s).stream()
                            .anyMatch(p -> level.getBlockState(p).is(io.github.swishhyy.wwmc.WWMC.ALLOY_FURNACE.get())))) {
                if(Research.has(town,"bronze_age")) { result.add(new Demand(AlloyWorkshop::copper,12)); result.add(new Demand(AlloyWorkshop::tin,4)); }
                if(Research.has(town,"steel_working")) result.add(new Demand(AlloyWorkshop::iron,8));
            }
        }
        if(role.excavates()) result.add(new Demand(ExcavationService::supportMaterial,SUPPORT_RESERVE));
        if(role==StructureRole.LUMBER) result.add(new Demand(s -> s.is(ItemTags.SAPLINGS),SAPLING_RESERVE));
        if(role==StructureRole.BUTCHER) result.add(new Demand(Carcasses::carcass,16));
        if(role==StructureRole.ANIMAL_KEEPER) {
            boolean scarce=town!=null && FoodSharing.scarce(InventoryOps.count(warehouse,FoodHealing::food),town.citizens.size());
            result.add(new Demand(s -> AnimalWork.feed(s) && (!scarce || !FoodHealing.food(s)),AnimalWork.FEED_LOAD*2));
        }
        if(role==StructureRole.ENCHANTER) {
            result.add(new Demand(Enchanting::lapis,LAPIS_RESERVE*2)); result.add(new Demand(Enchanting::candidate,2));
        }
        if(role==StructureRole.HOSPITAL) {
            boolean scarce=town!=null && FoodSharing.scarce(InventoryOps.count(warehouse,FoodHealing::food),town.citizens.size());
            result.add(new Demand(FoodHealing::food,scarce ? 2 : 16)); result.add(new Demand(s -> s.is(Items.PAPER),16));
        }
        if(role==StructureRole.GUARD) {
            result.add(new Demand(s -> GuardWeapons.melee(s) && !GuardEquipment.worn(s),2));
            result.add(new Demand(s -> GuardWeapons.bow(s) && !GuardEquipment.worn(s),1));
            result.add(new Demand(GuardWeapons::arrow,32));
            for(var slot:GuardEquipment.ARMOR) result.add(new Demand(s -> GuardEquipment.armor(s,slot) && !GuardEquipment.worn(s),1));
        }
        if(role==StructureRole.BLACKSMITH) {
            List<Container> stock=new ArrayList<>(barrels); stock.addAll(warehouse);
            if(town!=null && supplies.level() instanceof ServerLevel level) for(var order:town.progress.forgeOrders) {
                if(order.target()<=0 || Workshop.stock(stock,order)>=order.target()) continue;
                for(var plan:ForgeWorkshop.plans(level,order)) if(AgeProgression.allowed(town,plan.result())) {
                    Map<List<Item>,Integer> amounts=new LinkedHashMap<>();
                    Map<List<Item>,Ingredient> kinds=new LinkedHashMap<>();
                    for(var ingredient:plan.ingredients()) {
                        List<Item> key=ingredient.items().map(net.minecraft.core.Holder::value).toList();
                        amounts.merge(key,1,Integer::sum); kinds.putIfAbsent(key,ingredient);
                    }
                    for(var entry:amounts.entrySet()) result.add(new Demand(kinds.get(entry.getKey())::test,entry.getValue()*2));
                    result.add(new Demand(ForgeWorkshop::fuel,4));
                }
            }
            result.add(new Demand(BlacksmithRepair::damaged,2));
            result.add(new Demand(material -> stock.stream().anyMatch(box -> {
                for(int slot=0;slot<box.getContainerSize();slot++) if(BlacksmithRepair.damaged(box.getItem(slot)) && BlacksmithRepair.material(box.getItem(slot),material)) return true;
                return false;
            }),8));
        }
        if(role==StructureRole.CRAFTSMAN && town!=null) {
            if(supplies.level() instanceof ServerLevel level) {
                Set<String> seen=new HashSet<>();
                for(var material:TrapService.neededMaterials(level,town))
                    if(seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material.icon().getItem()).toString()))
                        result.add(new Demand(material.accepts(),Math.max(4,material.count()*2)));
            }
            List<Container> stock=new ArrayList<>(barrels); stock.addAll(warehouse);
            var permitted=town.craftOrders.stream().filter(o -> !ForgeWorkshop.forged(new ItemStack(o.resolve()))
                    && AgeProgression.allowed(town,new ItemStack(o.resolve()))).toList();
            Workshop.Job job=Workshop.choose(supplies.crafting(),permitted,stock,stock);
            if(job!=null) for(Ingredient ingredient:job.plan().ingredients()) result.add(new Demand(ingredient,Workshop.TRIP_BATCHES));
        }
        return result;
    }
    private static boolean maintenanceInput(Supplies supplies,Settlement town,ItemStack stack) {
        if(town==null || town.progress.traps.isEmpty()) return false;
        return stack.is(ItemTags.PLANKS) || stack.is(Items.STRING) || stack.is(Items.IRON_INGOT)
                || stack.is(io.github.swishhyy.wwmc.WWMC.BRONZE_INGOT.get());
    }
    public static boolean needsSupplies(Supplies supplies,StructureRole role,List<Container> barrels,List<Container> warehouse) { return needsSupplies(supplies,null,role,barrels,warehouse); }
    public static boolean needsSupplies(Supplies supplies,Settlement town,StructureRole role,List<Container> barrels,List<Container> warehouse) {
        return needsSupplies(supplies,town,role,null,barrels,warehouse);
    }
    public static boolean needsSupplies(Supplies supplies,Settlement town,Station station,List<Container> barrels,List<Container> warehouse) {
        return needsSupplies(supplies,town,station.role(),station,barrels,warehouse);
    }
    private static boolean needsSupplies(Supplies supplies,Settlement town,StructureRole role,Station station,List<Container> barrels,List<Container> warehouse) {
        if(freeSlots(barrels)<2) return false;
        return demands(supplies,town,role,station,barrels,warehouse).stream()
                .anyMatch(d -> InventoryOps.count(barrels,d.accepts())<d.target()/2+1 && InventoryOps.count(warehouse,d.accepts())>0);
    }
    public static int load(Supplies supplies,StructureRole role,List<Container> barrels,List<Container> warehouse,CitizenInventory bag) { return load(supplies,null,role,barrels,warehouse,bag); }
    public static int load(Supplies supplies,Settlement town,StructureRole role,List<Container> barrels,List<Container> warehouse,CitizenInventory bag) {
        return load(supplies,town,role,null,barrels,warehouse,bag);
    }
    public static int load(Supplies supplies,Settlement town,Station station,List<Container> barrels,List<Container> warehouse,CitizenInventory bag) {
        return load(supplies,town,station.role(),station,barrels,warehouse,bag);
    }
    private static int load(Supplies supplies,Settlement town,StructureRole role,Station station,List<Container> barrels,List<Container> warehouse,CitizenInventory bag) {
        int moved=0;
        for(Demand demand:demands(supplies,town,role,station,barrels,warehouse)) {
            int remaining=demand.target()-InventoryOps.count(barrels,demand.accepts())-InventoryOps.count(List.of(bag),demand.accepts());
            while(remaining-->0 && !bag.needsDelivery()) {
                ItemStack next=InventoryOps.takeOne(warehouse,demand.accepts());
                if(next.isEmpty()) break;
                bag.offer(next); moved++;
            }
        }
        return moved;
    }
}
