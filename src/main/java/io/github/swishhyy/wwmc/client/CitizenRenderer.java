package io.github.swishhyy.wwmc.client;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;

/**
 * Villager head, robe and skin on a humanoid skeleton with free arms, so citizens visibly wear guard armor and hold
 * their weapons and tools. Armor uses Minecraft's zombie-villager armor shapes, which fit the taller villager head.
 */
public final class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity,CitizenRenderer.State,CitizenModel> {
    public static final class State extends HumanoidRenderState {
        public io.github.swishhyy.wwmc.core.StructureRole job;
        public int work;
        public boolean resting;
        public boolean armoredHead,armoredChest,armoredLegs,armoredFeet,rightHanded;
        public float workElapsed;
    }
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(Identifier.fromNamespaceAndPath(WWMC.MODID,"citizen"),"main");
    private static final Identifier SKIN=Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context,new CitizenModel(context.bakeLayer(LAYER)),0.5F);
        ArmorModelSet<HumanoidModel<State>> armor=ArmorModelSet.bake(ModelLayers.ZOMBIE_VILLAGER_ARMOR,context.getModelSet(),HumanoidModel::new);
        addLayer(new CitizenOutfitLayer(this,context));
        addLayer(new HumanoidArmorLayer<>(this,armor,context.getEquipmentRenderer()));
    }
    /** Villager texture layout: the upper arm uses the sleeve and the hand uses the skin of the folded-arms block. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0.0F);
        PartDefinition root=mesh.getRoot();
        PartDefinition head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4.0F,-10.0F,-4.0F,8.0F,10.0F,8.0F),PartPose.ZERO);
        head.addOrReplaceChild("hat",CubeListBuilder.create().texOffs(32,0).addBox(-4.0F,-10.0F,-4.0F,8.0F,10.0F,8.0F,new CubeDeformation(0.51F)),PartPose.ZERO);
        head.addOrReplaceChild("nose",CubeListBuilder.create().texOffs(24,0).addBox(-1.0F,-1.0F,-6.0F,2.0F,4.0F,2.0F),PartPose.offset(0.0F,-2.0F,0.0F));
        PartDefinition body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(16,20).addBox(-4.0F,0.0F,-2.0F,8.0F,12.0F,4.0F),PartPose.ZERO);
        body.addOrReplaceChild("jacket_upper",CubeListBuilder.create().texOffs(0,38).addBox(-4.0F,0.0F,-3.0F,8.0F,12.0F,6.0F,new CubeDeformation(0.5F)),PartPose.ZERO);
        body.addOrReplaceChild("jacket_lower",CubeListBuilder.create().texOffs(0,50).addBox(-4.0F,12.0F,-3.0F,8.0F,8.0F,6.0F,new CubeDeformation(0.5F)),PartPose.ZERO);
        root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(44,22).addBox(-3.0F,-2.0F,-2.0F,4.0F,8.0F,4.0F)
                .texOffs(40,38).addBox(-3.0F,6.0F,-2.0F,4.0F,4.0F,4.0F),PartPose.offset(-5.0F,2.0F,0.0F));
        root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(44,22).mirror().addBox(-1.0F,-2.0F,-2.0F,4.0F,8.0F,4.0F)
                .texOffs(40,38).addBox(-1.0F,6.0F,-2.0F,4.0F,4.0F,4.0F),PartPose.offset(5.0F,2.0F,0.0F));
        root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,22).addBox(-2.0F,0.0F,-2.0F,4.0F,12.0F,4.0F),PartPose.offset(-2.0F,12.0F,0.0F));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,22).mirror().addBox(-2.0F,0.0F,-2.0F,4.0F,12.0F,4.0F),PartPose.offset(2.0F,12.0F,0.0F));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return SKIN; }
    @Override public void extractRenderState(CitizenEntity citizen,State state,float partialTick) {
        super.extractRenderState(citizen,state,partialTick);
        state.job=citizen.appearanceJob(); state.work=citizen.workAnimation(); state.resting=citizen.isSleeping() || citizen.recovering();
        state.workElapsed=Math.max(0,citizen.level().getGameTime()-citizen.workAnimationBegan()+partialTick);
        state.armoredHead=!citizen.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
        state.armoredChest=!citizen.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty();
        state.armoredLegs=!citizen.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty();
        state.armoredFeet=!citizen.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
        HumanoidModel.ArmPose main=citizen.isUsingItem() && citizen.getUseItem().getItem() instanceof BowItem ? HumanoidModel.ArmPose.BOW_AND_ARROW
                : citizen.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        HumanoidModel.ArmPose off=citizen.isBlocking() ? HumanoidModel.ArmPose.BLOCK
                : citizen.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        boolean right=citizen.getMainArm()==HumanoidArm.RIGHT;
        state.rightHanded=right;
        state.rightArmPose=right ? main : off;
        state.leftArmPose=right ? off : main;
    }
}
