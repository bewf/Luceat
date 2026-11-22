package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class EntityShadows {
	public static final int BINDING = 8;
	public static final String BLOCK_NAME = "LuceatEntities";
	public static final int MAX_CASTERS = 10;
	public static final int MAX_BOXES = 64;
	public static final int BOX_VECTORS = MAX_BOXES * 5;

	private static final double REACH = 40.0;
	private static final int SIZE = 16 + MAX_CASTERS * 32 + BOX_VECTORS * 16;
	private static final ByteBuffer SCRATCH = BufferUtils.createByteBuffer(SIZE);
	private static final float[] SPHERES = new float[MAX_CASTERS * 4];
	private static final float[] INFO = new float[MAX_CASTERS * 4];
	private static final float[] BOXES = new float[BOX_VECTORS * 4];

	private static int handle;
	private static int casters;
	private static int boxCount;

	private EntityShadows() {
	}

	public static void update(Minecraft minecraft, Vec3 camera, float partialTick) {
		casters = 0;
		boxCount = 0;
		LuceatConfig config = LuceatConfig.get();
		ClientLevel level = minecraft.level;
		if (level != null && config.enabled && config.entityShadows && config.sunShadows) {
			collect(level, camera, partialTick);
		}
		upload();
	}

	public static void bind(int program) {
		if (handle == 0) {
			return;
		}
		int index = GL31.glGetUniformBlockIndex(program, BLOCK_NAME);
		if (index == GL31.GL_INVALID_INDEX) {
			return;
		}
		GL31.glUniformBlockBinding(program, index, BINDING);
		GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, handle);
	}

	private static void collect(ClientLevel level, Vec3 camera, float partialTick) {
		List<LivingEntity> near = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && !living.isInvisible() && !living.isSpectator() && !living.isDeadOrDying() && living.distanceToSqr(camera) < REACH * REACH) {
				near.add(living);
			}
		}
		near.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(camera)));
		for (LivingEntity living : near) {
			if (casters >= MAX_CASTERS) {
				break;
			}
			add(living, camera, partialTick);
		}
	}

	private static void add(LivingEntity living, Vec3 camera, float partialTick) {
		double height = living.getBbHeight();
		double width = living.getBbWidth();
		if (width * height < 0.12) {
			return;
		}
		boolean humanoid = width <= 0.8 && height / width >= 2.8 && height / width <= 4.1;
		if (boxCount + (humanoid ? 6 : 1) > MAX_BOXES) {
			return;
		}
		Vec3 position = living.getPosition(partialTick);
		double cx = position.x - camera.x;
		double cy = position.y - camera.y;
		double cz = position.z - camera.z;
		double yaw = Math.toRadians(Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot));
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double sideX = Math.cos(yaw);
		double sideZ = Math.sin(yaw);
		int first = boxCount;
		float radius;
		float middle;
		if (humanoid) {
			double scale = height / 1.8;
			humanoid(living, partialTick, cx, cy, cz, scale, forwardX, forwardZ, sideX, sideZ);
			radius = (float) (1.3 * scale);
			middle = (float) (0.95 * scale);
		} else {
			double hx = width * 0.45;
			double hy = height * 0.45;
			double hz = width * 0.55;
			box(cx, cy + height * 0.5, cz, hx, hy, hz, sideX, 0.0, sideZ, 0.0, 1.0, 0.0, forwardX, 0.0, forwardZ);
			radius = (float) (Math.sqrt(hx * hx + hy * hy + hz * hz) * 1.05);
			middle = (float) (height * 0.5);
		}
		SPHERES[casters * 4] = (float) cx;
		SPHERES[casters * 4 + 1] = (float) (cy + middle);
		SPHERES[casters * 4 + 2] = (float) cz;
		SPHERES[casters * 4 + 3] = radius;
		INFO[casters * 4] = first;
		INFO[casters * 4 + 1] = boxCount - first;
		casters++;
	}

	private static void humanoid(LivingEntity living, float partialTick, double cx, double cy, double cz, double scale, double forwardX, double forwardZ, double sideX, double sideZ) {
		double swing = Math.cos(living.walkAnimation.position(partialTick) * 0.6662F) * Math.min(1.0F, living.walkAnimation.speed(partialTick));
		box(cx, cy + 1.125 * scale, cz, 0.25 * scale, 0.375 * scale, 0.125 * scale, sideX, 0.0, sideZ, 0.0, 1.0, 0.0, forwardX, 0.0, forwardZ);
		double headYaw = Math.toRadians(Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot));
		double pitch = Math.toRadians(Mth.lerp(partialTick, living.xRotO, living.getXRot()));
		double headForwardX = -Math.sin(headYaw);
		double headForwardZ = Math.cos(headYaw);
		double cosPitch = Math.cos(pitch);
		double sinPitch = Math.sin(pitch);
		box(cx, cy + 1.75 * scale, cz, 0.25 * scale, 0.25 * scale, 0.25 * scale,
			Math.cos(headYaw), 0.0, Math.sin(headYaw),
			headForwardX * sinPitch, cosPitch, headForwardZ * sinPitch,
			headForwardX * cosPitch, -sinPitch, headForwardZ * cosPitch);
		for (int sign = -1; sign <= 1; sign += 2) {
			limb(cx, cy, cz, scale, forwardX, forwardZ, sideX, sideZ, 0.125 * sign, 0.75, 0.375, 1.4 * swing * sign);
			limb(cx, cy, cz, scale, forwardX, forwardZ, sideX, sideZ, 0.375 * sign, 1.375, 0.25, -0.9 * swing * sign);
		}
	}

	private static void limb(double cx, double cy, double cz, double scale, double forwardX, double forwardZ, double sideX, double sideZ, double offset, double pivotY, double drop, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double reach = -drop * sin * scale;
		box(cx + offset * sideX * scale + reach * forwardX,
			cy + (pivotY - drop * cos) * scale,
			cz + offset * sideZ * scale + reach * forwardZ,
			0.125 * scale, 0.375 * scale, 0.125 * scale,
			sideX, 0.0, sideZ,
			forwardX * sin, cos, forwardZ * sin,
			forwardX * cos, -sin, forwardZ * cos);
	}

	private static void box(double cx, double cy, double cz, double hx, double hy, double hz, double ax, double ay, double az, double bx, double by, double bz, double dx, double dy, double dz) {
		int at = boxCount * 20;
		BOXES[at] = (float) cx;
		BOXES[at + 1] = (float) cy;
		BOXES[at + 2] = (float) cz;
		BOXES[at + 3] = (float) hx;
		BOXES[at + 4] = (float) hy;
		BOXES[at + 5] = (float) hz;
		BOXES[at + 8] = (float) ax;
		BOXES[at + 9] = (float) ay;
		BOXES[at + 10] = (float) az;
		BOXES[at + 12] = (float) bx;
		BOXES[at + 13] = (float) by;
		BOXES[at + 14] = (float) bz;
		BOXES[at + 16] = (float) dx;
		BOXES[at + 17] = (float) dy;
		BOXES[at + 18] = (float) dz;
		boxCount++;
	}

	private static void upload() {
		SCRATCH.clear();
		SCRATCH.putFloat(casters).putFloat(boxCount).putFloat(0.0F).putFloat(0.0F);
		for (float value : SPHERES) {
			SCRATCH.putFloat(value);
		}
		for (float value : INFO) {
			SCRATCH.putFloat(value);
		}
		for (float value : BOXES) {
			SCRATCH.putFloat(value);
		}
		SCRATCH.flip();
		if (handle == 0) {
			handle = GL15.glGenBuffers();
			GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, handle);
			GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, SIZE, GL15.GL_DYNAMIC_DRAW);
		} else {
			GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, handle);
		}
		GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, SCRATCH);
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
		java.util.Arrays.fill(SPHERES, 0.0F);
		java.util.Arrays.fill(INFO, 0.0F);
		java.util.Arrays.fill(BOXES, 0.0F);
	}
}
