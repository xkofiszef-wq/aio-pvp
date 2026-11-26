package pl.kerf.project.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=26.3 {
/*import net.minecraft.client.renderer.state.level.PlayerRenderState;
*///?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=26.3 {
/*import net.minecraft.resources.Identifier;
*///?}
//? if >=26.2 {
/*import net.minecraft.util.ARGB;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {
	//? if <26.2 {
	@ModifyArg(
		method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1
	)
	private static float aio$fireOffset(float y) {
		return Modules.LOW_FIRE.on() ? y + Modules.LOW_FIRE.offsetY.f() : y;
	//?} else {
	/*/^* Low Fire: cały ogień niżej. W 26.2 ogień rysuje lambda, więc przesuwamy pozę przed zgłoszeniem i cofamy po nim. ^/
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void aio$fireLower(PoseStack matrices, SubmitNodeCollector queue, TextureAtlasSprite sprite, CallbackInfo ci) {
		matrices.pushPose();
		if (Modules.LOW_FIRE.on()) {
			matrices.translate(0.0F, Modules.LOW_FIRE.offsetY.f(), 0.0F);
		}
	*///?}
	}

	//? if >=26.2 {
	/*@Inject(method = "submitFire", at = @At("RETURN"))
	private static void aio$fireLowerEnd(PoseStack matrices, SubmitNodeCollector queue, TextureAtlasSprite sprite, CallbackInfo ci) {
		matrices.popPose();
	}

	/^* Low Fire: krycie ognia (kolor ARGB ćwiartek ognia). ^/
	*///?}
	@ModifyArg(
		//? if <26.2 {
		method = "renderFire",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
		index = 3
		//?} else {
		/*method = "buildFireQuad",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"),
		index = 8
		*///?}
	)
	//? if <26.2 {
	private static float aio$fireOpacity(float alpha) {
		return Modules.LOW_FIRE.on() ? alpha * Modules.LOW_FIRE.opacity.f() / 100.0F : alpha;
	//?} else {
	/*private static int aio$fireOpacity(int color) {
		return Modules.LOW_FIRE.on() ? ARGB.color(Math.round(ARGB.alpha(color) * Modules.LOW_FIRE.opacity.f() / 100.0F), color) : color;
	*///?}
	}

	/** Clear View: w bloku (np. po wepchnięciu w ścianę) nie ma ciemnej nakładki z teksturą bloku na cały ekran. */
	//? if <26.2 {
	@Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
	private static void aio$noInWallOverlay(TextureAtlasSprite sprite, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
	*///?}
	//? if =26.2 {
	/*private static void aio$noInWallOverlay(TextureAtlasSprite sprite, PoseStack matrices, SubmitNodeCollector queue, int color, CallbackInfo ci) {
	*///?}
	//? if >=26.3 {
	/*private static void aio$noInWallOverlay(Identifier atlas, float u0, float v0, float u1, float v1, PoseStack matrices, SubmitNodeCollector queue, int color, CallbackInfo ci) {
	*///?}
		if (Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.inWall.on()) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderItemActivationAnimation",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER)
	)
	//? if <26.3 {
	private void aio$totemScale(PoseStack matrices, float tickProgress, SubmitNodeCollector queue, CallbackInfo ci) {
	//?} else {
	/*private void aio$totemScale(PlayerRenderState player, PoseStack matrices, float tickProgress, SubmitNodeCollector queue, CallbackInfo ci) {
	*///?}
		if (Modules.TOTEM.on()) {
			float scale = Modules.TOTEM.scale.f();
			matrices.scale(scale, scale, scale);
		}
	}
}
