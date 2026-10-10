package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Player claims are closed until an invitation is accepted. Workers and natural combat retain their normal rules. */
public final class ClaimProtection {
    private final Map<ServerPlayer,UUID> entered=new WeakHashMap<>();
    private final Map<Player,Long> notices=new WeakHashMap<>();
    private static Settlement claim(ServerLevel level,BlockPos pos) {
        Settlement town=SettlementData.get(level).at(pos);
        return town!=null && !town.trading.npc ? town : null;
    }
    public static boolean denied(ServerLevel level,Player player,BlockPos pos) {
        Settlement town=claim(level,pos);
        return town!=null && !TownAccess.builds(town,player.getUUID());
    }
    private void notice(ServerLevel level,Player player,BlockPos pos) {
        long now=level.getGameTime();
        if(now-notices.getOrDefault(player,now-40)<40) return;
        notices.put(player,now);
        Settlement town=claim(level,pos);
        if(town!=null) SettlementService.notify(player,town.name+" is protected. Its owner must grant you permission in Relationships.");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void breakBlock(BreakBlockEvent event) {
        if(event.getLevel() instanceof ServerLevel level && denied(level,event.getPlayer(),event.getPos())) {
            event.setCanceled(true); event.setNotifyClient(true); notice(level,event.getPlayer(),event.getPos());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if(event.getLevel() instanceof ServerLevel level && denied(level,event.getEntity(),event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        Player player=event.getEntity(); BlockPos pos=event.getPos();
        Settlement town=claim(level,pos);
        // Invitees may open only the flag's invitation screen; no claim access exists before acceptance.
        boolean invitation=town!=null && level.getBlockState(pos).is(WWMC.BANNER.get()) && TownAccess.invited(town,player.getUUID());
        boolean board=town!=null && town.center.equals(pos) && level.getBlockState(pos).is(WWMC.BANNER.get()) && player.getItemInHand(event.getHand()).isEmpty();
        boolean blocked=denied(level,player,pos) && !invitation && !board;
        var item=player.getItemInHand(event.getHand()).getItem();
        if(!blocked && event.getFace()!=null && (item instanceof BlockItem || item instanceof BucketItem))
            blocked=denied(level,player,pos.relative(event.getFace()));
        if(blocked) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); notice(level,player,pos); }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void useItem(PlayerInteractEvent.RightClickItem event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity().getItemInHand(event.getHand()).getItem() instanceof BucketItem)) return;
        Player player=event.getEntity();
        if(player.pick(6,1.0F,true) instanceof BlockHitResult hit && (denied(level,player,hit.getBlockPos()) || denied(level,player,hit.getBlockPos().relative(hit.getDirection())))) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); notice(level,player,hit.getBlockPos());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void place(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) return;
        boolean blocked=denied(level,player,event.getPos());
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi)
            blocked|=multi.getReplacedBlockSnapshots().stream().anyMatch(s -> denied(level,player,s.getPos()));
        if(blocked) { event.setCanceled(true); notice(level,player,event.getPos()); }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void tool(BlockEvent.BlockToolModificationEvent event) {
        if(event.getLevel() instanceof ServerLevel level && event.getPlayer()!=null && denied(level,event.getPlayer(),event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void trample(BlockEvent.FarmlandTrampleEvent event) {
        if(event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player && denied(level,player,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void useEntity(PlayerInteractEvent.EntityInteract event) {
        if(event.getLevel() instanceof ServerLevel level && denied(level,event.getEntity(),event.getTarget().blockPosition())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); notice(level,event.getEntity(),event.getTarget().blockPosition());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void attack(AttackEntityEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level && denied(level,event.getEntity(),event.getTarget().blockPosition())
                && !MultiplayerCombat.claimException(level,event.getEntity(),event.getTarget())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void damage(LivingIncomingDamageEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level && event.getSource().getEntity() instanceof Player player && denied(level,player,event.getEntity().blockPosition())
                && !MultiplayerCombat.claimException(level,player,event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void invulnerability(EntityInvulnerabilityCheckEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level && event.getSource().getEntity() instanceof Player player && denied(level,player,event.getEntity().blockPosition())
                && !MultiplayerCombat.claimException(level,player,event.getEntity())) event.setInvulnerable(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void explosion(ExplosionEvent.Detonate event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        var source=event.getExplosion().getIndirectSourceEntity();
        event.getAffectedBlocks().removeIf(pos -> level.getBlockState(pos).is(WWMC.BANNER.get()) || source instanceof Player player && denied(level,player,pos));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void piston(PistonEvent.Pre event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        var movement=event.getStructureHelper(); if(movement==null || !movement.resolve()) return;
        Settlement origin=claim(level,event.getPos());
        var direction=movement.getPushDirection();
        if(movement.getToPush().stream().anyMatch(pos -> claim(level,pos)!=origin || claim(level,pos.relative(direction))!=origin)
                || movement.getToDestroy().stream().anyMatch(pos -> claim(level,pos)!=origin)) event.setCanceled(true);
    }
    /** One notice for each transition, including login and direct movement between two different towns. */
    public String entryNotice(ServerPlayer player) {
        Settlement town=SettlementData.get((ServerLevel)player.level()).at(player.blockPosition());
        UUID before=entered.get(player),after=town==null ? null : town.id;
        if(Objects.equals(before,after)) return null;
        if(after==null) entered.remove(player); else entered.put(player,after);
        return town==null ? null : "You are now entering "+town.name;
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || player.tickCount%10!=0) return;
        String text=entryNotice(player);
        if(text!=null) player.sendOverlayMessage(Component.literal(text));
    }
}
