package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//? if <26.3 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
*///?}
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.gui.Hud;
*///?}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.CustomCrosshairRenderer;
import pl.kerf.project.render.SaturationOutline;

//? if <26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Hud.class)
*///?}
public class InGameHudMixin {
	//? if <26.1.2 {
	private static final String HUD_ARGS = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V";

	//?}
	@Shadow
	@Final
	private Minecraft minecraft;

	/** Locator Bar "Na pasku XP": zamiast paska lokalizacji zostaje pasek doświadczenia (ikony rysuje ExperienceBarMixin). */
	@WrapOperation(method = "nextContextualInfoState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;hasWaypoints()Z"))
	//? if <26.1.2 {
	private boolean aio$xpInsteadOfLocator(net.minecraft.client.waypoints.ClientWaypointManager handler, Operation<Boolean> original) {
		return original.call(handler) && !(Modules.LOCATOR_BAR.on() && Modules.LOCATOR_BAR.xpBar.on() && this.minecraft.gameMode.hasExperience());
	//?} else {
	/*private boolean aio$xpInsteadOfLocator(net.minecraft.client.waypoints.ClientWaypointManager manager, Operation<Boolean> original) {
		return original.call(manager) && !(Modules.LOCATOR_BAR.on() && Modules.LOCATOR_BAR.xpBar.on() && this.minecraft.gameMode.hasExperience());
	*///?}
	}

	/** Clear View: usuwa nakładkę lunety z ekranu (pierwsza osoba). */
	//? if <26.1.2 {
	@WrapWithCondition(method = "renderCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSpyglassOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	//?}
	//? if =26.1.2 {
	/*@WrapWithCondition(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractSpyglassOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	*///?}
	//? if <26.2 {
	private boolean aio$noSpyglassOverlay(Gui self, GuiGraphics context, float scale) {
	//?} else {
	/*@WrapWithCondition(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSpyglassOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	private boolean aio$noSpyglassOverlay(Hud self, GuiGraphics context, float scale) {
	*///?}
		return !(Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.spyglass.on());
	}

	/** Clear View: usuwa nakładkę dyni na głowie (pierwsze renderOverlay w renderMiscOverlays to nakładka ekwipunku/dyni). */
	@WrapWithCondition(
		//? if <26.1.2 {
		method = "renderCameraOverlays",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0)
		//?} else {
		/*method = "extractCameraOverlays",
		*///?}
		//? if >=26.2 {
		/*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0)
		*///?}
		//? if =26.1.2 {
		/*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0)
		*///?}
	)
	//? if <26.2 {
	private boolean aio$noPumpkinOverlay(Gui self, GuiGraphics context, Identifier texture, float opacity) {
	//?} else {
	/*private boolean aio$noPumpkinOverlay(Hud self, GuiGraphics context, Identifier texture, float opacity) {
	*///?}
		return !(Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.pumpkin.on());
	}

	/** Przesuwa i skaluje element wokół jego naturalnego punktu zaczepienia. Zawsze robi push, żeby pop był sparowany. */
	private static void aio$push(GuiGraphics context, Modules.Tweak tweak, float anchorX, float anchorY) {
		context.pose().pushMatrix();
		if (Modules.HUD_TWEAKER.on()) {
			context.pose().translate(anchorX + tweak.x.f(), anchorY + tweak.y.f());
			context.pose().scale(tweak.scale.f(), tweak.scale.f());
			context.pose().translate(-anchorX, -anchorY);
		}
	}

	//? if <26.1.2 {
	@Inject(method = "renderTitle", at = @At("HEAD"))
	//?} else {
	/*@Inject(method = "extractTitle", at = @At("HEAD"))
	*///?}
	private void aio$titlePush(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.title, context.guiWidth() / 2.0F, context.guiHeight() / 2.0F);
	}

	//? if <26.1.2 {
	@Inject(method = "renderTitle", at = @At("RETURN"))
	//?} else {
	/*@Inject(method = "extractTitle", at = @At("RETURN"))
	*///?}
	private void aio$titlePop(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		context.pose().popMatrix();
	}

	//? if <26.1.2 {
	@Inject(method = "renderOverlayMessage", at = @At("HEAD"))
	//?} else {
	/*@Inject(method = "extractOverlayMessage", at = @At("HEAD"))
	*///?}
	private void aio$actionbarPush(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.actionbar, context.guiWidth() / 2.0F, context.guiHeight() - 68.0F);
	}

	//? if <26.1.2 {
	@Inject(method = "renderOverlayMessage", at = @At("RETURN"))
	//?} else {
	/*@Inject(method = "extractOverlayMessage", at = @At("RETURN"))
	*///?}
	private void aio$actionbarPop(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		context.pose().popMatrix();
	}

	//? if <26.1.2 {
	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
	//?} else {
	/*@Inject(method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
	*///?}
	private void aio$scoreboardPush(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.scoreboard, context.guiWidth(), context.guiHeight() / 2.0F);
	}

	//? if <26.1.2 {
	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
	//?} else {
	/*@Inject(method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
	*///?}
	private void aio$scoreboardPop(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		context.pose().popMatrix();
	}

	//? if <26.1.2 {
	@Inject(method = "renderBossOverlay", at = @At("HEAD"))
	//?} else {
	/*@Inject(method = "extractBossOverlay", at = @At("HEAD"))
	*///?}
	private void aio$bossbarPush(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		aio$push(context, Modules.HUD_TWEAKER.bossbar, context.guiWidth() / 2.0F, 0.0F);
	}

	//? if <26.1.2 {
	@Inject(method = "renderBossOverlay", at = @At("RETURN"))
	//?} else {
	/*@Inject(method = "extractBossOverlay", at = @At("RETURN"))
	*///?}
	private void aio$bossbarPop(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		context.pose().popMatrix();
	}

	/** Czy w tej klatce podnieśliśmy dolny pasek (żeby RETURN zdjął dokładnie to, co włożył HEAD). */
	private boolean aio$lifted;

	/** HUD Tweaker -> Podniesienie hotbara: cały dolny pasek (hotbar, serca, głód, XP, nazwa przedmiotu) wyżej o N px. */
	//? if <26.1.2 {
	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"))
	//?} else {
	/*@Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"))
	*///?}
	private void aio$liftStart(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		int lift = Modules.HUD_TWEAKER.hotbarLift();
		this.aio$lifted = lift > 0;
		if (this.aio$lifted) {
			context.pose().pushMatrix();
			context.pose().translate(0.0F, -lift);
		}
	}

	//? if <26.1.2 {
	@Inject(method = "renderHotbarAndDecorations", at = @At("RETURN"))
	//?} else {
	/*@Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
	*///?}
	private void aio$liftEnd(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		if (this.aio$lifted) {
			context.pose().popMatrix();
		}
	}

	//? if <26.1.2 {
	@Inject(method = "renderFood", at = @At("HEAD"))
	//?} else {
	/*@Inject(method = "extractFood", at = @At("HEAD"))
	*///?}
	private void aio$foodBegin(GuiGraphics context, Player player, int top, int right, CallbackInfo ci) {
		SaturationOutline.beginFood();
	}

	/** Saturation: zapamiętuje, gdzie vanilla rysuje każdą ikonkę głodu (przy saturacji 0 drgają), żeby obwódki trafiały. */
	@WrapOperation(
		//? if <26.1.2 {
		method = "renderFood",
		//?} else {
		/*method = "extractFood",
		*///?}
		//? if <26.3 {
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0)
		//?} else {
		/*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0)
		*///?}
	)
	private void aio$foodIcon(GuiGraphics context, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		SaturationOutline.iconAt(x, y);
		original.call(context, pipeline, sprite, x, y, w, h);
	}

	//? if <26.1.2 {
	@Inject(method = "renderFood", at = @At("TAIL"))
	//?} else {
	/*@Inject(method = "extractFood", at = @At("TAIL"))
	*///?}
	private void aio$saturation(GuiGraphics context, Player player, int top, int right, CallbackInfo ci) {
		if (Modules.SATURATION.on()) {
			SaturationOutline.render(context, player, top, right);
		}
	}

	//? if <26.1.2 {
	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	//?} else {
	/*@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	*///?}
	private void aio$hideVanillaEffects(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		// Potion Status zastępuje ikony vanilla w prawym górnym rogu, więc obie naraz to duplikat.
		if (Modules.POTION_STATUS.on()) {
			ci.cancel();
		}
	}

	/** Gdy kursor jest na celu, rysuje celownik zwykłym pipeline'em w wybranym kolorze zamiast inwersji kolorów. */
	@WrapOperation(
		//? if <26.1.2 {
		method = "renderCrosshair",
		//?} else {
		/*method = "extractCrosshair",
		*///?}
		at = @At(
			value = "INVOKE",
			//? if <26.3 {
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			//?} else {
			/*target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			*///?}
			ordinal = 0
		)
	)
	private void aio$crosshairColor(GuiGraphics context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
		Modules.Crosshair module = Modules.CROSSHAIR;
		boolean onTarget = module.on() && this.aio$onTarget();
		if (module.on() && module.custom.on()) {
			// Własny rysunek: osobny dla "bez celu" i "na celu".
			CustomCrosshairRenderer.drawInGame(context, onTarget);
		} else if (onTarget && module.recolor.on()) {
			context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, module.color.argb());
		} else {
			original.call(context, pipeline, sprite, x, y, width, height);
		}
	}

	private boolean aio$onTarget() {
		return this.minecraft.hitResult instanceof EntityHitResult hit && aio$isTarget(hit.getEntity(), Modules.CROSSHAIR);
	}

	private static boolean aio$isTarget(Entity entity, Modules.Crosshair module) {
		return module.targets.is(0) ? entity instanceof Avatar : entity instanceof LivingEntity;
	}
}
