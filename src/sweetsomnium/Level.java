package sweetsomnium;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One level: the tile map, platforms, candies, door and scenery.
 *
 * Levels are written as text maps (one character per 48x48 tile), plus a short
 * list of platforms, which can sit on half-tile positions.
 */
public class Level {

    public static final int TILE = 48;

    private final int number;
    private final String name;
    private final char[][] tiles;
    private final int cols;
    private final int rows;
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Collectible> collectibles = new ArrayList<>();
    private final List<Point> lollipopTrees = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private Door door;
    private double spawnX;
    private double spawnY;
    private final BufferedImage scenery; // ground, blocks and trees, pre-drawn once

    public static Level create(int number) {
        return switch (number) {
            case 1 -> candyLand();
            default -> throw new IllegalArgumentException("Level " + number + " isn't built yet");
        };
    }

    /**
     * Level 1: Candy Land. Traced from docs/reference/level1_layout.jpg (49 x 16 tiles).
     *
     * Map legend:
     *   .  sky
     *   #  candy ground (the top tile is picked automatically)
     *   B  chocolate block
     *   c  candy to collect
     *   P  player start
     *   D  exit door (stands on the tile below it)
     *   L  lollipop tree (scenery, not solid)
     *   g  red gummy that paces back and forth   (enemies = the red dots in the reference)
     *   h  red gummy that bounces on the spot (used on the tiny platform)
     *   w  green gummy worm, crawls slowly
     */
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
            "#####Lc.B...###...##.L.w#..##..##....#######L.D..", // 12
            "#########################..##..##....############", // 13
            "#########################..##..##....############", // 14
            "#########################..##..##....############", // 15
        };
        // One-way platforms: { left edge in tiles, row line its top sits on, width in tiles }.
        // The small ones are centred in their gaps, like in the reference.
        double[][] platforms = {
            {15.0, 11, 2},
            {25.5, 11, 1},
            {29.5, 10, 1},
            {33.5,  9, 1},
            {35.5,  8, 1},
        };
        return new Level(1, "Candy Land", map, platforms);
    }

    private Level(int number, String name, String[] map, double[][] platformData) {
        this.number = number;
        this.name = name;
        this.rows = map.length;
        this.cols = map[0].length();
        this.tiles = new char[rows][cols];

        boolean hasSpawn = false;
        List<int[]> enemySpots = new ArrayList<>(); // {col, row, char}; built after the platforms exist
        for (int r = 0; r < rows; r++) {
            if (map[r].length() != cols) {
                throw new IllegalArgumentException("Level " + number + ", map row " + r + " is "
                        + map[r].length() + " characters long, expected " + cols);
            }
            for (int c = 0; c < cols; c++) {
                char ch = map[r].charAt(c);
                switch (ch) {
                    case '#', 'B' -> tiles[r][c] = ch;
                    case 'c' -> collectibles.add(new Collectible(c * TILE, r * TILE));
                    case 'P' -> {
                        spawnX = c * TILE + (TILE - Player.WIDTH) / 2.0;
                        spawnY = (r + 1) * TILE - Player.HEIGHT;
                        hasSpawn = true;
                    }
                    case 'D' -> door = new Door(c, r);
                    case 'L' -> lollipopTrees.add(new Point(c * TILE + TILE / 2, (r + 1) * TILE));
                    case 'g', 'h', 'w' -> enemySpots.add(new int[] {c, r, ch});
                    default -> { } // '.' and anything else: empty
                }
                if (tiles[r][c] == 0) {
                    tiles[r][c] = '.';
                }
            }
        }
        if (!hasSpawn || door == null) {
            throw new IllegalArgumentException("Level " + number + " needs a 'P' (start) and a 'D' (door)");
        }
        for (double[] p : platformData) {
            platforms.add(new Platform(p[0], (int) p[1], (int) p[2]));
        }
        for (int[] e : enemySpots) {
            Enemy.Type type = switch (e[2]) {
                case 'h' -> Enemy.Type.GUMMY_IDLE;
                case 'w' -> Enemy.Type.WORM;
                default -> Enemy.Type.GUMMY;
            };
            enemies.add(new Enemy(type, e[0], e[1], this));
        }
        scenery = drawScenery();
    }

    // ---------------------------------------------------------------- queries

    public int number() {
        return number;
    }

    public String name() {
        return name;
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

    public List<Platform> platforms() {
        return Collections.unmodifiableList(platforms);
    }

    /** True if there's ground or a platform top right at (px, feetY). */
    public boolean hasFloorAt(double px, double feetY) {
        if (isSolid(tile(px), tile(feetY + 1))) {
            return true;
        }
        for (Platform p : platforms) {
            if (px >= p.left() && px <= p.right() && Math.abs(p.top() - feetY) < 1) {
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

    public List<Enemy> enemies() {
        return Collections.unmodifiableList(enemies);
    }

    public List<Collectible> collectibles() {
        return Collections.unmodifiableList(collectibles);
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

    public void update() {
        for (Collectible c : collectibles) {
            c.update();
        }
        door.update();
        for (Enemy e : enemies) {
            e.update(this);
        }
    }

    /** Draws everything in the level except the player. g is already offset by the camera. */
    public void draw(Graphics2D g) {
        g.drawImage(scenery, 0, 0, null);
        for (Platform p : platforms) {
            p.draw(g);
        }
        door.draw(g);
        for (Collectible c : collectibles) {
            c.draw(g);
        }
        for (Enemy e : enemies) {
            e.draw(g);
        }
    }

    private BufferedImage drawScenery() {
        BufferedImage img = new BufferedImage(pixelWidth(), pixelHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (Point p : lollipopTrees) {
            drawLollipopTree(g, p.x, p.y);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                BufferedImage sprite = switch (tiles[r][c]) {
                    case '#' -> (r == 0 || tiles[r - 1][c] != '#') ? Assets.GROUND_TOP : Assets.GROUND_FILL;
                    case 'B' -> Assets.CHOCOLATE_BLOCK;
                    default -> null;
                };
                if (sprite != null) {
                    g.drawImage(sprite, c * TILE, r * TILE, null);
                }
            }
        }
        g.dispose();
        return img;
    }

    /** A lollipop on a stick, standing on groundY. Drawn without anti-aliasing to match the pixel sprites. */
    private static void drawLollipopTree(Graphics2D g, int cx, int groundY) {
        Color outline = Color.BLACK;
        int stickH = 40;
        int r = 19;
        int cy = groundY - stickH - r + 6;

        // Stick
        g.setColor(outline);
        g.fillRect(cx - 3, cy, 7, groundY - cy);
        g.setColor(new Color(0xFFF4FA));
        g.fillRect(cx - 2, cy, 5, groundY - cy);
        g.setColor(new Color(0xDEBDE3));
        g.fillRect(cx + 1, cy, 2, groundY - cy);

        // Candy disc with a swirl
        Shape disc = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        g.setColor(new Color(0xEC8ACD));
        g.fill(disc);
        Shape oldClip = g.getClip();
        g.clip(disc);
        Path2D spiral = new Path2D.Double();
        double turns = 3.2;
        double maxT = turns * 2 * Math.PI;
        spiral.moveTo(cx, cy);
        for (double t = 0; t <= maxT; t += 0.12) {
            double rr = (r + 2) * t / maxT;
            spiral.lineTo(cx + rr * Math.cos(t), cy + rr * Math.sin(t));
        }
        g.setColor(new Color(0xF2D2F6));
        g.setStroke(new BasicStroke(4));
        g.draw(spiral);
        g.setStroke(new BasicStroke(1));
        g.setClip(oldClip);
        g.setColor(outline);
        g.draw(disc);
        g.setColor(Color.WHITE);
        g.fillRect(cx - r / 2 - 2, cy - r / 2 - 1, 4, 2);
        g.fillRect(cx - r / 2 - 2, cy - r / 2 + 1, 2, 2);

        // Ribbon where the stick meets the candy
        int by = cy + r + 3;
        g.setColor(outline);
        g.fillRect(cx - 8, by - 3, 17, 7);
        g.setColor(new Color(0x9FE3D0));
        g.fillRect(cx - 7, by - 2, 6, 5);
        g.fillRect(cx + 2, by - 2, 6, 5);
        g.setColor(new Color(0x5FB8A3));
        g.fillRect(cx - 1, by - 2, 3, 5);
    }
}
