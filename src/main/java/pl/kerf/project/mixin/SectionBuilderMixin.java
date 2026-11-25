package pl.kerf.project.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
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
@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {
	private static final Direction[] aio$SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	/** Animowane tekstury modelu danego stanu bloku; czyszczone po przeładowaniu modeli (nowy obiekt modeli bloków). */
	private static final Map<BlockState, Set<SpriteContents>> aio$cache = new ConcurrentHashMap<>();
	private static volatile Object aio$cacheOwner;

	private static Set<SpriteContents> aio$spritesOf(BlockRenderManager manager, BlockState state) {
		return aio$cache.computeIfAbsent(state, key -> {
			Set<SpriteContents> found = new HashSet<>();
			BlockStateModel model = manager.getModel(key);
			List<BlockModelPart> parts = new ArrayList<>();
			model.addParts(Random.create(42L), parts);
			for (BlockModelPart part : parts) {
				for (Direction side : aio$SIDES) {
					for (BakedQuad quad : part.getQuads(side)) {
						SpriteContents contents = quad.sprite().getContents();
						if (contents.isAnimated()) {
							found.add(contents);
						}
					}
				}
			}

			return found.isEmpty() ? Set.of() : Set.copyOf(found);
		});
	}

	@Inject(method = "build", at = @At("RETURN"))
	private void aio$attach(ChunkSectionPos sectionPos, ChunkRendererRegion region, VertexSorter sorter, BlockBufferAllocatorStorage storage,
		CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
		SectionBuilder.RenderData data = cir.getReturnValue();
		if (!Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.animateOnlyVisible)) {
			((AnimatedSprites.SpritesHolder) (Object) data).aio$setSprites(null);
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Object owner = client.getBakedModelManager().getBlockModels();
		if (owner != aio$cacheOwner) {
			aio$cache.clear();
			aio$cacheOwner = owner;
		}

		BlockRenderManager manager = client.getBlockRenderManager();
		Set<SpriteContents> set = new HashSet<>();
		BlockPos min = sectionPos.getMinPos();
		for (BlockPos pos : BlockPos.iterate(min, min.add(15, 15, 15))) {
			BlockState state = region.getBlockState(pos);
			if (state.getRenderType() == BlockRenderType.MODEL) {
				set.addAll(aio$spritesOf(manager, state));
			}
		}

		((AnimatedSprites.SpritesHolder) (Object) data).aio$setSprites(Set.copyOf(set));
	}
}
