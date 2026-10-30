package me.bewf.luceat.client.gui.glass;

public final class Icons {
	public static final String[] SUN = {
		"....#....",
		".#..#..#.",
		"..#####..",
		"..#...#..",
		"###...###",
		"..#...#..",
		"..#####..",
		".#..#..#.",
		"....#...."
	};
	public static final String[] SHADOW = {
		"..#####..",
		".####..#.",
		"#####...#",
		"#####...#",
		"#####...#",
		"#####...#",
		"#####...#",
		".####..#.",
		"..#####.."
	};
	public static final String[] BULB = {
		"..#####..",
		".#.....#.",
		"#.......#",
		"#.......#",
		".#.....#.",
		"..#...#..",
		"..#####..",
		"...###...",
		"....#...."
	};
	public static final String[] FLAME = {
		"....#....",
		"...##....",
		"..###.#..",
		"..####.#.",
		".#######.",
		".#######.",
		".#######.",
		"..#####..",
		"...###..."
	};
	public static final String[] GAUGE = {
		"..#####..",
		".#.....#.",
		"#.....#.#",
		"#....#..#",
		"#...#...#",
		"#..#....#",
		"#.......#",
		".#.....#.",
		"..#####.."
	};
	public static final String[] GEAR = {
		".#.###.#.",
		"#########",
		".##...##.",
		"###...###",
		"###...###",
		"###...###",
		".##...##.",
		"#########",
		".#.###.#."
	};
	public static final String[] CUBE = {
		"..#####..",
		".#.....##",
		"#######.#",
		"#.....#.#",
		"#.....#.#",
		"#.....###",
		"#.....#..",
		"#######..",
		"........."
	};
	public static final String[] BOLT = {
		"....##...",
		"...##....",
		"..##.....",
		".######..",
		"...##....",
		"..##.....",
		".##......",
		"##.......",
		"#........"
	};
	public static final String[] INFO = {
		"..#####..",
		".#.....#.",
		"#...#...#",
		"#.......#",
		"#...#...#",
		"#...#...#",
		"#...#...#",
		".#.....#.",
		"..#####.."
	};
	public static final String[] SEARCH = {
		"..####...",
		".#....#..",
		"#......#.",
		"#......#.",
		"#......#.",
		".#....#..",
		"..####.#.",
		".......#.",
		"........#"
	};
	public static final String[] RESET = {
		"..####.#.",
		".#....###",
		"#......#.",
		"#........",
		"#.......#",
		".#.....#.",
		"..#####.."
	};
	public static final String[] CLOSE = {
		"#.......#",
		".#.....#.",
		"..#...#..",
		"...#.#...",
		"....#....",
		"...#.#...",
		"..#...#..",
		".#.....#.",
		"#.......#"
	};
	public static final String[] CHEVRON = {
		"#...#",
		".#.#.",
		"..#.."
	};
	public static final String[] MOUSE = {
		"..###..",
		".#.#.#.",
		"#..#..#",
		"#.....#",
		"#.....#",
		"#.....#",
		".#...#.",
		"..###.."
	};
	public static final String[] EXPAND = {
		"###...###",
		"#.......#",
		"#.......#",
		".........",
		".........",
		".........",
		"#.......#",
		"#.......#",
		"###...###"
	};
	public static final String[] TRASH = {
		"...###...",
		"#########",
		".........",
		".#######.",
		".#.#.#.#.",
		".#.#.#.#.",
		".#.#.#.#.",
		".#.#.#.#.",
		".#######."
	};
	public static final String[] CHECK = {
		"........#",
		".......#.",
		"#.....#..",
		".#...#...",
		"..#.#....",
		"...#....."
	};

	public static final String[] STAR = {
		"....#....",
		"...###...",
		"..#####..",
		"#########",
		"..#####..",
		"...###...",
		"....#...."
	};
	public static final String[] SLIDERS = {
		"..#......",
		"#########",
		"..#......",
		".........",
		"......#..",
		"#########",
		"......#.."
	};

	private Icons() {
	}

	public static void draw(Surface s, int x, int y, String[] pattern, int argb) {
		for (int row = 0; row < pattern.length; row++) {
			String line = pattern[row];
			int run = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == '#';
				if (on && run < 0) {
					run = col;
				} else if (!on && run >= 0) {
					s.fill(x + run, y + row, x + col, y + row + 1, argb);
					run = -1;
				}
			}
		}
	}

	public static int width(String[] pattern) {
		return pattern[0].length();
	}

	public static int height(String[] pattern) {
		return pattern.length;
	}
}
