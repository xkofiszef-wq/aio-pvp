package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.CustomCrosshairRenderer;
import pl.kerf.project.render.SaturationOutline;

@Mixin(InGameHud.class)
public class InGameHudMixin {
	private static final String HUD_ARGS = "(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V";

	@Shadow
	@Final
	private MinecraftClient client;

	/** Clear View: usuwa nakładkę lunety z ekranu (pierwsza osoba). */
	@WrapWithCondition(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderSpyglassOverlay(Lnet/minecraft/client/gui/DrawContext;F)V"))
	private boolean aio$noSpyglassOverlay(InGameHud self, DrawContext context, float scale) {
		return !(Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.spyglass.on());
	}

	/** Clear View: usuwa nakładkę dyni na głowie (pierwsze renderOverlay w renderMiscOverlays to nakładka ekwipunku/dyni). */
	@WrapWithCondition(
		method = "renderMiscOverlays",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 0)
	)
	private boolean aio$noPumpkinOverlay(InGameHud self, DrawContext context, Identifier texture, float opacity) {
		return !(Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.pumpkin.on());
	}

	/** Przesuwa i skaluje element wokół jego naturalnego punktu zaczepienia. Zawsze robi push, żeby pop był sparowany. */
	private static void aio$push(DrawContext context, Modules.Tweak tweak, float anchorX, float anchorY) {
		context.getMatrices().pushMatrix();
		if (Modules.HUD_TWEAKER.on()) {
			context.getMatrices().translate(anchorX + tweak.x.f(), anchorY + tweak.y.f());
			context.getMatrices().scale(tweak.scale.f(), tweak.scale.f());
			context.getMatrices().translate(-anchorX, -anchorY);
		}
	}

	@Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"))
	private void aio$titlePush(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.title, context.getScaledWindowWidth() / 2.0F, context.getScaledWindowHeight() / 2.0F);
	}

	@Inject(method = "renderTitleAndSubtitle", at = @At("RETURN"))
	private void aio$titlePop(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		context.getMatrices().popMatrix();
	}

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"))
	private void aio$actionbarPush(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.actionbar, context.getScaledWindowWidth() / 2.0F, context.getScaledWindowHeight() - 68.0F);
	}

	@Inject(method = "renderOverlayMessage", at = @At("RETURN"))
	private void aio$actionbarPop(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		context.getMatrices().popMatrix();
	}

	@Inject(method = "renderScoreboardSidebar" + HUD_ARGS, at = @At("HEAD"))
	private void aio$scoreboardPush(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.scoreboard, context.getScaledWindowWidth(), context.getScaledWindowHeight() / 2.0F);
	}

	@Inject(method = "renderScoreboardSidebar" + HUD_ARGS, at = @At("RETURN"))
	private void aio$scoreboardPop(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		context.getMatrices().popMatrix();
	}

	@Inject(method = "renderBossBarHud", at = @At("HEAD"))
	private void aio$bossbarPush(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.bossbar, context.getScaledWindowWidth() / 2.0F, 0.0F);
	}

	@Inject(method = "renderBossBarHud", at = @At("RETURN"))
	private void aio$bossbarPop(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		context.getMatrices().popMatrix();
	}

	@Inject(method = "renderFood", at = @At("TAIL"))
	private void aio$saturation(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
		if (Modules.SATURATION.on()) {
			SaturationOutline.render(context, player, top, right);
		}
	}

	@Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
	private void aio$hideVanillaEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		// Potion Status zastępuje ikony vanilla w prawym górnym rogu, więc obie naraz to duplikat.
		if (Modules.POTION_STATUS.on()) {
			ci.cancel();
		}
	}

	/** Gdy kursor jest na celu, rysuje celownik zwykłym pipeline'em w wybranym kolorze zamiast inwersji kolorów. */
	@WrapOperation(
		method = "renderCrosshair",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
			ordinal = 0
		)
	)
	private void aio$crosshairColor(DrawContext context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
		Modules.Crosshair module = Modules.CROSSHAIR;
		boolean onTarget = module.on() && this.aio$onTarget();
		if (module.on() && module.custom.on()) {
			// Własny rysunek: osobny dla "bez celu" i "na celu".
			CustomCrosshairRenderer.drawInGame(context, onTarget);
		} else if (onTarget && module.recolor.on()) {
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, module.color.argb());
		} else {
			original.call(context, pipeline, sprite, x, y, width, height);
		}
	}

	private boolean aio$onTarget() {
		return this.client.crosshairTarget instanceof EntityHitResult hit && aio$isTarget(hit.getEntity(), Modules.CROSSHAIR);
	}

	private static boolean aio$isTarget(Entity entity, Modules.Crosshair module) {
		return module.targets.is(0) ? entity instanceof PlayerLikeEntity : entity instanceof LivingEntity;
	}
}
