package io.github.swishhyy.wwmc.settlement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Consent permits narrowly scoped player combat. It never opens storage, block placement, citizen attacks or research. */
public final class MultiplayerCombat {
    public static boolean claimException(ServerLevel level,Player attacker,net.minecraft.world.entity.Entity target) {
        return target instanceof Player defender && OutpostContests.allows(level,attacker,defender);
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD) || level.getGameTime()%20!=0) return;
        PlayerContracts.cleanup(level); OutpostContests.tick(level);
    }
}
