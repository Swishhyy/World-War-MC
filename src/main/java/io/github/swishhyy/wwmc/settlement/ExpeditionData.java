package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Encounter sites, their surviving defenders and ownership survive chunk unload and server restart. */
public final class ExpeditionData extends SavedData {
    public static final class Site {
        public static final Codec<Site> CODEC=RecordCodecBuilder.create(i -> i.group(
                Settlement.UUID_CODEC.fieldOf("id").forGetter(s -> s.id),BlockPos.CODEC.fieldOf("pos").forGetter(s -> s.pos),
                Codec.STRING.fieldOf("kind").forGetter(s -> s.kind),Codec.BOOL.optionalFieldOf("spawned",false).forGetter(s -> s.spawned),
                Settlement.UUID_CODEC.listOf().optionalFieldOf("guards",List.of()).forGetter(s -> s.guards),Codec.BOOL.optionalFieldOf("cleared",false).forGetter(s -> s.cleared),
                Settlement.UUID_CODEC.optionalFieldOf("claimed").forGetter(s -> Optional.ofNullable(s.claimed)),Codec.LONG.optionalFieldOf("next_raid",0L).forGetter(s -> s.nextRaid),
                BlockPos.CODEC.optionalFieldOf("ambush").forGetter(s -> Optional.ofNullable(s.ambush)),Settlement.UUID_CODEC.optionalFieldOf("victim").forGetter(s -> Optional.ofNullable(s.victim)),
                Codec.STRING.optionalFieldOf("region","").forGetter(s -> s.region),
                Codec.STRING.optionalFieldOf("objective","").forGetter(s -> s.objective),
                Codec.STRING.optionalFieldOf("resource","").forGetter(s -> s.resource),
                Settlement.UUID_CODEC.listOf().optionalFieldOf("captives",List.of()).forGetter(s -> s.captives),
                Settlement.UUID_CODEC.optionalFieldOf("leader").forGetter(s -> Optional.ofNullable(s.leader)),
                Codec.BOOL.optionalFieldOf("rewarded",false).forGetter(s -> s.rewarded)
        ).apply(i,Site::new));
        /** What clearing a site achieves: free captives, recover stolen goods, defeat a captain, defend a town, or simply clear it. */
        public static final String CLEAR="clear",RESCUE="rescue",RECOVER="recover",LEADER="leader",DEFEND="defend";
        public final UUID id;
        public final BlockPos pos;
        public final String kind,region;
        /** The objective, and the regional resource id of the land the site stands on; both empty for sites found before them. */
        public final String objective,resource;
        public boolean spawned,cleared,rewarded;
        public final List<UUID> guards,captives;
        public UUID claimed,victim,leader;
        public long nextRaid;
        public BlockPos ambush;
        public Site(UUID id,BlockPos pos,String kind,String region) { this(id,pos,kind,region,"",""); }
        public Site(UUID id,BlockPos pos,String kind,String region,String objective,String resource) {
            this(id,pos,kind,false,List.of(),false,Optional.empty(),0,Optional.empty(),Optional.empty(),region,objective,resource,List.of(),Optional.empty(),false);
        }
        private Site(UUID id,BlockPos pos,String kind,boolean spawned,List<UUID> guards,boolean cleared,Optional<UUID> claimed,long nextRaid,
                Optional<BlockPos> ambush,Optional<UUID> victim,String region,String objective,String resource,List<UUID> captives,Optional<UUID> leader,boolean rewarded) {
            this.id=id; this.pos=pos.immutable(); this.kind=kind; this.region=region; this.spawned=spawned; this.guards=new ArrayList<>(guards);
            this.cleared=cleared; this.claimed=claimed.orElse(null); this.nextRaid=nextRaid; this.ambush=ambush.orElse(null); this.victim=victim.orElse(null);
            this.objective=objective; this.resource=resource; this.captives=new ArrayList<>(captives); this.leader=leader.orElse(null); this.rewarded=rewarded;
        }
        public String title() { return switch(kind) { case "townhall" -> "Ruined Town Hall"; case "mine" -> "Ruined Mining Workshop"; case "fort" -> "Ruined Castle"; case "raid" -> "Bandit Raid"; default -> "Bandit Camp"; }; }
        /** The objective in force: forts found before objectives still hold a captain and a schematic. */
        public String task() { return objective.isEmpty() && kind.equals("fort") ? LEADER : objective; }
        /** What the expedition must do here, for the Sites list. */
        public String goal() {
            return switch(task()) {
                case RESCUE -> captives.isEmpty() && spawned ? "Captives freed" : "Rescue the captive villagers held in its pen";
                case RECOVER -> "Recover the stolen supplies in its second barrel";
                case LEADER -> "Defeat its Bandit Captain, who carries a research schematic";
                case DEFEND -> "Drive the raiders off a neighboring town";
                default -> "Drive out the occupiers";
            };
        }
    }
    private static final Codec<ExpeditionData> CODEC=Site.CODEC.listOf().xmap(ExpeditionData::new,d -> d.sites);
    private static final SavedDataType<ExpeditionData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"expeditions"),ExpeditionData::new,CODEC);
    public final List<Site> sites;
    public ExpeditionData() { this(List.of()); }
    private ExpeditionData(List<Site> sites) { this.sites=new ArrayList<>(sites); }
    public static ExpeditionData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public Site byId(UUID id) { return sites.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
}
