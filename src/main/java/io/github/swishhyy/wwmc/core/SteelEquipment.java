package io.github.swishhyy.wwmc.core;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.*;

/** A durable Iron Age alloy, below diamond in mining speed, mining tier and armor protection. */
public final class SteelEquipment {
    private SteelEquipment() {}
    public static final TagKey<Item> REPAIR=TagKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("c","ingots/steel"));
    public static final ToolMaterial TOOLS=new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL,500,7F,2.5F,14,REPAIR);
    public static final ResourceKey<EquipmentAsset> ASSET=ResourceKey.create(EquipmentAssets.ROOT_ID,Identifier.fromNamespaceAndPath("wwmc","steel"));
    public static final ArmorMaterial ARMOR=new ArmorMaterial(24,Map.of(ArmorType.HELMET,2,ArmorType.CHESTPLATE,7,ArmorType.LEGGINGS,5,ArmorType.BOOTS,2),
            12,SoundEvents.ARMOR_EQUIP_IRON,0,0,REPAIR,ASSET);
}
