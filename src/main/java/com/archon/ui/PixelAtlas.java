package com.archon.ui;

import com.archon.model.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * High-detail 508x508 (512 stride with 2px gutter padding) monochrome ink sprite atlas loaded from
 * resources/sprites/atlas.png with mipmapped linear filtering, plus procedural glow and white textures.
 */
public final class PixelAtlas {
    public static final int CELL = 508, PAD = 2, STRIDE = CELL + PAD * 2;
    public static final int COLS = 8, ROWS = 8, ATLAS_W = COLS * STRIDE, ATLAS_H = ROWS * STRIDE;

    public static final int POSE_IDLE = 0, POSE_MOVE = 1, POSE_ATTACK = 2,
            POSE_CAST = 3, POSE_GUARD = 4, POSE_HURT = 5, POSE_AFTER_ATTACK = 6, POSE_ATTACK_ALT = 7;

    public static final int
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
            ORC_ATTACK = 39, OGRE_ATTACK_2 = 40,
            THRALL_MOVE = THRALL,
            COUNT = 41;

    public static final int GLOW_SIZE = 64;

    private static final float[] EMPTY_ANCHORS = new float[0];
    private static final float[]
            EYES_THRALL = {0.522f, 0.132f, 0.546f, 0.135f},
            EYES_THRALL_ATTACK = {0.518f, 0.392f, 0.538f, 0.394f},
            EYES_ORC = {0.402f, 0.178f, 0.434f, 0.176f},
            EYES_ORC_ATTACK = {0.429f, 0.244f, 0.454f, 0.246f},
            EYES_GOBLIN = {0.381f, 0.341f},
            EYES_GOBLIN_AIM = {0.421f, 0.262f},
            EYES_GOBLIN_AFTER_SHOT = {0.398f, 0.262f},
            EYES_GOBLIN_ASSASSIN = {0.302f, 0.302f, 0.337f, 0.298f},
            EYES_GOBLIN_ASSASSIN_ATTACK = {0.353f, 0.298f, 0.389f, 0.298f},
            EYES_OGRE = {0.365f, 0.194f, 0.409f, 0.194f},
            EYES_OGRE_ATTACK = {0.397f, 0.226f, 0.437f, 0.224f},
            EYES_OGRE_ATTACK_2 = {0.298f, 0.143f, 0.325f, 0.143f},
            EYES_RAT = {0.278f, 0.732f};
    private static final float[]
            WEAP_ORC = {0.088f, 0.340f, 0.365f, 0.482f, 0.14f},
            WEAP_ORC_ATTACK = {0.055f, 0.478f, 0.365f, 0.505f, 0.14f},
            WEAP_ASSASSIN = {0.068f, 0.725f, 0.202f, 0.555f, 0.11f, 0.742f, 0.588f, 0.922f, 0.625f, 0.09f},
            WEAP_ASSASSIN_ATK = {0.036f, 0.407f, 0.195f, 0.407f, 0.075f, 0.024f, 0.496f, 0.185f, 0.496f, 0.075f};

    public final Texture texture, whiteTex, glowTex;
    public final TextureRegion[] regions = new TextureRegion[COUNT];
    public final TextureRegion white, glow;

    public PixelAtlas() {
        texture = new Texture(Gdx.files.internal("sprites/atlas.png"), true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        for (int i = 0; i < COUNT; i++) {
            int cx = (i % COLS) * STRIDE + PAD;
            int cy = (i / COLS) * STRIDE + PAD;
            regions[i] = new TextureRegion(texture, cx, cy, CELL, CELL);
        }

        Pixmap wp = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        wp.setColor(1f, 1f, 1f, 1f);
        wp.fill();
        whiteTex = new Texture(wp);
        wp.dispose();
        white = new TextureRegion(whiteTex);

        int[] glowPixels = bakeGlowPixels();
        Pixmap gp = new Pixmap(GLOW_SIZE, GLOW_SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                gp.drawPixel(x, y, glowPixels[y * GLOW_SIZE + x]);
            }
        }
        glowTex = new Texture(gp);
        glowTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        gp.dispose();
        glow = new TextureRegion(glowTex);
    }

    public void dispose() {
        texture.dispose();
        whiteTex.dispose();
        glowTex.dispose();
    }

