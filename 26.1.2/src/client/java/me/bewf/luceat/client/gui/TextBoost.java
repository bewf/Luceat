package me.bewf.luceat.client.gui;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class TextBoost {
	private static final Identifier INTENSITY = Identifier.fromNamespaceAndPath("luceat", "core/gui_text_intensity");
	private static final Identifier COLOUR = Identifier.fromNamespaceAndPath("luceat", "core/gui_text_color");
	private static final Map<RenderPipeline, RenderPipeline> BUILT = new IdentityHashMap<>();

	private TextBoost() {
	}

	public static RenderPipeline choose(RenderPipeline base) {
		if (base == RenderPipelines.GUI_TEXT_INTENSITY) {
			return build(base, INTENSITY, "pipeline/luceat_gui_text_intensity");
		}
		if (base == RenderPipelines.GUI_TEXT) {
			return build(base, COLOUR, "pipeline/luceat_gui_text_color");
		}
		return base;
	}

	public static void reload() {
		BUILT.clear();
	}

	private static RenderPipeline build(RenderPipeline base, Identifier fragment, String location) {
		if (BUILT.containsKey(base)) {
			RenderPipeline known = BUILT.get(base);
			return known != null ? known : base;
		}
		RenderPipeline custom = null;
		try {
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation(location)
					.withVertexShader(base.getVertexShader())
					.withFragmentShader(fragment)
					.withColorTargetState(base.getColorTargetState())
					.withDepthStencilState(base.getDepthStencilState())
					.withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
					.withCull(base.isCull());
			for (String sampler : base.getSamplers()) {
				builder.withSampler(sampler);
			}
			for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
				if (uniform.textureFormat() != null) {
					builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
				} else {
					builder.withUniform(uniform.name(), uniform.type());
				}
			}
			RenderPipeline built = builder.build();
			CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(built);
			custom = compiled.isValid() ? built : null;
		} catch (RuntimeException | LinkageError error) {
			custom = null;
		}
		BUILT.put(base, custom);
		return custom != null ? custom : base;
	}
}
