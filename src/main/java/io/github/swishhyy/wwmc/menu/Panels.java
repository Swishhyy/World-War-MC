package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.PanelView.Action;
import io.github.swishhyy.wwmc.menu.PanelView.Row;
import io.github.swishhyy.wwmc.menu.PanelView.Tab;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

/** Builds every settlement screen on the server and applies its buttons. Only the town's owner sees these screens. */
public final class Panels {
    public static final int PRIORITY=1,ALARM=2,RECRUIT=3,BREAD=4,POSTS=5,RANGE_UP=6,CREW_UP=7,GROW=8,YIELD_UP=9,TARGET=10,RAISE=11,FORGET=12,JOB=13,JOB_STEP=14,GUARD_ROLE=15,PATROL=16,PATROL_CLEAR=17;
    public static final int GREEN=0xFF3FA34D,RED=0xFFC0392B,AMBER=0xFFD39B1E,GRAY=0xFF707070;
    private Panels() {}
    private static ServerLevel level(ServerPlayer player) { return (ServerLevel)player.level(); }
    private static ItemStack icon(ItemLike item) { return new ItemStack(item); }
    public static ItemStack stationIcon(StructureRole role) { return new ItemStack(WWMC.STATION_ITEMS.get(role).get()); }
    /** The owner's settlement at this position, while the owner stands within eight blocks of it. */
    private static Settlement owned(ServerPlayer player,BlockPos pos) {
        if(!player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(pos))>64) return null;
        Settlement town=SettlementData.get(level(player)).at(pos);
        return SettlementService.owns(player,town) ? town : null;
    }
    private static Station stationAt(ServerPlayer player,BlockPos pos) {
        Settlement town=owned(player,pos);
        Station station=town==null ? null : town.station(pos);
        return station!=null && SettlementService.active(level(player),station) ? station : null;
    }
    /** Cheap check the open screen runs every tick. */
    public static boolean valid(ServerPlayer player,BlockPos pos,boolean banner) {
        if(banner) { Settlement town=owned(player,pos); return town!=null && town.center.equals(pos) && level(player).hasChunkAt(pos) && level(player).getBlockState(pos).is(WWMC.BANNER.get()); }
        return stationAt(player,pos)!=null;
    }
    private static void dirty(ServerPlayer player) { SettlementData.get(level(player)).setDirty(); }

    // ---------- Opening ----------
    public static void openTown(ServerPlayer player,Settlement town) {
        PanelView view=town(level(player),town,player);
        BlockPos pos=town.center;
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,PanelMenu.Kind.TOWN,pos,player,view),view.title()),
                buf -> PanelMenu.write(buf,PanelMenu.Kind.TOWN,pos,view));
    }
    public static void openStation(ServerPlayer player,Settlement town,Station station) {
        BlockPos pos=station.position();
        if(station.role()==StructureRole.TRADER) { io.github.swishhyy.wwmc.settlement.TraderPanel.open(player,town,station); return; }
        if(station.role()==StructureRole.CRAFTSMAN) {
            PanelView view=craftsman(level(player),town,station,"");
            player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new CraftsmanMenu(id,inventory,pos,player,view),view.title()),
                    buf -> CraftsmanMenu.write(buf,pos,view));
            return;
        }
        PanelView view=station(level(player),town,station,player);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,PanelMenu.Kind.STATION,pos,player,view),view.title()),
                buf -> PanelMenu.write(buf,PanelMenu.Kind.STATION,pos,view));
    }
    public static void openCitizen(ServerPlayer player,CitizenEntity citizen,CitizenInventory bag) {
        PanelView view=citizen(citizen);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> {
            CitizenMenu menu=new CitizenMenu(id,inventory,bag,citizen,player,view);
            bag.opened(p,menu);
            return menu;
        },citizen.getName()),buf -> PanelView.STREAM_CODEC.encode(buf,view));
    }

    // ---------- Town ----------
    public static PanelView town(ServerPlayer player,BlockPos pos) {
        Settlement town=owned(player,pos);
        return town==null ? null : town(level(player),town,player);
    }
    /** Citizens who could join now: free housing beds, within the population limit. */
    public static int vacancies(ServerLevel level,Settlement town) {
        return Math.max(0,Math.min(SettlementService.housingBeds(level,town).size(),SettlementService.populationLimit(town))-town.citizens.size());
    }
    public static PanelView town(ServerLevel level,Settlement town) { return town(level,town,null); }
    public static PanelView town(ServerLevel level,Settlement town,ServerPlayer viewer) {
        int beds=SettlementService.housingBeds(level,town).size(),limit=SettlementService.populationLimit(town);
        List<Row> needs=needRows(level,town); long problems=needs.stream().filter(r -> r.color()!=GREEN).count();
        var overview=new ArrayList<Row>();
        overview.add(new Row(icon(WWMC.BANNER_ITEM.get()),"Population",town.citizens.size()+" / "+limit+" citizens · "+beds+" housing beds · +"+Research.populationBonus(town)+" places from research"));
        overview.add(new Row(icon(Items.PAPER),"Jobs",TownJobs.assess(level,town).summary()));
        overview.add(new Row(icon(Items.BREAD),"Food",InventoryOps.count(SettlementService.townStorage(level,town),FoodHealing::food)+" meals in storage"));
        int happiness=CitizenWellbeing.average(level,town);
        overview.add(new Row(icon(Items.APPLE),"Happiness: "+happiness+" / 100",CitizenWellbeing.mood(happiness)+" · "+town.progress.children.size()+" children · details in People / Wellbeing").bar(happiness/100F,happiness>=75 ? GREEN : happiness>=50 ? AMBER : RED));
        overview.add(researchStatus(Research.status(level,town)));
        overview.addAll(needs.stream().filter(r -> r.color()!=GREEN).limit(1).toList());
        var more=List.of(
                new Row(icon(Items.BELL),Component.literal("Alarm: "+DefenseService.status(town)),Component.literal("Shelter civilians and put guards on duty; toggle the all-clear when safe."),0,-1,0,"act:alarm"),
                new Row(icon(Items.FILLED_MAP),Component.literal("Expeditions and projects"),Component.literal("Army, ruined sites, outposts, supply projects and town journal"),0,-1,0,"act:campaign"),
                new Row(icon(Items.NAME_TAG),Component.literal("Town settings"),Component.literal("Name, colours, permissions, invitations and alliances"),0,-1,0,"act:relationships"),
                new Row(icon(Items.MAP),Component.literal("Settlement map"),Component.literal("Claims, routes, ruins and shared markers"),0,-1,0,"act:map"),
                new Row(icon(Items.PAPER),"Population research","+"+Research.populationBonus(town)+" places from housing discoveries"));
        return new PanelView(Component.literal(town.name),Component.literal(Research.age(town)+" · "+(problems==0 ? "Your settlement at a glance" : problems+" needs attention")),
                List.of(new Tab("Overview",overview),new Tab("Needs",needs),new Tab("More",more)),
                List.of(new Action(TownViews.PEOPLE,"People",true,"Citizens, jobs, recruitment and housing"),
                        new Action(TownViews.PRODUCTION,"Production",true,"Stock, automatic workshops and blacksmith orders"),
                        new Action(TownViews.RESEARCH,"Research",true,"Scroll production, requirements and discoveries"),
                        new Action(MultiplayerViews.OPEN,"Neighbours",true,"Trade, relationships and news")));
    }
    /** A separate short status row keeps the reason visible beside the project's percentage and work time. */
    public static Row researchStatus(Research.Status status) {
        return new Row(icon(Items.LECTERN),Component.literal(status.title()),Component.literal(status.detail()),
                status.paused() ? AMBER : status.state()==Research.WorkState.WORKING ? GREEN : GRAY,PanelView.NO_BAR,PanelView.NO_VALUE);
    }
    /** The town's needs, most urgent first; one at a place has a Show button that outlines it in the world. */
    private static List<Row> needRows(ServerLevel level,Settlement town) {
        List<Row> rows=new ArrayList<>();
        for(TownNeeds.Need need:TownNeeds.assess(level,town)) {
            int color=need.severity()==TownNeeds.URGENT ? RED : need.severity()==TownNeeds.WARNING ? AMBER : 0;
            BlockPos at=need.at();
            rows.add(new Row(need.icon(),Component.literal(need.title()),Component.literal(need.detail()),color,PanelView.NO_BAR,
                    at==null ? PanelView.NO_VALUE : 0,at==null ? "" : "act:show:"+at.getX()+","+at.getY()+","+at.getZ()));
            if(rows.size()>=PanelView.MAX_ROWS) break;
        }
        if(rows.isEmpty()) rows.add(new Row(icon(Items.EMERALD),Component.literal("Nothing needed"),
                Component.literal("Every loaded station has its workers, storage and supplies"),GREEN,PanelView.NO_BAR,PanelView.NO_VALUE));
        return rows;
    }
    /** Closes the screen and outlines a block of this town for the player, with directions in chat. */
    private static void show(ServerPlayer player,Settlement town,String coordinates) {
        String[] parts=coordinates.split(",");
        if(parts.length!=3) return;
        BlockPos pos;
        try { pos=new BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])); }
        catch(NumberFormatException e) { return; }
        if(!town.contains(pos)) return;
        player.closeContainer();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new WwmcNetwork.HighlightPayload(pos,20));
        SettlementService.tell(player,"Marked "+pos.toShortString()+": "+directions(player.blockPosition(),pos)+". The outline shows through walls for 20 seconds.");
    }
    /** How far, and which way, from one place to another, such as "34 blocks north-east". */
    public static String directions(BlockPos from,BlockPos to) {
        int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
        int distance=(int)Math.round(Math.sqrt((double)dx*dx+(double)dz*dz));
        if(distance<2) return "right here";
        String[] names={"south","south-west","west","north-west","north","north-east","east","south-east"};
        double angle=Math.toDegrees(Math.atan2(-dx,dz));
        return distance+" blocks "+names[Math.floorMod((int)Math.round(angle/45.0),8)];
    }
    /** The population upgrade button: its price, and greyed out when unaffordable or at the ceiling. */
    public static Action grow(Settlement town,ServerPlayer viewer) {
        if(!SettlementService.canGrow(town)) return new Action(GROW,"Population: maxed",false,"The town is at the server's ceiling of "+Config.MAX_CITIZENS.get()+" citizens");
        int cost=SettlementService.populationCost(town),next=SettlementService.populationLimitAt(town,SettlementService.populationLevel(town)+1);
        return new Action(GROW,"Grow: "+cost+" emeralds",affords(viewer,cost),"Raise the limit from "+SettlementService.populationLimit(town)+" to "+next
                +" citizens for "+cost+" emeralds. Each upgrade costs "+Config.POPULATION_COST.get()+" more than the last, and every enemy wave grows: "
                +Config.WAVE_MOBS_PER_UPGRADE.get()+" more attackers, with pillagers and later vindicators among them.");
    }
    private static boolean affords(ServerPlayer viewer,int cost) {
        return viewer==null || viewer.getAbilities().instabuild || SettlementService.emeralds(viewer)>=cost;
    }
    private static Row alarm(Settlement town) {
        Row row=new Row(icon(Items.BELL),"Alarm",DefenseService.status(town));
        return DefenseService.alarmed(town) ? new Row(row.icon(),row.text(),row.detail(),RED,PanelView.NO_BAR,PanelView.NO_VALUE) : row;
    }
    /** A citizen's own station: where it works by assignment, even while asleep or on an errand. */
    private static Station home(Settlement town,CitizenEntity citizen) {
        BlockPos home=town.jobs.home(citizen.getUUID());
        return home==null ? null : town.station(home);
    }
    public static Row person(Settlement town,CitizenEntity citizen) {
        Station job=home(town,citizen);
        float health=citizen.getHealth()/Math.max(1,citizen.getMaxHealth());
        return new Row(job==null ? icon(Items.PAPER) : stationIcon(job.role()),citizen.getName().getString(),
                (citizen.isBaby() ? "Child · grows up in "+Math.max(1,(-citizen.getAge()+1199)/1200)+" min" : job==null ? "No job" : CitizenSkill.title(citizen.skillLevel(job.role()))+" "+job.role().title().toLowerCase(Locale.ROOT))
                        +": "+citizen.activity()+" · happiness "+citizen.happiness()+"/100").bar(health,health<0.5F ? RED : GREEN);
    }
    private static Row storage(ItemStack icon,String name,List<Container> containers,String none) {
        if(containers.isEmpty()) return new Row(icon,name,none);
        int slots=0,used=0;
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) { slots++; if(!container.getItem(slot).isEmpty()) used++; }
        float fill=used/(float)Math.max(1,slots);
        return new Row(icon,name,containers.size()+" containers, "+Math.round(fill*100)+"% of slots used").bar(fill,fill>0.9F ? RED : fill>0.7F ? AMBER : GREEN);
    }
    public static void townAction(ServerPlayer player,BlockPos pos,int action,int value,String key) {
        Settlement town=owned(player,pos);
        if(town==null) return;
        ServerLevel level=level(player);
        switch(action) {
            case TownViews.PEOPLE -> TownViews.open(player,town,PanelMenu.Kind.PEOPLE);
            case TownViews.PRODUCTION -> TownViews.open(player,town,PanelMenu.Kind.PRODUCTION);
            case TownViews.RESEARCH -> TownViews.open(player,town,PanelMenu.Kind.RESEARCH);
            case RelationshipViews.OPEN -> RelationshipViews.open(player,town);
            case CampaignViews.OPEN -> CampaignViews.open(player,town);
            case MultiplayerViews.OPEN -> MultiplayerViews.open(player,town);
            case PRIORITY -> SettlementService.applyPreset(level,town,JobBoard.PRESETS.get((JobBoard.PRESETS.indexOf(town.priority)+1)%JobBoard.PRESETS.size()));
            case JOB -> {
                if(key.equals("act:alarm")) { DefenseService.toggle(level,town); return; }
                if(key.equals("act:campaign")) { CampaignViews.open(player,town); return; }
                if(key.equals("act:relationships")) { RelationshipViews.open(player,town); return; }
                if(key.equals("act:map")) { player.closeContainer(); SettlementMap.open(player); return; }
                if(key.startsWith("act:show:")) { show(player,town,key.substring(9)); return; }
                StructureRole role=workRole(key);
                if(role!=null && value>=JobBoard.OFF && value<=JobBoard.HIGH) SettlementService.setJobLevel(level,town,role,value);
            }
            case ALARM -> DefenseService.toggle(level,town);
            case RECRUIT -> {
                int added=SettlementService.recruit(level,town,1);
                SettlementService.notify(player,added>0 ? "A new citizen joined "+town.name+"."
                        : town.citizens.size()>=SettlementService.populationLimit(town) ? "The town is full: buy room for more citizens with emeralds."
                        : "No citizen could join: free housing beds and open ground beside the banner are needed.");
            }
            case GROW -> SettlementService.upgradePopulation(level,player,town);
            default -> {}
        }
    }

    // ---------- Stations ----------
    public static PanelView station(ServerPlayer player,BlockPos pos) {
        Station station=stationAt(player,pos);
        return station==null ? null : station(level(player),owned(player,pos),station,player);
    }
    private static String crew(Settlement town,Station station) {
        return station.role().providesWork() ? "Crew "+town.jobs.assigned(station.position())+"/"+SettlementService.workerLimit(town,station) : "No crew";
    }
    private static StructureRole workRole(String id) {
        for(StructureRole role:StructureRole.values()) if(role.providesWork() && role.id().equals(id)) return role;
        return null;
    }
    private static int levelColor(int level) { return level==JobBoard.OFF ? GRAY : level==JobBoard.HIGH ? GREEN : 0; }
    /**
     * One row per job the town has stations for: its priority, with buttons to change it, and who holds its places.
     * Rows keep a fixed order so they do not move under the buttons.
     */
    public static List<Row> jobRows(Settlement town,TownJobs jobs) {
        List<Row> rows=new ArrayList<>();
        rows.add(new Row(icon(Items.PAPER),"Jobs: "+jobs.assigned()+"/"+jobs.places()+" filled",jobs.unassigned()+" unassigned; "+jobs.open()
                +" loaded openings, "+jobs.off()+" switched-off places, "+jobs.waiting()+" places waiting for loading or trade"));
        rows.add(new Row(icon(Items.BOOK),"Citizens keep their jobs","Open places in higher-priority jobs fill first and draw citizens from lower ones. Off frees a job's crew."));
        for(StructureRole role:StructureRole.values()) {
            if(!role.providesWork()) continue;
            List<Station> stations=town.stations.stream().filter(station -> station.role()==role).toList();
            if(stations.isEmpty()) continue;
            int places=0;
            List<String> names=new ArrayList<>();
            for(Station station:stations) {
                places+=SettlementService.workerLimit(town,station);
                for(UUID id:town.jobs.crew(station.position())) names.add(town.citizenNames.getOrDefault(id,"a citizen"));
            }
            int level=town.jobs.level(role);
            String detail=places==0 && role==StructureRole.HOSPITAL ? "Medic locked: fund the Field Hospital project on the Campaign tab"
                    : level==JobBoard.OFF ? "Switched off: nobody works here"
                    : names.size()+" of "+places+" places filled"+(names.isEmpty() ? "" : ": "+String.join(", ",names));
            rows.add(new Row(stationIcon(role),Component.literal(role.title()+" · "+JobBoard.levelName(level)),Component.literal(detail),
                    levelColor(level),PanelView.NO_BAR,level,role.id()));
        }
        if(rows.size()==2) rows.add(new Row(icon(Items.PAPER),"No work stations","Place a job station inside the claim to give citizens work"));
        return rows;
    }
    /** One line for the town's station list. */
    public static Row summary(ServerLevel level,Settlement town,Station station) {
        List<Row> lines=status(level,town,station);
        String first=lines.isEmpty() ? "" : lines.getFirst().detail().getString();
        return new Row(stationIcon(station.role()),station.role().title()+" Station",station.position().toShortString()+" · "
                +(station.role().providesWork() ? crew(town,station)+" · " : "")+first);
    }
    public static PanelView station(ServerLevel level,Settlement town,Station station) { return station(level,town,station,null); }
    public static PanelView station(ServerLevel level,Settlement town,Station station,ServerPlayer viewer) {
        StructureRole role=station.role();
        List<Tab> tabs=new ArrayList<>();
        tabs.add(new Tab("Status",status(level,town,station)));
        if(role.providesWork()) tabs.add(new Tab("Crew",crewRows(level,town,station)));
        if(role==StructureRole.WAREHOUSE) tabs.add(new Tab("Contents",contents(SettlementService.storageAt(level,town,station.position()))));
        else if(role.keepsJobStorage()) tabs.add(new Tab("Barrels",contents(SettlementService.jobStorage(level,town,station))));
        if(Upgrades.widens(role) || Upgrades.hires(role) || Upgrades.yields(role)) tabs.add(new Tab("Upgrades",upgradeRows(station,viewer)));
        List<Action> actions=new ArrayList<>();
        if(role==StructureRole.COOK) actions.add(new Action(BREAD,town.disabledRecipes.contains("bread") ? "Bread: off" : "Bread: on",true,"Cooks bake bread from 3 wheat, up to 32 loaves"));
        if(role==StructureRole.GUARD) {
            GuardPosts plan=GuardService.posts(level,station);
            actions.add(new Action(POSTS,"Choose guard posts",true,"Then use the Station Inspector on the ground for the day post and the night post"));
            actions.add(new Action(GUARD_ROLE,"Role: "+GuardPosts.title(plan.role()),true,GuardPosts.describe(plan.role())+". Click for the next role."));
            actions.add(new Action(PATROL,"Mark patrol route",true,"Then use the Station Inspector on the ground for up to "+GuardPosts.MAX_PATROL
                    +" points in order, and sneak-use it to save. The guard walks from its post through each point and back."));
            actions.add(new Action(PATROL_CLEAR,plan.patrol().isEmpty() ? "No marked route" : "Clear route ("+plan.patrol().size()+")",!plan.patrol().isEmpty(),
                    "The guard picks its own rounds around the town's stations again"));
        }
        if(role==StructureRole.BLACKSMITH || role==StructureRole.CRAFTSMAN)
            actions.add(new Action(TownViews.PRODUCTION,"Production orders",true,"Set automatic workshop, forging and metallurgy targets"));
        if(role==StructureRole.RESEARCHER) actions.add(new Action(TownViews.RESEARCH,"Research",true,"Scroll production and settlement discoveries"));
        for(Upgrades.Kind kind:Upgrades.Kind.values()) if(kind.supports(role)) actions.add(upgrade(station,kind,viewer));
        if(role.providesWork()) {
            int priority=town.jobs.level(role);
            actions.add(new Action(JOB_STEP,role.title()+" priority: "+JobBoard.levelName(priority),true,"Sets the priority of every "+role.id()
                    +" station: Off, Low, Normal or High. Higher-priority jobs fill first and draw citizens from lower ones; Off frees the crew. Click for the next level."));
        }
        return new PanelView(Component.literal(role.title()+" Station"),Component.literal(crew(town,station)+" · "
                +(role.excavates() ? "facing "+station.facing().getName() : "range "+station.size())),tabs,actions);
    }
    /** A range or crew upgrade button with its price; greyed out when maxed or unaffordable. */
    private static Action upgrade(Station station,Upgrades.Kind kind,ServerPlayer viewer) {
        int level=station.upgradeLevel(kind);
        int action=switch(kind) { case RANGE -> RANGE_UP; case CREW -> CREW_UP; case YIELD -> YIELD_UP; };
        if(level>=Upgrades.MAX_STATION_LEVEL) return new Action(action,kind.title()+": maxed",false,"Fully upgraded");
        int cost=SettlementService.stationCost(station,kind);
        Station next=station.upgraded(kind,level+1);
        String effect=switch(kind) {
            case RANGE -> "Widen the range from "+station.size()+" to "+next.size();
            case CREW -> "Raise the crew from "+SettlementService.workerLimit(station)+" to "+SettlementService.workerLimit(next)+" workers";
            case YIELD -> "Average "+Upgrades.yieldPercent(next.yieldLevel())+"% extra produce or mineral drops; replanting seeds and Silk Touch blocks do not multiply";
        };
        return new Action(action,kind.title()+": "+cost+" emeralds",affords(viewer,cost),
                effect+" for "+cost+" emeralds (level "+(level+1)+" of "+Upgrades.MAX_STATION_LEVEL+"). Emerald blocks count as nine.");
    }
    private static List<Row> upgradeRows(Station station,ServerPlayer viewer) {
        List<Row> rows=new ArrayList<>();
        StructureRole role=station.role();
        if(Upgrades.widens(role)) rows.add(new Row(icon(Items.SPYGLASS),"Range "+station.size(),"Level "+station.range()+" of "+Upgrades.MAX_STATION_LEVEL
                +(station.range()<Upgrades.MAX_STATION_LEVEL ? "; next "+station.withRange(station.range()+1).size()+" for "+SettlementService.stationCost(station,true)+" emeralds" : "; fully upgraded"))
                .bar(station.range()/(float)Upgrades.MAX_STATION_LEVEL,GREEN));
        if(Upgrades.hires(role)) rows.add(new Row(icon(Items.IRON_HELMET),"Crew of "+SettlementService.workerLimit(station),"Level "+station.crew()+" of "+Upgrades.MAX_STATION_LEVEL
                +(station.crew()<Upgrades.MAX_STATION_LEVEL ? "; next adds a worker for "+SettlementService.stationCost(station,false)+" emeralds" : "; fully upgraded"))
                .bar(station.crew()/(float)Upgrades.MAX_STATION_LEVEL,GREEN));
        if(Upgrades.yields(role)) rows.add(new Row(icon(role==StructureRole.FARM ? Items.WHEAT : Items.RAW_IRON),"Yield +"+Upgrades.yieldPercent(station.yieldLevel())+"%",
                "Level "+station.yieldLevel()+" of "+Upgrades.MAX_STATION_LEVEL+"; bonus chance per harvested unit"
                +(station.yieldLevel()<Upgrades.MAX_STATION_LEVEL ? "; next costs "+SettlementService.stationCost(station,Upgrades.Kind.YIELD)+" emeralds" : "; fully upgraded"))
                .bar(station.yieldLevel()/(float)Upgrades.MAX_STATION_LEVEL,GREEN));
        if(viewer!=null) rows.add(new Row(icon(Items.EMERALD),"Your emeralds",viewer.getAbilities().instabuild ? "Creative: upgrades are free"
                : SettlementService.emeralds(viewer)+" carried, emerald blocks counted as nine"));
        rows.add(new Row(stationIcon(role),"Kept when moved","A broken station's item keeps its upgrades"));
        return rows;
    }
    /** The citizens whose job is this station, including those asleep, on an errand or out of loaded range. */
    private static List<Row> crewRows(ServerLevel level,Settlement town,Station station) {
        List<Row> rows=new ArrayList<>();
        for(UUID id:town.jobs.crew(station.position())) {
            if(level.getEntity(id) instanceof CitizenEntity citizen) rows.add(person(town,citizen));
            else rows.add(new Row(icon(Items.MAP),town.citizenNames.getOrDefault(id,"A citizen"),CitizenRecall.whereabouts(level,town,id)));
        }
        if(rows.isEmpty()) rows.add(new Row(icon(Items.PAPER),"Nobody assigned",town.jobs.level(station.role())==JobBoard.OFF ? "This job is switched off"
                : "Citizens without a job, or in a lower-priority job, take open places"));
        return rows;
    }
    /** Totals of each item in these containers, largest first. */
    private static List<Row> contents(List<Container> containers) {
        if(containers.isEmpty()) return List.of(new Row(icon(Items.BARREL),"No storage","Put a barrel in the station's range"));
        Map<Item,Integer> totals=new LinkedHashMap<>();
        Map<Item,ItemStack> examples=new HashMap<>();
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) {
            ItemStack stack=container.getItem(slot);
            if(stack.isEmpty()) continue;
            totals.merge(stack.getItem(),stack.getCount(),Integer::sum);
            examples.putIfAbsent(stack.getItem(),stack.copyWithCount(1));
        }
        List<Row> rows=new ArrayList<>(List.of(storage(icon(Items.BARREL),"Storage",containers,"")));
        totals.entrySet().stream().sorted(Map.Entry.<Item,Integer>comparingByValue().reversed()).limit(PanelView.MAX_ROWS-1)
                .forEach(entry -> rows.add(new Row(examples.get(entry.getKey()),examples.get(entry.getKey()).getHoverName(),
                        Component.literal("× "+entry.getValue()),0,PanelView.NO_BAR,PanelView.NO_VALUE)));
        return rows;
    }
    /** Detected resources and the job's state, one row each. */
    public static List<Row> status(ServerLevel level,Settlement town,Station station) {
        List<Row> rows=new ArrayList<>();
        switch(station.role()) {
            case HOUSING,BARRACKS -> rows.add(new Row(stationIcon(station.role()),"Beds",SettlementService.beds(level,town,station).size()+" complete beds in range house citizens"));
            case HOSPITAL -> rows.add(new Row(stationIcon(StructureRole.HOSPITAL),"Patient beds",SettlementService.beds(level,town,station).size()
                    +" beds; injured citizens stay until full health; +1 health every 5s. A funded medic can assist with meals and paper."));
            case WAREHOUSE -> rows.add(storage(icon(Items.CHEST),"Storage",SettlementService.storageAt(level,town,station.position()),"Put chests or barrels within "+station.radius()+" blocks"));
            case FARM -> rows.add(new Row(icon(Items.WHEAT),"Crops",SettlementService.workBlocks(level,town,station)+" ripe crops in range"));
            case HUNTER -> rows.add(new Row(icon(Items.IRON_SWORD),"Hunting","Hunts unprotected adults within "+AnimalWork.huntingRadius(station)+" blocks; needs a sword or axe in its barrel"));
            case FISHERMAN -> rows.add(new Row(icon(Items.FISHING_ROD),"Fishing","Needs a rod and a dry bank beside open, two-block-deep water in range; "+Config.FISHING_SECONDS.get()+"s per catch"));
            case ANIMAL_KEEPER -> rows.add(new Row(icon(Items.WHEAT),"Animals","Breeds with real feed; keeps "+Config.ANIMAL_BREEDERS.get()+" adults per species; needs a sword or axe for surplus adults"));
            case BUTCHER -> rows.add(new Row(icon(Items.BEEF),"Butchery","Prepares carcasses from its barrel into raw portions; needs an axe; couriers take meat to cooks"));
            case LUMBER -> rows.add(new Row(icon(Items.OAK_SAPLING),"Forest","Fells whole natural trees and replants saplings in range"));
            case MINE -> {
                BlockPos vein=OreVeins.find(level,town,station);
                if(vein!=null) {
                    var ore=level.getBlockState(vein);
                    long wait=Math.max(0,OreVeins.readyAt(level,vein)-level.getGameTime());
                    rows.add(new Row(new ItemStack(ore.getBlock()),ore.getBlock().getName(),Component.literal("Endless vein at "+vein.toShortString()
                            +(wait>0 ? ", replenishes in "+(wait+19)/20+"s" : ", ready")),0,PanelView.NO_BAR,PanelView.NO_VALUE));
                    CitizenEntity miner=SettlementService.workers(level).members(station.position(),level.getGameTime()).stream().map(level::getEntity)
                            .filter(CitizenEntity.class::isInstance).map(CitizenEntity.class::cast).findFirst().orElse(null);
                    ItemStack pickaxe=miner==null ? ItemStack.EMPTY : miner.getMainHandItem();
                    rows.add(new Row(pickaxe.isEmpty() ? icon(Items.IRON_PICKAXE) : pickaxe.copyWithCount(1),"Replenishment",
                            "About "+(OreVeins.interval(ore,Config.ORE_VEIN_SECONDS.get(),pickaxe)+19)/20+"s with "
                            +(pickaxe.isEmpty() ? "a stone pickaxe (baseline)" : pickaxe.getHoverName().getString())+"; needs a pickaxe that can mine this ore"));
                } else rows.add(new Row(icon(Items.STONE_PICKAXE),"Tunnels",ExcavationService.status(level,town,station)));
                if(vein==null) rows.add(new Row(icon(Items.RAW_IRON),"Ore vein","Place the station within "+OreVeins.REACH+" blocks of an exposed ore to mine it forever instead"));
            }
            case QUARRY -> rows.add(new Row(icon(Items.IRON_PICKAXE),"Excavation",ExcavationService.status(level,town,station)));
            case GUARD -> {
                GuardPosts plan=GuardService.posts(level,station);
                rows.add(new Row(icon(plan.role().equals(GuardPosts.ARCHER) ? Items.BOW : plan.role().equals(GuardPosts.SHIELD) ? Items.SHIELD : Items.IRON_SWORD),
                        GuardPosts.title(plan.role()),GuardPosts.describe(plan.role())));
                rows.add(new Row(icon(Items.COMPASS),"Posts",GuardService.status(level,station)));
                if(plan.patrol().isEmpty()) rows.add(new Row(icon(Items.MAP),"Patrol","No marked route: the guard walks rounds past the town's stations"));
                for(int n=0;n<plan.patrol().size();n++) rows.add(new Row(icon(Items.MAP),"Patrol point "+(n+1),plan.patrol().get(n).toShortString()));
                boolean tower=GuardRoles.watchtower(level,station.position());
                rows.add(new Row(icon(Items.SPYGLASS),tower ? "Watchtower" : "Not a watchtower",tower
                        ? "Raised "+GuardRoles.TOWER_HEIGHT+"+ blocks above the ground: sees hostiles from "+GuardRoles.towerSight(town)+" blocks and warns of approaching ones"
                        : "Build the station at least "+GuardRoles.TOWER_HEIGHT+" blocks above the surrounding ground for earlier warnings"));
            }
            case SMELTERY -> rows.add(new Row(icon(Items.FURNACE),"Furnaces",SettlementService.processingDevices(level,town,station).size()+" furnaces or blast furnaces in range"));
            case COOK -> rows.add(new Row(icon(Items.SMOKER),"Kitchen",SettlementService.processingDevices(level,town,station).size()+" furnaces, smokers or lit campfires; bread "+(town.disabledRecipes.contains("bread") ? "off" : "on")));
            case BLACKSMITH -> {
                rows.add(new Row(icon(Items.ANVIL),"Blacksmith",SettlementService.anvils(level,town,station).size()+" anvils; repairs damaged equipment and fills forge orders"));
                rows.add(new Row(icon(Items.FURNACE),"Forge heat",ForgeWorkshop.heat(level,town,station).size()+" furnaces; new equipment and alloys also need coal/charcoal"));
                for(BlockPos at:SettlementService.anvils(level,town,station)) if(level.getBlockState(at).getBlock() instanceof io.github.swishhyy.wwmc.block.BronzeAnvilBlock) {
                    String condition=switch(level.getBlockState(at).getValue(io.github.swishhyy.wwmc.block.BronzeAnvilBlock.WEAR)/4) {
                        case 0 -> "Fresh"; case 1 -> "Chipped"; default -> "Damaged";
                    };
                    rows.add(new Row(icon(WWMC.BRONZE_ANVIL_ITEM.get()),"Bronze anvil",condition+" · 65% slower than iron · "+at.toShortString()));
                }
            }
            case GATHERER -> rows.add(new Row(icon(Items.STONE_SHOVEL),"Gathering","Needs a shovel; harvests cane and bamboo tops, plus dry exposed sand, gravel and clay. Plant bases and construction are preserved."));
            case CRAFTSMAN -> rows.add(new Row(icon(Items.CRAFTING_TABLE),"Orders",town.craftOrders.size()+" learned recipes"));
            case COURIER -> rows.add(new Row(icon(Items.BUNDLE),"Deliveries","The only town haulers: collect job goods and deliver tools, materials, carcasses and feed through the warehouse"));
            case TRADER -> rows.add(new Row(icon(Items.COMPASS),"Trade route",town.trading.status));
            case ENCHANTER -> rows.addAll(enchanter(level,town,station));
        }
        if(station.role().keepsJobStorage()) {
            List<Container> barrels=SettlementService.jobStorage(level,town,station);
            rows.add(storage(icon(Items.BARREL),"Job barrels",barrels,"None: a barrel within "+station.radius()+" blocks keeps tools, supplies and goods here"));
        }
        return rows;
    }
    /** The table and its level, lapis, the queue and the item on the table. */
    private static List<Row> enchanter(ServerLevel level,Settlement town,Station station) {
        List<Row> rows=new ArrayList<>();
        int cap=Config.ENCHANTER_MAX_LEVEL.get();
        List<BlockPos> tables=SettlementService.enchantingTables(level,town,station);
        if(tables.isEmpty()) rows.add(new Row(icon(Items.ENCHANTING_TABLE),"Enchanting table","None within "+station.radius()+" blocks: place one in range"));
        else {
            BlockPos table=tables.getFirst();
            int power=Enchanting.power(level,table);
            rows.add(new Row(icon(Items.ENCHANTING_TABLE),"Enchanting table",power+" bookshelves around it: about level "+Enchanting.typicalLevel(power,cap)
                    +", never above "+cap).bar(Enchanting.typicalLevel(power,cap)/30F,Enchanting.typicalLevel(power,cap)>=cap ? GREEN : AMBER));
        }
        List<Container> local=SettlementService.jobStorage(level,town,station),stored=SettlementService.storage(level,town);
        int lapis=InventoryOps.count(local,Enchanting::lapis)+InventoryOps.count(stored,Enchanting::lapis);
        rows.add(new Row(icon(Items.LAPIS_LAZULI),"Lapis lazuli",lapis+" in this station's barrels and the warehouse; 1 to 3 per item by level")
                .bar(Math.min(1F,lapis/27F),lapis==0 ? RED : lapis<9 ? AMBER : GREEN));
        int waiting=Enchanting.count(local,stack -> false)+Enchanting.count(stored,stack -> false);
        rows.add(new Row(icon(Items.BOOK),"Waiting",waiting+" unenchanted items and books; armor and weapons go first, then tools, then books"));
        rows.add(new Row(icon(Items.CLOCK),"Work time",Config.ENCHANT_MINUTES.get()+" min for a book or common item; iron or gold ×1.3, diamond ×1.6, netherite ×2"));
        for(UUID id:SettlementService.workers(level).members(station.position(),level.getGameTime()))
            if(level.getEntity(id) instanceof CitizenEntity citizen && !citizen.enchanting().isEmpty()) {
                ItemStack item=citizen.enchanting();
                float progress=citizen.enchantProgress();
                rows.add(new Row(item.copy(),item.getHoverName().copy(),Component.literal(Math.round(progress*100)+"% done"
                        +(citizen.enchantLevel()>0 ? " at level "+citizen.enchantLevel() : "")),0,progress,PanelView.NO_VALUE));
            }
        return rows;
    }
    public static void stationAction(ServerPlayer player,BlockPos pos,int action) {
        Station station=stationAt(player,pos);
        if(station==null) return;
        Settlement town=owned(player,pos);
        if(action==TownViews.PRODUCTION || action==TownViews.RESEARCH) {
            TownViews.open(player,town,action==TownViews.PRODUCTION ? PanelMenu.Kind.PRODUCTION : PanelMenu.Kind.RESEARCH,pos); return;
        }
        if(action==RANGE_UP || action==CREW_UP || action==YIELD_UP) SettlementService.upgradeStation(level(player),player,town,station,
                action==RANGE_UP ? Upgrades.Kind.RANGE : action==CREW_UP ? Upgrades.Kind.CREW : Upgrades.Kind.YIELD);
        else if(action==BREAD && station.role()==StructureRole.COOK) {
            if(!town.disabledRecipes.remove("bread")) town.disabledRecipes.add("bread");
            dirty(player);
        } else if(action==POSTS && station.role()==StructureRole.GUARD) {
            player.closeContainer();
            GuardService.begin(level(player),player,pos);
        } else if((action==GUARD_ROLE || action==PATROL_CLEAR) && station.role()==StructureRole.GUARD) {
            WorldWorkData data=WorldWorkData.get(level(player));
            GuardPosts plan=GuardService.posts(level(player),station);
            data.guardPosts.put(pos,action==PATROL_CLEAR ? plan.withPatrol(List.of())
                    : plan.withRole(GuardPosts.ROLES.get((GuardPosts.ROLES.indexOf(plan.role())+1)%GuardPosts.ROLES.size())));
            data.setDirty();
        } else if(action==PATROL && station.role()==StructureRole.GUARD) {
            player.closeContainer();
            GuardService.beginPatrol(level(player),player,pos);
        } else if(action==JOB_STEP && station.role().providesWork()) {
            SettlementService.setJobLevel(level(player),town,station.role(),(town.jobs.level(station.role())+1)%(JobBoard.HIGH+1));
        }
    }

    // ---------- Craftsman ----------
    public static PanelView craftsman(ServerPlayer player,BlockPos pos,String feedback) {
        Station station=stationAt(player,pos);
        return station==null ? null : craftsman(level(player),owned(player,pos),station,feedback);
    }
    public static PanelView craftsman(ServerLevel level,Settlement town,Station station,String feedback) {
        List<Container> stock=SettlementService.townStorage(level,town);
        Workshop.Recipes recipes=Workshop.Recipes.of(level);
        List<Row> orders=new ArrayList<>();
        for(Workshop.Order order:town.craftOrders) {
            Item item=order.resolve();
            int have=Workshop.stock(stock,order);
            boolean materials=Workshop.plans(recipes,order).stream().anyMatch(plan -> Workshop.batches(recipes,town.craftOrders,order,plan,stock,1)>0);
            String note=item==Items.AIR ? "Unknown item" : ForgeWorkshop.forged(new ItemStack(item)) ? "Order from the blacksmith in Production / Forge" : order.target()==0 ? "Paused" : have>=order.target() ? "Stocked"
                    : materials ? "Ready to craft" : "Missing materials";
            int color=order.target()==0 ? GRAY : have>=order.target() ? GREEN : materials ? AMBER : RED;
            orders.add(new Row(new ItemStack(item),new ItemStack(item).getHoverName().copy().append(order.anyWood() ? " (any wood)" : ""),
                    Component.literal(have+" in town · "+note),color,order.target()==0 ? 0 : have/(float)order.target(),order.target()));
        }
        return new PanelView(Component.literal("Craftsman Station · "+crew(town,station)),
                Component.literal(feedback.isEmpty() ? "Click the slot with an item, or shift-click one, to teach its recipe" : feedback),
                List.of(new Tab("Orders",orders),new Tab("Crew",crewRows(level,town,station))),List.of());
    }
    /** How a craft order's row is named: its item's id, which the screen also reads from the row's icon. */
    public static String rowKey(Workshop.Order order) { return BuiltInRegistries.ITEM.getKey(order.resolve()).toString(); }
    public static String teach(ServerPlayer player,BlockPos pos,ItemStack example) {
        Station station=stationAt(player,pos);
        if(station==null || station.role()!=StructureRole.CRAFTSMAN) return "";
        Settlement town=owned(player,pos);
        int before=town.craftOrders.size();
        String result=Workshop.learn(Workshop.Recipes.of(level(player)),town,example);
        if(town.craftOrders.size()!=before) dirty(player);
        return result;
    }
    /** The row's item key must still name the order at that index; a click on a list that has changed is dropped. */
    public static void craftAction(ServerPlayer player,BlockPos pos,int action,int index,int value,String key) {
        Station station=stationAt(player,pos);
        if(station==null || station.role()!=StructureRole.CRAFTSMAN) return;
        Settlement town=owned(player,pos);
        List<Workshop.Order> orders=town.craftOrders;
        if(index<0 || index>=orders.size() || !key.equals(rowKey(orders.get(index)))) return;
        switch(action) {
            case TARGET -> orders.set(index,orders.get(index).withTarget(value));
            case RAISE -> { if(index>0) Collections.swap(orders,index,index-1); }
            case FORGET -> orders.remove(index);
            default -> { return; }
        }
        dirty(player);
    }

    // ---------- Citizens ----------
    public static PanelView citizen(CitizenEntity citizen) {
        ServerLevel level=(ServerLevel)citizen.level();
        Settlement town=citizen.town(level);
        Station job=town==null ? null : home(town,citizen);
        List<Row> status=new ArrayList<>();
        StructureRole role=citizen.skillRole();
        int skill=citizen.skillLevel(role);
        status.add(new Row(job==null ? icon(Items.PAPER) : stationIcon(job.role()),citizen.isBaby() ? "Child · grows up in "+Math.max(1,(-citizen.getAge()+1199)/1200)+" min" : job==null ? "No job"
                : CitizenSkill.title(skill)+" "+job.role().title().toLowerCase(Locale.ROOT)+" at "+job.position().toShortString(),citizen.activity()));
        float health=citizen.getHealth()/Math.max(1,citizen.getMaxHealth());
        status.add(new Row(icon(Items.GOLDEN_APPLE),"Health",Math.round(citizen.getHealth())+" / "+Math.round(citizen.getMaxHealth())).bar(health,health<0.5F ? RED : GREEN));
        int meal=Math.max(0,citizen.mealTicks());
        status.add(new Row(icon(Items.BREAD),"Next meal",meal==0 ? "Hungry now" : "In about "+Math.max(1,meal/1200)+" min").bar(meal/(float)Math.max(1,Config.mealIntervalTicks()),meal==0 ? RED : AMBER));
        if(citizen.overflowing()) status.add(new Row(icon(Items.CHEST),Component.literal("Overflow"),Component.literal("Carrying a harvest larger than the bag; it waits for delivery"),
                0,PanelView.NO_BAR,PanelView.NO_VALUE,"overflow"));
        List<Row> skills=new ArrayList<>();
        List<String> meals=citizen.recentMeals();
        int morale=MealVariety.bonus(meals);
        skills.add(new Row(icon(Items.EXPERIENCE_BOTTLE),(citizen.isBaby() ? "Child" : role==null ? "No job" : CitizenSkill.title(skill)+" "+role.title().toLowerCase(Locale.ROOT))+" · happiness "+citizen.happiness()+"/100",
                (role==null ? "" : CitizenSkill.perk(role,skill)+"; ")+(morale>0 ? "varied meals: "+morale+"% faster" : "varied meals would add up to 8% speed")));
        if(town!=null) {
            var outlook=CitizenWellbeing.outlook(citizen,CitizenWellbeing.conditions(level,town));
            skills.add(new Row(icon(Items.APPLE),"Happiness: "+CitizenWellbeing.mood(citizen.happiness()),outlook.reason()+" · gradually moving toward "+outlook.target()+"/100"));
        }
        int toolBonus=WorkerTools.bonus(role,citizen.getMainHandItem());
        if(toolBonus>0) skills.add(new Row(citizen.getMainHandItem().copy(),"Tool quality: +"+toolBonus+"% work speed","Better axes, hoes and shovels improve suitable gathering work. Mining already uses the pickaxe's actual break speed."));
        for(StructureRole known:StructureRole.values()) {
            int points=citizen.experience(known);
            if(points<=0 && known!=role) continue;
            int rank=CitizenSkill.level(points),next=CitizenSkill.next(rank);
            skills.add(new Row(stationIcon(known),known.title()+": "+CitizenSkill.title(rank),points+(next<0 ? " experience, the top level" : " / "+next+" experience")+" · "+CitizenSkill.perk(known,rank))
                    .bar(next<0 ? 1F : points/(float)next,rank>=CitizenSkill.MAX_LEVEL ? GREEN : AMBER));
        }
        skills.add(new Row(icon(Items.COOKED_BEEF),"Diet: "+MealVariety.mood(meals),meals.isEmpty() ? "No meals yet"
                : MealVariety.distinct(meals)+" kinds in the last "+meals.size()+" meals: "+String.join(", ",meals.stream().map(id -> id.replace("minecraft:","").replace('_',' ')).toList())));
        if(citizen.tradeCargoCount()>0) skills.add(new Row(icon(Items.BUNDLE),"Trade load",citizen.tradeCargoCount()+" items reserved for the destination; separate from meals and job supplies"));
        List<Row> gear=new ArrayList<>();
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            ItemStack worn=citizen.getItemBySlot(slot);
            if(!worn.isEmpty()) gear.add(new Row(worn.copy(),worn.getHoverName(),Component.literal(slot.getName()),0,
                    worn.isDamageableItem() ? 1F-worn.getDamageValue()/(float)worn.getMaxDamage() : PanelView.NO_BAR,PanelView.NO_VALUE));
        }
        return new PanelView(citizen.getName(),Component.literal(town==null ? "" : town.name),List.of(new Tab("Status",status),new Tab("Equipment",gear),new Tab("Skills",skills)),List.of());
    }
}
