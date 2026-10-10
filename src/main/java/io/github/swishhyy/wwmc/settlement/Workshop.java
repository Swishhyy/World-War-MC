package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * Craftsman orders the owner teaches by example: keep a stock of an item by following Minecraft's own crafting-table
 * recipes for it. Materials are real stored items, each batch is checked against its recipe before it is made, and
 * a recipe's container items (buckets, bottles) come back.
 */
public final class Workshop {
    public static final int MAX_ORDERS=64,MAX_TARGET=256,TRIP_BATCHES=8;
    /** Keep {@code target} of {@code item} in town storage. A planks order accepts planks of any wood. */
    public record Order(String item,int target) {
        public static final Codec<Order> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(Order::item),
            Codec.INT.optionalFieldOf("target",16).forGetter(Order::target)
        ).apply(i,Order::new));
        public Order { target=Math.clamp(target,0,MAX_TARGET); }
        /** The ordered item; air when its mod is gone, which leaves the order idle instead of breaking the save. */
        public Item resolve() {
            Identifier id=Identifier.tryParse(item);
            Item resolved=id==null ? null : BuiltInRegistries.ITEM.getValue(id);
            return resolved==null ? Items.AIR : resolved;
        }
        public boolean anyWood() { return resolve().getDefaultInstance().is(ItemTags.PLANKS); }
        public boolean product(ItemStack stack) { return !stack.isEmpty() && resolve()!=Items.AIR && (stack.is(resolve()) || anyWood() && stack.is(ItemTags.PLANKS)); }
        public Order withTarget(int amount) { return new Order(item,amount); }
    }
    /** A crafting-table recipe as a worker follows it: one ingredient per used grid cell. Shapeless plans have no width. */
    public record Plan(RecipeHolder<CraftingRecipe> holder,List<Ingredient> ingredients,IntList slots,int width,int height,ItemStack result) {
        public boolean uses(ItemStack stack) { return ingredients.stream().anyMatch(ingredient -> ingredient.test(stack)); }
        /** The crafting grid for one batch, from one item per ingredient; null when the items cannot fill this shape. */
        public CraftingInput input(List<ItemStack> items) {
            if(items.size()!=ingredients.size()) return null;
            List<ItemStack> grid=new ArrayList<>();
            if(width==0) {
                int columns=Math.min(3,items.size()),rows=(items.size()+columns-1)/columns;
                grid.addAll(items);
                while(grid.size()<columns*rows) grid.add(ItemStack.EMPTY);
                return CraftingInput.of(columns,rows,grid);
            }
            if(width*height!=slots.size()) return null;
            for(int cell=0;cell<slots.size();cell++) grid.add(slots.getInt(cell)<0 ? ItemStack.EMPTY : items.get(slots.getInt(cell)));
            return CraftingInput.of(width,height,grid);
        }
    }
    public record Job(Order order,Plan plan) {}
    /** The recipes a workshop follows. The level is only passed on to recipe checks and may be absent in tests. */
    public record Recipes(RecipeMap map,Level level) {
        public static Recipes of(ServerLevel level) { return new Recipes(level.getServer().getRecipeManager().recipeMap(),level); }
    }
    /** Pre-learned orders that keep workers equipped and supplied, in priority order: tools, then building goods. */
    private static final List<String[]> DEFAULTS=List.of(
        new String[]{"stone_pickaxe","stone_pickaxe","2"},new String[]{"stone_axe","stone_axe","2"},new String[]{"stone_sword","stone_sword","2"},
        new String[]{"bow","bow","1"},new String[]{"arrows","arrow","64"},new String[]{"torches","torch","32"},new String[]{"ladders","ladder","32"},
        new String[]{"sticks","stick","32"},new String[]{"planks","oak_planks","64"});
    private static RecipeMap indexed;
    private static Map<Item,List<Plan>> index=Map.of();
    private Workshop() {}
    /** Orders for a new town, or an older one; an order switched off before orders were learnable keeps a target of zero. */
    public static List<Order> defaults(Collection<String> disabled) {
        List<Order> orders=new ArrayList<>();
        for(String[] entry:DEFAULTS) orders.add(new Order("minecraft:"+entry[1],disabled.contains(entry[0]) ? 0 : Integer.parseInt(entry[2])));
        return orders;
    }
    /** Shaped and shapeless crafting-table recipes by result. Special recipes such as dyeing or map copying are left out. */
    public static Map<Item,List<Plan>> plans(Recipes recipes) {
        if(recipes.map()!=indexed) {
            Map<Item,List<Plan>> built=new HashMap<>();
            for(RecipeHolder<CraftingRecipe> holder:recipes.map().byType(RecipeType.CRAFTING)) {
                Plan plan;
                // One broken recipe from a mod or datapack must not stop the workshop.
                try { plan=plan(recipes.level(),holder); }
                catch(RuntimeException e) {
                    WWMC.LOGGER.error("[WWMC][recipe-error] Cannot index crafting recipe {}; skipping it for craftsmen",holder.id(),e);
                    plan=null;
                }
                if(plan!=null) built.computeIfAbsent(plan.result().getItem(),item -> new ArrayList<>()).add(plan);
            }
            index=built; indexed=recipes.map();
        }
        return index;
    }
    private static Plan plan(Level level,RecipeHolder<CraftingRecipe> holder) {
        CraftingRecipe recipe=holder.value();
        int width=0,height=0;
        // Exact classes only: subclasses such as map extending check more than their ingredients.
        if(recipe.getClass()==ShapedRecipe.class) {
            ShapedCraftingRecipeDisplay shape=null;
            for(var display:recipe.display()) if(display instanceof ShapedCraftingRecipeDisplay shaped) { shape=shaped; break; }
            if(shape==null) return null;
            width=shape.width(); height=shape.height();
        } else if(recipe.getClass()!=ShapelessRecipe.class) return null;
        PlacementInfo placement=recipe.placementInfo();
        if(placement.isImpossibleToPlace() || placement.ingredients().isEmpty()) return null;
        List<ItemStack> sample=new ArrayList<>();
        for(Ingredient ingredient:placement.ingredients()) {
            var item=ingredient.items().findFirst();
            if(item.isEmpty()) return null;
            sample.add(new ItemStack(item.get().value()));
        }
        Plan draft=new Plan(holder,List.copyOf(placement.ingredients()),new IntArrayList(placement.slotsToIngredientIndex()),width,height,ItemStack.EMPTY);
        CraftingInput input=draft.input(sample);
        // Recipes with stricter ingredients than their first listed item (custom NeoForge ingredients) are skipped.
        if(input==null || !recipe.matches(input,level)) return null;
        ItemStack result=recipe.assemble(input);
        return result.isEmpty() ? null : new Plan(holder,draft.ingredients(),draft.slots(),width,height,result.copy());
    }
    /** Recipes for an order's product; a planks order may use the recipe for any wood. */
    public static List<Plan> plans(Recipes recipes,Order order) {
        Map<Item,List<Plan>> all=plans(recipes);
        if(!order.anyWood()) return all.getOrDefault(order.resolve(),List.of());
        List<Plan> result=new ArrayList<>();
        for(var entry:all.entrySet()) if(entry.getKey().getDefaultInstance().is(ItemTags.PLANKS)) result.addAll(entry.getValue());
        return result;
    }
    public static boolean product(Settlement town,ItemStack stack) { return town.craftOrders.stream().anyMatch(order -> order.product(stack)); }
    public static int stock(List<Container> storage,Order order) { return InventoryOps.count(storage,order::product); }
    /**
     * The first order, in the owner's priority order, that is below its target and has a recipe the sources hold
     * materials for. {@code stock} is where finished goods are counted.
     */
    public static Job choose(Recipes recipes,List<Order> orders,List<Container> stock,List<Container> sources) {
        for(Order order:orders) {
            if(order.target()<=0 || order.resolve()==Items.AIR || stock(stock,order)>=order.target()) continue;
            for(Plan plan:plans(recipes,order)) if(batches(recipes,orders,order,plan,sources,1)>0) return new Job(order,plan);
        }
        return null;
    }
    /**
     * Whole batches the sources hold materials for. When two orders make each other (ingots and blocks), each only
     * draws on the other's stock above its target, so they never convert back and forth.
     */
    public static int batches(Recipes recipes,List<Order> orders,Order order,Plan plan,List<Container> sources,int limit) {
        List<ItemStack> pool=pool(sources);
        for(Order other:orders) if(other!=order && cyclic(recipes,order,plan,other)) reserve(pool,other);
        int made=0;
        while(made<limit && take(pool,plan.ingredients())) made++;
        return made;
    }
    private static List<ItemStack> pool(List<Container> sources) {
        List<ItemStack> pool=new ArrayList<>();
        for(Container container:sources) for(int slot=0;slot<container.getContainerSize();slot++)
            if(!container.getItem(slot).isEmpty()) pool.add(container.getItem(slot).copy());
        return pool;
    }
    private static boolean cyclic(Recipes recipes,Order order,Plan plan,Order other) {
        ItemStack theirs=other.resolve().getDefaultInstance(),ours=order.resolve().getDefaultInstance();
        return !theirs.isEmpty() && plan.uses(theirs) && plans(recipes,other).stream().anyMatch(p -> p.uses(ours));
    }
    private static void reserve(List<ItemStack> pool,Order order) {
        int left=order.target();
        for(ItemStack stack:pool) if(left>0 && order.product(stack)) { int kept=Math.min(left,stack.getCount()); stack.shrink(kept); left-=kept; }
    }
    /** Remove one batch from the pool, one item per ingredient in order; on a shortfall nothing is removed. */
    private static boolean take(List<ItemStack> pool,List<Ingredient> ingredients) {
        List<ItemStack> used=new ArrayList<>();
        for(Ingredient ingredient:ingredients) {
            ItemStack match=null;
            for(ItemStack stack:pool) if(!stack.isEmpty() && ingredient.test(stack)) { match=stack; break; }
            if(match==null) { used.forEach(stack -> stack.grow(1)); return false; }
            match.shrink(1); used.add(match);
        }
        return true;
    }
    /** Move materials for up to {@link #TRIP_BATCHES} batches, no more than the shortage needs, into the bag. */
    public static int fetch(Recipes recipes,List<Order> orders,Job job,List<Container> stock,List<Container> sources,Container bag) {
        int perBatch=Math.max(1,job.plan().result().getCount());
        int needed=(job.order().target()-stock(stock,job.order())+perBatch-1)/perBatch;
        int batches=Math.min(needed,batches(recipes,orders,job.order(),job.plan(),sources,TRIP_BATCHES));
        for(int batch=0;batch<batches;batch++) for(Ingredient ingredient:job.plan().ingredients()) {
            ItemStack rest=InventoryOps.insert(bag,InventoryOps.takeOne(sources,ingredient));
            // A full bag returns the material rather than losing it.
            for(Container container:sources) rest=InventoryOps.insert(container,rest);
        }
        return Math.max(0,batches);
    }
    public static boolean ready(Container bag,Plan plan) { return take(pool(List.of(bag)),plan.ingredients()); }
    /**
     * Craft one batch from the bag. The product and any container items go to {@code output}; if the batch no longer
     * matches its recipe the materials go back instead. Returns whether something was made.
     */
    public static boolean craft(Level level,Container bag,Plan plan,Consumer<ItemStack> output) {
        List<ItemStack> items=new ArrayList<>();
        for(Ingredient ingredient:plan.ingredients()) {
            ItemStack item=InventoryOps.takeOne(List.of(bag),ingredient);
            if(item.isEmpty()) { items.forEach(output); return false; }
            items.add(item);
        }
        CraftingInput input=plan.input(items);
        if(input==null || !plan.holder().value().matches(input,level)) { items.forEach(output); return false; }
        ItemStack made=plan.holder().value().assemble(input);
        if(made.isEmpty()) { items.forEach(output); return false; }
        output.accept(made);
        for(ItemStack item:items) {
            var remainder=item.getCraftingRemainder();
            if(remainder!=null) output.accept(remainder.create());
        }
        return true;
    }
    /** Teach the workshop an item from an example; returns what happened, for the owner. */
    public static String learn(Recipes recipes,Settlement town,ItemStack example) {
        if(example.isEmpty()) return "Place an item in the slot to teach its recipe";
        Order order=new Order(BuiltInRegistries.ITEM.getKey(example.getItem()).toString(),example.getMaxStackSize()==1 ? 1 : 16);
        String name=example.getHoverName().getString();
        if(ForgeWorkshop.forged(example)) return "Order "+name+" from the blacksmith in Production / Forge";
        if(town.craftOrders.stream().anyMatch(o -> o.resolve()==example.getItem() || o.anyWood() && order.anyWood())) return "Craftsmen already make "+name;
        if(plans(recipes,order).isEmpty()) return "No crafting-table recipe makes "+name;
        if(town.craftOrders.size()>=MAX_ORDERS) return "The workshop knows "+MAX_ORDERS+" recipes; forget one first";
        town.craftOrders.add(order);
        return "Learned "+name+(order.anyWood() ? " (any wood)" : "");
    }
    /** Index of the order for this item id, or -1. */
    public static int find(Settlement town,String item) {
        for(int i=0;i<town.craftOrders.size();i++) if(town.craftOrders.get(i).item().equals(item)) return i;
        return -1;
    }
}
