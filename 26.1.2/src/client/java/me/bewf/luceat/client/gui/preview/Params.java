package me.bewf.luceat.client.gui.preview;

import me.bewf.luceat.client.sky.CloudType;

public record Params(
	boolean shadows,
	float brightness,
	float maxBrightness,
	float daylightFade,
	float directional,
	float shadowStrength,
	float vanillaReplace,
	float placedStrength,
	float softRadius,
	float time,
	boolean vanilla,
	float shadowDistance,
	float bounce,
	float shine,
	CloudType cloud
) {
}
