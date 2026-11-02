package me.bewf.luceat.client.gui.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Figures {
	static final int VOX = 16;
	static final int EMISSIVE = 254;

	private Figures() {
	}

	static int rgb(int hex) {
		return 0xFF000000 | hex;
	}

	static int shade(int argb, float factor) {
		int a = argb >>> 24;
		int r = Math.min(255, Math.max(0, Math.round((argb >> 16 & 255) * factor)));
		int g = Math.min(255, Math.max(0, Math.round((argb >> 8 & 255) * factor)));
		int b = Math.min(255, Math.max(0, Math.round((argb & 255) * factor)));
		return a << 24 | r << 16 | g << 8 | b;
	}

	static int textured(int argb, int x, int y, int z, int amount) {
		float n = 1.0F + (Atmosphere.hash(x * 5 + y * 11 + 3, z * 3 + y * 7 + x) - 0.5F) * amount / 100.0F;
		return shade(argb, n);
	}

	static final class Canvas {
		private final Scene.Builder builder;
		private final Map<Integer, int[]> cells = new HashMap<>();

		Canvas(Scene.Builder builder) {
			this.builder = builder;
		}

		void px(int wx, int wy, int wz, int argb) {
			int cx = Math.floorDiv(wx, VOX);
			int cy = Math.floorDiv(wy, VOX);
			int cz = Math.floorDiv(wz, VOX);
			if (!builder.in(cx, cy, cz)) {
				return;
			}
			int key = cx + builder.width * (cy + builder.height * cz);
			if (builder.cells[key] != null) {
				return;
			}
			int[] array = cells.computeIfAbsent(key, k -> new int[VOX * VOX * VOX]);
			int lx = Math.floorMod(wx, VOX);
			int ly = Math.floorMod(wy, VOX);
			int lz = Math.floorMod(wz, VOX);
			array[lx + VOX * (ly + VOX * lz)] = argb;
		}

		void box(int x0, int y0, int z0, int x1, int y1, int z1, int argb, int noise) {
			for (int z = z0; z < z1; z++) {
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						px(x, y, z, noise > 0 ? textured(argb, x, y, z, noise) : argb);
					}
				}
			}
		}

		void commit() {
			for (Map.Entry<Integer, int[]> entry : cells.entrySet()) {
				int key = entry.getKey();
				int x = key % builder.width;
				int y = key / builder.width % builder.height;
				int z = key / (builder.width * builder.height);
				builder.custom(x, y, z, entry.getValue());
			}
			cells.clear();
		}
	}

	record Palette(int skin, int hair, int shirt, int pants, int shoes, int eye) {
	}

	static final Palette ADVENTURER = new Palette(rgb(0xC48A66), rgb(0x3B2A1E), rgb(0x2FA0A6), rgb(0x3A3FA0), rgb(0x55555B), rgb(0x3A55D8));
	static final Palette VISITOR = new Palette(rgb(0x8A5A3C), rgb(0x1F1A18), rgb(0xB5452F), rgb(0x3C3C46), rgb(0x2A2A2E), rgb(0x2E7A4A));
	static final Palette UNDEAD = new Palette(rgb(0x5E9A4E), rgb(0x3E6A38), rgb(0x2B8FA8), rgb(0x3A3C8C), rgb(0x4A4A50), rgb(0x1A1A1A));

	private record Part(int kind, double x0, double y0, double z0, double x1, double y1, double z1, double pivotY, double pivotZ, double pitch, double pivotX, double roll) {
	}

	static final class Person {
		private final Palette palette;
		private final List<Part> parts = new ArrayList<>();
		private final double yaw;
		private final double rootX;
		private final double rootY;
		private final double rootZ;
		private double[] hand;
		private boolean torch;
		private final int flame;
		private final double rightPitch;
		private final double leftPitch;

		Person(Palette palette, double blockX, double blockY, double blockZ, double yawDegrees, double rightPitchDegrees, double leftPitchDegrees, boolean torch, int flame) {
			this.palette = palette;
			this.rootX = blockX * VOX;
			this.rootY = blockY * VOX;
			this.rootZ = blockZ * VOX;
			this.yaw = Math.toRadians(yawDegrees);
			this.rightPitch = Math.toRadians(rightPitchDegrees);
			this.leftPitch = Math.toRadians(leftPitchDegrees);
			this.torch = torch;
			this.flame = flame;
			parts.add(new Part(0, -4, 0, -2, 0, 12, 2, 0, 0, 0, 0, 0));
			parts.add(new Part(0, 0, 0, -2, 4, 12, 2, 0, 0, 0, 0, 0));
			parts.add(new Part(1, -4, 12, -2, 4, 24, 2, 0, 0, 0, 0, 0));
			parts.add(new Part(2, -4, 24, -4, 4, 32, 4, 0, 0, 0, 0, 0));
			parts.add(new Part(3, 4, 12, -2, 8, 24, 2, 22, 0, rightPitch, 6, 0));
			parts.add(new Part(3, -8, 12, -2, -4, 24, 2, 22, 0, leftPitch, -6, 0));
			double length = 11.0;
			hand = new double[]{6.0, 22.0 - Math.cos(rightPitch) * length, -Math.sin(rightPitch) * length};
		}

		double[] handBlock() {
			double lx = hand[0];
			double ly = hand[1] + 11.0;
			double lz = hand[2];
			return toWorld(lx, ly, lz);
		}

		double[] toWorld(double lx, double ly, double lz) {
			double cos = Math.cos(yaw);
			double sin = Math.sin(yaw);
			double wx = lx * cos + lz * sin;
			double wz = -lx * sin + lz * cos;
			return new double[]{(rootX + wx) / VOX, (rootY + ly) / VOX, (rootZ + wz) / VOX};
		}

		void paint(Canvas canvas) {
			int reach = 40;
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dy = 0; dy <= 44; dy++) {
					for (int dx = -reach; dx <= reach; dx++) {
						double cos = Math.cos(yaw);
						double sin = Math.sin(yaw);
						double lx = (dx + 0.5) * cos - (dz + 0.5) * sin;
						double lz = (dx + 0.5) * sin + (dz + 0.5) * cos;
						double ly = dy + 0.5;
						int colour = sample(lx, ly, lz);
						if (colour != 0) {
							canvas.px((int) Math.floor(rootX) + dx, (int) Math.floor(rootY) + dy, (int) Math.floor(rootZ) + dz, colour);
						}
					}
				}
			}
		}

		private int sample(double lx, double ly, double lz) {
			for (Part part : parts) {
				double px = lx;
				double py = ly;
				double pz = lz;
				if (part.pitch != 0.0) {
					double dy = py - part.pivotY;
					double dz = pz - part.pivotZ;
					double c = Math.cos(part.pitch);
					double s = Math.sin(part.pitch);
					py = part.pivotY + dy * c + dz * s;
					pz = part.pivotZ - dy * s + dz * c;
				}
				if (px >= part.x0 && px < part.x1 && py >= part.y0 && py < part.y1 && pz >= part.z0 && pz < part.z1) {
					return colourOf(part, px, py, pz);
				}
			}
			if (torch) {
				double hx = hand[0];
				double hy = hand[1];
				double hz = hand[2];
				if (Math.abs(lx - hx) < 1.0 && Math.abs(lz - hz) < 1.0 && ly >= hy - 1.0 && ly < hy + 9.0) {
					return textured(rgb(0x8B6A3A), (int) lx, (int) ly, (int) lz, 20);
				}
				if (Math.abs(lx - hx) < 2.0 && Math.abs(lz - hz) < 2.0 && ly >= hy + 9.0 && ly < hy + 13.0) {
					int base = flame;
					float hot = (float) ((ly - hy - 9.0) / 4.0);
					int a = EMISSIVE << 24;
					int r = Math.min(255, (base >> 16 & 255) + (int) (hot * 20));
					int g = Math.min(255, (base >> 8 & 255) + (int) (hot * 70));
					int b = Math.min(255, (base & 255) + (int) (hot * 60));
					return a | r << 16 | g << 8 | b;
				}
			}
			return 0;
		}

		private int colourOf(Part part, double px, double py, double pz) {
			int x = (int) Math.floor(px);
			int y = (int) Math.floor(py);
			int z = (int) Math.floor(pz);
			switch (part.kind) {
				case 0:
					return textured(y < 2 ? palette.shoes : palette.pants, x, y, z, 14);
				case 1:
					return textured(palette.shirt, x, y, z, 12);
				case 2:
					return head(x, y, z);
				default:
					return textured(py < 15.0 ? palette.skin : palette.shirt, x, y, z, 12);
			}
		}

		private int head(int x, int y, int z) {
			boolean front = z == -4;
			if (y >= 30 || (z >= 2 && y >= 26) || (y >= 28 && (x < -3 || x >= 3))) {
				return textured(palette.hair, x, y, z, 22);
			}
			if (front) {
				if (y == 28 || y == 27) {
					if (x == -3 || x == 2) {
						return rgb(0xF4F4F4);
					}
					if (x == -2 || x == 1) {
						return palette.eye;
					}
				}
				if (y == 25 && x >= -1 && x < 1) {
					return shade(palette.skin, 0.62F);
				}
				if (y == 29 && x >= -4 && x < 0 || y == 29 && x >= 0) {
					return textured(palette.hair, x, y, z, 22);
				}
			}
			return textured(palette.skin, x, y, z, 10);
		}
	}
}
