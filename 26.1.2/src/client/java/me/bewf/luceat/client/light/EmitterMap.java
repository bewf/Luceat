package me.bewf.luceat.client.light;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class EmitterMap {
	public static final String SAMPLER = "u_LuceatEmitMap";
	public static final int UNIT = 19;

	private static final int SIDE = 128;
	private static final int MASK = SIDE - 1;
	private static final int REACH = 62;
	private static final int UPLOADS_PER_FRAME = 2048;

	private static final ByteBuffer ONE = BufferUtils.createByteBuffer(1);
	private static final Map<Integer, Byte> SHOWN = new HashMap<>();

	private static int texture;
	private static ClientLevel level;

	private EmitterMap() {
	}

	public static void update(ClientLevel clientLevel, List<LightSource> lights, Vec3 camera) {
		if (clientLevel == null || !LightVolume.capable()) {
			return;
		}
		ensureTexture();
		if (clientLevel != level) {
			level = clientLevel;
			clearAll();
		}
		LuceatConfig config = LuceatConfig.get();
		double limit = config.mergeFarLights ? Math.min(REACH, config.joinBeyond) : REACH;
		Map<Integer, Byte> wanted = new HashMap<>();
		if (config.placedLights && config.emissiveGlow > 0.0F) {
			for (LightSource light : lights) {
				if (!light.placed() || light.radius() < 1.0F) {
					continue;
				}
				int x = (int) Math.floor(light.x());
				int y = (int) Math.floor(light.y());
				int z = (int) Math.floor(light.z());
				if (Math.abs(x + 0.5 - camera.x) > limit || Math.abs(y + 0.5 - camera.y) > limit || Math.abs(z + 0.5 - camera.z) > limit) {
					continue;
				}
				byte strength = (byte) Math.round(255.0F * Math.min(1.0F, Math.max(0.35F, light.radius() / 14.0F)));
				wanted.merge(index(x, y, z), strength, (a, b) -> (a & 0xFF) >= (b & 0xFF) ? a : b);
			}
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		int budget = UPLOADS_PER_FRAME;
		Iterator<Map.Entry<Integer, Byte>> iterator = SHOWN.entrySet().iterator();
		while (iterator.hasNext() && budget > 0) {
			Map.Entry<Integer, Byte> entry = iterator.next();
			if (!wanted.containsKey(entry.getKey())) {
				put(entry.getKey(), (byte) 0);
				iterator.remove();
				budget--;
			}
		}
		for (Map.Entry<Integer, Byte> entry : wanted.entrySet()) {
			if (budget <= 0) {
				break;
			}
			if (!entry.getValue().equals(SHOWN.get(entry.getKey()))) {
				put(entry.getKey(), entry.getValue());
				SHOWN.put(entry.getKey(), entry.getValue());
				budget--;
			}
		}
		GL13.glActiveTexture(previousUnit);
	}

	public static void bind(int program) {
		if (texture == 0) {
			return;
		}
		int location = GL20.glGetUniformLocation(program, SAMPLER);
		if (location < 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL20.glUniform1i(location, UNIT);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL13.glActiveTexture(previousUnit);
	}

	private static int index(int x, int y, int z) {
		return ((z & MASK) * SIDE + (y & MASK)) * SIDE + (x & MASK);
	}

	private static void put(int index, byte value) {
		ONE.clear();
		ONE.put(value);
		ONE.flip();
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, index & MASK, (index / SIDE) & MASK, index / (SIDE * SIDE), 1, 1, 1, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, ONE);
	}

	private static void ensureTexture() {
		if (texture != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		texture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, SIDE, SIDE, SIDE, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(SIDE * SIDE * SIDE));
		GL13.glActiveTexture(previousUnit);
		SHOWN.clear();
	}

	private static void clearAll() {
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, SIDE, SIDE, SIDE, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(SIDE * SIDE * SIDE));
		GL13.glActiveTexture(previousUnit);
		SHOWN.clear();
	}
}
