package me.bewf.luceat.client.gui.preview;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class Worlds {
	private Worlds() {
	}

	private static BlockState b(Block block) {
		return Bs.b(block);
	}

	private static double riverX(double z) {
		return 34.0 + 7.0 * Math.sin(z * 0.11 + 0.6) + 3.0 * Math.sin(z * 0.27);
	}

	static Scene cherry() {
		int w = 76;
		int h = 32;
		int d = 76;
		Scene.Builder s = new Scene.Builder("cherry", "Cherry hill", w, h, d);
		int level = 8;
		int[] heights = Craft.heights(s, 11, 3.0, 4.0, (x, z) -> {
			double v = Craft.hill(x, z, 14, 38, 17, 11) + Craft.hill(x, z, 62, 44, 13, 7) + Craft.hill(x, z, 36, 74, 40, 9) - 3.5;
			double dist = Math.abs(x - riverX(z));
			v -= 9.0 * Math.exp(-Math.pow(dist / 4.2, 2.0)) * Math.min(1.0, z / 14.0);
			return v;
		});
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int top = heights[x + w * z];
				boolean wet = top <= level;
				for (int y = 0; y <= top && y < h; y++) {
					Block block;
					if (y == top) {
						block = wet ? (top <= level - 1 ? Blocks.GRAVEL : Blocks.SAND) : Blocks.GRASS_BLOCK;
					} else if (y >= top - 3) {
						block = wet ? Blocks.SAND : Blocks.DIRT;
					} else {
						block = Blocks.STONE;
					}
					s.set(x, y, z, b(block));
				}
				for (int y = top + 1; y <= level && y < h; y++) {
					s.set(x, y, z, b(Blocks.WATER));
				}
			}
		}
		Craft.cherry(s, 13, Craft.top(s, 13, 34), 34, 3, 1.25);
		Craft.cherry(s, 21, Craft.top(s, 21, 44), 44, 11, 1.05);
		Craft.cherry(s, 8, Craft.top(s, 8, 46), 46, 19, 1.15);
		Craft.cherry(s, 26, Craft.top(s, 26, 56), 56, 7, 0.95);
		Craft.cherry(s, 6, Craft.top(s, 6, 28), 28, 23, 0.9);
		Craft.oak(s, 60, Craft.top(s, 60, 46), 46, 5, 1.1, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		Craft.oak(s, 66, Craft.top(s, 66, 54), 54, 9, 0.9, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		Craft.spruce(s, 55, Craft.top(s, 55, 58), 58, 4, 9);
		Craft.spruce(s, 49, Craft.top(s, 49, 66), 66, 8, 11);
		Craft.scatter(s, 0, 20, 30, 70, 0.28F, 2, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.FERN);
		Craft.scatter(s, 44, 30, 75, 72, 0.3F, 5, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.ALLIUM, Blocks.AZURE_BLUET);
		Craft.petals(s, 13, 38, 9.0, 0.5F, 3);
		Craft.petals(s, 22, 46, 6.0, 0.4F, 4);
		for (int i = 0; i < 6; i++) {
			int z = 24 + i * 5;
			int x = (int) Math.round(riverX(z)) - 7 - (i % 2);
			int y = Craft.top(s, x, z);
			if (y > level) {
				Craft.lampPost(s, x, y, z, Blocks.OAK_FENCE, 1 + (i % 2));
			}
		}
		s.camera(new double[]{33, 13.2, 6}, new double[]{37, 10.6, 60}, 62.0);
		s.time(0.37F);
		s.sun(0.12);
		s.fog(0.012F);
		return s.caption("A cherry hill at dusk. Lanterns light the bank.").build();
	}
}
