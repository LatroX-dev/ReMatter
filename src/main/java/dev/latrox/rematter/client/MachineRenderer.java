package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.MachineBlock;
import dev.latrox.rematter.content.MachineBlockEntity;
import dev.latrox.rematter.content.MachineCellAccess;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModContent;
import net.minecraft.world.item.ItemStack;
import dev.latrox.rematter.content.IndustrialStructure;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/** Crystal instruments. Every working pose comes from server-reported process progress. */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("rematter", "mechanisms"), "main");
    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("rematter", "textures/entity/light.png");
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath("rematter", "textures/block/reaction_chamber_crystal.png");
    private static final Map<MachineKind,Identifier> CRYSTALS = familyTextures("block");
    private static final Map<MachineKind,Identifier> SIGNALS = familyTextures("entity");
    private static final Identifier HEATER = Identifier.fromNamespaceAndPath("rematter", "textures/block/copper_trim.png");
    private static final Identifier CONNECTOR_BODY = Identifier.fromNamespaceAndPath("rematter", "textures/block/graphite.png");
    private static final Identifier CONNECTOR_METAL = Identifier.fromNamespaceAndPath("rematter", "textures/block/brushed_steel.png");
    private final Map<MachineBlockEntity,ProcessVisualMotion> motion=new WeakHashMap<>();
    private final ItemModelResolver itemModelResolver;
    private final ModelPart core, halo, trace, beam, mote, handCrank, handGrip;
    private final ItemStack[][] cellVisuals = new ItemStack[5][dev.latrox.rematter.content.CellStatus.values().length];
    private final ModelPart handRoller, handSieve, handMixer;

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
        ModelPart root = context.bakeLayer(LAYER);
        core=root.getChild("core"); halo=root.getChild("halo"); trace=root.getChild("trace");
        beam=root.getChild("beam"); mote=root.getChild("mote");
        handRoller=root.getChild("hand_roller");handSieve=root.getChild("hand_sieve");handMixer=root.getChild("hand_mixer");
        handCrank=root.getChild("hand_crank"); handGrip=root.getChild("hand_grip");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var crystal=root.addOrReplaceChild("core",CubeListBuilder.create().texOffs(0,0)
                .addBox(-1.3F,-1.5F,-1.3F,2.6F,3,2.6F)
                .addBox(-.52F,-2.5F,-.52F,1.04F,5,1.04F),PartPose.ZERO);
        crystal.addOrReplaceChild("cut_facets",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.936F,-1.9F,-.936F,1.872F,3.8F,1.872F),PartPose.rotation(0,Mth.PI/4,0));
        var ring=root.addOrReplaceChild("halo",CubeListBuilder.create(),PartPose.ZERO);
        var glow=root.addOrReplaceChild("trace",CubeListBuilder.create(),PartPose.ZERO);
        for(int i=0;i<8;i++) {
            float angle=i*Mth.PI/4;
            ring.addOrReplaceChild("segment_"+i,CubeListBuilder.create().texOffs(0,0)
                    .addBox(-1.45F,-.2F,3.5F,2.9F,.4F,.55F),PartPose.rotation(0,angle,0));
            glow.addOrReplaceChild("segment_"+i,CubeListBuilder.create().texOffs(0,0)
                    .addBox(-.75F,-.06F,3.39F,1.5F,.12F,.15F),PartPose.rotation(0,angle,0));
        }
        root.addOrReplaceChild("beam",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.095F,0,-.095F,.19F,1,.19F),PartPose.ZERO);
        root.addOrReplaceChild("mote",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.22F,-.22F,-.22F,.44F,.44F,.44F),PartPose.ZERO);
        var roller=root.addOrReplaceChild("hand_roller",CubeListBuilder.create().texOffs(0,0)
                .addBox(-4,-.99F,-.99F,8,1.98F,1.98F).addBox(-4.6F,-.25F,-.25F,9.2F,.5F,.5F),PartPose.ZERO);
        roller.addOrReplaceChild("facets",CubeListBuilder.create().texOffs(0,0)
                .addBox(-4,-.99F,-.99F,8,1.98F,1.98F),PartPose.rotation(Mth.PI/4,0,0));
        var meshGrid=CubeListBuilder.create().texOffs(0,0)
                .addBox(-4.4F,-.15F,-3, .4F,.3F,6).addBox(4,-.15F,-3,.4F,.3F,6)
                .addBox(-4,-.15F,-3,8,.3F,.4F).addBox(-4,-.15F,2.6F,8,.3F,.4F);
        for(int x=-4;x<=4;x++)meshGrid.addBox(x,-.06F,-2.6F,.1F,.12F,5.2F);
        for(int z=-3;z<=2;z++)meshGrid.addBox(-4,-.06F,z+.3F,8,.12F,.1F);
        root.addOrReplaceChild("hand_sieve",meshGrid,PartPose.ZERO);
        root.addOrReplaceChild("hand_mixer",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.2F,-.8F,-.2F,.4F,2.6F,.4F)
                .addBox(-3,0,-.3F,6,.4F,.6F).addBox(-.3F,0,-3,.6F,.4F,6),PartPose.ZERO);
        root.addOrReplaceChild("hand_crank",CubeListBuilder.create().texOffs(0,0)
                .addBox(-1.7F,-.2F,-.2F,2,.4F,.4F)
                .texOffs(0,16).addBox(-.15F,-.22F,-.22F,.3F,1.72F,.44F),PartPose.ZERO);
        root.addOrReplaceChild("hand_grip",CubeListBuilder.create().texOffs(0,0)
                .addBox(.15F,1.15F,-.25F,.7F,.4F,.5F),PartPose.ZERO);
        return LayerDefinition.create(mesh,32,32);
    }

    /** The extracted state that a kind's visuals read. Everything else is never extracted. */
    enum Detail {
        NONE, MOTION, ITEMS, CELLS;

        static Detail of(MachineKind kind) {
            return switch (kind) {
                case TERMINAL, NETWORK_INTERFACE -> NONE;
                case ELECTROLYZER, REACTOR, SEPARATOR, THERMAL_PROCESSOR, AIR_SEPARATOR -> MOTION;
                case DRIVE -> CELLS;
                case ANALYZER, DECOMPOSER, SYNTHESIZER, CRYSTALLIZER, PREPARATOR, HAND_PREPARATOR, SINTER_FURNACE,
                        INDUSTRIAL_PREPARATOR, INDUSTRIAL_REACTOR -> ITEMS;
            };
        }
    }

    @Override public MachineRenderState createRenderState() { return new MachineRenderState(); }
    @Override public boolean shouldRender(MachineBlockEntity machine,Vec3 camera) {
        return Detail.of(machine.kind()) != Detail.NONE
                && (!IndustrialStructure.isIndustrial(machine.kind()) || machine.industrialFormed())
                && BlockEntityRenderer.super.shouldRender(machine,camera);
    }
    @Override public AABB getRenderBoundingBox(MachineBlockEntity machine) {
        if(!IndustrialStructure.isIndustrial(machine.kind()) || !machine.industrialFormed()) return new AABB(machine.getBlockPos());
        var facing=machine.getBlockState().getValue(MachineBlock.FACING);
        var first=machine.getBlockPos().relative(facing.getClockWise(),-machine.industrialControllerColumn()).below();
        var last=first.relative(facing.getClockWise(),machine.industrialWidth()-1)
                .relative(facing.getOpposite(),machine.industrialDepth()-1).above(machine.industrialHeight()-1);
        return new AABB(Math.min(first.getX(),last.getX()),Math.min(first.getY(),last.getY()),Math.min(first.getZ(),last.getZ()),
                Math.max(first.getX(),last.getX())+1,Math.max(first.getY(),last.getY())+1,Math.max(first.getZ(),last.getZ())+1);
    }
    @Override public int getViewDistance() { return 48; }
    @Override public void extractRenderState(MachineBlockEntity machine,MachineRenderState state,float partialTick,
            Vec3 cameraPosition,ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine,state,partialTick,cameraPosition,breakProgress);
        state.kind=machine.kind(); state.facing=machine.getBlockState().getValue(MachineBlock.FACING);
        state.active=machine.getBlockState().getValue(MachineBlock.ACTIVE);
        var detail=Detail.of(state.kind);
        if(detail==Detail.CELLS) {
            state.cellMask=machine.visibleCellMask();
            for(int bay=0;bay<MachineCellAccess.DRIVE_BAYS;bay++) {
                state.cellTiers[bay]=Math.clamp(machine.visibleCellTier(bay),0,4);
                var appearance=(state.cellMask&(1<<bay))!=0?cellAppearance(state.cellTiers[bay],machine.visibleCellStatus(bay)):ItemStack.EMPTY;
                itemModelResolver.updateForTopItem(state.cellItems[bay],appearance,ItemDisplayContext.FIXED,
                        machine.getLevel(),null,(int)machine.getBlockPos().asLong()+bay+20);
            }
        }
        if(detail!=Detail.MOTION&&detail!=Detail.ITEMS)return;
        state.preparationStage=machine.preparationStage();state.preparationMixing=machine.preparationMixing();
        double time=machine.getLevel()==null?0:machine.getLevel().getGameTime()+(double)partialTick;
        state.phase=motion.computeIfAbsent(machine,key->new ProcessVisualMotion()).sample(time,machine.visualProgress(),machine.visualDuration(),state.active);
        state.hasProcess=machine.visualDuration()>0;
        state.drive=state.active?1:state.hasProcess?.25F:0;
        state.industrialWidth=machine.industrialWidth();state.industrialHeight=machine.industrialHeight();
        state.industrialDepth=machine.industrialDepth();state.industrialColumn=machine.industrialControllerColumn();
        if(detail!=Detail.ITEMS)return;
        int seed=(int)machine.getBlockPos().asLong();
        itemModelResolver.updateForTopItem(state.inputItem,machine.visibleInputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed);
        itemModelResolver.updateForTopItem(state.secondaryItem,machine.visibleSecondaryItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+3);
        itemModelResolver.updateForTopItem(state.tertiaryItem,machine.visibleTertiaryItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+4);
        itemModelResolver.updateForTopItem(state.outputItem,machine.visibleOutputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+1);
        itemModelResolver.updateForTopItem(state.processInputItem,machine.visibleProcessInputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+5);
        itemModelResolver.updateForTopItem(state.processOutputItem,machine.visibleProcessOutputItem(),ItemDisplayContext.FIXED,machine.getLevel(),null,seed+6);
    }

    private ItemStack cellAppearance(int tier,dev.latrox.rematter.content.CellStatus status) {
        if(tier==0)return ItemStack.EMPTY;
        // Renderer construction also runs before item components are bound during startup.
        // Resolve stacks only when extracting an actual machine in a loaded world.
        int color=status.ordinal();
        if(cellVisuals[tier][color]==null) {
            var stack=new ItemStack(switch(tier) {
            case 1 -> ModContent.MATTER_CELL_64K.get();
            case 2 -> ModContent.MATTER_CELL_256K.get();
            case 3 -> ModContent.MATTER_CELL_1M.get();
            default -> ModContent.CELL_HOUSING.get();
            });
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new net.minecraft.world.item.component.CustomModelData(java.util.List.of(),java.util.List.of(),
                            java.util.List.of("rematter:cell_led/"+status.name().toLowerCase(java.util.Locale.ROOT)),java.util.List.of()));
            cellVisuals[tier][color]=stack;
        }
        return cellVisuals[tier][color];
    }

    @Override public void submit(MachineRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose();pose.translate(.5F,0,.5F);pose.mulPose(Axis.YP.rotationDegrees(180-state.facing.toYRot()));pose.translate(-.5F,0,-.5F);
        float p=state.phase;
        switch(state.kind) {
            case HAND_PREPARATOR -> {
                // All movement follows actual hand-crank work. The starter has no crystal or powered field.
                pose.pushPose();pose.translate(15.1F/16,13.5F/16,6F/16);pose.mulPose(Axis.XP.rotationDegrees(p*720));
                part(handCrank,CONNECTOR_BODY,false,1,state,pose,collector);part(handGrip,CONNECTOR_METAL,false,1,state,pose,collector);pose.popPose();
                float crushing=Math.min(1,p*3)*720;
                for(int roll=0;roll<2;roll++) {
                    pose.pushPose();pose.translate(.5F,11F/16,(roll==0?5.8F:9.6F)/16);
                    pose.mulPose(Axis.XP.rotationDegrees(roll==0?crushing:-crushing));
                    part(handRoller,CONNECTOR_METAL,false,1,state,pose,collector);pose.popPose();
                }
                float separation=state.preparationMixing?Math.clamp((p-2F/3)*3,0,1):Math.clamp((p-1F/3)*3,0,1);
                pose.pushPose();pose.translate((8+Mth.sin(separation*Mth.TWO_PI*6)*.18F)/16,7F/16,7.7F/16);
                part(handSieve,CONNECTOR_METAL,false,1,state,pose,collector);pose.popPose();
                pose.pushPose();pose.translate(.5F,4.5F/16,7.8F/16);
                pose.mulPose(Axis.YP.rotationDegrees(state.preparationMixing?Math.clamp((p-1F/3)*3,0,1)*720:0));
                part(handMixer,CONNECTOR_METAL,false,1,state,pose,collector);pose.popPose();
                item(state.inputItem,5.5F,12.43F,5.8F,3,2.1F,2.6F,state,pose,collector);
                item(state.secondaryItem,10.5F,12.43F,5.8F,3,2.1F,2.6F,state,pose,collector);
                item(state.tertiaryItem,8,12.43F,9.6F,3,2.1F,2.6F,state,pose,collector);
                item(state.outputItem,8,3.27F,2.9F,6,2.3F,3.4F,state,pose,collector);
            }
            case PREPARATOR -> {
                float y=6;
                halo(8,y+2.1F,7.7F,p*180,0,.83F,state,pose,collector);
                transformItems(8,y+1.5F,7.7F,3.2F,state,pose,collector);
                if(state.hasProcess)for(float x:new float[]{3.2F,8,12.8F})beam(x,11.8F,12,8,y+2,7.7F,state,pose,collector);
                item(state.outputItem,8,3.57F,2.9F,6,2.5F,3.4F,state,pose,collector);
            }
            case ANALYZER -> {
                // A scanning hoop traverses the sample once per actual analysis.
                float y=state.hasProcess?5.4F+5*ease(p):7.5F;
                halo(8,y,7.5F,0,0,1,state,pose,collector);
                floatingItem(state.inputItem,8,7.2F,7.5F,4.4F,0,state,pose,collector);
                if(state.hasProcess)beam(8,11.6F,11.6F,8,y,7.5F,state,pose,collector);
            }
            case DECOMPOSER -> {
                halo(8,6.2F,7.6F,-p*180,0,.85F,state,pose,collector);
                transformItems(8,7,7.6F,4.3F,state,pose,collector);
                if(state.hasProcess) {
                    beam(8,7,7.6F,3.4F,10.6F,6.1F,state,pose,collector);
                    beam(8,7,7.6F,12.6F,10.6F,6.1F,state,pose,collector);
                    fragments(8,7,7.6F,3.6F,p,false,state,pose,collector);
                }
            }
            case SYNTHESIZER, CRYSTALLIZER -> {
                halo(8,6.1F,7.4F,p*180,0,.88F,state,pose,collector);
                transformItems(8,7.6F,7.4F,4.2F,state,pose,collector);
                if(state.hasProcess) {
                    beam(3,9,4.2F,8,7.6F,7.4F,state,pose,collector);
                    beam(13,9,4.2F,8,7.6F,7.4F,state,pose,collector);
                    beam(8,13,12.4F,8,7.6F,7.4F,state,pose,collector);
                    fragments(8,7.6F,7.4F,3.3F,p,true,state,pose,collector);
                }
                item(state.outputItem,8,3.92F,2.1F,5.6F,2.8F,2.4F,state,pose,collector);
            }
            case ELECTROLYZER, THERMAL_PROCESSOR, AIR_SEPARATOR -> {
                for(int pole=0;pole<2;pole++) {
                    float x=pole==0?4.3F:11.7F;
                    float y=state.hasProcess?6+6*(pole==0?p:1-p):9;
                    halo(x,y,7.7F,p*180*(pole==0?1:-1),0,.55F,state,pose,collector);
                    if(state.hasProcess)beam(x,5.5F,7.7F,x,12.8F,7.7F,state,pose,collector);
                }
            }
            case REACTOR, SEPARATOR -> {
                crystal(8,8.5F,8,p*180,1,false,state,pose,collector);
                halo(8,8.5F,8,p*90,45,1.05F,state,pose,collector);
                if(state.hasProcess)beam(8,4.8F,8,8,13.2F,8,state,pose,collector);
            }
            case SINTER_FURNACE -> {
                float heat=state.hasProcess?Math.max(.12F,Mth.sin(p*Mth.PI)):0;
                halo(8,7.7F,8.8F,p*90,0,.78F,state,pose,collector);
                {
                    for(float x:new float[]{4.4F,11.6F}) {
                        pose.pushPose();pose.translate(x/16,8.2F/16,10.7F/16);pose.scale(.45F,.8F,.45F);
                        part(core,HEATER,heat>0,heat>0?heat:1,state,pose,collector);pose.popPose();
                    }
                }
                transformItems(8,7.8F,8.8F,4.5F,state,pose,collector);
                item(state.outputItem,8,4.18F,2.75F,6.8F,2.6F,3.1F,state,pose,collector);
            }
            case DRIVE -> {
                // Real registered item models: texture packs, tier windows and item art stay identical.
                // Bays are numbered top-to-bottom on the left, then top-to-bottom on the right.
                for(int bay=0;bay<MachineCellAccess.DRIVE_BAYS;bay++)if((state.cellMask&(1<<bay))!=0) {
                    // The local front faces north: +X is left when standing in front and looking south.
                    float x=11.6F-MachineCellAccess.driveColumn(bay)*7.2F,y=12.1F-MachineCellAccess.driveRow(bay)*4F;
                    mountedCell(state.cellItems[bay],x,y,.55F,state,pose,collector);
                }
            }
            case INDUSTRIAL_PREPARATOR,INDUSTRIAL_REACTOR -> {
                if(state.industrialWidth>0) {
                    float x=(state.industrialWidth/2F-state.industrialColumn)*16;
                    float y=(state.industrialHeight/2F-1)*16;
                    float z=state.industrialDepth/2F*16;
                    boolean reactor=state.kind==MachineKind.INDUSTRIAL_REACTOR;
                    float size=Math.min(2.8F,Math.min(state.industrialWidth-2,state.industrialDepth-2)*1.35F);
                    halo(x,y,z,p*(reactor?180:-180),reactor?55:0,size,state,pose,collector);
                    if(reactor)halo(x,y,z,-p*90,-55,size,state,pose,collector);
                    else halo(x,y+3,z,p*90,0,size*.85F,state,pose,collector);
                    transformItems(x,y,z,Math.min(12,size*4.5F),state,pose,collector);
                    if(reactor)crystal(x,y,z,p*180,Math.min(2,size),state.hasProcess,state,pose,collector);
                }
            }
            default -> { }
        }
        pose.popPose();
    }

    private static float ease(float x) { x=Math.clamp(x,0,1);return x*x*(3-2*x); }

    /** The change of item model is inside an opaque, small light core, never in open view. */
    private void transformItems(float x,float y,float z,float size,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        float p=state.phase;
        var source=state.processInputItem.isEmpty()?state.inputItem:state.processInputItem;
        if(!state.hasProcess) {
            floatingItem(source,x,y,z,size,0,state,pose,collector);
            floatingItem(state.secondaryItem,x-3,y-.4F,z-1,size*.5F,0,state,pose,collector);
            floatingItem(state.tertiaryItem,x+3,y-.4F,z-1,size*.5F,0,state,pose,collector);
            return;
        }
        float inputScale=1-ease((p-.32F)/.16F);
        float outputScale=ease((p-.62F)/.2F);
        float lift=ease(p/.3F)*.8F;
        if(inputScale>.001F) {
            floatingItem(source,x,y+lift,z,size*inputScale,p*45,state,pose,collector);
            float spread=3*(1-ease(p/.5F));
            floatingItem(state.secondaryItem,x-spread,y+lift-.3F,z-1,size*.5F*inputScale,-p*45,state,pose,collector);
            floatingItem(state.tertiaryItem,x+spread,y+lift-.3F,z-1,size*.5F*inputScale,p*45,state,pose,collector);
        }
        if(outputScale>.001F)floatingItem(state.processOutputItem,x,y+(1-outputScale)*.8F,z,size*outputScale,0,state,pose,collector);
        float mask=ease((p-.3F)/.16F)*(1-ease((p-.65F)/.2F));
        if(mask>.001F)crystal(x,y+.6F,z,p*180,size*.31F*mask,true,state,pose,collector);
    }

    private void halo(float x,float y,float z,float yaw,float tilt,float scale,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.mulPose(Axis.ZP.rotationDegrees(tilt));pose.scale(scale,scale,scale);
        part(halo,CONNECTOR_BODY,false,1,state,pose,collector);
        part(trace,CRYSTAL,state.hasProcess,Math.max(.25F,state.drive),state,pose,collector);pose.popPose();
    }
    private void crystal(float x,float y,float z,float rotation,float scale,boolean glow,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(rotation));pose.scale(scale,scale,scale);
        part(core,CRYSTAL,glow,glow?Math.max(.3F,state.drive):1,state,pose,collector);pose.popPose();
    }
    private void beam(float x1,float y1,float z1,float x2,float y2,float z2,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<.01F)return;
        pose.pushPose();pose.translate(x1/16,y1/16,z1/16);pose.mulPose(new Quaternionf().rotationTo(0,1,0,dx/length,dy/length,dz/length));pose.scale(1,length,1);
        part(beam,LIGHT,true,Math.max(.18F,state.drive*.8F),state,pose,collector);pose.popPose();
    }
    private void fragments(float x,float y,float z,float radius,float p,boolean inward,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        float t=Math.clamp((p-.24F)/.55F,0,1);if(t==0||t==1)return;
        float distance=radius*(inward?1-ease(t):ease(t));
        float scale=Mth.sin(t*Mth.PI);
        for(int i=0;i<6;i++) {
            float angle=i*Mth.TWO_PI/6+p*Mth.PI;
            pose.pushPose();pose.translate((x+Mth.cos(angle)*distance)/16,(y+.7F+Mth.sin(angle*2)*.6F)/16,(z+Mth.sin(angle)*distance)/16);
            pose.scale(scale,scale,scale);part(mote,LIGHT,true,Math.max(.2F,state.drive),state,pose,collector);pose.popPose();
        }
    }
    /** Fit the real fixed-context item uniformly in front of the socket, without distorting its art. */
    private static void mountedCell(ItemStackRenderState item,float x,float y,float z,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        if(item.isEmpty())return;
        var b=item.getModelBoundingBox();
        double sx=b.getXsize(),sy=b.getYsize(),sz=b.getZsize();
        if(!Double.isFinite(sx+sy+sz)||sx<=0||sy<=0||sz<0)return;
        // Match the clear space between the bay rails without flattening the cartridge.
        double scale=Math.min(5.4/sx,Math.min(2.9/sy,13.4/Math.max(sz,.00001)))/16;
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.scale((float)scale,(float)scale,(float)scale);
        // The model's north face is its handle; its full depth extends into the drive.
        pose.translate(-(b.minX+b.maxX)/2,-(b.minY+b.maxY)/2,-b.minZ);
        item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    private static void floatingItem(ItemStackRenderState item,float x,float y,float z,float size,float yaw,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        if(item.isEmpty()||size<=.001F)return;
        var bounds=item.getModelBoundingBox();double extent=Math.max(bounds.getXsize(),Math.max(bounds.getYsize(),bounds.getZsize()));
        if(!Double.isFinite(extent)||extent<=0)return;
        double scale=size/extent/16;
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.scale((float)scale,(float)scale,(float)scale);
        pose.translate(-(bounds.minX+bounds.maxX)/2,-(bounds.minY+bounds.maxY)/2,-(bounds.minZ+bounds.maxZ)/2);
        item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    private static void part(ModelPart part,Identifier texture,boolean glow,float brightness,MachineRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        if(texture.equals(CRYSTAL))texture=CRYSTALS.getOrDefault(state.kind,texture);
        else if(texture.equals(LIGHT))texture=SIGNALS.getOrDefault(state.kind,texture);
        int value=(int)(255*Math.clamp(brightness,0,1));int tint=glow?0xff000000|value<<16|value<<8|value:-1;
        collector.submitModelPart(part,pose,RenderTypes.entitySolid(texture),glow?15_728_880:state.lightCoords,OverlayTexture.NO_OVERLAY,null,tint,state.breakProgress);
    }
    private static Map<MachineKind,Identifier> familyTextures(String folder) {
        var textures=new java.util.EnumMap<MachineKind,Identifier>(MachineKind.class);
        for(var kind:MachineKind.values()) {
            String family=switch(kind) {
                case ANALYZER, AIR_SEPARATOR -> "ice";
                case DECOMPOSER -> "copper";
                case SYNTHESIZER -> "leaf";
                case ELECTROLYZER,NETWORK_INTERFACE -> "blue";
                case REACTOR,INDUSTRIAL_REACTOR,CRYSTALLIZER -> "violet";
                case PREPARATOR,INDUSTRIAL_PREPARATOR,SEPARATOR -> "amber";
                case SINTER_FURNACE,THERMAL_PROCESSOR -> "ember";
                case DRIVE,TERMINAL -> "mint";
                default -> null;
            };
            if(family!=null)textures.put(kind,Identifier.fromNamespaceAndPath("rematter","textures/"+folder+"/machine_"+family+".png"));
        }
        return Map.copyOf(textures);
    }

    /** Fit the resolved model, including its native fixed transform, onto a work surface. */
    private static void item(ItemStackRenderState item, float x, float surfaceY, float z,
            float width, float height, float depth, MachineRenderState state,
            PoseStack pose, SubmitNodeCollector collector) {
        if (item.isEmpty()) return;
        var bounds = item.getModelBoundingBox();
        // Laying the native fixed model flat turns local Z into vertical height.
        double sizeX = bounds.getXsize(), sizeY = bounds.getZsize(), sizeZ = bounds.getYsize();
        double extent = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (!Double.isFinite(extent) || extent <= 0 || !Double.isFinite(bounds.minX)
                || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.maxZ)) return;
        double scale = Math.min(width / Math.max(sizeX, 0.00001),
                Math.min(height / Math.max(sizeY, 0.00001), depth / Math.max(sizeZ, 0.00001))) / 16;
        pose.pushPose();
        pose.translate(x / 16, surfaceY / 16, z / 16);
        pose.scale((float) scale, (float) scale, (float) scale);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.translate(-(bounds.minX + sizeX / 2), -(bounds.minY + sizeZ / 2), -bounds.maxZ);
        item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

}
