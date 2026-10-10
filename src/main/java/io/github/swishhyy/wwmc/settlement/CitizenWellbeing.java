package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;

/** Food is remembered when eaten; amenities count by type in actual occupied housing. */
public final class CitizenWellbeing {
    public record Conditions(int beds,int population,Set<String> amenities,boolean alarm) {}
    public record Outlook(int target,String reason) {}
    private CitizenWellbeing() {}
    private static String amenity(net.minecraft.world.level.block.state.BlockState state) {
        if(state.is(BlockTags.FLOWERS) && !state.is(Blocks.WITHER_ROSE) || state.is(BlockTags.FLOWER_POTS)) return "Garden";
        if(state.is(Blocks.BELL)) return "Meeting bell";
        if(state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return "Warmth";
        if(state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF)
                && ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.stream().anyMatch(state::getValue)) return "Books";
        return "";
    }
    public static Conditions conditions(ServerLevel level,Settlement town) {
        Set<String> amenities=new LinkedHashSet<>();
        for(Station station:town.stations) if(station.role().providesHousing() && SettlementService.active(level,station)
                && !SettlementService.beds(level,town,station).isEmpty()) {
            var positions=SettlementService.resourcePositions(level,town,station,"amenities",() -> {
                List<BlockPos> found=new ArrayList<>();
                for(BlockPos pos:SettlementService.cells(station)) if(town.contains(pos) && level.hasChunkAt(pos)
                        && !amenity(level.getBlockState(pos)).isEmpty()) found.add(pos.immutable());
                return found;
            });
            for(BlockPos pos:positions) if(town.contains(pos) && level.hasChunkAt(pos)) {
                String type=amenity(level.getBlockState(pos)); if(!type.isEmpty()) amenities.add(type);
            }
        }
        return new Conditions(SettlementService.housingBeds(level,town).size(),town.citizens.size(),Set.copyOf(amenities),DefenseService.alarmed(town));
    }
    public static Outlook outlook(CitizenEntity citizen,Conditions town) {
        int score=50,kinds=MealVariety.distinct(citizen.recentMeals()); List<String> reasons=new ArrayList<>();
        if(town.beds()>=town.population() && town.beds()>0) { score+=10; reasons.add("Enough housing"); }
        else { score-=town.beds()==0 ? 15 : 10; reasons.add(town.beds()==0 ? "Needs housing" : "Crowded housing"); }
        if(kinds>=2) { score+=kinds>=4 ? 20 : kinds==3 ? 15 : 8; reasons.add(kinds+" different meals eaten"); }
        else if(citizen.recentMeals().size()>=4) { score-=8; reasons.add("Repetitive meals"); }
        if(!town.amenities().isEmpty()) { score+=town.amenities().size()*5; reasons.add(town.amenities().size()+" amenity types"); }
        if(citizen.mealTicks()<=0) { score-=25; reasons.add("Hungry"); }
        if(town.alarm() || citizen.inCombat()) { score-=20; reasons.add("Danger nearby"); }
        if(citizen.getHealth()<citizen.getMaxHealth()*0.75F) { score-=10; reasons.add("Injured"); }
        return new Outlook(Math.clamp(score,0,100),String.join(" · ",reasons));
    }
    public static void tick(ServerLevel level,Settlement town) {
        List<CitizenEntity> loaded=loaded(level,town); if(loaded.isEmpty()) return;
        Conditions conditions=conditions(level,town);
        for(CitizenEntity citizen:loaded) {
            int target=outlook(citizen,conditions).target();
            citizen.setHappiness(citizen.happiness()+Math.clamp(target-citizen.happiness(),-2,2));
        }
    }
    public static List<CitizenEntity> loaded(ServerLevel level,Settlement town) {
        List<CitizenEntity> citizens=new ArrayList<>();
        for(UUID id:town.citizens) if(level.getEntity(id) instanceof CitizenEntity citizen && citizen.isAlive() && citizen.town(level)==town) citizens.add(citizen);
        return citizens;
    }
    public static int average(ServerLevel level,Settlement town) {
        return (int)Math.round(loaded(level,town).stream().mapToInt(CitizenEntity::happiness).average().orElse(50));
    }
    public static String mood(int happiness) { return happiness>=75 ? "Happy" : happiness>=50 ? "Content" : happiness>=25 ? "Unhappy" : "Distressed"; }
}
