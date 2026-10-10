package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.CitizenMenu;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Banner observations must describe actual interruptions without changing paid work or loading chunks. */
public final class ResearchStatusWorldTests {
    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="Banner and Campaign research reasons follow jobs, stations, sleeping, inventory access, injuries and danger without charging, advancing or loading a paid project.")
    static void pauseReasonsAreReadOnly(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-13400));
            var chunks=CitizenNavigationTests.pinTicking(level,start,2);
            CitizenNavigationTests.meadow(level,start,-8,30,-12,12);
            Station warehouse=new Station(start.east(6),StructureRole.WAREHOUSE),station=new Station(start.east(20),StructureRole.RESEARCHER);
            var town=town(level,start,List.of(warehouse,station));
            BlockPos desk=station.position().north(2);
            level.setBlockAndUpdate(desk,Blocks.LECTERN.defaultBlockState());
            Container stock=stock(level,warehouse);
            var viewer=new FakePlayer(level,new GameProfile(town.owner,"ResearchOwner"));
            viewer.setPos(station.position().getX()+.5,station.position().getY(),station.position().getZ()+1.5);
            helper.assertTrue(Research.status(level,town).paused(),"Scroll writing must explain the missing researcher");
            legacyProject(town,stock);
            town.progress.projectTicks=1230;
            var worker=worker(level,town,station,station.position().south(),false);
            town.jobs.release(worker.getUUID());
            BiConsumer<Research.WorkState,String> check=(state,reason) -> {
                Research.Status status=Research.status(level,town);
                helper.assertTrue(status.state()==state && status.detail().contains(reason),"Wrong research status: "+status);
                for(var row:statusRows(level,town,viewer)) {
                    helper.assertTrue(row.text().getString().equals(status.title()) && row.detail().getString().equals(status.detail()),"Banner and Campaign disagree with the interruption");
                    helper.assertTrue(row.color()==Panels.AMBER,"Paused research is not marked amber");
                }
                helper.assertTrue(town.progress.projectTicks==1230 && town.progress.project.equals("bronze_age"),"Opening status altered paid progress");
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> !s.isEmpty())==4,"Opening status charged or returned paid supplies");
            };
            helper.runAtTickTime(20,() -> {
                check.accept(Research.WorkState.PAUSED,"No researcher assigned");
                level.setBlockAndUpdate(desk,Blocks.AIR.defaultBlockState());
                check.accept(Research.WorkState.PAUSED,"No lectern in range");
                level.setBlockAndUpdate(desk,Blocks.LECTERN.defaultBlockState());
                town.jobs.setLevel(StructureRole.RESEARCHER,JobBoard.OFF);
                check.accept(Research.WorkState.PAUSED,"jobs are off");
                town.jobs.setLevel(StructureRole.RESEARCHER,JobBoard.NORMAL);
                town.jobs.assign(worker.getUUID(),station.position());
                check.accept(Research.WorkState.PAUSED,"unavailable for work");
                var menu=new CitizenMenu(7,viewer.getInventory(),worker.bag(),worker,viewer,Panels.citizen(worker));
                viewer.containerMenu=menu; worker.bag().opened(viewer,menu);
                check.accept(Research.WorkState.PAUSED,"inventory is open");
                viewer.containerMenu=viewer.inventoryMenu;
                BlockPos foot=station.position().south(3);
                var bed=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
                level.setBlockAndUpdate(foot,bed.setValue(BedBlock.PART,BedPart.FOOT));
                level.setBlockAndUpdate(foot.north(),bed.setValue(BedBlock.PART,BedPart.HEAD));
                worker.startSleeping(foot);
                check.accept(Research.WorkState.PAUSED,"sleeping");
                worker.stopSleeping();
                worker.setHealth(worker.getMaxHealth()-1);
                check.accept(Research.WorkState.PAUSED,"hospital recovery");
                worker.setHealth(worker.getMaxHealth());
                DefenseService.toggle(level,town);
                check.accept(Research.WorkState.PAUSED,"danger");
                DefenseService.toggle(level,town);
                town.jobs.release(worker.getUUID());
                UUID missing=UUID.randomUUID(); town.citizens.add(missing); town.jobs.assign(missing,station.position());
                check.accept(Research.WorkState.PAUSED,"Assigned researcher is unloaded or missing");
                town.jobs.release(missing); town.citizens.remove(missing);
                level.setBlockAndUpdate(station.position(),Blocks.AIR.defaultBlockState());
                check.accept(Research.WorkState.PAUSED,"Station is missing");
                level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(StructureRole.RESEARCHER).get().defaultBlockState());
                Station unloaded=new Station(start.east(224),StructureRole.RESEARCHER);
                helper.assertTrue(!level.hasChunkAt(unloaded.position()),"Fixture's distant station unexpectedly loaded");
                town.stations.remove(station); town.stations.add(unloaded);
                check.accept(Research.WorkState.PAUSED,"Station is unloaded");
                helper.assertTrue(!level.hasChunkAt(unloaded.position()),"Reading research status loaded a distant chunk");
                town.stations.remove(unloaded);
                check.accept(Research.WorkState.PAUSED,"No Researcher Station");
                town.stations.add(station);
                var active=CampaignViews.build(level,town,viewer,false).tabs().stream().filter(t -> t.name().getString().equals("Research"))
                        .flatMap(t -> t.rows().stream()).filter(r -> r.key().equals("act:research:bronze_age")).findFirst().orElseThrow();
                helper.assertTrue(active.value()==1 && active.detail().getString().contains("Supplies already paid")
                        && !active.detail().getString().contains("Finish Bronze Age first"),"Paid project's button or supply explanation is misleading");
                var saved=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
                var restored=Settlement.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow();
                helper.assertTrue(restored.progress.project.equals("bronze_age") && restored.progress.projectTicks==1230,"Status interrupted saved paid progress");
                worker.discard(); SettlementData.get(level).settlements.remove(town);
                CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=1000)
    @EmptyTemplate
    @TestHolder(description="Researchers explain sealed and newly blocked lecterns, resume after a real route is restored, and show walking/working despite another paused researcher without repaying supplies.")
    static void blockedRoutesResumeAndAvailableWorkersWin(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-13800));
            var chunks=CitizenNavigationTests.pinTicking(level,start,3);
            CitizenNavigationTests.meadow(level,start,-8,54,-12,12);
            Station warehouse=new Station(start.east(6),StructureRole.WAREHOUSE),first=new Station(start.east(20),StructureRole.RESEARCHER),second=new Station(start.east(42),StructureRole.RESEARCHER);
            var town=town(level,start,List.of(warehouse,first,second)); Container stock=stock(level,warehouse);
            BlockPos firstDesk=first.position().north(2),secondDesk=second.position().north(2);
            level.setBlockAndUpdate(firstDesk,Blocks.LECTERN.defaultBlockState()); level.setBlockAndUpdate(secondDesk,Blocks.LECTERN.defaultBlockState());
            enclosure(level,firstDesk,true);
            CitizenEntity a=worker(level,town,first,start.east(8),true),b=worker(level,town,second,start.east(26),false);
            var viewer=new FakePlayer(level,new GameProfile(town.owner,"RouteOwner"));
            legacyProject(town,stock);
            int[] phase={0},frozen={0}; long[] stoppedAt={0};
            helper.succeedWhen(() -> {
                var status=Research.status(level,town);
                if(phase[0]==0) {
                    helper.assertTrue(status.paused() && status.detail().contains("cannot reach the lectern"),"Sealed lectern was not diagnosed: "+status+"; "+a.activity());
                    helper.assertTrue(town.progress.projectTicks==0,"Research advanced through a sealed wall");
                    enclosure(level,firstDesk,false); phase[0]=1;
                }
                if(phase[0]==1) {
                    helper.assertTrue(town.progress.projectTicks>0 && status.state()==Research.WorkState.WORKING,"Restored route did not resume research: "+status+"; "+a.activity());
                    a.setNoAi(true); b.setNoAi(false); phase[0]=2;
                }
                if(phase[0]==2) {
                    helper.assertTrue(status.state()==Research.WorkState.WAITING && status.detail().contains("walking to the lectern"),"A paused colleague hid the available researcher's travel: "+status+"; "+b.activity());
                    helper.assertTrue(b.blockPosition().distSqr(secondDesk)>25,"Worker reached the desk before the dynamic obstruction");
                    enclosure(level,secondDesk,true); phase[0]=3;
                }
                if(phase[0]==3) {
                    helper.assertTrue(status.paused() && status.detail().contains("cannot reach the lectern"),"New obstruction retained a stale walking state: "+status+"; "+b.activity()+"; position="+b.blockPosition()+"; job="+town.jobs.home(b.getUUID()));
                    enclosure(level,secondDesk,false); phase[0]=4;
                }
                if(phase[0]==4) {
                    helper.assertTrue(status.state()==Research.WorkState.WORKING,"Working researcher did not override a paused colleague: "+status+"; "+b.activity());
                    for(var row:statusRows(level,town,viewer)) helper.assertTrue(row.text().getString().equals("Research working") && row.color()==Panels.GREEN,"Actual resumed work is hidden at the banner");
                    b.setNoAi(true); frozen[0]=town.progress.projectTicks; stoppedAt[0]=level.getGameTime(); phase[0]=5;
                }
                helper.assertTrue(level.getGameTime()-stoppedAt[0]>=50,"Waiting to verify paused work");
                helper.assertTrue(Research.status(level,town).paused() && town.progress.projectTicks==frozen[0],"An unavailable worker's recent working snapshot kept research alive");
                helper.assertTrue(InventoryOps.count(List.of(stock),s -> !s.isEmpty())==4,"Route retries charged the project twice");
                a.discard(); b.discard(); SettlementData.get(level).settlements.remove(town);
                CitizenNavigationTests.releaseTicking(level,start,chunks);
            });
        });
    }

    private static Settlement town(ServerLevel level,BlockPos start,List<Station> stations) {
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Research status",start,96,List.of(),stations,"balanced");
        SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
        for(var station:stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
        return town;
    }
    /** Older paid projects keep their original supplies and route behavior. */
    private static void legacyProject(Settlement town,Container stock) {
        for(int slot=0;slot<4;slot++) stock.removeItem(slot,slot==0 ? 24 : 8);
        town.progress.project="bronze_age"; town.progress.projectTicks=0;
    }
    private static Container stock(ServerLevel level,Station warehouse) {
        BlockPos chest=warehouse.position().south(2); level.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
        Container stock=(Container)level.getBlockEntity(chest);
        stock.setItem(0,new ItemStack(Items.COPPER_INGOT,25)); stock.setItem(1,new ItemStack(WWMC.TIN_INGOT.get(),9));
        stock.setItem(2,new ItemStack(Items.COAL,9)); stock.setItem(3,new ItemStack(Items.PAPER,9)); return stock;
    }
    private static CitizenEntity worker(ServerLevel level,Settlement town,Station station,BlockPos pos,boolean ai) {
        var worker=new CitizenEntity(WWMC.CITIZEN.get(),level); worker.join(town.id); worker.setNoAi(!ai);
        worker.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); worker.bag().offer(new ItemStack(Items.BREAD));
        town.citizens.add(worker.getUUID()); town.jobs.assign(worker.getUUID(),station.position()); level.addFreshEntity(worker); return worker;
    }
    private static List<PanelView.Row> statusRows(ServerLevel level,Settlement town,FakePlayer viewer) {
        return List.of(statusRow(Panels.town(level,town,viewer),"Overview"),statusRow(CampaignViews.build(level,town,viewer,false),"Research"));
    }
    private static PanelView.Row statusRow(PanelView panel,String tab) {
        return panel.tabs().stream().filter(t -> t.name().getString().equals(tab)).flatMap(t -> t.rows().stream())
                .filter(r -> List.of("Research working","Research waiting","Research paused").contains(r.text().getString())).findFirst().orElseThrow();
    }
    private static void enclosure(ServerLevel level,BlockPos desk,boolean close) {
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) for(int y=0;y<=4;y++)
            if(Math.abs(x)==4 || Math.abs(z)==4 || y==4)
                level.setBlockAndUpdate(desk.offset(x,y,z),(close ? Blocks.STONE : Blocks.AIR).defaultBlockState());
    }
}
