package me.bewf.luceat.client.gui.preview;

public enum SceneId {
	CAMPFIRE("showcase"),
	STAGE("room"),
	BOUNCE("bounce"),
	SHINY("shiny");

	private final String key;

	SceneId(String key) {
		this.key = key;
	}

	public Scene scene() {
		return Scenes.scene(key);
	}
}
