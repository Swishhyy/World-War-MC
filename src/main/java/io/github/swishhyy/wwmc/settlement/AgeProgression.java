package io.github.swishhyy.wwmc.settlement;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative equipment research. Possession and storage are always allowed; membership grants use everywhere. */
public final class AgeProgression {
    private static final String[] TECHS={"","bronze_age","iron_age","gemcraft","netherite_smithing"};
    private static final String[] TITLES={"Stone Age","Bronze Age","Iron Age","Gemcraft","Netherite Smithing"};
    private static TagKey<Item> tag(String name) { return TagKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("wwmc","requires_"+name)); }
    public static final TagKey<Item> BRONZE=tag("bronze"),IRON=tag("iron"),DIAMOND=tag("gemcraft"),NETHERITE=tag("netherite");
    public static final TagKey<Item> STEEL=tag("steel");
    private static final Map<Player,Long> NOTICES=new WeakHashMap<>();
    public static int required(ItemStack stack) {
        if(stack.isEmpty()) return 0;
        return stack.is(NETHERITE) ? 4 : stack.is(DIAMOND) ? 3 : stack.is(STEEL) || stack.is(IRON) ? 2 : stack.is(BRONZE) ? 1 : 0;
    }
    public static String requirement(ItemStack stack) { return stack.is(STEEL) ? "Steelworking" : TITLES[required(stack)]; }
    public static boolean member(Settlement town,UUID player) {
        return !town.trading.npc && (town.owner.equals(player) || town.campaign.members.containsKey(player));
    }
    public static boolean allowed(Settlement town,ItemStack stack) {
        if(stack.is(STEEL)) return Research.has(town,"steel_working");
        int tier=required(stack); return tier==0 || Research.has(town,TECHS[tier]);
    }
    public static boolean allowed(Player player,ItemStack stack) {
        if(required(stack)==0 || player.isCreative() || player.isSpectator()) return true;
        // Clients do not decide permissions. The server checks every recipe pickup, interaction and attack.
        if(!(player instanceof ServerPlayer serverPlayer)) return true;
        for(ServerLevel level:serverPlayer.level().getServer().getAllLevels())
            for(Settlement town:SettlementData.get(level).settlements)
                if(member(town,player.getUUID()) && allowed(town,stack)) return true;
        return false;
    }
    public static void notice(Player player,ItemStack stack) {
        long now=player.level().getGameTime();
        if(now-NOTICES.getOrDefault(player,now-60)<60) return;
        NOTICES.put(player,now);
        SettlementService.notify(player,"Requires "+requirement(stack)+" research in your settlement. Keep this item in storage until it is unlocked.");
    }
    /** Called before a recipe result is taken, including shift-click. Ingredients are never consumed for a locked result. */
    public static boolean denyResult(AbstractContainerMenu menu,int slot,Player player) {
        if(!(player instanceof ServerPlayer) || slot<0 || slot>=menu.slots.size()) return false;
        boolean output=(menu instanceof CraftingMenu || menu instanceof InventoryMenu) && slot==0
                || menu instanceof SmithingMenu && slot==3 || menu instanceof AnvilMenu && slot==2;
        if(!output) return false;
        ItemStack result=menu.getSlot(slot).getItem();
        if(!player.isCreative() && !player.isSpectator() && !(menu instanceof AnvilMenu) && ForgeWorkshop.forged(result)) {
            long now=player.level().getGameTime();
            if(now-NOTICES.getOrDefault(player,now-60)>=60) {
                NOTICES.put(player,now);
                SettlementService.notify(player,"This equipment must be forged by your blacksmith. Set an order in Production / Forge.");
            }
            return true;
        }
        if(allowed(player,result)) return false;
        notice(player,result); return true;
    }
    private static boolean denyHand(Player player,net.minecraft.world.InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(allowed(player,stack)) return false;
        notice(player,stack); return true;
    }
    @SubscribeEvent public void attack(AttackEntityEvent event) {
        if(event.getEntity() instanceof ServerPlayer && denyHand(event.getEntity(),net.minecraft.world.InteractionHand.MAIN_HAND)) event.setCanceled(true);
    }
    @SubscribeEvent public void mine(PlayerInteractEvent.LeftClickBlock event) {
        if(event.getEntity() instanceof ServerPlayer && denyHand(event.getEntity(),event.getHand())) event.setCanceled(true);
    }
    @SubscribeEvent public void broken(BreakBlockEvent event) {
        if(event.getPlayer() instanceof ServerPlayer && denyHand(event.getPlayer(),net.minecraft.world.InteractionHand.MAIN_HAND)) {
            event.setCanceled(true); event.setNotifyClient(true);
        }
    }
    @SubscribeEvent public void use(PlayerInteractEvent.RightClickItem event) {
        if(event.getEntity() instanceof ServerPlayer && denyHand(event.getEntity(),event.getHand())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public void block(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack block=new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem());
        boolean lockedBlock=!allowed(player,block);
        if(denyHand(player,event.getHand()) || lockedBlock) {
            if(lockedBlock) notice(player,block);
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public void entity(PlayerInteractEvent.EntityInteract event) {
        if(event.getEntity() instanceof ServerPlayer && denyHand(event.getEntity(),event.getHand())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    /** Includes dispenser and inventory equipment changes; the original stack is returned, never deleted. */
    public static void unequip(ServerPlayer player) {
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            ItemStack worn=player.getItemBySlot(slot);
            if(worn.isEmpty() || allowed(player,worn)) continue;
            notice(player,worn); player.setItemSlot(slot,ItemStack.EMPTY);
            player.getInventory().add(worn);
            if(!worn.isEmpty()) player.drop(worn,false);
        }
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer player) unequip(player);
    }
    @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
        // Remove locked armor before vanilla calculates its damage reduction, even if equipped during this tick.
        if(event.getEntity() instanceof ServerPlayer player) unequip(player);
        if(event.getSource().getEntity() instanceof ServerPlayer player && !allowed(player,player.getMainHandItem())) {
            notice(player,player.getMainHandItem()); event.setCanceled(true);
        }
    }
}
