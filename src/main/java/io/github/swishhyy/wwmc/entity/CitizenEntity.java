package io.github.swishhyy.wwmc.entity;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.DiagnosticWindow;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.CitizenNames;
import io.github.swishhyy.wwmc.core.WorkCadence;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Villager-styled citizen with visible equipment and an independent station-driven work routine. */
public final class CitizenEntity extends Villager {
    private static final EntityDataAccessor<Integer> JOB_LOOK=SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WORK_LOOK=SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> WORK_BEGAN=SynchedEntityData.defineId(CitizenEntity.class,EntityDataSerializers.LONG);
    private long workingUntil,nextFeedbackPulse,nextFeedbackSound;
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(JOB_LOOK,-1); builder.define(WORK_LOOK,WorkFeedback.NONE); builder.define(WORK_BEGAN,0L);
    }
    /** The saved assignment, also visible to remote clients while the worker is idle or resting. */
    public StructureRole appearanceJob() {
        int job=entityData.get(JOB_LOOK);
        return job>=0 && job<StructureRole.values().length ? StructureRole.values()[job] : null;
    }
    public int workAnimation() { return entityData.get(WORK_LOOK); }
    public long workAnimationBegan() { return entityData.get(WORK_BEGAN); }
    public void working(int kind) {
        if(!(level() instanceof ServerLevel) || isSleeping() || recovering) return;
        if(entityData.get(WORK_LOOK)!=kind || level().getGameTime()>=workingUntil) entityData.set(WORK_BEGAN,level().getGameTime());
        workingUntil=level().getGameTime()+15; entityData.set(WORK_LOOK,kind);
    }
    public boolean feedbackPulse(long now) { if(now<nextFeedbackPulse) return false; nextFeedbackPulse=now+20; return true; }
    public boolean feedbackSound(long now) { if(now<nextFeedbackSound) return false; nextFeedbackSound=now+80; return true; }
    private enum Action { HARVEST,FELL,PLANT,EXCAVATE,SUPPORT,CAVE,VEIN,GATHER }
    private Action action=Action.HARVEST;
    private ForestryService.Task forestTask;
    private String forestIdleReason="No accessible natural tree; needs saplings and clear soil in range";
    private ExcavationService.Ticket excavation;
    private BlockPos targetLease;
    /** Arrows a guard with a bow keeps in their bag. */
    private static final int ARROW_STOCK=32;
    /** Extra attack damage a stored weapon needs before a guard swaps for it; a fresher copy of the same sword is not worth the trip. */
    private static final double MELEE_UPGRADE=0.5;
    private int minimumAxeDurability=1,guardAttackTicks,patrolTicks,gearTicks,patrolVisits,scavengeTicks,armoryTicks;
    private boolean armoryStocked;
    private boolean guardWasActive;
    private UUID gearStand;
    private int gearPathTicks,healingTicks;
    private final Map<UUID,Long> ignoredStands=new HashMap<>();
    private long gearReturnAt,shiftGearUntil,nextSmithAt;
    /** This is the original item taken for repair, saved separately until it is returned. */
    private ItemStack repairItem=ItemStack.EMPTY;
    private UUID repairStand;
    private EquipmentSlot repairSlot;
    private BlockPos repairAnvil,smithStand;
    private ForgeWorkshop.Plan forgeJob;
    private boolean forgeDelivery;
    private boolean repairDelivery;
    private long nextFoodTripAt,lastMealAt;
    private BlockPos pantryTarget,pantryStand;
    private final AnimalWork animalWork=new AnimalWork();
    /** Ticks a craftsman works one batch at the bench. */
    private static final int CRAFT_TICKS=40;
    private BlockPos trapWork;
    private final Map<BlockPos,Long> failedTrapWork=new HashMap<>();
    private int trapWorkTicks,trapPathTicks;
    private static final int MAX_FAILED_TARGETS=2048;
    /** Ticks between a citizen's looks for an open place in a job of higher priority than its own. */
    private static final int PROMOTION_CHECK=600;
    /** A cook's bread batch. */
    private Crafting.Recipe order;
    /** A craftsman's learned order and the recipe chosen for it. */
    private Workshop.Job craftJob;
    /** A courier's current errand: the job station whose barrels it serves, and whether it is bringing supplies. */
    private BlockPos haulStation;
    private boolean haulSupply;
    private int haulTicks;
    /** The storage a supply or delivery trip is walking to, and for how long; an unreachable job barrel is skipped for a minute. */
    private BlockPos depotTarget,depotStand;
    private int depotTicks;
    private static final int BARREL_WALK_TICKS=400;
    /** An enchanter's item, kept apart from the bag until it is delivered, with the work done on it and the level rolled for it. */
    private ItemStack enchantItem=ItemStack.EMPTY;
    private int enchantTicks,enchantLevel;
    private BlockPos researchDesk,researchStand;
    private final Set<BlockPos> researchRejectedStands=new HashSet<>();
    private long nextResearchRouteAt;
    private Research.Status researchState=Research.Status.waiting("Waiting for the researcher to start work.");
    private String researchProject="";
    private BlockPos researchStation;
    private long researchStateAt=Long.MIN_VALUE;
    private boolean enchantDone;
    private BlockPos enchantTable,enchantStand;
    private long nextEnchantAt;
    /** Kinds of item no enchantment fit, skipped until the given game time. */
    private final Map<Item,Long> unenchantable=new HashMap<>();
    /** Reported hostiles this guard could not reach, ignored until the given game time, and how long it has chased the current one out of sight. */
    private final Map<UUID,Long> ignoredThreats=new HashMap<>();
    private UUID respondTarget;
    private int respondTicks;
    /** A civilian's call to the guards, shown instead of the activity for a few seconds. */
    private String callNote="";
    private long callNoteUntil;
    /** The job worked last; a change sends that job's tools, weapons and armor back to the warehouse. */
    private StructureRole lastRole;
    private boolean returningGear;
    /** Loose items a guard could not reach, ignored until the given game time. */
    private final Map<UUID,Long> ignoredLoot=new HashMap<>();
    private UUID scavengeTarget;
    private int scavengePathTicks;
    /** Thirty seconds without progress on a job route triggers recovery beside the job, with a banner fallback. */
    private static final int STUCK_TICKS=600;
    private Vec3 stuckAnchor;
    private int stuckTicks,lastWalkTick=-1000;
    private BlockPos blockedJob;
    private Vec3 blockedJobAnchor;
    private long blockedJobSince=-1,lastBlockedJobAttempt;
    private static final int COMBAT_QUIET_TICKS=200;
    private long combatUntil=-1,fearUntil=-1,nextFearCheck;
    private UUID combatEnemy;
    private boolean nearbyDanger,sheltering;
    private BlockPos patrolTarget,activePost;
    private BlockPos processor,processorStand;
    private boolean processingDelivery,processingSupplied;
    private long nextProcessingAt,guardSupplyAt;
    private int processingIdle;
    private record IdleStation(long until,String reason) {}
    private final Map<BlockPos,IdleStation> idleStations=new HashMap<>();
    private UUID settlementId;
    private BlockPos workplace, target, sleepingBed,homeBed,workStand,clearingLeaf;
    private final CitizenInventory cargo=new CitizenInventory(this::canOpenInventory);
    private final Map<BlockPos,Long> failedTargets=new HashMap<>();
    private int searchDelay, workProgress, pathTicks, blindTicks, mealTicks=7200;
    private final WorkCadence.ReachBudget reachBudget=new WorkCadence.ReachBudget();
    private long nextPathAt;
    private BlockPos pathDestination;
    /** Game time of the next look for a job of higher priority with an open place. */
    private long nextPromotionAt;
    /** The town's priority revision at that look; a change brings the next look forward. */
    private int seenJobRevision=-1;
    /** A job the owner just released this citizen from, which it does not take again for a minute. */
    private BlockPos leftJob;
    private long leftJobUntil;
    /** Why the citizen has no work at the moment, shown as its activity. */
    private String jobNote="Looking for a job";
    private String activity="Waiting for a job station";
    private final DiagnosticWindow diagnosticWindow=new DiagnosticWindow();
    private long nextRescueWarning;
    private TradeShipment tradeShipment=new TradeShipment();
    private final TradeNavigation tradeNavigation=new TradeNavigation();
    private final TradeNavigation expeditionNavigation=new TradeNavigation();
    private boolean recovering;
    private BlockPos hospitalBed,hospitalStand;
    private int hospitalRestTicks,hospitalPathTicks;
    private long nextTradeAt;
    private int lastNpcHurt=-1;
    /** Experience earned at each job, by job id; it stays with the citizen when it changes job. */
    private final Map<String,Integer> experience=new HashMap<>();
    /** The last few meals eaten, oldest first, for a varied diet. */
    private List<String> recentMeals=new ArrayList<>();
    private int happiness=50;
    public int happiness() { return happiness; }
    public void setHappiness(int value) { happiness=Math.clamp(value,0,100); }
    public BlockPos homeBed() { return homeBed; }
    public void setHomeBed(BlockPos bed) { homeBed=bed==null ? null : bed.immutable(); }
    public CitizenEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level); setPersistenceRequired(); setCanPickUpLoot(false);
        for(EquipmentSlot slot:EquipmentSlot.values()) setDropChance(slot,0);
        planLongRoutes();
    }
    /**
     * Villagers plan 48-block routes at most; towns are larger, so citizens plan farther with a matching search budget.
     * Citizens never pick fights by follow range, so it only sets how far a route may reach. Saved citizens load their
     * old range, so this runs again after loading.
     */
    private void planLongRoutes() {
        var range=getAttribute(Attributes.FOLLOW_RANGE);
        if(range!=null && range.getBaseValue()<CitizenNavigation.ROUTE_LENGTH) range.setBaseValue(CitizenNavigation.ROUTE_LENGTH);
        getNavigation().setRequiredPathLength(CitizenNavigation.ROUTE_LENGTH);
    }
    public void join(UUID id) { settlementId=id; mealTicks=Config.mealIntervalTicks(); }
    public Settlement town(ServerLevel level) { return settlementId==null ? null : SettlementData.get(level).byId(settlementId); }
    @Override protected PathNavigation createNavigation(Level level) { return new CitizenNavigation(this,level); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        // The villager brain that normally opens doors is disabled for citizens, so doors are handled here.
        goalSelector.addGoal(1,new OpenDoorGoal(this,true));
        goalSelector.addGoal(1,new ShelterGoal());
        goalSelector.addGoal(2,new RestGoal());
        goalSelector.addGoal(3,new WorkGoal());
        goalSelector.addGoal(3,new ChildGoal());
        goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,6.0F));
        goalSelector.addGoal(5,new RandomLookAroundGoal(this));
    }
    // Keep ordinary villager trades, breeding, and POI jobs out of the custom work scheduler.
    @Override protected void customServerAiStep(ServerLevel level) {}
    @Override public void tick() {
        if(level() instanceof ServerLevel server && !recovering && isGuard() && !HospitalCare.needsCare(town(server),this) && WorkCadence.due(server.getGameTime(),getId(),10)) {
            Settlement town=town(server); Station station=homeStation(town);
            if(station==null) station=town.station(workplace);
            BlockPos post=station.position();
            // Sleeping reserves do not run their work goal, but still belong to this station's roster.
            SettlementService.workers(server).claim(post,getUUID(),server.getGameTime(),200,SettlementService.workerLimit(town,station));
            if(GuardService.onDuty(server,town,post,getUUID()) || DefenseService.bellRun(town,getUUID())!=null
                    || isSleeping() && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> !getItemBySlot(slot).isEmpty())) wakeForAlarm();
            else if(sleepingBed!=null) {
                if(SettlementService.housingBeds(server,town).contains(sleepingBed))
                    SettlementService.reservations(server).claim(sleepingBed,getUUID(),server.getGameTime(),200);
                else leaveBed();
            }
        }
        super.tick();
        if(level() instanceof ServerLevel server) {
            if(server.getGameTime()>=workingUntil || isSleeping() || recovering) entityData.set(WORK_LOOK,WorkFeedback.NONE);
            if(WorkCadence.due(server.getGameTime(),getId(),20)) {
                Settlement assignedTown=town(server);
                Station assigned=assignedTown==null || isBaby() ? null : assignedTown.station(assignedTown.jobs.home(getUUID()));
                entityData.set(JOB_LOOK,assigned==null ? -1 : assigned.role().ordinal());
            }
            if(WorkCadence.due(server.getGameTime(),getId(),10) && isAlive()) {
                Settlement town=town(server);
                if(HospitalCare.needsCare(town,this)) {
                    reachBudget.reset(); failedTargets.entrySet().removeIf(e -> e.getValue()<=server.getGameTime());
                    HospitalCare.patient(server,town,this);
                }
            }
            if(mealTicks>0) mealTicks--;
            if(healingTicks>0) healingTicks--;
            if(guardAttackTicks>0) guardAttackTicks--;
            if(WorkCadence.due(server.getGameTime(),getId(),20)) {
                cargo.flush();
                Settlement town=town(server);
                if(town!=null) {
                    if(isBaby()) {
                        if(town.progress.children.add(getUUID()) | town.jobs.release(getUUID())) SettlementData.get(server).setDirty();
                        if(workplace!=null) releaseWork(server);
                    } else if(town.progress.children.remove(getUUID())) {
                        SettlementData.get(server).setDirty();
                        CampaignService.record(server,town,getName().getString()+" has grown up and can now take a job.");
                    }
                    if(town.trading.npc && getLastHurtByMob() instanceof Player attacker && getLastHurtByMobTimestamp()>lastNpcHurt) {
                        lastNpcHurt=getLastHurtByMobTimestamp();
                        TradeRoutes.attacked(town,attacker.getUUID(),SettlementData.get(server).settlements); SettlementData.get(server).setDirty();
                    }
                    if(!cargo.isOpen()) eatFrom(List.of(cargo));
                    if(getCustomName()==null || CitizenNames.numbered(getCustomName().getString()))
                        setCustomName(Component.literal(SettlementService.citizenName(server,town,getUUID())));
                    String name=getCustomName().getString();
                    if(!name.equals(town.citizenNames.put(getUUID(),name))) SettlementData.get(server).setDirty();
                    if(isAlive()) CitizenRecall.seen(server,town,this);
                    if(!isBaby()) { checkStuck(server,town); reportDiagnostics(server,town); }
                }
                else diagnosticWindow.reset();
            }
        }
    }
    /** Context is built only when a diagnostic is emitted; no scans, path probes or chunk loads. */
    private String diagnosticContext(ServerLevel level,Settlement town) {
        BlockPos home=town.jobs.home(getUUID()); Station station=home==null ? null : town.station(home);
        return "dimension="+level.dimension()+" town=\""+town.name+"\" townId="+town.id+" citizen=\""+getName().getString()
                +"\" citizenId="+getUUID()+" job="+(station==null ? "none" : station.role().id())+" station="+home+" position="+blockPosition();
    }
    private void reportDiagnostics(ServerLevel level,Settlement town) {
        if(!Config.SERVER_DIAGNOSTICS.get() || !isAlive() || isNoAi() || isSleeping() || cargo.isOpen()
                || !recovering && !tradeShipment.travelling() && !isGuard() && (night(level) || DefenseService.alarmed(town))) {
            diagnosticWindow.reset(); return;
        }
        boolean blocked=TownNeeds.asks(activity) || activity.startsWith("Needs a free, reachable hospital bed")
                || activity.startsWith("Stuck,") || activity.startsWith("Trade route blocked");
        BlockPos home=town.jobs.home(getUUID());
        String problem=blocked ? town.id+"|"+home : null;
        long now=level.getGameTime();
        switch(diagnosticWindow.sample(problem,now,Config.DIAGNOSTIC_DELAY.get()*20L,Config.DIAGNOSTIC_REPEAT.get()*20L)) {
            case WARNING -> {
                int slots=0;
                for(int slot=0;slot<cargo.getContainerSize();slot++) if(!cargo.getItem(slot).isEmpty()) slots++;
                WWMC.LOGGER.warn("[WWMC][worker-stalled] {} blockedSeconds={} activity=\"{}\" target={} depot={} pantry={} pantryStand={} pathDestination={} navigationDone={} pathTicks={} failedTargets={} health={}/{} mealTicks={} bagSlots={}/{} pendingStacks={}",
                        diagnosticContext(level,town),diagnosticWindow.blockedTicks(now)/20,activity,target,depotTarget,pantryTarget,pantryStand,pathDestination,getNavigation().isDone(),pathTicks,failedTargets.size(),
                        getHealth(),getMaxHealth(),mealTicks,slots,cargo.getContainerSize(),cargo.pendingItems().size());
            }
            case RESOLVED -> WWMC.LOGGER.info("[WWMC][worker-resumed] {} activity=\"{}\"",diagnosticContext(level,town),activity);
            default -> {}
        }
    }
    private void clearBlockedJob() { blockedJob=null; blockedJobAnchor=null; blockedJobSince=-1; }
    /** Failed plans survive the job's short retry pauses; they do not count ordinary idle time as being stuck. */
    private void failedJobPath(ServerLevel level) {
        Settlement town=town(level); Station home=town==null ? null : homeStation(town);
        if(home==null || home.role()==StructureRole.TRADER || sheltering || recovering || inCombat()
                || SquadService.assigned(town,getUUID()) || night(level) && home.role()!=StructureRole.GUARD) return;
        long now=level.getGameTime();
        if(!home.position().equals(blockedJob) || blockedJobAnchor==null || position().distanceToSqr(blockedJobAnchor)>2.25) {
            blockedJob=home.position(); blockedJobAnchor=position(); blockedJobSince=now;
        }
        lastBlockedJobAttempt=now;
    }
    /** Movement stalls and repeated failed job plans share the same thirty-second, trader-free recovery. */
    private void checkStuck(ServerLevel level,Settlement town) {
        Station home=homeStation(town);
        if(home==null || home.role()==StructureRole.TRADER || tradeShipment.travelling() || SquadService.assigned(town,getUUID())
                || sheltering || recovering || inCombat() || isNoAi() || cargo.isOpen() || isSleeping() || isPassenger()
                || night(level) && home.role()!=StructureRole.GUARD) {
            clearBlockedJob(); stuckAnchor=null; stuckTicks=0; return;
        }
        long now=level.getGameTime();
        if(blockedJob!=null && (!blockedJob.equals(home.position()) || now-lastBlockedJobAttempt>260
                || position().distanceToSqr(blockedJobAnchor)>2.25)) clearBlockedJob();
        boolean failed=blockedJobSince>=0 && now-blockedJobSince>=STUCK_TICKS;
        boolean trying=tickCount-lastWalkTick<=40 && !isSleeping() && !isPassenger();
        if(!trying || stuckAnchor==null || position().distanceToSqr(stuckAnchor)>2.25) { stuckAnchor=position(); stuckTicks=0; }
        else stuckTicks+=20;
        if(!failed && stuckTicks<STUCK_TICKS) return;
        stuckAnchor=null; stuckTicks=0;
        Vec3 spot=SettlementService.active(level,home) ? standingRoom(level,home.position()) : null;
        boolean atJob=spot!=null;
        if(spot==null) spot=rescueSpot(level,town);
        clearBlockedJob();
        if(Config.SERVER_DIAGNOSTICS.get() && now>=nextRescueWarning) {
            WWMC.LOGGER.warn("[WWMC][stuck-rescue] {} result={} destination={} activity=\"{}\" target={} pathDestination={}",
                    diagnosticContext(level,town),spot==null ? "no-standing-room" : atJob ? "returned-to-job" : "returned-to-banner",spot,activity,target,pathDestination);
            nextRescueWarning=now+Config.DIAGNOSTIC_REPEAT.get()*20L;
        }
        if(spot==null) { activity="Stuck, and neither my job nor the banner has free standing room"; return; }
        getNavigation().stop();
        abandonTrip(level); releaseWork(level);
        activity=atJob ? "Got stuck and returned to my job" : "Got stuck and returned to the settlement banner";
        idleStations.remove(home.position()); failedTargets.clear(); searchDelay=0; pathDestination=null; nextPathAt=0;
        setPos(spot.x,spot.y,spot.z); resetFallDistance();
    }
    /** Abandons the trip under way, so the citizen does not walk straight back into the same trap; false when there was none. */
    private boolean abandonTrip(ServerLevel level) {
        if(action==Action.EXCAVATE && excavation!=null && excavation.quarry() && !excavation.remote() && workplace!=null) {
            SettlementService.reservations(level).release(excavation.lease(),getUUID());
            excavation=excavation.fromControlBlock(workplace); targetLease=excavation.lease(); pathTicks=0; blindTicks=0;
        } else if(target!=null) cancelTarget(level,true);
        else if(returningGear) returningGear=false; // the gear stays in the bag and goes back with the next delivery
        else if(isGuard()) patrolTarget=null;
        else return false;
        return true;
    }
    /**
     * Brings this citizen back from a frozen or unloaded chunk to its station or banner ({@code home}), keeping its
     * job. The errand that led it out is dropped. False when it is on a trade trip or there is no room to stand.
     */
    public boolean recall(ServerLevel level,Settlement town,BlockPos home) {
        if(tradeShipment.travelling() || SquadService.assigned(town,getUUID())) return false;
        Vec3 spot=standingRoom(level,home);
        if(spot==null) spot=rescueSpot(level,town);
        if(spot==null) return false;
        leaveHospitalBed();
        leaveBed();
        if(isPassenger()) stopRiding();
        getNavigation().stop();
        abandonTrip(level);
        setPos(spot.x,spot.y,spot.z); resetFallDistance();
        stuckAnchor=null; stuckTicks=0; clearBlockedJob();
        activity="Was out of loaded range and came back";
        return true;
    }
    private static Vec3 rescueSpot(ServerLevel level,Settlement town) { return standingRoom(level,town.center); }
    /** On top of a block such as the banner, or failing that a clear spot with firm footing right beside it; never in an unloaded or frozen chunk. */
    private static Vec3 standingRoom(ServerLevel level,BlockPos anchor) {
        if(!level.hasChunkAt(anchor) || !level.isPositionEntityTicking(anchor)) return null;
        CitizenReach.StandingView ground=CitizenReach.ground(level,pos -> level.hasChunkAt(pos) && level.isPositionEntityTicking(pos));
        List<BlockPos> spots=new ArrayList<>(List.of(anchor.above()));
        for(int dy=1;dy>=-1;dy--) for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) if(dx!=0 || dz!=0) spots.add(anchor.offset(dx,dy,dz));
        for(BlockPos spot:spots) {
            if(CitizenReach.standing(ground,spot)) return ground.feet(spot);
        }
        return null;
    }
    // Citizens never shove each other, so crews can pass on narrow quarry stairs and walkways without knocking anyone off.
    @Override protected void doPush(Entity entity) { if(!(entity instanceof CitizenEntity)) super.doPush(entity); }
    public boolean inQuarry(ServerLevel level) { Settlement town=town(level); return town!=null && ExcavationService.inPit(level,town,blockPosition()); }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(level() instanceof ServerLevel server && hand==InteractionHand.MAIN_HAND) {
            Settlement town=town(server);
            if(town!=null && town.owner.equals(player.getUUID()) && player.isShiftKeyDown()) {
                BlockPos left=town.jobs.home(getUUID());
                if(left!=null) { town.jobs.release(getUUID()); leftJob=left; leftJobUntil=server.getGameTime()+1200; SettlementData.get(server).setDirty(); }
                releaseWork(server); searchDelay=0; nextPromotionAt=0;
                SettlementService.notify(player,getName().getString()+" released their job and will take another open place.");
            } else if(canOpenInventory(player) && player.getItemInHand(hand).isEmpty()) {
                if(player instanceof ServerPlayer viewer) Panels.openCitizen(viewer,this,cargo);
            } else if(canOpenInventory(player) && FoodHealing.food(player.getItemInHand(hand))) {
                if(wantsMeal()) {
                    ItemStack held=player.getItemInHand(hand);
                    ItemStack meal=player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1);
                    consumeMeal(meal);
                    if(!player.getAbilities().instabuild) {
                        var remainder=meal.get(DataComponents.USE_REMAINDER);
                        if(remainder!=null) cargo.offer(remainder.convertInto().create());
                    }
                } else SettlementService.notify(player,getName().getString()+": Already fed. Injuries recover in hospital beds.");
            } else {
                SettlementService.notify(player,getName().getString()+": "+activity+
                        (workplace==null ? "" : " at "+workplace.toShortString()));
            }
        }
        return InteractionResult.SUCCESS;
    }
    private boolean canOpenInventory(Player player) {
        if(!(level() instanceof ServerLevel server) || !isAlive() || distanceToSqr(player)>64) return false;
        Settlement town=town(server); return TownAccess.manages(town,player.getUUID());
    }
    /** Role of the station this citizen works at, or null. */
    private StructureRole role() {
        if(!(level() instanceof ServerLevel server) || workplace==null) return null;
        Settlement town=town(server); Station station=town==null ? null : town.station(workplace);
        return station==null ? null : station.role();
    }
    /** The saved post identifies a guard even while its active work goal is paused or has not resumed after loading. */
    public boolean isGuard() {
        if(isBaby() || !(level() instanceof ServerLevel server)) return false;
        Settlement town=town(server); if(town==null) return false;
        Station station=homeStation(town);
        if(station==null) station=town.station(workplace);
        return station!=null && station.role()==StructureRole.GUARD && town.jobs.level(StructureRole.GUARD)!=JobBoard.OFF && SettlementService.active(server,station);
    }
    /** Damage and attacks keep recovery paused for ten quiet seconds; a nearby, live opponent keeps a guard fighting. */
    public boolean inCombat() {
        if(!(level() instanceof ServerLevel server)) return false;
        if(server.getGameTime()<combatUntil) return true;
        LivingEntity enemy=getTarget();
        if(isGuard() && enemy!=null && enemy.isAlive() && distanceToSqr(enemy)<=48*48) return true;
        return nearbyThreat(server,isGuard() ? 24 : 12);
    }
    private boolean nearbyThreat(ServerLevel level,int range) {
        if(level.getGameTime()>=nextFearCheck) {
            nextFearCheck=level.getGameTime()+20;
            nearbyDanger=!level.getEntitiesOfClass(Monster.class,getBoundingBox().inflate(range),
                    m -> DefenseService.hostile(m) && distanceToSqr(m)<=range*range && hasLineOfSight(m)).isEmpty();
        }
        return nearbyDanger;
    }
    public void combatWith(LivingEntity enemy) {
        if(!(level() instanceof ServerLevel server) || !enemy.isAlive()) return;
        combatEnemy=enemy.getUUID(); combatUntil=server.getGameTime()+COMBAT_QUIET_TICKS;
        Settlement town=town(server);
        if(town!=null && enemy instanceof Monster monster && DefenseService.hostile(monster) && town.contains(monster.blockPosition()))
            DefenseService.report(town,monster,getName().getString(),false,server.getGameTime());
        if(recovering || hospitalBed!=null) { leaveHospitalBed(); recovering=false; }
        if(isGuard()) leaveBed();
    }
    @Override public float applyItemBlocking(ServerLevel level,DamageSource source,float amount) {
        ItemStack blocking=getItemBlockingWith();
        EquipmentSlot slot=getUsedItemHand().asEquipmentSlot();
        float stopped=super.applyItemBlocking(level,source,amount);
        // Vanilla's BlocksAttacks durability path only handles players; citizens spend the same real shield wear.
        if(stopped>0 && blocking!=null && blocking.is(Items.SHIELD)) {
            var rules=blocking.get(DataComponents.BLOCKS_ATTACKS);
            if(rules!=null) {
                int wear=rules.itemDamage().apply(stopped);
                if(wear>0) blocking.hurtAndBreak(wear,this,slot);
                if(blocking.isEmpty()) stopUsingItem();
            }
        }
        return stopped;
    }
    /** A guard post has an open place and guarding matters more than this citizen's own job, so it volunteers. */
    private boolean guardVacancy(ServerLevel level,Settlement town) {
        if(isBaby()) return false;
        int guard=town.jobs.level(StructureRole.GUARD);
        // A citizen finishing an enchantment, a repair or a trade run cannot change job yet, so the post waits for another.
        if(guard==JobBoard.OFF || midTask()) return false;
        Station home=homeStation(town);
        if(home!=null && (home.role()==StructureRole.GUARD || town.jobs.level(home.role())>=guard)) return false;
        return town.stations.stream().anyMatch(s -> s.role()==StructureRole.GUARD && SettlementService.active(level,s)
                && town.jobs.assigned(s.position())<SettlementService.workerLimit(town,s));
    }
    /** The station this citizen works at by assignment, even while it sleeps or runs an errand. */
    private Station homeStation(Settlement town) {
        BlockPos home=town.jobs.home(getUUID());
        return home==null ? null : town.station(home);
    }
    public int experience(StructureRole role) { return role==null ? 0 : experience.getOrDefault(role.id(),0); }
    public int skillLevel(StructureRole role) { return CitizenSkill.level(experience(role)); }
    /** The job experience counts toward: the citizen's own station, even while asleep or on an errand, or the guard post it covers. */
    public StructureRole skillRole() {
        if(isBaby() || !(level() instanceof ServerLevel server)) return null;
        Settlement town=town(server);
        Station home=town==null ? null : homeStation(town);
        return home!=null ? home.role() : isGuard() ? StructureRole.GUARD : null;
    }
    /** Finished work at a job; reaching a new level is written in the town journal. */
    public void gainExperience(StructureRole role,int amount) {
        if(role==null || amount<=0 || !(level() instanceof ServerLevel server)) return;
        int before=experience(role),after=Math.min(CitizenSkill.CAP,before+amount);
        experience.put(role.id(),after);
        int reached=CitizenSkill.level(after);
        Settlement town=town(server);
        TutorialProgress.completed(server,town,role);
        if(reached>CitizenSkill.level(before) && town!=null) {
            String title=CitizenSkill.title(reached);
            CampaignService.journal(server,town,getName().getString()+" is now "+("AEIOU".indexOf(title.charAt(0))>=0 ? "an " : "a ")+title+" "
                    +role.title().toLowerCase(Locale.ROOT)+": "+CitizenSkill.perk(role,reached)+".");
        }
    }
    public void gainExperience(int amount) { gainExperience(skillRole(),amount); }
    public List<String> recentMeals() { return List.copyOf(recentMeals); }
    /** Extra work speed, in percent, from experience at the current job and a varied diet. */
    public int speedBonus() {
        StructureRole role=skillRole();
        int bonus=MealVariety.bonus(recentMeals)+CitizenSkill.speed(role,skillLevel(role))+WorkerTools.bonus(role,getMainHandItem());
        return level() instanceof ServerLevel server ? bonus+Research.speed(town(server),role) : bonus;
    }
    /** Ticks that work taking {@code base} ticks for an ordinary newcomer takes this citizen. */
    public int effort(int base) { return Math.max(1,base*100/(100+speedBonus())); }
    /**
     * Progress from one ten-tick work step. Work advances in whole steps, so a small speed bonus would round away;
     * instead it is the chance of a double step, which gives the same speed on average.
     */
    public int workStep() {
        int bonus=speedBonus();
        if(bonus<0) return getRandom().nextInt(100)<-bonus ? 0 : 10;
        return bonus>0 && getRandom().nextInt(100)<bonus ? 20 : 10;
    }
    /** Of {@code uses} uses of a tool, how many cost durability; experienced workers spare some. */
    public int toolWear(int uses) {
        StructureRole role=skillRole();
        int saving=CitizenSkill.toolSaving(role,skillLevel(role))+(level() instanceof ServerLevel server ? Research.toolSaving(town(server),role) : 0),cost=0;
        for(int n=0;n<uses;n++) if(saving<=0 || getRandom().nextInt(100)>=saving) cost++;
        return cost;
    }
    /** One use of the held tool. */
    public void wearTool() {
        int cost=toolWear(1);
        if(cost>0) getMainHandItem().hurtAndBreak(cost,this,EquipmentSlot.MAINHAND);
    }
    private boolean night(ServerLevel level) { return SettlementService.night(level); }
    private boolean alarmed() {
        if(!(level() instanceof ServerLevel server)) return false;
        Settlement town=town(server); return town!=null && DefenseService.alarmed(town);
    }
    private boolean near(BlockPos pos) { return distanceToSqr(Vec3.atCenterOf(pos))<=6.25; }
    private boolean visible(ServerLevel level,BlockPos pos) {
        return CitizenReach.visible(level,getEyePosition(),pos);
    }
    private boolean canUse(ServerLevel level,BlockPos pos) { return CitizenReach.canUse(level,getEyePosition(),pos); }
    private boolean handNear(BlockPos pos) { return CitizenReach.within(getEyePosition(),pos); }
    private boolean walk(BlockPos pos) { return walk(pos,0.65); }
    private boolean walk(BlockPos pos,double speed) { return walk(pos,speed,1); }
    /** Accuracy 0 ends on the block itself, for standing spots chosen for their view of the work. */
    private boolean walk(BlockPos pos,double speed,int accuracy) {
        lastWalkTick=tickCount;
        // Trips into or out of a deep quarry follow its spiral stairs a few steps at a time.
        if(level() instanceof ServerLevel server && town(server)!=null) {
            BlockPos via=ExcavationService.waypoint(server,town(server),blockPosition(),pos);
            if(via!=null) { pos=via; accuracy=1; }
        }
        // Vanilla accepts a waypoint within part of a block. A usable work spot may need its actual center:
        // stopping a few tenths short can leave the target outside hand reach or behind adjacent furniture.
        if(accuracy==0 && getNavigation().isDone() && level() instanceof ServerLevel server
                && Math.floor(getX())==pos.getX() && Math.floor(getZ())==pos.getZ()) {
            var ground=CitizenReach.ground(server,server::hasChunkAt);
            if(CitizenReach.standing(ground,pos)) {
                Vec3 feet=ground.feet(pos);
                if(Math.abs(getY()-feet.y)<0.6) {
                    if(position().distanceToSqr(feet)>1.0E-6) getMoveControl().setWantedPosition(feet.x,feet.y,feet.z,speed);
                    getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
                    return true;
                }
            }
        }
        long now=level().getGameTime();
        if(getNavigation().isDone() || !pos.equals(pathDestination) || now>=nextPathAt) {
            var path=getNavigation().createPath(pos,accuracy);
            if(path==null) { if(level() instanceof ServerLevel server && onGround()) failedJobPath(server); return false; }
            if(path.canReach()) clearBlockedJob();
            else if(level() instanceof ServerLevel server && onGround()) failedJobPath(server);
            getNavigation().moveTo(path,speed);
            pathDestination=pos.immutable(); nextPathAt=now+40;
        }
        getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return true;
    }
    private void releaseWork(ServerLevel level) {
        Settlement home=town(level);
        if(home!=null && getUUID().equals(home.trading.runner) && !tradeShipment.travelling()) {
            home.trading.runner=null; home.trading.runnerPos=null; SettlementData.get(level).setDirty();
        }
        leaveBed();
        animalWork.reset();
        enchantTable=null; enchantStand=null;
        researchDesk=null; researchStand=null;
        researchRejectedStands.clear(); nextResearchRouteAt=0;
        trapWork=null; trapWorkTicks=0; trapPathTicks=0;
        var book=SettlementService.reservations(level);
        if(workplace!=null) SettlementService.workers(level).release(workplace,getUUID());
        if(target!=null) book.release(target,getUUID());
        if(targetLease!=null) book.release(targetLease,getUUID());
        if(isUsingItem()) stopUsingItem();
        targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1; order=null; craftJob=null;
        haulStation=null; haulSupply=false; haulTicks=0;
        workStand=null; clearingLeaf=null;
        processor=null; processorStand=null; processingDelivery=false; processingSupplied=false; processingIdle=0; nextProcessingAt=0;
        workplace=null; target=null; patrolTarget=null; activePost=null; setTarget(null); workProgress=0; pathTicks=0; blindTicks=0; getNavigation().stop();
    }
    /**
     * The citizen's own station, when it can work there now. A citizen without one takes the open place of highest
     * priority; every half minute it also looks for an open place in a job of higher priority than its own. Otherwise
     * it stays: no work, night or a full crew mean waiting, not taking another station.
     */
    private Station chooseJob(ServerLevel level,Settlement town) {
        var book=SettlementService.workers(level);
        long now=level.getGameTime();
        idleStations.entrySet().removeIf(e -> e.getValue().until()<=now);
        JobBoard jobs=town.jobs;
        Station home=homeStation(town);
        if(home!=null && !keepsJob(level,town,home)) { jobs.release(getUUID()); home=null; SettlementData.get(level).setDirty(); }
        if(home==null || promotionDue(level,town)) {
            Station better=promotion(level,town,home);
            if(better!=null) { jobs.assign(getUUID(),better.position()); home=better; SettlementData.get(level).setDirty(); }
        }
        if(home==null) { jobNote="No open job: every crew is full or its job is switched off"; return null; }
        String name=home.role().title()+" Station";
        if(!SettlementService.active(level,home)) { jobNote="Waiting for my "+name+" to load"; return null; }
        if(night(level) && home.role()!=StructureRole.GUARD) { jobNote="Off duty until morning"; return null; }
        if(!traderOpen(level,town,home)) { jobNote="Waiting for a connected, unpaused trade route"; return null; }
        IdleStation idle=idleStations.get(home.position());
        if(idle!=null) { jobNote=idle.reason(); return null; }
        if(book.claim(home.position(),getUUID(),now,200,SettlementService.workerLimit(town,home))) return home;
        jobNote="My "+name+" crew is full for now"; return null;
    }
    /** Keep the actual problem visible in screens and diagnostics until this job is retried. */
    private void pauseStation(ServerLevel level,BlockPos station,int ticks) {
        idleStations.put(station,new IdleStation(level.getGameTime()+ticks,activity));
    }
    /** Part-way through an enchantment, a repair or a trade run, a citizen finishes before changing job. */
    private boolean midTask() { return !enchantItem.isEmpty() || !repairItem.isEmpty() || tradeShipment.travelling(); }
    /** Every half minute, or at once after the owner changes priorities, a citizen looks for a more important job. */
    private boolean promotionDue(ServerLevel level,Settlement town) {
        if(midTask() || level.getGameTime()<nextPromotionAt && town.jobs.revision()==seenJobRevision) return false;
        nextPromotionAt=level.getGameTime()+PROMOTION_CHECK; seenJobRevision=town.jobs.revision();
        return true;
    }
    /** The open place of highest priority above the citizen's own job (any open place for a citizen without one). */
    private Station promotion(ServerLevel level,Settlement town,Station home) {
        JobBoard jobs=town.jobs;
        int current=home==null ? JobBoard.OFF : jobs.level(home.role());
        boolean leaving=leftJob!=null && level.getGameTime()<leftJobUntil;
        return town.stations.stream().filter(s -> s.role().providesWork() && jobs.level(s.role())>current && !(leaving && s.position().equals(leftJob))
                && SettlementService.active(level,s) && jobs.assigned(s.position())<SettlementService.workerLimit(town,s) && traderOpen(level,town,s))
                .min(jobs.openOrder().thenComparingDouble(s -> distanceToSqr(Vec3.atCenterOf(s.position())))).orElse(null);
    }
    /** A job holds while its station stands (or lies beyond loaded chunks), its priority is on and the crew keeps this place. */
    private boolean keepsJob(ServerLevel level,Settlement town,Station station) {
        if(!station.role().providesWork() || town.jobs.level(station.role())==JobBoard.OFF) return false;
        if(level.hasChunkAt(station.position()) && !SettlementService.active(level,station)) return false;
        return town.jobs.holdsPlace(getUUID(),station,SettlementService.workerLimit(town,station));
    }
    private boolean keepsPlaceToTake(ServerLevel level,Settlement town,Station station) {
        return station.role().providesWork() && town.jobs.level(station.role())>JobBoard.OFF && SettlementService.active(level,station)
                && town.jobs.assigned(station.position())<SettlementService.workerLimit(town,station);
    }
    /** A trader's place is open only while a trip can leave and no other citizen is already the runner. */
    private boolean traderOpen(ServerLevel level,Settlement town,Station station) {
        return station.role()!=StructureRole.TRADER || TradeRoutes.canDepart(level,town)
                && (town.trading.runner==null || town.trading.runner.equals(getUUID()));
    }
    private boolean toolFits(StructureRole role,ItemStack stack) {
        if(role==StructureRole.HUNTER) return AnimalWork.weapon(stack) && !GuardEquipment.worn(stack);
        if(role==StructureRole.FISHERMAN) return stack.is(Items.FISHING_ROD) && !GuardEquipment.worn(stack);
        if(role==StructureRole.BUTCHER) return stack.is(ItemTags.AXES) && !GuardEquipment.worn(stack);
        if(role==StructureRole.GATHERER && target!=null) return stack.is(ItemTags.SHOVELS) && !GuardEquipment.worn(stack);
        if(target==null || action==Action.PLANT || action==Action.SUPPORT || role==StructureRole.FARM) return true;
        if((role==StructureRole.LUMBER || role.excavates()) && GuardEquipment.worn(stack)) return false;
        if(role==StructureRole.LUMBER) return stack.is(ItemTags.AXES) && ForestryService.durability(stack)>=minimumAxeDurability;
        if(!role.excavates() || !stack.is(ItemTags.PICKAXES)) return false;
        BlockState state=target==null ? Blocks.STONE.defaultBlockState() : level().getBlockState(target);
        return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
    }
    private boolean properTool(StructureRole role) { return toolFits(role,getMainHandItem()); }
    private boolean needsSupply() {
        ItemStack supply=getOffhandItem();
        if(action==Action.PLANT) return forestTask==null || !supply.is(forestTask.planting().species().seed) || supply.getCount()<forestTask.planting().cost();
        return action==Action.SUPPORT && (supply.isEmpty() || !ExcavationService.supportMaterial(supply));
    }
    private boolean deliverCargo() { return cargo.needsDelivery(); }
    private void upgradeWorkTool(List<Container> supplies,StructureRole role) {
        if(role!=StructureRole.FARM && role!=StructureRole.LUMBER && role!=StructureRole.GATHERER) return;
        int held=WorkerTools.bonus(role,getMainHandItem());
        ItemStack better=InventoryOps.takeBest(supplies,s -> JobStorage.tool(role,s) && !GuardEquipment.worn(s)
                && (getMainHandItem().isEmpty() || WorkerTools.bonus(role,s)>held) && toolFits(role,s),s -> WorkerTools.bonus(role,s));
        if(!better.isEmpty()) { cargo.offer(getMainHandItem()); setItemSlot(EquipmentSlot.MAINHAND,better); }
    }
    /**
     * Farther than one planned route reaches, a probe cannot confirm a destination. The walk goes leg by leg instead,
     * and trips that get nowhere give up through their own time limits and the stuck rescue.
     */
    private boolean beyondOneRoute(BlockPos pos) {
        double reach=CitizenNavigation.ROUTE_LENGTH*0.75;
        return distanceToSqr(Vec3.atCenterOf(pos))>reach*reach;
    }
    private boolean canReach(BlockPos pos) {
        if(near(pos) || beyondOneRoute(pos)) return true;
        long now=level().getGameTime();
        if(failedTargets.getOrDefault(pos,0L)>now) return false;
        return reachBudget.check(() -> {
            var path=getNavigation().createPath(pos,1);
            if(path!=null && path.canReach()) return true;
            // Retain failed probes long enough for a bounded search to advance past an obstructed group.
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(pos.immutable(),now+1200);
            return false;
        });
    }
    private CitizenReach.StandingView standingView(ServerLevel level,Settlement town) {
        return CitizenReach.ground(level,p -> town.contains(p) && p.getY()>=level.getMinY() && p.getY()<level.getMaxY() && level.hasChunkAt(p));
    }
    private boolean reachableStand(BlockPos pos) {
        if(pos.equals(blockPosition()) || beyondOneRoute(pos)) return true;
        if(failedTargets.containsKey(pos)) return false;
        return reachBudget.check(() -> {
            var path=getNavigation().createPath(pos,0);
            if(path!=null && path.canReach()) return true;
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(pos.immutable(),level().getGameTime()+1200);
            return false;
        });
    }
    /** Select ground from which the resource is in reach, instead of trying to enter a log or canopy. */
    private boolean workAccessible(ServerLevel level,Settlement town,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        BlockPos touch=state.isAir() || CitizenReach.softCover(level,pos,state) ? pos.below() : pos;
        var view=standingView(level,town);
        boolean lumber=TreeSpecies.ofLog(level.getBlockState(touch))!=null;
        if(handNear(touch) && workSight(level,town,getEyePosition(),touch)
                && (CitizenReach.standing(view,blockPosition()) || lumber)) { workStand=blockPosition(); return true; }
        for(BlockPos stand:CitizenReach.stands(view,touch,position(),getEyeHeight())) {
            if(reachBudget.deferred()) break;
            if(!reachableStand(stand)) continue;
            Vec3 eye=view.feet(stand).add(0,getEyeHeight(),0);
            if(workSight(level,town,eye,touch)) { workStand=stand; return true; }
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(stand,level.getGameTime()+1200);
        }
        return false;
    }
    private boolean workSight(ServerLevel level,Settlement town,Vec3 eye,BlockPos pos) {
        if(CitizenReach.canUse(level,eye,pos)) return true;
        TreeSpecies species=TreeSpecies.ofLog(level.getBlockState(pos));
        if(species==null) return false;
        var hit=CitizenReach.hit(level,eye,pos);
        BlockPos obstacle=hit.getBlockPos();
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK && CitizenReach.within(eye,obstacle)
                && level.hasChunkAt(obstacle) && town.contains(obstacle) && ForestryService.naturalLeaf(level.getBlockState(obstacle))
                && !WorldWorkData.get(level).protectedBlocks.contains(obstacle) && !ForestryService.protectedFixture(town,obstacle);
    }
    /** The first natural leaf in reach, including leaves intersecting a worker who was already stuck in a canopy. */
    private BlockPos blockingLeaf(ServerLevel level,Settlement town) {
        if(forestTask==null || forestTask.tree()==null) return null;
        AABB body=getBoundingBox().deflate(0.001);
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY,body.minZ),BlockPos.containing(body.maxX,body.maxY,body.maxZ)))
            if(CitizenReach.within(getEyePosition(),p) && ForestryService.clearableLeaf(level,town,forestTask.tree(),p)) return p.immutable();
        var hit=CitizenReach.hit(level,getEyePosition(),target);
        BlockPos p=hit.getBlockPos();
        return !p.equals(target) && hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                && handNear(p) && getEyePosition().distanceToSqr(hit.getLocation())<=CitizenReach.BLOCKS*CitizenReach.BLOCKS+1.0E-7
                && ForestryService.clearableLeaf(level,town,forestTask.tree(),p) ? p : null;
    }
    private boolean food(ItemStack stack) {
        return FoodHealing.food(stack);
    }
    private boolean wantsMeal() { return mealTicks<=0; }
    private void consumeMeal(ItemStack meal) {
        mealTicks=MealVariety.fullTicks(meal,Config.mealIntervalTicks()); healingTicks=FoodHealing.COOLDOWN; lastMealAt=level().getGameTime();
        recentMeals=MealVariety.remember(recentMeals,MealVariety.id(meal));
        playSound(SoundEvents.GENERIC_EAT.value(),0.5F,1.0F);
    }
    private boolean eatFrom(List<Container> supplies) {
        if(!wantsMeal()) return false;
        boolean personal=supplies.size()==1 && supplies.getFirst()==cargo;
        // A courier's bag is town cargo. Deliver its meals intact, then eat fairly from the communal stock.
        if(personal && level() instanceof ServerLevel server) {
            Settlement town=town(server);
            Station home=town==null ? null : homeStation(town);
            if(home!=null && home.role()==StructureRole.COURIER) return false;
        }
        if(!personal && level() instanceof ServerLevel server) {
            Settlement town=town(server);
            if(town!=null) {
                Map<UUID,Long> hungry=new HashMap<>();
                for(UUID id:town.citizens) if(server.getEntity(id) instanceof CitizenEntity citizen && citizen.isAlive()
                        && citizen.wantsMeal() && InventoryOps.count(List.of(citizen.cargo),FoodHealing::food)==0)
                    hungry.put(id,citizen.lastMealAt);
                if(!FoodSharing.mayTake(getUUID(),lastMealAt,hungry,InventoryOps.count(supplies,FoodHealing::food),town.citizens.size())) return false;
            }
        }
        ItemStack meal=FoodHealing.take(supplies,cargo::offer,recentMeals);
        if(meal.isEmpty()) return false;
        consumeMeal(meal); return true;
    }
    /** A pantry visit takes a meal, never another job's supplies or production cargo. */
    private boolean visitPantry(ServerLevel level,Settlement town) {
        BlockPos pantry=SettlementService.warehouse(level,town,blockPosition());
        if(pantry==null) return true;
        var stock=SettlementService.storageAt(level,town,pantry);
        if(InventoryOps.count(stock,this::food)==0) {
            pantryTarget=null; pantryStand=null; nextFoodTripAt=level.getGameTime()+200;
            return true;
        }
        // The solid warehouse is an interaction target. Walk to clear ground with a view of it,
        // rather than requiring a path onto the station or stopping behind a storage barrel.
        if(!canUse(level,pantry)) {
            if(!pantry.equals(pantryTarget) || !standingSpotUsable(level,town,pantryStand,pantry)) {
                pantryTarget=pantry; pantryStand=null;
                for(BlockPos stand:CitizenReach.stands(standingView(level,town),pantry,position(),getEyeHeight())) {
                    if(!standingSpotUsable(level,town,stand,pantry)) continue;
                    if(reachableStand(stand)) { pantryStand=stand; break; }
                    if(reachBudget.deferred()) { activity="Looking for accessible ground beside the pantry"; return false; }
                }
            }
            if(pantryStand!=null && walk(pantryStand,0.65,0)) {
                activity="Walking to the communal pantry for a meal";
                return false;
            }
            pantryTarget=null; pantryStand=null; nextFoodTripAt=level.getGameTime()+200;
            return true;
        }
        getNavigation().stop();
        eatFrom(stock);
        pantryTarget=null; pantryStand=null;
        nextFoodTripAt=level.getGameTime()+200;
        return true;
    }
    /** Citizens keep only what their current job uses; everything else, including finished craft goods, goes to the warehouse. */
    private boolean retainSupply(ItemStack stack) {
        StructureRole role=role();
        if(role==StructureRole.BLACKSMITH && (!repairItem.isEmpty() && BlacksmithRepair.material(repairItem,stack)
                || forgeJob!=null && forgeJob.uses(stack))) return true;
        if(role==StructureRole.CRAFTSMAN && trapWork!=null && level() instanceof ServerLevel server) {
            var material=TrapService.material(server.getBlockState(trapWork));
            if(material!=null && material.accepts().test(stack)) return true;
        }
        if(gear(stack) && GuardEquipment.worn(stack)) return false;
        if(level() instanceof ServerLevel level && role!=null && role.processes()) {
            Settlement town=town(level);
            if(town!=null && ProcessingService.supply(level,town,homeStation(town),stack)) return true;
        }
        if(role!=null && role.animalJob() && AnimalWork.supply(role,stack)) return true;
        boolean guard=role==StructureRole.GUARD;
        return stack.is(ItemTags.HOES) && role==StructureRole.FARM || stack.is(ItemTags.SHOVELS) && role==StructureRole.GATHERER || stack.is(ItemTags.AXES) && role==StructureRole.LUMBER
                || stack.is(ItemTags.PICKAXES) && role!=null && role.excavates()
                || guard && (GuardWeapons.melee(stack) && GuardWeapons.score(stack)>=bestMelee() || GuardWeapons.bow(stack) || GuardWeapons.arrow(stack) || stack.is(Items.SHIELD)
                    || armor(stack) && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> GuardEquipment.upgrade(stack,getItemBySlot(slot),slot)))
                || order!=null && order.uses(stack)
                || craftJob!=null && role==StructureRole.CRAFTSMAN && craftJob.plan().uses(stack)
                || role==StructureRole.COURIER && haulSupply && haulingInput(stack)
                || role==StructureRole.ENCHANTER && Enchanting.lapis(stack)
                || action==Action.PLANT && forestTask!=null && stack.is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(stack);
    }
    private static boolean armor(ItemStack stack) { return Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> GuardEquipment.armor(stack,slot)); }
    /** Supplies a courier is carrying to a smeltery or kitchen barrel stay in the bag until they arrive. */
    private boolean haulingInput(ItemStack stack) {
        if(!(level() instanceof ServerLevel server) || haulStation==null) return false;
        Settlement town=town(server); Station job=town==null ? null : town.station(haulStation);
        return job!=null && JobStorage.input(JobStorage.Supplies.of(server),town,job.role(),stack);
    }
    /** Tools, weapons and armor that belong to particular jobs. */
    private static boolean gear(ItemStack stack) {
        return stack.is(ItemTags.HOES) || stack.is(ItemTags.SHOVELS) || stack.is(Items.FISHING_ROD) || GuardWeapons.weapon(stack) || GuardWeapons.arrow(stack) || stack.is(Items.SHIELD) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || armor(stack);
    }
    /** A new job: put away the previous one's equipment and return it before starting. */
    private void changeRole(StructureRole role) {
        forgeJob=null; forgeDelivery=false; smithStand=null;
        if(role!=StructureRole.BLACKSMITH && !repairItem.isEmpty()) { cargo.offer(repairItem); repairItem=ItemStack.EMPTY; repairStand=null; repairSlot=null; repairAnvil=null; }
        if(role!=StructureRole.ENCHANTER && !enchantItem.isEmpty()) { cargo.offer(enchantItem); enchantItem=ItemStack.EMPTY; enchantTicks=0; enchantLevel=0; enchantDone=false; }
        if(role!=StructureRole.BLACKSMITH) { repairStand=null; repairSlot=null; repairAnvil=null; repairDelivery=false; }
        guardWasActive=false;
        if(isUsingItem()) stopUsingItem();
        if(role!=StructureRole.GUARD) for(EquipmentSlot slot:GuardEquipment.ARMOR) if(!getItemBySlot(slot).isEmpty()) {
            cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
        }
        for(EquipmentSlot hand:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) if(!getItemBySlot(hand).isEmpty()) {
            cargo.offer(getItemBySlot(hand)); setItemSlot(hand,ItemStack.EMPTY);
        }
        returningGear=hasReturnableGear(); pathTicks=0;
    }
    private boolean hasReturnableGear() {
        for(int slot=0;slot<cargo.getContainerSize();slot++) {
            ItemStack stack=cargo.getItem(slot);
            if(!stack.isEmpty() && gear(stack) && !retainSupply(stack)) return true;
        }
        return false;
    }
    /** Carry gear the new job does not use to the warehouse so the next worker in the old job finds it; drop it if no warehouse can be reached. */
    private boolean returnGear(ServerLevel level,Settlement town) {
        if(!hasReturnableGear()) { returningGear=false; return true; }
        BlockPos warehouse=jobDepot(level,town);
        if(warehouse==null && town.station(workplace)!=null) {
            // The warehouse is only unloaded: keep the gear in the bag; ordinary deliveries hand it in later.
            returningGear=false; return true;
        }
        if(warehouse!=null && !canUse(level,warehouse)) {
            pathTicks+=10;
            // A path cannot be planned mid-jump; only a long failure or one on solid ground means the warehouse is out of reach.
            if((walk(warehouse) || !onGround()) && pathTicks<=1200) { activity="Returning my previous job's gear to the job barrel"; return false; }
            warehouse=null;
        }
        getNavigation().stop(); pathTicks=0;
        List<Container> storage=warehouse==null ? List.of() : SettlementService.jobStorage(level,town,town.station(workplace));
        for(int slot=0;slot<cargo.getContainerSize();slot++) {
            ItemStack stack=cargo.getItem(slot);
            if(stack.isEmpty() || !gear(stack) || retainSupply(stack)) continue;
            ItemStack rest=stack.copy();
            for(Container container:storage) rest=InventoryOps.insert(container,rest);
            if(!rest.isEmpty() && GuardEquipment.worn(stack)) { cargo.setItem(slot,rest); continue; }
            if(!rest.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),rest);
            cargo.setItem(slot,ItemStack.EMPTY);
        }
        returningGear=false; return true;
    }
    private void useLocalSupplies(StructureRole role) {
        eatFrom(List.of(cargo));
        upgradeWorkTool(List.of(cargo),role);
        if(!properTool(role)) {
            ItemStack tool=InventoryOps.takeOne(List.of(cargo),s -> toolFits(role,s));
            if(!tool.isEmpty()) { cargo.offer(getMainHandItem()); setItemSlot(EquipmentSlot.MAINHAND,tool); }
        }
        if(needsSupply()) {
            ItemStack supply=getOffhandItem();
            java.util.function.Predicate<ItemStack> matches=stack -> action==Action.PLANT ? stack.is(forestTask.planting().species().seed) : ExcavationService.supportMaterial(stack);
            if(!supply.isEmpty() && !matches.test(supply)) { cargo.offer(supply); supply=ItemStack.EMPTY; }
            int cost=action==Action.PLANT ? forestTask.planting().cost() : 1;
            while(supply.getCount()<cost) {
                ItemStack held=supply;
                ItemStack next=InventoryOps.takeOne(List.of(cargo),s -> matches.test(s) && (held.isEmpty() || ItemStack.isSameItemSameComponents(held,s)));
                if(next.isEmpty()) break;
                if(supply.isEmpty()) supply=next; else supply.grow(1);
            }
            setItemSlot(EquipmentSlot.OFFHAND,supply);
        }
    }
    private boolean visitWarehouse(ServerLevel level,Settlement town,StructureRole role) {
        if(role!=null && role.keepsJobStorage()) {
            Station station=town.station(workplace);
            if(station==null) { activity="Needs an assigned station with a job barrel"; return false; }
            return visitDepot(level,town,station);
        }
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse==null) { activity="Needs a loaded warehouse with a chest or barrel in range"; return false; }
        return visitStorage(level,town,role,warehouse,SettlementService.storageAt(level,town,warehouse),true);
    }
    private boolean supplyFor(ItemStack stack) {
        return action==Action.PLANT ? forestTask!=null && stack.is(forestTask.planting().species().seed) : ExcavationService.supportMaterial(stack);
    }
    private BlockPos nearest(List<BlockPos> positions) {
        return positions.stream().min(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p)))).orElse(null);
    }
    /** The closest job barrel this citizen has not recently failed to reach, or null. */
    private BlockPos nearestBarrel(List<BlockPos> barrels) {
        return nearest(barrels.stream().filter(barrel -> !failedTargets.containsKey(barrel)).toList());
    }
    /** Goods this citizen should hand in: a full enough bag, or for cooks anything they baked or cooked. */
    private boolean carryingGoods(StructureRole role) {
        return cargo.hasDeliverable(this::retainSupply,this::food) || role==StructureRole.COOK && !cargo.first(stack -> !retainSupply(stack)).isEmpty();
    }
    /** Finished goods may stay in job barrels while couriers collect them, or when there is no warehouse to take them to. */
    private boolean dropOff(ServerLevel level,Settlement town,List<Container> barrels) {
        return !barrels.isEmpty() && JobStorage.freeSlots(barrels)>0;
    }
    private BlockPos jobDepot(ServerLevel level,Settlement town) {
        Station job=town.station(workplace);
        return job==null ? null : nearestBarrel(SettlementService.jobBarrels(level,town,job));
    }
    /** Shared barrel selection and diagnostics for every production job, including crafting, processing and enchanting. */
    private BlockPos jobBarrel(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> barrels=SettlementService.jobBarrels(level,town,station);
        if(barrels.isEmpty()) { activity="Needs a job barrel within "+station.radius()+" blocks of the station, outside warehouse range"; return null; }
        BlockPos barrel=nearestBarrel(barrels);
        if(barrel==null) {
            // A citizen brought beside a barrel can use it immediately, even after a recent failed route.
            barrel=nearest(barrels.stream().filter(pos -> canUse(level,pos)).toList());
            if(barrel!=null) failedTargets.remove(barrel);
        }
        if(barrel==null) {
            activity="Cannot reach "+barrels.size()+" job barrel"+(barrels.size()==1 ? "" : "s")+"; clear a path and standing room beside "+(barrels.size()==1 ? "it" : "them");
            failedJobPath(level);
        }
        return barrel;
    }
    /** A supply or delivery trip to the job's own barrels; couriers deliver supplies and collect finished goods. */
    private boolean visitDepot(ServerLevel level,Settlement town,Station station) {
        BlockPos barrel=jobBarrel(level,town,station);
        return barrel!=null && visitStorage(level,town,station.role(),barrel,SettlementService.jobStorage(level,town,station),true);
    }
    /** Walk to the storage, hand in goods (if {@code deposit}), and collect food, tools and supplies this job needs. */
    private boolean visitStorage(ServerLevel level,Settlement town,StructureRole role,BlockPos depot,List<Container> storage,boolean deposit) {
        Station place=town.station(depot);
        boolean warehouse=place!=null && place.role()==StructureRole.WAREHOUSE;
        if(!canUse(level,depot)) {
            if(!depot.equals(depotTarget)) { depotTarget=depot.immutable(); depotStand=null; depotTicks=0; }
            if(!standingSpotUsable(level,town,depotStand,depot)) {
                depotStand=null;
                for(BlockPos stand:CitizenReach.stands(standingView(level,town),depot,position(),getEyeHeight())) {
                    if(!standingSpotUsable(level,town,stand,depot)) continue;
                    if(reachableStand(stand)) { depotStand=stand; break; }
                    if(reachBudget.deferred()) { activity="Looking for clear ground beside the job's barrel"; return false; }
                }
            }
            depotTicks+=10;
            boolean moving=depotStand!=null && walk(depotStand,0.65,0);
            activity=warehouse ? "Carrying supplies / returning for food or tools" : "Walking to the job's barrel";
            // Skip a blocked barrel for a minute and try another of this station's barrels.
            if(!warehouse && (depotTicks>BARREL_WALK_TICKS || !moving && onGround())) {
                failedJobPath(level);
                if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(depotTarget,level.getGameTime()+1200);
                depotTarget=null; depotStand=null; depotTicks=0; activity="Cannot reach the job's barrel";
            }
            return false;
        }
        depotTarget=null; depotStand=null; depotTicks=0;
        getNavigation().stop();
        int[] foodReserve={role.foodJob() || role==StructureRole.COURIER ? 0 : FoodSharing.PERSONAL_LIMIT};
        int[] fuelReserve={ProcessingService.FUEL_LOAD};
        if(deposit) cargo.deposit(storage,stack -> {
            if(role.processes() && ProcessingService.fuel(level,stack) && !ProcessingService.ingredient(role,stack)) {
                int keep=Math.min(fuelReserve[0],stack.getCount()); fuelReserve[0]-=keep; return keep;
            }
            if(retainSupply(stack)) return stack.getCount();
            if(food(stack)) { int keep=Math.min(foodReserve[0],stack.getCount()); foodReserve[0]-=keep; return keep; }
            return 0;
        });
        boolean keepSupply=action==Action.PLANT && forestTask!=null && getOffhandItem().is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(getOffhandItem());
        if(!keepSupply && !getOffhandItem().isEmpty()) {
            ItemStack leftover=getOffhandItem();
            // A job barrel may be full; leftover supplies then ride along to the warehouse instead.
            if(warehouse) for(Container container:storage) leftover=InventoryOps.insert(container,leftover);
            else { cargo.offer(leftover); leftover=ItemStack.EMPTY; }
            setItemSlot(EquipmentSlot.OFFHAND,leftover);
            if(!leftover.isEmpty()) { activity="Needs storage space for planting/building supplies"; return false; }
        }
        if(deposit && cargo.needsDelivery()) {
            activity=warehouse ? "Warehouse is full; keeping supplies in my inventory" : "The job's barrels are full";
            return false;
        }
        if(wantsMeal() && !eatFrom(storage) && warehouse && mealTicks<=0 && !role.foodJob()
                && role!=StructureRole.GUARD && role!=StructureRole.CRAFTSMAN && role!=StructureRole.BLACKSMITH && role!=StructureRole.COURIER) {
            activity="Waiting my turn for food in the warehouse"; return false;
        }
        int rations=InventoryOps.count(List.of(cargo),this::food);
        int allowance=role.foodJob() || role==StructureRole.COURIER ? 0 : FoodSharing.spareLimit(InventoryOps.count(storage,this::food),town.citizens.size());
        for(int count=rations;count<allowance;count++) {
            ItemStack ration=InventoryOps.takeOne(storage,this::food);
            if(ration.isEmpty()) break;
            cargo.offer(ration);
        }
        if(role==StructureRole.GUARD && guardWasActive) stockArmory(storage);
        upgradeWorkTool(storage,role);
        if(!properTool(role)) {
            ItemStack held=getMainHandItem();
            // At a job barrel the replaced tool rides along in the bag and reaches the warehouse with the next delivery.
            if(warehouse) for(Container container:storage) held=InventoryOps.insert(container,held);
            else { cargo.offer(held); held=ItemStack.EMPTY; }
            setItemSlot(EquipmentSlot.MAINHAND,held);
            if(!held.isEmpty()) { activity="Needs storage space to change tools"; return false; }
            ItemStack tool=InventoryOps.takeOne(storage,s -> toolFits(role,s));
            setItemSlot(EquipmentSlot.MAINHAND,tool);
            if(tool.isEmpty()) { activity="Needs an "+(role==StructureRole.LUMBER ? "axe with at least "+minimumAxeDurability+" durability" : role==StructureRole.HUNTER ? "sword or axe" : role==StructureRole.FISHERMAN ? "fishing rod" : role==StructureRole.BUTCHER ? "axe for butchering" : role==StructureRole.GATHERER ? "shovel" : "appropriate pickaxe")+" in the job barrel"; return false; }
        }
        if(needsSupply()) {
            ItemStack supply=getOffhandItem();
            int needed=action==Action.PLANT ? forestTask.planting().cost() : 1;
            for(int i=supply.getCount();i<needed;i++) {
                ItemStack next=InventoryOps.takeOne(storage,stack -> action==Action.PLANT ? stack.is(forestTask.planting().species().seed) : ExcavationService.supportMaterial(stack));
                if(next.isEmpty()) break;
                if(supply.isEmpty()) supply=next; else if(ItemStack.isSameItemSameComponents(supply,next)) supply.grow(1);
                else { // Keep different building materials in cargo until the next delivery.
                    cargo.offer(next);
                }
            }
            setItemSlot(EquipmentSlot.OFFHAND,supply);
            if(needsSupply()) { activity=action==Action.PLANT ? "Needs "+needed+" "+forestTask.planting().species().name().toLowerCase(Locale.ROOT)+" saplings in storage" : "Needs cobblestone, stone, or dirt to support the tunnel floor"; return false; }
        }
        return true;
    }
    private boolean harvestable(ServerLevel level,Settlement town,StructureRole role,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !town.contains(pos) || SettlementService.protectedFurniture(town,pos)
                || level.getBlockEntity(pos)!=null) return false;
        BlockState state=level.getBlockState(pos);
        if(role==StructureRole.GATHERER) return Gathering.harvestable(level,town,pos);
        return role==StructureRole.FARM && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && seed(state)!=null;
    }
    private Item seed(BlockState state) {
        if(state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if(state.is(Blocks.CARROTS)) return Items.CARROT;
        if(state.is(Blocks.POTATOES)) return Items.POTATO;
        if(state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return null;
    }
    private BlockPos findTarget(ServerLevel level,Settlement town,Station station) {
        var book=SettlementService.reservations(level);
        if(station.role()==StructureRole.LUMBER) {
            var search=ForestryService.search(level,town,station,p -> !failedTargets.containsKey(p)
                    && !reachBudget.deferred() && book.available(p,getUUID(),level.getGameTime()) && workAccessible(level,town,p),
                    item -> cargo.count(item)+(getOffhandItem().is(item) ? getOffhandItem().getCount() : 0));
            forestTask=search.task(); forestIdleReason=search.reason();
            if(forestTask==null || !book.claim(forestTask.target(),getUUID(),level.getGameTime(),200)) return null;
            action=forestTask.planting()==null ? Action.FELL : Action.PLANT;
            minimumAxeDurability=forestTask.tree()==null ? 1 : forestTask.tree().logs().size();
            return forestTask.target();
        }
        if(station.role()==StructureRole.MINE) {
            // A mine near an exposed ore works only that vein, one miner at a time.
            BlockPos vein=OreVeins.find(level,town,station);
            if(vein!=null) {
                if(!book.available(vein,getUUID(),level.getGameTime()) || failedTargets.containsKey(vein)
                        || !workAccessible(level,town,vein) || !book.claim(vein,getUUID(),level.getGameTime(),200)) return null;
                action=Action.VEIN; return vein;
            }
        }
        if(station.role().excavates()) {
            ExcavationJob job=ExcavationService.job(level,town,station);
            if(station.role()==StructureRole.MINE && job!=null && getY()<=job.targetY+6) {
                BlockPos ore=CaveMining.find(level,town,blockPosition(),p -> !failedTargets.containsKey(p)
                        && !reachBudget.deferred() && book.available(p,getUUID(),level.getGameTime()) && workAccessible(level,town,p));
                if(ore!=null && book.claim(ore,getUUID(),level.getGameTime(),200)) { action=Action.CAVE; return ore; }
                if(reachBudget.deferred()) return null;
            }
            excavation=ExcavationService.next(level,town,station,getUUID(),blockPosition(),failedTargets::containsKey);
            if(excavation==null) return null;
            action=excavation.support() ? Action.SUPPORT : Action.EXCAVATE;
            targetLease=excavation.lease(); return excavation.target();
        }
        action=station.role()==StructureRole.GATHERER ? Action.GATHER : Action.HARVEST;
        List<BlockPos> candidates=new ArrayList<>();
        for(BlockPos pos:SettlementService.cells(station)) if(!failedTargets.containsKey(pos)
                && SettlementService.ownsBlock(level,town,station,pos) && harvestable(level,town,station.role(),pos)) candidates.add(pos.immutable());
        candidates.sort(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p))));
        for(BlockPos pos:candidates) if(workAccessible(level,town,pos) && book.claim(pos,getUUID(),level.getGameTime(),200)) return pos;
        return null;
    }
    private String idleReason(ServerLevel level,Settlement town,Station station) {
        if(station.role()==StructureRole.MINE) {
            BlockPos vein=OreVeins.find(level,town,station);
            if(vein!=null) return SettlementService.reservations(level).available(vein,getUUID(),level.getGameTime())
                    ? "Cannot reach the "+OreVeins.name(level.getBlockState(vein))+" vein: it needs open standing room beside it"
                    : "Another miner is already working this vein";
        }
        return station.role().excavates() ? ExcavationService.status(level,town,station)
                : station.role()==StructureRole.LUMBER ? forestIdleReason : station.role()==StructureRole.GATHERER
                    ? "No accessible cane, bamboo or unprotected dry surface sand, gravel or clay" : "No mature accessible crops";
    }
    private void cancelTarget(ServerLevel level,boolean failed) {
        if(target!=null) {
            SettlementService.reservations(level).release(target,getUUID());
            if(targetLease!=null) SettlementService.reservations(level).release(targetLease,getUUID());
            if(failed && failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(target,level.getGameTime()+1200);
        }
        target=null; targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1;
        workStand=null; clearingLeaf=null;
        workProgress=0; pathTicks=0; blindTicks=0; searchDelay=10; getNavigation().stop();
    }
    private void storeDrops(ServerLevel level,List<ItemStack> drops) {
        for(ItemStack stack:drops) cargo.offer(stack);
    }
    private boolean validTarget(ServerLevel level,Settlement town,Station station) {
        return switch(action) {
            case HARVEST,GATHER -> SettlementService.ownsBlock(level,town,station,target) && harvestable(level,town,station.role(),target);
            // The complete construction/provenance check runs again inside fell before any block changes.
            // While walking or swinging, checking the root avoids retraversing an entire tree twice a second.
            case FELL -> forestTask!=null && forestTask.tree()!=null && level.hasChunkAt(target)
                    && level.getBlockState(target).is(forestTask.tree().species().log)
                    && SettlementService.ownsBlock(level,town,station,target);
            case PLANT -> forestTask!=null && ForestryService.canPlant(level,town,station,forestTask.planting())
                    && SettlementService.ownsBlock(level,town,station,target);
            case EXCAVATE,SUPPORT -> excavation!=null && ExcavationService.valid(level,town,station,excavation);
            case CAVE -> CaveMining.ore(level.getBlockState(target)) && ExcavationService.safeBlock(level,town,target);
            case VEIN -> target.equals(OreVeins.find(level,town,station));
        };
    }
    private void harvest(ServerLevel level,Settlement town,Station station) {
        if(!validTarget(level,town,station) || !properTool(station.role()) || needsSupply()) { cancelTarget(level,false); return; }
        swing(InteractionHand.MAIN_HAND);
        if(action==Action.PLANT) {
            boolean planted=ForestryService.plant(level,town,station,forestTask.planting(),getOffhandItem());
            if(planted) gainExperience(station.role(),1);
            cancelTarget(level,!planted); return;
        }
        if(action==Action.SUPPORT) {
            boolean placed=ExcavationService.placeSupport(level,town,station,excavation,getOffhandItem());
            cancelTarget(level,!placed); return;
        }
        if(action==Action.FELL) {
            List<ItemStack> drops=ForestryService.fell(level,town,station,target,this);
            if(drops!=null) { storeDrops(level,drops); gainExperience(station.role(),2); }
            cancelTarget(level,drops==null); return;
        }
        if(action==Action.VEIN) {
            // The ore yields its normal drops, then stays in place for the next yield.
            BlockState ore=level.getBlockState(target);
            List<ItemStack> drops=Block.getDrops(ore,level,target,null,this,getMainHandItem());
            OreVeins.worked(level,target,ore,getMainHandItem(),Research.has(town,"deep_mining"));
            wearTool();
            level.levelEvent(2001,target,Block.getId(ore));
            storeDrops(level,ProductionYield.apply(station,ore,drops,getRandom())); workProgress=0; gainExperience(station.role(),1); return;
        }
        if(action==Action.GATHER && Gathering.plant(level.getBlockState(target))) {
            var cut=Gathering.cut(level,town,target,this);
            if(cut!=null) { storeDrops(level,cut.drops()); for(int i=0;i<cut.blocks();i++) wearTool(); gainExperience(station.role(),1); }
            cancelTarget(level,cut==null); return;
        }
        BlockState state=level.getBlockState(target);
        List<ItemStack> drops=new ArrayList<>(Block.getDrops(state,level,target,null,this,getMainHandItem()));
        if(action==Action.HARVEST) {
            Item seed=seed(state);
            ItemStack reserved=drops.stream().filter(s -> s.is(seed) && !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            if(reserved.isEmpty()) { cancelTarget(level,true); return; }
            reserved.shrink(1);
            if(!level.setBlock(target,((CropBlock)state.getBlock()).getStateForAge(0),3)) { cancelTarget(level,true); return; }
            if(getMainHandItem().is(ItemTags.HOES)) wearTool();
        } else {
            if(!level.destroyBlock(target,false,this)) { cancelTarget(level,true); return; }
            wearTool();
            if(action==Action.EXCAVATE) ExcavationService.completed(level,station,excavation);
        }
        level.levelEvent(2001,target,Block.getId(state));
        storeDrops(level,ProductionYield.apply(station,state,drops,getRandom())); cancelTarget(level,false);
        gainExperience(station.role(),1);
    }
    private static GuardEquipment.Equipment equipment(LivingEntity entity) {
        return new GuardEquipment.Equipment() {
            public ItemStack get(EquipmentSlot slot) { return entity.getItemBySlot(slot); }
            public void set(EquipmentSlot slot,ItemStack stack) { entity.setItemSlot(slot,stack); }
        };
    }
    private void wearLocalArmor() {
        for(EquipmentSlot slot:GuardEquipment.ARMOR) {
            ItemStack next=InventoryOps.takeBest(List.of(cargo),s -> GuardEquipment.upgrade(s,getItemBySlot(slot),slot),GuardEquipment::protection);
            if(!next.isEmpty()) { cargo.offer(getItemBySlot(slot)); setItemSlot(slot,next); }
        }
        if(GuardEquipment.worn(getOffhandItem())) { if(isUsingItem()) stopUsingItem(); cargo.offer(getOffhandItem()); setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY); }
        if(!getOffhandItem().is(Items.SHIELD)) {
            ItemStack shield=InventoryOps.takeOne(List.of(cargo),s -> s.is(Items.SHIELD) && GuardEquipment.usable(s));
            if(!shield.isEmpty()) { cargo.offer(getOffhandItem()); setItemSlot(EquipmentSlot.OFFHAND,shield); }
        }
    }
    private int arrows() { return InventoryOps.count(List.of(cargo),GuardWeapons::arrow); }
    private boolean carries(Predicate<ItemStack> kind) { return GuardEquipment.usable(getMainHandItem()) && kind.test(getMainHandItem())
            || InventoryOps.count(List.of(cargo),s -> GuardEquipment.usable(s) && kind.test(s))>0; }
    /** Best melee weapon score carried in hand or bag; zero without one. */
    private double bestMelee() {
        double best=GuardWeapons.melee(getMainHandItem()) && GuardEquipment.usable(getMainHandItem()) ? GuardWeapons.score(getMainHandItem()) : 0;
        for(int slot=0;slot<cargo.getContainerSize();slot++) if(GuardWeapons.melee(cargo.getItem(slot)) && GuardEquipment.usable(cargo.getItem(slot))) best=Math.max(best,GuardWeapons.score(cargo.getItem(slot)));
        return best;
    }
    /** Gear a guard is still looking for: armor for empty slots, a better melee weapon, one bow, and arrows for that bow. */
    private boolean needs(ItemStack stack) {
        if(!GuardEquipment.usable(stack)) return false;
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(GuardEquipment.armor(stack,slot))
            return GuardEquipment.upgrade(stack,getItemBySlot(slot),slot)
                    && InventoryOps.count(List.of(cargo),s -> GuardEquipment.armor(s,slot) && GuardEquipment.usable(s)
                        && GuardEquipment.protection(s)>=GuardEquipment.protection(stack))==0;
        if(GuardWeapons.melee(stack)) return GuardWeapons.score(stack)>bestMelee()+MELEE_UPGRADE;
        if(GuardWeapons.bow(stack)) return !carries(GuardWeapons::bow);
        if(stack.is(Items.SHIELD)) return (!getOffhandItem().is(Items.SHIELD) || !GuardEquipment.usable(getOffhandItem()))
                && cargo.first(s -> s.is(Items.SHIELD) && GuardEquipment.usable(s)).isEmpty();
        return GuardWeapons.arrow(stack) && carries(GuardWeapons::bow) && arrows()<ARROW_STOCK;
    }
    private int wanted(ItemStack stack) { return GuardWeapons.arrow(stack) ? Math.min(stack.getCount(),ARROW_STOCK-arrows()) : 1; }
    private void wield(ItemStack next) {
        ItemStack previous=getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND,next);
        if(!previous.isEmpty()) cargo.offer(previous);
    }
    private boolean hold(Predicate<ItemStack> kind) {
        if(kind.test(getMainHandItem()) && GuardEquipment.usable(getMainHandItem())) return true;
        ItemStack next=InventoryOps.takeBest(List.of(cargo),s -> kind.test(s) && GuardEquipment.usable(s),GuardWeapons::score);
        if(next.isEmpty()) return false;
        wield(next); return true;
    }
    /** Off the firing line a guard carries their strongest melee weapon, or the bow if that is all they have. */
    private void readyMelee() {
        // The work scheduler runs every ten ticks; drawing a bow takes twenty. Never replace a drawing bow.
        if(isUsingItem() && getUsedItemHand()==InteractionHand.MAIN_HAND && GuardWeapons.bow(getMainHandItem())) return;
        double held=GuardWeapons.score(getMainHandItem());
        ItemStack better=InventoryOps.takeBest(List.of(cargo),s -> GuardEquipment.usable(s) && GuardWeapons.score(s)>held,GuardWeapons::score);
        if(!better.isEmpty()) wield(better);
        else if(!GuardWeapons.weapon(getMainHandItem())) hold(GuardWeapons::bow);
    }
    private void stockArmory(List<Container> storage) {
        for(EquipmentSlot slot:GuardEquipment.ARMOR) {
            ItemStack next=InventoryOps.takeBest(storage,s -> GuardEquipment.upgrade(s,getItemBySlot(slot),slot),GuardEquipment::protection);
            if(!next.isEmpty()) { cargo.offer(getItemBySlot(slot)); setItemSlot(slot,next); }
        }
        double best=bestMelee()+MELEE_UPGRADE;
        ItemStack weapon=InventoryOps.takeBest(storage,s -> GuardEquipment.usable(s) && GuardWeapons.melee(s) && GuardWeapons.score(s)>best,GuardWeapons::score);
        if(!weapon.isEmpty()) cargo.offer(weapon);
        if(!carries(GuardWeapons::bow)) {
            ItemStack bow=InventoryOps.takeOne(storage,s -> GuardWeapons.bow(s) && GuardEquipment.usable(s));
            if(!bow.isEmpty()) cargo.offer(bow);
        }
        if(needs(new ItemStack(Items.SHIELD))) {
            ItemStack shield=InventoryOps.takeOne(storage,s -> s.is(Items.SHIELD) && GuardEquipment.usable(s));
            if(!shield.isEmpty()) cargo.offer(shield);
        }
        wearLocalArmor();
        while(carries(GuardWeapons::bow) && arrows()<ARROW_STOCK) {
            ItemStack arrow=InventoryOps.takeOne(storage,GuardWeapons::arrow);
            if(arrow.isEmpty()) break;
            cargo.offer(arrow);
        }
        readyMelee();
        // A replaced weapon goes straight back into storage for someone else.
        cargo.deposit(storage,s -> gear(s) && !retainSupply(s) ? 0 : s.getCount());
    }
    private boolean stocks(Container container) {
        for(int slot=0;slot<container.getContainerSize();slot++) if(needs(container.getItem(slot))) return true;
        return false;
    }
    private boolean equipFromStand(ServerLevel level,Settlement town,Station station) {
        if(gearTicks>0) { gearTicks-=10; return false; }
        gearTicks=40;
        var stands=new ArrayList<>(GuardService.stands(level,town,station));
        stands.sort(Comparator.comparingDouble(this::distanceToSqr));
        for(ArmorStand stand:stands) {
            boolean missing=Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> needs(stand.getItemBySlot(slot)))
                    || needs(stand.getItemBySlot(EquipmentSlot.MAINHAND)) || needs(stand.getItemBySlot(EquipmentSlot.OFFHAND));
            if(!missing) continue;
            if(!standNear(stand)) { if(approachStand(level,stand,"Collecting better gear from a stand")) { gearTicks=0; return true; } continue; }
            gearStand=null; gearPathTicks=0;
            for(EquipmentSlot slot:GuardEquipment.ARMOR) GuardEquipment.upgrade(equipment(stand),equipment(this),slot);
            // Stands double as weapon racks: take a needed weapon, or arrows, from either hand.
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
                ItemStack held=stand.getItemBySlot(slot);
                if(!needs(held)) continue;
                cargo.offer(held.split(wanted(held)));
                stand.setItemSlot(slot,held.isEmpty() ? ItemStack.EMPTY : held);
            }
            wearLocalArmor(); readyMelee();
            swing(InteractionHand.MAIN_HAND); return false;
        }
        return false;
    }
    private boolean standNear(ArmorStand stand) { return CitizenReach.within(getEyePosition(),stand.getBoundingBox()) && hasLineOfSight(stand); }
    private boolean approachStand(ServerLevel level,ArmorStand stand,String status) {
        if(ignoredStands.getOrDefault(stand.getUUID(),0L)>level.getGameTime()) return false;
        if(!stand.getUUID().equals(gearStand)) { gearStand=stand.getUUID(); gearPathTicks=0; }
        gearPathTicks+=10;
        if(gearPathTicks>400 || !canReach(stand.blockPosition()) || !walk(stand.blockPosition()) && onGround()) {
            ignoredStands.put(stand.getUUID(),level.getGameTime()+200); gearStand=null; gearPathTicks=0; return false;
        }
        activity=status; return true;
    }
    private ItemStack returnableArmor(EquipmentSlot slot,boolean all) {
        ItemStack worn=getItemBySlot(slot);
        if(!worn.isEmpty() && (all || GuardEquipment.worn(worn))) return worn;
        return cargo.first(stack -> GuardEquipment.armor(stack,slot) && (all || !GuardEquipment.upgrade(stack,worn,slot)));
    }
    private boolean hasArmorToReturn(boolean all) { return Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> !returnableArmor(slot,all).isEmpty()); }
    private GuardEquipment.Equipment returningArmor(boolean all) {
        Map<EquipmentSlot,ItemStack> sources=new EnumMap<>(EquipmentSlot.class);
        Set<EquipmentSlot> worn=new HashSet<>();
        for(EquipmentSlot slot:GuardEquipment.ARMOR) {
            sources.put(slot,returnableArmor(slot,all));
            if(!getItemBySlot(slot).isEmpty() && (all || GuardEquipment.worn(getItemBySlot(slot)))) worn.add(slot);
        }
        return new GuardEquipment.Equipment() {
            public ItemStack get(EquipmentSlot slot) { return sources.getOrDefault(slot,ItemStack.EMPTY); }
            public void set(EquipmentSlot slot,ItemStack stack) {
                if(worn.contains(slot)) setItemSlot(slot,stack); else cargo.replace(sources.get(slot),stack);
            }
        };
    }
    /** Leave the real armor for the next shift. A full rack falls back to warehouse storage, never overwrites a piece. */
    private boolean returnGuardArmor(ServerLevel level,Settlement town,Station station,boolean all) {
        if(!hasArmorToReturn(all)) return false;
        if(level.getGameTime()<gearReturnAt) {
            if(all) for(EquipmentSlot slot:GuardEquipment.ARMOR) { cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY); }
            return false;
        }
        List<ArmorStand> stands=new ArrayList<>(GuardService.stands(level,town,station));
        stands.sort(Comparator.comparingDouble(this::distanceToSqr));
        for(ArmorStand stand:stands) {
            if(Arrays.stream(GuardEquipment.ARMOR).noneMatch(slot -> stand.getItemBySlot(slot).isEmpty() && !returnableArmor(slot,all).isEmpty())) continue;
            if(!standNear(stand)) { if(approachStand(level,stand,all ? "Returning armor for the next shift" : "Returning armor for repair")) return true; continue; }
            getNavigation().stop(); gearStand=null; gearPathTicks=0;
            for(EquipmentSlot slot:GuardEquipment.ARMOR) GuardEquipment.deposit(returningArmor(all),equipment(stand),slot);
            if(!hasArmorToReturn(all)) return false;
        }
        // No accessible empty stand slot: remove the equipment before sleeping and keep it until a real destination accepts it.
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(!getItemBySlot(slot).isEmpty() && (all || GuardEquipment.worn(getItemBySlot(slot)))) {
            cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
        }
        BlockPos warehouse=jobDepot(level,town);
        if(warehouse!=null) {
            if(!canUse(level,warehouse)) {
                gearPathTicks+=10;
                if(gearPathTicks<=400 && canReach(warehouse) && walk(warehouse)) { activity="Returning spare armor to the guard barrel"; return true; }
            } else cargo.deposit(SettlementService.jobStorage(level,town,station),s -> armor(s) && (all || !retainSupply(s)) ? 0 : s.getCount());
        }
        gearPathTicks=0; gearReturnAt=level.getGameTime()+100; return false;
    }
    private boolean wornWeapons() {
        return GuardWeapons.weapon(getMainHandItem()) && GuardEquipment.worn(getMainHandItem())
                || !cargo.first(s -> GuardWeapons.weapon(s) && GuardEquipment.worn(s)).isEmpty();
    }
    private void retireWeapon() {
        if(GuardWeapons.weapon(getMainHandItem()) && GuardEquipment.worn(getMainHandItem())) { cargo.offer(getMainHandItem()); setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); }
    }
    /** Pick up loose weapons and arrows, such as a fallen skeleton's bow, that have lain in town for a few seconds. */
    private boolean scavenge(ServerLevel level,Settlement town) {
        if(scavengeTicks>0) { scavengeTicks-=10; return false; }
        ignoredLoot.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        ItemEntity loot=level.getEntitiesOfClass(ItemEntity.class,getBoundingBox().inflate(12,4,12),
                e -> e.isAlive() && e.getAge()>=100 && town.contains(e.blockPosition()) && !ignoredLoot.containsKey(e.getUUID()) && needs(e.getItem())).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(loot==null) { scavengeTicks=60; return false; }
        if(!loot.getUUID().equals(scavengeTarget)) { scavengeTarget=loot.getUUID(); scavengePathTicks=0; }
        if(distanceToSqr(loot)>2.25) {
            // Items on roofs or behind walls are skipped for a minute instead of holding the guard in place.
            scavengePathTicks+=10;
            if(scavengePathTicks==10 && !canReach(loot.blockPosition()) || scavengePathTicks>300 || !walk(loot.blockPosition()) && onGround()) {
                ignoredLoot.put(loot.getUUID(),level.getGameTime()+1200); scavengeTarget=null; scavengePathTicks=0; scavengeTicks=60; return false;
            }
            activity="Picking up a weapon or gear"; return true;
        }
        ItemStack stack=loot.getItem();
        int amount=wanted(stack);
        take(loot,amount);
        cargo.offer(stack.split(amount));
        if(stack.isEmpty()) loot.discard(); else loot.setItem(stack);
        readyMelee(); return false;
    }
    /** The next stop on the owner's marked route: the post, then each point in order, skipping any not loaded. */
    private BlockPos routePoint(ServerLevel level,Settlement town,GuardPosts plan,BlockPos post) {
        List<BlockPos> stops=new ArrayList<>();
        stops.add(post); stops.addAll(plan.patrol());
        for(int tries=0;tries<stops.size();tries++) {
            BlockPos next=stops.get(Math.floorMod(patrolVisits++,stops.size()));
            if(town.contains(next) && level.hasChunkAt(next)) return next;
        }
        return post;
    }
    private BlockPos patrolPoint(ServerLevel level,Settlement town,BlockPos post) {
        // Visit furnished work sites around town, returning to the active shift post every third leg.
        if(++patrolVisits%3==0) return post;
        for(int attempt=0;attempt<12;attempt++) {
            BlockPos anchor=attempt<6 && !town.stations.isEmpty()
                    ? town.stations.get(getRandom().nextInt(town.stations.size())).position() : blockPosition();
            int spread=attempt<6 ? 3 : 12;
            int x=anchor.getX()+getRandom().nextInt(spread*2+1)-spread;
            int z=anchor.getZ()+getRandom().nextInt(spread*2+1)-spread;
            BlockPos column=new BlockPos(x,anchor.getY(),z);
            if(!town.contains(column) || !level.hasChunkAt(column)) continue;
            for(int dy=2;dy>=-3;dy--) {
                BlockPos candidate=column.offset(0,dy,0);
                if(GuardService.walkable(level,town,candidate) && canReach(candidate)) return candidate;
            }
        }
        return post;
    }
    /** No citizen or player may stand within a block of the arrow's straight path. */
    private boolean clearShot(ServerLevel level,LivingEntity enemy) {
        Vec3 from=getEyePosition(),to=enemy.getBoundingBox().getCenter(),path=to.subtract(from);
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1.0),
                e -> e!=this && e!=enemy && e.isAlive() && (e instanceof CitizenEntity || e instanceof Player))) {
            Vec3 point=other.getBoundingBox().getCenter();
            double along=Math.clamp(point.subtract(from).dot(path)/Math.max(1.0E-6,path.lengthSqr()),0.0,1.0);
            if(point.distanceTo(from.add(path.scale(along)))<1.0) return false;
        }
        return true;
    }
    private void shoot(ServerLevel level,LivingEntity enemy) {
        ItemStack ammo=InventoryOps.takeOne(List.of(cargo),GuardWeapons::arrow);
        if(ammo.isEmpty()) return;
        ItemStack bow=getMainHandItem();
        var arrow=ProjectileUtil.getMobArrow(this,ammo,1.0F,bow);
        double dx=enemy.getX()-getX(),dy=enemy.getY(1.0/3.0)-arrow.getY(),dz=enemy.getZ()-getZ();
        arrow.shoot(dx,dy+Math.sqrt(dx*dx+dz*dz)*0.2,dz,1.6F,4.0F);
        level.addFreshEntity(arrow);
        playSound(SoundEvents.ARROW_SHOOT,1.0F,1.0F/(getRandom().nextFloat()*0.4F+0.8F));
        bow.hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
    }
    private void fight(ServerLevel level,LivingEntity enemy) {
        combatWith(enemy);
        setTarget(enemy); activity="Defending the settlement";
        getLookControl().setLookAt(enemy,30.0F,30.0F);
        double distance=distanceTo(enemy);
        Settlement town=town(level);
        BlockPos post=town==null ? null : town.jobs.home(getUUID());
        String role=post==null ? GuardPosts.SWORD : GuardRoles.role(level,post);
        boolean shield=GuardPosts.SHIELD.equals(role) && getOffhandItem().is(Items.SHIELD) && GuardEquipment.usable(getOffhandItem());
        // Archers shoot at range with real arrows, switching to a melee weapon once the enemy closes in.
        if(!shield && distance>(GuardPosts.ARCHER.equals(role) ? 3 : GuardWeapons.BOW_MIN_RANGE) && distance<=GuardWeapons.BOW_MAX_RANGE && arrows()>0
                && hasLineOfSight(enemy) && carries(GuardWeapons::bow) && clearShot(level,enemy) && hold(GuardWeapons::bow)) {
            getNavigation().stop(); activity="Shooting at an attacker";
            if(isUsingItem() && getUsedItemHand()!=InteractionHand.MAIN_HAND) stopUsingItem();
            if(!isUsingItem()) { if(guardAttackTicks==0) startUsingItem(InteractionHand.MAIN_HAND); }
            else if(getTicksUsingItem()>=20) { stopUsingItem(); shoot(level,enemy); guardAttackTicks=CitizenSkill.guardCooldown(skillLevel(StructureRole.GUARD),getRandom().nextInt(100)); }
            return;
        }
        // Tool-work reach is wider than a native melee swing. Keep approaching until an attack can land.
        boolean inReach=isWithinMeleeAttackRange(enemy) && hasLineOfSight(enemy);
        if(shield && (!inReach || guardAttackTicks>0)) {
            if(isUsingItem() && getUsedItemHand()!=InteractionHand.OFF_HAND) stopUsingItem();
            if(!isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
        } else if(isUsingItem()) stopUsingItem();
        // Without a melee weapon, put the bow away and fight with fists instead of wearing it out.
        if(!hold(GuardWeapons::melee) && GuardWeapons.bow(getMainHandItem())) wield(ItemStack.EMPTY);
        if(inReach) {
            getNavigation().stop();
            if(guardAttackTicks==0) {
                swing(InteractionHand.MAIN_HAND);
                if(doHurtTarget(level,enemy) && GuardWeapons.melee(getMainHandItem())) getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
                guardAttackTicks=shield ? 20 : CitizenSkill.guardCooldown(skillLevel(StructureRole.GUARD),getRandom().nextInt(100));
            }
        } else walk(enemy.blockPosition(),0.8);
    }
    /**
     * Head for a hostile a citizen reported, or a wave straggler, and fight it once it is in sight. One that stays out
     * of reach for a minute, or has no path at all, is left to the other guards for two minutes.
     */
    private boolean respond(ServerLevel level,Settlement town,DefenseService.Call call) {
        Monster enemy=call.mob();
        String name=enemy.getName().getString();
        if(!enemy.getUUID().equals(respondTarget)) { respondTarget=enemy.getUUID(); respondTicks=0; }
        if(distanceToSqr(enemy)<=GuardWeapons.BOW_MAX_RANGE*GuardWeapons.BOW_MAX_RANGE && hasLineOfSight(enemy)) {
            respondTicks=0;
            fight(level,enemy);
            activity=call.wave() ? "Fighting a straggler from the wave" : "Dealing with the "+name+" "+call.reporter()+" reported";
            return true;
        }
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        respondTicks+=10;
        activity=(call.wave() ? "Hunting a glowing "+name+" left from the wave" : "Answering "+call.reporter()+"'s call about a "+name)
                +" near "+enemy.blockPosition().toShortString();
        if(respondTicks>1800 || !walk(enemy.blockPosition(),0.8) && onGround()) {
            ignoredThreats.put(enemy.getUUID(),level.getGameTime()+2400);
            DefenseService.release(town,enemy.getUUID(),getUUID());
            respondTarget=null; respondTicks=0; return false;
        }
        return true;
    }
    /** A civilian who spots a hostile near their work calls the guards to deal with it. */
    public void called(Monster monster,boolean guards) {
        if(isGuard()) return;
        callNote=guards ? "Called the guards about a "+monster.getName().getString()+" nearby"
                : "Spotted a "+monster.getName().getString()+" nearby, but the town has no guards";
        callNoteUntil=level().getGameTime()+100;
    }
    private void runToBell(ServerLevel level,Settlement town,BlockPos bell) {
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        if(canUse(level,bell)) {
            getNavigation().stop(); getLookControl().setLookAt(bell.getX()+0.5,bell.getY()+0.5,bell.getZ()+0.5);
            swing(InteractionHand.MAIN_HAND); activity="Ringing the alarm bell";
            DefenseService.ring(level,town,this,bell); return;
        }
        activity="Running to ring the alarm bell";
        // Mid-jump or mid-fall a path cannot be planned; only give up once on the ground.
        if(!walk(bell,0.9) && onGround()) DefenseService.abandon(town,getUUID());
    }
    private void guard(ServerLevel level,Settlement town,Station station) {
        ignoredStands.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        retireWeapon();
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(GuardEquipment.worn(getItemBySlot(slot))) {
            cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
        }
        wearLocalArmor();
        BlockPos bell=DefenseService.bellRun(town,getUUID());
        boolean alarm=DefenseService.alarmed(town);
        LivingEntity aggressor=combatEnemy==null ? null : level.getEntity(combatEnemy) instanceof LivingEntity living ? living : null;
        if(aggressor instanceof Monster hostile && DefenseService.hostile(hostile) && town.contains(hostile.blockPosition())
                && distanceToSqr(hostile)<=48*48 && hasLineOfSight(hostile)) { wakeForAlarm(); readyMelee(); fight(level,hostile); return; }
        if(!GuardService.onDuty(level,town,station.position(),getUUID()) && !inCombat()) {
            guardWasActive=false;
            if(activePost!=null) { activePost=null; patrolTarget=null; getNavigation().stop(); }
            setTarget(null); if(isUsingItem()) stopUsingItem();
            if(returnGuardArmor(level,town,station,true)) return;
            if(wornWeapons() && level.getGameTime()>=guardSupplyAt) {
                BlockPos depot=jobDepot(level,town);
                if(depot!=null && (handNear(depot) || canReach(depot)) && !visitWarehouse(level,town,StructureRole.GUARD) && !canUse(level,depot)) return;
                guardSupplyAt=level.getGameTime()+200;
            }
            eatFrom(List.of(cargo)); rest(level,town);
            activity="Off duty: "+activity; return;
        }
        wakeForAlarm();
        if(!guardWasActive) {
            guardWasActive=true; gearTicks=0; armoryTicks=0; guardSupplyAt=0; gearReturnAt=0;
            gearStand=null; gearPathTicks=0; shiftGearUntil=level.getGameTime()+400;
        }
        wearLocalArmor(); readyMelee();
        if(town.trading.npc) {
            Player attacker=level.getEntitiesOfClass(Player.class,getBoundingBox().inflate(24),p -> p.isAlive() && !p.isSpectator()
                    && !p.getAbilities().instabuild && town.contains(p.blockPosition()) && town.trading.relations.getOrDefault(p.getUUID(),0)<0 && hasLineOfSight(p))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if(attacker!=null) { fight(level,attacker); activity="Defending the town against an attacker"; return; }
        }
        GuardPosts plan=GuardService.posts(level,station);
        boolean shield=GuardPosts.SHIELD.equals(plan.role()),archer=GuardPosts.ARCHER.equals(plan.role());
        BlockPos held=plan.active(night(level));
        // A shield guard keeps its gate; an archer watches the approaches from farther away.
        int sight=archer ? (alarm ? 40 : 28) : alarm ? 32 : 16;
        Monster enemy=level.getEntitiesOfClass(Monster.class,getBoundingBox().inflate(sight),
                m -> DefenseService.hostile(m) && town.contains(m.blockPosition()) && hasLineOfSight(m)
                        && (!shield || alarm || m.blockPosition().distSqr(held)<=100 || m.getTarget()==this)).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        ignoredThreats.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        DefenseService.Call call=shield && !alarm ? null : DefenseService.assignment(level,town,this,ignoredThreats::containsKey);
        if(enemy!=null && (enemy.getTarget()==this || CitizenReach.within(getEyePosition(),enemy.getBoundingBox()))) { fight(level,enemy); return; }
        if(call!=null && respond(level,town,call)) return;
        if(enemy!=null) { fight(level,enemy); return; }
        if(bell!=null) { runToBell(level,town,bell); return; }
        respondTarget=null; respondTicks=0;
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        if(returnGuardArmor(level,town,station,false)) return;
        if(equipFromStand(level,town,station) || scavenge(level,town)) return;
        useLocalSupplies(StructureRole.GUARD);
        if(armoryTicks>0) armoryTicks-=10;
        else {
            armoryTicks=100;
            BlockPos depot=jobDepot(level,town);
            armoryStocked=depot!=null && SettlementService.jobStorage(level,town,station).stream().anyMatch(this::stocks);
        }
        // While the alarm rings, only an unarmed guard leaves the defense to resupply.
        if(wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0 && level.getGameTime()>=nextFoodTripAt
                && !alarm && !visitPantry(level,town)) return;
        boolean foodTrip=wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0;
        boolean resupply=alarm ? !carries(GuardWeapons::weapon) && (armoryStocked || wornWeapons())
                : deliverCargo() || mealTicks<=0 || foodTrip || armoryStocked || wornWeapons();
        if(resupply && level.getGameTime()>=guardSupplyAt) {
            BlockPos warehouse=jobDepot(level,town);
            if(warehouse!=null && (handNear(warehouse) || canReach(warehouse))
                    && !visitWarehouse(level,town,StructureRole.GUARD) && !canUse(level,warehouse)) return;
            // An empty pantry must not leave the station's only sentry waiting there forever.
            guardSupplyAt=level.getGameTime()+200;
            armoryStocked=false;
        }
        // Give the outgoing guard time to bring back the shared set. Missing stock never suspends defense indefinitely.
        if(!alarm && level.getGameTime()<shiftGearUntil && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> getItemBySlot(slot).isEmpty())) {
            if(!near(station.position())) walk(station.position()); else getNavigation().stop();
            activity="Starting shift: checking for the shared armor set"; return;
        }
        String shift=(alarm ? "On alert: " : "")+(night(level) ? "night" : "day");
        BlockPos post=GuardService.posts(level,station).active(night(level));
        if(!Objects.equals(activePost,post)) { activePost=post; patrolTarget=post; patrolTicks=0; pathTicks=0; getNavigation().stop(); }
        if(!town.contains(post) || !level.hasChunkAt(post)) { activity="Waiting for the shift post to be loaded"; return; }
        if(shield) {
            if(near(post)) { getNavigation().stop(); activity="Holding the "+shift+" post"+(getOffhandItem().is(Items.SHIELD) ? " behind a shield" : ""); }
            else { activity="Returning to hold the "+shift+" post"; walk(post,alarm ? 0.8 : 0.65); }
            return;
        }
        if(patrolTicks>0) { patrolTicks-=10; activity="Guarding the "+shift+" post"; return; }
        if(patrolTarget==null) { patrolTarget=plan.patrol().isEmpty() ? patrolPoint(level,town,post) : routePoint(level,town,plan,post); pathTicks=0; }
        if(near(patrolTarget)) {
            getNavigation().stop(); patrolTarget=null; patrolTicks=(alarm ? 20 : 40)*(archer ? 2 : 1); pathTicks=0;
            activity=(plan.patrol().isEmpty() ? "Patrolling the " : "Walking the marked ")+shift+" route"; return;
        }
        activity="Walking the "+shift+" patrol"; pathTicks+=10;
        if(!walk(patrolTarget,alarm ? 0.8 : 0.65) || pathTicks>600) { patrolTarget=null; patrolTicks=60; pathTicks=0; }
    }
    /** Craftsmen fill warehouse shortages from real materials, one trip of batches at a time. */
    private void craft(ServerLevel level,Settlement town,Station station,BlockPos bench) {
        if(order!=null && (town.disabledRecipes.contains(order.id()) || !Crafting.ready(cargo,order))) order=null;
        if(order==null) {
            // Hand in what was baked where finished goods go, then gather wheat: from the kitchen's barrels first.
            if(carryingGoods(station.role()) && !visitDepot(level,town,station)) return;
            List<Container> stock=SettlementService.townStorage(level,town);
            List<Container> local=SettlementService.jobStorage(level,town,station);
            BlockPos barrel=jobBarrel(level,town,station);
            if(barrel==null) return;
            if(!visitStorage(level,town,station.role(),barrel,local,false)) return;
            List<Container> storage=local;
            Crafting.Recipe next=Crafting.choose(stock,storage,town.disabledRecipes,station.role());
            if(next==null || Crafting.fetch(stock,storage,cargo,next)==0) {
                activity="Waiting for orders or courier-delivered ingredients in the kitchen barrel";
                pauseStation(level,station.position(),400); releaseWork(level); searchDelay=20; return;
            }
            order=next; workProgress=0; pathTicks=0;
        }
        if(!canUse(level,bench)) {
            activity="Carrying materials for "+order.label()+" to the workbench"; pathTicks+=10;
            if(station.role().processes()) { approachProcessor(level,town); return; }
            if(!walk(bench) || pathTicks>1200) { pauseStation(level,station.position(),200); releaseWork(level); }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        getLookControl().setLookAt(bench.getX()+0.5,bench.getY()+0.5,bench.getZ()+0.5);
        activity="Crafting "+order.label();
        WorkFeedback.pulse(level,this,bench,WorkFeedback.CRAFTING);
        workProgress+=workStep();
        if(workProgress>=CRAFT_TICKS) {
            workProgress=0; swing(InteractionHand.MAIN_HAND); cargo.offer(Crafting.craft(cargo,order)); gainExperience(station.role(),1);
            order=null; processingDelivery=true; processingSupplied=false;
        }
    }
    /**
     * Craftsmen fill learned orders in the owner's priority order: deliver what they made, then gather materials for one
     * trip, from the workshop's own barrels when they hold enough and from the warehouse otherwise, and craft at the bench.
     */
    private void craftsman(ServerLevel level,Settlement town,Station station) {
        BlockPos bench=station.position();
        if(craftJob==null && maintainTraps(level,town,station)) return;
        if(craftJob!=null && ForgeWorkshop.forged(craftJob.plan().result())) { craftJob=null; workProgress=0; }
        if(craftJob!=null && !AgeProgression.allowed(town,craftJob.plan().result())) {
            activity="Workshop order requires "+AgeProgression.requirement(craftJob.plan().result())+" research";
            getNavigation().stop(); return;
        }
        if(craftJob!=null && (Workshop.find(town,craftJob.order().item())<0 || town.craftOrders.get(Workshop.find(town,craftJob.order().item())).target()<=0
                || !Workshop.ready(cargo,craftJob.plan()))) craftJob=null;
        if(craftJob==null) {
            if(carryingGoods(station.role()) && !visitDepot(level,town,station)) return;
            List<Container> stock=SettlementService.townStorage(level,town);
            BlockPos barrel=jobBarrel(level,town,station);
            List<Container> local=SettlementService.jobStorage(level,town,station);
            var permitted=town.craftOrders.stream().filter(o -> {
                var item=o.resolve();
                return item!=Items.AIR && !ForgeWorkshop.forged(new ItemStack(item)) && AgeProgression.allowed(town,new ItemStack(item));
            }).toList();
            Workshop.Job next=barrel==null ? null : Workshop.choose(Workshop.Recipes.of(level),permitted,stock,local);
            if(barrel==null) return;
            if(!visitStorage(level,town,station.role(),barrel,local,false)) return;
            List<Container> sources=local;
            if(next==null || Workshop.fetch(Workshop.Recipes.of(level),town.craftOrders,next,stock,sources,cargo)==0) {
                activity="Orders are stocked, or waiting for courier-delivered materials";
                pauseStation(level,station.position(),400); releaseWork(level); searchDelay=20; return;
            }
            craftJob=next; workProgress=0; pathTicks=0;
        }
        String product=craftJob.plan().result().getHoverName().getString();
        if(!canUse(level,bench)) {
            activity="Carrying materials for "+product+" to the workbench"; pathTicks+=10;
            if(!walk(bench) || pathTicks>1200) { pauseStation(level,station.position(),200); releaseWork(level); }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        getLookControl().setLookAt(bench.getX()+0.5,bench.getY()+0.5,bench.getZ()+0.5);
        activity="Crafting "+product;
        WorkFeedback.pulse(level,this,bench,WorkFeedback.CRAFTING);
        workProgress+=workStep();
        if(workProgress>=CRAFT_TICKS) {
            workProgress=0;
            if(Workshop.craft(level,cargo,craftJob.plan(),cargo::offer)) { swing(InteractionHand.MAIN_HAND); gainExperience(station.role(),1); }
            else craftJob=null;
        }
    }
    /** A craftsman carries paid materials to one reachable defense, and performs repairs only after the alarm ends. */
    private boolean maintainTraps(ServerLevel level,Settlement town,Station station) {
        if(DefenseService.alarmed(town)) { trapWork=null; trapWorkTicks=0; trapPathTicks=0; return false; }
        long now=level.getGameTime();
        failedTrapWork.values().removeIf(until -> until<=now);
        if(trapWork!=null && (!level.hasChunkAt(trapWork) || !TrapService.needsMaintenance(level.getBlockState(trapWork)))) {
            trapWork=null; trapWorkTicks=0; trapPathTicks=0;
        }
        List<Container> local=SettlementService.jobStorage(level,town,station);
        if(trapWork==null) {
            List<Container> available=new ArrayList<>(local); available.add(cargo);
            trapWork=TrapService.nextMaintenance(level,town,station.position(),available,
                    pos -> failedTrapWork.getOrDefault(pos,0L)>now || pos.distSqr(station.position())>128*128);
            if(trapWork==null) return false;
            trapWorkTicks=0; trapPathTicks=0;
        }
        var material=TrapService.material(level.getBlockState(trapWork));
        if(material==null) { trapWork=null; return false; }
        if(!TrapService.supplied(material,List.of(cargo))) {
            BlockPos barrel=jobBarrel(level,town,station);
            if(barrel==null) { trapWork=null; return false; }
            if(!visitStorage(level,town,StructureRole.CRAFTSMAN,barrel,local,false)) return true;
            int held=InventoryOps.count(List.of(cargo),material.accepts());
            if(InventoryOps.count(local,material.accepts())+held<material.count()) { trapWork=null; return false; }
            for(int i=held;i<material.count();i++) cargo.offer(InventoryOps.takeOne(local,material.accepts()));
        }
        if(!canUse(level,trapWork)) {
            activity="Carrying materials to maintain a trap"; trapPathTicks+=10;
            if(!walk(trapWork) || trapPathTicks>=600) {
                failedTrapWork.put(trapWork,now+600); trapWork=null; trapWorkTicks=0; trapPathTicks=0; return false;
            }
            return true;
        }
        getNavigation().stop(); activity="Maintaining a settlement trap";
        WorkFeedback.pulse(level,this,trapWork,WorkFeedback.CRAFTING); trapWorkTicks+=workStep();
        if(trapWorkTicks>=CRAFT_TICKS) {
            if(TrapService.maintain(level,town,trapWork,List.of(cargo))) gainExperience(StructureRole.CRAFTSMAN,1);
            trapWork=null; trapWorkTicks=0; trapPathTicks=0;
        }
        return true;
    }
    /** Couriers carry finished goods to the warehouse and supply every production job's local barrels. */
    private void courier(ServerLevel level,Settlement town,Station station) {
        eatFrom(List.of(cargo));
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse==null) {
            activity="Needs a loaded warehouse to carry goods to";
            pauseStation(level,station.position(),400); releaseWork(level); searchDelay=20; return;
        }
        Station job=haulStation==null ? null : town.station(haulStation);
        List<BlockPos> barrels=job==null ? List.of() : SettlementService.jobBarrels(level,town,job);
        if(barrels.isEmpty() || nearestBarrel(barrels)==null) {
            haulStation=null; haulSupply=false;
            // Goods already carried reach the warehouse before the next errand.
            if(cargo.hasDeliverable(this::retainSupply,this::food) || InventoryOps.count(List.of(cargo),this::food)>0) {
                visitWarehouse(level,town,StructureRole.COURIER); return;
            }
            if(wantsMeal() && level.getGameTime()>=nextFoodTripAt && !visitPantry(level,town)) return;
            job=errand(level,town,warehouse);
            if(job==null) {
                activity="No goods waiting in job barrels";
                pauseStation(level,station.position(),200); releaseWork(level); searchDelay=20; return;
            }
            barrels=SettlementService.jobBarrels(level,town,job);
        }
        var book=SettlementService.reservations(level);
        BlockPos claim=barrels.getFirst(),barrel=nearestBarrel(barrels);
        // One courier per job's barrels at a time.
        if(!book.claim(claim,getUUID(),level.getGameTime(),200)) { haulStation=null; haulSupply=false; return; }
        if((haulTicks+=10)>2400) {
            // An errand that drags on is dropped, and its barrel skipped for a while.
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(barrel,level.getGameTime()+1200);
            book.release(claim,getUUID()); haulStation=null; haulSupply=false; return;
        }
        List<Container> local=SettlementService.jobStorage(level,town,job);
        if(haulSupply && InventoryOps.count(List.of(cargo),this::haulingInput)==0) {
            // Pick up the supplies first.
            if(!visitWarehouse(level,town,StructureRole.COURIER)) return;
            if(JobStorage.load(JobStorage.Supplies.of(level),town,job,local,SettlementService.storageAt(level,town,warehouse),cargo)==0) {
                book.release(claim,getUUID()); haulStation=null; haulSupply=false;
            } else activity="Carrying supplies to the "+job.role().id()+" station";
            return;
        }
        if(!canUse(level,barrel)) {
            activity=haulSupply ? "Carrying supplies to the "+job.role().id()+" station" : "Walking to collect goods at the "+job.role().id()+" station";
            if(!walk(barrel) && onGround()) {
                if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(barrel,level.getGameTime()+1200);
                book.release(claim,getUUID()); haulStation=null; haulSupply=false;
            }
            return;
        }
        getNavigation().stop(); swing(InteractionHand.MAIN_HAND);
        if(haulSupply) {
            StructureRole role=job.role();
            JobStorage.Supplies supplies=JobStorage.Supplies.of(level);
            cargo.deposit(local,stack -> JobStorage.input(supplies,town,role,stack) ? 0 : stack.getCount());
            activity="Stocked the "+role.id()+" station's barrels";
            gainExperience(StructureRole.COURIER,1);
        } else {
            if(job.role()==StructureRole.GUARD) {
                ArmorStand rack=GuardService.stands(level,town,job).stream()
                        .filter(stand -> Arrays.stream(EquipmentSlot.values()).anyMatch(slot -> GuardEquipment.worn(stand.getItemBySlot(slot))))
                        .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                if(rack!=null) {
                    if(!standNear(rack)) { approachStand(level,rack,"Collecting retired rack equipment for the blacksmith"); return; }
                    for(EquipmentSlot slot:EquipmentSlot.values()) if(GuardEquipment.worn(rack.getItemBySlot(slot))) {
                        cargo.offer(rack.getItemBySlot(slot)); rack.setItemSlot(slot,ItemStack.EMPTY);
                    }
                }
            }
            int moved=JobStorage.collect(JobStorage.Supplies.of(level),town,job.role(),local,cargo);
            activity="Collected "+moved+" goods from the "+job.role().id()+" station";
            if(moved>0) gainExperience(StructureRole.COURIER,1);
        }
        book.release(claim,getUUID());
        haulStation=null; haulSupply=false;
    }
    /**
     * The next courier errand: the largest worthwhile load, otherwise supplies for a production job, otherwise any
     * waiting goods at all, so a pair of new tools or a few ingots never wait for a full load.
     */
    private Station errand(ServerLevel level,Settlement town,BlockPos warehouse) {
        List<Container> stored=SettlementService.storageAt(level,town,warehouse);
        int pantry=InventoryOps.count(stored,FoodHealing::food);
        JobStorage.Supplies supplies=JobStorage.Supplies.of(level);
        var book=SettlementService.reservations(level);
        Station worthwhile=null,small=null,supply=null;
        int most=0,fewest=0;
        for(Station candidate:town.stations) {
            List<BlockPos> spots=SettlementService.jobBarrels(level,town,candidate);
            if(spots.isEmpty() || nearestBarrel(spots)==null || !book.available(spots.getFirst(),getUUID(),level.getGameTime())) continue;
            List<Container> local=SettlementService.jobStorage(level,town,candidate);
            var pickups=JobStorage.collectable(supplies,town,candidate.role(),local);
            int goods=JobStorage.goods(pickups);
            if(candidate.role()==StructureRole.GUARD) for(ArmorStand rack:GuardService.stands(level,town,candidate))
                for(EquipmentSlot slot:EquipmentSlot.values()) if(GuardEquipment.worn(rack.getItemBySlot(slot))) goods+=rack.getItemBySlot(slot).getCount();
            if(goods>most && JobStorage.worthCollecting(pickups,JobStorage.freeSlots(local),pantry)) { worthwhile=candidate; most=goods; }
            else if(goods>fewest) { small=candidate; fewest=goods; }
            if(supply==null && JobStorage.needsSupplies(supplies,town,candidate,local,stored)) supply=candidate;
        }
        Station chosen=worthwhile!=null ? worthwhile : supply!=null ? supply : small;
        if(chosen!=null) { haulStation=chosen.position(); haulSupply=worthwhile==null && chosen==supply; haulTicks=0; }
        return chosen;
    }
    /** Interleave path probes across appliances so one blocked appliance cannot consume every probe. */
    private boolean chooseProcessingApproach(ServerLevel level,Settlement town,List<BlockPos> devices) {
        List<BlockPos> ordered=new ArrayList<>(devices);
        int first=processor==null ? -1 : ordered.indexOf(processor);
        if(first>=0) Collections.rotate(ordered,-first);
        else ordered.sort(Comparator.comparingDouble(p -> p.distSqr(blockPosition())));
        processor=null; processorStand=null;
        var view=standingView(level,town);
        List<List<BlockPos>> approaches=new ArrayList<>();
        int largest=0;
        for(BlockPos device:ordered) {
            List<BlockPos> stands=canUse(level,device) ? List.of(blockPosition())
                    : CitizenReach.stands(view,device,position(),getEyeHeight()).stream()
                        .filter(stand -> !failedTargets.containsKey(stand) && standingSpotUsable(level,town,stand,device)).toList();
            approaches.add(stands); largest=Math.max(largest,stands.size());
        }
        for(int step=0;step<largest;step++) for(int i=0;i<ordered.size();i++) {
            List<BlockPos> stands=approaches.get(i);
            if(step>=stands.size()) continue;
            if(reachableStand(stands.get(step))) { processor=ordered.get(i); processorStand=stands.get(step); return true; }
            if(reachBudget.deferred()) return false;
        }
        return false;
    }
    private boolean approachProcessor(ServerLevel level,Settlement town) {
        if(canUse(level,processor)) return true;
        activity="Carrying ingredients/fuel to the appliance"; pathTicks+=10;
        if(!standingSpotUsable(level,town,processorStand,processor) || !walk(processorStand,0.65,0) || pathTicks>1200) {
            // Only this standing spot is skipped, briefly. Other appliances and newly opened approaches still work.
            if(processorStand!=null && failedTargets.size()<MAX_FAILED_TARGETS)
                failedTargets.put(processorStand,level.getGameTime()+200);
            processorStand=null; pathTicks=0;
            getNavigation().stop();
        }
        return false;
    }
    private void process(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> devices=SettlementService.processingDevices(level,town,station);
        if(devices.isEmpty()) {
            activity=station.role()==StructureRole.COOK ? "Needs a furnace, smoker or lit campfire in range" : "Needs a furnace, blast furnace or alloy furnace in range";
            pauseStation(level,station.position(),200); releaseWork(level); return;
        }
        if(processor==null || !devices.contains(processor)
                || !canUse(level,processor) && !standingSpotUsable(level,town,processorStand,processor)) {
            if(!chooseProcessingApproach(level,town,devices)) {
                if(reachBudget.deferred()) { activity="Checking reachable cooking/smelting blocks"; return; }
                activity="Cannot reach the station's appliances";
                pauseStation(level,station.position(),200); releaseWork(level); return;
            }
            pathTicks=0;
        }
        if(level.getGameTime()<nextProcessingAt) { activity="Waiting for the next cooking/smelting batch"; return; }
        if(processingDelivery || !processingSupplied) {
            BlockPos barrel=jobBarrel(level,town,station);
            List<Container> local=barrel==null ? List.of() : SettlementService.jobStorage(level,town,station);
            if(barrel==null) return;
            if(!visitStorage(level,town,station.role(),barrel,local,true)) return;
            List<Container> storage=local;
            processingDelivery=false;
            processingSupplied=true;
            ProcessingService.fetch(level,station.role(),processor,storage,cargo);
            if(station.role()==StructureRole.COOK && !ProcessingService.busy(level,processor)
                    && !ProcessingService.hasInputs(level,station.role(),processor,List.of(cargo))) {
                List<Container> stock=SettlementService.townStorage(level,town);
                Crafting.Recipe bread=Crafting.choose(stock,storage,town.disabledRecipes,StructureRole.COOK);
                if(bread!=null && (Crafting.ready(cargo,bread) || Crafting.fetch(stock,storage,cargo,bread)>0)) { order=bread; workProgress=0; }
            }
        }
        if(!approachProcessor(level,town)) return;
        getNavigation().stop(); pathTicks=0;
        int collected=ProcessingService.service(level,station.role(),processor,cargo,this);
        if(collected>0 || ProcessingService.busy(level,processor)) WorkFeedback.pulse(level,this,processor,WorkFeedback.PROCESSING);
        swing(InteractionHand.MAIN_HAND);
        activity=station.role()==StructureRole.COOK ? "Supplying the kitchen and collecting cooked food" : "Supplying furnaces and collecting processed materials";
        if(collected==0 && !ProcessingService.busy(level,processor) && !ProcessingService.hasInputs(level,station.role(),processor,List.of(cargo))) {
            if(station.role()==StructureRole.COOK && order!=null && !town.disabledRecipes.contains(order.id()) && Crafting.ready(cargo,order)) {
                craft(level,town,station,processor);
                if(order==null) {
                    // One bread batch must not monopolize the worker while another appliance has finished meals.
                    processor=devices.get((devices.indexOf(processor)+1)%devices.size()); processorStand=null; nextProcessingAt=0;
                }
                return;
            }
            if(++processingIdle>=devices.size()) {
                activity="Waiting for courier-delivered processing inputs";
                pauseStation(level,station.position(),200); releaseWork(level); return;
            }
        } else processingIdle=0;
        if(ProcessingService.needsFuel(level,processor)) activity="Waiting for couriers to deliver furnace/smoker fuel";
        if(level.getBlockEntity(processor) instanceof io.github.swishhyy.wwmc.block.AlloyFurnaceEntity alloy) {
            if(alloy.data.get(4)==3 || alloy.data.get(4)==4) activity=alloy.data.get(4)==3 ? "Alloy furnace needs Bronze Age research" : "Alloy furnace needs Steelworking research";
            else if(alloy.data.get(4)==5) activity="Alloy furnace output is full";
        }
        processingDelivery=collected>0 || cargo.needsDelivery();
        processingSupplied=false;
        if(collected>0) gainExperience(station.role(),1);
        nextProcessingAt=processingDelivery ? 0 : level.getGameTime()+(workStep()>10 ? 20 : 40);
        // Rotate through the station's appliances; progress stays in their block entities.
        processor=devices.get((devices.indexOf(processor)+1)%devices.size());
        processorStand=null;
    }
    public String activity() { return level().getGameTime()<callNoteUntil ? callNote : activity; }
    /** Observe current interruptions without running pathfinding, taking supplies or waking the citizen. */
    public Research.Status researchStatus(ServerLevel level,Settlement town,Station station) {
        if(!level.isPositionEntityTicking(blockPosition()))
            return Research.Status.paused("Researcher is outside the active area. Keep their area loaded.");
        if(HospitalCare.needsCare(town,this)) return Research.Status.paused("Researcher needs hospital recovery before returning to work.");
        if(SquadService.assigned(town,getUUID())) return Research.Status.paused("Researcher is on squad duty. Return them to their job.");
        if(DefenseService.alarmed(town) || sheltering || level.getGameTime()<fearUntil || inCombat())
            return Research.Status.paused("Researcher is sheltering until the danger has passed.");
        if(night(level)) return Research.Status.paused("Researchers are off duty until morning.");
        if(isSleeping()) return Research.Status.paused("Researcher is sleeping.");
        if(cargo.isOpen()) return Research.Status.paused("Researcher's inventory is open. Close it to resume work.");
        if(isNoAi()) return Research.Status.paused("Researcher is unavailable for work. Check the Crew tab.");
        if(returningGear) return Research.Status.waiting("Researcher is returning equipment from their previous job.");
        if(pantryTarget!=null && wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0)
            return Research.Status.paused("Researcher is fetching a meal from the pantry.");
        String supplies=Research.scrollPause(level,town);
        if(!supplies.isEmpty()) return Research.Status.paused(supplies);
        if(station.position().equals(researchStation) && town.progress.project.equals(researchProject)
                && researchStateAt!=Long.MIN_VALUE && level.getGameTime()>=researchStateAt && level.getGameTime()-researchStateAt<=30)
            return researchState;
        return Research.Status.waiting("Waiting for the researcher to start work.");
    }
    public StructureRole jobRole() { return role(); }
    public CitizenInventory bag() { return cargo; }
    public boolean workWalk(BlockPos pos) { return walk(pos); }
    public boolean workStandAt(BlockPos pos) { return walk(pos,0.65,0); }
    public boolean workAt(ServerLevel level,BlockPos pos) { return canUse(level,pos); }
    public boolean workDepot(ServerLevel level,Settlement town,Station station) { return visitDepot(level,town,station); }
    public void workActivity(String text) { activity=text; }
    public boolean recovering() { return recovering; }
    public void recovering(boolean value) { recovering=value; }
    public BlockPos hospitalBed() { return hospitalBed; }
    public void pauseForHospital(ServerLevel level) { leaveBed(); releaseWork(level); }
    public boolean hospitalCanTry(BlockPos bed) { return !failedTargets.containsKey(bed); }
    public void hospitalBed(BlockPos bed) {
        if(Objects.equals(hospitalBed,bed)) return;
        leaveHospitalBed(); leaveBed(); hospitalBed=bed.immutable(); hospitalRestTicks=0; hospitalPathTicks=0;
    }
    public void leaveHospitalBed() {
        if(hospitalBed==null) return;
        if(isSleeping()) stopSleeping();
        if(level() instanceof ServerLevel level) SettlementService.reservations(level).release(hospitalBed,getUUID());
        hospitalBed=null; hospitalStand=null; hospitalRestTicks=0; hospitalPathTicks=0;
    }
    public boolean hospitalRestAt(ServerLevel level,Settlement town,BlockPos bed) {
        if(HospitalCare.inBed(this)) { getNavigation().stop(); return true; }
        if(canUse(level,bed) && distanceToSqr(Vec3.atCenterOf(bed))<=4) {
            getNavigation().stop(); startSleeping(bed); hospitalPathTicks=0; return true;
        }
        if(!standingSpotUsable(level,town,hospitalStand,bed)) {
            hospitalStand=null;
            for(BlockPos stand:CitizenReach.stands(standingView(level,town),bed,position(),getEyeHeight())) {
                if(standingView(level,town).feet(stand).distanceToSqr(Vec3.atCenterOf(bed))>4 || !standingSpotUsable(level,town,stand,bed)) continue;
                if(reachableStand(stand)) { hospitalStand=stand; break; }
                if(reachBudget.deferred()) return true;
            }
        }
        hospitalPathTicks+=10;
        if(hospitalStand==null || !walk(hospitalStand,0.65,0) || hospitalPathTicks>1200) {
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(bed,level.getGameTime()+200);
            return false;
        }
        return true;
    }
    public boolean hospitalHealingPulse() {
        hospitalRestTicks+=10;
        if(hospitalRestTicks<HospitalCare.HEAL_TICKS) return false;
        hospitalRestTicks=0; return true;
    }
    public void hospitalMeal(ServerLevel level,Settlement town) {
        if(wantsMeal() && !eatFrom(List.of(cargo))) visitPantry(level,town);
    }
    public void expeditionFight(ServerLevel level,Monster enemy) { fight(level,enemy); }
    public void expeditionWalk(ServerLevel level,BlockPos point) { lastWalkTick=tickCount; expeditionNavigation.walk(level,this,point,0.8); }
    /** Station this citizen works at, or null. */
    public BlockPos workplace() { return workplace; }
    /** Ticks until the next scheduled meal. */
    public int mealTicks() { return mealTicks; }
    /** Overflow from a large harvest waiting for bag space. */
    public boolean overflowing() { return cargo.hasPending(); }
    private ArmorStand repairStandEntity(ServerLevel level,Settlement town) {
        if(repairStand==null || !(level.getEntity(repairStand) instanceof ArmorStand stand) || !stand.isAlive()) return null;
        Station owner=town.nearestStation(stand.blockPosition(),s -> s.role()==StructureRole.GUARD && SettlementService.active(level,s));
        return owner!=null && GuardService.stands(level,town,owner).contains(stand) ? stand : null;
    }
    private void finishRepair(ServerLevel level,Settlement town) {
        ArmorStand stand=repairStandEntity(level,town);
        if(stand!=null && repairSlot!=null && stand.getItemBySlot(repairSlot).isEmpty() && GuardEquipment.armor(repairItem,repairSlot)) {
            if(!standNear(stand)) { if(approachStand(level,stand,"Returning repaired armor to its stand")) return; }
            else { getNavigation().stop(); stand.setItemSlot(repairSlot,repairItem); repairItem=ItemStack.EMPTY; }
        }
        if(!repairItem.isEmpty()) {
            BlockPos warehouse=jobDepot(level,town);
            if(warehouse==null) { activity="Holding repaired equipment: needs a blacksmith barrel"; return; }
            if(!canUse(level,warehouse)) { activity="Returning repaired equipment to the blacksmith barrel"; walk(warehouse); return; }
            getNavigation().stop();
            for(Container storage:SettlementService.jobStorage(level,town,town.station(workplace))) repairItem=InventoryOps.insert(storage,repairItem);
            if(!repairItem.isEmpty()) { activity="Holding repaired equipment: the blacksmith barrel is full"; return; }
        }
        repairStand=null; repairSlot=null; repairAnvil=null; repairDelivery=false; workProgress=0; nextSmithAt=level.getGameTime()+20;
        activity="Repair delivered";
    }
    private void blacksmith(ServerLevel level,Settlement town,Station station) {
        eatFrom(List.of(cargo));
        if(forgeJob!=null || forgeDelivery) { forge(level,town,station); return; }
        if(!repairItem.isEmpty() && (repairDelivery || !BlacksmithRepair.damaged(repairItem))) { finishRepair(level,town); return; }
        if(level.getGameTime()<nextSmithAt) return;
        List<BlockPos> anvils=SettlementService.anvils(level,town,station);
        if(repairAnvil==null || !anvils.contains(repairAnvil)) {
            repairAnvil=anvils.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition())))
                    .filter(p -> handNear(p) || canReach(p)).findFirst().orElse(null);
        }
        if(repairAnvil==null) { activity="Needs an accessible anvil within three blocks of the Blacksmith Station"; nextSmithAt=level.getGameTime()+100; return; }
        BlockPos warehouse=jobBarrel(level,town,station);
        if(warehouse==null) { nextSmithAt=level.getGameTime()+100; return; }
        List<Container> storage=SettlementService.jobStorage(level,town,town.station(workplace));
        // A chosen stand stays the destination until pickup, rather than restarting the warehouse trip each update.
        if(repairItem.isEmpty() && repairStand!=null) {
            ArmorStand stand=repairStandEntity(level,town);
            ItemStack selected=stand==null || repairSlot==null ? ItemStack.EMPTY : stand.getItemBySlot(repairSlot);
            if(stand==null || !GuardEquipment.worn(selected) || !BlacksmithRepair.supplied(selected,storage)) {
                repairStand=null; repairSlot=null; nextSmithAt=level.getGameTime()+40; return;
            }
            if(!standNear(stand)) {
                if(approachStand(level,stand,"Collecting worn armor for repair")) return;
                repairStand=null; repairSlot=null; nextSmithAt=level.getGameTime()+100; return;
            }
            getNavigation().stop(); repairItem=selected.split(1);
            stand.setItemSlot(repairSlot,selected.isEmpty() ? ItemStack.EMPTY : selected);
        }
        boolean supplied=!repairItem.isEmpty() && BlacksmithRepair.supplied(repairItem,List.of(cargo));
        if(repairItem.isEmpty() || !supplied || cargo.needsDelivery()) {
            if(!visitWarehouse(level,town,StructureRole.BLACKSMITH)) return;
            storage=SettlementService.jobStorage(level,town,town.station(workplace));
            if(repairItem.isEmpty()) {
                List<Container> stock=storage;
                repairItem=InventoryOps.takeBest(storage,s -> BlacksmithRepair.supplied(s,stock),s -> s.getDamageValue()/(double)s.getMaxDamage());
                repairStand=null; repairSlot=null;
                if(repairItem.isEmpty()) {
                    // Guard racks hold retired armor. Only pieces below 25% are borrowed, leaving usable shared sets for the next shift.
                    for(Station guard:town.stations) if(guard.role()==StructureRole.GUARD && SettlementService.active(level,guard))
                        for(ArmorStand stand:GuardService.stands(level,town,guard)) {
                            if(!station.contains(stand.blockPosition())) continue;
                            if(ignoredStands.getOrDefault(stand.getUUID(),0L)>level.getGameTime()) continue;
                            for(EquipmentSlot slot:GuardEquipment.ARMOR) if(GuardEquipment.worn(stand.getItemBySlot(slot))
                                    && BlacksmithRepair.supplied(stand.getItemBySlot(slot),storage)) {
                                repairStand=stand.getUUID(); repairSlot=slot;
                                activity="Collecting retired guard armor"; return;
                            }
                        }
                    forge(level,town,station); return;
                }
            }
            int needed=BlacksmithRepair.materialsNeeded(repairItem)-InventoryOps.count(List.of(cargo),s -> BlacksmithRepair.material(repairItem,s));
            for(int count=0;count<needed;count++) {
                ItemStack material=InventoryOps.takeOne(storage,s -> BlacksmithRepair.material(repairItem,s));
                if(material.isEmpty()) break;
                cargo.offer(material);
            }
            if(!BlacksmithRepair.supplied(repairItem,List.of(cargo))) { activity="Waiting for this item's matching repair material"; nextSmithAt=level.getGameTime()+100; return; }
        }
        if(!approachAnvil(level,town)) return;
        getNavigation().stop(); activity="Repairing equipment at the anvil";
        WorkFeedback.pulse(level,this,repairAnvil,WorkFeedback.SMITHING); workProgress+=workStep();
        if(workProgress>=ForgeWorkshop.workTicks(level.getBlockState(repairAnvil),BlacksmithRepair.WORK_TICKS)) {
            if(BlacksmithRepair.repair(repairItem,cargo)) { swing(InteractionHand.MAIN_HAND); playSound(SoundEvents.ANVIL_USE,0.4F,1.0F);
                ForgeWorkshop.wear(level,repairAnvil); gainExperience(StructureRole.BLACKSMITH,2); }
            workProgress=0;
        }
        if(!BlacksmithRepair.damaged(repairItem)
                || !BlacksmithRepair.supplied(repairItem,List.of(cargo)) && !BlacksmithRepair.supplied(repairItem,storage)) {
            repairDelivery=true; finishRepair(level,town);
        }
    }
    /** A solid anvil is an interaction target. Approach visible standing room beside it. */
    private boolean approachAnvil(ServerLevel level,Settlement town) {
        if(canUse(level,repairAnvil)) { smithStand=null; return true; }
        if(!standingSpotUsable(level,town,smithStand,repairAnvil)) {
            smithStand=null;
            for(BlockPos stand:CitizenReach.stands(standingView(level,town),repairAnvil,position(),getEyeHeight())) {
                if(!standingSpotUsable(level,town,stand,repairAnvil)) continue;
                if(reachableStand(stand)) { smithStand=stand; break; }
                if(reachBudget.deferred()) { activity="Checking a route to the anvil"; return false; }
            }
        }
        activity=smithStand==null ? "Cannot reach the anvil: clear standing room beside it" : "Walking to the anvil";
        if(smithStand!=null) walk(smithStand,0.65,0);
        else { repairAnvil=null; nextSmithAt=level.getGameTime()+100; }
        return false;
    }
    /** The smith takes one paid batch, works at a real anvil, then returns the actual result to its barrel. */
    private void forge(ServerLevel level,Settlement town,Station station) {
        if(forgeDelivery) {
            if(!visitDepot(level,town,station)) return;
            forgeDelivery=false; forgeJob=null; workProgress=0; nextSmithAt=level.getGameTime()+20; return;
        }
        if(level.getGameTime()<nextSmithAt) return;
        var anvils=SettlementService.anvils(level,town,station);
        if(repairAnvil==null || !anvils.contains(repairAnvil)) {
            repairAnvil=anvils.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition())))
                    .filter(p -> handNear(p) || canReach(p)).findFirst().orElse(null);
            smithStand=null;
        }
        if(repairAnvil==null) { activity="Forging needs an accessible bronze or iron anvil"; nextSmithAt=level.getGameTime()+100; return; }
        if(ForgeWorkshop.heat(level,town,station).isEmpty()) { activity="Forging needs a furnace or blast furnace in the Blacksmith Station's range"; nextSmithAt=level.getGameTime()+100; return; }
        if(forgeJob!=null && (town.progress.forgeOrders.stream().noneMatch(o -> o.item().equals(forgeJob.order().item()) && o.target()>0)
                || !ForgeWorkshop.ready(forgeJob,List.of(cargo)))) { forgeJob=null; workProgress=0; }
        if(forgeJob==null) {
            BlockPos barrel=jobBarrel(level,town,station);
            if(barrel==null) return;
            List<Container> local=SettlementService.jobStorage(level,town,station);
            if(!visitStorage(level,town,StructureRole.BLACKSMITH,barrel,local,true)) return;
            forgeJob=ForgeWorkshop.choose(level,town,SettlementService.townStorage(level,town),local,
                    level.getBlockState(repairAnvil).getBlock() instanceof io.github.swishhyy.wwmc.block.BronzeAnvilBlock);
            if(forgeJob==null || !ForgeWorkshop.fetch(forgeJob,local,cargo)) {
                forgeJob=null; activity=town.progress.forgeOrders.stream().noneMatch(o -> o.target()>0)
                        ? "Set forging or metallurgy orders in Production / Forge; repairs run automatically"
                        : "Forge orders are stocked, locked by research, or missing materials and coal/charcoal";
                nextSmithAt=level.getGameTime()+100; return;
            }
            workProgress=0;
        }
        if(!AgeProgression.allowed(town,forgeJob.result())) { activity="Forge order needs "+AgeProgression.requirement(forgeJob.result())+" research"; return; }
        if(level.getBlockState(repairAnvil).getBlock() instanceof io.github.swishhyy.wwmc.block.BronzeAnvilBlock
                && AgeProgression.required(forgeJob.result())>2) { activity="Gem and netherite forging needs an iron anvil"; return; }
        if(!approachAnvil(level,town)) return;
        getNavigation().stop(); getLookControl().setLookAt(repairAnvil.getX()+.5,repairAnvil.getY()+.8,repairAnvil.getZ()+.5);
        activity="Forging "+forgeJob.result().getHoverName().getString();
        WorkFeedback.pulse(level,this,repairAnvil,WorkFeedback.SMITHING); workProgress+=workStep();
        if(workProgress>=ForgeWorkshop.workTicks(level.getBlockState(repairAnvil),forgeJob.ticks())) {
            if(ForgeWorkshop.craft(level,forgeJob,cargo)) {
                swing(InteractionHand.MAIN_HAND); playSound(SoundEvents.ANVIL_USE,.4F,1.0F); ForgeWorkshop.wear(level,repairAnvil);
                gainExperience(StructureRole.BLACKSMITH,2); forgeDelivery=true;
            } else { forgeJob=null; activity="Forge materials changed; returning the remaining supplies"; }
            workProgress=0;
        }
    }
    private int lapisCarried() { return InventoryOps.count(List.of(cargo),Enchanting::lapis); }
    private boolean standingSpotUsable(ServerLevel level,Settlement town,BlockPos stand,BlockPos table) {
        var view=standingView(level,town);
        if(stand==null || !CitizenReach.standing(view,stand)) return false;
        Vec3 feet=view.feet(stand);
        // If the citizen has actually arrived but still cannot interact, try another approach rather than
        // trusting an optimistic ray from the ideal node center forever.
        if(getNavigation().isDone() && position().distanceToSqr(feet)<0.01 && !canUse(level,table)) return false;
        return CitizenReach.canUse(level,feet.add(0,getEyeHeight(),0),table);
    }
    /** A solid table is a work target, not a walking destination. Probe only clear ground from which it can be used. */
    private boolean chooseEnchantingApproach(ServerLevel level,Settlement town,List<BlockPos> tables) {
        enchantTable=null; enchantStand=null;
        var view=standingView(level,town);
        for(BlockPos table:tables.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).toList()) {
            if(canUse(level,table)) { enchantTable=table; return true; }
            for(BlockPos stand:CitizenReach.stands(view,table,position(),getEyeHeight())) {
                // Obstructed views do not spend a path probe or blacklist the table.
                if(!standingSpotUsable(level,town,stand,table)) continue;
                if(reachableStand(stand)) { enchantTable=table; enchantStand=stand; return true; }
                if(reachBudget.deferred()) return false;
            }
        }
        return false;
    }
    /** Enchanters take one item and lapis from their local barrels and work from clear ground within reach of a table. */
    private void researcher(ServerLevel level,Settlement town,Station station) {
        if(!station.position().equals(researchStation) || !town.progress.project.equals(researchProject)) {
            researchDesk=null; researchStand=null; researchRejectedStands.clear(); nextResearchRouteAt=0;
        }
        researchProject=town.progress.project; researchStation=station.position(); researchStateAt=level.getGameTime();
        researchState=Research.Status.waiting("Researcher is checking a route to the lectern.");
        eatFrom(List.of(cargo));
        if(wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0 && !visitPantry(level,town)) {
            researchState=Research.Status.paused("Researcher is fetching a meal from the pantry."); return;
        }
        String supplies=Research.scrollPause(level,town);
        if(!supplies.isEmpty()) { activity=supplies; researchState=Research.Status.paused(supplies); getNavigation().stop(); return; }
        List<BlockPos> desks=Research.desks(level,town,station);
        if(researchDesk==null && level.getGameTime()<nextResearchRouteAt) {
            researchBlocked(level,desks); return;
        }
        if(nextResearchRouteAt>0) { researchRejectedStands.clear(); nextResearchRouteAt=0; }
        if(researchDesk==null || !desks.contains(researchDesk)
                || !canUse(level,researchDesk) && !standingSpotUsable(level,town,researchStand,researchDesk)) {
            researchDesk=null; researchStand=null;
            // Navigation caches the active path: discard it before probing a changed work approach.
            getNavigation().stop();
            var view=standingView(level,town);
            for(BlockPos desk:desks.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).limit(4).toList()) {
                if(canUse(level,desk)) { researchDesk=desk; break; }
                for(BlockPos stand:CitizenReach.stands(view,desk,position(),getEyeHeight())) {
                    if(!standingSpotUsable(level,town,stand,desk)) continue;
                    if(researchReachableStand(stand)) { researchDesk=desk; researchStand=stand; break; }
                    if(reachBudget.deferred()) { activity="Checking a route to the research lectern"; return; }
                }
                if(researchDesk!=null) break;
            }
            if(researchDesk==null) {
                nextResearchRouteAt=level.getGameTime()+100;
                researchBlocked(level,desks);
                return;
            }
        }
        if(!canUse(level,researchDesk)) {
            activity="Walking to the research lectern";
            researchState=Research.Status.waiting("Researcher is walking to the lectern.");
            // A periodic request for the same target otherwise reuses the path from before a wall was built.
            if(level.getGameTime()>=nextPathAt) getNavigation().stop();
            boolean moving=walk(researchStand,0.65,0);
            var path=getNavigation().getPath();
            if(onGround() && (!moving || path!=null && !path.canReach() && !beyondOneRoute(researchStand))) {
                researchRejectedStands.add(researchStand); researchDesk=null; researchStand=null;
                getNavigation().stop();
                researchState=Research.Status.waiting("Researcher is checking another route to the lectern.");
            }
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(researchDesk.getX()+0.5,researchDesk.getY()+0.8,researchDesk.getZ()+0.5);
        activity=Research.project(town)==null ? "Writing research scrolls at the lectern" : "Researching "+Research.progress(town);
        WorkFeedback.pulse(level,this,researchDesk,WorkFeedback.RESEARCHING);
        if(Research.work(level,town,station,researchDesk)) {
            clearBlockedJob();
            researchState=Research.Status.working();
            if(level.getGameTime()%200==0) gainExperience(StructureRole.RESEARCHER,1);
        } else researchState=Research.Status.waiting("Waiting for the next researcher work step.");
    }
    /** Keep rejected approaches for the complete bounded search, then retry after a short pause. */
    private boolean researchReachableStand(BlockPos stand) {
        if(stand.equals(blockPosition()) || beyondOneRoute(stand)) return true;
        if(researchRejectedStands.contains(stand)) return false;
        boolean reachable=reachBudget.check(() -> {
            var path=getNavigation().createPath(stand,0);
            return path!=null && path.canReach();
        });
        if(!reachable && !reachBudget.deferred()) researchRejectedStands.add(stand);
        return reachable;
    }
    private void researchBlocked(ServerLevel level,List<BlockPos> desks) {
        activity=desks.isEmpty() ? "Needs a lectern within the Researcher Station's range" : "Cannot reach the research lectern";
        researchState=Research.Status.paused(desks.isEmpty() ? "No lectern in range. Place one beside the Researcher Station."
                : "Researcher cannot reach the lectern. Clear a route and standing space.");
        getNavigation().stop();
        if(!desks.isEmpty() && onGround()) failedJobPath(level);
    }
    private void enchanter(ServerLevel level,Settlement town,Station station) {
        eatFrom(List.of(cargo));
        if(!enchantItem.isEmpty() && enchantDone) { deliverEnchanted(level,town,station); return; }
        if(level.getGameTime()<nextEnchantAt) return;
        unenchantable.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        List<BlockPos> tables=SettlementService.enchantingTables(level,town,station);
        if(enchantTable==null || !tables.contains(enchantTable)
                || !canUse(level,enchantTable) && !standingSpotUsable(level,town,enchantStand,enchantTable)) {
            if(!chooseEnchantingApproach(level,town,tables)) {
                if(reachBudget.deferred()) { activity="Looking for a reachable enchanting table"; return; }
                activity=tables.isEmpty() ? "Needs an enchanting table within "+station.radius()+" blocks of the Enchanter Station" : "Cannot reach the enchanting table";
                nextEnchantAt=level.getGameTime()+100; return;
            }
            pathTicks=0;
        }
        int cap=Config.ENCHANTER_MAX_LEVEL.get();
        if((enchantItem.isEmpty() || lapisCarried()<Enchanting.lapisCost(enchantLevel>0 ? enchantLevel : cap))
                && !gatherForEnchanting(level,town,station,cap)) return;
        String name=enchantItem.getHoverName().getString();
        if(!canUse(level,enchantTable)) {
            activity="Carrying "+name+" to the enchanting table"; pathTicks+=10;
            if(!walk(enchantStand,0.65,0) && onGround() || pathTicks>1200) {
                if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(enchantStand,level.getGameTime()+1200);
                pauseStation(level,station.position(),200); releaseWork(level);
            }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        getLookControl().setLookAt(enchantTable.getX()+0.5,enchantTable.getY()+0.75,enchantTable.getZ()+0.5);
        if(enchantLevel<=0) enchantLevel=Enchanting.level(getRandom(),Enchanting.power(level,enchantTable),enchantItem,cap);
        if(enchantLevel<=0) {
            unenchantable.put(enchantItem.getItem(),level.getGameTime()+12000);
            enchantDone=true; activity="Nothing can enchant "+name; return;
        }
        int duration=effort(Enchanting.ticks(enchantItem,Config.ENCHANT_MINUTES.get()));
        enchantTicks=Math.min(duration,enchantTicks+10);
        WorkFeedback.pulse(level,this,enchantTable,WorkFeedback.ENCHANTING);
        if(enchantTicks<duration) {
            int minutes=(duration-enchantTicks+Enchanting.TICKS_PER_MINUTE-1)/Enchanting.TICKS_PER_MINUTE;
            activity="Enchanting "+name+" at level "+enchantLevel+": "+enchantTicks*100/duration+"% done, about "+minutes+" min left";
            return;
        }
        int lapis=Enchanting.lapisCost(enchantLevel);
        if(lapisCarried()<lapis) { activity="Needs "+lapis+" lapis lazuli to finish "+name; return; }
        ItemStack result=Enchanting.enchant(level.registryAccess(),getRandom(),enchantItem,enchantLevel);
        if(result.isEmpty()) {
            unenchantable.put(enchantItem.getItem(),level.getGameTime()+12000);
            enchantDone=true; activity="No enchantment fits "+name; return;
        }
        for(int spent=0;spent<lapis;spent++) InventoryOps.takeOne(List.of(cargo),Enchanting::lapis);
        enchantItem=result; enchantDone=true;
        swing(InteractionHand.MAIN_HAND);
        playSound(SoundEvents.ENCHANTMENT_TABLE_USE,1.0F,1.0F);
        activity="Enchanted "+result.getHoverName().getString();
        gainExperience(StructureRole.ENCHANTER,3);
    }
    /** Collect an item and lapis from this station's courier-supplied barrels. */
    private boolean gatherForEnchanting(ServerLevel level,Settlement town,Station station,int cap) {
        Predicate<ItemStack> skipped=stack -> unenchantable.containsKey(stack.getItem());
        int needed=Enchanting.lapisCost(enchantLevel>0 ? enchantLevel : cap);
        boolean wantItem=enchantItem.isEmpty(),wantLapis=lapisCarried()<needed;
        BlockPos barrel=jobBarrel(level,town,station);
        List<Container> local=barrel==null ? List.of() : SettlementService.jobStorage(level,town,station);
        if(barrel==null) return false;
        if(wantItem && !Enchanting.waiting(local,skipped)) { activity="Waiting for unenchanted gear or books in my barrel"; nextEnchantAt=level.getGameTime()+200; return false; }
        if(wantLapis && lapisCarried()+InventoryOps.count(local,Enchanting::lapis)<needed) { activity="Waiting for "+needed+" lapis in my barrel"; nextEnchantAt=level.getGameTime()+200; return false; }
        List<Container> source=local;
        if(!visitStorage(level,town,StructureRole.ENCHANTER,barrel,source,false)) return false;
        if(wantItem) {
            ItemStack next=Enchanting.takeNext(source,skipped);
            if(!next.isEmpty()) { enchantItem=next; enchantTicks=0; enchantLevel=0; enchantDone=false; }
        }
        for(int count=lapisCarried();count<Enchanting.LAPIS_CARRY;count++) {
            ItemStack lapis=InventoryOps.takeOne(source,Enchanting::lapis);
            if(lapis.isEmpty()) break;
            cargo.offer(lapis);
        }
        return !enchantItem.isEmpty() && lapisCarried()>=Enchanting.lapisCost(enchantLevel>0 ? enchantLevel : cap);
    }
    /** Bring a finished item to this station's barrels when goods may stay there (see {@link #dropOff}), else to the warehouse. */
    private void deliverEnchanted(ServerLevel level,Settlement town,Station station) {
        String name=enchantItem.getHoverName().getString();
        BlockPos barrel=jobBarrel(level,town,station);
        List<Container> local=barrel==null ? List.of() : SettlementService.jobStorage(level,town,station);
        boolean toBarrel=barrel!=null && dropOff(level,town,local);
        BlockPos depot=barrel;
        if(depot==null) { nextEnchantAt=level.getGameTime()+100; return; }
        if(!canUse(level,depot)) {
            activity="Delivering the finished "+name; pathTicks+=10;
            if(!walk(depot) && onGround() || pathTicks>1200) {
                if(toBarrel && failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(barrel,level.getGameTime()+1200);
                pathTicks=0; nextEnchantAt=level.getGameTime()+100;
            }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        ItemStack rest=enchantItem;
        for(Container container:local) rest=InventoryOps.insert(container,rest);
        enchantItem=rest;
        if(!rest.isEmpty()) { activity="Storage is full; holding the finished "+name; nextEnchantAt=level.getGameTime()+100; return; }
        enchantTicks=0; enchantLevel=0; enchantDone=false;
        swing(InteractionHand.MAIN_HAND);
        activity="Delivered the finished "+name;
    }
    /** The item an enchanter is working on, or empty. */
    public ItemStack enchanting() { return enchantItem; }
    /** The level rolled for that item, or zero before it reaches the table. */
    public int enchantLevel() { return enchantLevel; }
    /** Work done on that item, from 0 to 1. */
    public float enchantProgress() {
        if(enchantItem.isEmpty()) return 0F;
        return enchantDone ? 1F : enchantTicks/(float)Math.max(1,Enchanting.ticks(enchantItem,Config.ENCHANT_MINUTES.get()));
    }
    /** The citizen's own job, kept while it sleeps or runs errands, or "none". */
    public String job() {
        if(!(level() instanceof ServerLevel server) || town(server)==null) return "none";
        Station station=homeStation(town(server));
        return station==null ? "none" : station.role().id();
    }
    public int tradeCargoCount() { return tradeShipment.items().stream().mapToInt(ItemStack::getCount).sum()+tradeShipment.rewardItems().stream().mapToInt(ItemStack::getCount).sum(); }
    /** Town positions remain known across chunk boundaries; the carrier physically visits each storage station. */
    private BlockPos tradeWarehouse(Settlement town) {
        return town.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE)
                .min(Comparator.comparingDouble(s -> s.position().distSqr(blockPosition()))).map(Station::position).orElse(null);
    }
    private void tradeNote(Settlement home,String message) { activity=message; home.trading.status=message; }
    private boolean tradeArrive(ServerLevel level,Settlement home,BlockPos destination) {
        if(canUse(level,destination)) { getNavigation().stop(); tradeNavigation.reset(); return true; }
        if(!SquadService.convoyReady(level,home,this)) { getNavigation().stop(); tradeNote(home,"Waiting for the convoy escort to regroup"); return false; }
        lastWalkTick=tickCount;
        // A leg that is only being planned again is not a blockage; report one after fifteen seconds without progress.
        if(!tradeNavigation.walk(level,this,destination,0.75) && tradeNavigation.stuck(level.getGameTime()))
            tradeNote(home,"Trade route blocked near "+blockPosition().getX()+", "+blockPosition().getZ()+": clear a walkable path, bridge the water or build a road");
        return false;
    }
    /** A saved itinerary, with isolated goods that neither meals nor another job can consume. */
    private void trader(ServerLevel level,Settlement home,Station checkpoint) {
        if(!TradeChunks.keep(level,home,blockPosition())) { tradeNote(home,"Waiting for a server trader slot"); return; }
        boolean changed=!getUUID().equals(home.trading.runner) || home.trading.runnerPos==null
                || (Math.floorDiv(home.trading.runnerPos.getX(),16)!=Math.floorDiv(blockPosition().getX(),16) || Math.floorDiv(home.trading.runnerPos.getZ(),16)!=Math.floorDiv(blockPosition().getZ(),16));
        home.trading.runner=getUUID(); home.trading.runnerPos=blockPosition().immutable();
        if(changed) SettlementData.get(level).setDirty();
        if(checkpoint!=null) SettlementService.workers(level).claim(checkpoint.position(),getUUID(),level.getGameTime(),200,1);
        if(cargo.isOpen()) { getNavigation().stop(); tradeNote(home,"Waiting while my inventory is open"); return; }
        eatFrom(List.of(cargo));
        if(level.getGameTime()<nextTradeAt) return;
        Settlement destination=tradeShipment.destination==null ? null : SettlementData.get(level).byId(tradeShipment.destination);
        Station arrival=TradeRoutes.checkpoint(destination);
        boolean broken=checkpoint==null || level.hasChunkAt(checkpoint.position()) && !SettlementService.active(level,checkpoint)
                || !TradeRoutes.agreed(home,destination) || arrival==null
                || level.hasChunkAt(arrival.position()) && !SettlementService.active(level,arrival);
        if(tradeShipment.travelling() && !List.of("return","home").contains(tradeShipment.stage) && broken) {
            if(destination!=null) destination.campaign.incoming.remove(getUUID());
            tradeShipment.stage="return"; tradeNavigation.reset();
        }
        switch(tradeShipment.stage) {
            case "idle" -> {
                destination=TradeRoutes.partner(level,home);
                if(destination==null || !TradeRoutes.canDepart(level,home,destination)) { home.campaign.routeCursor++; tradeNote(home,"Waiting for a connected, unpaused route"); releaseWork(level); nextTradeAt=level.getGameTime()+100; return; }
                BlockPos warehouse=tradeWarehouse(home);
                if(warehouse==null) { tradeNote(home,"Needs a warehouse with storage"); nextTradeAt=level.getGameTime()+100; return; }
                tradeNote(home,"Collecting exports from the warehouse");
                if(!tradeArrive(level,home,warehouse)) return;
                eatFrom(SettlementService.storageAt(level,home,warehouse));
                var stock=SettlementService.storageAt(level,home,warehouse);
                int rations=InventoryOps.count(List.of(cargo),this::food);
                for(int n=rations;n<FoodSharing.spareLimit(InventoryOps.count(stock,this::food),home.citizens.size());n++) {
                    ItemStack ration=InventoryOps.takeOne(stock,this::food);
                    if(ration.isEmpty()) break;
                    cargo.offer(ration);
                }
                BlockPos targetWarehouse=tradeWarehouse(destination);
                if(targetWarehouse!=null) SupplyRequests.snapshotLoaded(level,destination);
                boolean depot=home.campaign.projects.contains("depot"); tradeShipment.capacity=TradeSettings.MAX_EXPORTS*(depot ? 2 : 1);
                int moved=PlayerContracts.load(level,home,destination,SettlementService.storageAt(level,home,warehouse),SupplyRequests.policy(home,destination,depot),tradeShipment,depot);
                if(moved==0) { home.campaign.routeCursor++; SettlementData.get(level).setDirty(); tradeNote(home,"Waiting for requested goods above the home reserves; checking another route next"); nextTradeAt=level.getGameTime()+200; return; }
                tradeShipment.destination=destination.id; tradeShipment.stage="checkpoint"; tradeNavigation.reset();
                destination.campaign.incoming.put(getUUID(),CampaignContracts.counts(tradeShipment));
                CampaignService.journal(level,home,"Shipment departed for "+destination.name+" with "+moved+" items.");
                WWMC.LOGGER.info("[WWMC] [trade] Carrier {} departed {} for {} with {} items",getUUID(),home.id,destination.id,moved);
            }
            case "checkpoint" -> {
                tradeNote(home,"Taking "+tradeCargoCount()+" items to the home checkpoint");
                if(tradeArrive(level,home,checkpoint.position())) { tradeShipment.stage="outbound"; tradeNavigation.reset(); }
            }
            case "outbound" -> {
                tradeNote(home,"Travelling to "+destination.name+" with "+tradeCargoCount()+" items");
                if(tradeArrive(level,home,arrival.position())) { tradeShipment.stage="deliver"; tradeNavigation.reset(); }
            }
            case "deliver" -> {
                BlockPos warehouse=tradeWarehouse(destination);
                if(warehouse==null) { tradeNote(home,"Destination needs warehouse storage; cargo retained"); nextTradeAt=level.getGameTime()+100; return; }
                tradeNote(home,"Delivering to "+destination.name+"'s warehouse");
                if(!tradeArrive(level,home,warehouse)) return;
                var before=CampaignContracts.counts(tradeShipment);
                var plainBefore=PlayerContracts.plainCounts(tradeShipment);
                int moved=TradeGoods.unload(tradeShipment,SettlementService.storageAt(level,destination,warehouse));
                var remaining=CampaignContracts.counts(tradeShipment);
                if(remaining.isEmpty()) destination.campaign.incoming.remove(getUUID()); else destination.campaign.incoming.put(getUUID(),remaining);
                before.replaceAll((key,count) -> count-remaining.getOrDefault(key,0)); before.values().removeIf(count -> count<=0);
                var plainRemaining=PlayerContracts.plainCounts(tradeShipment);
                plainBefore.replaceAll((key,count) -> count-plainRemaining.getOrDefault(key,0)); plainBefore.values().removeIf(count -> count<=0);
                if(moved>0) {
                    CampaignContracts.delivered(level,home,destination,before,tradeShipment.rewards);
                    PlayerContracts.delivered(level,home,destination,plainBefore);
                    CampaignService.journal(level,destination,"Received "+moved+" items from "+home.name+".");
                    WWMC.LOGGER.info("[WWMC] [trade] Carrier {} delivered {} items from {} to {}",getUUID(),moved,home.id,destination.id);
                    SupplyRequests.snapshotLoaded(level,destination);
                }
                home.trading.delivered+=moved;
                if(moved>0) gainExperience(StructureRole.TRADER,3);
                if(moved>0 && destination.trading.npc) destination.trading.relations.merge(home.owner,moved,(a,b) -> Math.min(1000,a+b));
                if(moved>0) SettlementData.get(level).setDirty();
                if(tradeShipment.isEmpty()) { tradeShipment.stage="return"; tradeNavigation.reset(); }
                else { tradeNote(home,"Destination storage is full; keeping "+tradeCargoCount()+" items"); nextTradeAt=level.getGameTime()+100; }
            }
            case "return" -> {
                tradeNote(home,"Returning to "+home.name+(tradeShipment.isEmpty() ? "" : " with undelivered goods"));
                BlockPos rally=checkpoint==null ? home.center : checkpoint.position();
                if(!handNear(rally) && !tradeArrive(level,home,rally)) return;
                tradeShipment.stage="home"; tradeNavigation.reset();
            }
            case "home" -> {
                if(!tradeShipment.isEmpty() || !tradeShipment.rewards.isEmpty()) {
                    BlockPos warehouse=tradeWarehouse(home);
                    if(warehouse==null) { tradeNote(home,"Home needs storage for the returned goods"); return; }
                    if(!tradeArrive(level,home,warehouse)) return;
                    TradeGoods.unload(tradeShipment,SettlementService.storageAt(level,home,warehouse));
                    TradeGoods.unload(tradeShipment.rewards,SettlementService.storageAt(level,home,warehouse));
                    if(!tradeShipment.isEmpty() || !tradeShipment.rewards.isEmpty()) { tradeNote(home,"Home storage is full; returned goods and contract rewards are safe in the trade load"); nextTradeAt=level.getGameTime()+100; return; }
                }
                tradeShipment.finish();
                home.campaign.routeCursor++;
                if(!TradeRoutes.canDepart(level,home)) { home.trading.runner=null; home.trading.runnerPos=null; }
                tradeNavigation.reset(); nextTradeAt=level.getGameTime()+200; SettlementData.get(level).setDirty();
                tradeNote(home,"Returned; preparing the next trip");
            }
            default -> tradeShipment.stage="return";
        }
    }
    private void work(ServerLevel level) {
        if(isBaby()) return;
        reachBudget.reset();
        failedTargets.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        Settlement town=town(level);
        if(town==null) { activity="Settlement unavailable"; return; }
        if(HospitalCare.needsCare(town,this)) return;
        if(SquadService.act(level,town,this)) return;
        // The checkpoint may be unloaded behind the carrier. Its saved itinerary owns the job until it returns.
        if(tradeShipment.travelling()) { leaveBed(); trader(level,town,TradeRoutes.checkpoint(town)); return; }
        // An open guard post draws the first citizen whose own job matters less, at once rather than at the next half-minute look.
        if(!isGuard() && guardVacancy(level,town)) { nextPromotionAt=0; releaseWork(level); }
        if(isGuard()) {
            Station empty=GuardService.uncovered(level,town);
            if(empty!=null && !empty.position().equals(workplace) && town.jobs.assigned(workplace)>1
                    && SettlementService.workers(level).claim(empty.position(),getUUID(),level.getGameTime(),200,SettlementService.workerLimit(town,empty))) {
                releaseWork(level); workplace=empty.position();
                town.jobs.assign(getUUID(),workplace); SettlementData.get(level).setDirty();
            }
        }
        var book=SettlementService.reservations(level);
        Station station=workplace==null ? null : town.station(workplace);
        BlockPos home=town.jobs.home(getUUID());
        // A citizen from before job assignments keeps the station it was working at.
        if(home==null && station!=null && keepsPlaceToTake(level,town,station)) {
            town.jobs.assign(getUUID(),workplace); home=workplace; SettlementData.get(level).setDirty();
        }
        // A promotion, a job switched off or a removed station ends work here at once.
        if(station!=null && !workplace.equals(home)) station=null;
        // Between tasks, or between a vein's yields, take an open place in a more important job.
        if(station!=null && (target==null || action==Action.VEIN) && promotionDue(level,town)) {
            Station better=promotion(level,town,station);
            if(better!=null) { town.jobs.assign(getUUID(),better.position()); SettlementData.get(level).setDirty(); station=null; }
        }
        if(station==null || !SettlementService.active(level,station)
                || !SettlementService.workers(level).claim(workplace,getUUID(),level.getGameTime(),200,SettlementService.workerLimit(town,station))) {
            if(workplace!=null || target!=null) releaseWork(level);
            // Idle jobs still need meals. The search cooldown must not keep a hungry citizen away from the pantry.
            Station assigned=homeStation(town);
            if((assigned==null || assigned.role()!=StructureRole.GUARD) && wantsMeal()
                    && InventoryOps.count(List.of(cargo),this::food)==0 && level.getGameTime()>=nextFoodTripAt) {
                if(!visitPantry(level,town)) return;
                nextFoodTripAt=level.getGameTime()+200;
            }
            if(searchDelay>0) { searchDelay-=10; return; }
            station=chooseJob(level,town);
            if(station==null) {
                activity=jobNote; searchDelay=40;
                // Wait at the station rather than wherever the last errand ended, heading there even while it is beyond loaded ground.
                Station own=homeStation(town);
                if(own!=null && !night(level) && distanceToSqr(Vec3.atCenterOf(own.position()))>100) walk(own.position());
                return;
            }
            workplace=station.position();
        }
        if(cargo.isOpen() && !(station.role()==StructureRole.GUARD && DefenseService.alarmed(town))) {
            getNavigation().stop(); return;
        }
        StructureRole role=station.role();
        // Armor outside the guard job, or a tool or weapon this job does not use, also counts as a change (e.g. after a reload).
        boolean wrongKit=role!=StructureRole.GUARD && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> !getItemBySlot(slot).isEmpty())
                || gear(getMainHandItem()) && !retainSupply(getMainHandItem())
                || role!=StructureRole.BLACKSMITH && !repairItem.isEmpty()
                || role!=StructureRole.ENCHANTER && !enchantItem.isEmpty();
        if(lastRole!=null && lastRole!=role || wrongKit) changeRole(role);
        lastRole=role;
        // A guard drafted during an alarm defends first and returns old gear afterwards.
        if(returningGear && !(role==StructureRole.GUARD && DefenseService.alarmed(town)) && !returnGear(level,town)) return;
        if(station.role()==StructureRole.GUARD) { guard(level,town,station); return; }
        if(station.role()==StructureRole.HOSPITAL) { HospitalCare.medic(level,town,station,this); return; }
        if(station.role()==StructureRole.BLACKSMITH) { blacksmith(level,town,station); return; }
        if(station.role()==StructureRole.COURIER) { courier(level,town,station); return; }
        if(station.role()==StructureRole.TRADER) { trader(level,town,station); return; }
        if(station.role()==StructureRole.ENCHANTER) { enchanter(level,town,station); return; }
        if(station.role()==StructureRole.RESEARCHER) { researcher(level,town,station); return; }
        if(town.campaign.parent!=null && InventoryOps.count(SettlementService.townStorage(level,town),FoodHealing::food)==0 && InventoryOps.count(List.of(cargo),FoodHealing::food)==0) {
            getNavigation().stop(); activity="Outpost awaiting a food shipment from home"; return;
        }
        useLocalSupplies(station.role());
        if(wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0
                && level.getGameTime()>=nextFoodTripAt) {
            if(!visitPantry(level,town)) return;
            nextFoodTripAt=level.getGameTime()+200;
        }
        // Deliver only full loads; use personal supplies before returning for replacements.
        if(deliverCargo() || InventoryOps.count(List.of(cargo),this::food)>FoodSharing.PERSONAL_LIMIT
                || mealTicks<=0 && !station.role().foodJob() && station.role()!=StructureRole.CRAFTSMAN) {
            if(!visitDepot(level,town,station)) return;
        }
        if(station.role().animalJob()) {
            if(!properTool(station.role()) && !visitDepot(level,town,station)) return;
            if(!properTool(station.role())) return;
            animalWork.tick(level,town,station,this); return;
        }
        if(station.role()==StructureRole.CRAFTSMAN) { craftsman(level,town,station); return; }
        if(station.role().processes()) { process(level,town,station); return; }
        if(target==null) {
            if(searchDelay>0) { searchDelay-=10; return; }
            // Stationary jobs, a vein mine among them, first walk to their station, then look for work beside it.
            boolean stationary=!station.role().excavates() || station.role()==StructureRole.MINE && OreVeins.find(level,town,station)!=null;
            if(stationary && !handNear(station.position())) {
                activity="Returning to the "+station.role().id()+" worksite"; pathTicks+=10;
                if(!walk(station.position()) || pathTicks>1200) {
                    pauseStation(level,station.position(),200); releaseWork(level);
                }
                return;
            }
            target=findTarget(level,town,station);
            if(target==null) {
                if(reachBudget.deferred()) { activity="Checking accessible work nearby"; return; }
                if(cargo.hasDeliverable(this::retainSupply,this::food)) { visitDepot(level,town,station); return; }
                activity=idleReason(level,town,station);
                pauseStation(level,station.position(),200); releaseWork(level); searchDelay=20; return;
            }
            pathTicks=0; blindTicks=0;
        }
        if(!validTarget(level,town,station) || !book.claimAll(targetLease==null ? List.of(target) : List.of(target,targetLease),getUUID(),level.getGameTime(),200)) {
            cancelTarget(level,false); return;
        }
        useLocalSupplies(station.role());
        if(!properTool(station.role()) || needsSupply()) { if(!visitDepot(level,town,station)) return; }
        if(action==Action.FELL) {
            BlockPos leaf=blockingLeaf(level,town);
            if(leaf!=null) {
                getNavigation().stop(); activity="Clearing natural leaves to reach the trunk";
                if(!leaf.equals(clearingLeaf)) { clearingLeaf=leaf; workProgress=0; }
                workProgress+=10;
                WorkFeedback.pulse(level,this,leaf,WorkFeedback.CHOPPING);
                if(workProgress>=20) {
                    swing(InteractionHand.MAIN_HAND);
                    var drops=ForestryService.clearLeaf(level,town,station,forestTask.tree(),leaf,this);
                    if(drops==null) { cancelTarget(level,true); return; }
                    storeDrops(level,drops); workProgress=0; clearingLeaf=null;
                }
                return;
            }
        }
        clearingLeaf=null;
        BlockPos approach=excavation==null ? (workStand==null ? target : workStand) : excavation.stand();
        BlockPos visibleAt=action==Action.PLANT ? target.below() : excavation!=null && excavation.remote() ? station.position()
                : target;
        BlockPos touch=excavation!=null && excavation.remote() ? station.position() : target;
        if(!handNear(touch) || !canUse(level,visibleAt)) {
            activity=action==Action.PLANT ? "Walking to plant saplings" : "Walking to "+station.role().id()+" work"; pathTicks+=10;
            // Standing beside the work without a clear view will not fix itself; give up sooner than a long walk.
            if(handNear(touch)) blindTicks+=10;
            // A chosen standing spot sees the work from that block, not from the one beside it.
            boolean onStand=excavation==null && workStand!=null;
            if(!walk(approach,0.65,onStand ? 0 : 1) || pathTicks>1200 || blindTicks>100) {
                if(action==Action.EXCAVATE && excavation.quarry() && !excavation.remote()) {
                    // No way into the pit from here: keep the quarry moving from its control block instead.
                    SettlementService.reservations(level).release(excavation.lease(),getUUID());
                    excavation=excavation.fromControlBlock(station.position()); targetLease=excavation.lease(); pathTicks=0; blindTicks=0;
                } else cancelTarget(level,true);
            }
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(visibleAt.getX()+0.5,visibleAt.getY()+0.5,visibleAt.getZ()+0.5);
        activity=switch(action) {
            case FELL -> "Felling a whole natural tree";
            case PLANT -> "Planting saplings";
            case SUPPORT -> excavation.quarry() ? "Rebuilding a quarry step" : "Supporting the tunnel floor";
            case EXCAVATE -> excavation.remote() ? "Operating the quarry from its control block" : excavation.quarry() ? "Quarrying" : "Excavating a tunnel";
            case HARVEST -> "Harvesting crops";
            case GATHER -> "Gathering "+level.getBlockState(target).getBlock().getName().getString();
            case CAVE -> "Mining accessible cave ore";
            case VEIN -> "Mining the "+OreVeins.name(level.getBlockState(target))+" vein";
        };
        if(action==Action.VEIN && level.getGameTime()<OreVeins.readyAt(level,target)) {
            workProgress=0;
            activity="Waiting for the "+OreVeins.name(level.getBlockState(target))+" vein to replenish ("
                    +(OreVeins.readyAt(level,target)-level.getGameTime()+19)/20+"s)";
            return;
        }
        WorkFeedback.pulse(level,this,visibleAt,switch(action) {
            case FELL -> WorkFeedback.CHOPPING;
            case HARVEST,PLANT -> WorkFeedback.FARMING;
            case SUPPORT -> WorkFeedback.CRAFTING;
            default -> WorkFeedback.MINING;
        });
        workProgress+=workStep();
        // Stone yields to a pickaxe in about a second; harder blocks and weaker tools take longer.
        int required=action==Action.EXCAVATE || action==Action.CAVE || action==Action.VEIN ? ExcavationService.breakTicks(level,target,getMainHandItem())
                : action==Action.SUPPORT ? 20 : Config.WORK_TICKS.get();
        if(workProgress>=Specialization.ticks(town,station.role(),required)) harvest(level,town,station);
    }
    private final class WorkGoal extends Goal {
        WorkGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() {
            if(isBaby() || !(level() instanceof ServerLevel l) || town(l)==null) return false;
            // Civilians stop work during an alarm; a vacant guard post still draws a volunteer.
            return tradeShipment.travelling() || SquadService.assigned(town(l),getUUID()) || HospitalCare.needsCare(town(l),CitizenEntity.this)
                    || isGuard() || guardVacancy(l,town(l)) || !night(l) && !DefenseService.alarmed(town(l));
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { if(level() instanceof ServerLevel l && WorkCadence.due(l.getGameTime(),getId(),10)) work(l); }
        @Override public void stop() { if(level() instanceof ServerLevel l) releaseWork(l); }
    }
    /** Children eat, play near their home and use the same night rest and shelter goals as adults. */
    private final class ChildGoal extends Goal {
        private long nextPlay;
        ChildGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() { return isBaby() && level() instanceof ServerLevel l && town(l)!=null && !night(l)
                && !DefenseService.alarmed(town(l)) && !HospitalCare.needsCare(town(l),CitizenEntity.this); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(!(level() instanceof ServerLevel l) || !WorkCadence.due(l.getGameTime(),getId(),20)) return;
            Settlement town=town(l); if(town==null) return;
            reachBudget.reset(); activity="Playing near home";
            if(wantsMeal() && l.getGameTime()>=nextFoodTripAt && !visitPantry(l,town)) return;
            if(l.getGameTime()<nextPlay || !getNavigation().isDone()) return;
            nextPlay=l.getGameTime()+100+getRandom().nextInt(100);
            var beds=SettlementService.housingBeds(l,town);
            if(homeBed==null || !beds.contains(homeBed)) homeBed=beds.stream().min(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p)))).orElse(null);
            if(homeBed==null) { activity="Needs a housing bed"; return; }
            for(int i=0;i<6;i++) {
                BlockPos play=homeBed.offset(getRandom().nextInt(9)-4,getRandom().nextInt(3)-1,getRandom().nextInt(9)-4);
                if(!PopulationGrowth.safe(l,town,play)) continue;
                var path=getNavigation().createPath(play,0);
                if(path!=null && path.canReach()) { getNavigation().moveTo(path,0.6); break; }
            }
        }
        @Override public void stop() { getNavigation().stop(); }
    }
    /** Fear sends civilians to a real housing bed, instead of choosing a random escape point outside their home. */
    private final class ShelterGoal extends Goal {
        private BlockPos refuge,bed;
        private long retryAt;
        ShelterGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() {
            if(!(level() instanceof ServerLevel l)) return false;
            Settlement town=town(l);
            if(town==null || recovering || tradeShipment.travelling() || SquadService.assigned(town,getUUID())
                    || isGuard()) return false;
            if(DefenseService.alarmed(town)) fearUntil=l.getGameTime()+20;
            else if(inCombat()) fearUntil=l.getGameTime()+STUCK_TICKS;
            return l.getGameTime()<fearUntil;
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { refuge=null; bed=null; retryAt=0; sheltering=true; }
        @Override public void tick() {
            if(!(level() instanceof ServerLevel level) || !WorkCadence.due(level.getGameTime(),getId(),20)) return;
            Settlement town=town(level); if(town==null) return;
            var beds=new ArrayList<>(SettlementService.housingBeds(level,town));
            var book=SettlementService.reservations(level);
            if(bed!=null && (!beds.contains(bed) || !book.claim(bed,getUUID(),level.getGameTime(),200))) {
                leaveBed(); bed=null; refuge=null;
            }
            if(bed==null && level.getGameTime()>=retryAt) {
                retryAt=level.getGameTime()+200;
                beds.sort(Comparator.comparingDouble(p -> p.equals(homeBed) ? -1 : distanceToSqr(Vec3.atCenterOf(p))));
                for(BlockPos candidate:beds.stream().limit(8).toList()) {
                    if(level.getBlockState(candidate).getValue(BedBlock.OCCUPIED) && !candidate.equals(sleepingBed)
                            || !book.claim(candidate,getUUID(),level.getGameTime(),200)) continue;
                    BlockPos stand=null;
                    int probes=0;
                    for(BlockPos spot:CitizenReach.stands(standingView(level,town),candidate,position(),getEyeHeight())) {
                        if(spot.distSqr(candidate)>4 || !CitizenReach.visible(level,Vec3.atBottomCenterOf(spot).add(0,getEyeHeight(),0),candidate)) continue;
                        if(++probes>2) break;
                        var path=getNavigation().createPath(spot,0);
                        if(spot.equals(blockPosition()) || path!=null && (path.canReach() || beyondOneRoute(spot))) { stand=spot; break; }
                    }
                    if(stand!=null) { bed=candidate; sleepingBed=candidate; homeBed=candidate; refuge=stand; break; }
                    book.release(candidate,getUUID());
                }
            }
            if(bed!=null) {
                if(distanceToSqr(Vec3.atCenterOf(bed))<=4 && visible(level,bed)) {
                    getNavigation().stop();
                    if(night(level) && !isSleeping()) startSleeping(bed);
                    activity="Sheltering at my bed until it is safe"; return;
                }
                if(walk(refuge,1.0,0)) { activity="Running home to my bed for shelter"; return; }
                book.release(bed,getUUID()); bed=null; refuge=null;
            }
            // Towns without an accessible bed still have a housing or banner rally point.
            BlockPos fallback=DefenseService.refuge(level,town,blockPosition());
            if(distanceToSqr(Vec3.atCenterOf(fallback))>9 && walk(fallback,0.9)) activity="Running for cover; needs a reachable housing bed";
            else { getNavigation().stop(); activity="Taking cover; needs a reachable housing bed"; }
        }
        @Override public void stop() { sheltering=false; refuge=null; bed=null; leaveBed(); getNavigation().stop(); }
    }
    private final class RestGoal extends Goal {
        RestGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() { return !tradeShipment.travelling() && level() instanceof ServerLevel l && town(l)!=null && night(l)
                && !SquadService.assigned(town(l),getUUID()) && !HospitalCare.needsCare(town(l),CitizenEntity.this) && !isGuard() && !guardVacancy(l,town(l)); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(!(level() instanceof ServerLevel level) || !WorkCadence.due(level.getGameTime(),getId(),20)) return;
            Settlement town=town(level); if(town==null) return;
            rest(level,town);
        }
        @Override public void stop() { leaveBed(); }
    }
    private void rest(ServerLevel level,Settlement town) {
        if(HospitalCare.needsCare(town,this)) return;
        if(!cargo.isOpen()) eatFrom(List.of(cargo));
        var beds=new ArrayList<>(SettlementService.housingBeds(level,town));
        beds.sort(Comparator.comparingDouble(p -> p.equals(homeBed) ? -1 : distanceToSqr(Vec3.atCenterOf(p))));
        var book=SettlementService.reservations(level);
        if(sleepingBed!=null && (!beds.contains(sleepingBed) || !book.claim(sleepingBed,getUUID(),level.getGameTime(),200))) leaveBed();
        if(sleepingBed==null) for(BlockPos bed:beds) {
            if(!level.getBlockState(bed).getValue(BedBlock.OCCUPIED) && book.claim(bed,getUUID(),level.getGameTime(),200)) { sleepingBed=bed; homeBed=bed; break; }
        }
        if(sleepingBed==null) { activity="Needs a loaded housing bed"; return; }
        activity="Resting";
        if(distanceToSqr(Vec3.atCenterOf(sleepingBed))<=4) { getNavigation().stop(); if(!isSleeping()) startSleeping(sleepingBed); }
        else walk(sleepingBed);
    }
    private void leaveBed() {
        if(hospitalBed!=null) return;
        if(isSleeping()) stopSleeping();
        if(level() instanceof ServerLevel l && sleepingBed!=null) SettlementService.reservations(l).release(sleepingBed,getUUID());
        sleepingBed=null;
    }
    /** Called synchronously when the bell rings, including for guards whose sleeping AI is paused. */
    public void wakeForAlarm() { leaveBed(); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if(settlementId!=null) output.putString("wwmc_settlement",settlementId.toString());
        if(workplace!=null) output.store("wwmc_workplace",BlockPos.CODEC,workplace);
        if(homeBed!=null) output.store("wwmc_home_bed",BlockPos.CODEC,homeBed);
        output.putInt("wwmc_meal_ticks",mealTicks);
        output.putInt("wwmc_happiness",happiness);
        output.putLong("wwmc_last_meal",lastMealAt);
        output.putInt("wwmc_healing_ticks",healingTicks);
        output.putBoolean("wwmc_recovering",recovering);
        if(hospitalBed!=null) output.store("wwmc_hospital_bed",BlockPos.CODEC,hospitalBed);
        output.putInt("wwmc_hospital_rest_ticks",hospitalRestTicks);
        output.store("wwmc_repair_item",ItemStack.OPTIONAL_CODEC,repairItem);
        output.putBoolean("wwmc_repair_delivery",repairDelivery);
        output.store("wwmc_enchant_item",ItemStack.OPTIONAL_CODEC,enchantItem);
        output.putInt("wwmc_enchant_ticks",enchantTicks);
        output.putInt("wwmc_enchant_level",enchantLevel);
        output.putBoolean("wwmc_enchant_done",enchantDone);
        if(repairStand!=null) output.putString("wwmc_repair_stand",repairStand.toString());
        if(repairSlot!=null) output.putString("wwmc_repair_slot",repairSlot.name());
        output.store("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf(),cargo.contents());
        output.store("wwmc_pending_cargo",ItemStack.OPTIONAL_CODEC.listOf(),cargo.pendingItems());
        output.store("wwmc_trade_shipment",TradeShipment.CODEC,tradeShipment);
        output.store("wwmc_experience",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,com.mojang.serialization.Codec.INT),experience);
        output.store("wwmc_recent_meals",com.mojang.serialization.Codec.STRING.listOf(),recentMeals);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        planLongRoutes();
        String id=input.getStringOr("wwmc_settlement","");
        try { settlementId=id.isEmpty() ? null : UUID.fromString(id); } catch(IllegalArgumentException e) { settlementId=null; }
        workplace=input.read("wwmc_workplace",BlockPos.CODEC).orElse(null);
        homeBed=input.read("wwmc_home_bed",BlockPos.CODEC).orElse(null);
        mealTicks=input.getIntOr("wwmc_meal_ticks",7200);
        happiness=Math.clamp(input.getIntOr("wwmc_happiness",50),0,100);
        lastMealAt=input.getLongOr("wwmc_last_meal",0L);
        healingTicks=Math.clamp(input.getIntOr("wwmc_healing_ticks",0),0,FoodHealing.COOLDOWN);
        recovering=input.getBooleanOr("wwmc_recovering",false);
        hospitalBed=input.read("wwmc_hospital_bed",BlockPos.CODEC).orElse(null);
        hospitalRestTicks=Math.clamp(input.getIntOr("wwmc_hospital_rest_ticks",0),0,HospitalCare.HEAL_TICKS-1);
        repairItem=input.read("wwmc_repair_item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        repairDelivery=input.getBooleanOr("wwmc_repair_delivery",false);
        enchantItem=input.read("wwmc_enchant_item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        enchantTicks=Math.max(0,input.getIntOr("wwmc_enchant_ticks",0));
        enchantLevel=Math.clamp(input.getIntOr("wwmc_enchant_level",0),0,30);
        enchantDone=input.getBooleanOr("wwmc_enchant_done",false);
        try { repairStand=UUID.fromString(input.getStringOr("wwmc_repair_stand","")); } catch(IllegalArgumentException e) { repairStand=null; }
        try { repairSlot=EquipmentSlot.valueOf(input.getStringOr("wwmc_repair_slot","")); } catch(IllegalArgumentException e) { repairSlot=null; }
        var stacks=input.read("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        cargo.restore(stacks,input.read("wwmc_pending_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        tradeShipment=input.read("wwmc_trade_shipment",TradeShipment.CODEC).orElseGet(TradeShipment::new);
        experience.clear();
        input.read("wwmc_experience",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,com.mojang.serialization.Codec.INT))
                .orElse(Map.of()).forEach((job,points) -> experience.put(job,Math.clamp(points,0,CitizenSkill.CAP)));
        recentMeals=new ArrayList<>(input.read("wwmc_recent_meals",com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of()));
        while(recentMeals.size()>MealVariety.REMEMBERED) recentMeals.removeFirst();
    }
    @Override public void die(DamageSource source) {
        if(level() instanceof ServerLevel level) {
            Settlement home=town(level);
            if(home!=null && source.getEntity() instanceof Player attacker) TradeRoutes.attacked(home,attacker.getUUID(),SettlementData.get(level).settlements);
            if(home!=null && getUUID().equals(home.trading.runner)) { home.trading.runner=null; home.trading.runnerPos=null; SettlementData.get(level).setDirty(); }
            if(tradeShipment.destination!=null) {
                Settlement destination=SettlementData.get(level).byId(tradeShipment.destination);
                if(destination!=null) { destination.campaign.incoming.remove(getUUID()); SettlementData.get(level).setDirty(); }
            }
            for(ItemStack stack:tradeShipment.items()) if(!stack.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),stack);
            for(ItemStack stack:tradeShipment.rewardItems()) if(!stack.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),stack);
            tradeShipment.clearContent();
            tradeShipment.rewards.clearContent();
            Containers.dropItemStack(level,getX(),getY(),getZ(),repairItem); repairItem=ItemStack.EMPTY;
            Containers.dropItemStack(level,getX(),getY(),getZ(),enchantItem); enchantItem=ItemStack.EMPTY;
            Settlement town=town(level);
            if(town!=null) {
                town.citizens.remove(getUUID()); town.citizenNames.remove(getUUID()); town.citizenPlaces.remove(getUUID()); town.jobs.release(getUUID());
                town.progress.children.remove(getUUID());
                SettlementData.get(level).setDirty();
            }
            releaseWork(level);
            Containers.dropItemStack(level,getX(),getY(),getZ(),getOffhandItem()); setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            for(int i=0;i<cargo.getContainerSize();i++) {
                Containers.dropItemStack(level,getX(),getY(),getZ(),cargo.removeItemNoUpdate(i));
            }
            for(ItemStack stack:cargo.pendingItems()) Containers.dropItemStack(level,getX(),getY(),getZ(),stack);
            cargo.restore(List.of(),List.of());
            for(EquipmentSlot slot:GuardEquipment.ARMOR) {
                Containers.dropItemStack(level,getX(),getY(),getZ(),getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
            }
            Containers.dropItemStack(level,getX(),getY(),getZ(),getMainHandItem());
            setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        super.die(source);
    }
}
