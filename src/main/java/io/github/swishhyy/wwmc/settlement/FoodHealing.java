package io.github.swishhyy.wwmc.settlement;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/** Safe meal selection keeps its bowl or bottle; citizens use meals for hunger, not healing. */
public final class FoodHealing {
    public static final int COOLDOWN=600;
    private FoodHealing() {}
    public static boolean food(ItemStack stack) {
        var nutrition=stack.get(DataComponents.FOOD);
        return !stack.isEmpty() && nutrition!=null && nutrition.nutrition()>0 && !stack.is(Items.ROTTEN_FLESH)
                && !stack.is(Tags.Items.FOODS_RAW_MEAT) && !stack.is(Tags.Items.FOODS_RAW_FISH)
                && !stack.is(Items.SPIDER_EYE) && !stack.is(Items.POISONOUS_POTATO) && !stack.is(Items.PUFFERFISH);
    }
    public static float healing(ItemStack meal,float health,float maximum) {
        if(!food(meal)) return 0;
        return Math.max(0,Math.min(maximum-health,meal.get(DataComponents.FOOD).nutrition()));
    }
    public static boolean due(int rationTicks,int cooldown,float health,float maximum) {
        return rationTicks<=0 || cooldown<=0 && health<maximum;
    }
    public static ItemStack take(List<Container> storage,Consumer<ItemStack> remainder) {
        return take(storage,remainder,List.of());
    }
    /** Prefer an uneaten or less recently eaten meal, rather than always emptying the first food slot. */
    public static ItemStack take(List<Container> storage,Consumer<ItemStack> remainder,List<String> recent) {
        ItemStack meal=InventoryOps.takeBest(storage,FoodHealing::food,s -> -(recent.lastIndexOf(MealVariety.id(s))+1));
        var container=meal.get(DataComponents.USE_REMAINDER);
        if(container!=null) remainder.accept(container.convertInto().create());
        return meal;
    }
}
