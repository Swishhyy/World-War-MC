package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/** One small birth chance per loaded minute, with real housing, food and native villager childhood. */
public final class PopulationGrowth {
    public static final int ATTEMPT_TICKS=1200,CHILD_TICKS=24000,PARENT_COOLDOWN=6000,MEALS=6;
    private PopulationGrowth() {}
    public static boolean adultReady(CitizenEntity citizen) {
        return citizen.isAlive() && !citizen.isNoAi() && citizen.getAge()==0 && citizen.happiness()>=75
                && MealVariety.distinct(citizen.recentMeals())>=3 && citizen.mealTicks()>0
                && citizen.getHealth()>=citizen.getMaxHealth()*0.9F && !citizen.recovering() && !citizen.inCombat();
    }
    public static String pause(ServerLevel level,Settlement town) {
        if(!town.progress.growthEnabled) return "Population growth paused";
        if(DefenseService.alarmed(town)) return "Waiting until the town is safe";
        if(town.citizens.size()>=SettlementService.populationLimit(town)) return "Population limit reached";
        if(SettlementService.housingBeds(level,town).size()<=town.citizens.size()) return "Needs a spare housing bed";
        if(InventoryOps.count(SettlementService.storage(level,town),FoodHealing::food)<reserve(town)) return "Needs six spare meals plus a food reserve";
        if(CitizenWellbeing.loaded(level,town).stream().filter(PopulationGrowth::adultReady).count()<2) return "Needs two healthy, happy adults with varied meals";
        return "Ready · 5% chance each loaded minute";
    }
    private static int reserve(Settlement town) { return (town.citizens.size()+1)*2+MEALS; }
    public static void tick(ServerLevel level,Settlement town,RandomSource random) {
        town.progress.children.removeIf(id -> !town.citizens.contains(id));
        if(CitizenWellbeing.loaded(level,town).isEmpty()) { town.progress.lastGrowthTick=Long.MIN_VALUE; return; }
        long now=level.getGameTime();
        if(town.progress.lastGrowthTick==Long.MIN_VALUE || now<town.progress.lastGrowthTick) { town.progress.lastGrowthTick=now; return; }
        int elapsed=(int)Math.clamp(now-town.progress.lastGrowthTick,0L,200L); town.progress.lastGrowthTick=now;
        town.progress.birthWaitTicks=Math.max(0,town.progress.birthWaitTicks-elapsed);
        SettlementData.get(level).setDirty();
        if(town.progress.birthWaitTicks>0) return;
        town.progress.birthWaitTicks=ATTEMPT_TICKS;
        // No catch-up births when the town is reloaded, and no rerolls when an attempt is paused.
        if(pause(level,town).startsWith("Ready") && random.nextInt(100)<5) birth(level,town);
    }
    public static boolean safe(ServerLevel level,Settlement town,BlockPos pos) {
        var view=CitizenReach.ground(level,p -> town.contains(p) && level.hasChunkAt(p));
        if(!CitizenReach.standing(view,pos)) return false;
        for(BlockPos p:List.of(pos,pos.above(),pos.below())) {
            var s=level.getBlockState(p);
            if(s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.WITHER_ROSE)
                    || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) return false;
        }
        return true;
    }
    /** The same eligibility and actual spawn checks apply to every caller, including a scheduled chance. */
    public static boolean birth(ServerLevel level,Settlement town) {
        if(!pause(level,town).startsWith("Ready")) return false;
        var adults=CitizenWellbeing.loaded(level,town).stream().filter(PopulationGrowth::adultReady)
                .filter(c -> town.contains(c.blockPosition())).toList();
        Set<BlockPos> homes=new HashSet<>();
        CitizenWellbeing.loaded(level,town).forEach(c -> { if(c.homeBed()!=null) homes.add(c.homeBed()); });
        for(BlockPos bed:SettlementService.housingBeds(level,town)) {
            if(homes.contains(bed) || level.getBlockState(bed).getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) continue;
            var parents=adults.stream().filter(c -> c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bed))<=32*32).limit(2).toList();
            if(parents.size()<2 || parents.get(0).distanceToSqr(parents.get(1))>16*16) continue;
            CitizenEntity baby=WWMC.CITIZEN.get().create(level,EntitySpawnReason.BREEDING); if(baby==null) return false;
            baby.setAge(-CHILD_TICKS);
            BlockPos spot=null;
            var ground=CitizenReach.ground(level,p -> town.contains(p) && level.hasChunkAt(p));
            for(BlockPos trial:BlockPos.betweenClosed(bed.offset(-2,-1,-2),bed.offset(2,1,2))) if(safe(level,town,trial)) {
                baby.setPos(ground.feet(trial));
                if(level.noCollision(baby)) { spot=trial.immutable(); break; }
            }
            if(spot==null) continue;
            baby.join(town.id); baby.setHomeBed(bed); baby.setHappiness(75);
            baby.setCustomName(Component.literal(SettlementService.citizenName(level,town,baby.getUUID())));
            if(!level.addFreshEntity(baby)) return false;
            var stock=SettlementService.storage(level,town);
            for(int i=0;i<MEALS;i++) FoodHealing.take(stock,remainder -> {
                ItemStack left=remainder;
                for(var container:stock) { left=InventoryOps.insert(container,left); if(left.isEmpty()) break; }
                if(!left.isEmpty()) baby.bag().offer(left);
            });
            town.citizens.add(baby.getUUID()); town.progress.children.add(baby.getUUID());
            parents.forEach(c -> c.setAge(PARENT_COOLDOWN));
            SettlementData.get(level).setDirty();
            level.sendParticles(ParticleTypes.HEART,baby.getX(),baby.getY()+0.7,baby.getZ(),7,0.3,0.3,0.3,0);
            CampaignService.record(level,town,baby.getName().getString()+" was born. Children grow up before taking jobs.");
            WWMC.LOGGER.info("[WWMC][birth] town={} child={} population={} meals={}",town.id,baby.getUUID(),town.citizens.size(),MEALS);
            return true;
        }
        return false;
    }
}
