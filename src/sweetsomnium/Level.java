package sweetsomnium;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * One level: the tile map, platforms, candies, enemies, spikes and door.
 *
 * Levels are written as text maps (one character per 48x48 tile), plus a short
 * list of platforms, which can sit on half-tile positions and can move.
 *
 * Map legend (all levels):
 *   .  sky
 *   #  ground (the top tile is picked automatically)
 *   B  block
 *   c  candy / pill to collect
 *   P  player start
 *   D  exit door (stands on the tile below it)
 *   ^  spike standing on the tile below      v  spike hanging from the tile above
 *   g  gummy that paces back and forth       h  gummy that bounces on the spot
 *   w  worm that crawls back and forth
 *   t  twitchy gummy (fast, stops and starts) j  gummy that hops when you're near
 *   k  worm that rushes at you when you're near
 *   m  the level 3 creature, hanging above this column
 */
public class Level {

    public static final int TILE = 48;
    public static final int LAST_LEVEL = 3;

    /** Marks a platform that crumbles, in the platform lists below. */
    private static final double FALLS = -1;

    private final int number;
    private final String name;
    private final Theme theme;
    private final char[][] tiles;
    private final int cols;
    private final int rows;
    // Platforms, enemies, spikes and monsters never change after the level is built,
    // so they are plain arrays sized to fit. Candies get picked up and removed, so they
    // live in a linked list.
    private final Platform[] platforms;
    private final Enemy[] enemies;
    private final Spike[] spikes;
    private final Monster[] monsters;
    private final CandyList candies = new CandyList();
    private final int totalCandies;
    private Door door;
    private double spawnX;
    private double spawnY;
    private final BufferedImage scenery; // ground and blocks (or level 3's whole backdrop), pre-drawn once

    public static Level create(int number) {
        switch (number) {
            case 1:
                return candyLand();
            case 2:
                return sourTurn();
            case 3:
                return withdrawal();
            default:
                throw new IllegalArgumentException("Level " + number + " isn't built yet");
        }
    }

    /** Level 1: Candy Land. Traced from docs/reference/level1_layout.jpg (49 x 16 tiles). */
    private static Level candyLand() {
        String[] map = {
           //          1111111111222222222233333333334444444444    <- column numbers
           //0123456789012345678901234567890123456789012345678
            ".................................................", // 0
            ".................................................", // 1
            ".................................................", // 2
            ".................................................", // 3
            ".................................................", // 4
            ".................................................", // 5
            ".................................................", // 6
            "......................................c..........", // 7
            "..................................h..###.........", // 8
            ".P...................................####........", // 9
            "###................c........g..##....#####.......", // 10
            "####......B.##....##.......##..##....######......", // 11
            "#####.c.B...###...##...w#..##..##....#######..D..", // 12
            "#########################..##..##....############", // 13
            "#########################..##..##....############", // 14
            "#########################..##..##....############", // 15
        };
        // Platforms: { left edge in tiles, row line its top sits on, width in tiles }.
        // The small ones are centred in their gaps, like in the reference.
        double[][] platforms = {
            {15.0, 11, 2},
            {25.5, 11, 1},
            {29.5, 10, 1},
            {33.5,  9, 1},
            {35.5,  8, 1},
        };
        return new Level(1, "Candy Land", Theme.candyLand(), map, platforms);
    }

