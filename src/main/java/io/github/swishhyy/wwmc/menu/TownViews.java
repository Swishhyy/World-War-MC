package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

/** Focused banner pages. Every row is authorized against its actual town and current item key. */
public final class TownViews {
    public static final int PEOPLE=1000,PRODUCTION=1001,RESEARCH=1002,BACK=1003,ROW_ACTION=1004;
    private TownViews() {}
    private static PanelView.Row row(ItemLike item,String title,String detail) { return new PanelView.Row(new ItemStack(item),title,detail); }
    private static PanelView.Row target(Item item,String title,String detail,String key,int target) {
        return new PanelView.Row(new ItemStack(item),Component.literal(title),Component.literal("Target "+target+" · "+detail),0,-1,target,key);
    }
    public static boolean focused(PanelMenu.Kind kind) { return kind==PanelMenu.Kind.PEOPLE || kind==PanelMenu.Kind.PRODUCTION || kind==PanelMenu.Kind.RESEARCH; }
    public static void open(ServerPlayer player,Settlement town,PanelMenu.Kind kind) { open(player,town,kind,town.center); }
    public static void open(ServerPlayer player,Settlement town,PanelMenu.Kind kind,BlockPos anchor) {
        if(!focused(kind) || !valid(player,anchor)) return;
        PanelView view=build((ServerLevel)player.level(),town,player,kind);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,kind,anchor,player,view),view.title()),
                buf -> PanelMenu.write(buf,kind,anchor,view));
    }
    public static boolean valid(ServerPlayer player,BlockPos pos) {
        var level=(ServerLevel)player.level(); Settlement town=SettlementData.get(level).at(pos);
        if(!player.isAlive() || !TownAccess.manages(town,player.getUUID()) || player.distanceToSqr(Vec3.atCenterOf(pos))>64 || !level.hasChunkAt(pos)) return false;
        return town.center.equals(pos) ? level.getBlockState(pos).is(WWMC.BANNER.get()) : town.station(pos)!=null && SettlementService.active(level,town.station(pos));
    }
    public static PanelView view(ServerPlayer player,BlockPos pos,PanelMenu.Kind kind) {
        return valid(player,pos) ? build((ServerLevel)player.level(),SettlementData.get((ServerLevel)player.level()).at(pos),player,kind) : null;
    }
    public static PanelView build(ServerLevel level,Settlement town,ServerPlayer player,PanelMenu.Kind kind) {
        var tabs=new ArrayList<PanelView.Tab>();
        var actions=new ArrayList<PanelView.Action>(); actions.add(new PanelView.Action(BACK,"Back",true));
        if(kind==PanelMenu.Kind.PEOPLE) {
            var citizens=new ArrayList<PanelView.Row>();
            DefenseService.loadedCitizens(level,town).stream().sorted(Comparator.comparing(c -> c.getName().getString())).forEach(c -> citizens.add(Panels.person(town,c)));
            if(citizens.isEmpty()) citizens.add(row(WWMC.STATION_ITEMS.get(StructureRole.HOUSING).get(),"No loaded citizens","Provide housing beds, then recruit a citizen."));
            var conditions=CitizenWellbeing.conditions(level,town); int happiness=CitizenWellbeing.average(level,town);
            var housing=List.of(row(WWMC.STATION_ITEMS.get(StructureRole.HOUSING).get(),"Population",town.citizens.size()+" / "+SettlementService.populationLimit(town)
                            +" citizens; "+SettlementService.housingBeds(level,town).size()+" usable housing beds"),
                    row(Items.APPLE,"Happiness: "+happiness+" / 100",CitizenWellbeing.mood(happiness)+" · varied meals eaten, enough housing, safety and amenity types. Happiness changes gradually."),
                    row(Items.BOOKSHELF,"Housing amenities",conditions.amenities().isEmpty() ? "Add a garden, bell, lit campfire or books to furnished housing. Each type counts once." : String.join(", ",conditions.amenities())+". Each type counts once."),
                    new PanelView.Row(new ItemStack(Items.BED.red()),Component.literal("Children: "+town.progress.children.size()),
                            Component.literal(PopulationGrowth.pause(level,town)+". A birth uses 6 meals; a child grows up in 20 loaded minutes."),0,-1,0,
                            town.progress.growthEnabled ? "act:growth:pause" : "act:growth:resume"),
                    row(Items.PAPER,"Population research","+"+Research.populationBonus(town)+" places from housing discoveries"));
            tabs.add(new PanelView.Tab("Citizens",citizens)); tabs.add(new PanelView.Tab("Jobs",Panels.jobRows(town,TownJobs.assess(level,town))));
            tabs.add(new PanelView.Tab("Wellbeing",housing));
            actions.add(new PanelView.Action(Panels.RECRUIT,"Recruit citizen",Panels.vacancies(level,town)>0));
            actions.add(Panels.grow(town,player)); actions.add(new PanelView.Action(Panels.PRIORITY,"Preset: "+town.priority,true));
        } else if(kind==PanelMenu.Kind.PRODUCTION) {
            var stock=new ArrayList<PanelView.Row>();
            stock.add(row(Items.CHEST,"Warehouse requests","Targets reserve your own supplies and guide incoming trade; workshop and forge targets make goods."));
            stock.add(row(Items.SUGAR_CANE,"Paper chain","Gatherer harvests cane; craftsman makes paper; researcher writes scrolls using paper and ink or charcoal."));
            stock.add(row(Items.COAL,"Metal chain","Miners gather ore; smelters refine it and make bronze/steel in alloy furnaces. Blacksmiths forge equipment and repair it."));
            var requested=new LinkedHashSet<Item>(List.of(Items.BREAD,Items.OAK_LOG,Items.COBBLESTONE,Items.SAND,Items.GRAVEL,Items.CLAY_BALL,Items.SUGAR_CANE,Items.PAPER,Items.COAL,
                    Items.CHARCOAL,Items.COPPER_INGOT,WWMC.TIN_INGOT.get(),WWMC.BRONZE_INGOT.get(),Items.IRON_INGOT,WWMC.RESEARCH_SCROLL.get()));
            for(String id:town.campaign.requests.keySet()) { Item item=SupplyRequests.item(id); if(item!=Items.AIR) requested.add(item); }
            for(Item item:requested) {
                String id=BuiltInRegistries.ITEM.getKey(item).toString();
                stock.add(target(item,new ItemStack(item).getHoverName().getString(),InventoryOps.count(SettlementService.storage(level,town),s -> s.is(item))+" in warehouse · trade reserve",
                        "request:"+id,town.campaign.requests.getOrDefault(id,0)));
            }
            var workshop=new ArrayList<PanelView.Row>();
            workshop.add(row(Items.CRAFTING_TABLE,"Automatic workshop","Set a stock target. Craftsmen make these from real recipes; couriers supply ingredients and collect results. Zero pauses the order."));
            var items=new LinkedHashSet<Item>();
            for(var order:town.craftOrders) if(!ForgeWorkshop.forged(new ItemStack(order.resolve()))) items.add(order.resolve());
            items.add(Items.PAPER); items.add(Items.STONE_SHOVEL); items.add(Items.STONE_HOE); items.add(WWMC.BANNER_ITEM.get()); items.add(WWMC.BRONZE_ANVIL_ITEM.get());
            items.add(Items.FURNACE); items.add(Items.LECTERN); items.add(Items.BARREL); items.add(Items.CHEST);
            items.add(WWMC.ALLOY_FURNACE_ITEM.get());
            for(var role:StructureRole.values()) items.add(WWMC.STATION_ITEMS.get(role).get());
            for(Item item:items) {
                String id=BuiltInRegistries.ITEM.getKey(item).toString(); int index=Workshop.find(town,id),value=index<0 ? 0 : town.craftOrders.get(index).target();
                String note=AgeProgression.allowed(town,new ItemStack(item)) ? "" : " · needs "+AgeProgression.requirement(new ItemStack(item));
                workshop.add(target(item,new ItemStack(item).getHoverName().getString(),InventoryOps.count(SettlementService.townStorage(level,town),s -> s.is(item))+" in town"+note,"craft:"+id,value));
            }
            var forge=new ArrayList<PanelView.Row>();
            forge.add(row(WWMC.ALLOY_FURNACE_ITEM.get(),"Alloys at the smeltery","Use an alloy furnace with two material inputs and separate fuel. A smelter services it; couriers supply its job barrel. Bronze: 3 copper + 1 tin → 4 ingots. Steel: 1 iron + 1 coal/charcoal → 1 ingot."));
            for(Item item:AlloyWorkshop.catalogue()) {
                int value=AlloyWorkshop.target(town,item);
                if(value==Integer.MAX_VALUE) continue;
                String id=BuiltInRegistries.ITEM.getKey(item).toString();
                String note=AgeProgression.allowed(town,new ItemStack(item)) ? "" : " · needs "+AgeProgression.requirement(new ItemStack(item));
                forge.add(target(item,new ItemStack(item).getHoverName().getString(),InventoryOps.count(SettlementService.townStorage(level,town),s -> s.is(item))+" in town"+note,"alloy:"+id,value));
            }
            forge.add(row(WWMC.BRONZE_ANVIL_ITEM.get(),"Equipment at the blacksmith","Anvil, nearby furnace, job barrel and coal/charcoal required. Repairs take priority. Bronze anvils work 65% slower than iron and wear normally."));
            for(Item item:ForgeWorkshop.catalogue()) {
                String id=BuiltInRegistries.ITEM.getKey(item).toString();
                if(ForgeWorkshop.plans(level,new Workshop.Order(id,1)).isEmpty()) continue;
                int value=town.progress.forgeOrders.stream().filter(o -> o.item().equals(id)).mapToInt(Workshop.Order::target).findFirst().orElse(0);
                String note=AgeProgression.allowed(town,new ItemStack(item)) ? "" : " · needs "+AgeProgression.requirement(new ItemStack(item));
                forge.add(target(item,new ItemStack(item).getHoverName().getString(),InventoryOps.count(SettlementService.townStorage(level,town),s -> s.is(item))+" in town"+note,"forge:"+id,value));
            }
            var stations=new ArrayList<PanelView.Row>();
            for(var station:town.stations) stations.add(Panels.summary(level,town,station));
            tabs.add(new PanelView.Tab("Stock",stock)); tabs.add(new PanelView.Tab("Workshop",workshop));
            tabs.add(new PanelView.Tab("Metalwork",forge)); tabs.add(new PanelView.Tab("Stations",stations));
        } else {
            var research=new ArrayList<PanelView.Row>(); var completed=new ArrayList<PanelView.Row>();
            research.add(target(WWMC.RESEARCH_SCROLL.get(),"Research scrolls: "+Research.scrolls(level,town),"Set the warehouse target. Each scroll costs 2 paper + 1 ink sac or charcoal and 30 seconds of lectern work.","scroll:target",town.progress.scrollTarget));
            research.add(Panels.researchStatus(Research.status(level,town)));
            if(Research.project(town)!=null) research.add(row(Items.BOOK,"Saved research project",Research.progress(town)+". Its old supplies are already paid; it will finish without using scrolls."));
            for(var tech:Research.ALL) {
                if(Research.has(town,tech.id())) { completed.add(row(Items.ENCHANTED_BOOK,tech.title(),tech.benefit())); continue; }
                boolean active=tech.id().equals(town.progress.project); String missing=active ? "Finish the paid project at a lectern" : Research.missing(level,town,tech);
                String costs=String.join(", ",tech.costs().stream().map(c -> c.count()+" "+c.name()).toList());
                research.add(new PanelView.Row(new ItemStack(Items.BOOK),Component.literal(tech.title()+": "+(missing.isEmpty() ? "ready" : "waiting")),
                        Component.literal(tech.benefit()+". Costs "+costs+(missing.isEmpty() ? "" : ". "+missing)),missing.isEmpty() ? Panels.GREEN : Panels.AMBER,-1,
                        missing.isEmpty() ? 0 : 1,"act:research:"+tech.id()));
            }
            tabs.add(new PanelView.Tab("Discoveries",research)); tabs.add(new PanelView.Tab("Completed",completed));
        }
        return new PanelView(Component.literal(town.name+" · "+(kind==PanelMenu.Kind.PEOPLE ? "People" : kind==PanelMenu.Kind.PRODUCTION ? "Production" : "Research")),
                Component.literal(kind==PanelMenu.Kind.PEOPLE ? "Citizens, work and housing" : kind==PanelMenu.Kind.PRODUCTION ? "Real supplies, stock targets and village production" : "Researcher-made scrolls unlock discoveries for settlement members"),tabs,actions);
    }
    public static void act(ServerPlayer player,BlockPos pos,PanelMenu.Kind kind,int action,int value,String key) {
        if(!valid(player,pos)) return;
        var level=(ServerLevel)player.level(); Settlement town=SettlementData.get(level).at(pos);
        if(action==BACK) { if(pos.equals(town.center)) Panels.openTown(player,town); else Panels.openStation(player,town,town.station(pos)); return; }
        if(kind==PanelMenu.Kind.PEOPLE) {
            if(action==ROW_ACTION && (town.progress.growthEnabled ? "act:growth:pause" : "act:growth:resume").equals(key)) {
                town.progress.growthEnabled=!town.progress.growthEnabled; SettlementData.get(level).setDirty();
            }
            else if(action==Panels.RECRUIT) SettlementService.recruit(level,town,1);
            else if(action==Panels.GROW) SettlementService.upgradePopulation(level,player,town);
            else if(action==Panels.PRIORITY) SettlementService.applyPreset(level,town,JobBoard.PRESETS.get((JobBoard.PRESETS.indexOf(town.priority)+1)%JobBoard.PRESETS.size()));
            else if(action==ROW_ACTION && value>=JobBoard.OFF && value<=JobBoard.HIGH)
                for(var role:StructureRole.values()) if(role.providesWork() && role.id().equals(key)) SettlementService.setJobLevel(level,town,role,value);
            return;
        }
        if(action!=ROW_ACTION || key.length()>256) return;
        if(kind==PanelMenu.Kind.RESEARCH) {
            if(key.equals("scroll:target") && value>=0 && value<=256) town.progress.scrollTarget=value;
            else if(key.startsWith("act:research:")) SettlementService.notify(player,Research.study(level,town,key.substring(13)));
            else return;
        } else if(kind==PanelMenu.Kind.PRODUCTION) {
            if(key.startsWith("request:")) { if(!SupplyRequests.set(town,key.substring(8),value)) return; }
            else if(value<0 || value>256) return;
            else if(key.startsWith("alloy:")) { if(!AlloyWorkshop.order(town,key.substring(6),value)) return; }
            else if(key.startsWith("forge:")) { if(!ForgeWorkshop.order(level,town,key.substring(6),value)) return; }
            else if(key.startsWith("craft:")) {
                String id=key.substring(6); Item item=SupplyRequests.item(id); if(item==Items.AIR || ForgeWorkshop.forged(new ItemStack(item))) return;
                var order=new Workshop.Order(id,value); if(Workshop.plans(Workshop.Recipes.of(level),order).isEmpty()) return;
                int index=Workshop.find(town,id);
                if(index>=0) town.craftOrders.set(index,order);
                else { if(town.craftOrders.size()>=Workshop.MAX_ORDERS) return; town.craftOrders.add(order); }
            } else return;
        } else return;
        SettlementData.get(level).setDirty();
    }
}
