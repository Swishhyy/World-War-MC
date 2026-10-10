package io.github.swishhyy.wwmc.settlement;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.block.SettlementBannerBlock;
import io.github.swishhyy.wwmc.core.ReservationBook;
import io.github.swishhyy.wwmc.core.WorkforceBook;
import io.github.swishhyy.wwmc.core.MiningLayout;
import io.github.swishhyy.wwmc.core.CitizenNames;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.ShiftClock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.Panels;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class SettlementService {
    private static final Map<ServerLevel,ReservationBook<BlockPos>> RESERVATIONS=new WeakHashMap<>();
    private static final Map<ServerLevel,WorkforceBook<BlockPos>> WORKFORCE=new WeakHashMap<>();
    /** Cache locations briefly, never inventory contents or loaded chunk references. */
    private static final Map<ServerLevel,StationResourceCache> RESOURCE_SCANS=new WeakHashMap<>();
    private static List<BlockPos> resourcePositions(ServerLevel level,Settlement town,Station station,String kind,Supplier<List<BlockPos>> scan) {
        return RESOURCE_SCANS.computeIfAbsent(level,l -> new StationResourceCache()).positions(town,station,kind,level.getGameTime(),scan);
    }
    private static void refreshResources(ServerLevel level,Settlement town) {
        StationResourceCache scans=RESOURCE_SCANS.get(level);
        if(scans!=null) scans.refresh(town.id);
    }
    public static WorkforceBook<BlockPos> workers(ServerLevel level) { return WORKFORCE.computeIfAbsent(level,l -> new WorkforceBook<>()); }
    /** One worker per job block; only quarries use a configured crew and crew upgrades. */
    public static int workerLimit(Station station) {
        if(!station.role().providesWork()) return 0;
        return station.role()==StructureRole.QUARRY ? Config.QUARRY_WORKERS.get()+station.crew() : 1;
    }
    /** NPC crews spread their small population across all essential jobs. */
    public static int workerLimit(Settlement town,Station station) {
        if(station.role()==StructureRole.HOSPITAL && !town.campaign.projects.contains("hospital")) return 0;
        return town.trading.npc && station.role().providesWork() ? 1 : workerLimit(station);
    }
    /** Sets every job's priority from a preset. Citizens keep their jobs unless a job of higher priority has an open place. */
    public static void applyPreset(ServerLevel level,Settlement town,String preset) {
        town.priority=preset; town.jobs.apply(preset);
        SettlementData.get(level).setDirty();
    }
    /** Changes one job's priority; switching a job off sends its crew to look for other work. */
    public static void setJobLevel(ServerLevel level,Settlement town,StructureRole role,int value) {
        town.jobs.setLevel(role,value);
        if(value==JobBoard.OFF) town.jobs.releaseRole(town,role);
        town.priority=town.jobs.matchingPreset();
        SettlementData.get(level).setDirty();
    }
    public static ReservationBook<BlockPos> reservations(ServerLevel level) {
        return RESERVATIONS.computeIfAbsent(level, l -> new ReservationBook<>());
    }
    public static void tell(Player player,String text) {
        if(player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text));
    }
    /** A short notice above the hotbar instead of a chat line. */
    public static void notify(Player player,String text) {
        if(player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.literal(text));
    }
    public static boolean owns(Player player,Settlement settlement) { return TownAccess.manages(settlement,player.getUUID()); }
    public static boolean night(ServerLevel level) {
        return ShiftClock.night(level.clockManager().getTotalTicks(level.registryAccess().getOrThrow(WorldClocks.OVERWORLD)));
    }

    public static void foundOrInspect(ServerLevel level,Player player,BlockPos pos) {
        if(!level.dimension().equals(Level.OVERWORLD)) { notify(player,"Settlements currently belong in the Overworld."); return; }
        SettlementData data=SettlementData.get(level);
        Settlement present=data.at(pos);
        if(present!=null) {
            if(player instanceof ServerPlayer visitor && present.center.equals(pos) && !present.trading.npc
                    && !TownAccess.builds(present,player.getUUID()) && !TownAccess.invited(present,player.getUUID())) {
                MultiplayerViews.open(visitor,present); return;
            }
            if(player instanceof ServerPlayer viewer && (TownAccess.builds(present,player.getUUID()) || TownAccess.invited(present,player.getUUID()))) {
                if(owns(player,present) && present.center.equals(pos)) Panels.openTown(viewer,present);
                else RelationshipViews.open(viewer,present,pos);
            }
            else notify(player,present.name+": "+present.citizens.size()+" citizens"+(owns(player,present) ? ". Open the town screen at its banner." : "."));
            return;
        }
        int radius=Config.SETTLEMENT_RADIUS.get();
        if(data.settlements.stream().anyMatch(s -> s.overlaps(pos,radius))) { notify(player,"Move your banner farther away: settlement claims cannot overlap."); return; }
        long previous=data.settlements.stream().filter(s -> s.owner.equals(player.getUUID())).count();
        Settlement settlement=new Settlement(UUID.randomUUID(),player.getUUID(),player.getName().getString()+"'s settlement"+(previous>0 ? " "+(previous+1) : ""),pos,radius,List.of(),List.of(),"balanced");
        settlement.populationLevel=0;
        settlement.campaign.playerNames.put(player.getUUID(),player.getName().getString());
        data.settlements.add(settlement); data.setDirty();
        placeBorders(level,settlement);
        tell(player,"Founded "+settlement.name+". Place housing, warehouse, and work stations inside the "+radius+"-block claim. Up to "
                +populationLimit(settlement)+" citizens may live here; buy room for more with emeralds on the town screen.");
    }
    /** Restore an old lost rally point without founding a replacement town or changing its claim. */
    public static String recoverBanner(ServerLevel level,Player player,Settlement town) {
        if(town==null || !town.contains(player.blockPosition()) || !TownAccess.builds(town,player.getUUID())) return "Stand inside a town where you have building permission.";
        if(!level.hasChunkAt(town.center)) return "Move closer to the original flag at "+town.center.toShortString()+" so its chunk is loaded.";
        BlockState state=level.getBlockState(town.center);
        if(state.is(WWMC.BANNER.get())) return "The settlement flag is already in place at "+town.center.toShortString()+".";
        if(!state.isAir()) return "Clear the original flag position at "+town.center.toShortString()+" first; recovery never replaces another block.";
        ItemStack replacement=ItemStack.EMPTY;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            ItemStack item=player.getInventory().getItem(slot);
            if(item.is(WWMC.BANNER_ITEM.get())) { replacement=item; break; }
        }
        if(replacement.isEmpty() && !player.getAbilities().instabuild) return "Carry one Settlement Banner in your inventory to restore the flag.";
        if(!level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState().setValue(SettlementBannerBlock.FACING,player.getDirection()))) return "The flag could not be restored here.";
        if(!player.getAbilities().instabuild) { replacement.shrink(1); player.getInventory().setChanged(); }
        CampaignService.record(level,town,"Settlement flag restored at "+town.center.toShortString()+".");
        return "Restored "+town.name+"'s flag. Its citizens, stations, claim and routes are unchanged.";
    }
    public static void registerStation(ServerLevel level,Player player,BlockPos pos,StructureRole role) {
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(pos);
        if(!TownAccess.builds(settlement,player.getUUID())) { notify(player,"Place stations inside a town where you have building permission."); return; }
        if(role==StructureRole.TRADER && !TradeRoutes.uniqueCheckpoint(level,settlement,pos)) { notify(player,"Each town can have only one Trader Block."); return; }
        if(settlement.station(pos)==null) {
            var state=level.getBlockState(pos);
            // A station broken and placed again keeps the upgrades its item carries.
            Station station=new Station(pos,role,state.getValue(StationBlock.FACING),state.getValue(StationBlock.RANGE),state.getValue(StationBlock.CREW),state.getValue(StationBlock.YIELD));
            if(role==StructureRole.QUARRY) {
                var bounds=MiningLayout.quarry(pos.getX(),pos.getZ(),station.facing().getStepX(),station.facing().getStepZ(),pos.getY(),pos.getY());
                if(!settlement.contains(new BlockPos(bounds.minX(),pos.getY(),bounds.minZ())) || !settlement.contains(new BlockPos(bounds.maxX(),pos.getY(),bounds.maxZ()))) {
                    notify(player,"The full chunk in front of this quarry must fit inside your town claim. Move or turn the station."); return;
                }
            }
            settlement.stations.add(station); synchronizeUpgrades(level,station); data.setDirty();
            notify(player,"Registered the "+role.title()+" Station"+(station.range()+station.crew()+station.yieldLevel()>0 ? " with its upgrades" : "")+". Right-click it to open its screen.");
        }
    }
    public static boolean active(ServerLevel level,Station station) {
        return level.hasChunkAt(station.position()) && level.getBlockState(station.position()).getBlock() instanceof StationBlock block && block.role()==station.role();
    }
    /** Retired crew levels in old blocks/items cannot restore extra slots; valid range and quarry upgrades stay intact. */
    private static boolean synchronizeUpgrades(ServerLevel level,Station station) {
        if(!active(level,station)) return false;
        BlockState state=level.getBlockState(station.position());
        BlockState normalized=state.setValue(StationBlock.RANGE,station.range()).setValue(StationBlock.CREW,station.crew()).setValue(StationBlock.YIELD,station.yieldLevel());
        return state!=normalized && level.setBlock(station.position(),normalized,3);
    }
    private static boolean availableCell(ServerLevel level,Settlement town,BlockPos pos) {
        return pos.getY()>=level.getMinY() && pos.getY()<level.getMaxY() && town.contains(pos) && level.hasChunkAt(pos);
    }
    private static boolean knownStation(ServerLevel level,Station station) {
        // Retain ownership across chunk boundaries; never load an absent chunk just to scan it.
        return !level.hasChunkAt(station.position()) || active(level,station);
    }
    public static Iterable<BlockPos> cells(Station station) {
        RoomBounds r=station.area();
        return BlockPos.betweenClosed(r.minX(),r.minY(),r.minZ(),r.maxX(),r.maxY(),r.maxZ());
    }
    public static boolean ownsBlock(ServerLevel level,Settlement town,Station station,BlockPos pos) {
        Station owner=town.nearestStation(pos,s -> s.role()==station.role() && knownStation(level,s));
        return station.equals(owner);
    }
    public static List<BlockPos> beds(ServerLevel level,Settlement town,Station station) {
        if(!station.role().detectsBeds() || !active(level,station)) return List.of();
        List<BlockPos> result=new ArrayList<>();
        for(BlockPos pos:resourcePositions(level,town,station,"beds",() -> scanBeds(level,town,station))) {
            // A cached location cannot keep a broken, obstructed, reassigned or unloaded bed usable.
            if(!availableCell(level,town,pos)) continue;
            var head=level.getBlockState(pos);
            if(!(head.getBlock() instanceof BedBlock) || head.getValue(BedBlock.PART)!=BedPart.HEAD) continue;
            BlockPos foot=pos.relative(head.getValue(BedBlock.FACING).getOpposite());
            if(availableCell(level,town,foot) && station.contains(foot) && StationDetection.completeBed(head,level.getBlockState(foot))
                    && station.equals(town.nearestStation(pos,s -> s.role().detectsBeds() && s.contains(foot) && knownStation(level,s)))) result.add(pos);
        }
        return result;
    }
    private static List<BlockPos> scanBeds(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> result=new ArrayList<>();
        if(!station.role().detectsBeds() || !active(level,station)) return result;
        for(BlockPos pos:cells(station)) {
            if(!availableCell(level,town,pos)) continue;
            var head=level.getBlockState(pos);
            if(!(head.getBlock() instanceof BedBlock) || head.getValue(BedBlock.PART)!=BedPart.HEAD) continue;
            BlockPos foot=pos.relative(head.getValue(BedBlock.FACING).getOpposite());
            if(!availableCell(level,town,foot) || !station.contains(foot)
                    || !StationDetection.completeBed(head,level.getBlockState(foot))) continue;
            // Hospital, barracks, and housing compete for the whole bed, rather than counting each half.
            Station owner=town.nearestStation(pos,s -> s.role().detectsBeds() && s.contains(foot) && knownStation(level,s));
            if(station.equals(owner)) result.add(pos.immutable());
        }
        return result;
    }
    public static List<BlockPos> housingBeds(ServerLevel level,Settlement settlement) {
        Set<BlockPos> result=new LinkedHashSet<>();
        for(Station station:settlement.stations) if(station.role().providesHousing()) result.addAll(beds(level,settlement,station));
        return new ArrayList<>(result);
    }
    public static int workBlocks(ServerLevel level,Settlement town,Station station) {
        int count=0;
        for(BlockPos pos:cells(station)) if(availableCell(level,town,pos) && !protectedFurniture(town,pos)
                && StationDetection.workBlock(station.role(),level.getBlockState(pos)) && ownsBlock(level,town,station,pos)) count++;
        return count;
    }
    public static List<BlockPos> processingDevices(ServerLevel level,Settlement town,Station station) {
        if(!station.role().processes() || !active(level,station)) return List.of();
        return resourcePositions(level,town,station,"devices",() -> {
            List<BlockPos> found=new ArrayList<>();
            for(BlockPos pos:cells(station)) if(availableCell(level,town,pos)
                    && StationDetection.processingBlock(station.role(),level.getBlockState(pos))
                    && ownsProcessor(level,town,station,pos)) found.add(pos.immutable());
            return found;
        }).stream().filter(pos -> availableCell(level,town,pos)
                && StationDetection.processingBlock(station.role(),level.getBlockState(pos))
                && ownsProcessor(level,town,station,pos)).toList();
    }
    /** Kitchens and smelteries compete for shared furnaces, so two workers cannot load incompatible batches into one. */
    private static boolean ownsProcessor(ServerLevel level,Settlement town,Station station,BlockPos pos) {
        Station owner=town.nearestStation(pos,s -> s.role().processes() && knownStation(level,s)
                && StationDetection.processingBlock(s.role(),level.getBlockState(pos)));
        return station.equals(owner);
    }
    /** The owner gets the station's screen; anyone else a one-line notice. */
    public static void inspectStation(ServerLevel level,Player player,BlockPos pos) {
        Settlement town=SettlementData.get(level).at(pos);
        if(town==null || town.station(pos)==null) return;
        Station station=town.station(pos);
        refreshResources(level,town);
        if(owns(player,town) && player instanceof ServerPlayer viewer) Panels.openStation(viewer,town,station);
        else notify(player,"This "+station.role().title()+" Station belongs to "+town.name+".");
    }
    public static List<BlockPos> anvils(ServerLevel level,Settlement town,Station station) {
        if(station.role()!=StructureRole.BLACKSMITH || !active(level,station)) return List.of();
        return resourcePositions(level,town,station,"anvils",() -> {
            List<BlockPos> found=new ArrayList<>();
            for(BlockPos pos:cells(station)) if(availableCell(level,town,pos) && StationDetection.anvil(level.getBlockState(pos))
                    && ownsBlock(level,town,station,pos)) found.add(pos.immutable());
            return found;
        }).stream().filter(pos -> availableCell(level,town,pos) && StationDetection.anvil(level.getBlockState(pos))
                && ownsBlock(level,town,station,pos)).toList();
    }
    /** Enchanting tables an enchanter station owns. */
    public static List<BlockPos> enchantingTables(ServerLevel level,Settlement town,Station station) {
        if(station.role()!=StructureRole.ENCHANTER || !active(level,station)) return List.of();
        return resourcePositions(level,town,station,"tables",() -> {
            List<BlockPos> found=new ArrayList<>();
            for(BlockPos pos:cells(station)) if(availableCell(level,town,pos) && StationDetection.enchantingTable(level.getBlockState(pos))
                    && ownsBlock(level,town,station,pos)) found.add(pos.immutable());
            return found;
        }).stream().filter(pos -> availableCell(level,town,pos) && StationDetection.enchantingTable(level.getBlockState(pos))
                && ownsBlock(level,town,station,pos)).toList();
    }
    public static List<Container> storage(ServerLevel level,Settlement settlement) {
        return storageAt(level,settlement,null);
    }
    public static List<Container> storageAt(ServerLevel level,Settlement settlement,BlockPos selectedWarehouse) {
        Set<BlockPos> positions=new LinkedHashSet<>();
        for(Station station:settlement.stations) {
            if(station.role()!=StructureRole.WAREHOUSE || !active(level,station)) continue;
            if(selectedWarehouse!=null && !station.position().equals(selectedWarehouse)) continue;
            for(BlockPos pos:resourcePositions(level,settlement,station,"storage",() -> scanStorage(level,settlement,station))) {
                if(availableCell(level,settlement,pos) && StationDetection.storageBlock(level.getBlockState(pos))
                        && ownsBlock(level,settlement,station,pos)) positions.add(pos.immutable());
            }
        }
        List<Container> containers=new ArrayList<>();
        // Each chest half contributes its actual block inventory once, including double chests.
        for(BlockPos pos:positions) if(level.getBlockEntity(pos) instanceof Container container) containers.add(container);
        return containers;
    }
    private static List<BlockPos> scanStorage(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> positions=new ArrayList<>();
        for(BlockPos pos:cells(station)) if(availableCell(level,town,pos) && StationDetection.storageBlock(level.getBlockState(pos))
                && ownsBlock(level,town,station,pos)) positions.add(pos.immutable());
        return positions;
    }
    /** Barrels in a work station's range that no warehouse range covers. Where job ranges overlap, the nearest job owns each barrel. */
    public static List<BlockPos> jobBarrels(ServerLevel level,Settlement town,Station station) {
        if(!station.role().keepsJobStorage() || !active(level,station)) return List.of();
        return resourcePositions(level,town,station,"barrels",() -> {
            List<BlockPos> found=new ArrayList<>();
            for(BlockPos pos:cells(station)) if(jobBarrel(level,town,station,pos)) found.add(pos.immutable());
            return found;
        }).stream().filter(pos -> jobBarrel(level,town,station,pos)).toList();
    }
    private static boolean jobBarrel(ServerLevel level,Settlement town,Station station,BlockPos pos) {
        return availableCell(level,town,pos) && level.getBlockState(pos).getBlock() instanceof BarrelBlock
                && town.nearestStation(pos,s -> s.role()==StructureRole.WAREHOUSE && knownStation(level,s))==null
                && station.equals(town.nearestStation(pos,s -> s.role().keepsJobStorage() && knownStation(level,s)));
    }
    public static List<Container> jobStorage(ServerLevel level,Settlement town,Station station) {
        List<Container> containers=new ArrayList<>();
        for(BlockPos pos:jobBarrels(level,town,station)) if(level.getBlockEntity(pos) instanceof Container container) containers.add(container);
        return containers;
    }
    /** Warehouse containers and every loaded job barrel: all the goods the town holds. */
    public static List<Container> townStorage(ServerLevel level,Settlement town) {
        List<Container> containers=new ArrayList<>(storage(level,town));
        for(Station station:town.stations) containers.addAll(jobStorage(level,town,station));
        return containers;
    }
    /** An enabled, loaded station with a living assigned courier can collect from job barrels. */
    public static boolean couriers(ServerLevel level,Settlement town) {
        if(town.jobs.level(StructureRole.COURIER)==JobBoard.OFF) return false;
        return town.stations.stream().filter(s -> s.role()==StructureRole.COURIER && active(level,s)).anyMatch(s ->
                town.jobs.crew(s.position()).stream().anyMatch(id -> town.citizens.contains(id)
                        && town.jobs.holdsPlace(id,s,workerLimit(town,s)) && !SquadService.assigned(town,id)
                        && level.getEntity(id) instanceof CitizenEntity citizen && citizen.isAlive() && !HospitalCare.needsCare(town,citizen)));
    }
    /** Explains why placing a courier station alone has not started hauling. */
    public static String courierAdvice(ServerLevel level,Settlement town) {
        List<Station> stations=town.stations.stream().filter(s -> s.role()==StructureRole.COURIER).toList();
        if(stations.isEmpty()) return "Add a Courier Station and assign a citizen to collect goods for the warehouse";
        if(town.jobs.level(StructureRole.COURIER)==JobBoard.OFF) return "Enable Courier jobs on the Jobs tab and assign a citizen";
        if(stations.stream().noneMatch(s -> active(level,s))) return "Keep a Courier Station loaded so its worker can collect goods";
        if(stations.stream().filter(s -> active(level,s)).noneMatch(s -> town.jobs.crew(s.position()).stream().anyMatch(id ->
                town.citizens.contains(id) && town.jobs.holdsPlace(id,s,workerLimit(town,s)) && !SquadService.assigned(town,id))))
            return "Assign a citizen to a loaded Courier Station; recruit one or raise Courier priority on the Jobs tab";
        return "The assigned courier is unavailable; check the Courier Station's Crew tab for their location and health";
    }
    public static BlockPos warehouse(ServerLevel level,Settlement settlement,BlockPos from) {
        return settlement.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE && active(level,s))
                .filter(s -> !storageAt(level,settlement,s.position()).isEmpty())
                .min(Comparator.comparingDouble(s -> s.position().distSqr(from))).map(Station::position).orElse(null);
    }
    public static boolean protectedFurniture(Settlement settlement,BlockPos pos) {
        return settlement.center.equals(pos) || settlement.borderBanners.contains(pos) || settlement.stations.stream().anyMatch(s -> s.position().equals(pos) ||
                ((!s.role().providesWork() || s.role()==StructureRole.HOSPITAL) && s.contains(pos)));
    }
    private static void placeBorders(ServerLevel level,Settlement town) { TownBorders.update(level,town); }
    // ---------- Emerald upgrades ----------
    /** Population level, counting a town from before upgrades as having bought enough for the citizens it already has. */
    public static int populationLevel(Settlement town) {
        return town.populationLevel>=0 ? town.populationLevel : Upgrades.levelFor(town.citizens.size(),Config.BASE_POPULATION.get(),Config.POPULATION_STEP.get());
    }
    public static int populationLimit(Settlement town) { return populationLimitAt(town,populationLevel(town)); }
    public static int populationLimitAt(Settlement town,int level) {
        return Upgrades.populationLimit(level,Config.BASE_POPULATION.get()+Research.populationBonus(town),Config.POPULATION_STEP.get(),Config.MAX_CITIZENS.get());
    }
    public static int populationLimitAt(int level) {
        return Upgrades.populationLimit(level,Config.BASE_POPULATION.get(),Config.POPULATION_STEP.get(),Config.MAX_CITIZENS.get());
    }
    /** Whether the town can still raise its limit, and what that costs. */
    public static boolean canGrow(Settlement town) { return populationLimitAt(town,populationLevel(town)+1)>populationLimit(town); }
    public static int populationCost(Settlement town) { return Upgrades.populationCost(Config.POPULATION_COST.get(),populationLevel(town)); }
    public static int stationCost(Station station,boolean range) { return stationCost(station,range ? Upgrades.Kind.RANGE : Upgrades.Kind.CREW); }
    public static int stationCost(Station station,Upgrades.Kind kind) {
        return Upgrades.stationCost(kind==Upgrades.Kind.YIELD ? Config.YIELD_COST.get() : Config.STATION_COST.get(),station.upgradeLevel(kind));
    }
    /** Emeralds a player carries, counting each emerald block as nine. */
    public static int emeralds(Player player) {
        Inventory inventory=player.getInventory();
        long total=0;
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            ItemStack stack=inventory.getItem(slot);
            if(stack.is(Items.EMERALD)) total+=stack.getCount(); else if(stack.is(Items.EMERALD_BLOCK)) total+=9L*stack.getCount();
        }
        return (int)Math.min(Integer.MAX_VALUE,total);
    }
    /** Take the price from the player's inventory, emerald blocks broken into change as needed. Creative players pay nothing. */
    public static boolean pay(Player player,int cost) {
        if(cost<=0 || player.getAbilities().instabuild) return true;
        Inventory inventory=player.getInventory();
        int emeralds=0,blocks=0;
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            ItemStack stack=inventory.getItem(slot);
            if(stack.is(Items.EMERALD)) emeralds+=stack.getCount(); else if(stack.is(Items.EMERALD_BLOCK)) blocks+=stack.getCount();
        }
        Upgrades.Payment payment=Upgrades.pay(emeralds,blocks,cost);
        if(payment==null) return false;
        take(inventory,Items.EMERALD,payment.emeralds());
        take(inventory,Items.EMERALD_BLOCK,payment.blocks());
        if(payment.change()>0) {
            ItemStack change=new ItemStack(Items.EMERALD,payment.change());
            if(!inventory.add(change)) player.drop(change,false);
        }
        inventory.setChanged();
        return true;
    }
    private static void take(Inventory inventory,Item item,int amount) {
        for(int slot=0;slot<inventory.getContainerSize() && amount>0;slot++) {
            ItemStack stack=inventory.getItem(slot);
            if(!stack.is(item)) continue;
            int taken=Math.min(amount,stack.getCount());
            stack.shrink(taken); amount-=taken;
        }
    }
    /** Buy the station's next range, crew or yield level. The block state carries the levels too, for the range preview and the dropped item. */
    public static void upgradeStation(ServerLevel level,Player player,Settlement town,Station station,boolean range) {
        upgradeStation(level,player,town,station,range ? Upgrades.Kind.RANGE : Upgrades.Kind.CREW);
    }
    public static void upgradeStation(ServerLevel level,Player player,Settlement town,Station station,Upgrades.Kind kind) {
        if(!TownAccess.manages(town,player.getUUID()) || !station.equals(town.station(station.position())) || !active(level,station)) return;
        StructureRole role=station.role();
        if(!kind.supports(role)) {
            notify(player,"The "+role.title()+" Station has no "+kind.title().toLowerCase(java.util.Locale.ROOT)+" upgrades."); return;
        }
        int current=station.upgradeLevel(kind);
        if(current>=Upgrades.MAX_STATION_LEVEL) { notify(player,"This station's "+kind.title().toLowerCase(java.util.Locale.ROOT)+" is fully upgraded."); return; }
        int cost=stationCost(station,kind);
        if(!pay(player,cost)) { notify(player,"That upgrade costs "+cost+" emeralds; you carry "+emeralds(player)+"."); return; }
        Station upgraded=station.upgraded(kind,current+1);
        if(!town.replace(upgraded)) return;
        BlockState state=level.getBlockState(station.position());
        if(state.getBlock() instanceof StationBlock)
            level.setBlock(station.position(),state.setValue(StationBlock.RANGE,upgraded.range()).setValue(StationBlock.CREW,upgraded.crew()).setValue(StationBlock.YIELD,upgraded.yieldLevel()),3);
        refreshResources(level,town);
        SettlementData.get(level).setDirty();
        notify(player,switch(kind) {
            case RANGE -> "The "+role.title()+" Station now reaches "+upgraded.size()+".";
            case CREW -> "The "+role.title()+" Station now has "+workerLimit(upgraded)+" crew slots.";
            case YIELD -> "The "+role.title()+" Station now averages "+Upgrades.yieldPercent(upgraded.yieldLevel())+"% extra produce or mineral drops.";
        });
    }
    /** Buy room for more citizens. Each upgrade costs more than the last, and the town's waves grow with it. */
    public static void upgradePopulation(ServerLevel level,Player player,Settlement town) {
        if(!canGrow(town)) { notify(player,town.name+" is at the server's ceiling of "+Config.MAX_CITIZENS.get()+" citizens."); return; }
        int cost=populationCost(town);
        if(!pay(player,cost)) { notify(player,"Room for more citizens costs "+cost+" emeralds; you carry "+emeralds(player)+"."); return; }
        town.populationLevel=populationLevel(town)+1;
        SettlementData.get(level).setDirty();
        notify(player,town.name+" can now hold "+populationLimit(town)+" citizens. Word spreads: enemy waves will be larger.");
    }
    public static String citizenName(ServerLevel level,Settlement town,UUID citizen) {
        String saved=town.citizenNames.get(citizen);
        if(saved!=null) return saved;
        List<String> used=new ArrayList<>(town.citizenNames.values());
        for(UUID id:town.citizens) {
            var entity=level.getEntity(id);
            if(entity!=null && entity.getCustomName()!=null && !id.equals(citizen)) used.add(entity.getCustomName().getString());
        }
        String name=CitizenNames.choose(citizen,used);
        town.citizenNames.put(citizen,name); SettlementData.get(level).setDirty(); return name;
    }
    public static String status(ServerLevel level,Settlement settlement) {
        return settlement.name+": "+settlement.citizens.size()+" of "+populationLimit(settlement)+" citizens allowed / "+housingBeds(level,settlement).size()+
                " loaded housing beds, "+settlement.stations.size()+" stations, priority: "+settlement.priority+". Claim radius: "+settlement.radius+
                ". Defense: "+DefenseService.status(settlement)+"; "+WaveService.status(level,settlement)+". Traps: "+TrapService.status(level,settlement)+".";
    }
    private static Settlement owned(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        SettlementData data=SettlementData.get(source.getLevel());
        Settlement local=data.at(player.blockPosition());
        if(owns(player,local)) return local;
        List<Settlement> owned=data.settlements.stream().filter(s -> owns(player,s)).toList();
        if(owned.size()>1) { source.sendFailure(Component.literal("Stand inside the town you want to manage, or use its banner screen.")); return null; }
        return owned.isEmpty() ? null : owned.getFirst();
    }
    private static int recruit(CommandSourceStack source,int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Settlement settlement=owned(source);
        if(settlement==null) { source.sendFailure(Component.literal("Right-click a settlement banner to found a town first.")); return 0; }
        ServerLevel level=source.getLevel();
        final int result=recruit(level,settlement,count);
        source.sendSuccess(() -> Component.literal("Recruited "+result+" citizens. "+status(level,settlement)+" Supply food and tools in the warehouse."),false);
        return result;
    }
    /** New citizens appear beside the banner while housing beds and the population limit allow; returns how many joined. */
    public static int recruit(ServerLevel level,Settlement settlement,int count) {
        refreshResources(level,settlement);
        List<BlockPos> beds=housingBeds(level,settlement);
        int limit=Math.min(beds.size(),populationLimit(settlement));
        int added=0;
        for(int i=0;i<count && settlement.citizens.size()<limit;i++) {
            CitizenEntity citizen=WWMC.CITIZEN.get().create(level,EntitySpawnReason.COMMAND);
            if(citizen==null) break;
            BlockPos spawn=null;
            for(int x=-3;x<=3 && spawn==null;x++) for(int z=-3;z<=3;z++) {
                BlockPos trial=settlement.center.offset(x,0,z);
                if(!level.hasChunkAt(trial)) continue;
                citizen.setPos(trial.getX()+0.5,trial.getY(),trial.getZ()+0.5);
                if(level.noCollision(citizen) && !level.getBlockState(trial.below()).isAir()) { spawn=trial; break; }
            }
            if(spawn==null) break;
            citizen.join(settlement.id);
            citizen.setCustomName(Component.literal(citizenName(level,settlement,citizen.getUUID())));
            if(level.addFreshEntity(citizen)) { settlement.citizens.add(citizen.getUUID()); added++; }
        }
        SettlementData.get(level).setDirty();
        if(added>0) CampaignService.record(level,settlement,added+" new "+(added==1 ? "citizen joined" : "citizens joined")+" the town.");
        return added;
    }
    private static int bread(CommandSourceStack source,boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Settlement s=owned(source);
        if(s==null) { source.sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
        if(enabled ? s.disabledRecipes.remove("bread") : s.disabledRecipes.add("bread")) SettlementData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Cooks will "+(enabled ? "" : "no longer ")+"bake bread."),false);
        return 1;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwmc")
            .then(Commands.literal("guide").executes(c -> {
                ServerPlayer player=c.getSource().getPlayerOrException();
                ItemStack book=WWMC.GUIDE.get().getDefaultInstance();
                if(!player.getInventory().add(book)) player.drop(book,false);
                notify(player,"Settlement Guide received. Right-click it to read recipes and town instructions."); return 1;
            }))
            .then(Commands.literal("status").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                c.getSource().sendSuccess(() -> Component.literal(status(c.getSource().getLevel(),s)),false); return 1;
            }))
            .then(Commands.literal("recruit").executes(c -> recruit(c.getSource(),1))
                .then(Commands.argument("count",IntegerArgumentType.integer(1,8)).executes(c -> recruit(c.getSource(),IntegerArgumentType.getInteger(c,"count")))))
            .then(Commands.literal("name").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c -> {
                Settlement s=owned(c.getSource()); if(s==null) return 0;
                String name=StringArgumentType.getString(c,"name").strip();
                if(name.isEmpty() || name.length()>48) { c.getSource().sendFailure(Component.literal("Use a town name of 1 to 48 characters.")); return 0; }
                notify(c.getSource().getPlayerOrException(),RelationshipViews.rename(c.getSource().getLevel(),s,c.getSource().getPlayerOrException().getUUID(),name)); return 1;
            })))
            .then(Commands.literal("priority").then(Commands.argument("priority",StringArgumentType.word())
                .suggests((c,b) -> { for(String p:JobBoard.PRESETS) b.suggest(p); return b.buildFuture(); })
                .executes(c -> {
                    Settlement s=owned(c.getSource()); if(s==null) return 0;
                    String p=StringArgumentType.getString(c,"priority");
                    if(!JobBoard.PRESETS.contains(p)) { c.getSource().sendFailure(Component.literal("Choose balanced, food, or materials.")); return 0; }
                    applyPreset(c.getSource().getLevel(),s,p);
                    c.getSource().sendSuccess(() -> Component.literal("Job priorities set to "+p+". Citizens move to open places in higher-priority jobs; the rest keep their jobs."),false); return 1;
                })))
            .then(Commands.literal("job").then(Commands.argument("job",StringArgumentType.word())
                .suggests((c,b) -> { for(StructureRole role:StructureRole.values()) if(role.providesWork()) b.suggest(role.id()); return b.buildFuture(); })
                .then(Commands.argument("level",StringArgumentType.word())
                    .suggests((c,b) -> { for(String level:JobBoard.LEVEL_NAMES) b.suggest(level.toLowerCase(java.util.Locale.ROOT)); return b.buildFuture(); })
                    .executes(c -> {
                        Settlement s=owned(c.getSource()); if(s==null) return 0;
                        String job=StringArgumentType.getString(c,"job"),name=StringArgumentType.getString(c,"level");
                        StructureRole role=java.util.Arrays.stream(StructureRole.values()).filter(r -> r.providesWork() && r.id().equals(job)).findFirst().orElse(null);
                        int level=JobBoard.LEVEL_NAMES.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).toList().indexOf(name.toLowerCase(java.util.Locale.ROOT));
                        if(role==null || level<0) { c.getSource().sendFailure(Component.literal("Use /wwmc job <job> <off|low|normal|high>, for example /wwmc job farm high.")); return 0; }
                        setJobLevel(c.getSource().getLevel(),s,role,level);
                        c.getSource().sendSuccess(() -> Component.literal(role.title()+" priority set to "+JobBoard.levelName(level)+"."),false); return 1;
                    }))))
            .then(Commands.literal("needs").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                var needs=TownNeeds.assess(c.getSource().getLevel(),s);
                StringBuilder text=new StringBuilder(s.name+": "+(needs.isEmpty() ? "nothing needed." : needs.size()+" needs."));
                for(TownNeeds.Need need:needs) text.append("\n").append(need.severity()==TownNeeds.URGENT ? "! " : "- ").append(need.title())
                        .append(need.at()==null ? "" : " ("+need.at().toShortString()+")").append(": ").append(need.detail());
                c.getSource().sendSuccess(() -> Component.literal(text.toString()),false); return needs.size();
            }))
            .then(Commands.literal("citizens").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                var loaded=DefenseService.loadedCitizens(c.getSource().getLevel(),s);
                StringBuilder text=new StringBuilder(s.name+": "+loaded.size()+" of "+s.citizens.size()+" citizens loaded.");
                for(CitizenEntity citizen:loaded) text.append("\n").append(citizen.getName().getString()).append(" (").append(citizen.job()).append("): ").append(citizen.activity());
                for(UUID id:s.citizens) if(!(c.getSource().getLevel().getEntity(id) instanceof CitizenEntity))
                    text.append("\n").append(s.citizenNames.getOrDefault(id,"A citizen")).append(": ").append(CitizenRecall.whereabouts(c.getSource().getLevel(),s,id));
                c.getSource().sendSuccess(() -> Component.literal(text.toString()),false); return loaded.size();
            }))
            .then(Commands.literal("craft").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                List<Container> stock=townStorage(c.getSource().getLevel(),s);
                StringBuilder text=new StringBuilder("Craftsman orders, town stock/target:");
                for(Workshop.Order order:s.craftOrders) text.append(" ").append(order.item().replace("minecraft:","")).append(" ").append(Workshop.stock(stock,order)).append("/").append(order.target()).append(",");
                text.setLength(text.length()-1);
                c.getSource().sendSuccess(() -> Component.literal(text+". Teach recipes and set amounts on the Craftsman Station screen."),false); return 1;
            }).then(Commands.literal("bread")
                .then(Commands.literal("on").executes(c -> bread(c.getSource(),true)))
                .then(Commands.literal("off").executes(c -> bread(c.getSource(),false)))))
            .then(Commands.literal("alarm").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                DefenseService.toggle(c.getSource().getLevel(),s); return 1;
            }))
            .then(Commands.literal("wave").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                int spawned=WaveService.callNow(c.getSource().getLevel(),s);
                if(spawned==0) c.getSource().sendFailure(Component.literal("No wave could gather: it needs loaded open ground 40-64 blocks from the banner, away from stations and from you, and a difficulty above peaceful."));
                return spawned;
            })));
    }
    @SubscribeEvent public void breakStation(BreakBlockEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(event.getPos());
        if(settlement==null) return;
        boolean banner=settlement.center.equals(event.getPos()) && event.getState().is(WWMC.BANNER.get());
        boolean station=event.getState().getBlock() instanceof StationBlock;
        if(!banner && !station) return;
        if(!(banner ? TownAccess.owner(settlement,event.getPlayer().getUUID()) : TownAccess.builds(settlement,event.getPlayer().getUUID()))) {
            event.setCanceled(true); event.setNotifyClient(true); notify(event.getPlayer(),"You need building permission to remove stations; only the owner may remove the banner."); return;
        }
        if(banner && !settlement.citizens.isEmpty()) {
            event.setCanceled(true); event.setNotifyClient(true); notify(event.getPlayer(),"This banner belongs to an occupied settlement. Keep it as your town's rally point."); return;
        }
        // Reconcile after the block is actually gone; another event listener may still cancel the break.
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%200!=0) return;
        reservations(level).prune(level.getGameTime());
        workers(level).prune(level.getGameTime());
        SettlementData data=SettlementData.get(level);
        if(data.settlements.removeIf(s -> s.trading.buildIndex<0 && s.citizens.isEmpty() && level.hasChunkAt(s.center) && !level.getBlockState(s.center).is(WWMC.BANNER.get()))) data.setDirty();
        StationResourceCache scans=RESOURCE_SCANS.get(level);
        if(scans!=null) scans.prune(level.getGameTime());
        for(Settlement s:data.settlements) {
            if(s.widenTo(Settlement.MIN_RADIUS,data.settlements)) {
                // Old corner banners are no longer the border; they stay in the world as ordinary blocks.
                s.borderBanners.clear(); data.setDirty();
                ServerPlayer owner=level.getServer().getPlayerList().getPlayer(s.owner);
                if(owner!=null) tell(owner,s.name+"'s claim now extends "+s.radius+" blocks from its banner.");
            }
            placeBorders(level,s);
            if(s.populationLevel<0) { s.populationLevel=populationLevel(s); data.setDirty(); }
            if(s.stations.removeIf(station -> level.hasChunkAt(station.position()) && !active(level,station))) data.setDirty();
            for(Station station:s.stations) if(synchronizeUpgrades(level,station)) data.setDirty();
            if(s.jobs.prune(s,station -> workerLimit(s,station))) data.setDirty();
        }
    }
    /** Quarry crews work on safety lines: a fall inside their town's pit does no damage. */
    @SubscribeEvent public void fall(LivingFallEvent event) {
        if(event.getEntity() instanceof CitizenEntity citizen && citizen.level() instanceof ServerLevel level && citizen.inQuarry(level)) event.setCanceled(true);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        RESERVATIONS.keySet().removeIf(level -> level.getServer()==event.getServer());
        WORKFORCE.keySet().removeIf(level -> level.getServer()==event.getServer());
        RESOURCE_SCANS.keySet().removeIf(level -> level.getServer()==event.getServer());
    }
}
