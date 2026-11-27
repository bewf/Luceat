package me.bewf.luceat.client.sky;

import java.util.EnumMap;
import java.util.Map;

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
	private static final Map<CloudType, RenderPipeline> PIPELINES = new EnumMap<>(CloudType.class);
	private static final Map<CloudType, Boolean> TRIED = new EnumMap<>(CloudType.class);
	private static boolean raining;

	private LuceatClouds() {
	}

	public static RenderPipeline choose(RenderPipeline vanilla) {
		if (vanilla != RenderPipelines.CLOUDS || !LuceatConfig.get().luceatClouds || IrisCompat.packOn()) {
			return vanilla;
		}
		RenderPipeline custom = pipeline(wanted());
		return custom != null ? custom : vanilla;
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
		return raining ? CloudType.OVERCAST : CloudType.CUMULUS;
	}

	private static RenderPipeline pipeline(CloudType type) {
		if (TRIED.containsKey(type)) {
			return PIPELINES.get(type);
		}
		TRIED.put(type, Boolean.TRUE);
		RenderPipeline pipeline = null;
		try {
			RenderPipeline base = RenderPipelines.CLOUDS;
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation("pipeline/luceat_clouds_" + type.name().toLowerCase(java.util.Locale.ROOT))
					.withVertexShader(SHADER)
					.withFragmentShader(SHADER)
					.withColorTargetState(base.getColorTargetState())
					.withDepthStencilState(base.getDepthStencilState())
					.withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
					.withCull(base.isCull())
					.withShaderDefine("LUCEAT_CUT", type.cut())
					.withShaderDefine("LUCEAT_THICK", type.thick())
					.withShaderDefine("LUCEAT_ALPHA", type.alpha())
					.withShaderDefine("LUCEAT_DARK", type.dark())
					.withShaderDefine("LUCEAT_FLAT", type.flat());
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
				Luceat.LOGGER.warn("Luceat clouds: the {} cloud shader did not compile, using vanilla clouds", type);
			} else {
				Luceat.LOGGER.info("Luceat clouds: {} pipeline ready", type);
			}
		} catch (RuntimeException | LinkageError error) {
			Luceat.LOGGER.warn("Luceat clouds: could not build the {} pipeline, using vanilla clouds", type, error);
			pipeline = null;
		}
		if (pipeline != null) {
			PIPELINES.put(type, pipeline);
		}
		return pipeline;
	}
}
