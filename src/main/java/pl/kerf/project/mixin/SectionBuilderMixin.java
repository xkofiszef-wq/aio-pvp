//? if <26.1.2 {
package pl.kerf.project.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.AnimatedSprites;

/**
 * Animate Only Visible Textures: po zbudowaniu fragmentu zapisuje, których animowanych tekstur używają jego bloki.
 * Bloki fragmentu są czytane z regionu, a tekstury z modeli bloków (z pamięcią podręczną na stan bloku), więc działa
 * niezależnie od tego, jak Fabric API albo inne mody rysują bloki (z nowszym Fabric API stary sposób nie widział tekstur).
 */
@Mixin(SectionCompiler.class)
public class SectionBuilderMixin {
	private static final Direction[] aio$SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	/** Animowane tekstury modelu danego stanu bloku; czyszczone po przeładowaniu modeli (nowy obiekt modeli bloków). */
	private static final Map<BlockState, Set<SpriteContents>> aio$cache = new ConcurrentHashMap<>();
	private static volatile Object aio$cacheOwner;

	private static Set<SpriteContents> aio$spritesOf(BlockRenderDispatcher manager, BlockState state) {
		return aio$cache.computeIfAbsent(state, key -> {
			Set<SpriteContents> found = new HashSet<>();
			BlockStateModel model = manager.getBlockModel(key);
			List<BlockModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			for (BlockModelPart part : parts) {
				for (Direction side : aio$SIDES) {
					for (BakedQuad quad : part.getQuads(side)) {
						SpriteContents contents = quad.sprite().contents();
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
	private void aio$attach(SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorter, SectionBufferBuilderPack storage,
		CallbackInfoReturnable<SectionCompiler.Results> cir) {
		SectionCompiler.Results data = cir.getReturnValue();
		if (!Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.animateOnlyVisible)) {
			((AnimatedSprites.SpritesHolder) (Object) data).aio$setSprites(null);
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Object owner = client.getModelManager().getBlockModelShaper();
		if (owner != aio$cacheOwner) {
			aio$cache.clear();
			aio$cacheOwner = owner;
		}

		BlockRenderDispatcher manager = client.getBlockRenderer();
		Set<SpriteContents> set = new HashSet<>();
		BlockPos min = sectionPos.origin();
		for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(15, 15, 15))) {
			BlockState state = region.getBlockState(pos);
			if (state.getRenderShape() == RenderShape.MODEL) {
				set.addAll(aio$spritesOf(manager, state));
			}
		}

		((AnimatedSprites.SpritesHolder) (Object) data).aio$setSprites(Set.copyOf(set));
	}
}
//?}
