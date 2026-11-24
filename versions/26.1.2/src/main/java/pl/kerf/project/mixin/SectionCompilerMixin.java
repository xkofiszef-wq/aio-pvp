package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: przy budowie fragmentu zbiera animowane tekstury, których używają jego bloki. */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	/** Budowa idzie w wielu wątkach, więc zbiór jest osobny dla każdego. */
	private static final ThreadLocal<Set<SpriteContents>> aio$collected = new ThreadLocal<>();
	private static final Direction[] aio$SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	@Inject(method = "compile", at = @At("HEAD"))
	private void aio$begin(CallbackInfoReturnable<SectionCompiler.Results> cir) {
		if (Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.animateOnlyVisible)) {
			aio$collected.set(new HashSet<>());
		} else {
			aio$collected.remove();
		}
	}

	@WrapOperation(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private void aio$collect(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter region,
		BlockPos pos, BlockState state, BlockStateModel model, long seed, Operation<Void> original) {
		Set<SpriteContents> set = aio$collected.get();
		if (set != null) {
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(seed), parts);
			for (BlockStateModelPart part : parts) {
				for (Direction side : aio$SIDES) {
					for (BakedQuad quad : part.getQuads(side)) {
						SpriteContents contents = quad.materialInfo().sprite().contents();
						if (contents.isAnimated()) {
							set.add(contents);
						}
					}
				}
			}
		}

		original.call(renderer, output, x, y, z, region, pos, state, model, seed);
	}

	@ModifyReturnValue(method = "compile", at = @At("RETURN"))
	private SectionCompiler.Results aio$attach(SectionCompiler.Results results) {
		Set<SpriteContents> set = aio$collected.get();
		((AnimatedSprites.SpritesHolder) (Object) results).aio$setSprites(set == null ? null : Set.copyOf(set));
		aio$collected.remove();
		return results;
	}
}