    /** Bakes a smooth 64x64 radial gradient for Linear-filtered shadows and additive light halos. */
    public static int[] bakeGlowPixels() {
        int[] out = new int[GLOW_SIZE * GLOW_SIZE];
        float mid = (GLOW_SIZE - 1) * 0.5f;
        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                float dx = (x - mid) / mid, dy = (y - mid) / mid;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 1f) continue;
                float f = 1f - d;
                int a = Math.min(255, Math.round(255f * f * f));
                out[y * GLOW_SIZE + x] = 0xFFFFFF00 | a;
            }
        }
        return out;
    }

    /** Headless RGBA8888 loader for unit tests without an OpenGL context. */
    public static int[] loadRgbaPixels() {
        BufferedImage img;
        try (InputStream in = PixelAtlas.class.getResourceAsStream("/sprites/atlas.png")) {
            if (in == null) throw new IllegalStateException("Missing resource /sprites/atlas.png");
            img = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (img.getWidth() != ATLAS_W || img.getHeight() != ATLAS_H) {
            throw new IllegalStateException("Unexpected atlas dimensions: " + img.getWidth() + "x" + img.getHeight());
        }
        int[] rgba = img.getRGB(0, 0, ATLAS_W, ATLAS_H, null, 0, ATLAS_W);
        for (int i = 0; i < rgba.length; i++) {
            int p = rgba[i];
            rgba[i] = (p << 8) | ((p >>> 24) & 0xFF);
        }
        return rgba;
    }

    public static int spriteForEntity(Scene s, int i) {
        if (i == 0) return THRALL;
        if (s.kind[i] == Entity.Kind.DOOR) return DOOR;
        if (s.kind[i] == Entity.Kind.PROP) {
            return switch (s.glyph[i]) {
                case 'i' -> BRAZIER;
                case 'B' -> BARREL;
                case 'c' -> CRATE;
                case 'S' -> PILLAR;
                default -> CRATE;
            };
        }
        if (s.glyph[i] == 'a' || containsIgnoreCase(s.name[i], "assassin")) return GOBLIN_ASSASSIN;
        if (containsIgnoreCase(s.name[i], "ogre") || (s.glyph[i] == 'G' && s.maxHp[i] >= 35)) return OGRE;
        if (containsIgnoreCase(s.name[i], "archer") || s.glyph[i] == 'g' || s.glyph[i] == 'G') return GOBLIN;
        return switch (s.glyph[i]) {
            case 'O' -> ORC;
            case 'r' -> RAT;
            default -> ORC;
        };
    }

    private static boolean containsIgnoreCase(String s, String needle) {
        if (s == null) return false;
        int nLen = needle.length(), limit = s.length() - nLen;
        for (int i = 0; i <= limit; i++) {
            if (s.regionMatches(true, i, needle, 0, nLen)) return true;
        }
        return false;
    }

    public static int entitySprite(int baseSprite, int pose) {
        return switch (baseSprite) {
            case ORC -> pose == POSE_ATTACK ? ORC_ATTACK : ORC;
            case GOBLIN -> switch (pose) {
                case POSE_ATTACK -> GOBLIN_AIM;
                case POSE_AFTER_ATTACK -> GOBLIN_AFTER_SHOT;
                default -> GOBLIN;
            };
            case GOBLIN_ASSASSIN -> pose == POSE_ATTACK ? GOBLIN_ASSASSIN_ATTACK : GOBLIN_ASSASSIN;
            case OGRE -> switch (pose) {
                case POSE_ATTACK -> OGRE_ATTACK;
                case POSE_ATTACK_ALT -> OGRE_ATTACK_2;
                default -> OGRE;
            };
            default -> baseSprite;
        };
    }

    public static int thrallSprite(int pose) {
        return switch (pose) {
            case POSE_MOVE -> THRALL_MOVE;
            case POSE_ATTACK -> THRALL_ATTACK;
            case POSE_CAST -> THRALL_CAST;
            case POSE_GUARD -> THRALL_GUARD;
            case POSE_HURT -> THRALL_HURT;
            default -> THRALL;
        };
    }

    /** Left-facing enemy sprites return -1; Thrall, OGRE_ATTACK_2, and props face right (+1). */
    public static int naturalFacing(int spriteId) {
        return switch (spriteId) {
            case ORC, ORC_ATTACK, GOBLIN, GOBLIN_AIM, GOBLIN_AFTER_SHOT,
                 GOBLIN_ASSASSIN, GOBLIN_ASSASSIN_ATTACK, OGRE, OGRE_ATTACK -> -1;
            default -> 1;
        };
    }

    /** Normalized (nx, ny) eye anchor pairs in cell space (y from top). */
    public static float[] entityEyes(int spriteId) {
        return switch (spriteId) {
            case THRALL, THRALL_CAST, THRALL_GUARD, THRALL_HURT -> EYES_THRALL;
            case THRALL_ATTACK -> EYES_THRALL_ATTACK;
            case ORC -> EYES_ORC;
            case ORC_ATTACK -> EYES_ORC_ATTACK;
            case GOBLIN -> EYES_GOBLIN;
            case GOBLIN_AIM -> EYES_GOBLIN_AIM;
            case GOBLIN_AFTER_SHOT -> EYES_GOBLIN_AFTER_SHOT;
            case GOBLIN_ASSASSIN -> EYES_GOBLIN_ASSASSIN;
            case GOBLIN_ASSASSIN_ATTACK -> EYES_GOBLIN_ASSASSIN_ATTACK;
            case OGRE -> EYES_OGRE;
            case OGRE_ATTACK -> EYES_OGRE_ATTACK;
            case OGRE_ATTACK_2 -> EYES_OGRE_ATTACK_2;
            case RAT -> EYES_RAT;
            default -> EMPTY_ANCHORS;
        };
    }

    /** Normalized blade line anchors (nx0, ny0, nx1, ny1, thickFrac) in cell space. */
    public static float[] weaponGlows(int spriteId) {
        return switch (spriteId) {
            case ORC -> WEAP_ORC;
            case ORC_ATTACK -> WEAP_ORC_ATTACK;
            case GOBLIN_ASSASSIN -> WEAP_ASSASSIN;
            case GOBLIN_ASSASSIN_ATTACK -> WEAP_ASSASSIN_ATK;
            default -> EMPTY_ANCHORS;
        };
    }

    public static int spriteForLoot(byte lootKind) {
        return switch (lootKind) {
            case Scene.LOOT_BLADE -> LOOT_BLADE;
            case Scene.LOOT_FLASK_OIL -> LOOT_OIL;
            case Scene.LOOT_FLASK_WATER -> LOOT_WATER;
            case Scene.LOOT_FLASK_ACID -> LOOT_ACID;
            case Scene.LOOT_STONE -> LOOT_STONE;
            default -> LOOT_STONE;
        };
    }
}
