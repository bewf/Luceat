package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;

public final class LightSmoother {
	private static final float FADE_IN_SECONDS = 0.1F;
	private static final float FADE_OUT_SECONDS = 0.2F;
	private static final float RADIUS_SECONDS = 0.2F;

	private static final class State {
		double x;
		double y;
		double z;
		float red;
		float green;
		float blue;
		float radius;
		float target;
		boolean placed;
		boolean fire;
		boolean noShadow;
		int vanilla;
		int shape;
		float fade;
		long frame;
	}

	private static final Map<Long, State> STATES = new HashMap<>();
	private static long frame;
	private static long lastNanos;

	private LightSmoother() {
	}

	public static void clear() {
		STATES.clear();
		lastNanos = 0L;
	}

	public static float settled(long key) {
		State state = STATES.get(key);
		if (state == null || state.fade < 1.0F || Math.abs(state.radius - state.target) > 0.05F) {
			return -1.0F;
		}
		return state.target;
	}

	public static long key(int kind, int entityId, int sub) {
		return ((long) kind << 40) | ((long) sub << 32) | (entityId & 0xFFFFFFFFL);
	}

	public static List<LightSource> apply(List<LightSource> lights) {
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
		lastNanos = now;
		frame++;
		float flicker = LuceatConfig.get().flicker;
		boolean directional = LuceatConfig.get().directionalLights;
		float seconds = (float) ((now / 1_000_000L) % 1_000_000L / 1000.0);
		float ease = LuceatConfig.get().lightEase;
		boolean instant = ease < 0.02F;
		float easeRadius = instant ? 1.0F : 1.0F - (float) Math.exp(-dt / (RADIUS_SECONDS * ease));
		float fadeIn = instant ? 1.0F : dt / (FADE_IN_SECONDS * ease);
		float fadeOut = instant ? 1.0F : dt / (FADE_OUT_SECONDS * ease);

		List<Long> order = new ArrayList<>(lights.size() + 4);
		for (LightSource light : lights) {
			long key = light.id() != 0L ? light.id() : positionKey(light);
			State state = STATES.get(key);
			if (state != null && state.frame == frame) {
				continue;
			}
			if (state == null) {
				state = new State();
				state.radius = light.radius();
				STATES.put(key, state);
			}
			state.x = light.x();
			state.y = light.y();
			state.z = light.z();
			state.red = light.red();
			state.green = light.green();
			state.blue = light.blue();
			state.placed = light.placed();
			state.fire = light.fire();
			state.noShadow = light.noShadow();
			state.vanilla = light.vanilla();
			state.shape = directional ? light.shape() : 0;
			state.target = light.radius();
			state.radius += (light.radius() - state.radius) * easeRadius;
			state.fade = Math.min(1.0F, state.fade + fadeIn);
			state.frame = frame;
			order.add(key);
		}
		Iterator<Map.Entry<Long, State>> iterator = STATES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, State> entry = iterator.next();
			State state = entry.getValue();
			if (state.frame == frame) {
				continue;
			}
			state.fade -= fadeOut;
			if (state.fade <= 0.0F) {
				iterator.remove();
			} else if (order.size() < LightBuffer.MAX_LIGHTS) {
				order.add(entry.getKey());
			}
		}
		List<LightSource> result = new ArrayList<>(order.size());
		for (long key : order) {
			State state = STATES.get(key);
			float curve = state.fade * state.fade * (3.0F - 2.0F * state.fade);
			float radius = state.radius * curve;
			if (state.fire && flicker > 0.0F) {
				float noise = Flame.noise(seconds, Flame.seedByte(key));
				radius += flicker * 1.3F * noise;
			}
			result.add(new LightSource(state.x, state.y, state.z, state.red, state.green, state.blue, Math.max(radius, 0.0F), state.placed, state.fire, key, state.noShadow, state.vanilla, state.shape));
		}
		return result;
	}

	private static long positionKey(LightSource light) {
		long key = ((long) Math.floor(light.x() * 4.0) * 73856093L) ^ ((long) Math.floor(light.y() * 4.0) * 19349663L) ^ ((long) Math.floor(light.z() * 4.0) * 83492791L) ^ 0x5F3759DFL;
		return key == 0L ? 1L : key;
	}
}