    /**
     * Level 2: Sour Turn. Traced from docs/reference/level2_layout.png (49 x 16 tiles).
     * The layout had no pills, enemies or door, so those are placed here.
     */
    private static Level sourTurn() {
        String[] map = {
           //          1111111111222222222233333333334444444444    <- column numbers
           //0123456789012345678901234567890123456789012345678
            ".................................................", // 0
            ".................................................", // 1
            ".................................................", // 2
            ".................................................", // 3
            ".................................................", // 4
            ".................................................", // 5
            ".................................................", // 6
            ".................................................", // 7
            "...............c.....c.t..c......................", // 8
            "...........c...B...BBBBB................c^.......", // 9
            "...........B...B...BBBBB.......B..c^....##.......", // 10
            "...........B...B...v.........B.B........##.......", // 11
            ".Pc.^.B^^^^B...B.t...^.^..k..B^B.....^..##....Dj.", // 12
            "############...#################....##..##..#####", // 13
            "############...#################....##..##..#####", // 14
            "############...#################....##..##..#####", // 15
        };
        // Platforms: { left edge in tiles, row line its top sits on, width in tiles,
        //              and optionally: tiles to travel sideways, tiles to travel down, frames per round trip }.
        double[][] platforms = {
            { 7.5, 11, 1},
            { 9.5, 10, 1},
            {12.5, 10, 1,  0, 1, 180},   // over the first pit: sinks a tile and comes back up
            {14.0,  9, 1},
            {17.0,  9, 1},
            {25.0,  9, 2},
            {28.0, 11, 1},
            {33.0, 10, 1, -1, 0, 210},   // over the wide pit: slides a tile left and back
            {34.0, 11, 2},
            {38.0, 11, 1},
            {39.0, 10, 1},
            {42.0, 10, 1},
        };
        return new Level(2, "Sour Turn", Theme.sourTurn(), map, platforms);
    }

    /**
     * Level 3: Withdrawal. Traced from docs/reference/level3_layout.png (70 x 16 tiles).
     * That picture is also drawn as the level's backdrop (the faces, and the ground baked in),
     * so the tiles here only decide where you can stand. Pills and creatures are placed here.
     */
    private static Level withdrawal() {
        String[] map = {
           //          1111111111222222222233333333334444444444555555555566666666667777    <- column numbers
           //0123456789012345678901234567890123456789012345678901234567890123456789
            ".....................m......................m......m..............m...", // 0
            "......................................................................", // 1
            "......................................................................", // 2
            "....................................c......................c..........", // 3
            "..........................................................###.........", // 4
            ".....................................BB...................###.........", // 5
            ".....................................BB...................###.........", // 6
            "..........c....................c..........................###.........", // 7
            "...............................##................c........###.........", // 8
            "................c..............##.....c...................###.........", // 9
            ".....BBBBBBBBBBBB..............##....##......c............###.........", // 10
            "...c.B...................c.....##....##......#....##......###......D..", // 11
            ".........................##....##....##......#....##......###...######", // 12
            ".P..........c............##....##....##......#....##......###...######", // 13
            "########.##.##.##...##...##....##....##......#....##......###...######", // 14
            "########.##.##.##...##...##....##....##......#....##......###...######", // 15
        };
        // Platforms: { left edge in tiles, row line its top sits on, width in tiles }, FALLS = crumbles.
        double[][] platforms = {
            { 3, 12, 1}, { 4, 10, 1}, { 8,  8, 1}, {10,  8, 1},
            {18, 10, 1}, {19, 11, 1}, {18, 14, 1}, {23, 12, 1, FALLS},
            {27, 11, 1}, {28,  9, 1, FALLS}, {30,  8, 1}, {33,  6, 1},
            {35,  4, 2}, {35,  9, 2}, {40,  6, 1, FALLS}, {40, 10, 1},
            {42,  8, 2}, {47, 10, 1}, {48,  9, 2}, {53, 11, 1},
            {53,  8, 2}, {55, 10, 1}, {55,  6, 1, FALLS}, {57,  4, 1},
            {61,  4, 1},
        };
        return new Level(3, "Withdrawal", Theme.withdrawal(), map, platforms);
    }

