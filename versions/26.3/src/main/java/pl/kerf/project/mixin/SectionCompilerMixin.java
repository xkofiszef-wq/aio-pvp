package pl.kerf.project.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.AnimatedSprites;

/**
 * Animate Only Visible Textures: po zbudowaniu fragmentu zapisuje, których animowanych tekstur używają jego bloki.
 * Bloki fragmentu są czytane z regionu, a tekstury z modeli bloków (z pamięcią podręczną na stan bloku), więc działa
 * niezależnie od tego, jak Fabric API albo inne mody rysują bloki.
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	private static final Direction[] aio$SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	/** Animowane tekstury modelu danego stanu bloku; czyszczone, gdy zmieni się zestaw modeli (przeładowanie zasobów). */
	private static final Map<BlockState, Set<SpriteContents>> aio$cache = new ConcurrentHashMap<>();
	private static volatile Object aio$cacheOwner;

	@Shadow
	@Final
	private BlockStateModelSet blockModelSet;

	private Set<SpriteContents> aio$spritesOf(BlockState state) {
		return aio$cache.computeIfAbsent(state, key -> {
			Set<SpriteContents> found = new HashSet<>();
			BlockStateModel model = this.blockModelSet.get(key);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			for (BlockStateModelPart part : parts) {
				for (Direction side : aio$SIDES) {
					for (BakedQuad quad : part.getQuads(side)) {
						SpriteContents contents = quad.materialInfo().sprite().contents();
						if (contents.isAnimated()) {
							found.add(contents);
						}
					}
				}
			}

			return found.isEmpty() ? Set.of() : Set.copyOf(found);
		});
	}

	@Inject(method = "compile", at = @At("RETURN"))
	private void aio$attach(SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack builders,
		CallbackInfoReturnable<SectionCompiler.Results> cir) {
		SectionCompiler.Results results = cir.getReturnValue();
		if (!Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.animateOnlyVisible)) {
			((AnimatedSprites.SpritesHolder) (Object) results).aio$setSprites(null);
			return;
		}

		if (this.blockModelSet != aio$cacheOwner) {
			aio$cache.clear();
			aio$cacheOwner = this.blockModelSet;
		}

		Set<SpriteContents> set = new HashSet<>();
		BlockPos min = sectionPos.origin();
		for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(15, 15, 15))) {
			BlockState state = region.getBlockState(pos);
			if (state.getRenderShape() == RenderShape.MODEL) {
				set.addAll(this.aio$spritesOf(state));
			}
		}

		((AnimatedSprites.SpritesHolder) (Object) results).aio$setSprites(Set.copyOf(set));
	}
}
