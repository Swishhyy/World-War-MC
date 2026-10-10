package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.item.GuideBook;
import io.github.swishhyy.wwmc.settlement.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import static org.junit.jupiter.api.Assertions.*;

public final class EquipmentRepairHealingChecks {
    private static final class Kit implements GuardEquipment.Equipment {
        final Map<EquipmentSlot,ItemStack> items=new EnumMap<>(EquipmentSlot.class);
        public ItemStack get(EquipmentSlot slot) { return items.getOrDefault(slot,ItemStack.EMPTY); }
        public void set(EquipmentSlot slot,ItemStack stack) { items.put(slot,stack); }
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void bothShiftsShareTheSameArmorAndOccupiedSlotsNeverLoseItems(MinecraftServer server) {
        Kit rack=new Kit(),day=new Kit(),night=new Kit();
        ItemStack original=new ItemStack(Items.IRON_CHESTPLATE); original.setDamageValue(37);
        original.set(DataComponents.CUSTOM_NAME,Component.literal("Town armor"));
        rack.set(EquipmentSlot.CHEST,original);
        assertTrue(GuardEquipment.upgrade(rack,day,EquipmentSlot.CHEST));
        assertTrue(rack.get(EquipmentSlot.CHEST).isEmpty());
        assertTrue(GuardEquipment.deposit(day,rack,EquipmentSlot.CHEST));
        assertTrue(day.get(EquipmentSlot.CHEST).isEmpty());
        assertTrue(GuardEquipment.upgrade(rack,night,EquipmentSlot.CHEST));
        assertEquals(37,night.get(EquipmentSlot.CHEST).getDamageValue());
        assertEquals("Town armor",night.get(EquipmentSlot.CHEST).get(DataComponents.CUSTOM_NAME).getString());
        rack.set(EquipmentSlot.CHEST,new ItemStack(Items.LEATHER_CHESTPLATE));
        assertFalse(GuardEquipment.deposit(night,rack,EquipmentSlot.CHEST));
        assertTrue(night.get(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE));
        assertTrue(rack.get(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE));
        assertFalse(GuardEquipment.upgrade(rack,night,EquipmentSlot.CHEST));
        ItemStack diamond=new ItemStack(Items.DIAMOND_CHESTPLATE); rack.set(EquipmentSlot.CHEST,diamond);
        assertTrue(GuardEquipment.upgrade(rack,night,EquipmentSlot.CHEST));
        assertTrue(rack.get(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),"The previous set stays on the rack after an upgrade");
        assertEquals(37,rack.get(EquipmentSlot.CHEST).getDamageValue());
        assertTrue(night.get(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void aFullBagCanStillReturnOverflowArmorForTheNextShift(MinecraftServer server) {
        CitizenInventory bag=new CitizenInventory(player -> true);
        for(int slot=0;slot<bag.getContainerSize();slot++) bag.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        ItemStack helmet=new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(50); bag.offer(helmet);
        assertTrue(bag.hasPending()); Kit rack=new Kit();
        ItemStack source=bag.first(s -> GuardEquipment.armor(s,EquipmentSlot.HEAD));
        GuardEquipment.Equipment overflow=new GuardEquipment.Equipment() {
            public ItemStack get(EquipmentSlot slot) { return source; }
            public void set(EquipmentSlot slot,ItemStack replacement) { bag.replace(source,replacement); }
        };
        assertTrue(GuardEquipment.deposit(overflow,rack,EquipmentSlot.HEAD));
        assertFalse(bag.hasPending()); assertEquals(50,rack.get(EquipmentSlot.HEAD).getDamageValue());
        assertEquals(36*64,InventoryOps.count(List.of(bag),s -> s.is(Items.COBBLESTONE)));
        CitizenInventory restored=new CitizenInventory(player -> true); restored.restore(bag.contents(),bag.pendingItems());
        assertTrue(restored.first(s -> GuardEquipment.armor(s,EquipmentSlot.HEAD)).isEmpty(),"The returned set cannot reappear from saved overflow");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void durabilityCutoffCannotRecycleWornEquipment(MinecraftServer server) {
        ItemStack tool=new ItemStack(Items.IRON_PICKAXE);
        tool.set(DataComponents.MAX_DAMAGE,100); tool.setDamageValue(75);
        assertFalse(GuardEquipment.worn(tool),"Exactly 25% is still usable");
        tool.setDamageValue(76); assertTrue(GuardEquipment.worn(tool));
        ItemStack armor=new ItemStack(Items.IRON_HELMET); armor.setDamageValue(armor.getMaxDamage()-1);
        assertFalse(GuardEquipment.upgrade(armor,ItemStack.EMPTY,EquipmentSlot.HEAD),"A retiring set cannot immediately be taken back");
        assertFalse(GuardEquipment.usable(ItemStack.EMPTY));
        assertFalse(GuardEquipment.worn(new ItemStack(Items.ARROW)));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void repairConsumesMatchingMaterialsAndPreservesEnchantsAndNames(MinecraftServer server) {
        ItemStack axe=new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(150); axe.set(DataComponents.CUSTOM_NAME,Component.literal("Old faithful"));
        axe.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),2);
        SimpleContainer stock=new SimpleContainer(3);
        stock.setItem(0,new ItemStack(Items.GOLD_INGOT,5));
        assertFalse(BlacksmithRepair.repair(axe,stock)); assertEquals(150,axe.getDamageValue());
        assertEquals(5,stock.getItem(0).getCount());
        stock.setItem(1,new ItemStack(Items.IRON_INGOT));
        assertTrue(BlacksmithRepair.supplied(axe,List.of(stock)),"One material can fund a partial repair");
        assertTrue(BlacksmithRepair.repair(axe,stock));
        assertEquals(150-axe.getMaxDamage()/4,axe.getDamageValue()); assertTrue(stock.getItem(1).isEmpty());
        assertEquals(2,axe.get(DataComponents.ENCHANTMENTS).getLevel(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING)));
        assertEquals("Old faithful",axe.get(DataComponents.CUSTOM_NAME).getString());
        stock.setItem(1,new ItemStack(Items.IRON_INGOT,4));
        int needed=BlacksmithRepair.materialsNeeded(axe);
        for(int i=0;i<needed;i++) assertTrue(BlacksmithRepair.repair(axe,stock));
        assertEquals(0,axe.getDamageValue()); assertEquals(4-needed,stock.getItem(1).getCount());
        assertFalse(BlacksmithRepair.repair(axe,stock),"A healthy item consumes no repair material");
        ItemStack armor=new ItemStack(Items.DIAMOND_CHESTPLATE); armor.setDamageValue(armor.getMaxDamage()-1);
        assertFalse(BlacksmithRepair.material(armor,new ItemStack(Items.IRON_INGOT)));
        assertTrue(BlacksmithRepair.material(armor,new ItemStack(Items.DIAMOND)));
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        ItemStack saved=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,axe).getOrThrow()).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(axe,saved),"In-flight repairs preserve the complete original stack across saves");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void foodHealingCostsMealsKeepsBowlsAndRespectsCooldown(MinecraftServer server) {
        SimpleContainer bag=new SimpleContainer(3);
        bag.setItem(0,new ItemStack(Items.COOKED_BEEF,2)); bag.setItem(1,new ItemStack(Items.ROTTEN_FLESH,2));
        List<ItemStack> remainder=new ArrayList<>();
        assertTrue(FoodHealing.due(2000,0,10,20)); assertFalse(FoodHealing.due(2000,20,10,20));
        assertTrue(FoodHealing.due(0,100,20,20)); assertFalse(FoodHealing.due(2000,0,20,20));
        ItemStack meal=FoodHealing.take(List.of(bag),remainder::add);
        assertEquals(8,FoodHealing.healing(meal,10,20)); assertEquals(2,FoodHealing.healing(meal,18,20));
        assertEquals(1,bag.getItem(0).getCount()); assertEquals(2,bag.getItem(1).getCount());
        assertTrue(remainder.isEmpty());
        bag.setItem(0,new ItemStack(Items.MUSHROOM_STEW));
        assertFalse(FoodHealing.take(List.of(bag),remainder::add).isEmpty());
        assertEquals(1,remainder.size()); assertTrue(remainder.getFirst().is(Items.BOWL));
        assertTrue(FoodHealing.take(List.of(bag),remainder::add).isEmpty());
        assertFalse(FoodHealing.food(new ItemStack(Items.BEEF))); assertFalse(FoodHealing.food(new ItemStack(Items.POISONOUS_POTATO)));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void illustratedGuideAndBlacksmithHardwareAreValid(MinecraftServer server) {
        var content=WWMC.GUIDE.get().getDefaultInstance().get(DataComponents.WRITTEN_BOOK_CONTENT);
        assertNotNull(content); assertEquals(GuideBook.pages().size(),content.pages().size());
        assertEquals(1,content.pages().size());
        assertTrue(WWMC.GUIDE.get() instanceof io.github.swishhyy.wwmc.item.GuideItem);
        assertTrue(GuideBook.TOPICS.size()<=12);
        for(String page:GuideBook.pages()) { assertTrue(page.split("\n",-1).length<=13); assertTrue(Arrays.stream(page.split("\n",-1)).allMatch(s -> s.length()<=18)); }
        for(StructureRole role:StructureRole.values()) assertTrue(GuideBook.STATIONS.stream().anyMatch(s -> s.role()==role),"Missing station reference: "+role);
        assertTrue(StationDetection.anvil(Blocks.ANVIL.defaultBlockState()));
        assertTrue(StationDetection.anvil(Blocks.CHIPPED_ANVIL.defaultBlockState()));
        assertFalse(StationDetection.anvil(Blocks.SMITHING_TABLE.defaultBlockState()));
    }
}
