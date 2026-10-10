package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.menu.PanelMenu;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Claim isolation, invitation consent, damaged-save recovery and real explosion protection. */
public final class SettlementWorldTests {
    private static ServerPlayer player(ServerLevel level,String name,BlockPos pos) {
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),name));
        player.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5); return player;
    }
    private static Settlement town(ServerLevel level,UUID owner,BlockPos center,String name) {
        var town=new Settlement(UUID.randomUUID(),owner,name,center,16,List.of(),List.of(),"balanced");
        SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
        level.setBlockAndUpdate(center,WWMC.BANNER.get().defaultBlockState()); return town;
    }
    private static void leave(ServerLevel level,Settlement... towns) {
        for(Settlement town:towns) SettlementData.get(level).settlements.remove(town);
        SettlementData.get(level).setDirty();
    }
    private static PlayerInteractEvent.RightClickBlock use(ServerPlayer player,BlockPos pos) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,
                new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
    }
    @GameTest(timeoutTicks=260)
    @EmptyTemplate
    @TestHolder(description="Creeper and TNT explosions destroy nearby ordinary blocks but preserve two towns' flags and saved claims through periodic cleanup; flags also cannot be moved by pistons.")
    static void twoTownFlagsSurviveExplosions(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(6000,2,-3400)),second=start.east(80);
            var firstChunks=CitizenNavigationTests.pinArea(level,start,-8,10,-8,8); var secondChunks=CitizenNavigationTests.pinArea(level,second,-8,10,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,10,-8,8); CitizenNavigationTests.meadow(level,second,-8,10,-8,8);
            UUID owner=UUID.randomUUID(); Settlement a=town(level,owner,start,"First Town"),b=town(level,owner,second,"Second Town");
            a.campaign.requests.put("minecraft:bread",32); b.campaign.requests.put("minecraft:bread",16);
            BlockPos firstBomb=start.east(2),secondBomb=second.east(2);
            level.setBlockAndUpdate(firstBomb,Blocks.OAK_PLANKS.defaultBlockState()); level.setBlockAndUpdate(secondBomb,Blocks.OAK_PLANKS.defaultBlockState());
            var creeper=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("creeper")).create(level,EntitySpawnReason.COMMAND);
            var tnt=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("tnt")).create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(creeper!=null && tnt!=null,"Explosion sources were not created");
            new ServerExplosion(level,creeper,null,null,Vec3.atCenterOf(firstBomb),3,false,Explosion.BlockInteraction.DESTROY).explode();
            new ServerExplosion(level,tnt,null,null,Vec3.atCenterOf(secondBomb),4,false,Explosion.BlockInteraction.DESTROY).explode();
            helper.assertTrue(level.getBlockState(firstBomb).isAir() && level.getBlockState(secondBomb).isAir(),"Explosion positive controls survived; blast protection may be global");
            helper.assertTrue(level.getBlockState(start).is(WWMC.BANNER.get()) && level.getBlockState(second).is(WWMC.BANNER.get()),"An explosion destroyed a settlement flag");
            helper.assertTrue(WWMC.BANNER.get().defaultBlockState().getPistonPushReaction()==PushReaction.BLOCK,"A piston can move the fixed rally point");
            List<BlockPos> forced=new ArrayList<>(List.of(start,second,firstBomb));
            NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level,new ServerExplosion(level,tnt,null,null,Vec3.atCenterOf(firstBomb),4,false,Explosion.BlockInteraction.DESTROY),new ArrayList<>(),forced));
            helper.assertTrue(forced.equals(List.of(firstBomb)),"An explosion that bypasses resistance can still destroy or duplicate flags");
            helper.runAfterDelay(210,() -> {
                var data=SettlementData.get(level);
                helper.assertTrue(data.at(start)==a && data.at(second)==b && data.byId(a.id)==a && data.byId(b.id)==b,"Periodic cleanup lost or combined a claim after an explosion");
                helper.assertTrue(a.campaign.requests.get("minecraft:bread")==32 && b.campaign.requests.get("minecraft:bread")==16,"Town policy was combined across claims");
                leave(level,a,b); CitizenNavigationTests.release(level,start,firstChunks); CitizenNavigationTests.release(level,second,secondChunks); helper.succeed();
            });
        });
    }
    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="Uninvited and allied players cannot break blocks or open storage; an invitation only opens the flag screen until accepted, builders gain local access, and revocation closes it immediately.")
    static void claimAccessAndRelationships(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(6000,2,-3520)),second=start.east(80);
            var firstChunks=CitizenNavigationTests.pinArea(level,start,-8,8,-8,8); var secondChunks=CitizenNavigationTests.pinArea(level,second,-8,8,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,8,-8,8); CitizenNavigationTests.meadow(level,second,-8,8,-8,8);
            ServerPlayer owner=player(level,"FlagOwner",start.east()),friend=player(level,"FlagFriend",start.south()),otherOwner=player(level,"OtherOwner",second.east());
            Settlement a=town(level,owner.getUUID(),start,"Home"),b=town(level,otherOwner.getUUID(),second,"Neighbor");
            BlockPos stock=start.west(3),stone=start.north(3); level.setBlockAndUpdate(stock,Blocks.BARREL.defaultBlockState()); level.setBlockAndUpdate(stone,Blocks.STONE.defaultBlockState());
            helper.assertTrue(!friend.gameMode.destroyBlock(stone) && level.getBlockState(stone).is(Blocks.STONE),"Uninvited player broke claimed blocks");
            helper.assertTrue(use(friend,stock).isCanceled() && !use(friend,start).isCanceled()
                    && MultiplayerViews.valid(friend,start) && !RelationshipViews.valid(friend,start),"Public contract board granted storage or relationship controls, or its flag was blocked");
            TownAccess.alliance(a,b,owner.getUUID()); TownAccess.alliance(b,a,otherOwner.getUUID());
            helper.assertTrue(TownAccess.allied(a,b) && ClaimProtection.denied(level,otherOwner,start),"An alliance granted access to another town's claim");
            TownAccess.invite(a,owner.getUUID(),friend.getUUID(),"builder");
            helper.assertTrue(!use(friend,start).isCanceled() && use(friend,stock).isCanceled(),"Pending invitation granted storage access or blocked its acceptance screen");
            helper.assertTrue(RelationshipViews.valid(friend,start),"Invitee cannot open Relationships at the flag");
            RelationshipViews.act(friend,start,RelationshipViews.ROW_ACTION,1,"act:accept:"+a.id);
            helper.assertTrue(TownAccess.builds(a,friend.getUUID()) && !ClaimProtection.denied(level,friend,start) && !TownAccess.manages(a,friend.getUUID()),"Builder invitation was not accepted with the correct permissions");
            helper.assertTrue(friend.gameMode.destroyBlock(stone) && !use(friend,stock).isCanceled(),"Accepted builder cannot build or interact");
            RelationshipViews.act(friend,start,RelationshipViews.RENAME,0,"Unauthorized name"); helper.assertTrue(a.name.equals("Home"),"Builder renamed an owner's town");
            RelationshipViews.act(owner,start,RelationshipViews.RENAME,0,"Swishhyy's Settlement"); helper.assertTrue(a.name.equals("Swishhyy's Settlement") && b.name.equals("Neighbor"),"Renaming changed the wrong town");
            RelationshipViews.act(owner,start,RelationshipViews.ROW_ACTION,0,"permission:"+friend.getUUID());
            helper.assertTrue(ClaimProtection.denied(level,friend,start) && !RelationshipViews.valid(friend,start) && use(friend,stock).isCanceled(),"Revocation retained access or an open management screen");
            var screen=Panels.town(level,a,owner);
            helper.assertTrue(screen.tabs().stream().flatMap(t -> t.rows().stream()).anyMatch(row -> row.key().equals("act:relationships")),"Town settings are inaccessible from More");
            leave(level,a,b); CitizenNavigationTests.release(level,start,firstChunks); CitizenNavigationTests.release(level,second,secondChunks); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="The Restore flag screen consumes one replacement only after a safe restoration, preserves the original occupied town and routes, rejects strangers and obstacles, and never restores the owner's other town.")
    static void missingFlagRecoveryPreservesTown(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(6000,2,-3640)),second=start.east(80);
            var firstChunks=CitizenNavigationTests.pinArea(level,start,-8,8,-8,8); var secondChunks=CitizenNavigationTests.pinArea(level,second,-8,8,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,8,-8,8); CitizenNavigationTests.meadow(level,second,-8,8,-8,8);
            ServerPlayer owner=player(level,"RecoveryOwner",start.south()),stranger=player(level,"RecoveryStranger",start.east());
            Settlement a=town(level,owner.getUUID(),start,"Damaged Town"),b=town(level,owner.getUUID(),second,"Other Town"); UUID citizen=UUID.randomUUID();
            a.citizens.add(citizen); a.stations.add(new Station(start.west(4),StructureRole.GUARD)); a.jobs.assign(citizen,start.west(4)); a.campaign.requests.put("minecraft:bread",32); a.trading.partner=b.id; b.trading.partner=a.id;
            level.setBlockAndUpdate(start.west(4),WWMC.STATIONS.get(StructureRole.GUARD).get().defaultBlockState());
            level.setBlockAndUpdate(start,Blocks.STONE.defaultBlockState());
            owner.getInventory().setItem(9,new ItemStack(WWMC.BANNER_ITEM.get(),2));
            SettlementService.recoverBanner(level,owner,a);
            helper.assertTrue(level.getBlockState(start).is(Blocks.STONE) && owner.getInventory().getItem(9).getCount()==2,"Recovery overwrote a block or consumed a flag on failure");
            level.setBlockAndUpdate(start,Blocks.AIR.defaultBlockState()); stranger.getInventory().setItem(9,new ItemStack(WWMC.BANNER_ITEM.get())); SettlementService.recoverBanner(level,stranger,a);
            helper.assertTrue(level.getBlockState(start).isAir() && stranger.getInventory().getItem(9).getCount()==1,"A stranger restored another town's rally point");
            BlockPos temporary=start.south(3); level.setBlockAndUpdate(temporary,WWMC.BANNER.get().defaultBlockState());
            var menu=new PanelMenu(1,PanelMenu.Kind.RELATIONSHIPS,temporary,owner,RelationshipViews.build(level,a,owner));
            helper.assertTrue(menu.stillValid(owner),"A lost town cannot open recovery from a replacement flag inside its claim");
            menu.act(owner,RelationshipViews.RECOVER,0,0,"");
            helper.assertTrue(level.getBlockState(start).is(WWMC.BANNER.get()) && owner.getInventory().getItem(9).getCount()==1,"Recovery did not consume exactly one physical replacement flag");
            menu.act(owner,RelationshipViews.RECOVER,0,0,""); helper.assertTrue(owner.getInventory().getItem(9).getCount()==1,"Repeated recovery charged twice");
            helper.assertTrue(SettlementData.get(level).at(start)==a && a.citizens.equals(List.of(citizen)) && a.jobs.home(citizen).equals(start.west(4)) && a.campaign.requests.get("minecraft:bread")==32,"Recovery reset town identity, population, assignment or policy");
            helper.assertTrue(a.trading.partner.equals(b.id) && b.trading.partner.equals(a.id) && b.name.equals("Other Town") && level.getBlockState(second).is(WWMC.BANNER.get()),"Recovery changed the other town or its route");
            leave(level,a,b); CitizenNavigationTests.release(level,start,firstChunks); CitizenNavigationTests.release(level,second,secondChunks); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="Crossing inclusive claim borders gives one entry notice with the current custom town name; walking inside stays quiet, and leaving then entering another town updates it.")
    static void townEntryNoticesFollowBoundaries(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(6000,2,-3760)),second=start.east(80);
            var firstChunks=CitizenNavigationTests.pinArea(level,start,-20,20,-8,8); var secondChunks=CitizenNavigationTests.pinArea(level,second,-8,8,-8,8);
            CitizenNavigationTests.meadow(level,start,-20,20,-8,8); CitizenNavigationTests.meadow(level,second,-8,8,-8,8);
            ServerPlayer owner=player(level,"NoticeOwner",start.west(17)); Settlement a=town(level,owner.getUUID(),start,"Home"),b=town(level,owner.getUUID(),second,"River Town"); var notices=new ClaimProtection();
            helper.assertTrue(notices.entryNotice(owner)==null,"Notice appeared outside the claim");
            RelationshipViews.rename(level,a,owner.getUUID(),"Swishhyy's Settlement"); owner.setPos(start.getX()-16+0.5,start.getY(),start.getZ()+0.5);
            helper.assertTrue("You are now entering Swishhyy's Settlement".equals(notices.entryNotice(owner)),"Entry notice did not use the inclusive border or the renamed town");
            owner.setPos(start.getX()+0.5,start.getY(),start.getZ()+1.5); helper.assertTrue(notices.entryNotice(owner)==null,"Entry notice repeated while walking inside one town");
            owner.setPos(start.getX()+17.5,start.getY(),start.getZ()+0.5); helper.assertTrue(notices.entryNotice(owner)==null,"Leaving a town produced an entering notice");
            owner.setPos(second.getX()+0.5,second.getY(),second.getZ()+0.5); helper.assertTrue("You are now entering River Town".equals(notices.entryNotice(owner)),"Entering the second town reused the first name");
            owner.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); helper.assertTrue("You are now entering Swishhyy's Settlement".equals(notices.entryNotice(owner)),"Returning to the first town did not notify again");
            leave(level,a,b); CitizenNavigationTests.release(level,start,firstChunks); CitizenNavigationTests.release(level,second,secondChunks); helper.succeed();
        });
    }
}
