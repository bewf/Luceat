package me.bewf.luceat.client.light;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EmissionShape {
	public static final int ROUND = 0;
	public static final int LANTERN = 1;
	public static final int LANTERN_HANGING = 2;
	public static final int CANDLE = 3;
	public static final int CAMPFIRE = 4;
	public static final int BODY_LOW = 5;
	public static final int BODY_MID = 6;
	public static final int BODY_HIGH = 7;
	public static final int COUNT = 8;

	private static final int BOXES = 2;

	private static final double[][] ORIGIN = {
		{0.0, 0.0, 0.0},
		{0.0, -0.28, 0.0},
		{0.0, -0.22, 0.0},
		{0.0, -0.03, 0.0},
		{0.0, 0.2, 0.0},
		{0.0, 0.0, 0.0},
		{0.0, 0.25, 0.0},
		{0.0, 0.45, 0.0}
	};

	private static final double[][][] CASING = {
		{},
		{{0.0, -0.09, 0.0, 0.19, 0.035, 0.19, 16.0}, {0.0, -0.47, 0.0, 0.19, 0.03, 0.19, 8.0}},
		{{0.0, -0.03, 0.0, 0.19, 0.03, 0.19, 16.0}, {0.0, -0.41, 0.0, 0.19, 0.03, 0.19, 8.0}},
		{{0.0, -0.31, 0.0, 0.12, 0.19, 0.12, 5.0}},
		{{0.0, -0.3, 0.0, 0.5, 0.2, 0.5, 4.5}},
		{{0.0, -0.375, 0.0, 0.4, 0.125, 0.4, 3.0}},
		{{0.0, -0.25, 0.0, 0.4, 0.25, 0.4, 3.0}},
		{{0.0, -0.125, 0.0, 0.4, 0.375, 0.4, 3.0}}
	};

	private static final Map<BlockState, Integer> KNOWN = new ConcurrentHashMap<>();

	private EmissionShape() {
	}

	public static void clear() {
		KNOWN.clear();
	}

	public static int of(BlockState state) {
		Integer known = KNOWN.get(state);
		if (known == null) {
			known = classify(state);
			KNOWN.put(state, known);
		}
		return known;
	}

	private static int classify(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.contains("lantern") && !path.contains("sea_") && !path.contains("jack_o")) {
			return flag(state, "hanging") ? LANTERN_HANGING : LANTERN;
		}
		if (path.contains("campfire")) {
			return flag(state, "lit") || !hasProperty(state, "lit") ? CAMPFIRE : ROUND;
		}
		if (path.contains("candle_cake")) {
			return BODY_MID;
		}
		if (path.contains("candle")) {
			return CANDLE;
		}
		return fromBounds(state);
	}

	private static int fromBounds(BlockState state) {
		if (state.isSolidRender()) {
			return ROUND;
		}
		VoxelShape shape;
		try {
			shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		} catch (RuntimeException unusual) {
			return ROUND;
		}
		if (shape.isEmpty()) {
			return ROUND;
		}
		AABB box = shape.bounds();
		double width = Math.min(box.maxX - box.minX, box.maxZ - box.minZ);
		double top = box.maxY;
		if (box.minY > 0.05 || width < 0.5 || top > 0.95) {
			return ROUND;
		}
		return top <= 0.375 ? BODY_LOW : top <= 0.625 ? BODY_MID : BODY_HIGH;
	}

	private static boolean hasProperty(BlockState state, String name) {
		for (Property<?> property : state.getBlock().getStateDefinition().getProperties()) {
			if (property.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean flag(BlockState state, String name) {
		for (Property<?> property : state.getBlock().getStateDefinition().getProperties()) {
			if (property.getName().equals(name)) {
				return Boolean.TRUE.equals(state.getValue(property));
			}
		}
		return false;
	}

	public static double transmit(int shape, double rx, double ry, double rz) {
		if (shape <= 0 || shape >= COUNT) {
			return 1.0;
		}
		double[] origin = ORIGIN[shape];
		double dx = rx - origin[0];
		double dy = ry - origin[1];
		double dz = rz - origin[2];
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-4) {
			return 1.0;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		double kept = 1.0;
		for (double[] part : CASING[shape]) {
			double near = 0.0;
			double far = length;
			boolean hit = true;
			for (int axis = 0; axis < 3 && hit; axis++) {
				double step = axis == 0 ? dx : axis == 1 ? dy : dz;
				double from = origin[axis] - part[axis];
				double half = part[3 + axis];
				if (Math.abs(step) < 1.0E-9) {
					hit = Math.abs(from) <= half;
					continue;
				}
				double a = (-half - from) / step;
				double b = (half - from) / step;
				near = Math.max(near, Math.min(a, b));
				far = Math.min(far, Math.max(a, b));
				hit = far > near;
			}
			if (hit) {
				kept *= Math.exp(-part[6] * (far - near));
			}
		}
		return kept;
	}

	public static String glsl() {
		StringBuilder out = new StringBuilder();
		out.append("const vec3 LUCEAT_ORIGIN[").append(COUNT).append("] = vec3[").append(COUNT).append("](");
		for (int i = 0; i < COUNT; i++) {
			out.append(i == 0 ? "" : ", ").append(vec3(ORIGIN[i][0], ORIGIN[i][1], ORIGIN[i][2]));
		}
		out.append(");\n");
		StringBuilder centres = new StringBuilder();
		StringBuilder halves = new StringBuilder();
		for (int i = 0; i < COUNT; i++) {
			for (int b = 0; b < BOXES; b++) {
				double[] part = b < CASING[i].length ? CASING[i][b] : new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
				boolean first = i == 0 && b == 0;
				centres.append(first ? "" : ", ").append("vec4(").append(num(part[0])).append(", ").append(num(part[1])).append(", ").append(num(part[2])).append(", ").append(num(part[6])).append(")");
				halves.append(first ? "" : ", ").append(vec3(part[3], part[4], part[5]));
			}
		}
		int total = COUNT * BOXES;
		out.append("\t\t\tconst vec4 LUCEAT_PART_CENTRE[").append(total).append("] = vec4[").append(total).append("](").append(centres).append(");\n");
		out.append("\t\t\tconst vec3 LUCEAT_PART_HALF[").append(total).append("] = vec3[").append(total).append("](").append(halves).append(");\n");
		return out.toString();
	}

	private static String vec3(double x, double y, double z) {
		return "vec3(" + num(x) + ", " + num(y) + ", " + num(z) + ")";
	}

	private static String num(double value) {
		return String.format(java.util.Locale.ROOT, "%.4f", value);
	}
}
