package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Player-discovered sites use loaded, unclaimed natural terrain. Defenders and loot are finite and saved. */
public final class ExpeditionService {
    private int cursor;
    /** When each neutral town may next be raided, so a friendly visit does not bring raiders every few minutes. */
    private static final Map<UUID,Long> NEXT_RAID=new HashMap<>();
    /** Raids in which a player, or a player town's citizen, killed at least one raider. */
    private static final Set<UUID> FOUGHT=new HashSet<>();
    /** How often a rescue was attempted while some captives were not loaded yet. */
    private static final Map<UUID,Integer> RESCUE_TRIES=new HashMap<>();
    private static boolean nearby(ServerLevel level,BlockPos pos,int range) {
        return level.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(pos))<(double)range*range);
    }
    public static boolean clearSite(ServerLevel level,BlockPos pos) {
        return siteIssue(level,pos).isEmpty();
    }
    public static String siteIssue(ServerLevel level,BlockPos pos) {
        return siteIssue(level,pos,5);
    }
    private static String siteIssue(ServerLevel level,BlockPos pos,int radius) {
        var data=SettlementData.get(level); var protection=WorldWorkData.get(level);
        if(data.settlements.stream().anyMatch(t -> t.overlaps(pos,Settlement.MIN_RADIUS+radius+3))) return "too close to an existing claim";
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            BlockPos feet=pos.offset(x,0,z); if(!level.hasChunkAt(feet)) return "unloaded ground at "+feet.toShortString();
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,feet.getX(),feet.getZ());
            if(Math.abs(ground-pos.getY())>2) return "uneven ground at "+feet.toShortString()+"; height "+ground;
            if(!level.getFluidState(new BlockPos(feet.getX(),ground-1,feet.getZ())).isEmpty()) return "wet ground at "+feet.toShortString();
            for(int y=-3;y<=6;y++) {
                BlockPos block=feet.offset(0,y,0); var state=level.getBlockState(block);
                if(protection.protectedBlocks.contains(block) || level.getBlockEntity(block)!=null) return "protected construction at "+block.toShortString();
                if(!state.isAir() && !state.is(Blocks.GRASS_BLOCK) && !state.is(BlockTags.DIRT) && !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.LEAVES)
                        && !state.is(BlockTags.LOGS) && !state.canBeReplaced()) return "non-natural block "+BuiltInRegistries.BLOCK.getKey(state.getBlock())+" at "+block.toShortString();
            }
        }
        return "";
    }
    public static ExpeditionData.Site discover(ServerLevel level,BlockPos probe,String kind,String region) {
        var data=ExpeditionData.get(level);
        if(data.sites.size()>=Config.MAX_EXPEDITIONS.get() || data.sites.stream().anyMatch(s -> s.region.equals(region) || s.pos.distSqr(probe)<384*384)) return null;
        if(!level.hasChunkAt(probe)) return null;
        BlockPos pos=new BlockPos(probe.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,probe.getX(),probe.getZ()),probe.getZ());
        if(!siteIssue(level,pos,RuinedSites.radius(kind)).isEmpty()) return null;
        UUID id=UUID.nameUUIDFromBytes((level.getSeed()+":"+region).getBytes(StandardCharsets.UTF_8));
        String objective=switch(kind) {
            case "fort" -> ExpeditionData.Site.LEADER;
            case "camp" -> Math.floorMod(id.hashCode(),2)==0 ? ExpeditionData.Site.RESCUE : ExpeditionData.Site.RECOVER;
            default -> ExpeditionData.Site.CLEAR;
        };
        Regions.Region land=Regions.of(level,pos);
        ExpeditionData.Site site=new ExpeditionData.Site(id,pos,kind,region,objective,land.id());
        build(level,site); data.sites.add(site); data.setDirty();
        for(Settlement town:SettlementData.get(level).settlements) if(!town.trading.npc && town.center.distSqr(pos)<4096.0*4096.0)
            CampaignService.record(level,town,"Scouts found a "+site.title()+" at "+pos.toShortString()+" in the "+land.title().toLowerCase(Locale.ROOT)
                    +". "+site.goal()+"; an outpost there would mine "+land.ore().getName().getString().toLowerCase(Locale.ROOT)+".");
        return site;
    }
    private static void put(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(pos,state,3); WorldWorkData.get(level).protect(pos);
    }
    private static void build(ServerLevel level,ExpeditionData.Site site) {
        BlockPos c=site.pos;
        if(site.kind.equals("fort") || site.kind.equals("mine") || site.kind.equals("townhall")) {
            RuinedSites.build(level,site);
            cache(level,site); return;
        }
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
            put(level,c.offset(x,-1,z),(site.kind.equals("fort") ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState());
            for(int y=0;y<=3;y++) level.setBlock(c.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        for(int x:new int[]{-4,4}) for(int z:new int[]{-4,4}) {
            for(int y=0;y<3;y++) put(level,c.offset(x,y,z),(site.kind.equals("fort") ? Blocks.STONE_BRICKS : Blocks.OAK_FENCE).defaultBlockState());
            put(level,c.offset(x,3,z),Blocks.WOOL.red().defaultBlockState());
        }
        // Open aisles and two beds remain useful after capture. No later rebuild overwrites player alterations.
        for(int x:new int[]{-2,-1,1,2}) {
            var bed=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
            put(level,c.offset(x,0,-2),bed.setValue(BedBlock.PART,BedPart.FOOT));
            put(level,c.offset(x,0,-3),bed.setValue(BedBlock.PART,BedPart.HEAD));
        }
        cache(level,site);
    }
    private static void cache(ServerLevel level,ExpeditionData.Site site) {
        BlockPos c=site.pos;
        put(level,c.east(3),Blocks.BARREL.defaultBlockState());
        put(level,c.west(3),Blocks.BARREL.defaultBlockState());
        var cache=(Container)level.getBlockEntity(c.east(3));
        cache.setItem(0,new ItemStack(Items.IRON_INGOT,site.kind.equals("fort") ? 24 : 12));
        cache.setItem(1,new ItemStack(Items.BREAD,16)); cache.setItem(2,new ItemStack(Items.EMERALD,4));
        cache.setItem(3,new ItemStack(Items.STONE_PICKAXE)); cache.setItem(4,new ItemStack(Items.PAPER,16)); cache.setChanged();
        cache.setItem(6,new ItemStack(WWMC.GUIDE.get())); cache.setItem(7,new ItemStack(WWMC.TIN_INGOT.get(),8));
        cache.setChanged();
        if(site.kind.equals("townhall")) {
            cache.setItem(0,new ItemStack(Items.COPPER_INGOT,12)); cache.setItem(2,new ItemStack(Items.CHARCOAL,4));
            cache.setItem(3,new ItemStack(Items.STONE_SHOVEL)); cache.setItem(4,new ItemStack(Items.PAPER,24));
            cache.setItem(5,new ItemStack(WWMC.RESEARCH_SCROLL.get(),2)); cache.setChanged();
        }
        if(site.kind.equals("mine")) for(int y=0;y<2;y++) level.setBlock(c.offset(-4,y,-1),ore(site).defaultBlockState(),3);
        if(ExpeditionData.Site.RESCUE.equals(site.objective)) {
            // A fenced pen in the south-east corner, clear of the outpost's station spots.
            for(int x=2;x<=4;x++) for(int z=1;z<=4;z++) if((x==2 || x==4 || z==1 || z==4) && !(x==4 && z==4)) put(level,c.offset(x,0,z),Blocks.OAK_FENCE.defaultBlockState());
        }
        if(ExpeditionData.Site.RECOVER.equals(site.objective)) {
            put(level,c.offset(3,0,-3),Blocks.BARREL.defaultBlockState());
            var stolen=(Container)level.getBlockEntity(c.offset(3,0,-3));
            stolen.setItem(0,new ItemStack(Items.IRON_INGOT,16)); stolen.setItem(1,new ItemStack(Items.BREAD,24)); stolen.setItem(2,new ItemStack(Items.LEATHER,8));
            stolen.setItem(3,new ItemStack(Items.GOLD_INGOT,4)); stolen.setItem(4,new ItemStack(Items.IRON_PICKAXE)); stolen.setItem(5,new ItemStack(Items.EMERALD,8));
            stolen.setChanged();
        }
    }
    /** The ore an outpost at this site mines: its region's, or iron for sites found before regions. */
    public static net.minecraft.world.level.block.Block ore(ExpeditionData.Site site) {
        Regions.Region land=Regions.byId(site.resource);
        return land==null ? Blocks.IRON_ORE : land.ore();
    }
    private static void captives(ServerLevel level,ExpeditionData.Site site) {
        if(!ExpeditionData.Site.RESCUE.equals(site.objective) || !site.captives.isEmpty()) return;
        List<BlockPos> spots=List.of(site.pos.offset(3,0,2),site.pos.offset(3,0,3));
        for(int n=0;n<spots.size();n++) {
            CitizenEntity captive=WWMC.CITIZEN.get().create(level,EntitySpawnReason.EVENT);
            if(captive==null) continue;
            BlockPos spot=spots.get(n);
            captive.setPos(spot.getX()+0.5,spot.getY(),spot.getZ()+0.5);
            // Captives wait, unharmed, until they are freed.
            captive.setNoAi(true); captive.setInvulnerable(true);
            captive.setCustomName(Component.literal("Captive villager"));
            if(level.addFreshEntity(captive)) site.captives.add(captive.getUUID());
        }
        ExpeditionData.get(level).setDirty();
    }
    private static void captain(ServerLevel level,ExpeditionData.Site site) {
        if(!ExpeditionData.Site.LEADER.equals(site.task()) || site.leader!=null || level.getDifficulty()==Difficulty.PEACEFUL) return;
        var entity=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("vindicator")).create(level,EntitySpawnReason.EVENT);
        if(!(entity instanceof Mob captain)) return;
        BlockPos pos=site.pos.north(2);
        captain.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        if(!level.noCollision(captain)) captain.setPos(site.pos.getX()+0.5,site.pos.getY(),site.pos.getZ()+0.5);
        captain.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),EntitySpawnReason.EVENT,null);
        captain.setCustomName(Component.literal("Bandit Captain")); captain.setCustomNameVisible(true);
        captain.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET)); captain.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        captain.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS)); captain.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
        captain.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        // The captain's gear is the expedition's equipment reward.
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND}) captain.setDropChance(slot,1.0F);
        var health=captain.getAttribute(Attributes.MAX_HEALTH);
        if(health!=null) health.setBaseValue(60);
        captain.setHealth(captain.getMaxHealth());
        captain.setPersistenceRequired(); captain.addTag("wwmc_expedition_"+site.id); captain.addTag("wwmc_captain");
        if(level.addFreshEntity(captain)) { site.guards.add(captain.getUUID()); site.leader=captain.getUUID(); ExpeditionData.get(level).setDirty(); }
    }
    /** The nearest managing player's town within this many blocks of a place: a main town before an outpost, then the nearest. */
    private static Settlement credit(ServerLevel level,BlockPos pos,int range) {
        Settlement best=null; double nearest=(double)range*range;
        for(ServerPlayer player:level.players()) {
            if(!player.isAlive() || player.isSpectator()) continue;
            double distance=player.distanceToSqr(Vec3.atCenterOf(pos));
            if(distance>nearest) continue;
            Settlement managed=SettlementData.get(level).settlements.stream().filter(t -> !t.trading.npc && TownAccess.manages(t,player.getUUID()))
                    .min(Comparator.comparing((Settlement t) -> t.campaign.parent!=null).thenComparingDouble(t -> t.center.distSqr(pos))).orElse(null);
            if(managed!=null) { nearest=distance; best=managed; }
        }
        return best;
    }
    /** Grants a cleared site's reward once one of the clearing town's managers is there to receive it. */
    private static boolean reward(ServerLevel level,ExpeditionData.Site site) {
        if(ExpeditionData.Site.DEFEND.equals(site.objective) && !FOUGHT.contains(site.id)) {
            // Nobody from a player town fought: the neighbor's own guards won, and no one is owed thanks.
            Settlement neighbor=site.victim==null ? null : SettlementData.get(level).byId(site.victim);
            if(neighbor!=null) CampaignService.record(level,neighbor,neighbor.name+"'s own guards drove off a bandit raid.");
            site.rewarded=true; ExpeditionData.get(level).setDirty();
            return true;
        }
        Settlement town=credit(level,site.pos,ExpeditionData.Site.DEFEND.equals(site.objective) ? 96 : 24);
        if(town==null) return false;
        switch(site.task()) {
            case ExpeditionData.Site.RESCUE -> {
                int freed=0;
                int tries=RESCUE_TRIES.merge(site.id,1,Integer::sum);
                for(UUID id:List.copyOf(site.captives)) {
                    // Captives load with their chunk a moment after the player arrives; one still missing later is lost.
                    if(!(level.getEntity(id) instanceof CitizenEntity captive) || !captive.isAlive()) { if(tries>=5) site.captives.remove(id); continue; }
                    site.captives.remove(id);
                    captive.setNoAi(false); captive.setInvulnerable(false);
                    captive.join(town.id);
                    if(!town.citizens.contains(id)) town.citizens.add(id);
                    captive.setCustomName(Component.literal(SettlementService.citizenName(level,town,id)));
                    freed++;
                }
                if(freed>0) CampaignService.record(level,town,freed+(freed==1 ? " captive" : " captives")+" freed at the "+site.title()+" joined "+town.name+". They head home once you leave the camp.");
                if(!site.captives.isEmpty()) { ExpeditionData.get(level).setDirty(); SettlementData.get(level).setDirty(); return false; }
                RESCUE_TRIES.remove(site.id);
            }
            case ExpeditionData.Site.RECOVER -> CampaignService.record(level,town,"Recovered the stolen supplies at the "+site.title()+" ("+site.pos.east(3).north(3).toShortString()
                    +"). Carry them home or claim the site as an outpost.");
            case ExpeditionData.Site.LEADER -> {
                String schematic=Research.SCHEMATICS.stream().filter(id -> !town.progress.schematics.contains(id)).findFirst().orElse("");
                if(schematic.isEmpty()) CampaignService.record(level,town,"The Bandit Captain is defeated. "+town.name+" already holds every schematic; keep the captain's gear.");
                else {
                    town.progress.schematics.add(schematic);
                    CampaignService.record(level,town,"The Bandit Captain is defeated. Recovered the "+Research.schematicTitle(schematic).toLowerCase(Locale.ROOT)
                            +": new research is possible on the Campaign screen.");
                }
            }
            case ExpeditionData.Site.DEFEND -> {
                Settlement neighbor=site.victim==null ? null : SettlementData.get(level).byId(site.victim);
                FOUGHT.remove(site.id);
                if(neighbor!=null) {
                    neighbor.trading.relations.merge(town.owner,150,(a,b) -> Math.min(1000,a+b));
                    boolean volunteered=town.citizens.size()<SettlementService.populationLimit(town) && volunteer(level,neighbor,town);
                    CampaignService.record(level,town,"Drove the raiders off "+neighbor.name+". Its people are grateful"
                            +(volunteered ? ", and a volunteer joins "+town.name+"." : "; with room in "+town.name+", a volunteer would have joined."));
                    CampaignService.record(level,neighbor,town.name+" drove off a bandit raid.");
                }
            }
            default -> {}
        }
        site.rewarded=true; ExpeditionData.get(level).setDirty(); SettlementData.get(level).setDirty();
        return true;
    }
    /** A neighbor's grateful volunteer: a new citizen beside its banner who joins the defending town and walks home in time. */
    private static boolean volunteer(ServerLevel level,Settlement neighbor,Settlement town) {
        CitizenEntity citizen=WWMC.CITIZEN.get().create(level,EntitySpawnReason.EVENT);
        if(citizen==null) return false;
        for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) {
            BlockPos trial=neighbor.center.offset(x,0,z);
            if(!level.hasChunkAt(trial)) continue;
            citizen.setPos(trial.getX()+0.5,trial.getY(),trial.getZ()+0.5);
            if(!level.noCollision(citizen) || level.getBlockState(trial.below()).isAir()) continue;
            citizen.join(town.id);
            citizen.setCustomName(Component.literal(SettlementService.citizenName(level,town,citizen.getUUID())));
            if(!level.addFreshEntity(citizen)) return false;
            town.citizens.add(citizen.getUUID());
            return true;
        }
        return false;
    }
    /** Bandits sometimes raid a neutral town while a friendly player visits; at most once every two days per town. */
    private static void raids(ServerLevel level) {
        var data=ExpeditionData.get(level);
        long now=level.getGameTime();
        for(Settlement npc:SettlementData.get(level).settlements) {
            if(!npc.trading.npc || npc.citizens.isEmpty() || !level.hasChunkAt(npc.center)) continue;
            if(NEXT_RAID.getOrDefault(npc.id,0L)>now) continue;
            if(data.sites.stream().anyMatch(s -> ExpeditionData.Site.DEFEND.equals(s.objective) && !s.rewarded && npc.id.equals(s.victim))) continue;
            ServerPlayer friend=level.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(npc.center))<96*96
                    && npc.trading.relations.getOrDefault(p.getUUID(),0)>=0
                    && SettlementData.get(level).settlements.stream().anyMatch(t -> !t.trading.npc && TownAccess.manages(t,p.getUUID()))).findFirst().orElse(null);
            if(friend==null) continue;
            NEXT_RAID.put(npc.id,now+48000);
            if(level.getRandom().nextInt(4)!=0) continue;
            BlockPos ground=raidGround(level,npc.center);
            if(ground==null) continue;
            ExpeditionData.Site raid=new ExpeditionData.Site(UUID.randomUUID(),npc.center,"raid","raid:"+npc.id+":"+now,ExpeditionData.Site.DEFEND,"");
            raid.victim=npc.id; raid.spawned=true;
            if(spawn(level,raid,ground,4)==0) continue;
            data.sites.add(raid); data.setDirty();
            String text="Bandits are raiding "+npc.name+" at "+ground.toShortString()+"! Drive them off for goodwill and a volunteer.";
            SettlementService.tell(friend,text);
            for(Settlement town:SettlementData.get(level).settlements) if(!town.trading.npc && TownAccess.manages(town,friend.getUUID())) CampaignService.record(level,town,text);
        }
    }
    private static BlockPos raidGround(ServerLevel level,BlockPos center) {
        for(int attempt=0;attempt<8;attempt++) {
            double angle=level.getRandom().nextDouble()*Math.PI*2;
            BlockPos column=center.offset((int)Math.round(Math.cos(angle)*28),0,(int)Math.round(Math.sin(angle)*28));
            if(!level.hasChunkAt(column)) continue;
            BlockPos ground=new BlockPos(column.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
            if(level.getFluidState(ground.below()).isEmpty()) return ground;
        }
        return null;
    }
    private static int aliveBudget(ServerLevel level) { return ExpeditionData.get(level).sites.stream().mapToInt(s -> s.guards.size()).sum(); }
    public static int spawn(ServerLevel level,ExpeditionData.Site site,BlockPos origin,int count) {
        if(level.getDifficulty()==Difficulty.PEACEFUL || !level.hasChunkAt(origin)) return 0;
        int made=0;
        for(int n=0;n<count && aliveBudget(level)<Config.MAX_BANDITS.get();n++) {
            BlockPos pos=origin.offset(n%3-1,0,2+n/3);
            if(!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()) continue;
            var entity=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(n%3==2 ? "vindicator" : "pillager")).create(level,EntitySpawnReason.EVENT);
            Mob bandit=entity instanceof Mob mob ? mob : null;
            if(bandit==null) continue;
            bandit.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
            if(!level.noCollision(bandit)) continue;
            bandit.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),EntitySpawnReason.EVENT,null);
            bandit.setPersistenceRequired(); bandit.addTag("wwmc_expedition_"+site.id);
            if(level.addFreshEntity(bandit)) { site.guards.add(bandit.getUUID()); made++; }
        }
        if(made>0) ExpeditionData.get(level).setDirty();
        return made;
    }
    public static String claim(ServerLevel level,Settlement parent,ServerPlayer player,UUID id) {
        var site=ExpeditionData.get(level).byId(id);
        if(!TownAccess.manages(parent,player.getUUID())) return "You need steward permission.";
        if(!parent.campaign.projects.contains("frontier")) return "Complete the Frontier Charter project first.";
        if(parent.campaign.extraRoutes.size()>=4) return "Disconnect an extra supply route before claiming another outpost.";
        if(site==null || !site.cleared || site.claimed!=null || site.kind.equals("raid")) return "Choose an unclaimed, cleared expedition site.";
        if(player.distanceToSqr(Vec3.atCenterOf(site.pos))>12*12) return "Walk to the cleared site to claim it; use /wwmc outpost claim there.";
        var data=SettlementData.get(level);
        if(data.settlements.stream().anyMatch(t -> t.overlaps(site.pos,Settlement.MIN_RADIUS))) return "This site overlaps a newer settlement claim.";
        List<Station> furnished=furnishedStations(level,site);
        Map<StructureRole,BlockPos> positions=new EnumMap<>(StructureRole.class);
        for(var role:List.of(StructureRole.WAREHOUSE,StructureRole.TRADER,StructureRole.MINE,StructureRole.HOUSING,StructureRole.COURIER)) {
            BlockPos p=furnished.stream().filter(s -> s.role()==role).min(Comparator.comparingDouble(s -> s.position().distSqr(site.pos)))
                    .map(Station::position).orElseGet(() -> station(site.pos,role));
            // The furnished mining workshop uses the old courier spot for its residential room.
            if(role==StructureRole.COURIER && furnished.stream().anyMatch(s -> s.position().equals(site.pos.south(4)) && s.role()==StructureRole.HOUSING))
                p=site.pos.offset(2,0,2);
            if(!level.hasChunkAt(p)) return "Wait for the site's station positions to load before claiming.";
            var state=level.getBlockState(p);
            if(!state.isAir() && !(state.getBlock() instanceof StationBlock block && block.role()==role))
                return "Clear the center and station positions before claiming; the site was changed.";
            positions.put(role,p);
        }
        if(!level.getBlockState(site.pos).isAir()) return "Clear the center block before claiming.";
        Settlement outpost=new Settlement(UUID.randomUUID(),parent.owner,parent.name+" "+site.title()+" Outpost",site.pos,Settlement.MIN_RADIUS,List.of(),List.of(),"materials");
        outpost.populationLevel=0; outpost.campaign.parent=parent.id; outpost.campaign.members.putAll(parent.campaign.members);
        outpost.jobs.setLevel(StructureRole.MINE,JobBoard.HIGH); outpost.priority=JobBoard.CUSTOM;
        outpost.campaign.requests.put("minecraft:bread",32); outpost.campaign.requests.put("minecraft:stone_pickaxe",2);
        outpost.campaign.requests.put("minecraft:oak_planks",16);
        Regions.Region land=Regions.byId(site.resource);
        outpost.trading.exports.add(new TradeSettings.Export(land==null ? "minecraft:raw_iron" : land.product(),4,32));
        put(level,site.pos,WWMC.BANNER.get().defaultBlockState());
        for(var role:List.of(StructureRole.WAREHOUSE,StructureRole.TRADER,StructureRole.MINE,StructureRole.HOUSING,StructureRole.COURIER)) {
            BlockPos p=positions.get(role);
            if(level.getBlockState(p).isAir()) { put(level,p,WWMC.STATIONS.get(role).get().defaultBlockState()); outpost.stations.add(new Station(p,role)); }
        }
        outpost.stations.addAll(furnished);
        // Every outpost's mine works its region's ore as an endless vein.
        BlockPos mine=positions.get(StructureRole.MINE);
        for(Direction direction:List.of(Direction.WEST,Direction.NORTH,Direction.SOUTH,Direction.EAST)) {
            BlockPos vein=mine.relative(direction);
            if(!level.hasChunkAt(vein) || !level.getBlockState(vein).isAir() || !level.getBlockState(vein.above()).isAir()) continue;
            level.setBlock(vein,ore(site).defaultBlockState(),3); level.setBlock(vein.above(),ore(site).defaultBlockState(),3); break;
        }
        data.settlements.add(outpost); site.claimed=outpost.id; ExpeditionData.get(level).setDirty();
        parent.campaign.extraRoutes.add(outpost.id); outpost.campaign.extraRoutes.add(parent.id);
        SettlementService.recruit(level,outpost,4);
        CampaignService.record(level,parent,"Claimed "+outpost.name+" at "+site.pos.toShortString()+". Its trader requests food and tools from home.");
        CampaignService.record(level,outpost,"Founded as a supplied outpost of "+parent.name+". Recruit workers and keep the warehouse stocked.");
        return "Outpost claimed. Food and tools keep its mining industry working; a physical supply route now connects it to home.";
    }
    /** Adopt the example rooms as they stand, retaining station upgrades and every inventory. Old camp layouts still work. */
    private static List<Station> furnishedStations(ServerLevel level,ExpeditionData.Site site) {
        List<Station> found=new ArrayList<>(); int radius=RuinedSites.radius(site.kind);
        for(BlockPos p:BlockPos.betweenClosed(site.pos.offset(-radius,0,-radius),site.pos.offset(radius,6,radius))) {
            if(!level.hasChunkAt(p)) continue;
            var state=level.getBlockState(p);
            if(state.getBlock() instanceof StationBlock block)
                found.add(new Station(p,block.role(),state.getValue(StationBlock.FACING),state.getValue(StationBlock.RANGE),state.getValue(StationBlock.CREW),state.getValue(StationBlock.YIELD)));
        }
        return found;
    }
    private static BlockPos station(BlockPos center,StructureRole role) {
        return switch(role) { case WAREHOUSE -> center.east(2); case TRADER -> center.south(2); case MINE -> center.west(2);
            case HOUSING -> center.north(); case COURIER -> center.south(4); default -> center; };
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD) || level.getGameTime()%100!=0 || !Config.EXPEDITIONS.get()) return;
        var data=ExpeditionData.get(level);
        if(level.getGameTime()%200==0 && !level.players().isEmpty() && data.sites.size()<Config.MAX_EXPEDITIONS.get()) {
            var player=level.players().get(Math.floorMod(cursor++,level.players().size()));
            int rx=Math.floorDiv(player.blockPosition().getX(),768),rz=Math.floorDiv(player.blockPosition().getZ(),768);
            Random random=new Random(level.getSeed()^((long)rx<<32)^rz);
            BlockPos probe=new BlockPos(rx*768+128+random.nextInt(512),0,rz*768+128+random.nextInt(512));
            discover(level,probe,List.of("camp","mine","fort","townhall").get(random.nextInt(4)),rx+":"+rz);
        }
        if(level.getGameTime()%200==0) raids(level);
        // A defended raid leaves nothing to claim; its record goes once the defenders are thanked.
        if(data.sites.removeIf(s -> s.kind.equals("raid") && s.rewarded)) data.setDirty();
        for(var site:data.sites) {
            if(!level.hasChunkAt(site.pos)) continue;
            if(!site.spawned && nearby(level,site.pos,96) && spawn(level,site,site.pos,site.kind.equals("fort") ? 6 : 3)>0) {
                site.spawned=true; captives(level,site); captain(level,site); data.setDirty();
            }
            if(site.cleared) { if(!site.rewarded && !site.task().isEmpty() && !site.task().equals(ExpeditionData.Site.CLEAR)) reward(level,site); continue; }
            for(UUID id:site.guards) if(level.getEntity(id) instanceof Mob bandit && bandit.isAlive()) {
                CitizenEntity soldier=level.getEntitiesOfClass(CitizenEntity.class,bandit.getBoundingBox().inflate(24),c -> c.isAlive() && c.isGuard()
                        && SquadService.assigned(c.town(level),c.getUUID())).stream().min(Comparator.comparingDouble(bandit::distanceToSqr)).orElse(null);
                if(soldier!=null && bandit.getTarget()==null) bandit.setTarget(soldier);
                else if(bandit.getTarget()==null && bandit.blockPosition().distSqr(site.pos)>32*32) bandit.getNavigation().moveTo(site.pos.getX()+0.5,site.pos.getY(),site.pos.getZ()+0.5,0.7);
            }
            ambush(level,site);
        }
    }
    private static void ambush(ServerLevel level,ExpeditionData.Site site) {
        if(!site.spawned || site.cleared || !Config.CONVOY_RAIDS.get() || site.kind.equals("raid")) return;
        if(site.ambush!=null) {
            if(level.getGameTime()<site.nextRaid) return;
            Settlement victim=site.victim==null ? null : SettlementData.get(level).byId(site.victim);
            CitizenEntity trader=victim!=null && victim.trading.runner!=null && level.getEntity(victim.trading.runner) instanceof CitizenEntity c ? c : null;
            if(trader!=null && trader.isAlive() && trader.tradeCargoCount()>0 && trader.blockPosition().distSqr(site.pos)<128*128
                    && nearby(level,trader.blockPosition(),96)) {
                BlockPos ground=ambushGround(level,trader.blockPosition()); if(ground!=null) spawn(level,site,ground,3);
            }
            site.ambush=null; site.victim=null; site.nextRaid=level.getGameTime()+12000; ExpeditionData.get(level).setDirty(); return;
        }
        if(level.getGameTime()<site.nextRaid) return;
        for(Settlement town:SettlementData.get(level).settlements) if(town.trading.runner!=null && town.trading.runnerPos!=null && town.trading.runnerPos.distSqr(site.pos)<128*128
                && nearby(level,town.trading.runnerPos,96)) {
            if(!(level.getEntity(town.trading.runner) instanceof CitizenEntity trader) || !trader.isAlive() || trader.tradeCargoCount()==0) continue;
            BlockPos ground=ambushGround(level,town.trading.runnerPos); if(ground==null) continue;
            site.ambush=ground; site.victim=town.id; site.nextRaid=level.getGameTime()+600;
            CampaignService.record(level,town,"Bandits are gathering near the trader at "+ground.toShortString()+". Escort the shipment or clear their camp.");
            ExpeditionData.get(level).setDirty(); return;
        }
    }
    private static BlockPos ambushGround(ServerLevel level,BlockPos trader) {
        BlockPos column=trader.offset(8,0,8); if(!level.hasChunkAt(column)) return null;
        BlockPos ground=new BlockPos(column.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
        return level.getFluidState(ground.below()).isEmpty() ? ground : null;
    }
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel level)) return;
        var killer=event.getSource().getEntity();
        boolean helper=killer instanceof ServerPlayer || killer instanceof CitizenEntity citizen && citizen.town(level)!=null && !citizen.town(level).trading.npc;
        if(helper) for(var site:ExpeditionData.get(level).sites) if(site.kind.equals("raid") && site.guards.contains(event.getEntity().getUUID())) FOUGHT.add(site.id);
        removed(level,event.getEntity().getUUID());
    }
    @SubscribeEvent public void left(EntityLeaveLevelEvent event) {
        // Peaceful-mode removal is final; a chunk unload must retain its saved defender and budget slot.
        if(event.getLevel() instanceof ServerLevel level && event.getEntity().getRemovalReason()==Entity.RemovalReason.DISCARDED)
            removed(level,event.getEntity().getUUID());
    }
    private static void removed(ServerLevel level,UUID defender) {
        var data=ExpeditionData.get(level);
        for(var site:data.sites) if(site.guards.remove(defender)) {
            if(defender.equals(site.leader))
                for(Settlement town:SettlementData.get(level).settlements) if(!town.trading.npc && town.center.distSqr(site.pos)<4096.0*4096.0)
                    CampaignService.record(level,town,"The Bandit Captain of the "+site.title()+" at "+site.pos.toShortString()+" has fallen; its gear lies where it died.");
            if(site.spawned && site.guards.isEmpty() && site.kind.equals("raid")) { site.cleared=true; data.setDirty(); break; }
            if(site.spawned && site.guards.isEmpty()) {
                site.cleared=true; site.ambush=null;
                for(Settlement town:SettlementData.get(level).settlements) if(!town.trading.npc && town.center.distSqr(site.pos)<4096.0*4096.0)
                    CampaignService.record(level,town,"The "+site.title()+" at "+site.pos.toShortString()+" is cleared. Recover its supplies or claim an outpost with a Frontier Charter.");
            }
            data.setDirty(); break;
        }
    }
}