    private Level(int number, String name, Theme theme, String[] map, double[][] platformData) {
        this.number = number;
        this.name = name;
        this.theme = theme;
        this.rows = map.length;
        this.cols = map[0].length();
        this.tiles = new char[rows][cols];

        // Count things first, so each array can be made exactly the right size
        for (int r = 0; r < rows; r++) {
            if (map[r].length() != cols) {
                throw new IllegalArgumentException("Level " + number + ", map row " + r + " is "
                        + map[r].length() + " characters long, expected " + cols);
            }
        }
        spikes = new Spike[countChars(map, "^v")];
        enemies = new Enemy[countChars(map, "ghwtjk")];
        monsters = new Monster[countChars(map, "m")];
        int[][] enemySpots = new int[enemies.length][]; // {col, row, char}; built after the platforms exist
        int[] monsterCols = new int[monsters.length];
        int spikeCount = 0;
        int enemyCount = 0;
        int monsterCount = 0;

        boolean hasSpawn = false;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = map[r].charAt(c);
                tiles[r][c] = '.';
                if (ch == '#' || ch == 'B') {
                    tiles[r][c] = ch;
                } else if (ch == 'c') {
                    candies.add(new Collectible(c * TILE, r * TILE, theme.collectible));
                } else if (ch == 'P') {
                    spawnX = c * TILE + (TILE - Player.WIDTH) / 2.0;
                    spawnY = (r + 1) * TILE - Player.HEIGHT;
                    hasSpawn = true;
                } else if (ch == 'D') {
                    door = new Door(c, r, theme.doorSaturation);
                } else if (ch == '^' || ch == 'v') {
                    spikes[spikeCount] = new Spike(c, r, ch == 'v', theme.spike);
                    spikeCount++;
                    tiles[r][c] = ch;
                } else if (ch == 'm') {
                    monsterCols[monsterCount] = c;
                    monsterCount++;
                } else if ("ghwtjk".indexOf(ch) >= 0) {
                    enemySpots[enemyCount] = new int[] {c, r, ch};
                    enemyCount++;
                }
                // '.' and anything else: empty
            }
        }
        if (!hasSpawn || door == null) {
            throw new IllegalArgumentException("Level " + number + " needs a 'P' (start) and a 'D' (door)");
        }
        totalCandies = candies.size();

        platforms = new Platform[platformData.length];
        for (int i = 0; i < platformData.length; i++) {
            double[] p = platformData[i];
            boolean moves = p.length >= 6;
            boolean falls = p.length == 4 && p[3] == FALLS;
            platforms[i] = new Platform(p[0], (int) p[1], (int) p[2],
                    moves ? p[3] : 0, moves ? p[4] : 0, moves ? (int) p[5] : 0, falls, theme);
        }
        for (int i = 0; i < monsterCols.length; i++) {
            monsters[i] = new Monster(monsterCols[i], this);
        }
        for (int i = 0; i < enemySpots.length; i++) {
            int[] e = enemySpots[i];
            int kind = (e[2] == 'w' || e[2] == 'k') ? Enemy.WORM : Enemy.GUMMY;
            int behavior;
            if (e[2] == 'h') {
                behavior = Enemy.IDLE;
            } else if (e[2] == 't') {
                behavior = Enemy.TWITCHY;
            } else if (e[2] == 'j') {
                behavior = Enemy.HOPPER;
            } else if (e[2] == 'k') {
                behavior = Enemy.CHASER;
            } else {
                behavior = Enemy.PATROL;
            }
            enemies[i] = new Enemy(kind, behavior, e[0], e[1], this);
        }
        scenery = drawScenery();
    }

    /** How many characters in the map are any of {@code wanted}. */
    private static int countChars(String[] map, String wanted) {
        int count = 0;
        for (String row : map) {
            for (int c = 0; c < row.length(); c++) {
                if (wanted.indexOf(row.charAt(c)) >= 0) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- queries

    public int number() {
        return number;
    }

    public String name() {
        return name;
    }

    public Theme theme() {
        return theme;
    }

    public int pixelWidth() {
        return cols * TILE;
    }

    public int pixelHeight() {
        return rows * TILE;
    }

    /** Solid tiles block movement. The left/right edges of the level act as walls; below the map is a pit. */
    public boolean isSolid(int col, int row) {
        if (col < 0 || col >= cols) {
            return true;
        }
        if (row < 0 || row >= rows) {
            return false;
        }
        char t = tiles[row][col];
        return t == '#' || t == 'B';
    }

    /** Enemies treat spike tiles like walls. */
    public boolean isSpike(int col, int row) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return false;
        }
        return tiles[row][col] == '^' || tiles[row][col] == 'v';
    }

    public Platform[] platforms() {
        return platforms;
    }

    /** True if there's ground or a platform top right at (px, feetY). */
    public boolean hasFloorAt(double px, double feetY) {
        if (isSolid(tile(px), tile(feetY + 1))) {
            return true;
        }
        for (Platform p : platforms) {
            if (p.isSolid() && px >= p.left() && px <= p.right() && Math.abs(p.top() - feetY) < 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where something placed at tile (col, row) would stand: the nearest floor at or below
     * that tile, anywhere across its width. Returns {centre x, floor y}.
     */
    public double[] standingSpot(int col, int row) {
        double left = col * TILE;
        double right = left + TILE;
        double floorY = Double.MAX_VALUE;
        double centerX = left + TILE / 2.0;
        for (int r = row; r < rows; r++) {
            if (isSolid(col, r)) {
                floorY = r * TILE;
                break;
            }
        }
        for (Platform p : platforms) {
            if (p.top() >= row * TILE && p.top() < floorY && p.right() > left && p.left() < right) {
                floorY = p.top();
                centerX = (Math.max(left, p.left()) + Math.min(right, p.right())) / 2;
            }
        }
        if (floorY == Double.MAX_VALUE) {
            throw new IllegalArgumentException("Enemy at column " + col + ", row " + row + " has nothing to stand on");
        }
        return new double[] {centerX, floorY};
    }

    private static int tile(double pixel) {
        return (int) Math.floor(pixel / TILE);
    }

    public Enemy[] enemies() {
        return enemies;
    }

    public Spike[] spikes() {
        return spikes;
    }

    public Monster[] monsters() {
        return monsters;
    }

    /** The candies still in the level. Picked-up ones are removed once they finish popping. */
    public CandyList candies() {
        return candies;
    }

    /** How many candies the level started with. */
    public int totalCandies() {
        return totalCandies;
    }

    public Door door() {
        return door;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnY() {
        return spawnY;
    }

    // ---------------------------------------------------------------- update & draw

    /** @param player the player's hitbox, so enemies can react to it (null on the title screen) */
    public void update(Rectangle player) {
        for (Platform p : platforms) {
            p.update();
        }
        for (CandyList.Node n = candies.first(); n != null; n = n.next) {
            n.candy.update();
        }
        candies.removeFinished();
        door.update();
        for (Enemy e : enemies) {
            e.update(this, player);
        }
        for (Monster m : monsters) {
            m.update(player);
        }
    }

    /** Draws everything in the level except the player. g is already offset by the camera. */
    public void draw(Graphics2D g) {
        g.drawImage(scenery, 0, 0, null);
        for (Platform p : platforms) {
            if (theme.backdrop == null || p.crumbles()) { // the backdrop already shows the still ones
                p.draw(g);
            }
        }
        for (Spike s : spikes) {
            s.draw(g);
        }
        door.draw(g);
        for (CandyList.Node n = candies.first(); n != null; n = n.next) {
            n.candy.draw(g);
        }
        for (Enemy e : enemies) {
            e.draw(g);
        }
        for (Monster m : monsters) {
            m.draw(g);
        }
    }

    private BufferedImage drawScenery() {
        BufferedImage img = new BufferedImage(pixelWidth(), pixelHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (theme.backdrop != null) {
            g.drawImage(theme.backdrop, 0, 0, null);
            g.dispose();
            paintOutCrumblingPlatforms(img);
            return img;
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                BufferedImage sprite = null;
                if (tiles[r][c] == '#') {
                    sprite = (r == 0 || tiles[r - 1][c] != '#') ? theme.groundTop : theme.groundFill;
                } else if (tiles[r][c] == 'B') {
                    sprite = theme.block;
                }
                if (sprite != null) {
                    g.drawImage(sprite, c * TILE, r * TILE, null);
                }
            }
        }
        g.dispose();
        return img;
    }

    /**
     * Crumbling platforms are drawn separately (so they can fall), but level 3's backdrop
     * already has them painted in. Cover each one with the strip of picture just above it.
     */
    private void paintOutCrumblingPlatforms(BufferedImage img) {
        int bar = 8; // the striped bar is 8 pixels tall
        for (Platform p : platforms) {
            if (!p.crumbles()) {
                continue;
            }
            int[] area = p.barArea();
            for (int y = 0; y < bar; y++) {
                for (int x = area[0]; x < area[0] + area[2] && x < img.getWidth(); x++) {
                    img.setRGB(x, area[1] + y, img.getRGB(x, area[1] - bar + y));
                }
            }
        }
    }
}
