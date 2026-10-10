package io.github.swishhyy.wwmc;
import io.github.swishhyy.wwmc.client.CitizenRenderer;
import io.github.swishhyy.wwmc.client.StationRangePreview;
import io.github.swishhyy.wwmc.client.screen.CitizenScreen;
import io.github.swishhyy.wwmc.client.screen.CraftsmanScreen;
import io.github.swishhyy.wwmc.client.screen.PanelScreen;
import io.github.swishhyy.wwmc.client.screen.WwmcConfigScreen;
import io.github.swishhyy.wwmc.client.screen.TraderScreen;
import io.github.swishhyy.wwmc.menu.ViewMenu;
import io.github.swishhyy.wwmc.menu.WwmcMenus;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.Upgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;

@Mod(value=WWMC.MODID,dist=Dist.CLIENT)
public final class WWMCClient {
    public WWMCClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,WwmcConfigScreen::create);
        bus.addListener(WWMCClient::renderers);
        bus.addListener(WWMCClient::layers);
        bus.addListener(WWMCClient::screens);
        bus.addListener(WWMCClient::payloads);
        NeoForge.EVENT_BUS.register(new StationRangePreview());
        NeoForge.EVENT_BUS.addListener(WWMCClient::tooltip);
    }
    /** A station item from a broken, upgraded station names the upgrades it will bring back. */
    private static void tooltip(ItemTooltipEvent event) {
        if(io.github.swishhyy.wwmc.settlement.AgeProgression.required(event.getItemStack())>0)
            event.getToolTip().add(Component.literal("Settlement research: "+io.github.swishhyy.wwmc.settlement.AgeProgression.requirement(event.getItemStack())).withStyle(ChatFormatting.GOLD));
        if(event.getItemStack().getItem() instanceof BlockItem trapItem && trapItem.getBlock() instanceof io.github.swishhyy.wwmc.block.TrapBlock trap) {
            var saved=event.getItemStack().get(DataComponents.BLOCK_STATE);
            int wear=0;
            if(saved!=null) try { wear=Integer.parseInt(saved.properties().getOrDefault("wear","0")); } catch(NumberFormatException ignored) {}
            event.getToolTip().add(Component.literal(trap.kind().effect()).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(Math.max(0,trap.kind().uses-wear)+" uses remaining; hostile mobs only")
                    .withStyle(wear>=trap.kind().uses ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if(!(event.getItemStack().getItem() instanceof BlockItem item) || !(item.getBlock() instanceof StationBlock block)) return;
        var state=event.getItemStack().get(DataComponents.BLOCK_STATE);
        if(state==null) return;
        String range=state.properties().getOrDefault(StationBlock.RANGE.getName(),"0"),crew=state.properties().getOrDefault(StationBlock.CREW.getName(),"0");
        if(!range.equals("0")) event.getToolTip().add(Component.literal("Range upgrade "+range).withStyle(ChatFormatting.GREEN));
        if(Upgrades.hires(block.role()) && !crew.equals("0")) event.getToolTip().add(Component.literal("Crew upgrade "+crew).withStyle(ChatFormatting.GREEN));
        String yieldLevel=state.properties().getOrDefault(StationBlock.YIELD.getName(),"0");
        if(Upgrades.yields(block.role()) && !yieldLevel.equals("0")) event.getToolTip().add(Component.literal("Yield upgrade "+yieldLevel+" (+"+yieldLevel+"0%)").withStyle(ChatFormatting.GREEN));
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(WWMC.CITIZEN.get(),CitizenRenderer::new); }
    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CitizenRenderer.LAYER,CitizenRenderer::createBodyLayer);
        event.registerLayerDefinition(io.github.swishhyy.wwmc.client.CitizenOutfitLayer.PROFESSION,CitizenRenderer::createBodyLayer);
        event.registerLayerDefinition(io.github.swishhyy.wwmc.client.CitizenOutfitLayer.LAYER,io.github.swishhyy.wwmc.client.CitizenOutfitLayer::createBodyLayer);
    }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(WwmcMenus.ALLOY_FURNACE.get(),io.github.swishhyy.wwmc.client.screen.AlloyFurnaceScreen::new);
        event.register(WwmcMenus.PANEL.get(),PanelScreen::new);
        event.register(WwmcMenus.CRAFTSMAN.get(),CraftsmanScreen::new);
        event.register(WwmcMenus.CITIZEN.get(),CitizenScreen::new);
        event.register(WwmcMenus.TRADER.get(),TraderScreen::new);
        event.register(WwmcMenus.MAP.get(),io.github.swishhyy.wwmc.client.screen.MapScreen::new);
    }
    /** A refresh only applies to the screen it was built for. */
    private static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WwmcNetwork.GuidePayload.TYPE,(payload,context) ->
                io.github.swishhyy.wwmc.client.screen.GuideScreen.open(null,payload.topic()));
        event.register(WwmcNetwork.ViewPayload.TYPE,(payload,context) -> {
            if(context.player().containerMenu.containerId==payload.containerId() && context.player().containerMenu instanceof ViewMenu menu) menu.view(payload.view());
        });
        event.register(WwmcNetwork.HighlightPayload.TYPE,(payload,context) -> StationRangePreview.highlight(payload.pos(),payload.seconds()));
        event.register(WwmcNetwork.MapPayload.TYPE,(payload,context) -> {
            if(context.player().containerMenu.containerId==payload.containerId() && context.player().containerMenu instanceof io.github.swishhyy.wwmc.menu.MapMenu menu) menu.map=payload.map();
        });
    }
}
