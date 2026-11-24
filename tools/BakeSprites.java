import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/**
 * Offline asset baker: extracts hand-drawn graphite sprites, wispy fire drawings, and dark
 * high-contrast weathered dungeon flagstones from images/, applies grounding (bottom-masked rim
 * + foot AO), and packs src/main/resources/sprites/atlas.png (4096x4096, 8x8 cells, stride 512).
 *
 * Run: java -Djava.awt.headless=true tools/BakeSprites.java [--preview path/to/bake_check.png]
 */
public final class BakeSprites {
    static final int CELL = 508, PAD = 2, STRIDE = CELL + PAD * 2; // 512
    static final int COLS = 8, ROWS = 8, COUNT = 41;
    static final int ATLAS_W = COLS * STRIDE, ATLAS_H = ROWS * STRIDE; // 4096 x 4096

    // Atlas slot indices (must match com.archon.ui.PixelAtlas)
    static final int
            FLOOR_0 = 0, FLOOR_1 = 1, WALL_TOP = 2, WALL_FRONT = 3,
            WATER = 4, ACID = 5, OIL_SLICK = 6, ICE_TILE = 7,
            TAR_TILE = 8, FIRE_0 = 9, FIRE_1 = 10, GAS_PUFF = 11,
            THRALL = 12, ORC = 13, GOBLIN = 14, OGRE = 15, RAT = 16,
            BRAZIER = 17, BARREL = 18, CRATE = 19, PILLAR = 20, DOOR = 21,
            LOOT_BLADE = 22, LOOT_OIL = 23, LOOT_WATER = 24, LOOT_ACID = 25, LOOT_STONE = 26,
            TORCH_FIRE = 27, THRALL_ATTACK = 28, THRALL_CAST = 29, THRALL_GUARD = 30, THRALL_HURT = 31,
            GOBLIN_AIM = 32, GOBLIN_AFTER_SHOT = 33, GOBLIN_ASSASSIN = 34,
            GOBLIN_ASSASSIN_ATTACK = 35, OGRE_ATTACK = 36,
            BLOOD_SPLAT_0 = 37, BLOOD_SPLAT_1 = 38,
            ORC_ATTACK = 39, OGRE_ATTACK_2 = 40;

    private static final int DRAW_RES = 512;

    public static void main(String[] args) throws Exception {
        String previewOut = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--preview".equals(args[i])) previewOut = args[i + 1];
        }

        File imagesDir = new File("images");
        File outFile = new File("src/main/resources/sprites/atlas.png");
        outFile.getParentFile().mkdirs();

        BufferedImage[] cells = new BufferedImage[COUNT];

        // 1. B&W Tile2.jpg at 1:1 scale with crisp black tile border (bakeTiledBwFloor kept for rollback)
        File floorFile = findImage(imagesDir, "Tile2", "Tile", "floor_0");
        cells[FLOOR_0] = addTileBorder(bakeSingleBwFloor(floorFile, false));
        cells[FLOOR_1] = addTileBorder(bakeSingleBwFloor(floorFile, true));
        cells[WALL_TOP] = loadOrDrawTile(imagesDir, new String[]{"WallTop", "wall_top"}, BakeSprites::drawWallTop);
        cells[WALL_FRONT] = loadOrDrawTile(imagesDir, new String[]{"WallFront", "wall_front"}, BakeSprites::drawWallFront);

        // 2. Elemental tiles & hand-drawn fire sprites
        cells[WATER] = loadOrDrawSprite(imagesDir, new String[]{"Water", "water"}, 0.88, 0.06, false, false, BakeSprites::drawWaterDecal);
        cells[ACID] = loadOrDrawSprite(imagesDir, new String[]{"Acid", "acid"}, 0.88, 0.06, false, false, BakeSprites::drawAcidDecal);
        cells[OIL_SLICK] = loadOrDrawSprite(imagesDir, new String[]{"Oil", "oil_slick"}, 0.88, 0.06, false, false, BakeSprites::drawOilDecal);
        cells[ICE_TILE] = loadOrDrawSprite(imagesDir, new String[]{"Ice", "ice_tile"}, 0.92, 0.04, false, false, BakeSprites::drawIceDecal);
        cells[TAR_TILE] = loadOrDrawSprite(imagesDir, new String[]{"Tar", "tar_tile"}, 0.90, 0.05, false, false, BakeSprites::drawTarDecal);
        cells[FIRE_0] = loadFireOrFallback(imagesDir, new String[]{"Fire", "Fire0", "fire_0"}, 0.94, 0.02, 0);
        cells[FIRE_1] = loadFireOrFallback(imagesDir, new String[]{"Fire2", "Fire1", "fire_1"}, 0.94, 0.02, 1);
        cells[GAS_PUFF] = loadOrDrawSprite(imagesDir, new String[]{"Gas", "gas_puff"}, 0.90, 0.05, false, false, BakeSprites::drawGasPuff);
        cells[TORCH_FIRE] = loadFireOrFallback(imagesDir, new String[]{"Fire_torch", "fire_torch", "FireTorch", "torch_fire"}, 0.92, 0.04, 0);

        // 3. Creatures (bottom-masked rim + subtle foot AO for strong grounding)
        cells[THRALL] = loadPaperSprite(imagesDir, new String[]{"Thrall_idle", "Thrall2", "thrall_idle", "thrall"}, 0.87, 0.06);
        cells[ORC] = loadPaperSprite(imagesDir, new String[]{"Orc_idle", "Orc", "orc"}, 0.87, 0.06, false);
        cells[GOBLIN] = loadPaperSprite(imagesDir, new String[]{"Goblin_archer_idle", "Goblin_archer", "Goblin", "goblin"}, 0.78, 0.06, false, 0.24, 0.46, 0.33, 0.51);
        cells[OGRE] = loadPaperSprite(imagesDir, new String[]{"Ogre_idle", "Ogre", "ogre"}, 0.95, 0.05, false);
        cells[RAT] = loadOrDrawSprite(imagesDir, new String[]{"Rat", "rat"}, 0.64, 0.08, true, true, BakeSprites::drawRatPlaceholder);

        // 4. Props & Door
        cells[BRAZIER] = loadPaperSprite(imagesDir, new String[]{"Brazier", "brazier"}, 0.78, 0.07);
        cells[BARREL] = loadOrDrawSprite(imagesDir, new String[]{"Barrel", "barrel"}, 0.72, 0.07, true, true, BakeSprites::drawBarrelPlaceholder);
        cells[CRATE] = loadOrDrawSprite(imagesDir, new String[]{"Crate", "crate"}, 0.74, 0.07, true, true, BakeSprites::drawCratePlaceholder);
        cells[PILLAR] = loadOrDrawSprite(imagesDir, new String[]{"Pillar", "pillar"}, 0.90, 0.06, true, true, BakeSprites::drawPillarPlaceholder);
        cells[DOOR] = loadOrDrawSprite(imagesDir, new String[]{"Door", "door"}, 0.92, 0.06, true, true, BakeSprites::drawDoorPlaceholder);

