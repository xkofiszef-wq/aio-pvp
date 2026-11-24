package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: przy budowie fragmentu zbiera animowane tekstury, których używają jego bloki. */
@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {
	/** Budowa idzie w wielu wątkach, więc zbiór jest osobny dla każdego. */
	private static final ThreadLocal<Set<SpriteContents>> aio$collected = new ThreadLocal<>();
	private static final Direction[] aio$SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	@Inject(method = "build", at = @At("HEAD"))
	private void aio$begin(CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
		if (Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.animateOnlyVisible)) {
			aio$collected.set(new HashSet<>());
		} else {
			aio$collected.remove();
		}
	}

	@WrapOperation(method = "build", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLjava/util/List;)V"))
	private void aio$collect(BlockRenderManager manager, BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
		VertexConsumer consumer, boolean cull, List<BlockModelPart> parts, Operation<Void> original) {
		Set<SpriteContents> set = aio$collected.get();
		if (set != null) {
			for (BlockModelPart part : parts) {
				for (Direction side : aio$SIDES) {
					for (BakedQuad quad : part.getQuads(side)) {
						SpriteContents contents = quad.sprite().getContents();
						if (contents.isAnimated()) {
							set.add(contents);
						}
					}
				}
			}
		}

		original.call(manager, state, pos, world, matrices, consumer, cull, parts);
	}

	@ModifyReturnValue(method = "build", at = @At("RETURN"))
	private SectionBuilder.RenderData aio$attach(SectionBuilder.RenderData data) {
		Set<SpriteContents> set = aio$collected.get();
		((AnimatedSprites.SpritesHolder) (Object) data).aio$setSprites(set == null ? null : Set.copyOf(set));
		aio$collected.remove();
		return data;
	}
}
