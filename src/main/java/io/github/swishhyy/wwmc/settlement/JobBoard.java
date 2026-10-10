package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;

/**
 * Who works where, and how much each job matters. A citizen keeps its job: it goes back to the same station every
 * morning, after a delivery or a meal, instead of taking whichever station has the smallest crew at that moment. It
 * changes job only when its station is gone or switched off, or when a job of higher priority has an open place.
 */
public final class JobBoard {
    public static final int OFF=0,LOW=1,NORMAL=2,HIGH=3;
    public static final List<String> LEVEL_NAMES=List.of("Off","Low","Normal","High");
    /** Priority presets, applied from the banner or with /wwmc priority; "custom" once a single job is changed. */
    public static final List<String> PRESETS=List.of("balanced","food","materials");
    public static final String CUSTOM="custom";
    private static final Codec<UUID> UUID_CODEC=Codec.STRING.xmap(UUID::fromString,UUID::toString);
    public static final Codec<JobBoard> CODEC=RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING,Codec.intRange(OFF,HIGH)).optionalFieldOf("levels",Map.of()).forGetter(b -> b.levels),
        Codec.unboundedMap(UUID_CODEC,BlockPos.CODEC).optionalFieldOf("assignments",Map.of()).forGetter(b -> b.assignments)
    ).apply(i,JobBoard::new));
    private final Map<String,Integer> levels;
    private final Map<UUID,BlockPos> assignments;
    /** Counts priority changes, so citizens look for a better job at once instead of at their next regular look. */
    private int revision;
    public JobBoard(Map<String,Integer> levels,Map<UUID,BlockPos> assignments) {
        this.levels=new HashMap<>(levels); this.assignments=new HashMap<>();
        assignments.forEach((citizen,station) -> this.assignments.put(citizen,station.immutable()));
    }
    /** Levels for a preset; unknown names, including "custom", count as balanced. */
    public static JobBoard preset(String preset) {
        JobBoard board=new JobBoard(Map.of(),Map.of());
        board.apply(preset);
        return board;
    }
    public void apply(String preset) {
        revision++;
        levels.clear();
        for(StructureRole role:StructureRole.values()) if(role.providesWork()) levels.put(role.id(),presetLevel(preset,role));
    }
    private static int presetLevel(String preset,StructureRole role) {
        if(role==StructureRole.GUARD || role==StructureRole.TRADER) return HIGH;
        // Food and material production depend on deliveries; do not promote their couriers away.
        if(role==StructureRole.COURIER && ("food".equals(preset) || "materials".equals(preset))) return HIGH;
        boolean food=role.foodJob();
        return switch(preset==null ? "" : preset) {
            case "food" -> food ? HIGH : NORMAL;
            case "materials" -> food ? LOW : NORMAL;
            default -> NORMAL;
        };
    }
    /** The preset these levels match, or "custom". */
    public String matchingPreset() {
        for(String preset:PRESETS) {
            boolean same=true;
            for(StructureRole role:StructureRole.values()) if(role.providesWork() && level(role)!=presetLevel(preset,role)) same=false;
            if(same) return preset;
        }
        return CUSTOM;
    }
    public int level(StructureRole role) {
        if(!role.providesWork()) return OFF;
        return Math.clamp(levels.getOrDefault(role.id(),presetLevel("balanced",role)),OFF,HIGH);
    }
    public void setLevel(StructureRole role,int level) {
        if(!role.providesWork()) return;
        levels.put(role.id(),Math.clamp(level,OFF,HIGH)); revision++;
    }
    public int revision() { return revision; }
    public static String levelName(int level) { return LEVEL_NAMES.get(Math.clamp(level,OFF,HIGH)); }
    /** Copies a saved board over the preset this one started from. */
    public void load(Optional<JobBoard> saved) {
        saved.ifPresent(board -> { levels.clear(); levels.putAll(board.levels); assignments.clear(); assignments.putAll(board.assignments); });
    }

    // ---------- Assignments ----------
    public BlockPos home(UUID citizen) { return assignments.get(citizen); }
    public void assign(UUID citizen,BlockPos station) { assignments.put(citizen,station.immutable()); }
    public boolean release(UUID citizen) { return assignments.remove(citizen)!=null; }
    /** Citizens whose job is this station, in a fixed order so the same ones keep their places when a crew shrinks. */
    public List<UUID> crew(BlockPos station) {
        List<UUID> crew=new ArrayList<>();
        assignments.forEach((citizen,home) -> { if(home.equals(station)) crew.add(citizen); });
        crew.sort(Comparator.naturalOrder());
        return crew;
    }
    public int assigned(BlockPos station) {
        int count=0;
        for(BlockPos home:assignments.values()) if(home.equals(station)) count++;
        return count;
    }
    /** Frees everyone working at this kind of station, as when the owner switches the job off. */
    public int releaseRole(Settlement town,StructureRole role) {
        int before=assignments.size();
        assignments.values().removeIf(home -> { Station station=town.station(home); return station!=null && station.role()==role; });
        return before-assignments.size();
    }
    /**
     * Drops jobs that no longer hold: citizens who left, stations that were removed or switched off, and places beyond
     * a crew's size. Returns whether anything changed.
     */
    public boolean prune(Settlement town,ToIntFunction<Station> places) {
        int before=assignments.size();
        assignments.keySet().removeIf(citizen -> !town.citizens.contains(citizen) || town.progress.children.contains(citizen));
        assignments.values().removeIf(home -> { Station station=town.station(home); return station==null || level(station.role())==OFF; });
        for(Station station:town.stations) {
            List<UUID> crew=crew(station.position());
            for(int n=Math.max(0,places.applyAsInt(station));n<crew.size();n++) assignments.remove(crew.get(n));
        }
        return assignments.size()!=before;
    }
    /** Whether this citizen holds one of its station's places; extras beyond a shrunken crew do not. */
    public boolean holdsPlace(UUID citizen,Station station,int places) {
        int index=crew(station.position()).indexOf(citizen);
        return index>=0 && index<places;
    }
    /** Order in which open jobs are taken: higher priority first, then guards and traders, then the emptier station. */
    public Comparator<Station> openOrder() {
        return Comparator.comparingInt((Station s) -> -level(s.role()))
                .thenComparingInt(s -> s.role()==StructureRole.GUARD ? 0 : s.role()==StructureRole.TRADER ? 1 : 2)
                .thenComparingInt(s -> assigned(s.position()));
    }
}