        // 5. Ground Loot
        cells[LOOT_BLADE] = loadOrDrawSprite(imagesDir, new String[]{"LootBlade", "loot_blade"}, 0.72, 0.14, true, false, BakeSprites::drawLootBlade);
        cells[LOOT_OIL] = loadOrDrawSprite(imagesDir, new String[]{"LootOil", "loot_oil"}, 0.66, 0.14, true, false, g -> drawLootFlask(g, 1));
        cells[LOOT_WATER] = loadOrDrawSprite(imagesDir, new String[]{"LootWater", "loot_water"}, 0.66, 0.14, true, false, g -> drawLootFlask(g, 2));
        cells[LOOT_ACID] = loadOrDrawSprite(imagesDir, new String[]{"LootAcid", "loot_acid"}, 0.66, 0.14, true, false, g -> drawLootFlask(g, 3));
        cells[LOOT_STONE] = loadOrDrawSprite(imagesDir, new String[]{"LootStone", "loot_stone"}, 0.54, 0.18, true, false, BakeSprites::drawLootStone);

        // 6. Thrall & Enemy Combat Poses
        cells[THRALL_ATTACK] = loadPoseOrClone(imagesDir, new String[]{"Thrall_attack", "ThrallAttack", "thrall_attack"}, cells[THRALL]);
        cells[THRALL_CAST] = loadPoseOrClone(imagesDir, new String[]{"ThrallCast", "thrall_cast"}, cells[THRALL]);
        cells[THRALL_GUARD] = loadPoseOrClone(imagesDir, new String[]{"ThrallGuard", "thrall_guard"}, cells[THRALL]);
        cells[THRALL_HURT] = loadPoseOrClone(imagesDir, new String[]{"ThrallHurt", "thrall_hurt"}, cells[THRALL]);
        cells[GOBLIN_AIM] = loadPaperSprite(imagesDir, new String[]{"Goblin_archer_aim"}, 0.90, 0.06, false, 0.28, 0.25, 0.31, 0.52);
        cells[GOBLIN_AFTER_SHOT] = loadPaperSprite(imagesDir, new String[]{"Goblin_archer_after_shot"}, 0.90, 0.06, false, 0.28, 0.25, 0.31, 0.52);
        cells[GOBLIN_ASSASSIN] = loadPaperSprite(imagesDir, new String[]{"Goblin_assassin_idle", "Goblin_assassin"}, 0.88, 0.06, false, 0.67, 0.40);
        cells[GOBLIN_ASSASSIN_ATTACK] = loadPaperSprite(imagesDir, new String[]{"goblin_assassin_attack", "Goblin_assassin_attack"}, 0.94, 0.06, false);
        cells[OGRE_ATTACK] = loadPaperSprite(imagesDir, new String[]{"Ogre_attack"}, 0.95, 0.05, false);
        cells[ORC_ATTACK] = loadPaperSprite(imagesDir, new String[]{"Orc_attack", "orc_attack"}, 0.94, 0.06, false);
        cells[OGRE_ATTACK_2] = loadPaperSprite(imagesDir, new String[]{"Ogre_attack_2", "ogre_attack_2"}, 0.95, 0.05, false);
        cells[BLOOD_SPLAT_0] = loadOrDrawSprite(imagesDir, new String[]{"BloodSplat0", "blood_splat_0"}, 0.90, 0.05, false, false, g -> drawBloodSplat(g, 0));
        cells[BLOOD_SPLAT_1] = loadOrDrawSprite(imagesDir, new String[]{"BloodSplat1", "blood_splat_1"}, 0.90, 0.05, false, false, g -> drawBloodSplat(g, 1));

