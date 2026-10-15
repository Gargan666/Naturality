// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class maddened_creeper<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "maddened_creeper"), "main");
	private final ModelPart Body;
	private final ModelPart Head;
	private final ModelPart leg0;
	private final ModelPart leg1;
	private final ModelPart leg2;
	private final ModelPart leg3;

	public maddened_creeper(ModelPart root) {
		this.Body = root.getChild("Body");
		this.Head = this.Body.getChild("Head");
		this.leg0 = this.Body.getChild("leg0");
		this.leg1 = this.Body.getChild("leg1");
		this.leg2 = this.Body.getChild("leg2");
		this.leg3 = this.Body.getChild("leg3");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition Body = partdefinition.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -18.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition Head = Body.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(8, 8).addBox(-4.0F, 0.0F, -4.0F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(16, 36).addBox(-4.0F, -8.0F, -3.0F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(23, 36).addBox(3.0F, -7.0F, -4.0F, 0.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(23, 36).addBox(-3.0F, -7.0F, -4.0F, 0.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(15, 36).addBox(-3.0F, -7.0F, -4.0F, 6.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(15, 36).addBox(-3.0F, -1.0F, -4.0F, 6.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -18.0F, 0.0F));

		PartDefinition leg0 = Body.addOrReplaceChild("leg0", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, 0.0F, 0.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, -6.0F, 2.0F));

		PartDefinition leg1 = Body.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, 0.0F, 0.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, -6.0F, 2.0F));

		PartDefinition leg2 = Body.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, 0.0F, -4.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, -6.0F, -2.0F));

		PartDefinition leg3 = Body.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, 0.0F, -4.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, -6.0F, -2.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		Body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}