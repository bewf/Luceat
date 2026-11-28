package me.bewf.luceat.client.sky;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.SunLight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class CloudUniform {
	public static final int BINDING = 12;
	public static final String BLOCK_NAME = "LuceatCloud";

	private static final int SIZE = 48;
	private static final ByteBuffer SCRATCH = BufferUtils.createByteBuffer(SIZE);
	private static int handle;
	private static double velocityX;
	private static double velocityY;
	private static double velocityZ;

	private CloudUniform() {
	}

	public static void update() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LuceatConfig config = LuceatConfig.get();
		float[] sun = SunLight.values(level, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), config);
		LocalPlayer player = minecraft.player;
		Vec3 motion = player == null ? Vec3.ZERO : player.getDeltaMovement().scale(20.0);
		velocityX += (motion.x - velocityX) * 0.2;
		velocityY += (motion.y - velocityY) * 0.2;
		velocityZ += (motion.z - velocityZ) * 0.2;
		float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);

		SCRATCH.clear();
		SCRATCH.putFloat(sun[0]).putFloat(sun[1]).putFloat(sun[2]).putFloat(sun[3]);
		SCRATCH.putFloat((float) velocityX).putFloat((float) velocityY).putFloat((float) velocityZ).putFloat(speed);
		SCRATCH.putFloat((float) (System.nanoTime() / 1.0E9 % 1000.0)).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
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
		GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, handle);
	}

	public static boolean attach(Object compiledPipeline) {
		int program = programId(compiledPipeline, 0);
		if (program <= 0) {
			return false;
		}
		int index = GL31.glGetUniformBlockIndex(program, BLOCK_NAME);
		if (index == GL31.GL_INVALID_INDEX) {
			return false;
		}
		GL31.glUniformBlockBinding(program, index, BINDING);
		return true;
	}

	private static int programId(Object holder, int depth) {
		if (holder == null || depth > 2) {
			return -1;
		}
		try {
			Method direct = holder.getClass().getMethod("getProgramId");
			Object value = direct.invoke(holder);
			if (value instanceof Integer id) {
				return id;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		for (Method method : holder.getClass().getMethods()) {
			if (method.getParameterCount() != 0 || method.getReturnType().isPrimitive() || method.getReturnType() == String.class
					|| method.getDeclaringClass() == Object.class) {
				continue;
			}
			String type = method.getReturnType().getName();
			if (!type.contains("GlProgram") && !type.contains("Program")) {
				continue;
			}
			try {
				int id = programId(method.invoke(holder), depth + 1);
				if (id > 0) {
					return id;
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		for (Field field : holder.getClass().getDeclaredFields()) {
			if (!field.getType().getName().contains("Program")) {
				continue;
			}
			try {
				field.setAccessible(true);
				int id = programId(field.get(holder), depth + 1);
				if (id > 0) {
					return id;
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		return -1;
	}
}
