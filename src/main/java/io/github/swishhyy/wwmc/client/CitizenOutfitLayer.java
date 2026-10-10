package io.github.swishhyy.wwmc.client;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;

/** Vanilla profession clothes plus job-colored sashes and hats; these are visual layers, never equipment items. */
public final class CitizenOutfitLayer extends RenderLayer<CitizenRenderer.State,CitizenModel> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(Identifier.fromNamespaceAndPath(WWMC.MODID,"citizen_outfit"),"main");
    public static final ModelLayerLocation PROFESSION=new ModelLayerLocation(Identifier.fromNamespaceAndPath(WWMC.MODID,"citizen_profession"),"main");
    private static final Identifier CLOTH=Identifier.withDefaultNamespace("textures/block/white_wool.png");
    private final CitizenModel accents,profession;
    private record Outfit(String profession,int color) {}
    private static Outfit outfit(StructureRole role) {
        return switch(role) {
            case FARM -> new Outfit("farmer",0xFFF0CF58);
            case GATHERER -> new Outfit("mason",0xFFAB9973);
            case LUMBER -> new Outfit("fletcher",0xFF467748);
            case MINE -> new Outfit("weaponsmith",0xFFE2B93F);
            case QUARRY -> new Outfit("armorer",0xFFE38033);
            case GUARD -> new Outfit("armorer",0xFFAF3942);
            case CRAFTSMAN -> new Outfit("mason",0xFFBC885B);
            case SMELTERY -> new Outfit("toolsmith",0xFFB24C32);
            case COOK -> new Outfit("butcher",0xFFF2EFE0);
            case BLACKSMITH -> new Outfit("weaponsmith",0xFF525D73);
            case COURIER -> new Outfit("leatherworker",0xFF40B3BE);
            case ENCHANTER -> new Outfit("cleric",0xFFB375DB);
            case TRADER -> new Outfit("cartographer",0xFFE9BE65);
            case HUNTER -> new Outfit("fletcher",0xFF784F37);
            case FISHERMAN -> new Outfit("fisherman",0xFF488DC4);
            case ANIMAL_KEEPER -> new Outfit("shepherd",0xFFB2CA6B);
            case BUTCHER -> new Outfit("butcher",0xFFC46D83);
            case HOSPITAL -> new Outfit("librarian",0xFFF3F6F5);
            case RESEARCHER -> new Outfit("librarian",0xFF5573BE);
            default -> new Outfit("none",0xFF8C7763);
        };
    }
    public CitizenOutfitLayer(CitizenRenderer parent,EntityRendererProvider.Context context) {
        super(parent); accents=new CitizenModel(context.bakeLayer(LAYER),true);
        profession=new CitizenModel(context.bakeLayer(PROFESSION),false,true);
    }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh=new MeshDefinition(); var root=mesh.getRoot();
        var head=root.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.ZERO);
        // A cap/hood crown and brim. Model texture coordinates wrap harmlessly over Minecraft's wool texture.
        head.addOrReplaceChild("hat",CubeListBuilder.create().texOffs(0,0).addBox(-4.6F,-10.6F,-4.6F,9.2F,3.0F,9.2F),PartPose.ZERO);
        head.addOrReplaceChild("brim",CubeListBuilder.create().texOffs(0,0).addBox(-5.2F,-8.0F,-5.2F,10.4F,0.6F,10.4F),PartPose.ZERO);
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-4.55F,9.0F,-3.55F,9.1F,2.0F,7.1F)
                .addBox(-1.2F,1.0F,-3.58F,2.4F,8.0F,0.25F),PartPose.ZERO);
        root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(0,0).addBox(-3.15F,5.0F,-2.15F,4.3F,1.4F,4.3F),PartPose.offset(-5.0F,2.0F,0));
        root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(0,0).addBox(-1.15F,5.0F,-2.15F,4.3F,1.4F,4.3F),PartPose.offset(5.0F,2.0F,0));
        root.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(-2,12,0));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(2,12,0));
        return LayerDefinition.create(mesh,16,16);
    }
    @Override public void submit(PoseStack poses,SubmitNodeCollector collector,int light,CitizenRenderer.State state,float yRot,float xRot) {
        if(state.isInvisible || state.job==null) return;
        Outfit outfit=outfit(state.job);
        Identifier clothes=Identifier.withDefaultNamespace("textures/entity/villager/profession/"+outfit.profession()+".png");
        renderColoredCutoutModel(profession,clothes,poses,collector,light,state,0xFFFFFFFF,1);
        renderColoredCutoutModel(accents,CLOTH,poses,collector,light,state,outfit.color(),2);
    }
}
