package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Native tool speed includes bronze, steel and other mods' tools. Miners already use block break speed. */
public final class WorkerTools {
    private WorkerTools() {}
    public static int bonus(StructureRole role,ItemStack tool) {
        if(role==null || tool.isEmpty()) return 0;
        var block=switch(role) {
            case LUMBER -> tool.is(ItemTags.AXES) ? Blocks.OAK_LOG : null;
            case FARM -> tool.is(ItemTags.HOES) ? Blocks.HAY_BLOCK : null;
            case GATHERER -> tool.is(ItemTags.SHOVELS) ? Blocks.DIRT : null;
            default -> null;
        };
        if(block==null) return 0;
        return Math.clamp((int)Math.round((Math.sqrt(tool.getDestroySpeed(block.defaultBlockState())/2.0)-1)*40),0,50);
    }
}
