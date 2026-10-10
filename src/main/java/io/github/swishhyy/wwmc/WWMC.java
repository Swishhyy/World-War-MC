package io.github.swishhyy.wwmc;
import com.mojang.logging.LogUtils;
import io.github.swishhyy.wwmc.block.SettlementBannerBlock;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.block.TrapBlock;
import io.github.swishhyy.wwmc.core.TrapKind;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.BronzeEquipment;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.item.SurveyorItem;
import io.github.swishhyy.wwmc.item.GuideBook;
import io.github.swishhyy.wwmc.settlement.Carcasses;
import net.minecraft.world.item.Item;
import io.github.swishhyy.wwmc.menu.WwmcMenus;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import io.github.swishhyy.wwmc.settlement.WorkProtection;
import io.github.swishhyy.wwmc.settlement.GuardService;
import io.github.swishhyy.wwmc.settlement.DefenseService;
import io.github.swishhyy.wwmc.settlement.WaveService;
import io.github.swishhyy.wwmc.settlement.TradeChunks;
import io.github.swishhyy.wwmc.settlement.NpcSettlements;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import io.github.swishhyy.wwmc.item.GuideItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(WWMC.MODID)
public final class WWMC {
    public static final String MODID="wwmc";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Entities ENTITIES=DeferredRegister.createEntities(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MODID);
    // Both models are detailed rather than full cubes, so they must not hide their neighbours' faces.
    public static final DeferredBlock<SettlementBannerBlock> BANNER=BLOCKS.registerBlock("settlement_banner",SettlementBannerBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(2,3_600_000).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> BANNER_ITEM=ITEMS.registerSimpleBlockItem(BANNER);
    public static final DeferredBlock<Block> TIN_ORE=BLOCKS.registerBlock("tin_ore",Block::new,p -> p.mapColor(MapColor.STONE).strength(3,3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE=BLOCKS.registerBlock("deepslate_tin_ore",Block::new,p -> p.mapColor(MapColor.DEEPSLATE).strength(4.5F,3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> TIN_BLOCK=BLOCKS.registerBlock("tin_block",Block::new,p -> p.mapColor(MapColor.METAL).strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> RAW_TIN_BLOCK=BLOCKS.registerBlock("raw_tin_block",Block::new,p -> p.mapColor(MapColor.METAL).strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> BRONZE_BLOCK=BLOCKS.registerBlock("bronze_block",Block::new,p -> p.mapColor(MapColor.COLOR_ORANGE).strength(4,6).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> TIN_ORE_ITEM=ITEMS.registerSimpleBlockItem(TIN_ORE),
            DEEPSLATE_TIN_ORE_ITEM=ITEMS.registerSimpleBlockItem(DEEPSLATE_TIN_ORE),TIN_BLOCK_ITEM=ITEMS.registerSimpleBlockItem(TIN_BLOCK),
            RAW_TIN_BLOCK_ITEM=ITEMS.registerSimpleBlockItem(RAW_TIN_BLOCK),BRONZE_BLOCK_ITEM=ITEMS.registerSimpleBlockItem(BRONZE_BLOCK);
    public static final DeferredItem<Item> RAW_TIN=material("raw_tin","tooltip.wwmc.raw_tin"),
            TIN_INGOT=material("tin_ingot","tooltip.wwmc.tin_ingot"),
            BRONZE_BLEND=material("bronze_blend","tooltip.wwmc.bronze_blend.craft","tooltip.wwmc.bronze_blend.smelt","tooltip.wwmc.bronze_blend.research"),
            BRONZE_INGOT=ITEMS.registerSimpleItem("bronze_ingot");
    private static DeferredItem<Item> material(String name,String... hints) {
        return ITEMS.registerItem(name,Item::new,p -> p.component(DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.Arrays.stream(hints).map(Component::translatable)
                        .map(c -> (Component)c).toList())));
    }
    public static final DeferredItem<Item> BRONZE_SWORD=ITEMS.registerItem("bronze_sword",Item::new,p -> p.sword(BronzeEquipment.TOOLS,3,-2.4F));
    public static final DeferredItem<Item> BRONZE_PICKAXE=ITEMS.registerItem("bronze_pickaxe",Item::new,p -> p.pickaxe(BronzeEquipment.TOOLS,1,-2.8F));
    public static final DeferredItem<Item> BRONZE_AXE=ITEMS.registerItem("bronze_axe",Item::new,p -> p.axe(BronzeEquipment.TOOLS,6,-3.1F));
    public static final DeferredItem<Item> BRONZE_SHOVEL=ITEMS.registerItem("bronze_shovel",Item::new,p -> p.shovel(BronzeEquipment.TOOLS,1.5F,-3));
    public static final DeferredItem<Item> BRONZE_HOE=ITEMS.registerItem("bronze_hoe",Item::new,p -> p.hoe(BronzeEquipment.TOOLS,-1.5F,-1));
    public static final DeferredItem<Item> BRONZE_HELMET=ITEMS.registerItem("bronze_helmet",Item::new,p -> p.humanoidArmor(BronzeEquipment.ARMOR,ArmorType.HELMET));
    public static final DeferredItem<Item> BRONZE_CHESTPLATE=ITEMS.registerItem("bronze_chestplate",Item::new,p -> p.humanoidArmor(BronzeEquipment.ARMOR,ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> BRONZE_LEGGINGS=ITEMS.registerItem("bronze_leggings",Item::new,p -> p.humanoidArmor(BronzeEquipment.ARMOR,ArmorType.LEGGINGS));
    public static final DeferredItem<Item> BRONZE_BOOTS=ITEMS.registerItem("bronze_boots",Item::new,p -> p.humanoidArmor(BronzeEquipment.ARMOR,ArmorType.BOOTS));
    public static final Map<StructureRole,DeferredBlock<StationBlock>> STATIONS=new EnumMap<>(StructureRole.class);
    public static final Map<StructureRole,DeferredItem<BlockItem>> STATION_ITEMS=new EnumMap<>(StructureRole.class);
    static {
        for(StructureRole role:StructureRole.values()) {
            var block=BLOCKS.registerBlock(role.id()+"_station",p -> new StationBlock(role,p),p -> p.mapColor(MapColor.WOOD).strength(2).noOcclusion());
            STATIONS.put(role,block); STATION_ITEMS.put(role,ITEMS.registerSimpleBlockItem(block));
        }
    }
    public static final Map<TrapKind,DeferredBlock<TrapBlock>> TRAPS=new EnumMap<>(TrapKind.class);
    public static final Map<TrapKind,DeferredItem<BlockItem>> TRAP_ITEMS=new EnumMap<>(TrapKind.class);
    static {
        for(TrapKind kind:TrapKind.values()) {
            var block=BLOCKS.registerBlock(kind.id,p -> new TrapBlock(kind,p),p -> p.mapColor(MapColor.WOOD).strength(1.5F)
                    .noCollision().noOcclusion().pushReaction(PushReaction.BLOCK));
            TRAPS.put(kind,block); TRAP_ITEMS.put(kind,ITEMS.registerSimpleBlockItem(block));
        }
    }
    public static final Map<Carcasses.Kind,DeferredItem<Item>> CARCASSES=new EnumMap<>(Carcasses.Kind.class);
    static { for(var kind:Carcasses.Kind.values()) CARCASSES.put(kind,ITEMS.registerItem(kind.id+"_carcass",Item::new,p -> p.stacksTo(16))); }
    public static final DeferredItem<SurveyorItem> SURVEYOR=ITEMS.registerItem("surveyor",SurveyorItem::new,p -> p.stacksTo(1));
    public static final DeferredItem<GuideItem> GUIDE=ITEMS.registerItem("settlement_guide",GuideItem::new,
            p -> p.stacksTo(1).component(DataComponents.WRITTEN_BOOK_CONTENT,GuideBook.content()));
    public static final DeferredHolder<EntityType<?>,EntityType<CitizenEntity>> CITIZEN=ENTITIES.registerEntityType("citizen",CitizenEntity::new,MobCategory.CREATURE,b -> b.sized(0.6F,1.95F).clientTrackingRange(10));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("settlement",() -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.wwmc")).withTabsBefore(CreativeModeTabs.COMBAT)
        .icon(() -> BANNER_ITEM.get().getDefaultInstance()).displayItems((p,out) -> {
            out.accept(BANNER_ITEM.get()); out.accept(SURVEYOR.get()); out.accept(GUIDE.get());
            for(var item:java.util.List.of(TIN_ORE_ITEM,DEEPSLATE_TIN_ORE_ITEM,TIN_BLOCK_ITEM,RAW_TIN_BLOCK_ITEM,BRONZE_BLOCK_ITEM,
                    RAW_TIN,TIN_INGOT,BRONZE_BLEND,BRONZE_INGOT,BRONZE_SWORD,BRONZE_PICKAXE,BRONZE_AXE,BRONZE_SHOVEL,BRONZE_HOE,
                    BRONZE_HELMET,BRONZE_CHESTPLATE,BRONZE_LEGGINGS,BRONZE_BOOTS)) out.accept(item.get());
            for(StructureRole role:StructureRole.values()) out.accept(STATION_ITEMS.get(role).get());
            for(TrapKind kind:TrapKind.values()) out.accept(TRAP_ITEMS.get(kind).get());
            for(var kind:Carcasses.Kind.values()) out.accept(CARCASSES.get(kind).get());
        }).build());
    public WWMC(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        WwmcMenus.MENUS.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(WwmcNetwork::register);
        bus.addListener(TradeChunks::register);
        bus.addListener(io.github.swishhyy.wwmc.settlement.CitizenRecall::register);
        NeoForge.EVENT_BUS.register(new SettlementService());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.ClaimProtection());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.MultiplayerCombat());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.MultiplayerCommands());
        NeoForge.EVENT_BUS.register(new Carcasses());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.AgeProgression());
        NeoForge.EVENT_BUS.register(new WorkProtection());
        NeoForge.EVENT_BUS.register(new GuardService());
        NeoForge.EVENT_BUS.register(new DefenseService());
        NeoForge.EVENT_BUS.register(new WaveService());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.TrapService());
        NeoForge.EVENT_BUS.register(new TradeChunks());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.CitizenRecall());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.TradeAtlas.Survey());
        NeoForge.EVENT_BUS.register(new NpcSettlements());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.CampaignService());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.CampaignCommands());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.ExpeditionService());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.CitizenCombat());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.SettlementMap());
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.wwmc.settlement.TutorialProgress());
        container.registerConfig(ModConfig.Type.SERVER,Config.SPEC);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent event) -> LOGGER.info(
                "[WWMC][server-start] version={} diagnostics={} stallSeconds={} repeatSeconds={}",
                container.getModInfo().getVersion(),Config.SERVER_DIAGNOSTICS.get(),Config.DIAGNOSTIC_DELAY.get(),Config.DIAGNOSTIC_REPEAT.get()));
    }
    private void attributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(),Villager.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,2.0).build());
    }
}
