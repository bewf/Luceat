package me.bewf.luceat.client.sky;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LuceatClouds {
	private static final Identifier SHADER = Identifier.fromNamespaceAndPath("luceat", "core/clouds");
	private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
	private static final Set<String> TRIED = new HashSet<>();
	private static boolean raining;
	private static boolean announced;

	private LuceatClouds() {
	}

	public static RenderPipeline choose(RenderPipeline vanilla) {
		if (!isVanillaClouds(vanilla) || !LuceatConfig.get().luceatClouds || IrisCompat.packOn()) {
			return vanilla;
		}
		RenderPipeline custom = pipeline(wanted(), LuceatConfig.get().cloudQuality);
		if (custom == null) {
			return vanilla;
		}
		if (!announced) {
			announced = true;
			Luceat.LOGGER.info("Luceat clouds: drawing with the Luceat cloud shader");
		}
		CloudUniform.update();
		return custom;
	}

	private static int zeroToOne() {
		Object device = RenderSystem.getDevice();
		for (Class<?> type : new Class<?>[]{com.mojang.blaze3d.systems.GpuDevice.class, device.getClass()}) {
			try {
				return Boolean.TRUE.equals(type.getMethod("isZZeroToOne").invoke(device)) ? 1 : 0;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		return 0;
	}

	private static boolean isVanillaClouds(RenderPipeline pipeline) {
		return pipeline == RenderPipelines.CLOUDS || pipeline != null && pipeline.getLocation().getPath().matches("pipeline/(flat_)?clouds");
	}

	public static void reload() {
		PIPELINES.clear();
		TRIED.clear();
	}

	public static CloudType wanted() {
		LuceatConfig config = LuceatConfig.get();
		return switch (config.cloudMode) {
			case CUSTOM -> config.cloudType;
			case REAL_WORLD -> RealWeather.type();
			case IN_GAME -> inGame();
		};
	}

	public static CloudType previewType(LuceatConfig config) {
		if (!config.luceatClouds) {
			return CloudType.CUMULUS;
		}
		return switch (config.cloudMode) {
			case CUSTOM -> config.cloudType;
			case REAL_WORLD -> RealWeather.type();
			case IN_GAME -> CloudType.CUMULUS;
		};
	}

	private static CloudType inGame() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return CloudType.CUMULUS;
		}
		float rain = level.getRainLevel(1.0F);
		float thunder = level.getThunderLevel(1.0F);
		raining = raining ? rain > 0.3F : rain > 0.5F;
		if (thunder > 0.5F) {
			return CloudType.STORM;
		}
		return raining ? CloudType.NIMBOSTRATUS : CloudType.CUMULUS;
	}

	private static RenderPipeline pipeline(CloudType type, CloudQuality quality) {
		String key = type.name() + "_" + quality.name();
		if (!TRIED.add(key)) {
			return PIPELINES.get(key);
		}
		RenderPipeline pipeline = null;
		try {
			RenderPipeline base = RenderPipelines.CLOUDS;
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation("pipeline/luceat_clouds_" + key.toLowerCase(java.util.Locale.ROOT))
					.withVertexShader(SHADER)
					.withFragmentShader(SHADER)
					.withColorTargetState(base.getColorTargetState())
					.withDepthStencilState(base.getDepthStencilState())
					.withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
					.withCull(false)
					.withShaderDefine("LUCEAT_CUT", type.cut())
					.withShaderDefine("LUCEAT_DEPTH", type.depth())
					.withShaderDefine("LUCEAT_LIFT", type.lift())
					.withShaderDefine("LUCEAT_ALPHA", type.alpha())
					.withShaderDefine("LUCEAT_DARK", type.dark())
					.withShaderDefine("LUCEAT_FLAT", type.flat())
					.withShaderDefine("LUCEAT_FORM", type.form())
					.withShaderDefine("LUCEAT_ERODE", type.erode())
					.withShaderDefine("LUCEAT_ZZERO", zeroToOne())
					.withShaderDefine("LUCEAT_STEPS", quality.steps())
					.withShaderDefine("LUCEAT_TAPS", quality.taps())
					.withShaderDefine("LUCEAT_DETAIL", quality.detail());
			for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
				if (uniform.textureFormat() != null) {
					builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
				} else {
					builder.withUniform(uniform.name(), uniform.type());
				}
			}
			boolean hasGlobals = false;
			for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
				hasGlobals |= uniform.name().equals("Globals");
			}
			if (!hasGlobals) {
				builder.withUniform("Globals", UniformType.UNIFORM_BUFFER);
			}
			RenderPipeline built = builder.build();
			CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(built);
			pipeline = compiled.isValid() ? built : null;
			if (pipeline == null) {
				Luceat.LOGGER.warn("Luceat clouds: the {} cloud shader did not compile, using vanilla clouds", key);
			} else if (!CloudUniform.attach(compiled)) {
				Luceat.LOGGER.warn("Luceat clouds: could not link the cloud data block for {}, using vanilla clouds", key);
				pipeline = null;
			} else {
				Luceat.LOGGER.info("Luceat clouds: {} pipeline ready", key);
			}
		} catch (RuntimeException | LinkageError error) {
			Luceat.LOGGER.warn("Luceat clouds: could not build the {} pipeline, using vanilla clouds", key, error);
			pipeline = null;
		}
		if (pipeline != null) {
			PIPELINES.put(key, pipeline);
		}
		return pipeline;
	}
}
