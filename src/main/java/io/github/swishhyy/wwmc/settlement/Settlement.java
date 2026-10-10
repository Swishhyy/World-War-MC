package io.github.swishhyy.wwmc.settlement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class Settlement {
    /** Every claim extends at least this far from its banner. */
    public static final int MIN_RADIUS=240;
    /** The population level of a town saved before population upgrades, until the server works out what it already holds. */
    public static final int UNSET=-1;
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    /** A record codec takes at most sixteen fields; later fields are paired alongside in the same map. */
    private static final MapCodec<Settlement> CORE = RecordCodecBuilder.mapCodec(i -> i.group(
        UUID_CODEC.fieldOf("id").forGetter(s -> s.id), UUID_CODEC.fieldOf("owner").forGetter(s -> s.owner),
        Codec.STRING.fieldOf("name").forGetter(s -> s.name), BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
        Codec.intRange(16,512).fieldOf("radius").forGetter(s -> s.radius),
        UUID_CODEC.listOf().fieldOf("citizens").forGetter(s -> s.citizens),
        Station.CODEC.listOf().fieldOf("stations").forGetter(s -> s.stations),
        Codec.STRING.optionalFieldOf("priority", "balanced").forGetter(s -> s.priority),
        BlockPos.CODEC.listOf().optionalFieldOf("border_banners",List.of()).forGetter(s -> s.borderBanners),
        Codec.unboundedMap(UUID_CODEC,Codec.STRING).optionalFieldOf("citizen_names",Map.of()).forGetter(s -> s.citizenNames),
        Codec.LONG.optionalFieldOf("next_wave",0L).forGetter(s -> s.nextWave),
        Codec.INT.optionalFieldOf("waves",0).forGetter(s -> s.waves),
        Codec.STRING.listOf().optionalFieldOf("disabled_recipes",List.of()).forGetter(s -> new ArrayList<>(s.disabledRecipes)),
        Workshop.Order.CODEC.listOf().optionalFieldOf("craft_orders").forGetter(s -> Optional.of(s.craftOrders)),
        Codec.INT.optionalFieldOf("population_level",UNSET).forGetter(s -> s.populationLevel),
        TradeSettings.CODEC.optionalFieldOf("trading").forGetter(s -> Optional.of(s.trading))
    ).apply(i, Settlement::new));
    /** Additional optional fields preserve both prior recall saves and campaign data alongside the sixteen-field core. */
    private record Extra(Optional<JobBoard> jobs,Map<UUID,BlockPos> places,Optional<CampaignState> campaign,Optional<TownProgress> progress) {}
    private static final MapCodec<Extra> EXTRA = RecordCodecBuilder.mapCodec(i -> i.group(
        JobBoard.CODEC.optionalFieldOf("jobs").forGetter(Extra::jobs),
        Codec.unboundedMap(UUID_CODEC,BlockPos.CODEC).optionalFieldOf("citizen_places",Map.of()).forGetter(Extra::places),
        CampaignState.CODEC.optionalFieldOf("campaign").forGetter(Extra::campaign),
        TownProgress.CODEC.optionalFieldOf("progress").forGetter(Extra::progress)
    ).apply(i, Extra::new));
    public static final Codec<Settlement> CODEC = Codec.mapPair(CORE,EXTRA).xmap(
        pair -> {
            Settlement s=pair.getFirst();
            s.jobs.load(pair.getSecond().jobs());
            pair.getSecond().places().forEach((citizen,place) -> s.citizenPlaces.put(citizen,place.immutable()));
            s.campaign=pair.getSecond().campaign().orElseGet(CampaignState::new);
            s.progress=pair.getSecond().progress().orElseGet(TownProgress::legacy);
            return s;
        },
        s -> Pair.of(s,new Extra(Optional.of(s.jobs),s.citizenPlaces,Optional.of(s.campaign),Optional.of(s.progress)))).codec();
    public final UUID id;
    /** Changes only when both main settlement owners consent to an expedition outpost battle and its flag is captured. */
    public UUID owner;
    public String name, priority;
    public final BlockPos center;
    public int radius;
    /** Game time of the next enemy wave; zero until the town is populous enough. */
    public long nextWave;
    public int waves;
    /** Switched-off fixed recipes; today only "bread", which cooks bake. */
    public final Set<String> disabledRecipes;
    /** What craftsmen keep in stock, in priority order. */
    public final List<Workshop.Order> craftOrders;
    /** Population upgrades bought with emeralds at the banner; each raises the citizen limit and the waves' strength. */
    public int populationLevel;
    /** Routes and NPC origin are optional so settlements from earlier builds retain their identity. */
    public final TradeSettings trading;
    /** Each job's priority and each citizen's own station; towns from before keep their preset's priorities. */
    public final JobBoard jobs;
    public CampaignState campaign=new CampaignState();
    /** Research, schematics and shared map pings. */
    public TownProgress progress=new TownProgress();
    public final List<UUID> citizens;
    public final List<Station> stations;
    public final List<BlockPos> borderBanners;
    public final Map<UUID,String> citizenNames;
    /** Where each citizen last stood while ticking, so one stranded in an unloaded chunk can be found and brought back. */
    public final Map<UUID,BlockPos> citizenPlaces=new HashMap<>();
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority) {
        this(id,owner,name,center,radius,citizens,stations,priority,List.of());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners) {
        this(id,owner,name,center,radius,citizens,stations,priority,borderBanners,Map.of());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners,Map<UUID,String> citizenNames) {
        this(id,owner,name,center,radius,citizens,stations,priority,borderBanners,citizenNames,0L,0,List.of());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners,Map<UUID,String> citizenNames,long nextWave,int waves,List<String> disabledRecipes) {
        this(id,owner,name,center,radius,citizens,stations,priority,borderBanners,citizenNames,nextWave,waves,disabledRecipes,Optional.empty());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners,Map<UUID,String> citizenNames,long nextWave,int waves,List<String> disabledRecipes,Optional<List<Workshop.Order>> craftOrders) {
        this(id,owner,name,center,radius,citizens,stations,priority,borderBanners,citizenNames,nextWave,waves,disabledRecipes,craftOrders,0);
    }
    /** Towns saved before orders were learnable start with the default orders, minus any the owner had switched off. */
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners,Map<UUID,String> citizenNames,long nextWave,int waves,List<String> disabledRecipes,Optional<List<Workshop.Order>> craftOrders,int populationLevel) {
        this(id,owner,name,center,radius,citizens,stations,priority,borderBanners,citizenNames,nextWave,waves,disabledRecipes,craftOrders,populationLevel,Optional.empty());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners,Map<UUID,String> citizenNames,long nextWave,int waves,List<String> disabledRecipes,Optional<List<Workshop.Order>> craftOrders,int populationLevel,Optional<TradeSettings> trading) {
        this.id=id; this.owner=owner; this.name=name; this.center=center.immutable(); this.radius=radius;
        this.citizens=new ArrayList<>(citizens); this.stations=new ArrayList<>(stations); this.priority=priority;
        this.borderBanners=new ArrayList<>();
        borderBanners.forEach(p -> this.borderBanners.add(p.immutable()));
        this.citizenNames=new HashMap<>(citizenNames);
        this.nextWave=nextWave; this.waves=waves;
        this.disabledRecipes=new LinkedHashSet<>(disabledRecipes);
        this.craftOrders=new ArrayList<>(craftOrders.orElseGet(() -> Workshop.defaults(disabledRecipes)));
        this.populationLevel=Math.max(UNSET,populationLevel);
        this.trading=trading.orElseGet(TradeSettings::new);
        this.jobs=JobBoard.preset(priority);
    }
    public boolean contains(BlockPos pos) {
        return Math.abs((long)pos.getX()-center.getX()) <= radius && Math.abs((long)pos.getZ()-center.getZ()) <= radius;
    }
    public boolean overlaps(BlockPos pos, int r) {
        return Math.abs((long)pos.getX()-center.getX()) <= (long)radius+r && Math.abs((long)pos.getZ()-center.getZ()) <= (long)radius+r;
    }
    /** Nearest eligible station owns a block; coordinate ties are independent of placement/save order. */
    public Station nearestStation(BlockPos pos, Predicate<Station> eligible) {
        return stations.stream().filter(s -> s.contains(pos) && eligible.test(s))
                .min(Comparator.comparingDouble((Station s) -> s.position().distSqr(pos))
                    .thenComparingInt(s -> s.position().getX())
                    .thenComparingInt(s -> s.position().getY())
                    .thenComparingInt(s -> s.position().getZ())).orElse(null);
    }
    /** Enlarge an older, smaller claim when the larger square would not reach another town. */
    public boolean widenTo(int minimum,Collection<Settlement> towns) {
        if(radius>=minimum || towns.stream().anyMatch(other -> other!=this && other.overlaps(center,minimum))) return false;
        radius=minimum; return true;
    }
    public Station station(BlockPos pos) { return stations.stream().filter(s -> s.position().equals(pos)).findFirst().orElse(null); }
    /** Swap in a station's new record, such as after an upgrade; returns false when no station stands there. */
    public boolean replace(Station station) {
        for(int i=0;i<stations.size();i++) if(stations.get(i).position().equals(station.position())) { stations.set(i,station); return true; }
        return false;
    }
}
