package io.github.swishhyy.wwmc.test;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Navigation, combat and shelter regressions using live citizens in a player-free server. */
public final class VillagerAIWorldTests {
    private record Fixture(ServerLevel level,BlockPos start,List<ChunkPos> chunks,Settlement town,List<CitizenEntity> citizens) {
        CitizenEntity worker(BlockPos feet,Station job) {
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level); citizen.join(town.id);
            citizen.setPos(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5); citizen.bag().offer(new ItemStack(Items.BREAD));
            town.citizens.add(citizen.getUUID()); if(job!=null) town.jobs.assign(citizen.getUUID(),job.position());
            citizens.add(citizen); level.addFreshEntity(citizen); return citizen;
        }
        void close() {
            citizens.forEach(CitizenEntity::discard); SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
            CitizenNavigationTests.releaseTicking(level,start,chunks);
        }
    }
    private static Fixture fixture(ServerLevel level,BlockPos start,Station... stations) {
        var chunks=CitizenNavigationTests.pinTicking(level,start,2);
        CitizenNavigationTests.meadow(level,start,-8,48,-16,16);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Villager AI test",start.west(4),96,List.of(),List.of(stations),"balanced");
        SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
        level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
        for(Station station:stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
        return new Fixture(level,start,chunks,town,new ArrayList<>());
    }
    private static Monster zombie(ServerLevel level,BlockPos feet) {
        var mob=(Monster)BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie")).create(level,EntitySpawnReason.EVENT);
        mob.setPos(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET)); level.addFreshEntity(mob); return mob;
    }
    private static void bed(ServerLevel level,BlockPos foot) {
        var state=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
        level.setBlockAndUpdate(foot,state.setValue(BedBlock.PART,BedPart.FOOT));
        level.setBlockAndUpdate(foot.north(),state.setValue(BedBlock.PART,BedPart.HEAD));
    }
    @GameTest(timeoutTicks=750)
    @EmptyTemplate
    @TestHolder(description="An archer carrying both a bow and sword fires real arrows without approaching melee range, spends arrows and bow durability, then approaches and uses its sword when ammunition runs out.")
    static void archerDrawSurvivesWorkTicks(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-10000));
            Station post=new Station(start.east(2),StructureRole.GUARD); var f=fixture(level,start,post);
            WorldWorkData.get(level).guardPosts.put(post.position(),new GuardPosts(post.position(),post.position(),post.position()).withRole(GuardPosts.ARCHER));
            var guard=f.worker(start.east(4),post); guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
            guard.bag().offer(new ItemStack(Items.IRON_SWORD)); guard.bag().offer(new ItemStack(Items.ARROW,24));
            var enemy=zombie(level,start.east(18)); enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); enemy.setHealth(200);
            helper.runAtTickTime(220,() -> {
                helper.assertTrue(enemy.getHealth()<200,"Archer never landed a shot: "+guard.activity());
                helper.assertTrue(guard.bag().count(Items.ARROW)<24 && guard.getMainHandItem().is(Items.BOW) && guard.getMainHandItem().getDamageValue()>0,"Ranged defense did not consume real arrows and bow durability");
                helper.assertTrue(guard.distanceTo(enemy)>GuardWeapons.BOW_MIN_RANGE,"Archer approached melee range despite ammunition");
                for(int slot=0;slot<guard.bag().getContainerSize();slot++) if(guard.bag().getItem(slot).is(Items.ARROW)) guard.bag().setItem(slot,ItemStack.EMPTY);
                float before=enemy.getHealth();
                helper.succeedWhen(() -> {
                    helper.assertTrue(guard.getMainHandItem().is(Items.IRON_SWORD) && guard.distanceTo(enemy)<5 && enemy.getHealth()<before,"Out-of-ammo archer did not fall back to melee: "+guard.activity());
                    enemy.discard(); f.close();
                });
            });
        });
    }
    @GameTest(timeoutTicks=400)
    @EmptyTemplate
    @TestHolder(description="A shield guard collects a real shield from a stand, blocks frontal damage with native shield use and durability, takes rear damage, and continues attacking between blocks.")
    static void shieldGuardUsesAndResuppliesShield(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-10200));
            Station post=new Station(start.east(10),StructureRole.GUARD); var f=fixture(level,start,post);
            WorldWorkData.get(level).guardPosts.put(post.position(),new GuardPosts(post.position(),post.position(),post.position()).withRole(GuardPosts.SHIELD));
            var guard=f.worker(post.position().west(),post); guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            var stand=new net.minecraft.world.entity.decoration.ArmorStand(level,guard.getX(),guard.getY(),guard.getZ()+1);
            ItemStack shield=new ItemStack(Items.SHIELD); shield.setDamageValue(7); stand.setItemSlot(EquipmentSlot.OFFHAND,shield); level.addFreshEntity(stand);
            helper.runAtTickTime(80,() -> {
                helper.assertTrue(guard.getOffhandItem().is(Items.SHIELD) && guard.getOffhandItem().getDamageValue()==7 && stand.getOffhandItem().isEmpty(),"Guard did not collect the actual shield from the stand: "+guard.activity());
                var enemy=zombie(level,guard.blockPosition().south(3)); enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); enemy.setHealth(200);
                helper.succeedWhen(() -> {
                    helper.assertTrue(guard.isBlocking() && guard.getTicksUsingItem()>=5 && enemy.getHealth()<190,"Shield guard did not attack and raise its shield between attacks: "+guard.activity()+", blocking="+guard.isBlocking()+", useTicks="+guard.getTicksUsingItem()+", enemyHealth="+enemy.getHealth()+", distance="+guard.distanceTo(enemy));
                    guard.setYRot(0); guard.setYHeadRot(0);
                    enemy.setPos(guard.getX(),guard.getY(),guard.getZ()+3); float health=guard.getHealth();
                    guard.hurtServer(level,level.damageSources().mobAttack(enemy),6);
                    helper.assertTrue(guard.getHealth()==health && guard.getOffhandItem().getDamageValue()>7,"A frontal hit was not blocked by the real shield");
                    enemy.setPos(guard.getX(),guard.getY(),guard.getZ()-3); guard.invulnerableTime=0;
                    guard.hurtServer(level,level.damageSources().mobAttack(enemy),6);
                    helper.assertTrue(guard.getHealth()<health,"Shield incorrectly blocked rear damage");
                    stand.discard(); enemy.discard(); f.close();
                });
            });
        });
    }
    private static void trap(ServerLevel level,BlockPos feet) {
        for(int y=0;y<3;y++) for(BlockPos side:List.of(feet.north(),feet.south(),feet.east(),feet.west()))
            level.setBlockAndUpdate(side.above(y),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(feet.above(3),Blocks.STONE.defaultBlockState());
    }

    @GameTest(timeoutTicks=700)
    @EmptyTemplate
    @TestHolder(description="A citizen crosses a narrow corridor of flowers, short grass, ferns and full-height tall grass without destroying them or walking through its stone walls.")
    static void crossesDenseVegetation(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-8200));
            var f=fixture(level,start); BlockPos end=start.east(22);
            for(int x=-2;x<=24;x++) for(int y=0;y<3;y++) for(int z:List.of(-1,1))
                level.setBlockAndUpdate(start.offset(x,y,z),Blocks.STONE.defaultBlockState());
            for(int x=1;x<22;x++) {
                BlockPos pos=start.east(x);
                if(x%4==0) {
                    level.setBlock(pos,Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.LOWER),2);
                    level.setBlock(pos.above(),Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER),2);
                } else level.setBlockAndUpdate(pos,(x%4==1 ? Blocks.DANDELION : x%4==2 ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState());
            }
            var citizen=f.worker(start,null);
            helper.runAtTickTime(5,() -> {
                var path=citizen.getNavigation().createPath(end,0);
                helper.assertTrue(path!=null && path.canReach(),"Plants prevented a complete route through the only open corridor");
                helper.succeedWhen(() -> {
                    citizen.workStandAt(end);
                    helper.assertTrue(citizen.blockPosition().distSqr(end)<2,"Citizen has not crossed vegetation: "+citizen.blockPosition());
                    for(int x=1;x<22;x++) helper.assertTrue(!level.getBlockState(start.east(x)).isAir(),"Navigation destroyed meadow plants");
                    f.close();
                });
            });
        });
    }

    @GameTest(timeoutTicks=900)
    @EmptyTemplate
    @TestHolder(description="Repeated failed job routes rescue a trapped farmer beside its station after thirty seconds, preserve the job and bag, and never teleport an equally trapped trader or an idle farmer.")
    static void rescuesBlockedWorkerButNotTraderOrIdleWorker(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-8400));
            Station farm=new Station(start.east(24),StructureRole.FARM),trade=new Station(start.offset(24,0,7),StructureRole.TRADER),idle=new Station(start.offset(24,0,-7),StructureRole.FARM);
            var f=fixture(level,start,farm,trade,idle); BlockPos merchantStart=start.south(7);
            level.setBlockAndUpdate(farm.position().north(2),Blocks.BARREL.defaultBlockState());
            Container jobStorage=(Container)level.getBlockEntity(farm.position().north(2));
            trap(level,start); trap(level,merchantStart);
            var worker=f.worker(start,farm); worker.bag().offer(new ItemStack(Items.DIAMOND,3));
            worker.bag().offer(new ItemStack(Items.BREAD,3));
            var trader=f.worker(merchantStart,trade); trader.bag().offer(new ItemStack(Items.GOLD_INGOT,2));
            var waiting=f.worker(idle.position().west(2),idle); var waitingAt=waiting.position();
            helper.runAtTickTime(580,() -> helper.assertTrue(worker.blockPosition().distSqr(start)<4,"Worker was rescued before thirty seconds"));
            helper.runAtTickTime(820,() -> {
                helper.assertTrue(worker.blockPosition().distSqr(farm.position())<16,"Trapped worker did not return to its job: "+worker.activity()+" at "+worker.blockPosition());
                helper.assertTrue(farm.position().equals(f.town.jobs.home(worker.getUUID()))
                        && worker.bag().count(Items.DIAMOND)+InventoryOps.count(List.of(jobStorage),s -> s.is(Items.DIAMOND))==3,"Recovery lost the worker's job or inventory");
                helper.assertTrue(trader.blockPosition().distSqr(merchantStart)<4 && trader.bag().count(Items.GOLD_INGOT)==2,"A trader was teleported or lost cargo");
                helper.assertTrue(waiting.position().distanceToSqr(waitingAt)<4,"An idle, unblocked worker was teleported");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=1600)
    @EmptyTemplate
    @TestHolder(description="A guard keeps attacking after real hostile damage, receives no hospital healing in combat, then reaches a hospital bed and recovers after the attacker dies.")
    static void woundedGuardDefendsBeforeHospital(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-8600));
            Station post=new Station(start.east(10),StructureRole.GUARD),hospital=new Station(start.east(2),StructureRole.HOSPITAL);
            var f=fixture(level,start,post,hospital); bed(level,hospital.position().north(2));
            var guard=f.worker(post.position().west(2),post); guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            var enemy=zombie(level,post.position().east(3)); enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); enemy.setHealth(200);
            helper.runAtTickTime(30,() -> guard.hurtServer(level,level.damageSources().mobAttack(enemy),4));
            helper.runAtTickTime(240,() -> {
                helper.assertTrue(guard.isAlive() && guard.getHealth()<guard.getMaxHealth(),"Guard healed outside a hospital during combat");
                helper.assertTrue(!guard.recovering() && guard.hospitalBed()==null && guard.inCombat(),"Wounded guard abandoned combat: "+guard.activity());
                helper.assertTrue(enemy.getHealth()<180,"Guard did not continue attacking the hostile: "+enemy.getHealth()+" / "+guard.activity());
                enemy.kill(level);
                helper.succeedWhen(() -> {
                    helper.assertTrue(guard.getHealth()==guard.getMaxHealth() && !guard.recovering(),"Guard has not recovered after combat: "+guard.activity());
                    helper.assertTrue(f.town.campaign.journal.stream().anyMatch(e -> e.text().contains("recovered at the hospital")),"Recovery did not occur in a hospital bed or reach the journal");
                    helper.assertTrue(post.position().equals(f.town.jobs.home(guard.getUUID())),"Recovery discarded the guard's post");
                    f.close();
                });
            });
        });
    }

    @GameTest(timeoutTicks=1400)
    @EmptyTemplate
    @TestHolder(description="Launching an actual wave immediately reports every attacker, raises the alarm without a bell, and sends a shield guard beyond its usual gate radius to damage an attacker.")
    static void waveMobilizesShieldGuardImmediately(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-8800));
            var chunks=CitizenNavigationTests.pinTicking(level,start,6);
            CitizenNavigationTests.meadow(level,start,-84,84,-84,84);
            Station post=new Station(start,StructureRole.GUARD);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Immediate wave defense",start,84,List.of(),List.of(post),"balanced");
            SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
            level.setBlockAndUpdate(start,WWMC.STATIONS.get(StructureRole.GUARD).get().defaultBlockState());
            WorldWorkData.get(level).guardPosts.put(start,new GuardPosts(start,start,start).withRole(GuardPosts.SHIELD));
            var f=new Fixture(level,start,chunks,town,new ArrayList<>()); var guard=f.worker(start.east(2),post);
            guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            town.citizens.add(UUID.randomUUID()); town.citizens.add(UUID.randomUUID());
            helper.runAtTickTime(5,() -> {
                int spawned=WaveService.launch(level,town);
                helper.assertTrue(spawned>0,"Actual wave failed to spawn on loaded clear ground");
                helper.assertTrue(DefenseService.alarmed(town) && DefenseService.threats(town)>=spawned,"Wave waited for a bell or delayed its guard reports");
                helper.runAfterDelay(5,() -> {
                    var attackers=level.getEntitiesOfClass(Monster.class,new AABB(start).inflate(90),m -> m.entityTags().contains(WaveService.WAVE_TAG));
                    for(var mob:attackers) { mob.setNoAi(true); mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET)); }
                    helper.succeedWhen(() -> {
                        helper.assertTrue(attackers.stream().anyMatch(m -> m.getHealth()<m.getMaxHealth()),"Shield guard did not answer the wave: "+guard.activity()+" at "+guard.blockPosition());
                        helper.assertTrue(guard.blockPosition().distSqr(start)>100,"Shield guard never left its usual gate radius");
                        attackers.forEach(Monster::discard); f.close();
                    });
                });
            });
        });
    }

    @GameTest(timeoutTicks=650)
    @EmptyTemplate
    @TestHolder(description="During an alarm an outdoor civilian opens the house door and reaches a reserved bed, while a civilian already inside stays by its own bed instead of fleeing outdoors.")
    static void frightenedCitizensShelterInsideTheirBedsHouse(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-9000));
            Station housing=new Station(start.offset(22,0,0),StructureRole.HOUSING),aJob=new Station(start.east(2),StructureRole.FARM),bJob=new Station(start.east(4),StructureRole.FARM);
            var f=fixture(level,start,housing,aJob,bJob);
            for(int x=14;x<=24;x++) for(int z=-5;z<=5;z++) for(int y=0;y<=3;y++)
                if(x==14 || x==24 || z==-5 || z==5 || y==3) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.STONE.defaultBlockState());
            BlockPos door=start.east(14);
            var doorState=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST);
            level.setBlock(door,doorState.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
            level.setBlock(door.above(),doorState.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
            BlockPos outsideBed=start.offset(20,0,-2),insideBed=start.offset(20,0,2);
            bed(level,outsideBed); bed(level,insideBed);
            var outside=f.worker(start.east(6),aJob); var inside=f.worker(insideBed.west(),bJob);
            DefenseService.waveAlarm(level,f.town);
            helper.runAtTickTime(400,() -> {
                helper.assertTrue(outside.blockPosition().getX()>door.getX() && outside.blockPosition().getX()<start.getX()+24,
                        "Civilian never reached shelter through the house door: "+outside.activity()+" at "+outside.blockPosition());
                helper.assertTrue(inside.blockPosition().getX()>door.getX() && inside.blockPosition().getX()<start.getX()+24,"An indoor civilian ran out into danger");
                for(var citizen:List.of(outside,inside)) helper.assertTrue(citizen.activity().contains("Sheltering at my bed"),"Civilian ignored the actual beds: "+citizen.activity());
                helper.assertTrue(level.getBlockState(door).is(Blocks.OAK_DOOR),"Sheltering destroyed the house door");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=150)
    @EmptyTemplate
    @TestHolder(description="Population research requires the previous tier, spends real stock once, stacks with emerald upgrades, updates the banner, survives saving, and respects the server ceiling.")
    static void researchRaisesPopulationWithSavedProgress(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-9200));
            Station warehouse=new Station(start.east(6),StructureRole.WAREHOUSE),research=new Station(start.east(20),StructureRole.RESEARCHER);
            var f=fixture(level,start,warehouse,research); BlockPos desk=research.position().north();
            level.setBlockAndUpdate(desk,Blocks.LECTERN.defaultBlockState());
            f.town.populationLevel=1; int before=SettlementService.populationLimit(f.town);
            BlockPos chestPos=warehouse.position().south(2); level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());
            Container stock=(Container)level.getBlockEntity(chestPos);
            stock.setItem(0,new ItemStack(WWMC.RESEARCH_SCROLL.get(),18));
            var scholar=new CitizenEntity(WWMC.CITIZEN.get(),level); scholar.join(f.town.id); scholar.setNoAi(true);
            scholar.setPos(desk.getX()+1.5,desk.getY(),desk.getZ()+.5); f.town.citizens.add(scholar.getUUID());
            f.town.jobs.assign(scholar.getUUID(),research.position()); f.citizens.add(scholar); level.addFreshEntity(scholar);
            stock.setItem(2,new ItemStack(Items.COBBLESTONE,64)); stock.setItem(3,new ItemStack(Items.EMERALD,56));
            stock.setItem(4,new ItemStack(Items.IRON_INGOT,24)); stock.setItem(5,new ItemStack(Items.BRICK,64)); stock.setItem(6,new ItemStack(Items.GOLD_INGOT,32));
            helper.runAtTickTime(5,() -> {
                var town=f.town;
                helper.assertTrue(Research.study(level,town,"city_planning").contains("first"),"Population tiers ignored their prerequisites");
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> s.is(WWMC.RESEARCH_SCROLL.get()))==18,"Failed research spent scrolls");
            });
            for(int n=0;n<3;n++) {
                String id=List.of("housing_plans","civic_planning","city_planning").get(n);
                helper.runAtTickTime(10+n*10,() -> {
                    var town=f.town; String result=Research.study(level,town,id);
                    helper.assertTrue(town.progress.project.isEmpty() && Research.has(town,id),"Earned scrolls did not unlock population research: "+result);
                    helper.assertTrue(Research.has(town,id),"Could not study population research: "+result);
                    helper.assertTrue(Research.study(level,town,id).startsWith("Already"),"Population research was charged twice");
                });
            }
            helper.runAtTickTime(45,() -> {
                var town=f.town;
                helper.assertTrue(Research.populationBonus(town)==50 && SettlementService.populationLimit(town)==Math.min(Config.MAX_CITIZENS.get(),before+50),"Population research replaced upgrades or failed to raise the cap");
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> s.is(WWMC.RESEARCH_SCROLL.get()) || s.is(Items.EMERALD) || s.is(Items.COBBLESTONE) || s.is(Items.IRON_INGOT) || s.is(Items.BRICK) || s.is(Items.GOLD_INGOT))==0,"Research costs were not exact");
                var saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
                helper.assertTrue(SettlementService.populationLimit(saved)==SettlementService.populationLimit(town),"Research population bonus did not survive saving");
                helper.assertTrue(!SettlementService.canGrow(town),"Town can buy unusable upgrades above the server maximum");
                helper.assertTrue(Panels.town(level,town).tabs().stream().flatMap(t -> t.rows().stream()).anyMatch(r -> r.text().getString().equals("Population research")),"The banner hides research capacity");
                f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=450)
    @EmptyTemplate
    @TestHolder(description="A single nearby hostile sends a civilian to its bed even without a town alarm, and losing sight of danger on the way does not cancel the trip home.")
    static void localFearFinishesTheTripHome(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-9400));
            Station housing=new Station(start.east(22),StructureRole.HOUSING),farm=new Station(start.east(6),StructureRole.FARM);
            var f=fixture(level,start,housing,farm); BlockPos home=start.offset(21,0,-2); bed(level,home);
            var citizen=f.worker(start.east(3),farm); var enemy=zombie(level,start);
            helper.runAtTickTime(350,() -> {
                helper.assertTrue(!DefenseService.alarmed(f.town),"The test must exercise local fear without a town alarm");
                helper.assertTrue(citizen.blockPosition().distSqr(home.north())<9 && citizen.activity().contains("Sheltering at my bed"),
                        "Local fear did not finish the trip to a bed: "+citizen.activity()+" at "+citizen.blockPosition());
                enemy.discard(); f.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=250)
    @EmptyTemplate
    @TestHolder(description="A guard whose saved post exists but whose active work has not resumed yet still recognizes its duty, wakes during an alarm and fights instead of remaining idle.")
    static void savedGuardAssignmentStartsDuringAlarm(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-9600));
            Station post=new Station(start.east(10),StructureRole.GUARD); var f=fixture(level,start,post);
            var guard=f.worker(post.position().west(2),post); guard.setNoAi(true); guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            var enemy=zombie(level,post.position().east(4)); enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); enemy.setHealth(200);
            DefenseService.waveAlarm(level,f.town);
            helper.runAtTickTime(30,() -> {
                helper.assertTrue(guard.isGuard(),"A saved guard assignment was lost while active work was paused");
                guard.setNoAi(false);
            });
            helper.runAtTickTime(200,() -> {
                helper.assertTrue(enemy.getHealth()<200 && guard.getTarget()==enemy,"Guard never resumed defense during the alarm: "+guard.activity());
                enemy.discard(); f.close(); helper.succeed();
            });
        });
    }
}
