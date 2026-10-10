package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.WWMC;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WwmcMenus {
    private WwmcMenus() {}
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,WWMC.MODID);
    public static final DeferredHolder<MenuType<?>,MenuType<AlloyFurnaceMenu>> ALLOY_FURNACE=MENUS.register("alloy_furnace",() -> IMenuTypeExtension.create((id,inventory,buf) -> new AlloyFurnaceMenu(id,inventory)));
    public static final DeferredHolder<MenuType<?>,MenuType<PanelMenu>> PANEL=MENUS.register("panel",() -> IMenuTypeExtension.create(PanelMenu::read));
    public static final DeferredHolder<MenuType<?>,MenuType<CraftsmanMenu>> CRAFTSMAN=MENUS.register("craftsman",() -> IMenuTypeExtension.create(CraftsmanMenu::read));
    public static final DeferredHolder<MenuType<?>,MenuType<CitizenMenu>> CITIZEN=MENUS.register("citizen",() -> IMenuTypeExtension.create(CitizenMenu::read));
    public static final DeferredHolder<MenuType<?>,MenuType<TraderMenu>> TRADER=MENUS.register("trader",() -> IMenuTypeExtension.create(TraderMenu::read));
    public static final DeferredHolder<MenuType<?>,MenuType<MapMenu>> MAP=MENUS.register("map",() -> IMenuTypeExtension.create(MapMenu::read));
}