        // Pack into 4096x4096 atlas with 2px gutter padding
        BufferedImage atlas = new BufferedImage(ATLAS_W, ATLAS_H, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < COUNT; i++) {
            int ox = (i % COLS) * STRIDE + PAD;
            int oy = (i / COLS) * STRIDE + PAD;
            BufferedImage cell = cells[i];
            boolean extrude = i <= WALL_FRONT;
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    atlas.setRGB(ox + x, oy + y, cell.getRGB(x, y));
                }
            }
            if (extrude) {
                for (int p = 1; p <= PAD; p++) {
                    for (int x = 0; x < CELL; x++) {
                        atlas.setRGB(ox + x, oy - p, cell.getRGB(x, 0));
                        atlas.setRGB(ox + x, oy + CELL - 1 + p, cell.getRGB(x, CELL - 1));
                    }
                    for (int y = -PAD; y < CELL + PAD; y++) {
                        int sy = Math.max(0, Math.min(CELL - 1, y));
                        atlas.setRGB(ox - p, oy + y, cell.getRGB(0, sy));
                        atlas.setRGB(ox + CELL - 1 + p, oy + y, cell.getRGB(CELL - 1, sy));
                    }
                }
            }
        }
        ImageIO.write(atlas, "png", outFile);
        System.out.println("Baked " + COUNT + " sprites into " + outFile.getPath()
                + " (" + ATLAS_W + "x" + ATLAS_H + ")");

        if (previewOut != null) {
            writePreview(cells, new File(previewOut));
        }
    }

    // ---- Source image lookup ----

    private static File findImageOrNull(File dir, String... bases) {
        if (!dir.isDirectory()) return null;
        String[] exts = {".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG"};
        for (String base : bases) {
            for (String ext : exts) {
                File f = new File(dir, base + ext);
                if (f.isFile()) return f;
            }
        }
        return null;
    }

    private static File findImage(File dir, String... bases) {
        File f = findImageOrNull(dir, bases);
        if (f == null) throw new IllegalStateException("Missing required image in " + dir + " for " + bases[0]);
        return f;
    }

    private static BufferedImage loadOrDrawTile(File dir, String[] names, DrawFn fallback) throws Exception {
        File f = findImageOrNull(dir, names);
        if (f != null) return bakeFloorTile(f, 10, 96, 1.2);
        BufferedImage hi = new BufferedImage(DRAW_RES, DRAW_RES, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        fallback.draw(g);
        g.dispose();
        return downscaleProgressive(hi, CELL, CELL);
    }

    private static BufferedImage loadPaperSprite(
            File dir, String[] names, double fillFrac, double bottomMargin) throws Exception {
        return loadPaperSprite(dir, names, fillFrac, bottomMargin, true);
    }

    private static BufferedImage loadPaperSprite(
            File dir, String[] names, double fillFrac, double bottomMargin, boolean seedInterior, double... holeSeeds) throws Exception {
        File f = findImage(dir, names);
        BufferedImage raw = ImageIO.read(f);
        BufferedImage clean = extractPaperSprite(raw, fillFrac, bottomMargin, seedInterior, holeSeeds);
        BufferedImage scaled = downscaleProgressive(clean, CELL, CELL);
        sharpenGraphite(scaled, 0.45f);
        applyFootAo(scaled);
        return addContrastRim(scaled, 0xBCC6D2, 30, true);
    }

    private static BufferedImage loadOrDrawSprite(
            File dir, String[] names, double fillFrac, double bottomMargin, boolean rim, boolean grounded, DrawFn fallback) throws Exception {
        File f = findImageOrNull(dir, names);
        BufferedImage scaled;
        if (f != null) {
            BufferedImage raw = ImageIO.read(f);
            BufferedImage clean = extractPaperSprite(raw, fillFrac, bottomMargin);
            scaled = downscaleProgressive(clean, CELL, CELL);
            sharpenGraphite(scaled, 0.45f);
        } else {
            BufferedImage hi = new BufferedImage(DRAW_RES, DRAW_RES, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = hi.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            fallback.draw(g);
            g.dispose();
            BufferedImage cropped = cropAndFrame(hi, fillFrac, bottomMargin);
            scaled = downscaleProgressive(cropped, CELL, CELL);
        }
        if (grounded) applyFootAo(scaled);
        return rim ? addContrastRim(scaled, 0xBCC6D2, 30, grounded) : scaled;
    }

    private static BufferedImage loadPoseOrClone(File dir, String[] names, BufferedImage base) throws Exception {
        File f = findImageOrNull(dir, names);
        if (f == null) return copyImage(base);
        BufferedImage raw = ImageIO.read(f);
        BufferedImage clean = extractPaperSprite(raw, 0.93, 0.06);
        BufferedImage scaled = downscaleProgressive(clean, CELL, CELL);
        sharpenGraphite(scaled, 0.45f);
        applyFootAo(scaled);
        return addContrastRim(scaled, 0xBCC6D2, 30, true);
    }

    private static BufferedImage loadFireOrFallback(
            File dir, String[] names, double fillFrac, double bottomMargin, int variant) throws Exception {
        File f = findImageOrNull(dir, names);
        if (f != null) {
            BufferedImage raw = ImageIO.read(f);
            BufferedImage clean = extractFireSprite(raw, fillFrac, bottomMargin);
            return downscaleProgressive(clean, CELL, CELL);
        }
        BufferedImage hi = new BufferedImage(DRAW_RES, DRAW_RES, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGasPuff(g);
        g.dispose();
        return downscaleProgressive(hi, CELL, CELL);
    }

    // ---- Wispy pencil fire extraction (keeps white flame tongues opaque, softly feathers wisps) ----

    private static BufferedImage extractFireSprite(BufferedImage src, double fillFrac, double bottomMargin) {
        int w = src.getWidth(), h = src.getHeight();
        int[] lum = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                lum[y * w + x] = clamp255(Math.round(0.299f * ((rgb >> 16) & 0xFF)
                        + 0.587f * ((rgb >> 8) & 0xFF)
                        + 0.114f * (rgb & 0xFF)));
            }
        }
        int bgLum = sampleCornerLum(lum, w, h);

        // Border-only BFS so bright interior flame tongues stay 100% solid
        boolean[] bg = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int seedThresh = bgLum - 18;
        for (int x = 0; x < w; x++) {
            trySeed(lum, bg, q, x, 0, w, seedThresh);
            trySeed(lum, bg, q, x, h - 1, w, seedThresh);
        }
        for (int y = 0; y < h; y++) {
            trySeed(lum, bg, q, 0, y, w, seedThresh);
            trySeed(lum, bg, q, w - 1, y, w, seedThresh);
        }
        int floodThresh = bgLum - 22;
        while (!q.isEmpty()) {
            int idx = q.poll();
            int x = idx % w, y = idx / w;
            if (x > 0) trySeed(lum, bg, q, x - 1, y, w, floodThresh);
            if (x + 1 < w) trySeed(lum, bg, q, x + 1, y, w, floodThresh);
            if (y > 0) trySeed(lum, bg, q, x, y - 1, w, floodThresh);
            if (y + 1 < h) trySeed(lum, bg, q, x, y + 1, w, floodThresh);
        }

        // Remove tiny stray specks far from main flame while preserving sparks
        removeTinyComponents(bg, w, h, 0.0012);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (bg[idx]) { out.setRGB(x, y, 0); continue; }
                int l = lum[idx];
                boolean edge = touchesBg(bg, x, y, w, h, 3);
                int alpha = 255;
                if (edge && l > 160) {
                    float t = (bgLum - 4 - l) / (float) Math.max(1, bgLum - 160);
                    alpha = clamp255(Math.round(Math.max(0f, Math.min(1f, t)) * 255f));
                }
                // Lift dark soot shadows gently so orange tint reads as glowing fire, not black ash
                double lifted = Math.pow(l / 255.0, 0.72);
                int gray = clamp255(Math.round(52f + (float) lifted * 203f));
                out.setRGB(x, y, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return cropAndFrame(out, fillFrac, bottomMargin);
    }

    // ---- Paper cutout, despeckle & ground-shadow cleanup ----

    private static BufferedImage extractPaperSprite(BufferedImage src, double fillFrac, double bottomMargin) {
        return extractPaperSprite(src, fillFrac, bottomMargin, true);
    }

    private static BufferedImage extractPaperSprite(BufferedImage src, double fillFrac, double bottomMargin, boolean seedInterior, double... holeSeeds) {
        int w = src.getWidth(), h = src.getHeight();
        int[] lum = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                lum[y * w + x] = clamp255(Math.round(0.299f * r + 0.587f * g + 0.114f * b));
            }
        }
        int bgLum = sampleCornerLum(lum, w, h);

        boolean[] bg = new boolean[w * h];
        boolean[] purePaper = new boolean[w * h];
        int seedThresh = bgLum - 14;
        for (int i = 0; i < lum.length; i++) purePaper[i] = lum[i] >= seedThresh;

        int floodThresh = bgLum - 32;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            trySeed(lum, bg, q, x, 0, w, bgLum - 26);
            trySeed(lum, bg, q, x, h - 1, w, bgLum - 26);
        }
        for (int y = 0; y < h; y++) {
            trySeed(lum, bg, q, 0, y, w, bgLum - 26);
            trySeed(lum, bg, q, w - 1, y, w, bgLum - 26);
        }
        for (int i = 0; i + 1 < holeSeeds.length; i += 2) {
            int hx = Math.max(0, Math.min(w - 1, (int) Math.round(holeSeeds[i] * (w - 1))));
            int hy = Math.max(0, Math.min(h - 1, (int) Math.round(holeSeeds[i + 1] * (h - 1))));
            trySeed(lum, bg, q, hx, hy, w, floodThresh);
        }
        if (seedInterior) {
            int[] pref = new int[(w + 1) * (h + 1)];
            for (int y = 0; y < h; y++) {
                int rowSum = 0;
                for (int x = 0; x < w; x++) {
                    rowSum += purePaper[y * w + x] ? 1 : 0;
                    pref[(y + 1) * (w + 1) + (x + 1)] = pref[y * (w + 1) + (x + 1)] + rowSum;
                }
            }
            int rad = 9, side = rad * 2 + 1, fullArea = side * side;
            for (int y = rad; y < h - rad; y += 3) {
                for (int x = rad; x < w - rad; x += 3) {
                    int sum = pref[(y + rad + 1) * (w + 1) + (x + rad + 1)]
                            - pref[(y - rad) * (w + 1) + (x + rad + 1)]
                            - pref[(y + rad + 1) * (w + 1) + (x - rad)]
                            + pref[(y - rad) * (w + 1) + (x - rad)];
                    if (sum == fullArea && !bg[y * w + x]) {
                        bg[y * w + x] = true;
                        q.add(y * w + x);
                    }
                }
            }
        }

        while (!q.isEmpty()) {
            int idx = q.poll();
            int x = idx % w, y = idx / w;
            if (x > 0) trySeed(lum, bg, q, x - 1, y, w, floodThresh);
            if (x + 1 < w) trySeed(lum, bg, q, x + 1, y, w, floodThresh);
            if (y > 0) trySeed(lum, bg, q, x, y - 1, w, floodThresh);
            if (y + 1 < h) trySeed(lum, bg, q, x, y + 1, w, floodThresh);
        }

        removeTinyComponents(bg, w, h, 0.004);

        // Strip faint horizontal ground-shadow strokes in bottom 12% of bounds
        int minY = h, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!bg[y * w + x]) {
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxY > minY) {
            int shadowTop = maxY - Math.round((maxY - minY) * 0.10f);
            for (int y = shadowTop; y <= maxY; y++) {
                for (int x = 0; x < w; x++) {
                    int idx = y * w + x;
                    if (bg[idx] || lum[idx] <= 95) continue;
                    boolean hasDarkAnchor = false;
                    int y0 = Math.max(0, y - 5), y1 = Math.min(h - 1, y + 5);
                    int x0 = Math.max(0, x - 3), x1 = Math.min(w - 1, x + 3);
                    for (int ny = y0; ny <= y1 && !hasDarkAnchor; ny++) {
                        for (int nx = x0; nx <= x1; nx++) {
                            if (!bg[ny * w + nx] && lum[ny * w + nx] <= 90) {
                                hasDarkAnchor = true;
                                break;
                            }
                        }
                    }
                    if (!hasDarkAnchor) {
                        bg[idx] = true;
                        if (!seedInterior) q.add(idx);
                    }
                }
            }
            if (!seedInterior && !q.isEmpty()) {
                while (!q.isEmpty()) {
                    int idx = q.poll();
                    int x = idx % w, y = idx / w;
                    if (x > 0) trySeed(lum, bg, q, x - 1, y, w, floodThresh);
                    if (x + 1 < w) trySeed(lum, bg, q, x + 1, y, w, floodThresh);
                    if (y > 0) trySeed(lum, bg, q, x, y - 1, w, floodThresh);
                    if (y + 1 < h) trySeed(lum, bg, q, x, y + 1, w, floodThresh);
                }
            }
            removeTinyComponents(bg, w, h, 0.004);
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (bg[idx]) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int l = lum[idx];
                int alpha = 255;
                if (l > 155 && touchesBg(bg, x, y, w, h, 2)) {
                    float t = (bgLum - 8 - l) / (float) Math.max(1, bgLum - 160);
                    alpha = clamp255(Math.round(Math.max(0f, Math.min(1f, t)) * 255f));
                }
                int gray = clamp255(Math.round(((l / 255f - 0.5f) * 1.10f + 0.5f) * 255f));
                out.setRGB(x, y, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return cropAndFrame(out, fillFrac, bottomMargin);
    }

    private static int sampleCornerLum(int[] lum, int w, int h) {
        long sum = 0, n = 0;
        int[][] corners = {{2, 2}, {w - 18, 2}, {2, h - 18}, {w - 18, h - 18}};
        for (int[] c : corners) {
            for (int dy = 0; dy < 16; dy++) {
                for (int dx = 0; dx < 16; dx++) {
                    sum += lum[(c[1] + dy) * w + (c[0] + dx)];
                    n++;
                }
            }
        }
        return (int) (sum / n);
    }

    private static void trySeed(int[] lum, boolean[] bg, ArrayDeque<Integer> q, int x, int y, int w, int thresh) {
        int idx = y * w + x;
        if (!bg[idx] && lum[idx] >= thresh) {
            bg[idx] = true;
            q.add(idx);
        }
    }

    private static boolean touchesBg(boolean[] bg, int x, int y, int w, int h, int radius) {
        int x0 = Math.max(0, x - radius), x1 = Math.min(w - 1, x + radius);
        int y0 = Math.max(0, y - radius), y1 = Math.min(h - 1, y + radius);
        for (int ny = y0; ny <= y1; ny++) {
            for (int nx = x0; nx <= x1; nx++) {
                if (bg[ny * w + nx]) return true;
            }
        }
        return false;
    }

    private static void removeTinyComponents(boolean[] bg, int w, int h, double minRatio) {
        int n = w * h;
        int[] compId = new int[n];
        int[] compSize = new int[n / 4 + 8];
        int nextId = 1, maxSize = 0;
        int[] stack = new int[n];
        for (int i = 0; i < n; i++) {
            if (bg[i] || compId[i] != 0) continue;
            int id = nextId++;
            int sp = 0, count = 0;
            stack[sp++] = i;
            compId[i] = id;
            while (sp > 0) {
                int p = stack[--sp];
                count++;
                int x = p % w, y = p / w;
                if (x > 0 && !bg[p - 1] && compId[p - 1] == 0) { compId[p - 1] = id; stack[sp++] = p - 1; }
                if (x + 1 < w && !bg[p + 1] && compId[p + 1] == 0) { compId[p + 1] = id; stack[sp++] = p + 1; }
                if (y > 0 && !bg[p - w] && compId[p - w] == 0) { compId[p - w] = id; stack[sp++] = p - w; }
                if (y + 1 < h && !bg[p + w] && compId[p + w] == 0) { compId[p + w] = id; stack[sp++] = p + w; }
            }
            compSize[id] = count;
            if (count > maxSize) maxSize = count;
        }
        int threshold = Math.max(16, (int) Math.round(maxSize * minRatio));
        for (int i = 0; i < n; i++) {
            int id = compId[i];
            if (id != 0 && compSize[id] < threshold) bg[i] = true;
        }
    }

    private static BufferedImage cropAndFrame(BufferedImage img, double fillFrac, double bottomMargin) {
        int w = img.getWidth(), h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 22) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX) return img;
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        int side = (int) Math.ceil(Math.max(cw, ch) / fillFrac);
        int ox = (side - cw) / 2;
        int oy = side - (int) Math.round(side * bottomMargin) - ch;
        if (oy < 0) oy = (side - ch) / 2;
        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.drawImage(img, ox, oy, ox + cw, oy + ch, minX, minY, maxX + 1, maxY + 1, null);
        g.dispose();
        return square;
    }

    // ---- Dark weathered charcoal-slate floor tone-mapping ----

    private static BufferedImage bakeFloorTile(File file, int minOut, int maxOut, double gamma) throws Exception {
        BufferedImage src = ImageIO.read(file);
        int w = src.getWidth(), h = src.getHeight();
        int[] lum = new int[w * h];
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int l = clamp255(Math.round(0.299f * ((rgb >> 16) & 0xFF)
                        + 0.587f * ((rgb >> 8) & 0xFF)
                        + 0.114f * (rgb & 0xFF)));
                lum[y * w + x] = l;
                hist[l]++;
            }
        }
        int total = w * h, lo = 0, hi = 255, acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc * 100 >= total * 2) { lo = i; break; }
        }
        acc = 0;
        for (int i = 255; i >= 0; i--) {
            acc += hist[i];
            if (acc * 100 >= total * 2) { hi = i; break; }
        }

        int span = Math.max(1, hi - lo);
        BufferedImage dark = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                double norm = Math.max(0.0, Math.min(1.0, (lum[idx] - lo) / (double) span));
                // Strong dark contrast curve + subtle weathered slate texture
                norm = Math.pow(norm, gamma);
                double grain = Math.sin(x * 0.025 + y * 0.018) * 2.5 + Math.cos((x - y) * 0.09) * 1.5;
                int v = clamp255((int) Math.round(minOut + norm * (maxOut - minOut) + grain));
                int r = clamp255(v - 2), g = v, b = clamp255(v + 3);
                dark.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return downscaleProgressive(dark, CELL, CELL);
    }

    /** Converts source tile to clean grayscale at 1:1 scale into CELL x CELL. */
    private static BufferedImage bakeSingleBwFloor(File file, boolean flipX) throws Exception {
        BufferedImage src = ImageIO.read(file);
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage bw = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int l = clamp255(Math.round(0.299f * ((rgb >> 16) & 0xFF)
                        + 0.587f * ((rgb >> 8) & 0xFF)
                        + 0.114f * (rgb & 0xFF)));
                bw.setRGB(x, y, (0xFF << 24) | (l << 16) | (l << 8) | l);
            }
        }
        BufferedImage scaled = downscaleProgressive(bw, CELL, CELL);
        sharpenGraphite(scaled, 0.45f);
        if (!flipX) return scaled;
        BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(scaled, CELL, 0, 0, CELL, 0, 0, CELL, CELL, null);
        g.dispose();
        return out;
    }

    /** Converts source tile to clean grayscale, packs a 2x2 grid (4x squeezed) into CELL x CELL. */
    private static BufferedImage bakeTiledBwFloor(File file, boolean flipX) throws Exception {
        BufferedImage src = ImageIO.read(file);
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage bw = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int l = clamp255(Math.round(0.299f * ((rgb >> 16) & 0xFF)
                        + 0.587f * ((rgb >> 8) & 0xFF)
                        + 0.114f * (rgb & 0xFF)));
                bw.setRGB(x, y, (0xFF << 24) | (l << 16) | (l << 8) | l);
            }
        }
        int half = CELL / 2;
        BufferedImage sub = downscaleProgressive(bw, half, half);
        sharpenGraphite(sub, 0.45f);
        BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int gy = 0; gy < 2; gy++) {
            for (int gx = 0; gx < 2; gx++) {
                int ox = gx * half, oy = gy * half;
                if (flipX) {
                    g.drawImage(sub, ox + half, oy, ox, oy + half, 0, 0, half, half, null);
                } else {
                    g.drawImage(sub, ox, oy, null);
                }
            }
        }
        g.dispose();
        return out;
    }

    /** Draws a crisp proportional black border around the combined floor tile. */
    private static BufferedImage addTileBorder(BufferedImage tile) {
        int w = tile.getWidth(), h = tile.getHeight();
        int border = Math.max(3, Math.round(w * 3f / 252f));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y)) < border) {
                    tile.setRGB(x, y, 0xFF080808);
                }
            }
        }
        return tile;
    }

    /** Subtle unsharp mask restoring fine pencil/graphite contrast after progressive downscaling. */
    private static void sharpenGraphite(BufferedImage img, float amount) {
        int w = img.getWidth(), h = img.getHeight();
        int[] src = img.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int c = src[y * w + x];
                int a = (c >>> 24) & 0xFF;
                if (a < 200) continue;
                int wSum = 0;
                float rSum = 0f, gSum = 0f, bSum = 0f;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int np = src[(y + dy) * w + (x + dx)];
                        int na = (np >>> 24) & 0xFF;
                        if (na == 0) continue;
                        int k = (dx == 0 && dy == 0) ? 4 : (dx == 0 || dy == 0) ? 2 : 1;
                        int wt = k * na;
                        wSum += wt;
                        rSum += wt * ((np >> 16) & 0xFF);
                        gSum += wt * ((np >> 8) & 0xFF);
                        bSum += wt * (np & 0xFF);
                    }
                }
                if (wSum == 0) continue;
                int r0 = (c >> 16) & 0xFF, g0 = (c >> 8) & 0xFF, b0 = c & 0xFF;
                int r = clamp255(Math.round(r0 + amount * (r0 - rSum / wSum)));
                int g = clamp255(Math.round(g0 + amount * (g0 - gSum / wSum)));
                int b = clamp255(Math.round(b0 + amount * (b0 - bSum / wSum)));
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    // ---- Grounding (foot AO + bottom-masked rim) & downscaling ----

    private static void applyFootAo(BufferedImage sprite) {
        int w = sprite.getWidth(), h = sprite.getHeight();
        int maxY = -1;
        for (int y = h - 1; y >= 0 && maxY < 0; y--) {
            for (int x = 0; x < w; x++) {
                if (((sprite.getRGB(x, y) >>> 24) & 0xFF) > 30) { maxY = y; break; }
            }
        }
        if (maxY < 20) return;
        int aoStart = Math.max(0, maxY - Math.round(h * 0.12f));
        for (int y = aoStart; y <= maxY; y++) {
            float t = (y - aoStart) / (float) Math.max(1, maxY - aoStart);
            float mult = 1f - 0.22f * t * t;
            for (int x = 0; x < w; x++) {
                int p = sprite.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                if (a == 0) continue;
                int r = clamp255(Math.round(((p >> 16) & 0xFF) * mult));
                int g = clamp255(Math.round(((p >> 8) & 0xFF) * mult));
                int b = clamp255(Math.round((p & 0xFF) * mult));
                sprite.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static BufferedImage downscaleProgressive(BufferedImage src, int targetW, int targetH) {
        BufferedImage cur = toPre(src);
        int w = cur.getWidth(), h = cur.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH && (w > targetW || h > targetH)) {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            cur = scaleStepPre(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (w != targetW || h != targetH) {
            cur = scaleStepPre(cur, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(cur, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toPre(BufferedImage src) {
        BufferedImage pre = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = pre.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return pre;
    }

    private static BufferedImage scaleStepPre(BufferedImage src, int w, int h, Object hint) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage addContrastRim(BufferedImage sprite, int rimRgb, int maxAlpha, boolean maskBottom) {
        int w = sprite.getWidth(), h = sprite.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] alpha = new int[w * h];
        for (int i = 0; i < alpha.length; i++) alpha[i] = (sprite.getRGB(i % w, i / w) >>> 24) & 0xFF;
        int rr = (rimRgb >> 16) & 0xFF, rg = (rimRgb >> 8) & 0xFF, rb = rimRgb & 0xFF;

        int rad = Math.max(2, Math.round(w / 170f));
        int maxD2 = rad * rad, innerD2 = Math.max(1, (rad - 1) * (rad - 1));
        for (int y = 0; y < h; y++) {
            float rimWeight = 1f;
            if (maskBottom && y >= h * 0.78f) {
                float t = (y - h * 0.78f) / (h * 0.12f);
                rimWeight = Math.max(0f, 1f - t);
            }
            for (int x = 0; x < w; x++) {
                int argb = sprite.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a >= 210 || rimWeight <= 0.01f) { out.setRGB(x, y, argb); continue; }
                int near = 0;
                for (int dy = -rad; dy <= rad; dy++) {
                    for (int dx = -rad; dx <= rad; dx++) {
                        int nx = x + dx, ny = y + dy, d2 = dx * dx + dy * dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && d2 <= maxD2) {
                            int wVal = d2 <= innerD2 ? alpha[ny * w + nx] : (alpha[ny * w + nx] * 2) / 3;
                            if (wVal > near) near = wVal;
                        }
                    }
                }
                if (near > 45) {
                    int haloA = Math.round(Math.min(maxAlpha, (near * maxAlpha) / 255f) * rimWeight);
                    float sa = a / 255f, ha = (haloA / 255f) * (1f - sa), oa = sa + ha;
                    int r = clamp255(Math.round((((argb >> 16) & 0xFF) * sa + rr * ha) / Math.max(0.001f, oa)));
                    int g = clamp255(Math.round((((argb >> 8) & 0xFF) * sa + rg * ha) / Math.max(0.001f, oa)));
                    int b = clamp255(Math.round(((argb & 0xFF) * sa + rb * ha) / Math.max(0.001f, oa)));
                    out.setRGB(x, y, (clamp255(Math.round(oa * 255f)) << 24) | (r << 16) | (g << 8) | b);
                } else {
                    out.setRGB(x, y, argb);
                }
            }
        }
        return out;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    // ---- Shaded & hatched monochrome placeholders for unsupplied slots ----

    @FunctionalInterface
    private interface DrawFn {
        void draw(Graphics2D g);
    }

    private static final class Rng {
        int s;
        Rng(int seed) { this.s = seed; }
        double next() {
            s ^= s << 13; s ^= s >>> 17; s ^= s << 5;
            return (s & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
        }
    }

    private static void inkPoly(Graphics2D g, int[][] pts, Color fill, Color stroke, float lw) {
        int n = pts.length;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) { xs[i] = pts[i][0]; ys[i] = pts[i][1]; }
        if (fill != null) { g.setColor(fill); g.fillPolygon(xs, ys, n); }
        if (stroke != null) {
            g.setColor(stroke);
            g.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, n);
        }
    }

    private static void shadedPoly(Graphics2D g, int[][] pts, int lightV, int darkV, Color stroke, float lw, Rng rng) {
        int minX = DRAW_RES, minY = DRAW_RES, maxX = 0, maxY = 0;
        int[] xs = new int[pts.length], ys = new int[pts.length];
        for (int i = 0; i < pts.length; i++) {
            xs[i] = pts[i][0]; ys[i] = pts[i][1];
            minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
        }
        g.setPaint(new GradientPaint(minX, minY, new Color(lightV, lightV, lightV + 2),
                maxX, maxY, new Color(darkV, darkV, darkV + 2)));
        g.fillPolygon(xs, ys, pts.length);
        java.awt.Shape oldClip = g.getClip();
        g.setClip(new java.awt.Polygon(xs, ys, pts.length));
        hatchRect(g, minX + (maxX - minX) / 3, minY, maxX - minX, maxY - minY, 12,
                new Color(12, 14, 18, 95), 2.3f, rng);
        g.setClip(oldClip);
        if (stroke != null) {
            g.setColor(stroke);
            g.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, pts.length);
        }
    }

    private static void hatchRect(Graphics2D g, int x, int y, int w, int h, int step, Color col, float lw, Rng rng) {
        g.setColor(col);
        g.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int d = -h; d < w + h; d += step) {
            if (rng.next() < 0.16) continue;
            int x1 = Math.max(x, x + d), y1 = Math.min(y + h, y + Math.max(0, -d));
            int x2 = Math.min(x + w, x + d + h), y2 = Math.max(y, y + h - Math.max(0, d + h - w));
            if (x2 > x1 && y1 > y2) g.drawLine(x1, y1, x2, y2);
        }
    }

    private static void drawWallTop(Graphics2D g) {
        Rng rng = new Rng(101);
        g.setColor(new Color(10, 12, 16));
        g.fillRect(0, 0, DRAW_RES, DRAW_RES);
        int[][] blocks = {{8,8,244,244},{260,8,244,244},{8,260,244,244},{260,260,244,244}};
        for (int[] b : blocks) {
            g.setPaint(new GradientPaint(b[0], b[1], new Color(46, 50, 58), b[0] + b[2], b[1] + b[3], new Color(24, 28, 34)));
            g.fillRect(b[0], b[1], b[2], b[3]);
            hatchRect(g, b[0] + 10, b[1] + 10, b[2] - 20, b[3] - 20, 14, new Color(6, 8, 12, 125), 2.3f, rng);
            g.setColor(new Color(6, 8, 10));
            g.setStroke(new BasicStroke(7f));
            g.drawRect(b[0], b[1], b[2], b[3]);
            g.setColor(new Color(98, 106, 118, 115));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(b[0] + 8, b[1] + 8, b[0] + b[2] - 8, b[1] + 8);
        }
    }

    private static void drawWallFront(Graphics2D g) {
        Rng rng = new Rng(202);
        g.setColor(new Color(9, 11, 14));
        g.fillRect(0, 0, DRAW_RES, DRAW_RES);
        shadedPoly(g, new int[][]{{6,6},{252,6},{252,172},{6,172}}, 64, 40, new Color(6, 8, 10), 7f, rng);
        shadedPoly(g, new int[][]{{260,6},{506,6},{506,172},{260,172}}, 58, 36, new Color(6, 8, 10), 7f, rng);
        inkPoly(g, new int[][]{{4,164},{508,164},{508,194},{4,194}}, new Color(110, 118, 130), new Color(6, 8, 10), 6f);
        int[][] stones = {{6,198,164,150},{174,198,214,150},{392,198,114,150},{6,352,242,154},{252,352,254,154}};
        for (int[] s : stones) {
            g.setPaint(new GradientPaint(s[0], s[1], new Color(46, 50, 58), s[0], s[1] + s[3], new Color(22, 25, 31)));
            g.fillRect(s[0], s[1], s[2], s[3]);
            g.setColor(new Color(5, 7, 9, 185));
            g.fillRect(s[0], s[1], s[2], 24);
            hatchRect(g, s[0] + 4, s[1] + 20, s[2] - 8, s[3] - 24, 12, new Color(5, 7, 9, 140), 2.4f, rng);
            g.setColor(new Color(6, 8, 10));
            g.setStroke(new BasicStroke(7f));
            g.drawRect(s[0], s[1], s[2], s[3]);
        }
    }

    private static void drawWaterDecal(Graphics2D g) {
        inkPoly(g, new int[][]{{78,190},{170,118},{342,126},{432,210},{410,342},{290,404},{146,386},{70,290}},
                new Color(195, 202, 210, 175), new Color(26, 30, 36, 210), 7.5f);
        g.setColor(new Color(250, 252, 255, 205));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(140, 175, 220, 130, 25, 110);
        g.drawArc(190, 255, 160, 80, 195, 100);
    }

    private static void drawAcidDecal(Graphics2D g) {
        inkPoly(g, new int[][]{{66,222},{138,128},{278,108},{414,168},{438,298},{342,398},{172,408},{86,328}},
                new Color(200, 204, 200, 195), new Color(22, 24, 22, 225), 8f);
        g.setColor(new Color(255, 255, 255, 225));
        g.fillOval(178, 196, 48, 48);
        g.fillOval(294, 248, 36, 36);
        g.fillOval(222, 294, 28, 28);
        g.setColor(new Color(22, 24, 22));
        g.setStroke(new BasicStroke(5f));
        g.drawOval(178, 196, 48, 48);
        g.drawOval(294, 248, 36, 36);
    }

    private static void drawOilDecal(Graphics2D g) {
        inkPoly(g, new int[][]{{60,248},{128,136},{292,122},{428,194},{434,328},{316,406},{144,394},{74,318}},
                new Color(62, 62, 66, 215), new Color(14, 14, 16, 240), 8.5f);
        g.setColor(new Color(210, 212, 218, 170));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(132, 168, 230, 140, 35, 95);
    }

    private static void drawIceDecal(Graphics2D g) {
        inkPoly(g, new int[][]{{54,230},{144,74},{334,68},{448,186},{430,368},{276,442},{94,394}},
                new Color(212, 218, 226, 185), new Color(24, 28, 34, 215), 7.5f);
        g.setColor(new Color(255, 255, 255, 225));
        g.setStroke(new BasicStroke(8f));
        g.drawLine(134, 340, 326, 128);
        g.drawLine(210, 376, 384, 186);
    }

    private static void drawTarDecal(Graphics2D g) {
        drawOilDecal(g);
        g.setColor(new Color(235, 238, 240, 205));
        g.fillOval(210, 210, 42, 42);
        g.fillOval(292, 272, 30, 30);
        g.setColor(new Color(14, 16, 18));
        g.setStroke(new BasicStroke(5f));
        g.drawOval(210, 210, 42, 42);
    }

    private static void drawGasPuff(Graphics2D g) {
        int[][] ovals = {{110,164,170,150},{206,120,180,165},{264,186,166,148},{142,226,184,150}};
        for (int[] o : ovals) {
            g.setColor(new Color(224, 228, 232, 135));
            g.fill(new Ellipse2D.Double(o[0], o[1], o[2], o[3]));
            g.setColor(new Color(44, 48, 54, 175));
            g.setStroke(new BasicStroke(6f));
            g.draw(new Ellipse2D.Double(o[0], o[1], o[2], o[3]));
        }
    }

    private static void drawRatPlaceholder(Graphics2D g) {
        Rng rng = new Rng(455);
        shadedPoly(g, new int[][]{{140,264},{236,198},{358,214},{404,312},{346,386},{172,386},{102,324}},
                156, 84, new Color(14, 16, 18), 8.5f, rng);
        g.setColor(new Color(14, 16, 18));
        g.setStroke(new BasicStroke(8f));
        g.drawArc(364, 240, 108, 140, -60, 150);
        g.setColor(new Color(240, 242, 246));
        g.fillOval(146, 274, 22, 18);
        shadedPoly(g, new int[][]{{190,214},{172,156},{226,196}}, 168, 102, new Color(14,16,18), 7f, rng);
    }

    private static void drawBarrelPlaceholder(Graphics2D g) {
        Rng rng = new Rng(505);
        shadedPoly(g, new int[][]{{148,136},{364,136},{392,286},{362,438},{150,438},{120,286}},
                164, 82, new Color(14, 16, 18), 8.5f, rng);
        g.setColor(new Color(14, 16, 18, 175));
        g.setStroke(new BasicStroke(4.5f));
        for (int sx : new int[]{190, 232, 276, 318}) g.drawLine(sx, 166, sx + (sx - 256) / 6, 434);
        g.setPaint(new GradientPaint(148, 102, new Color(188, 192, 198), 364, 174, new Color(118, 122, 128)));
        g.fillOval(148, 102, 216, 72);
        g.setColor(new Color(14, 16, 18));
        g.setStroke(new BasicStroke(8f));
        g.drawOval(148, 102, 216, 72);
        shadedPoly(g, new int[][]{{126,204},{386,204},{386,232},{126,232}}, 98, 56, new Color(14,16,18), 7f, rng);
        shadedPoly(g, new int[][]{{126,348},{386,348},{386,376},{126,376}}, 98, 56, new Color(14,16,18), 7f, rng);
    }

    private static void drawCratePlaceholder(Graphics2D g) {
        Rng rng = new Rng(606);
        shadedPoly(g, new int[][]{{78,108},{434,108},{434,444},{78,444}}, 168, 92, new Color(14,16,18), 9f, rng);
        g.setColor(new Color(14, 16, 18));
        g.setStroke(new BasicStroke(8f));
        g.drawRect(114, 144, 284, 264);
        g.drawLine(114, 144, 398, 408);
        g.drawLine(398, 144, 114, 408);
    }

    private static void drawPillarPlaceholder(Graphics2D g) {
        Rng rng = new Rng(707);
        shadedPoly(g, new int[][]{{104,78},{408,78},{408,144},{104,144}}, 178, 104, new Color(14,16,18), 8.5f, rng);
        shadedPoly(g, new int[][]{{146,144},{366,144},{366,400},{146,400}}, 154, 78, new Color(14,16,18), 8.5f, rng);
        shadedPoly(g, new int[][]{{94,398},{418,398},{418,464},{94,464}}, 166, 94, new Color(14,16,18), 8.5f, rng);
    }

    private static void drawDoorPlaceholder(Graphics2D g) {
        Rng rng = new Rng(808);
        shadedPoly(g, new int[][]{{94,448},{94,164},{176,68},{336,68},{418,164},{418,448}},
                148, 76, new Color(14, 16, 18), 9f, rng);
        g.setColor(new Color(14, 16, 18));
        g.setStroke(new BasicStroke(6f));
        for (int px = 166; px <= 346; px += 60) g.drawLine(px, 84, px, 444);
        g.setColor(new Color(220, 224, 230));
        g.drawOval(322, 260, 42, 42);
    }

    private static void drawLootBlade(Graphics2D g) {
        Rng rng = new Rng(909);
        shadedPoly(g, new int[][]{{148,326},{366,92},{406,126},{186,360}}, 232, 152, new Color(14,16,18), 8f, rng);
        shadedPoly(g, new int[][]{{120,294},{214,386},{194,404},{102,312}}, 168, 96, new Color(14,16,18), 7f, rng);
        shadedPoly(g, new int[][]{{134,344},{166,376},{96,440},{68,412}}, 96, 52, new Color(14,16,18), 7f, rng);
    }

    private static void drawLootFlask(Graphics2D g, int kind) {
        inkPoly(g, new int[][]{{216,74},{296,74},{288,128},{224,128}}, new Color(132,136,140), new Color(14,16,18), 7.5f);
        inkPoly(g, new int[][]{{218,128},{294,128},{294,188},{384,264},{384,388},{316,442},{196,442},{128,388},{128,264},{218,188}},
                new Color(196, 202, 208), new Color(14, 16, 18), 8.5f);
        int fill = kind == 1 ? 150 : kind == 2 ? 185 : 225;
        g.setColor(new Color(fill, fill, fill));
        g.fillRoundRect(148, 286, 216, 134, 48, 48);
        g.setColor(new Color(255, 255, 255));
        g.setStroke(new BasicStroke(7f));
        g.drawArc(166, 240, 120, 140, 125, 70);
    }

    private static void drawLootStone(Graphics2D g) {
        Rng rng = new Rng(955);
        shadedPoly(g, new int[][]{{116,258},{188,158},{334,154},{404,244},{368,364},{208,384},{126,336}},
                182, 92, new Color(14, 16, 18), 8.5f, rng);
    }

    private static void drawBloodSplat(Graphics2D g, int variant) {
        Rng rng = new Rng(variant == 0 ? 1103 : 2207);
        int cx = 256, cy = 256;
        // Smooth spline-rounded organic blood pool (zero straight polygon edges)
        drawSmoothBlob(g, cx, cy, variant == 0 ? 118 : 134, variant == 0 ? 96 : 86, 14,
                variant == 1 ? -0.35 : 0.15, new Color(212, 212, 216, 224), rng);
        int lobes = variant == 0 ? 6 : 5;
        for (int i = 0; i < lobes; i++) {
            double ang = (Math.PI * 2.0 * i) / lobes + rng.next() * 0.55;
            if (variant == 1) ang = ang * 0.55 - 0.35;
            double dist = 48 + rng.next() * 62;
            double bx = cx + Math.cos(ang) * dist;
            double by = cy + Math.sin(ang) * dist * 0.85;
            drawSmoothBlob(g, bx, by, 34 + rng.next() * 32, 26 + rng.next() * 26, 10,
                    ang, new Color(202, 202, 206, 218), rng);
        }
        // Richer darker wet core inside the main spill
        drawSmoothBlob(g, cx - 8, cy + 4, variant == 0 ? 74 : 84, variant == 0 ? 58 : 52, 12,
                variant == 1 ? -0.30 : 0.1, new Color(158, 158, 164, 242), rng);
        // Smooth rounded droplets & curved radial splatter drops
        int drops = variant == 0 ? 18 : 22;
        for (int i = 0; i < drops; i++) {
            double ang = variant == 0 ? rng.next() * Math.PI * 2.0 : (rng.next() - 0.5) * 2.2;
            double dist = 102 + rng.next() * 116;
            double rx = 10 + rng.next() * 18;
            double ry = 5 + rng.next() * 8;
            double tx = cx + Math.cos(ang) * dist;
            double ty = cy + Math.sin(ang) * dist * 0.88;
            int v = 180 + (int) Math.round(rng.next() * 58);
            g.setColor(new Color(v, v, v, 215));
            Graphics2D dg = (Graphics2D) g.create();
            dg.translate(tx, ty);
            dg.rotate(ang);
            if (i % 3 == 0) {
                Path2D.Double drop = new Path2D.Double();
                drop.moveTo(-rx, 0);
                drop.quadTo(-rx * 0.15, -ry * 1.15, rx * 0.75, -ry * 0.55);
                drop.quadTo(rx * 1.15, 0, rx * 0.75, ry * 0.55);
                drop.quadTo(-rx * 0.15, ry * 1.15, -rx, 0);
                drop.closePath();
                dg.fill(drop);
            } else {
                dg.fill(new Ellipse2D.Double(-rx * 0.65, -ry * 0.75, rx * 1.3, ry * 1.5));
            }
            dg.dispose();
        }
    }

    private static void drawSmoothBlob(
            Graphics2D g, double cx, double cy, double rx, double ry, int pts, double rot, Color col, Rng rng) {
        double p1 = rng.next() * 6.283, p2 = rng.next() * 6.283, p3 = rng.next() * 6.283;
        double[] vx = new double[pts], vy = new double[pts];
        double cr = Math.cos(rot), sr = Math.sin(rot);
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2.0 * i) / pts;
            double mod = 1.0 + 0.14 * Math.sin(2.0 * a + p1)
                    + 0.11 * Math.sin(3.0 * a + p2)
                    + 0.08 * Math.sin(5.0 * a + p3);
            double lx = Math.cos(a) * rx * mod;
            double ly = Math.sin(a) * ry * mod;
            vx[i] = cx + lx * cr - ly * sr;
            vy[i] = cy + lx * sr + ly * cr;
        }
        Path2D.Double path = new Path2D.Double();
        double sx = 0.5 * (vx[pts - 1] + vx[0]), sy = 0.5 * (vy[pts - 1] + vy[0]);
        path.moveTo(sx, sy);
        for (int i = 0; i < pts; i++) {
            int next = (i + 1) % pts;
            path.quadTo(vx[i], vy[i], 0.5 * (vx[i] + vx[next]), 0.5 * (vy[i] + vy[next]));
        }
        path.closePath();
        g.setColor(col);
        g.fill(path);
    }

    // ---- Visual verification strip ----

    private static void writePreview(BufferedImage[] cells, File out) throws Exception {
        int cols = 4, rows = 4;
        BufferedImage img = new BufferedImage(cols * CELL, rows * CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(10, 12, 16));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + c) % 2 == 0 ? FLOOR_0 : FLOOR_1;
                g.drawImage(cells[idx], c * CELL, r * CELL, null);
            }
        }
        // Tinted blood splats on sample floor tiles before entities
        int s0 = Math.round(0.54f * CELL), s1 = Math.round(0.67f * CELL);
        g.drawImage(tintSample(cells[BLOOD_SPLAT_0], 195, 28, 34),
                CELL * 3 + Math.round(0.09f * CELL), CELL * 3 + Math.round(0.19f * CELL), s0, s0, null);
        g.drawImage(tintSample(cells[BLOOD_SPLAT_1], 180, 24, 30),
                CELL * 3 + Math.round(0.24f * CELL), CELL * 3 + Math.round(0.09f * CELL), s1, s1, null);

        // Row 0: Thrall + torch, Thrall_attack + torch, Orc, Orc_attack
        drawGroundShadow(g, 0, 0);
        g.drawImage(cells[THRALL], 0, 0, null);
        drawTorchTest(g, cells[TORCH_FIRE], 0, 0, 0.68f, 0.18f);

        drawGroundShadow(g, CELL, 0);
        g.drawImage(cells[THRALL_ATTACK], CELL, 0, null);
        drawTorchTest(g, cells[TORCH_FIRE], CELL, 0, 0.178f, 0.40f);

        drawGroundShadow(g, CELL * 2, 0);
        g.drawImage(cells[ORC], CELL * 2, 0, null);

        drawGroundShadow(g, CELL * 3, 0);
        g.drawImage(cells[ORC_ATTACK], CELL * 3, 0, null);

        // Row 1: Goblin Archer (idle, aim, after shot), Goblin Assassin idle
        int[] row1 = {GOBLIN, GOBLIN_AIM, GOBLIN_AFTER_SHOT, GOBLIN_ASSASSIN};
        for (int c = 0; c < row1.length; c++) {
            drawGroundShadow(g, c * CELL, CELL);
            g.drawImage(cells[row1[c]], c * CELL, CELL, null);
        }

        // Row 2: Goblin Assassin attack, Ogre idle, Ogre attack, Ogre attack 2
        int[] row2 = {GOBLIN_ASSASSIN_ATTACK, OGRE, OGRE_ATTACK, OGRE_ATTACK_2};
        for (int c = 0; c < row2.length; c++) {
            drawGroundShadow(g, c * CELL, CELL * 2);
            g.drawImage(cells[row2[c]], c * CELL, CELL * 2, null);
        }

        // Row 3: Brazier (scaled 0.80x) + fire, Floor0, Floor1, Blood splats
        int bw = Math.round(CELL * 0.80f), bx = (CELL - bw) / 2, by = CELL * 3 + (CELL - bw);
        g.drawImage(cells[BRAZIER], bx, by, bw, bw, null);
        int fw = Math.round(0.68f * CELL * 0.80f), fh = Math.round(0.50f * CELL * 0.80f);
        g.drawImage(tintSample(cells[FIRE_0], 245, 144, 68),
                (CELL - fw) / 2, CELL * 3 + CELL - Math.round(0.48f * CELL * 0.80f) - fh, fw, fh, null);

        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote preview strip: " + out.getPath());
    }

    private static void drawGroundShadow(Graphics2D g, int ox, int oy) {
        g.setColor(new Color(0, 0, 0, 115));
        g.fillOval(ox + Math.round(0.10f * CELL), oy + CELL - Math.round(0.15f * CELL),
                Math.round(0.80f * CELL), Math.round(0.12f * CELL));
        g.setColor(new Color(0, 0, 0, 210));
        g.fillOval(ox + Math.round(0.175f * CELL), oy + CELL - Math.round(0.11f * CELL),
                Math.round(0.65f * CELL), Math.round(0.07f * CELL));
    }

    private static void drawTorchTest(Graphics2D g, BufferedImage flame, int ox, int oy, float txNorm, float tyNorm) {
        int tx = ox + Math.round(txNorm * CELL);
        int ty = oy + Math.round(tyNorm * CELL);
        int fw = Math.round(0.22f * CELL), fh = Math.round(0.32f * CELL);
        g.drawImage(tintSample(flame, 245, 148, 72), tx - fw / 2, ty - Math.round(fh * 0.78f), fw, fh, null);
    }

    private static void drawBrazierFireTest(Graphics2D g, BufferedImage flame, int ox, int oy) {
        int fw = Math.round(0.68f * CELL), fh = Math.round(0.50f * CELL);
        int fx = ox + Math.round((CELL - fw) * 0.5f), fy = oy + Math.round(0.02f * CELL);
        g.drawImage(tintSample(flame, 245, 144, 68), fx, fy, fw, fh, null);
    }

    private static BufferedImage tintSample(BufferedImage src, int tr, int tg, int tb) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                int r = clamp255((((p >> 16) & 0xFF) * tr) / 255);
                int gr = clamp255((((p >> 8) & 0xFF) * tg) / 255);
                int b = clamp255(((p & 0xFF) * tb) / 255);
                out.setRGB(x, y, (a << 24) | (r << 16) | (gr << 8) | b);
            }
        }
        return out;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
