package me.bewf.luceat.client.sky;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LuceatClouds {
	private static final Identifier SHADER = Identifier.fromNamespaceAndPath("luceat", "core/clouds");
	private static RenderPipeline pipeline;
	private static boolean tried;

	private LuceatClouds() {
	}

	public static RenderPipeline choose(RenderPipeline vanilla) {
		if (vanilla != RenderPipelines.CLOUDS || !LuceatConfig.get().luceatClouds || IrisCompat.packOn()) {
			return vanilla;
		}
		RenderPipeline custom = pipeline();
		return custom != null ? custom : vanilla;
	}

	public static void reload() {
		pipeline = null;
		tried = false;
	}

	private static RenderPipeline pipeline() {
		if (tried) {
			return pipeline;
		}
		tried = true;
		try {
			RenderPipeline base = RenderPipelines.CLOUDS;
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation("pipeline/luceat_clouds")
					.withVertexShader(SHADER)
					.withFragmentShader(SHADER)
					.withColorTargetState(base.getColorTargetState())
					.withDepthStencilState(base.getDepthStencilState())
					.withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
					.withCull(base.isCull());
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
				Luceat.LOGGER.warn("Luceat clouds: the cloud shader did not compile, using vanilla clouds");
			} else {
				Luceat.LOGGER.info("Luceat clouds: custom cloud pipeline ready");
			}
		} catch (RuntimeException | LinkageError error) {
			Luceat.LOGGER.warn("Luceat clouds: could not build the cloud pipeline, using vanilla clouds", error);
			pipeline = null;
		}
		return pipeline;
	}
}
