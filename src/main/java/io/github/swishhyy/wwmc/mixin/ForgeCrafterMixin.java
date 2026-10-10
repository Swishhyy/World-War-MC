package io.github.swishhyy.wwmc.mixin;

import io.github.swishhyy.wwmc.settlement.ForgeWorkshop;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A redstone crafter cannot bypass the settlement forge. Returning no recipe also keeps its ingredients intact. */
@Mixin(CrafterBlock.class)
public abstract class ForgeCrafterMixin {
    @Inject(method="getPotentialResults",at=@At("RETURN"),cancellable=true)
    private static void wwmc$forgeOnly(ServerLevel level,CraftingInput input,CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> ci) {
        var recipe=ci.getReturnValue();
        if(recipe.isPresent() && ForgeWorkshop.forged(recipe.get().value().assemble(input))) ci.setReturnValue(Optional.empty());
    }
}
