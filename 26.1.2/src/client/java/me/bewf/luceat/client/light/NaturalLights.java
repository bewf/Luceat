package me.bewf.luceat.client.light;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;

public final class NaturalLights {
	private static final LightSpec FIRE = new LightSpec(10, 1.0F, 0.58F, 0.22F);
	private static final LightSpec BLAZE = new LightSpec(10, 1.0F, 0.55F, 0.2F);
	private static final LightSpec MAGMA = new LightSpec(8, 1.0F, 0.4F, 0.1F);
	private static final LightSpec ALLAY = new LightSpec(8, 0.35F, 0.8F, 1.0F);
	private static final LightSpec GLOW_SQUID = new LightSpec(8, 0.2F, 0.9F, 0.8F);
	private static final LightSpec ORB = new LightSpec(4, 0.6F, 1.0F, 0.3F);
	private static final LightSpec CRYSTAL = new LightSpec(10, 1.0F, 0.4F, 0.9F);
	private static final LightSpec TNT = new LightSpec(6, 1.0F, 0.95F, 0.8F);
	private static final LightSpec ROCKET = new LightSpec(8, 1.0F, 0.8F, 0.5F);
	private static final LightSpec DRAGON_FIRE = new LightSpec(10, 0.8F, 0.3F, 1.0F);
	private static final LightSpec SKULL = new LightSpec(6, 0.35F, 0.45F, 0.6F);
	private static final LightSpec CHARGED = new LightSpec(6, 0.4F, 0.6F, 1.0F);

	private NaturalLights() {
	}

	public static LightSpec of(Entity entity) {
		EntityType<?> type = entity.getType();
		if (type == EntityType.BLAZE) {
			return BLAZE;
		}
		if (type == EntityType.MAGMA_CUBE) {
			return MAGMA;
		}
		if (type == EntityType.ALLAY) {
			return ALLAY;
		}
		if (type == EntityType.GLOW_SQUID) {
			return GLOW_SQUID;
		}
		if (type == EntityType.EXPERIENCE_ORB) {
			return ORB;
		}
		if (type == EntityType.END_CRYSTAL) {
			return CRYSTAL;
		}
		if (type == EntityType.TNT) {
			return TNT;
		}
		if (type == EntityType.FIREWORK_ROCKET) {
			return ROCKET;
		}
		if (type == EntityType.FIREBALL || type == EntityType.SMALL_FIREBALL) {
			return FIRE;
		}
		if (type == EntityType.DRAGON_FIREBALL) {
			return DRAGON_FIRE;
		}
		if (type == EntityType.WITHER_SKULL) {
			return SKULL;
		}
		if (entity instanceof Creeper creeper && creeper.isPowered()) {
			return CHARGED;
		}
		if (entity.isOnFire()) {
			return FIRE;
		}
		return null;
	}
}
