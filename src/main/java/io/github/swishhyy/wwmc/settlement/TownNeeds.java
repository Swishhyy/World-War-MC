package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Problems the town's managers can fix, gathered from loaded stations, storage and citizens, most urgent first. Each
 * names the station to look at, so the town screen can point it out in the world. Nothing here loads a chunk.
 */
public final class TownNeeds {
    public static final int URGENT=2,WARNING=1,ADVICE=0;
    /** One problem; {@code at} is the block to highlight, or null when there is no single place to show. */
    public record Need(int severity,ItemStack icon,String title,String detail,BlockPos at) {}
    private TownNeeds() {}
    private static ItemStack icon(ItemLike item) { return new ItemStack(item); }
    private static ItemStack station(StructureRole role) { return new ItemStack(WWMC.STATION_ITEMS.get(role).get()); }
    private static Station first(Settlement town,StructureRole role) {
        return town.stations.stream().filter(s -> s.role()==role).findFirst().orElse(null);
    }
    /** Share of slots holding anything, across these containers. */
    public static float fill(List<Container> containers) {
        int slots=0,used=0;
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) { slots++; if(!container.getItem(slot).isEmpty()) used++; }
        return used/(float)Math.max(1,slots);
    }
    /** A worker's activity that asks the owner for something, rather than describing ordinary work or waiting. */
    public static boolean asks(String activity) {
        String text=activity.toLowerCase(Locale.ROOT);
        if(text.startsWith("off duty") || text.contains("hospital bed")) return false;
        // Growing crops, saplings and an idle smith are ordinary waits, not needs.
        return text.startsWith("needs ") || text.contains(": needs ") || text.startsWith("cannot reach")
                || text.startsWith("no accessible natural tree;")
                || text.startsWith("waiting for couriers to deliver") || text.startsWith("waiting for courier-delivered")
                || text.startsWith("waiting for this item's matching repair material")
                || text.startsWith("waiting for ") && text.contains(" lapis in my barrel") || text.contains("is full") || text.contains("are full");
    }
    public static List<Need> assess(ServerLevel level,Settlement town) {
        List<Need> needs=new ArrayList<>();
        int population=town.citizens.size();
        List<CitizenEntity> loaded=DefenseService.loadedCitizens(level,town);
        if(DefenseService.alarmed(town)) needs.add(new Need(URGENT,icon(Items.BELL),"The alarm is sounding",DefenseService.status(town),town.center));
        // Storage and food come first: every job depends on them.
        Station warehouse=first(town,StructureRole.WAREHOUSE);
        List<Container> storage=SettlementService.storage(level,town);
        if(warehouse==null) needs.add(new Need(URGENT,icon(Items.CHEST),"No warehouse","Place a Warehouse Station with chests or barrels in range: citizens eat from it and couriers haul through it",town.center));
        else if(storage.isEmpty() && level.hasChunkAt(warehouse.position()))
            needs.add(new Need(URGENT,icon(Items.CHEST),"The warehouse has no storage","Put chests or barrels within "+warehouse.radius()+" blocks of the Warehouse Station",warehouse.position()));
        else if(!storage.isEmpty()) {
            float fill=fill(storage);
            if(fill>=0.9F) needs.add(new Need(fill>=0.99F ? URGENT : WARNING,icon(Items.CHEST),fill>=0.99F ? "The warehouse is full" : "The warehouse is nearly full",
                    Math.round(fill*100)+"% of slots used: add chests or barrels in range, or export surplus by trader",warehouse.position()));
        }
        List<Container> everything=SettlementService.townStorage(level,town);
        int meals=InventoryOps.count(everything,FoodHealing::food);
        BlockPos pantry=warehouse==null ? town.center : warehouse.position();
        if(population>0 && meals<population) needs.add(new Need(meals==0 ? URGENT : WARNING,icon(Items.BREAD),meals==0 ? "No food in storage" : "Food is running low",
                meals+" meals for "+population+" citizens: farms, cooks, hunters, fishermen and traders bring more",pantry));
        if(population>=3 && meals>=population) {
            int kinds=MealVariety.kinds(everything);
            if(kinds<=1) needs.add(new Need(ADVICE,icon(Items.COOKED_BEEF),"Meals lack variety","Only one kind of food in storage: two or more kinds keep citizens content and working faster",pantry));
        }
        // People.
        int beds=SettlementService.housingBeds(level,town).size();
        Station housing=first(town,StructureRole.HOUSING);
        if(population>beds) needs.add(new Need(WARNING,icon(net.minecraft.world.level.block.Blocks.BED.red()),(population-beds)+(population-beds==1 ? " citizen has" : " citizens have")+" no housing bed",
                "Place complete beds near a Housing or Barracks Station",housing==null ? town.center : housing.position()));
        int injured=0;
        for(CitizenEntity citizen:loaded) if(HospitalCare.needsCare(town,citizen) && citizen.hospitalBed()==null) injured++;
        if(injured>0) {
            Station hospital=first(town,StructureRole.HOSPITAL);
            needs.add(new Need(injured>=3 ? URGENT : WARNING,station(StructureRole.HOSPITAL),injured+(injured==1 ? " citizen needs" : " citizens need")+" a hospital bed",
                    hospital==null ? "Build a Hospital Station with free, reachable beds; injured citizens rest there until fully healed"
                            : "Every hospital bed is taken or unreachable: add beds near the Hospital Station",hospital==null ? town.center : hospital.position()));
        }
        int missing=population-loaded.size();
        if(missing>0) needs.add(new Need(ADVICE,icon(Items.MAP),missing+(missing==1 ? " citizen is" : " citizens are")+" out of range",
                "They are brought back while their station is loaded; a station's Crew tab shows where each was last seen",null));
        int idle=0;
        for(CitizenEntity citizen:loaded) if(town.jobs.home(citizen.getUUID())==null && !SquadService.assigned(town,citizen.getUUID())) idle++;
        if(idle>0 && population>0) needs.add(new Need(ADVICE,icon(Items.PAPER),idle+(idle==1 ? " citizen has" : " citizens have")+" no job",
                TownJobs.assess(level,town).noJobAdvice(),null));
        // Stations.
        boolean barrels=false;
        for(Station station:town.stations) {
            BlockPos pos=station.position();
            if(!level.hasChunkAt(pos)) continue;
            StructureRole role=station.role();
            String name=role.title()+" Station";
            // A trader with no route to run, and a hospital before its medic is funded, are idle by design.
            boolean byDesign=role==StructureRole.TRADER && !TradeRoutes.canDepart(level,town) || role==StructureRole.HOSPITAL && !town.campaign.projects.contains("hospital");
            if(role.providesWork() && !byDesign && town.jobs.level(role)!=JobBoard.OFF && town.jobs.assigned(pos)==0)
                needs.add(new Need(WARNING,station(role),name+" has no worker","Recruit citizens, or raise this job's priority on the Jobs tab so a citizen moves here",pos));
            if(role.keepsJobStorage()) {
                if(SettlementService.jobStorage(level,town,station).isEmpty())
                    needs.add(new Need(WARNING,station(role),name+" needs a job barrel","Put a barrel within "+station.radius()
                            +" blocks, outside warehouse range. Where job ranges overlap, the nearest station owns the barrel",pos));
                else barrels=true;
            }
            switch(role) {
                case COOK,SMELTERY -> {
                    List<BlockPos> devices=SettlementService.processingDevices(level,town,station);
                    if(devices.isEmpty()) needs.add(new Need(WARNING,station(role),name+" needs "+(role==StructureRole.COOK ? "a smoker, furnace or campfire" : "a furnace"),
                            "Place one within "+station.radius()+" blocks of the station",pos));
                    else if(devices.stream().anyMatch(device -> ProcessingService.needsFuel(level,device)))
                        needs.add(new Need(WARNING,icon(Items.COAL),name+" needs fuel","A loaded "+(role==StructureRole.COOK ? "oven" : "furnace")
                                +" has no fuel: stock coal or charcoal in the warehouse for couriers to deliver",pos));
                }
                case BLACKSMITH -> {
                    if(SettlementService.anvils(level,town,station).isEmpty()) needs.add(new Need(WARNING,icon(Items.ANVIL),name+" needs an anvil","Place a bronze or iron anvil within three blocks of the station",pos));
                    if(town.progress.forgeOrders.stream().anyMatch(o -> o.target()>0) && ForgeWorkshop.heat(level,town,station).isEmpty())
                        needs.add(new Need(WARNING,icon(Items.FURNACE),name+" needs forge heat","Place a furnace or blast furnace in range for metallurgy and equipment orders",pos));
                }
                case ENCHANTER -> { if(SettlementService.enchantingTables(level,town,station).isEmpty()) needs.add(new Need(WARNING,icon(Items.ENCHANTING_TABLE),name+" needs an enchanting table","Place one within "+station.radius()+" blocks of the station",pos)); }
                case RESEARCHER -> { if(Research.desks(level,town,station).isEmpty()) needs.add(new Need(WARNING,icon(Items.LECTERN),name+" needs a lectern","Place a lectern within "+station.radius()+" blocks; supply paper and ink or charcoal to make scrolls",pos)); }
                case HOSPITAL -> { if(SettlementService.beds(level,town,station).isEmpty()) needs.add(new Need(WARNING,station(StructureRole.HOSPITAL),name+" has no patient beds","Place complete beds near the Hospital Station",pos)); }
                case GUARD -> { if(!GuardService.posts(level,station).chosen()) needs.add(new Need(ADVICE,icon(Items.IRON_SWORD),name+" has no chosen posts","Its guard stands at the station; open it and choose day and night posts",pos)); }
                case TRADER -> {
                    String status=town.trading.status==null ? "" : town.trading.status;
                    if(status.toLowerCase(Locale.ROOT).contains("blocked")) needs.add(new Need(WARNING,icon(Items.COMPASS),"The trade route is blocked",status,pos));
                }
                default -> {}
            }
        }
        if(barrels && !SettlementService.couriers(level,town))
            needs.add(new Need(WARNING,station(StructureRole.COURIER),"No courier","Production waits in job barrels: "+SettlementService.courierAdvice(level,town),null));
        // What workers themselves are waiting for, once per station and message.
        Map<String,Need> asked=new LinkedHashMap<>();
        Map<String,Integer> count=new HashMap<>();
        for(CitizenEntity citizen:loaded) {
            BlockPos home=town.jobs.home(citizen.getUUID());
            Station station=home==null ? null : town.station(home);
            String activity=citizen.activity();
            if(station==null || !asks(activity)) continue;
            String key=home.asLong()+"|"+activity;
            count.merge(key,1,Integer::sum);
            asked.putIfAbsent(key,new Need(WARNING,station(station.role()),station.role().title()+": "+citizen.getName().getString(),activity,home));
        }
        asked.forEach((key,need) -> {
            int workers=count.get(key);
            needs.add(workers==1 ? need : new Need(need.severity(),need.icon(),need.title()+" and "+(workers-1)+" more",need.detail(),need.at()));
        });
        for(TrapService.Entry trap:town.progress.traps) {
            BlockPos at=trap.pos();
            if(level.hasChunkAt(at) && level.isPositionEntityTicking(at) && TrapService.needsMaintenance(level.getBlockState(at)))
                needs.add(new Need(WARNING,icon(level.getBlockState(at).getBlock()),"Trap needs maintenance",TrapService.describe(level,at),at));
        }
        needs.sort(Comparator.comparingInt((Need need) -> -need.severity()));
        return needs;
    }
}
