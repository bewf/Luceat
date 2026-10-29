package me.bewf.luceat.client.light;

import java.nio.ByteBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.world.phys.Vec3;

public final class LightBuffer {
	public static final int MAX_LIGHTS = 256;
	public static final int BINDING = 7;
	public static final String BLOCK_NAME = "LuceatLights";

	private static final int HEADER = 160;
	private static final int SIZE = HEADER + MAX_LIGHTS * 32;

	private final ByteBuffer scratch = BufferUtils.createByteBuffer(SIZE);
	private int handle;

	public void update(List<LightSource> lights, Vec3 camera) {
		int count = Math.min(lights.size(), MAX_LIGHTS);
		this.scratch.clear();
		LuceatConfig config = LuceatConfig.get();
		this.scratch.putFloat(count).putFloat(config.shadows ? 1.0F : 0.0F).putFloat(config.brightness).putFloat(Math.max(config.maxBrightness, 0.1F));
		this.scratch.putFloat(config.daylightFade).putFloat(config.directionalShading).putFloat(config.shadowStrength).putFloat(config.vanillaReplace);
		this.scratch.putFloat(config.placedStrength).putFloat(Math.min(config.shadowDistance, ShadowVolume.RELIABLE)).putFloat(config.shadowSoftness * 0.45F).putFloat(config.lightSpread && LightSpread.supported() ? config.spillStrength : 0.0F);
		int blockX = (int) Math.floor(camera.x);
		int blockY = (int) Math.floor(camera.y);
		int blockZ = (int) Math.floor(camera.z);
		this.scratch.putInt(blockX).putInt(blockY).putInt(blockZ).putInt(0);
		this.scratch.putFloat((float) (camera.x - blockX)).putFloat((float) (camera.y - blockY)).putFloat((float) (camera.z - blockZ)).putFloat((float) (System.nanoTime() / 1.0E9 % 1000.0));
		this.scratch.putInt(ShadowVolume.centreX()).putInt(ShadowVolume.centreY()).putInt(ShadowVolume.centreZ()).putInt(ShadowVolume.HALF);
		this.scratch.putFloat(config.lightBounce).putFloat(config.shine).putFloat(config.shadows ? config.shadowedLights : 0).putFloat(Governor.quality());
		this.scratch.putInt(LightGrid.originX()).putInt(LightGrid.originY()).putInt(LightGrid.originZ()).putInt(LightGrid.SLOTS / 4);
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		float[] sun = SunLight.values(minecraft.level, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), config);
		for (int i = 0; i < 8; i++) {
			this.scratch.putFloat(sun[i]);
		}
		for (int i = 0; i < MAX_LIGHTS; i++) {
			if (i < count) {
				LightSource light = lights.get(i);
				this.scratch.putFloat((float) (light.x() - camera.x));
				this.scratch.putFloat((float) (light.y() - camera.y));
				this.scratch.putFloat((float) (light.z() - camera.z));
				this.scratch.putFloat(light.radius());
			} else {
				this.scratch.putFloat(0.0F).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
			}
		}
		for (int i = 0; i < MAX_LIGHTS; i++) {
			if (i < count) {
				LightSource light = lights.get(i);
				this.scratch.putFloat(light.red()).putFloat(light.green()).putFloat(light.blue()).putFloat((light.placed() ? 1.0F : 0.0F) + (light.noShadow() ? 2.0F : 0.0F) + (config.hideBehindBlocks ? 4.0F * Math.min(15, Math.max(0, light.vanilla())) : 0.0F));
			} else {
				this.scratch.putFloat(0.0F).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
			}
		}
		this.scratch.flip();

		if (this.handle == 0) {
			this.handle = GL15.glGenBuffers();
			GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, this.handle);
			GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, SIZE, GL15.GL_DYNAMIC_DRAW);
		} else {
			GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, this.handle);
		}
		GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, this.scratch);
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
	}

	public void bind(int program) {
		int index = GL31.glGetUniformBlockIndex(program, BLOCK_NAME);
		if (index == GL31.GL_INVALID_INDEX) {
			return;
		}
		GL31.glUniformBlockBinding(program, index, BINDING);
		GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, this.handle);
	}
}
