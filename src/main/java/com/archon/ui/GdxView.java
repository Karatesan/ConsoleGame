package com.archon.ui;

import com.archon.app.Game;
import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.ui.audio.SynthAudio;
import com.archon.verb.ExitCode;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.util.ArrayList;
import java.util.List;

/**
 * Desktop front end (100% fullscreen with F11 toggle). Renders via ScreenViewport with 2x
 * supersampled fonts, 252x252 monochrome comic/graphite sprites at 192px tiles (camera follow),
 * dynamic tile lighting, faded selective-color FX/elements/blood, and procedural synth audio.
 */
public final class GdxView extends ApplicationAdapter implements View {
    public static final int WIN_W = 1140, WIN_H = 820;
    private static final int TILE = 192, SIDE_W = 228, MAP_X = 28, MAP_Y = 24,
            GAP = 12, PAD_R = 14, PAD_B = 10, LINE_H = 22, MAX_FLOATERS = 16;
    private static final int INPUT_H = 32, SCROLLBAR_W = 8, JUMP_PILL_H = 22;
    private static final float SLIDE = 0.4f, GHOST_LIFE = 0.8f, FLOAT_LIFE = 1.6f, SCROLL_LINES = 1.0f,
            THRALL_ATTACK_SCALE = 1.24f, ARCHER_AIM_TIME = 0.36f, ARCHER_AFTER_TIME = 0.52f,
            OGRE_ATTACK_POSE_TIME = 1.25f, OGRE_ATTACK_LUNGE_TIME = 1.15f;
    static final float GOBLIN_SCALE = 0.78f, BRAZIER_SCALE = 0.80f;
    private static final int MAX_PARTICLES = 192, MAX_FX = 16, MAX_ENT = 32, MAX_CRIT_QUEUE = 8, MAX_BLOOD_SPLATS = 192;
    private static final int FX_BLOOD_BOLT = 1, FX_PSIONIC_SPIKE = 2, FX_PROJ = 3,
            FX_FIREBALL = 4, FX_ELEM_BOLT = 5, FX_RING = 6, FX_CROSS = 7, FX_DRAIN = 8, FX_CRIT = 9;
    private static final int ELEM_FORCE = 0, ELEM_FIRE = 1, ELEM_WATER = 2,
            ELEM_ACID = 3, ELEM_OIL = 4, ELEM_BLOOD = 5, ELEM_ITEM = 6;
    private static final int[] CROSS_DX = {0, 0, 0, 1, -1}, CROSS_DY = {0, -1, 1, 0, 0};
    private static final String[] NUMS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
            "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31"
    };

    // Faded graphic-novel selective-color palette over monochrome charcoal/graphite art
    private static final Color BG = Color.valueOf("090B0E"), PANEL = Color.valueOf("11151C"),
            BORDER = Color.valueOf("232B38"), BAR_BG = Color.valueOf("0A0C10"),
            FIRE = Color.valueOf("C96A42"), EMBER = Color.valueOf("D4A04E"),
            WATER_COL = Color.valueOf("4B7087"), ICE_COL = Color.valueOf("8EB0BF"),
            STEAM_COL = Color.valueOf("B8C0C8"), SUPERHEATED_COL = Color.valueOf("C7977B"),
            ACID_GLOW = Color.valueOf("82A34F"),
            MIST_COL = Color.valueOf("A37544"), STUN_COL = Color.valueOf("CCA94E"),
            BLEED_COL = Color.valueOf("A3353E"), BOLT_COL = Color.valueOf("79AFB8"),
            BLOOD_BRIGHT = Color.valueOf("AD3A46"), BLOOD_DEEP = Color.valueOf("691D25"),
            THRALL = Color.valueOf("69A698"), CREATURE = Color.valueOf("B85858"),
            PROP = Color.valueOf("B5935E"), DOOR = Color.valueOf("827568"),
            TEXT = Color.valueOf("D8DEE9"), MUTED = Color.valueOf("5E6C80"), SAGE = Color.valueOf("689E7E");

    // Log entry kinds & gutter groups
    private static final int K_ROUND = 0, K_PHASE = 1, K_CMD = 2, K_STAGE = 3, K_ACTOR = 4, K_TEXT = 5, K_ALERT = 6;
    private static final int G_NONE = 0, G_PLAYER = 1, G_HOSTILE = 2, G_ENV = 3;

    private static final class LogRow {
        final int round, kind, group, block;
        String text, badge;
        Color color, badgeColor;

        LogRow(int round, int kind, int group, int block, String text, Color color, String badge, Color badgeColor) {
            this.round = round;
            this.kind = kind;
            this.group = group;
            this.block = block;
            this.text = text;
            this.color = color;
            this.badge = badge;
            this.badgeColor = badgeColor;
        }
    }

    private final long seed;
    private final boolean magicArena;
    private Game game;
    private Presenter presenter;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private PixelAtlas atlas;
    private FrameBuffer lightFbo;
    private final OrthographicCamera lightCam = new OrthographicCamera();
    private final float[] tmpTorch = new float[2];
    private BitmapFont font, hudFont, badgeFont;
    private final SynthAudio audio = new SynthAudio();
    private float charW = 8f, fontH = 14f, hudFontH = 12f;
    private final ScreenViewport viewport = new ScreenViewport();
    private int viewW = WIN_W, viewH = WIN_H;

    private Scene cur, prev;
    private int[] prevIdx = new int[0];
    private float anim = SLIDE, time, blinkTime, ambientTimer;
    private int hp, maxHp, ap, roundAp, round, thinking, curGroup = G_NONE, curBlock, nextBlock = 1;
    private String actingId = "";
    private final ArrayList<LogRow> log = new ArrayList<>();
    private LogRow openStage;
    private float logScroll;
    private boolean draggingScroll, hoverScroll, hoverJump;
    private float dragOffset;
    private final Vector2 tmpMouse = new Vector2();
    private int lastJumpLines = -1;
    private String jumpLabel = "";

    private final int[] floatX = new int[MAX_FLOATERS], floatY = new int[MAX_FLOATERS];
    private final String[] floatText = new String[MAX_FLOATERS];
    private final float[] floatAge = new float[MAX_FLOATERS];
    private int nextFloater;

    // Flat ring-buffer particles & combat/spell VFX
    private final float[] partX = new float[MAX_PARTICLES], partY = new float[MAX_PARTICLES],
            partVX = new float[MAX_PARTICLES], partVY = new float[MAX_PARTICLES],
            partAge = new float[MAX_PARTICLES], partMaxAge = new float[MAX_PARTICLES],
            partSize = new float[MAX_PARTICLES];
    private final Color[] partCol = new Color[MAX_PARTICLES];
    private int nextPart;

    private final int[] fxKind = new int[MAX_FX], fxParam = new int[MAX_FX];
    private final float[] fxX0 = new float[MAX_FX], fxY0 = new float[MAX_FX],
            fxX1 = new float[MAX_FX], fxY1 = new float[MAX_FX],
            fxAge = new float[MAX_FX], fxMaxAge = new float[MAX_FX];
    private final Color[] fxCol = new Color[MAX_FX];
    private int nextFx;

    private final float[] bloodX = new float[MAX_BLOOD_SPLATS], bloodY = new float[MAX_BLOOD_SPLATS],
            bloodScale = new float[MAX_BLOOD_SPLATS], bloodRot = new float[MAX_BLOOD_SPLATS],
            bloodAlpha = new float[MAX_BLOOD_SPLATS], bloodShade = new float[MAX_BLOOD_SPLATS];
    private final int[] bloodKind = new int[MAX_BLOOD_SPLATS];
    private int bloodCount, nextBlood;
    private final float[] hitFlash = new float[MAX_ENT],
            lungeX = new float[MAX_ENT], lungeY = new float[MAX_ENT],
            lungeAge = new float[MAX_ENT], lungeMax = new float[MAX_ENT],
            entPoseAge = new float[MAX_ENT], entPoseMax = new float[MAX_ENT],
            entPx = new float[MAX_ENT], entPy = new float[MAX_ENT],
            entSx = new float[MAX_ENT], entSy = new float[MAX_ENT],
            entScale = new float[MAX_ENT], entRot = new float[MAX_ENT];
    private final int[] entPose = new int[MAX_ENT], drawOrder = new int[MAX_ENT],
            entSpr = new int[MAX_ENT], entFace = new int[MAX_ENT];
    private float camX, camY, panLastX, panLastY;
    private boolean draggingMap, manualPan;
    private static final float PIPE_STEP_DELAY = 0.26f;
    private float shakeTime, shakeMaxTime = 0.28f, shakeMag, shakeX, shakeY, hitStop, critFlashTime;
    private final int[] targetX = new int[8], targetY = new int[8];
    private int vfxRng = 0x13572468, pipeElem = ELEM_FORCE, lastImpactElem = ELEM_FORCE;
    private float curVfxDelay = 0f, lastPipeDelay = 0f;
    private int thrallPose = PixelAtlas.POSE_IDLE, thrallFacing = 1, thrallCastElem = ELEM_FORCE;
    private float thrallPoseAge, thrallPoseMax;
    private final String[] critMsg = new String[MAX_CRIT_QUEUE];
    private final int[] critTierQ = new int[MAX_CRIT_QUEUE], critElemQ = new int[MAX_CRIT_QUEUE];
    private int critCount;

    private final StringBuilder input = new StringBuilder();
    private int cursor;
    private final ArrayList<String> history = new ArrayList<>();
    private int historyPos;
    private boolean dirty;

    public GdxView(long seed) {
        this(seed, true);
    }

    public GdxView(long seed, boolean magicArena) {
        this.seed = seed;
        this.magicArena = magicArena;
        java.util.Arrays.fill(floatAge, FLOAT_LIFE);
        java.util.Arrays.fill(lungeMax, 0.22f);
        for (int i = 0; i < MAX_PARTICLES; i++) {
            partAge[i] = 1f;
            partMaxAge[i] = 1f;
            partCol[i] = new Color(Color.WHITE);
        }
        for (int i = 0; i < MAX_FX; i++) {
            fxAge[i] = 1f;
            fxMaxAge[i] = 1f;
            fxCol[i] = new Color(Color.WHITE);
        }
    }

    // ---- lifecycle ----

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        atlas = new PixelAtlas();
        lightFbo = new FrameBuffer(Pixmap.Format.RGBA8888, 512, 512, false);
        lightFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        MonoFont primary = MonoFont.build(14);
        font = primary.font;
        charW = primary.charW;
        fontH = primary.fontH;
        MonoFont hud = MonoFont.build(12);
        hudFont = hud.font;
        hudFontH = hud.fontH;
        badgeFont = MonoFont.build(10).font;
        audio.init();
        viewW = Math.max(1, Gdx.graphics.getWidth());
        viewH = Math.max(1, Gdx.graphics.getHeight());
        viewport.update(viewW, viewH, true);

        game = new Game(seed, magicArena);
        presenter = new Presenter(this, game.world);
        game.bus.subscribe(presenter::handle);
        game.start();
        frame(game.world, game.round);

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char c) {
                if (c == '\r' || c == '\n') submit();
                else if (c == '\b' || (c >= 32 && c != 127)) {
                    editInputChar(c);
                    playSfx(SynthAudio.KEY, 0.22f, 0.16f);
                }
                return true;
            }

            @Override
            public boolean keyDown(int key) {
                switch (key) {
                    case Input.Keys.F2 -> audio.toggleMute();
                    case Input.Keys.F3 -> audio.toggleMusic();
                    case Input.Keys.F11 -> {
                        if (Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(WIN_W, WIN_H);
                        else Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                    }
                    case Input.Keys.LEFT -> moveCursor(-1);
                    case Input.Keys.RIGHT -> moveCursor(1);
                    case Input.Keys.FORWARD_DEL -> deleteForward();
                    case Input.Keys.UP -> { if (historyPos > 0) recall(--historyPos); }
                    case Input.Keys.DOWN -> { if (historyPos < history.size()) recall(++historyPos); }
                    case Input.Keys.PAGE_UP -> setLogScroll(logScroll + Math.max(1, logLines() - 1));
                    case Input.Keys.PAGE_DOWN -> setLogScroll(logScroll - Math.max(1, logLines() - 1));
                    case Input.Keys.HOME -> setLogScroll(maxScroll());
                    case Input.Keys.END -> setLogScroll(0f);
                    default -> { return false; }
                }
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                setLogScroll(logScroll - amountY * SCROLL_LINES);
                return true;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                viewport.unproject(tmpMouse.set(screenX, screenY));
                hoverScroll = maxScroll() > 0 && inScrollbar(tmpMouse.x, tmpMouse.y);
                hoverJump = scrollLines() > 0 && inJumpPill(tmpMouse.x, tmpMouse.y);
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                viewport.unproject(tmpMouse.set(screenX, screenY));
                if (scrollLines() > 0 && inJumpPill(tmpMouse.x, tmpMouse.y)) {
                    setLogScroll(0f);
                    hoverJump = false;
                    return true;
                }
                if (maxScroll() > 0 && inScrollbar(tmpMouse.x, tmpMouse.y)) {
                    float ty = thumbY(), th = thumbH();
                    dragOffset = (tmpMouse.y >= ty && tmpMouse.y <= ty + th) ? tmpMouse.y - ty : th * 0.5f;
                    draggingScroll = true;
                    dragScrollbar(tmpMouse.y);
                    return true;
                }
                if (inMap(tmpMouse.x, tmpMouse.y)) {
                    draggingMap = true;
                    manualPan = true;
                    panLastX = tmpMouse.x;
                    panLastY = tmpMouse.y;
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (draggingScroll) {
                    viewport.unproject(tmpMouse.set(screenX, screenY));
                    dragScrollbar(tmpMouse.y);
                    return true;
                }
                if (draggingMap && cur != null) {
                    viewport.unproject(tmpMouse.set(screenX, screenY));
                    float dx = tmpMouse.x - panLastX;
                    float dy = tmpMouse.y - panLastY;
                    panLastX = tmpMouse.x;
                    panLastY = tmpMouse.y;
                    camX = clampCam(camX - dx / TILE, cur.w, viewTilesX());
                    camY = clampCam(camY + dy / TILE, cur.h, viewTilesY());
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                if (draggingMap) {
                    draggingMap = false;
                    return true;
                }
                if (!draggingScroll) return false;
                draggingScroll = false;
                viewport.unproject(tmpMouse.set(screenX, screenY));
                hoverScroll = maxScroll() > 0 && inScrollbar(tmpMouse.x, tmpMouse.y);
                return true;
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewW = width;
        viewH = height;
        viewport.update(width, height, true);
        if (cur != null) {
            camX = clampCam(camX, cur.w, viewTilesX());
            camY = clampCam(camY, cur.h, viewTilesY());
        }
        setLogScroll(logScroll);
    }

    void editInputChar(char c) {
        if (c == '\b') {
            if (cursor > 0) {
                input.deleteCharAt(--cursor);
                blinkTime = 0f;
            }
        } else if (c >= 32 && c != 127) {
            input.insert(cursor++, c);
            blinkTime = 0f;
        }
    }

    void moveCursor(int delta) {
        cursor = MathUtils.clamp(cursor + delta, 0, input.length());
        blinkTime = 0f;
    }

    void deleteForward() {
        if (cursor < input.length()) {
            input.deleteCharAt(cursor);
            blinkTime = 0f;
        }
    }

    String inputText() { return input.toString(); }

    int cursorPos() { return cursor; }

    int recentFxKind(int back) {
        int idx = ((nextFx - 1 - back) % MAX_FX + MAX_FX) % MAX_FX;
        return fxAge[idx] < fxMaxAge[idx] ? fxKind[idx] : 0;
    }

    int recentFxParam(int back) {
        int idx = ((nextFx - 1 - back) % MAX_FX + MAX_FX) % MAX_FX;
        return fxParam[idx];
    }

    int thrallFacing() { return thrallFacing; }

    float shakeMag() { return shakeMag; }

    float hitStopLeft() { return hitStop; }

    float critFlashLeft() { return critFlashTime; }

    String recentFloaterText(int back) {
        int idx = ((nextFloater - 1 - back) % MAX_FLOATERS + MAX_FLOATERS) % MAX_FLOATERS;
        return floatText[idx];
    }

    int bloodSplatCount() { return bloodCount; }

    static float entityScale(int spr) {
        return switch (spr) {
            case PixelAtlas.GOBLIN, PixelAtlas.GOBLIN_AIM, PixelAtlas.GOBLIN_AFTER_SHOT,
                 PixelAtlas.GOBLIN_ASSASSIN, PixelAtlas.GOBLIN_ASSASSIN_ATTACK -> GOBLIN_SCALE;
            case PixelAtlas.BRAZIER -> BRAZIER_SCALE;
            default -> 1f;
        };
    }

    void stepAnim(float dt) {
        anim += updateEffects(dt);
    }

    int thrallDrawSprite(int entFrame) {
        if (cur.frozenEntity[0]) return PixelAtlas.THRALL;
        if (cur.proneEntity[0]) return PixelAtlas.THRALL;
        if (thrallPose != PixelAtlas.POSE_IDLE && thrallPoseAge < thrallPoseMax) {
            return PixelAtlas.thrallSprite(thrallPose);
        }
        if (prevIdx[0] >= 0 && cur.moved(0, prev) && anim < SLIDE) {
            return PixelAtlas.thrallSprite(PixelAtlas.POSE_MOVE);
        }
        if (hitFlash[0] > 0) return PixelAtlas.THRALL_HURT;
        return PixelAtlas.THRALL;
    }

    int entityDrawSprite(int i, int entFrame) {
        if (i == 0) return thrallDrawSprite(entFrame);
        int base = PixelAtlas.spriteForEntity(cur, i);
        if (cur.frozenEntity[i] || cur.proneEntity[i]) return base;
        if (i < MAX_ENT && entPose[i] != PixelAtlas.POSE_IDLE && entPoseAge[i] < entPoseMax[i]) {
            if (base == PixelAtlas.GOBLIN && entPose[i] == PixelAtlas.POSE_ATTACK && entPoseAge[i] >= ARCHER_AIM_TIME) {
                return PixelAtlas.GOBLIN_AFTER_SHOT;
            }
            return PixelAtlas.entitySprite(base, entPose[i]);
        }
        if (base == PixelAtlas.GOBLIN) {
            if (cur.overwatchEntity[i] && cur.nockedEntity[i]) return PixelAtlas.GOBLIN_AIM;
            if (!cur.nockedEntity[i]) return PixelAtlas.GOBLIN_AFTER_SHOT;
        }
        return base;
    }

    void recall(int i) {
        input.setLength(0);
        if (i < history.size()) input.append(history.get(i));
        cursor = input.length();
        blinkTime = 0f;
    }

    private void submit() {
        String raw = input.toString().trim();
        input.setLength(0);
        cursor = 0;
        blinkTime = 0f;
        if (raw.isEmpty() || presenter.busy() || !game.world.thrall().alive()) return;
        if (raw.equalsIgnoreCase("quit") || raw.equalsIgnoreCase("exit")) {
            Gdx.app.exit();
            return;
        }
        history.add(raw);
        historyPos = history.size();
        logScroll = 0f;
        manualPan = false;
        curGroup = G_PLAYER;
        curBlock = nextBlock++;
        openStage = null;
        pipeElem = ELEM_FORCE;
        addRow(K_CMD, G_PLAYER, "> " + raw, THRALL, null, null);
        game.submit(raw);
        dirty = true;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        if (atlas != null) atlas.dispose();
        if (lightFbo != null) lightFbo.dispose();
        font.dispose();
        hudFont.dispose();
        badgeFont.dispose();
        audio.dispose();
    }

    // ---- View: events and scenes ----

    private int maxChars(boolean hasBadge) {
        return Math.max(40, (int) ((logW() - (hasBadge ? 190 : 40)) / charW));
    }

    private LogRow addRow(int kind, int group, String text, Color color, String badge, Color badgeColor) {
        int block = (kind == K_ROUND || kind == K_PHASE) ? 0 : curBlock;
        int limit = maxChars(badge != null || kind == K_STAGE);
        if (text.length() <= limit) {
            LogRow r = new LogRow(round, kind, group, block, text, color, badge, badgeColor);
            log.add(r);
            if (scrollLines() > 0) logScroll += 1f;
            else logScroll = 0f;
            return r;
        }
        int split = text.lastIndexOf(' ', limit);
        if (split <= 6) split = limit;
        LogRow first = new LogRow(round, kind, group, block, text.substring(0, split), color, badge, badgeColor);
        log.add(first);
        if (scrollLines() > 0) logScroll += 1f;
        else logScroll = 0f;
        addRow(K_TEXT, group, "      " + text.substring(split).trim(), color, null, null);
        return first;
    }

    /** Merges primary stage action + status pill, wrapping extra narrations underneath. */
    @Override
    public void handle(GameEvent event) {
        switch (event) {
            case GameEvent.LineStart e -> pipeElem = ELEM_FORCE;
            case GameEvent.Narrative e -> {
                String text = e.text();
                pipeElem = detectNarrativeElem(text, pipeElem);
                latchCrit(text, pipeElem);
                if (text.contains("shatter")) {
                    triggerShake(0.22f, 4f);
                    playSfx(SynthAudio.SHATTER, 0.68f, 0.14f);
                } else if (text.contains("EXPLOD") || text.contains("detonat")) {
                    triggerShake(0.28f, 5f);
                    playSfx(SynthAudio.BOOM, 0.72f, 0.12f);
                }
                Color c = text.startsWith("CRITICAL") || (curGroup == G_PLAYER && (text.endsWith("!") || text.contains("crippled") || text.contains("blind")))
                        ? PROP : (text.contains(" misses ") ? MUTED : TEXT);
                if (openStage != null && !text.contains("\n")) {
                    String combined = openStage.text.isEmpty() ? "  " + text : openStage.text + "  -  " + text;
                    if (combined.length() <= maxChars(true)) {
                        openStage.text = combined;
                        openStage.color = c;
                    } else {
                        openStage.color = TEXT;
                        addRow(K_TEXT, curGroup, "    " + text, c, null, null);
                    }
                    openStage = null;
                } else {
                    openStage = null;
                    String prefix = curGroup == G_NONE ? "" : "    ";
                    for (String line : text.split("\\R")) addRow(K_TEXT, curGroup, prefix + line, c, null, null);
                }
            }
            case GameEvent.Rejected e -> {
                openStage = null;
                playSfx(SynthAudio.REJECT, 0.42f, 0.08f);
                if (curBlock == 0) curBlock = nextBlock++;
                String msg = e.reason() + (e.hint() == null ? "" : " (" + e.hint() + ")");
                addRow(K_ALERT, G_PLAYER, "  " + msg, CREATURE, "REJECT | " + e.code().name(), CREATURE);
            }
            case GameEvent.StageStart e -> {
                if (curBlock == 0) curBlock = nextBlock++;
                String prefix = e.total() > 1 ? String.format("  [%d/%d] %s", e.index(), e.total(), e.render()) : "";
                openStage = addRow(K_STAGE, G_PLAYER, prefix, MUTED, null, null);
            }
            case GameEvent.StageResult e -> {
                ap = e.apLeft();
                if (e.code() == ExitCode.INVALID || e.code() == ExitCode.BLOCKED) {
                    playSfx(SynthAudio.REJECT, 0.42f, 0.08f);
                } else {
                    triggerStageVfx(e.render());
                }
                String pill = e.code().name() + (e.charged() > 0 ? " | " + e.charged() + " AP" : "");
                Color pc = switch (e.code()) {
                    case SUCCESS -> SAGE;
                    case MISS, PARTIAL -> PROP;
                    default -> CREATURE;
                };
                LogRow target = openStage;
                if (target == null && !log.isEmpty()) {
                    for (int i = log.size() - 1; i >= 0 && log.get(i).round == round; i--) {
                        if (log.get(i).kind == K_STAGE && log.get(i).badge == null) { target = log.get(i); break; }
                    }
                }
                if (target != null) {
                    if (target.text.isEmpty()) {
                        target.text = "  " + e.render();
                        target.color = TEXT;
                    } else if (target == openStage) {
                        target.color = TEXT;
                    }
                    target.badge = pill;
                    target.badgeColor = pc;
                } else {
                    addRow(K_STAGE, G_PLAYER, "  " + e.render(), TEXT, pill, pc);
                }
                openStage = null;
            }
            case GameEvent.StageSkipped e -> {
                openStage = null;
                String prefix = String.format("  [%d/%d] %s  -  %s", e.index(), e.total(), e.render(), e.why());
                addRow(K_STAGE, G_PLAYER, prefix, MUTED, "SKIPPED", MUTED);
            }
            case GameEvent.LineBroke e -> {
                ap = e.apLeft();
                openStage = null;
                playSfx(SynthAudio.REJECT, 0.42f, 0.08f);
                addRow(K_ALERT, G_PLAYER, "  " + e.reason(), CREATURE, "BREAK | -" + e.forfeited() + " AP", CREATURE);
            }
            case GameEvent.InterruptFired e -> {
                openStage = null;
                boolean meleeReact = false;
                if (cur != null) {
                    int si = cur.indexOf(e.source());
                    if (si > 0 && si < MAX_ENT) {
                        float ax = drawX(si) + 0.5f, ay = drawY(si) + 0.5f;
                        float tx = drawX(0) + 0.5f, ty = drawY(0) + 0.5f;
                        if (PixelAtlas.spriteForEntity(cur, si) == PixelAtlas.GOBLIN) {
                            spawnFx(FX_PROJ, ax, ay, tx, ty, 0.28f, PROP);
                            setEntityPose(si, PixelAtlas.POSE_AFTER_ATTACK, 0.55f);
                        } else {
                            meleeReact = true;
                            boolean ogre = PixelAtlas.spriteForEntity(cur, si) == PixelAtlas.OGRE;
                            float poseTime = ogre ? OGRE_ATTACK_POSE_TIME : 0.78f;
                            float lungeTime = ogre ? OGRE_ATTACK_LUNGE_TIME : 0.72f;
                            int atkPose = (ogre && nextRand01() < 0.5f) ? PixelAtlas.POSE_ATTACK_ALT : PixelAtlas.POSE_ATTACK;
                            setEntityPose(si, atkPose, poseTime);
                            lungeX[si] = (tx - ax) * 0.45f;
                            lungeY[si] = (ty - ay) * 0.45f;
                            lungeAge[si] = lungeTime;
                            lungeMax[si] = lungeTime;
                        }
                    }
                    spawnFx(FX_RING, cur.x[0] + 0.5f, cur.y[0] + 0.5f, 1.1f, 0f, 0.3f, CREATURE);
                    triggerShake(0.18f, 3f);
                }
                playSfxAt(meleeReact ? SynthAudio.SWING : SynthAudio.SHOOT, 0.62f, 0.12f,
                        cur != null ? cur.x[0] + 0.5f : 0f);
                addRow(K_ALERT, G_ENV, "  " + e.source() + ": " + e.description(), PROP, "REACT | -" + e.damage() + " HP", PROP);
            }
            case GameEvent.EnemyPhaseStart e -> {
                openStage = null;
                lastImpactElem = ELEM_FORCE;
                lastPipeDelay = 0f;
                curGroup = G_HOSTILE;
                curBlock = 0;
                addRow(K_PHASE, G_HOSTILE, "HOSTILE PHASE", CREATURE, null, null);
            }
            case GameEvent.ActorBeat e -> {
                actingId = e.id();
                manualPan = false;
                lastImpactElem = ELEM_FORCE;
                lastPipeDelay = 0f;
                curGroup = G_HOSTILE;
                curBlock = nextBlock++;
                triggerActorBeatVfx(e);
                for (int i = 0; i < e.lines().size(); i++) {
                    String l = e.lines().get(i);
                    latchCrit(l, detectNarrativeElem(l, ELEM_FORCE));
                }
                String move = e.from().equals(e.to()) ? "holds @" + e.to() : e.from() + " -> " + e.to();
                if (e.lines().isEmpty()) {
                    addRow(K_ACTOR, G_HOSTILE, "  " + e.name(), TEXT, move, MUTED);
                } else {
                    addRow(K_ACTOR, G_HOSTILE, "  " + e.name() + "  -  " + e.lines().get(0), TEXT, move, CREATURE);
                    for (int i = 1; i < e.lines().size(); i++) {
                        addRow(K_TEXT, G_HOSTILE, "      " + e.lines().get(i), TEXT, null, null);
                    }
                }
            }
            case GameEvent.EnvBeat e -> {
                actingId = "";
                curGroup = G_ENV;
                curBlock = nextBlock++;
                List<Vec2> ignited = e.ignited();
                for (int i = 0; i < ignited.size(); i++) {
                    Vec2 p = ignited.get(i);
                    spawnFx(FX_RING, p.x() + 0.5f, p.y() + 0.5f, 1.0f, 0f, 0.35f, FIRE);
                    spawnBurst(p.x() + 0.5f, p.y() + 0.5f, 12, FIRE, -1.0f);
                }
                boolean exploded = false, shattered = false;
                for (int i = 0; i < e.lines().size(); i++) {
                    String l = e.lines().get(i);
                    if (l.contains("shatter")) shattered = true;
                    else if (l.contains("detonate") || l.contains("explode")) exploded = true;
                    addRow(K_ALERT, G_ENV, "  " + l, FIRE, "HAZARD", FIRE);
                }
                if (shattered) {
                    triggerShake(0.22f, 4f);
                    playSfx(SynthAudio.SHATTER, 0.68f, 0.14f);
                } else if (exploded) {
                    triggerShake(0.28f, 5f);
                    playSfx(SynthAudio.BOOM, 0.72f, 0.12f);
                } else if (!ignited.isEmpty()) {
                    playSfxAt(SynthAudio.FIRE, 0.54f, 0.14f, ignited.get(0).x() + 0.5f);
                }
            }
            case GameEvent.Thinking e -> thinking = e.dots();
            case GameEvent.RoundStart e -> {
                thinking = 0;
                actingId = "";
                manualPan = false;
                curGroup = G_NONE;
                curBlock = 0;
                critCount = 0;
                openStage = null;
                round = e.round();
                ap = e.ap();
                if (e.round() > 1) playSfx(SynthAudio.SUBMIT, 0.22f, 0f);
                addRow(K_ROUND, G_NONE, "ROUND " + e.round(), THRALL, e.ap() + " AP READY", THRALL);
            }
            case GameEvent.ThrallDied e -> {
                curBlock = 0;
                playSfx(SynthAudio.STING_DEATH, 0.72f, 0f);
                addRow(K_ALERT, G_NONE, "THE THRALL COLLAPSES. THE LINK GOES DARK.", CREATURE, "FATAL", CREATURE);
            }
            default -> { }
        }
    }

    private static int detectNarrativeElem(String text, int fallback) {
        if (text.contains("Bloodlance") || text.contains("Crimson Nova") || text.contains("Blood Drain")
                || text.contains("Blood Surge") || text.contains("absorbs blood") || text.contains("absorbs spilled blood")) {
            return ELEM_BLOOD;
        }
        if (text.contains("Inferno") || text.contains("Heat ") || text.contains("draws inferno")
                || text.contains("draws heat") || text.contains("channeled inferno") || text.contains("channeled heat")) {
            return ELEM_FIRE;
        }
        if (text.contains("Acid ") || text.contains("acid flask")
                || text.contains("draws acid") || text.contains("channeled acid")) {
            return ELEM_ACID;
        }
        if (text.contains("Water ") || text.contains("Steam") || text.contains("Superheated") || text.contains("water flask")
                || text.contains("draws water") || text.contains("draws steam") || text.contains("channeled water")
                || text.contains("channeled steam")) {
            return ELEM_WATER;
        }
        if (text.contains("Oil ") || text.contains("Oil_mist") || text.contains("oil flask")
                || text.contains("draws oil") || text.contains("draws oil_mist") || text.contains("channeled oil")) {
            return ELEM_OIL;
        }
        if (text.contains("Kinetic") || text.contains("draws force") || text.contains("channeled force")) {
            return ELEM_FORCE;
        }
        return fallback;
    }

    private int inferStageElement(String cmd, String verb, int nTargets) {
        if (cmd.contains(" -b") || cmd.endsWith("-b") || cmd.contains("--blood")) return ELEM_BLOOD;
        if ("channel".equals(verb) && (cmd.contains(" -f") || cmd.endsWith("-f") || cmd.contains("--force"))) {
            return ELEM_FORCE;
        }
        if (("siphon".equals(verb) || "pull".equals(verb) || "take".equals(verb)) && nTargets > 0) {
            int tx = targetX[0], ty = targetY[0];
            int t = ty * cur.w + tx;
            if ("siphon".equals(verb)) {
                for (int i = 0; i < cur.n; i++) {
                    if (cur.hp[i] > 0 && cur.x[i] == tx && cur.y[i] == ty) {
                        if (cur.litEntity[i] || cur.burningEntity[i] || cur.kind[i] == Entity.Kind.CREATURE) return ELEM_FIRE;
                        if (cur.glyph[i] == 'B') return ELEM_OIL;
                    }
                }
                if (cur.burning[t]) return ELEM_FIRE;
            }
            if (cur.lootKind[t] == Scene.LOOT_FLASK_OIL) return ELEM_OIL;
            if (cur.lootKind[t] == Scene.LOOT_FLASK_ACID) return ELEM_ACID;
            if (cur.lootKind[t] == Scene.LOOT_FLASK_WATER) return ELEM_WATER;
            if ("siphon".equals(verb)) {
                if (cur.oil[t] || cur.oilMist[t]) return ELEM_OIL;
                if (cur.acid[t]) return ELEM_ACID;
                if (cur.water[t] || cur.steam[t] || cur.superheatedSteam[t]) return ELEM_WATER;
            } else if (cur.lootKind[t] != Scene.LOOT_NONE) {
                return ELEM_ITEM;
            }
        }
        if ("channel".equals(verb) && pipeElem == ELEM_FORCE && cur.channel != null) {
            if (cur.channel.startsWith("FIRE") || cur.channel.startsWith("INFERNO")) return ELEM_FIRE;
            if (cur.channel.startsWith("ACID")) return ELEM_ACID;
            if (cur.channel.startsWith("WATER") || cur.channel.startsWith("STEAM") || cur.channel.startsWith("SUPERHEATED")) return ELEM_WATER;
            if (cur.channel.startsWith("OIL")) return ELEM_OIL;
        }
        return pipeElem;
    }

    private static Color elemColor(int elem) {
        return switch (elem) {
            case ELEM_FIRE -> FIRE;
            case ELEM_WATER -> ICE_COL;
            case ELEM_ACID -> ACID_GLOW;
            case ELEM_OIL -> MIST_COL;
            case ELEM_BLOOD -> BLOOD_BRIGHT;
            case ELEM_ITEM -> PROP;
            default -> BOLT_COL;
        };
    }

    private void spawnCrossBurst(int ex, int ey, int countPerTile, Color col, float baseVy) {
        for (int c = 0; c < CROSS_DX.length; c++) {
            int cx = ex + CROSS_DX[c], cy = ey + CROSS_DY[c];
            if (cx >= 0 && cx < cur.w && cy >= 0 && cy < cur.h && !cur.wall[cy * cur.w + cx]) {
                spawnBurst(cx + 0.5f, cy + 0.5f, countPerTile, col, baseVy);
            }
        }
    }

    private void triggerStageVfx(String cmd) {
        if (cur == null || cmd == null || cmd.isBlank()) return;
        int len = cmd.length(), segStart = 0, pipeIdx = 0;
        while (segStart < len) {
            int bar = cmd.indexOf('|', segStart);
            int segEnd = bar < 0 ? len : bar;
            String seg = cmd.substring(segStart, segEnd).trim();
            if (!seg.isEmpty()) {
                curVfxDelay = pipeIdx * PIPE_STEP_DELAY;
                triggerInvocationVfx(seg, pipeIdx++);
            }
            if (bar < 0) break;
            segStart = bar + 1;
        }
        lastPipeDelay = pipeIdx > 1 ? (pipeIdx - 1) * PIPE_STEP_DELAY : 0f;
        curVfxDelay = 0f;
        lastImpactElem = pipeElem;
        pipeElem = ELEM_FORCE;
    }

    private void latchCrit(String text, int elem) {
        if (text == null || !text.startsWith("CRITICAL") || critCount >= MAX_CRIT_QUEUE) return;
        int tier = text.startsWith("CRITICAL T3!") ? 3 : text.startsWith("CRITICAL T2!") ? 2 : 1;
        critMsg[critCount] = text;
        critTierQ[critCount] = tier;
        critElemQ[critCount] = elem;
        critCount++;
    }

    private int consumeCritFor(String name) {
        for (int k = 0; k < critCount; k++) {
            if (critMsg[k] != null && name != null && critMsg[k].contains(name)) {
                critMsg[k] = null;
                return k;
            }
        }
        return -1;
    }

    private void triggerShake(float dur, float mag) {
        if (dur > shakeTime) {
            shakeTime = dur;
            shakeMaxTime = dur;
        }
        shakeMag = Math.max(shakeMag, mag);
    }

    private void triggerCritImpact(int entIdx, int tier, int elem, int delta, int floaterSlot) {
        float dur = tier >= 3 ? 0.42f : tier == 2 ? 0.36f : 0.30f;
        float mag = tier >= 3 ? 10f : tier == 2 ? 8f : 6.5f;
        triggerShake(dur, mag);
        hitStop = Math.max(hitStop, 0.05f + 0.015f * tier);
        critFlashTime = Math.max(critFlashTime, 0.14f);

        float cx = cur.x[entIdx] + 0.5f, cy = cur.y[entIdx] + 0.5f;
        Color cc = elem == ELEM_FORCE ? EMBER : elemColor(elem);
        spawnFx(FX_CRIT, cx, cy, cx, cy, 0.42f + 0.05f * tier, cc, (tier << 4) | elem);
        spawnFx(FX_RING, cx, cy, 1.5f + 0.3f * tier, 0f, 0.36f, EMBER, elem);
        spawnFx(FX_RING, cx, cy, 0.9f + 0.2f * tier, 0f, 0.24f, Color.WHITE, elem);
        spawnBurst(cx, cy, 16 + tier * 6, EMBER, -0.7f);
        if (elem != ELEM_FORCE) spawnBurst(cx, cy, 10 + tier * 4, cc, -0.5f);
        if (floaterSlot >= 0) {
            floatText[floaterSlot] = (tier > 1 ? "CRIT T" + tier + "! " : "CRIT! ") + delta;
        }
        float critDelay = curVfxDelay + (elem == ELEM_FORCE ? 0.05f : 0f);
        audio.playTierDelayed(SynthAudio.CRIT, tier - 1, critDelay, Math.min(1f, 0.84f + 0.06f * tier),
                1f + (nextRand01() - 0.5f) * 0.06f, panFor(cx));
        if (elem == ELEM_FIRE) playSfxAtPitched(SynthAudio.FIRE, 0.55f, 1f, 0.12f, cx);
        else if (elem == ELEM_ACID) playSfxAtPitched(SynthAudio.SIZZLE, 0.50f, 1f, 0.12f, cx);
        else if (elem == ELEM_BLOOD) playSfxAtPitched(SynthAudio.BLOOD_BOLT, 0.52f, 1.06f, 0.08f, cx);
        else if (elem == ELEM_FORCE && tier >= 2) playSfxAtPitched(SynthAudio.ZAP, 0.48f, 1.12f, 0.08f, cx);
        if (tier >= 2 && elem != ELEM_FORCE && elem != ELEM_FIRE && elem != ELEM_ACID) {
            playSfxAtPitched(SynthAudio.BOOM, 0.50f, 1f, 0.10f, cx);
        }
        if (cur.hp[entIdx] <= 0 && cur.kind[entIdx] == Entity.Kind.CREATURE) {
            playSfxAtPitched(SynthAudio.STING_KILL, 0.42f, 1f, 0f, cx);
        }
    }

    private void playSfx(int kind, float vol, float jitter) {
        audio.play(kind, vol, 1f + (nextRand01() - 0.5f) * jitter, 0f);
    }

    private float panFor(float tx) {
        return cur == null ? 0f : MathUtils.clamp((tx / Math.max(1f, cur.w) - 0.5f) * 1.15f, -0.6f, 0.6f);
    }

    private void playSfxAt(int kind, float vol, float jitter, float tx) {
        audio.play(kind, vol, 1f + (nextRand01() - 0.5f) * jitter, panFor(tx));
    }

    private void playSfxAtPitched(int kind, float vol, float basePitch, float jitter, float tx) {
        playSfxAtDelayed(kind, curVfxDelay, vol, basePitch, jitter, tx);
    }

    private void playSfxAtDelayed(int kind, float delay, float vol, float basePitch, float jitter, float tx) {
        audio.playDelayed(kind, delay, vol, basePitch * (1f + (nextRand01() - 0.5f) * jitter), panFor(tx));
    }

    private void setThrallPose(int pose, float dur, int targetTileX, boolean hasTarget, int elem) {
        thrallPose = pose;
        thrallPoseAge = 0f;
        thrallPoseMax = dur;
        thrallCastElem = elem;
        if (hasTarget && cur != null) {
            int dx = targetTileX - cur.x[0];
            if (dx != 0) thrallFacing = dx > 0 ? 1 : -1;
        }
    }

    private void setEntityPose(int idx, int pose, float dur) {
        if (idx <= 0 || idx >= MAX_ENT) return;
        entPose[idx] = pose;
        entPoseAge[idx] = 0f;
        entPoseMax[idx] = dur;
    }

    private void triggerInvocationVfx(String cmd, int pipeIdx) {
        int nTargets = findTargets(cmd, cur, targetX, targetY, targetX.length);
        float sx = cur.x[0] + 0.5f, sy = cur.y[0] + 0.5f;
        int ex = nTargets > 0 ? targetX[0] : cur.x[0];
        int ey = nTargets > 0 ? targetY[0] : cur.y[0];
        float tx = ex + 0.5f, ty = ey + 0.5f;

        String verb = firstWord(cmd);
        int elem = inferStageElement(cmd, verb, nTargets);
        boolean channeled = cur != null && cur.channel != null && !"none".equals(cur.channel);
        float pipePitch = 1f + Math.min(3, pipeIdx) * 0.045f + (channeled ? 0.03f : 0f);
        pipeElem = elem;
        switch (verb) {
            case "bolt" -> {
                setThrallPose(PixelAtlas.POSE_CAST, 0.66f, ex, nTargets > 0, elem);
                if (elem == ELEM_FIRE) {
                    if (nTargets > 0) spawnFx(FX_FIREBALL, sx, sy, tx, ty, 0.34f, FIRE, ELEM_FIRE);
                    spawnBurst(tx, ty, 20, EMBER, -0.9f);
                    playSfxAtPitched(SynthAudio.FIRE, 0.68f, pipePitch, 0.14f, tx);
                } else if (elem == ELEM_BLOOD) {
                    if (nTargets > 0) spawnFx(FX_BLOOD_BOLT, sx, sy, tx, ty, 0.36f, BLOOD_BRIGHT, ELEM_BLOOD);
                    spawnBurst(tx, ty, 18, BLOOD_BRIGHT, -0.4f);
                    spawnBurst(sx, sy, 8, BLOOD_DEEP, -0.6f);
                    playSfxAtPitched(SynthAudio.BLOOD_BOLT, 0.76f, pipePitch, 0.10f, tx);
                } else if (elem == ELEM_ACID || elem == ELEM_WATER || elem == ELEM_OIL || elem == ELEM_ITEM) {
                    Color ec = elemColor(elem);
                    if (nTargets > 0) spawnFx(FX_ELEM_BOLT, sx, sy, tx, ty, 0.32f, ec, elem);
                    spawnBurst(tx, ty, 14, ec, -0.5f);
                    int sfx = elem == ELEM_ITEM ? SynthAudio.SHOOT
                            : elem == ELEM_ACID ? SynthAudio.SIZZLE : SynthAudio.SPLASH;
                    playSfxAtPitched(sfx, 0.58f, pipePitch, 0.14f, tx);
                } else {
                    if (nTargets > 0) spawnFx(FX_PSIONIC_SPIKE, sx, sy, tx, ty, 0.36f, BOLT_COL, ELEM_FORCE);
                    spawnBurst(tx, ty, 14, Color.WHITE, -0.5f);
                    playSfxAtPitched(SynthAudio.ZAP, 0.62f, pipePitch, 0.12f, tx);
                }
                if (channeled) playSfxAtPitched(SynthAudio.PSIONIC, 0.22f, pipePitch, 0.08f, sx);
            }
            case "blast" -> {
                setThrallPose(PixelAtlas.POSE_CAST, 0.68f, ex, nTargets > 0, elem);
                Color ec = elemColor(elem);
                spawnFx(FX_CROSS, tx, ty, tx, ty, 0.48f, ec, elem);
                spawnFx(FX_RING, tx, ty, 2.2f, 0f, 0.45f, ec, elem);
                if (elem == ELEM_BLOOD) {
                    spawnFx(FX_RING, tx, ty, 1.2f, 0f, 0.35f, BLOOD_DEEP, elem);
                    spawnCrossBurst(ex, ey, 6, BLOOD_BRIGHT, -0.4f);
                    playSfxAtPitched(SynthAudio.BLOOD_BOLT, 0.78f, pipePitch * 0.84f, 0.10f, tx);
                    playSfxAtPitched(SynthAudio.SPLASH, 0.74f, pipePitch * 0.78f, 0.12f, tx);
                    playSfxAtPitched(SynthAudio.STING_BLOOD, 0.46f, pipePitch * 0.94f, 0.06f, tx);
                } else if (elem == ELEM_FIRE) {
                    spawnCrossBurst(ex, ey, 6, EMBER, -0.9f);
                    playSfxAtPitched(SynthAudio.FIRE, 0.60f, pipePitch, 0.12f, tx);
                } else {
                    spawnCrossBurst(ex, ey, 5, ec, -0.6f);
                    if (elem == ELEM_ACID) playSfxAtPitched(SynthAudio.SIZZLE, 0.54f, pipePitch, 0.12f, tx);
                    else if (elem == ELEM_WATER || elem == ELEM_OIL) playSfxAtPitched(SynthAudio.SPLASH, 0.54f, pipePitch, 0.12f, tx);
                }
                triggerShake(0.24f, 4f);
                if (elem != ELEM_BLOOD) playSfxAtPitched(SynthAudio.BOOM, 0.68f, pipePitch, 0.12f, tx);
                if (channeled) playSfxAtPitched(SynthAudio.PSIONIC, 0.22f, pipePitch, 0.08f, sx);
            }
            case "push", "pull" -> {
                setThrallPose(PixelAtlas.POSE_CAST, 0.56f, ex, nTargets > 0, elem);
                if (nTargets > 0) {
                    spawnFx(FX_RING, tx, ty, 1.4f, 0f, 0.35f, THRALL, elem);
                    spawnFx(FX_PROJ, verb.equals("push") ? sx : tx, verb.equals("push") ? sy : ty,
                            verb.equals("push") ? tx : sx, verb.equals("push") ? ty : sy, 0.22f, THRALL, elem);
                }
                playSfxAtPitched(SynthAudio.PSIONIC, 0.52f, pipePitch, 0.14f, tx);
            }
            case "throw", "shoot" -> {
                setThrallPose(PixelAtlas.POSE_ATTACK, 0.52f, ex, nTargets > 0, elem);
                Color pc = cmd.contains("acid") ? ACID_GLOW : cmd.contains("water") ? ICE_COL
                        : cmd.contains("oil") ? MIST_COL : PROP;
                if (nTargets > 0) spawnFx(FX_PROJ, sx, sy, tx, ty, 0.28f, pc, elem);
                playSfxAt(SynthAudio.SHOOT, 0.56f, 0.14f, tx);
            }
            case "siphon" -> {
                setThrallPose(PixelAtlas.POSE_CAST, 0.64f, ex, nTargets > 0, elem);
                Color sc = elem == ELEM_FORCE ? THRALL : elemColor(elem);
                if (elem == ELEM_BLOOD) {
                    if (nTargets > 0) spawnFx(FX_DRAIN, tx, ty, sx, sy, 0.42f, BLOOD_BRIGHT, elem);
                    spawnFx(FX_RING, sx, sy, 1.0f, 0f, 0.35f, BLOOD_BRIGHT, elem);
                    spawnBurst(tx, ty, 14, BLOOD_BRIGHT, -0.4f);
                } else if (nTargets > 0) {
                    spawnFx(FX_DRAIN, tx, ty, sx, sy, 0.34f, sc, elem);
                    spawnBurst(tx, ty, 10, sc, -0.5f);
                }
                playSfxAtPitched(SynthAudio.SIPHON, 0.58f, pipePitch, 0.08f, tx);
                if (elem == ELEM_FIRE) {
                    playSfxAtPitched(SynthAudio.FIRE, 0.40f, pipePitch * 1.06f, 0.10f, tx);
                } else if (elem == ELEM_ACID) {
                    playSfxAtPitched(SynthAudio.SIZZLE, 0.42f, pipePitch * 1.04f, 0.10f, tx);
                } else if (elem == ELEM_WATER || elem == ELEM_OIL) {
                    playSfxAtPitched(SynthAudio.SPLASH, 0.40f, pipePitch * (elem == ELEM_OIL ? 0.84f : 1.04f), 0.10f, tx);
                } else if (elem == ELEM_BLOOD) {
                    playSfxAtPitched(SynthAudio.SPLASH, 0.42f, pipePitch * 0.74f, 0.10f, tx);
                    playSfxAtPitched(SynthAudio.STING_BLOOD, 0.26f, pipePitch * 1.06f, 0.06f, tx);
                } else {
                    playSfxAtPitched(SynthAudio.PSIONIC, 0.26f, pipePitch * 1.12f, 0.08f, tx);
                }
            }
            case "pour" -> {
                setThrallPose(PixelAtlas.POSE_ATTACK, 0.32f, ex, nTargets > 0, elem);
                Color pc = elem == ELEM_FORCE ? WATER_COL : elemColor(elem);
                if (nTargets > 0) spawnFx(FX_PROJ, sx, sy, tx, ty, 0.25f, pc, elem);
                playSfxAt(elem == ELEM_ACID ? SynthAudio.SIZZLE : SynthAudio.SPLASH, 0.56f, 0.14f, tx);
            }
            case "ignite" -> {
                setThrallPose(PixelAtlas.POSE_CAST, 0.60f, ex, nTargets > 0, ELEM_FIRE);
                spawnFx(FX_RING, tx, ty, 1.2f, 0f, 0.35f, FIRE, ELEM_FIRE);
                spawnBurst(tx, ty, 16, FIRE, -1.0f);
                playSfxAtPitched(SynthAudio.FIRE, 0.62f, pipePitch, 0.16f, tx);
            }
            case "strike" -> {
                setThrallPose(PixelAtlas.POSE_ATTACK, 0.78f, ex, nTargets > 0, elem);
                if (nTargets > 0) {
                    lungeX[0] = (tx - sx) * 0.45f;
                    lungeY[0] = (ty - sy) * 0.45f;
                    lungeAge[0] = 0.72f;
                    lungeMax[0] = 0.72f;
                    spawnBurst(tx, ty, 8, TEXT, -0.3f);
                }
                playSfxAt(SynthAudio.SWING, 0.45f, 0.16f, tx);
            }
            case "channel" -> {
                setThrallPose(PixelAtlas.POSE_CAST, 0.64f, ex, false, elem);
                if (elem == ELEM_BLOOD) {
                    spawnFx(FX_RING, sx, sy, 1.4f, 0f, 0.40f, BLOOD_BRIGHT, elem);
                    spawnBurst(sx, sy, 18, BLOOD_BRIGHT, -0.9f);
                } else {
                    Color cc = elem == ELEM_FORCE ? BOLT_COL : elemColor(elem);
                    spawnFx(FX_RING, sx, sy, 1.1f, 0f, 0.35f, cc, elem);
                }
                playSfxAt(elem == ELEM_BLOOD ? SynthAudio.STING_BLOOD
                        : (elem == ELEM_FIRE ? SynthAudio.FIRE : SynthAudio.PSIONIC),
                        elem == ELEM_BLOOD ? 0.58f : 0.48f,
                        elem == ELEM_BLOOD ? 0f : 0.12f, sx);
            }
            case "guard" -> {
                setThrallPose(PixelAtlas.POSE_GUARD, 0.45f, ex, false, elem);
                playSfxAt(SynthAudio.STEP, 0.30f, 0.14f, sx);
            }
            case "nock", "take", "drop", "wield" -> playSfxAt(SynthAudio.STEP, 0.28f, 0.16f, sx);
            default -> { }
        }
    }

    private void triggerActorBeatVfx(GameEvent.ActorBeat e) {
        if (cur == null || e.lines().isEmpty()) return;
        int ai = cur.indexOf(e.id());
        if (ai <= 0 || ai >= MAX_ENT) return;
        boolean rangedAttack = false, meleeAttack = false;
        for (int i = 0; i < e.lines().size(); i++) {
            String l = e.lines().get(i);
            if (l.contains("'s arrow") || l.contains("fires") || l.contains("shoots")) {
                rangedAttack = true;
                break;
            }
            if (l.contains(" hits ") || l.contains(" misses ")) {
                meleeAttack = true;
            }
        }
        float ax = e.to().x() + 0.5f, ay = e.to().y() + 0.5f;
        float tx = cur.x[0] + 0.5f, ty = cur.y[0] + 0.5f;
        if (rangedAttack) {
            setEntityPose(ai, PixelAtlas.POSE_ATTACK, ARCHER_AIM_TIME + ARCHER_AFTER_TIME);
            curVfxDelay = ARCHER_AIM_TIME;
            spawnFx(FX_PROJ, ax, ay, tx, ty, 0.28f, PROP);
            playSfxAtPitched(SynthAudio.SHOOT, 0.52f, 1f, 0.14f, ax);
            curVfxDelay = 0f;
        } else if (meleeAttack) {
            boolean ogre = PixelAtlas.spriteForEntity(cur, ai) == PixelAtlas.OGRE;
            float poseTime = ogre ? OGRE_ATTACK_POSE_TIME : 0.78f;
            float lungeTime = ogre ? OGRE_ATTACK_LUNGE_TIME : 0.72f;
            int atkPose = (ogre && nextRand01() < 0.5f) ? PixelAtlas.POSE_ATTACK_ALT : PixelAtlas.POSE_ATTACK;
            setEntityPose(ai, atkPose, poseTime);
            lungeX[ai] = (tx - ax) * 0.45f;
            lungeY[ai] = (ty - ay) * 0.45f;
            lungeAge[ai] = lungeTime;
            lungeMax[ai] = lungeTime;
            if (ogre) {
                playSfxAtPitched(SynthAudio.SWING, 0.52f, 0.78f, 0.12f, ax);
                triggerShake(0.14f, 3.0f);
            } else {
                playSfxAt(SynthAudio.SWING, 0.40f, 0.16f, ax);
            }
        }
    }

    @Override
    public void frame(World world, RoundState rs) {
        hp = world.thrall().hp();
        maxHp = world.thrall().maxHp();
        ap = rs.ap();
        roundAp = rs.rules().roundAp();
        round = rs.roundNo();
        scene(new Scene(world), cur);
        presenter.prime(cur);
    }

    @Override
    public void scene(Scene s, Scene p) {
        prev = p;
        cur = s;
        hp = s.hp[0];
        maxHp = s.maxHp[0];
        updateAudioMusicState(s);
        anim = 0;
        prevIdx = new int[s.n];
        if (p == null) {
            java.util.Arrays.fill(entPose, PixelAtlas.POSE_IDLE);
            bloodCount = 0;
            nextBlood = 0;
            snapCameraTo(s.x[0] + 0.5f, s.y[0] + 0.5f);
        }
        float impactDelay = lastPipeDelay > 0f ? lastPipeDelay + 0.10f : 0f;
        int firstDamagedIdx = -1, firstDamagedDelta = 0, firstDamagedSlot = -1, normalHitCount = 0;
        for (int i = 0; i < s.n; i++) {
            prevIdx[i] = p == null ? -1 : p.indexOf(s.id[i]);
            if (i == 0 && prevIdx[0] >= 0 && s.moved(0, p)) {
                int dx = s.x[0] - p.x[prevIdx[0]];
                if (dx != 0) thrallFacing = dx > 0 ? 1 : -1;
                manualPan = false;
                playSfxAt(SynthAudio.STEP, 0.36f, 0.18f, s.x[0] + 0.5f);
            }
            int delta = p == null ? 0 : s.hpDelta(i, p);
            if (delta != 0) {
                int slot = nextFloater;
                floatX[slot] = s.x[i];
                floatY[slot] = s.y[i];
                floatText[slot] = (delta > 0 ? "+" : "") + delta;
                floatAge[slot] = 0;
                nextFloater = (slot + 1) % MAX_FLOATERS;
                if (delta < 0 && p != null) {
                    if (firstDamagedIdx < 0) {
                        firstDamagedIdx = i;
                        firstDamagedDelta = delta;
                        firstDamagedSlot = slot;
                    }
                    if (i < MAX_ENT) hitFlash[i] = 0.28f;
                    Color bloodOrSpark = s.kind[i] == Entity.Kind.CREATURE ? BLEED_COL : PROP;
                    int burstCount = s.kind[i] == Entity.Kind.CREATURE ? (s.hp[i] <= 0 ? 30 : 22) : 14;
                    curVfxDelay = impactDelay;
                    spawnBurst(s.x[i] + 0.5f, s.y[i] + 0.5f, burstCount, bloodOrSpark, -0.4f);
                    int critSlot = consumeCritFor(s.name[i]);
                    if (s.kind[i] == Entity.Kind.CREATURE) {
                        spawnBloodSplats(s.x[i], s.y[i], -delta, critSlot >= 0, s.hp[i] <= 0);
                    }
                    if (critSlot >= 0) {
                        triggerCritImpact(i, critTierQ[critSlot], critElemQ[critSlot], delta, slot);
                    } else if (!(i == 0 && lastImpactElem == ELEM_BLOOD)) {
                        triggerShake(0.16f, -delta >= 10 ? 5f : 2.5f);
                        playNormalHit(s, i, normalHitCount++);
                    }
                    curVfxDelay = 0f;
                } else if (delta > 0 && p != null) {
                    spawnBurst(s.x[i] + 0.5f, s.y[i] + 0.5f, 10, SAGE, -0.7f);
                    playSfxAt(SynthAudio.HEAL, 0.45f, 0f, s.x[i] + 0.5f);
                }
            }
        }
        if (p != null) {
            if (firstDamagedIdx >= 0) {
                curVfxDelay = impactDelay;
                for (int k = 0; k < critCount; k++) {
                    if (critMsg[k] != null) {
                        triggerCritImpact(firstDamagedIdx, critTierQ[k], critElemQ[k], firstDamagedDelta, firstDamagedSlot);
                        break;
                    }
                }
                curVfxDelay = 0f;
            }
            critCount = 0;
            boolean newSteam = false, newFire = false;
            float steamX = 0f, fireX = 0f;
            for (int ty = 0; ty < s.h; ty++) {
                for (int tx = 0; tx < s.w; tx++) {
                    int t = ty * s.w + tx;
                    if (s.newlyBurning(tx, ty, p)) {
                        spawnBurst(tx + 0.5f, ty + 0.5f, 16, FIRE, -1.1f);
                        if (!newFire) { newFire = true; fireX = tx + 0.5f; }
                    }
                    if ((s.steam[t] && !p.steam[t]) || (s.superheatedSteam[t] && !p.superheatedSteam[t]) || (s.oilMist[t] && !p.oilMist[t])) {
                        Color gc = s.superheatedSteam[t] ? SUPERHEATED_COL : s.oilMist[t] ? MIST_COL : STEAM_COL;
                        spawnBurst(tx + 0.5f, ty + 0.5f, 8, gc, -0.4f);
                        if (!newSteam) { newSteam = true; steamX = tx + 0.5f; }
                    }
                }
            }
            if (newSteam) playSfxAt(SynthAudio.SIZZLE, 0.40f, 0.14f, steamX);
            else if (newFire) playSfxAt(SynthAudio.FIRE, 0.38f, 0.16f, fireX);
        }
        lastImpactElem = ELEM_FORCE;
        lastPipeDelay = 0f;
    }

    private void playNormalHit(Scene s, int i, int hitIdx) {
        float tx = s.x[i] + 0.5f;
        boolean isProp = s.kind[i] == Entity.Kind.PROP || s.kind[i] == Entity.Kind.DOOR;
        int baseKind = isProp ? SynthAudio.HIT_PROP : SynthAudio.HIT;
        float basePitch = (i == 0 ? 0.88f : 1.0f) + Math.min(3, hitIdx) * 0.03f;
        float baseVol = Math.max(0.42f, 0.64f - 0.06f * Math.min(3, hitIdx));
        playSfxAtPitched(baseKind, baseVol, basePitch, 0.14f, tx);

        if (s.frozenEntity[i]) {
            playSfxAtPitched(SynthAudio.SHATTER, 0.30f, 1.18f, 0.10f, tx);
        } else if (s.wetEntity[i] || s.oiledEntity[i]) {
            playSfxAtPitched(SynthAudio.SPLASH, 0.28f, 1.10f, 0.12f, tx);
        } else if (s.burningEntity[i]) {
            playSfxAtPitched(SynthAudio.FIRE, 0.26f, 1.05f, 0.12f, tx);
        }

        if (i != 0) {
            if (lastImpactElem == ELEM_FIRE) playSfxAtPitched(SynthAudio.FIRE, 0.28f, 1.02f, 0.12f, tx);
            else if (lastImpactElem == ELEM_ACID) playSfxAtPitched(SynthAudio.SIZZLE, 0.28f, 1.02f, 0.12f, tx);
            else if (lastImpactElem == ELEM_BLOOD) playSfxAtPitched(SynthAudio.SPLASH, 0.34f, 0.78f, 0.10f, tx);
        }

        if (s.hp[i] <= 0) {
            if (isProp || s.frozenEntity[i]) playSfxAtPitched(SynthAudio.SHATTER, 0.56f, 1f, 0.12f, tx);
            else if (s.kind[i] == Entity.Kind.CREATURE) playSfxAtPitched(SynthAudio.STING_KILL, 0.44f, 1f, 0f, tx);
        }
    }

    private void updateAudioMusicState(Scene s) {
        if (s == null || s.maxHp[0] <= 0) return;
        float hpRatio = Math.max(0f, Math.min(1f, s.hp[0] / (float) s.maxHp[0]));
        audio.setCombat(hasVisibleEnemy(s));
        audio.setHpRatio(hpRatio);
    }

    static boolean hasVisibleEnemy(Scene s) {
        if (s == null || s.hp[0] <= 0) return false;
        int ox = s.x[0], oy = s.y[0];
        for (int i = 1; i < s.n; i++) {
            if (s.kind[i] == Entity.Kind.CREATURE && s.hp[i] > 0 && hasLineOfSight(s, ox, oy, s.x[i], s.y[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLineOfSight(Scene s, int ax, int ay, int bx, int by) {
        int dx = Math.abs(bx - ax), dy = Math.abs(by - ay);
        int sx = ax < bx ? 1 : -1, sy = ay < by ? 1 : -1;
        int err = dx - dy, x = ax, y = ay;
        while (x != bx || y != by) {
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
            if (x == bx && y == by) break;
            if (x < 0 || y < 0 || x >= s.w || y >= s.h) return false;
            int t = y * s.w + x;
            if (s.wall[t] || s.steam[t] || s.oilMist[t]) return false;
        }
        return true;
    }

    // ---- VFX & particle helpers ----

    private float nextRand01() {
        vfxRng ^= vfxRng << 13;
        vfxRng ^= vfxRng >>> 17;
        vfxRng ^= vfxRng << 5;
        return (vfxRng & 0x7FFF) / 32767f;
    }

    private void spawnParticle(float tx, float ty, float vx, float vy, float maxAge, float size, Color col) {
        partX[nextPart] = tx;
        partY[nextPart] = ty;
        partVX[nextPart] = vx;
        partVY[nextPart] = vy;
        partAge[nextPart] = -curVfxDelay;
        partMaxAge[nextPart] = maxAge;
        partSize[nextPart] = size;
        partCol[nextPart].set(col);
        nextPart = (nextPart + 1) % MAX_PARTICLES;
    }

    private void spawnBurst(float tx, float ty, int count, Color col, float baseVy) {
        for (int i = 0; i < count; i++) {
            float vx = (nextRand01() - 0.5f) * 2.2f;
            float vy = baseVy + (nextRand01() - 0.5f) * 1.6f;
            float age = 0.25f + nextRand01() * 0.45f;
            float size = 2.5f + nextRand01() * 3.5f;
            spawnParticle(tx + (nextRand01() - 0.5f) * 0.3f, ty + (nextRand01() - 0.5f) * 0.3f, vx, vy, age, size, col);
        }
    }

    private void spawnBloodSplats(int tx, int ty, int dmg, boolean crit, boolean killed) {
        int count = killed ? 6 : (crit || dmg >= 10) ? 4 : 2;
        for (int s = 0; s < count; s++) {
            int slot = nextBlood;
            boolean main = (s == 0);
            float sx = tx + (main ? (0.32f + nextRand01() * 0.36f) : (0.08f + nextRand01() * 0.84f));
            float sy = ty + (main ? (0.38f + nextRand01() * 0.34f) : (0.14f + nextRand01() * 0.76f));
            int gx = MathUtils.floor(sx), gy = MathUtils.floor(sy);
            if (gx < 0 || gx >= cur.w || gy < 0 || gy >= cur.h || cur.wall[gy * cur.w + gx]) {
                sx = MathUtils.clamp(sx, tx + 0.22f, tx + 0.78f);
                sy = MathUtils.clamp(sy, ty + 0.26f, ty + 0.78f);
            }
            bloodX[slot] = sx;
            bloodY[slot] = sy;
            bloodKind[slot] = (nextRand01() < 0.5f) ? 0 : 1;
            bloodRot[slot] = nextRand01() * 360f;
            if (killed) {
                bloodScale[slot] = main ? (0.74f + nextRand01() * 0.24f) : (0.32f + nextRand01() * 0.32f);
            } else if (crit || dmg >= 10) {
                bloodScale[slot] = main ? (0.62f + nextRand01() * 0.26f) : (0.30f + nextRand01() * 0.24f);
            } else {
                bloodScale[slot] = main ? (0.48f + nextRand01() * 0.20f) : (0.28f + nextRand01() * 0.18f);
            }
            bloodShade[slot] = 0.70f + nextRand01() * 0.32f;
            bloodAlpha[slot] = main ? (0.68f + nextRand01() * 0.20f) : (0.56f + nextRand01() * 0.24f);
            nextBlood = (slot + 1) % MAX_BLOOD_SPLATS;
            if (bloodCount < MAX_BLOOD_SPLATS) bloodCount++;
        }
    }

    private void spawnFx(int kind, float x0, float y0, float x1, float y1, float maxAge, Color col) {
        spawnFx(kind, x0, y0, x1, y1, maxAge, col, ELEM_FORCE);
    }

    private void spawnFx(int kind, float x0, float y0, float x1, float y1, float maxAge, Color col, int param) {
        fxKind[nextFx] = kind;
        fxX0[nextFx] = x0;
        fxY0[nextFx] = y0;
        fxX1[nextFx] = x1;
        fxY1[nextFx] = y1;
        fxAge[nextFx] = -curVfxDelay;
        fxMaxAge[nextFx] = maxAge;
        fxParam[nextFx] = param;
        fxCol[nextFx].set(col);
        nextFx = (nextFx + 1) % MAX_FX;
    }

    private float updateEffects(float dt) {
        if (critFlashTime > 0) critFlashTime = Math.max(0f, critFlashTime - dt);
        if (shakeTime > 0) {
            shakeTime = Math.max(0f, shakeTime - dt);
            float mag = shakeMag * (shakeTime / Math.max(0.01f, shakeMaxTime));
            shakeX = MathUtils.sin(time * 95f) * mag;
            shakeY = MathUtils.cos(time * 113f) * mag;
        } else {
            shakeX = 0f;
            shakeY = 0f;
            shakeMag = 0f;
        }

        float simDt = dt;
        if (hitStop > 0) {
            float used = Math.min(hitStop, dt);
            hitStop -= used;
            simDt = dt - used;
        }
        if (simDt <= 0f) return 0f;

        for (int i = 0; i < MAX_FLOATERS; i++) floatAge[i] += simDt;
        for (int i = 0; i < MAX_ENT; i++) {
            if (hitFlash[i] > 0) hitFlash[i] = Math.max(0f, hitFlash[i] - simDt);
            if (lungeAge[i] > 0) lungeAge[i] = Math.max(0f, lungeAge[i] - simDt);
            if (entPose[i] != PixelAtlas.POSE_IDLE) {
                entPoseAge[i] += simDt;
                if (entPoseAge[i] >= entPoseMax[i]) entPose[i] = PixelAtlas.POSE_IDLE;
            }
        }
        if (thrallPose != PixelAtlas.POSE_IDLE) {
            thrallPoseAge += simDt;
            if (thrallPoseAge >= thrallPoseMax) thrallPose = PixelAtlas.POSE_IDLE;
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (partAge[i] < partMaxAge[i]) {
                partAge[i] += simDt;
                if (partAge[i] >= 0f) {
                    partX[i] += partVX[i] * simDt;
                    partY[i] += partVY[i] * simDt;
                }
            }
        }
        for (int i = 0; i < MAX_FX; i++) {
            if (fxAge[i] < fxMaxAge[i]) fxAge[i] += simDt;
        }

        ambientTimer += simDt;
        if (ambientTimer >= 0.09f && cur != null) {
            ambientTimer = 0f;
            emitAmbientParticles();
        }
        return simDt;
    }

    private void emitAmbientParticles() {
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                if (cur.burning[t] && nextRand01() < 0.45f) {
                    Color fc = nextRand01() < 0.4f ? EMBER : FIRE;
                    spawnParticle(x + 0.2f + nextRand01() * 0.6f, y + 0.3f + nextRand01() * 0.4f,
                            (nextRand01() - 0.5f) * 0.3f, -0.8f - nextRand01() * 0.6f, 0.55f, 4.5f, fc);
                } else if ((cur.steam[t] || cur.superheatedSteam[t] || cur.oilMist[t]) && nextRand01() < 0.22f) {
                    Color gc = cur.superheatedSteam[t] ? SUPERHEATED_COL : cur.oilMist[t] ? MIST_COL : STEAM_COL;
                    spawnParticle(x + 0.15f + nextRand01() * 0.7f, y + 0.2f + nextRand01() * 0.6f,
                            (nextRand01() - 0.5f) * 0.25f, -0.35f, 0.75f, 6f, gc);
                } else if (cur.acid[t] && nextRand01() < 0.14f) {
                    spawnParticle(x + 0.2f + nextRand01() * 0.6f, y + 0.3f + nextRand01() * 0.4f,
                            0f, -0.45f, 0.45f, 4f, ACID_GLOW);
                }
            }
        }
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            if (cur.burningEntity[i] && nextRand01() < 0.6f) {
                spawnParticle(cur.x[i] + 0.3f + nextRand01() * 0.4f, cur.y[i] + 0.4f,
                        (nextRand01() - 0.5f) * 0.3f, -0.9f, 0.5f, 4.5f, EMBER);
            } else if (cur.frozenEntity[i] && nextRand01() < 0.25f) {
                spawnParticle(cur.x[i] + 0.2f + nextRand01() * 0.6f, cur.y[i] + 0.2f + nextRand01() * 0.6f,
                        0f, -0.25f, 0.5f, 4f, ICE_COL);
            } else if (cur.bleedingEntity[i] && nextRand01() < 0.25f) {
                spawnParticle(cur.x[i] + 0.3f + nextRand01() * 0.4f, cur.y[i] + 0.5f,
                        0f, 0.45f, 0.4f, 4f, BLEED_COL);
            }
        }
    }

    // ---- frame loop ----

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        time += dt;
        blinkTime += dt;
        anim += updateEffects(dt);
        audio.updateMusic(dt);

        presenter.update(dt);
        if (dirty && !presenter.busy()) {
            frame(game.world, game.round);
            dirty = false;
        }
        updateCamera(dt);
        logScroll = clampScroll(logScroll, log.size(), logLines());
        if (maxScroll() == 0) {
            draggingScroll = false;
            hoverScroll = false;
        }
        if (scrollLines() == 0) hoverJump = false;

        ScreenUtils.clear(BG);
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);

        drawPanelShapes();
        drawSpriteScene();
        drawLineOverlays();
        drawText();
    }

    static float clampCam(float v, int mapTiles, float viewTiles) {
        return MathUtils.clamp(v, 0f, Math.max(0f, mapTiles - viewTiles));
    }

    static boolean hasLitTorch(Scene s) {
        return s != null && (s.leftHand.contains("[LIT]") || s.rightHand.contains("[LIT]"));
    }

    private static float torchAnchorX(int spr) {
        return spr == PixelAtlas.THRALL_ATTACK ? 0.178f : 0.68f;
    }

    private static float torchAnchorY(int spr) {
        return spr == PixelAtlas.THRALL_ATTACK ? 0.40f : 0.185f;
    }

    private void snapCameraTo(float fx, float fy) {
        if (cur == null) return;
        float vtx = viewTilesX(), vty = viewTilesY();
        camX = clampCam(fx - vtx * 0.5f, cur.w, vtx);
        camY = clampCam(fy - vty * 0.5f, cur.h, vty);
    }

    private void updateCamera(float dt) {
        if (cur == null || manualPan || draggingMap) return;
        float vtx = viewTilesX(), vty = viewTilesY();
        float fx = drawX(0) + 0.5f, fy = drawY(0) + 0.5f;
        if (!presenter.busy() && input.length() > 0) {
            int nT = findTargets(input, cur, targetX, targetY, targetX.length);
            if (nT > 0) {
                float tx = targetX[0] + 0.5f, ty = targetY[0] + 0.5f;
                if (Math.abs(tx - fx) <= vtx - 1f && Math.abs(ty - fy) <= vty - 1f) {
                    fx = (fx + tx) * 0.5f;
                    fy = (fy + ty) * 0.5f;
                } else {
                    fx = tx;
                    fy = ty;
                }
            }
        } else if (!actingId.isEmpty()) {
            int ai = cur.indexOf(actingId);
            if (ai >= 0 && cur.hp[ai] > 0) {
                fx = drawX(ai) + 0.5f;
                fy = drawY(ai) + 0.5f;
            }
        }
        float goalX = clampCam(fx - vtx * 0.5f, cur.w, vtx);
        float goalY = clampCam(fy - vty * 0.5f, cur.h, vty);
        float alpha = 1f - (float) Math.exp(-8f * dt);
        camX += (goalX - camX) * alpha;
        camY += (goalY - camY) * alpha;
    }

    private boolean inMap(float x, float y) {
        int mw = mapW(), mh = mapH(), mapBottom = viewH - MAP_Y - mh;
        return x >= MAP_X && x <= MAP_X + mw && y >= mapBottom && y <= mapBottom + mh;
    }

    private void beginMapScissor() {
        int mw = mapW(), mh = mapH();
        float scaleX = viewport.getScreenWidth() / (float) viewW;
        float scaleY = viewport.getScreenHeight() / (float) viewH;
        int sx = viewport.getScreenX() + Math.round(MAP_X * scaleX);
        int sy = viewport.getScreenY() + Math.round((viewH - MAP_Y - mh) * scaleY);
        int sw = Math.max(1, Math.round(mw * scaleX));
        int sh = Math.max(1, Math.round(mh * scaleY));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        HdpiUtils.glScissor(sx, sy, sw, sh);
    }

    private void endMapScissor() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private float mapScreenX(float tx) { return MAP_X + (tx - camX) * TILE + shakeX; }

    private float mapScreenY(float ty) { return viewH - MAP_Y - (ty - camY + 1f) * TILE + shakeY; }

    private float centerScreenY(float ty) { return viewH - MAP_Y - (ty - camY) * TILE + shakeY; }

    private int inputTop() { return viewH - PAD_B - INPUT_H; }

    private int mapW() { return Math.max(TILE, viewW - MAP_X - SIDE_W - GAP - PAD_R); }

    private int mapH() { return Math.max(TILE, inputTop() - GAP - logHeight() - GAP - MAP_Y); }

    private float viewTilesX() { return mapW() / (float) TILE; }

    private float viewTilesY() { return mapH() / (float) TILE; }

    private int sideX() { return MAP_X + mapW() + GAP; }

    private int sideW() { return SIDE_W; }

    private int logTop() { return MAP_Y + mapH() + GAP; }

    private int logW() { return viewW - MAP_X - PAD_R; }

    private int logHeight() { return Math.max(124, Math.min(180, Math.round(viewH * 0.19f))); }

    private int logY() { return viewH - logTop() - logHeight(); }

    private int logLines() { return (logHeight() - 12) / LINE_H; }

    private int maxScroll() { return Math.max(0, log.size() - logLines()); }

    private int scrollLines() { return Math.round(logScroll); }

    private float trackX() { return MAP_X + logW() - SCROLLBAR_W - 5; }

    private float trackY() { return logY() + 5; }

    private float trackH() { return logHeight() - 10; }

    private float thumbH() { return thumbSize(trackH(), log.size(), logLines()); }

    private float thumbY() {
        int max = maxScroll();
        return max == 0 ? trackY() : trackY() + (trackH() - thumbH()) * (logScroll / max);
    }

    private void setLogScroll(float target) {
        logScroll = clampScroll(target, log.size(), logLines());
    }

    private void dragScrollbar(float worldY) {
        setLogScroll(scrollFromThumb(worldY - dragOffset, trackY(), trackH(), thumbH(), maxScroll()));
    }

    private boolean inScrollbar(float x, float y) {
        return x >= trackX() - 8 && x <= MAP_X + logW() && y >= logY() && y <= logY() + logHeight();
    }

    private String jumpLabel() {
        int s = scrollLines();
        if (s != lastJumpLines) {
            lastJumpLines = s;
            jumpLabel = "-" + s + " lines | End";
        }
        return jumpLabel;
    }

    private float jumpPillW() { return jumpLabel().length() * charW + 16; }

    private float jumpPillX() { return trackX() - 10 - jumpPillW(); }

    private float jumpPillY() { return logY() + 5; }

    private boolean inJumpPill(float x, float y) {
        float px = jumpPillX(), py = jumpPillY();
        return x >= px && x <= px + jumpPillW() && y >= py && y <= py + JUMP_PILL_H;
    }

    static float clampScroll(float scroll, int total, int visible) {
        return MathUtils.clamp(scroll, 0f, Math.max(0, total - visible));
    }

    static float thumbSize(float trackH, int total, int visible) {
        return Math.max(20f, trackH * visible / Math.max(1, total));
    }

    static float scrollFromThumb(float thumbBottom, float trackY, float trackH, float thumbH, int maxScroll) {
        float span = Math.max(1f, trackH - thumbH);
        float frac = MathUtils.clamp((thumbBottom - trackY) / span, 0f, 1f);
        return frac * maxScroll;
    }

    /** Zero-allocation parser extracting live target tile coordinates from a partial/full command. */
    static int findTargets(CharSequence cmd, Scene s, int[] outX, int[] outY, int max) {
        if (cmd == null || s == null || max <= 0) return 0;
        int len = cmd.length(), found = 0, i = 0;
        while (i < len && found < max) {
            while (i < len && isSep(cmd.charAt(i))) i++;
            if (i >= len) break;
            int start = i;
            while (i < len && !isSep(cmd.charAt(i))) i++;
            int end = i;
            if (cmd.charAt(start) == '-') continue;

            int tx = -1, ty = -1;
            if (cmd.charAt(start) == '@') {
                int slash = start + 1;
                while (slash < end && cmd.charAt(slash) != '/') slash++;
                int comma = -1;
                for (int k = start + 1; k < slash; k++) {
                    if (cmd.charAt(k) == ',') { comma = k; break; }
                }
                if (comma > start + 1 && comma + 1 < slash) {
                    int px = parseNonNegInt(cmd, start + 1, comma);
                    int py = parseNonNegInt(cmd, comma + 1, slash);
                    if (px >= 0 && px < s.w && py >= 0 && py < s.h) {
                        tx = px;
                        ty = py;
                    }
                } else {
                    int ei = matchEntityId(cmd, start + 1, slash, s);
                    if (ei >= 0) { tx = s.x[ei]; ty = s.y[ei]; }
                }
            } else {
                int ei = matchEntityId(cmd, start, end, s);
                if (ei >= 0) { tx = s.x[ei]; ty = s.y[ei]; }
            }

            if (tx >= 0) {
                boolean dup = false;
                for (int d = 0; d < found; d++) {
                    if (outX[d] == tx && outY[d] == ty) { dup = true; break; }
                }
                if (!dup) {
                    outX[found] = tx;
                    outY[found] = ty;
                    found++;
                }
            }
        }
        return found;
    }

    private static boolean isSep(char c) {
        return c <= ' ' || c == '|' || c == ';' || c == '&';
    }

    private static int parseNonNegInt(CharSequence s, int from, int to) {
        int v = 0;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
            if (v > 999) return -1;
        }
        return v;
    }

    private static int matchEntityId(CharSequence cmd, int from, int to, Scene s) {
        int len = to - from;
        if (len <= 0) return -1;
        for (int i = 0; i < s.n; i++) {
            if (s.hp[i] <= 0) continue;
            String eid = s.id[i];
            if (eid.length() == len && equalsIgnoreCaseRange(cmd, from, eid)) return i;
        }
        return -1;
    }

    private static boolean equalsIgnoreCaseRange(CharSequence a, int from, String b) {
        for (int i = 0; i < b.length(); i++) {
            char ca = a.charAt(from + i), cb = b.charAt(i);
            if (ca != cb && Character.toLowerCase(ca) != Character.toLowerCase(cb)) return false;
        }
        return true;
    }

    private static String numStr(int v) {
        return v >= 0 && v < NUMS.length ? NUMS[v] : Integer.toString(v);
    }

    private static String firstWord(String cmd) {
        int i = 0, len = cmd.length();
        while (i < len && cmd.charAt(i) <= ' ') i++;
        int s = i;
        while (i < len && cmd.charAt(i) > ' ') i++;
        return cmd.substring(s, i).toLowerCase();
    }

    private static Color colorOf(Scene s, int i) {
        return i == 0 ? THRALL : switch (s.kind[i]) {
            case CREATURE -> CREATURE;
            case PROP -> PROP;
            case DOOR -> DOOR;
        };
    }

    private static Color groupColor(int g) {
        return switch (g) {
            case G_PLAYER -> THRALL;
            case G_HOSTILE -> CREATURE;
            case G_ENV -> PROP;
            default -> BORDER;
        };
    }

    static float lungeFactor(float ageLeft, float maxDur, boolean holdAttack) {
        if (ageLeft <= 0f || maxDur <= 0f) return 0f;
        float u = MathUtils.clamp(1f - ageLeft / maxDur, 0f, 1f);
        if (!holdAttack) return MathUtils.sin(u * MathUtils.PI);
        if (u < 0.16f) return Interpolation.pow2Out.apply(u / 0.16f);
        if (u < 0.68f) return 1f;
        return 1f - Interpolation.smooth.apply((u - 0.68f) / 0.32f);
    }

    private float fireWarmthAt(float wx, float wy) {
        if (cur == null) return 0f;
        float w = 0f;
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                if (!cur.burning[y * cur.w + x]) continue;
                float dx = wx - (x + 0.5f), dy = wy - (y + 0.5f);
                w += 0.85f / (1f + 0.48f * (dx * dx + dy * dy));
            }
        }
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0 || !cur.litEntity[i]) continue;
            float power = (i == 0 && hasLitTorch(cur)) ? 0.70f : 0.95f;
            float dx = wx - (cur.x[i] + 0.5f), dy = wy - (cur.y[i] + 0.5f);
            w += power / (1f + 0.48f * (dx * dx + dy * dy));
        }
        return Math.min(1f, w);
    }

    private float drawX(int i) {
        int p = prevIdx[i];
        float base = (p < 0 || anim >= SLIDE) ? cur.x[i]
                : prev.x[p] + (cur.x[i] - prev.x[p]) * Interpolation.smooth.apply(anim / SLIDE);
        if (i < MAX_ENT && lungeAge[i] > 0) {
            base += lungeX[i] * lungeFactor(lungeAge[i], lungeMax[i], lungeMax[i] > 0.4f);
        }
        return base;
    }

    private float drawY(int i) {
        int p = prevIdx[i];
        float base = (p < 0 || anim >= SLIDE) ? cur.y[i]
                : prev.y[p] + (cur.y[i] - prev.y[p]) * Interpolation.smooth.apply(anim / SLIDE);
        if (i < MAX_ENT && lungeAge[i] > 0) {
            base += lungeY[i] * lungeFactor(lungeAge[i], lungeMax[i], lungeMax[i] > 0.4f);
        }
        return base;
    }

    private float rowMidY(int j) {
        return viewH - (logTop() + 7 + j * LINE_H) - fontH / 2f;
    }

    private int cardEnd(int first, int visible, int j0) {
        int b = log.get(first + j0).block;
        int j1 = j0;
        while (j1 + 1 < visible && first + j1 + 1 < log.size() && log.get(first + j1 + 1).block == b) {
            j1++;
        }
        return j1;
    }

    private void drawPanelShapes() {
        int logW = logW(), logY = logY(), mw = mapW(), mh = mapH(), inTop = inputTop();
        int sideY = viewH - MAP_Y - mh, inputY = viewH - inTop - INPUT_H;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(sideX(), sideY, sideW(), mh);
        shapes.rect(MAP_X, logY, logW, logHeight());
        shapes.rect(MAP_X, inputY, logW, INPUT_H);
        shapes.setColor(THRALL.r, THRALL.g, THRALL.b, presenter.busy() ? 0.25f : 0.85f);
        shapes.rect(MAP_X, inputY, 3, INPUT_H);
        if (!presenter.busy() && ((int) (blinkTime * 2.5f)) % 2 == 0) {
            float promptTop = inTop + (INPUT_H - fontH) / 2f;
            shapes.setColor(THRALL);
            shapes.rect(MAP_X + 12 + (8 + cursor) * charW, viewH - promptTop - fontH - 1, 2, fontH + 2);
        }

        int total = log.size(), visible = logLines();
        if (total > visible) {
            shapes.setColor(BAR_BG);
            shapes.rect(trackX(), trackY(), SCROLLBAR_W, trackH());
            shapes.setColor(draggingScroll ? THRALL : hoverScroll ? TEXT : MUTED);
            shapes.rect(trackX(), thumbY(), SCROLLBAR_W, thumbH());
        }

        // HUD status bars
        float hudX = sideX() + 12, barX = hudX + 26, barW = sideW() - 114;
        float hpBarY = viewH - (MAP_Y + 38) - (hudFontH + 8) / 2f;
        float apBarY = viewH - (MAP_Y + 58) - (hudFontH + 8) / 2f;
        shapes.setColor(BAR_BG);
        shapes.rect(barX, hpBarY, barW, 8);
        shapes.setColor(hp * 3 <= maxHp ? CREATURE : THRALL);
        shapes.rect(barX, hpBarY, barW * MathUtils.clamp(hp / (float) Math.max(1, maxHp), 0f, 1f), 8);
        int maxPips = Math.max(roundAp, ap);
        if (maxPips <= 10) {
            float pipStep = barW / Math.max(1, maxPips);
            float pipW = Math.max(4f, pipStep - 3f);
            for (int p = 0; p < maxPips; p++) {
                shapes.setColor(p < ap ? THRALL : BAR_BG);
                shapes.rect(barX + p * pipStep, apBarY, pipW, 8);
            }
        } else {
            shapes.setColor(BAR_BG);
            shapes.rect(barX, apBarY, barW, 8);
            shapes.setColor(THRALL);
            shapes.rect(barX, apBarY, barW * MathUtils.clamp(ap / (float) maxPips, 0f, 1f), 8);
        }
        shapes.setColor(BORDER);
        shapes.rect(sideX() + 10, viewH - (MAP_Y + 134), sideW() - 20, 1);
        shapes.rect(sideX() + 10, viewH - (MAP_Y + 202), sideW() - 20, 1);

        // Structured log cards & banners
        int scroll = scrollLines();
        int first = Math.max(0, log.size() - visible - scroll);
        for (int j = 0; j < visible && first + j < log.size(); ) {
            LogRow r = log.get(first + j);
            if (r.block == 0) { j++; continue; }
            int j1 = cardEnd(first, visible, j);
            float topY = rowMidY(j) + LINE_H / 2f - 1;
            float botY = rowMidY(j1) - LINE_H / 2f + 1;
            float dim = r.round < round ? 0.52f : 1f;
            Color gc = groupColor(r.group);
            shapes.setColor(gc.r, gc.g, gc.b, 0.055f * dim);
            shapes.rect(MAP_X + 6, botY, logW - 22, topY - botY);
            shapes.setColor(gc.r, gc.g, gc.b, 0.85f * dim);
            shapes.rect(MAP_X + 6, botY, 3, topY - botY);
            j = j1 + 1;
        }
        for (int j = 0; j < visible && first + j < log.size(); j++) {
            LogRow r = log.get(first + j);
            float dim = r.round < round ? 0.52f : 1f;
            float midY = rowMidY(j);
            float boxY = midY - (LINE_H - 2) / 2f;

            if (r.kind == K_ROUND) {
                shapes.setColor(BORDER.r, BORDER.g, BORDER.b, 0.55f * dim);
                shapes.rect(MAP_X + 6, boxY, logW - 22, LINE_H - 2);
                shapes.setColor(THRALL.r, THRALL.g, THRALL.b, dim);
                shapes.rect(MAP_X + 6, boxY, 4, LINE_H - 2);
            } else if (r.kind == K_PHASE) {
                shapes.setColor(CREATURE.r, CREATURE.g, CREATURE.b, 0.14f * dim);
                shapes.rect(MAP_X + 6, boxY, logW - 22, LINE_H - 2);
                shapes.setColor(CREATURE.r, CREATURE.g, CREATURE.b, dim);
                shapes.rect(MAP_X + 6, boxY, 3, LINE_H - 2);
            } else if (r.kind == K_CMD) {
                shapes.setColor(THRALL.r, THRALL.g, THRALL.b, 0.08f * dim);
                shapes.rect(MAP_X + 9, boxY, logW - 25, LINE_H - 2);
            }
            if (r.badge != null) {
                float pillW = r.badge.length() * charW + 14;
                float pillX = MAP_X + logW - 24 - pillW;
                Color bc = r.badgeColor;
                shapes.setColor(bc.r, bc.g, bc.b, 0.16f * dim);
                shapes.rect(pillX, midY - (LINE_H - 6) / 2f, pillW, LINE_H - 6);
            }
        }
        if (scroll > 0) {
            shapes.setColor(BAR_BG);
            shapes.rect(jumpPillX(), jumpPillY(), jumpPillW(), JUMP_PILL_H);
        }
        shapes.end();
    }

    private void updateThrallTorchPos(int animFrame) {
        float px = mapScreenX(drawX(0)), py = mapScreenY(drawY(0));
        float breathe = !cur.frozenEntity[0] ? MathUtils.sin(time * 3.4f) : 0f;
        float sxScale = 1f - breathe * 0.014f;
        float syScale = 1f + breathe * 0.022f;
        int p0 = prevIdx[0];
        float hopY = (p0 >= 0 && cur.moved(0, prev) && anim < SLIDE)
                ? MathUtils.sin((anim / SLIDE) * MathUtils.PI) * 10f : 0f;
        int spr = thrallDrawSprite(animFrame);
        if (spr == PixelAtlas.THRALL_ATTACK) {
            sxScale *= THRALL_ATTACK_SCALE;
            syScale *= THRALL_ATTACK_SCALE;
            hopY -= 0.025f * TILE;
        }
        int faceSign = thrallFacing * PixelAtlas.naturalFacing(spr);
        tmpTorch[0] = px + TILE * 0.5f + faceSign * ((torchAnchorX(spr) - 0.5f) * TILE * sxScale);
        tmpTorch[1] = py + hopY + (1f - torchAnchorY(spr)) * TILE * syScale;
    }

    private void renderLightMap(boolean thrallTorchLit) {
        int mw = mapW(), mh = mapH();
        lightCam.setToOrtho(false, mw, mh);
        lightCam.position.set(MAP_X + mw * 0.5f, viewH - MAP_Y - mh * 0.5f, 0f);
        lightCam.update();

        lightFbo.begin();
        Gdx.gl.glClearColor(0.36f, 0.37f, 0.40f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(lightCam.combined);
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        if (thrallTorchLit && !cur.proneEntity[0]) {
            float flicker = 0.92f + 0.08f * MathUtils.sin(time * 11.5f);
            float tx = tmpTorch[0], ty = tmpTorch[1] + 0.06f * TILE;
            float outer = TILE * 4.6f, inner = TILE * 2.1f;
            batch.setColor(0.76f, 0.62f, 0.42f, 0.72f * flicker);
            batch.draw(atlas.glow, tx - outer * 0.5f, ty - outer * 0.5f, outer, outer);
            batch.setColor(0.45f, 0.38f, 0.28f, 0.48f * flicker);
            batch.draw(atlas.glow, tx - inner * 0.5f, ty - inner * 0.5f, inner, inner);
        }
        if (cur.concentration > 0 && cur.hp[0] > 0) {
            float cx = mapScreenX(drawX(0)) + TILE * 0.5f, cy = mapScreenY(drawY(0)) + TILE * 0.5f;
            float rad = TILE * 3.4f;
            batch.setColor(0.42f, 0.62f, 0.68f, 0.48f);
            batch.draw(atlas.glow, cx - rad * 0.5f, cy - rad * 0.5f, rad, rad);
        }
        for (int i = 1; i < cur.n; i++) {
            if (cur.hp[i] <= 0 || !cur.litEntity[i]) continue;
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            float flicker = 0.92f + 0.08f * MathUtils.sin(time * 11.5f + i * 1.9f);
            if (cur.kind[i] == Entity.Kind.PROP && cur.glyph[i] == 'i') {
                float cx = px + TILE * 0.5f, cy = py + TILE * 0.64f * BRAZIER_SCALE;
                float outer = TILE * 5.6f, inner = TILE * 2.5f;
                batch.setColor(0.84f, 0.64f, 0.38f, 0.80f * flicker);
                batch.draw(atlas.glow, cx - outer * 0.5f, cy - outer * 0.5f, outer, outer);
                batch.setColor(0.52f, 0.40f, 0.24f, 0.52f * flicker);
                batch.draw(atlas.glow, cx - inner * 0.5f, cy - inner * 0.5f, inner, inner);
            } else {
                float outer = TILE * 4.2f;
                batch.setColor(0.76f, 0.58f, 0.36f, 0.68f * flicker);
                batch.draw(atlas.glow, px + TILE * 0.5f - outer * 0.5f, py + TILE * 0.5f - outer * 0.5f, outer, outer);
            }
        }
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                if (!cur.burning[y * cur.w + x]) continue;
                float cx = mapScreenX(x) + TILE * 0.5f, cy = mapScreenY(y) + TILE * 0.36f;
                float flicker = 0.92f + 0.08f * MathUtils.sin(time * 11.5f + x * 1.9f + y * 2.3f);
                float outer = TILE * 4.0f;
                batch.setColor(0.80f, 0.56f, 0.32f, 0.72f * flicker);
                batch.draw(atlas.glow, cx - outer * 0.5f, cy - outer * 0.5f, outer, outer);
            }
        }
        for (int i = 0; i < MAX_FX; i++) {
            if (fxKind[i] != FX_FIREBALL || fxAge[i] < 0f || fxAge[i] >= fxMaxAge[i]) continue;
            float u = fxAge[i] / fxMaxAge[i];
            float cx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
            float cy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
            float outer = TILE * 3.2f;
            batch.setColor(0.85f, 0.56f, 0.28f, 0.75f);
            batch.draw(atlas.glow, cx - outer * 0.5f, cy - outer * 0.5f, outer, outer);
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.end();
        lightFbo.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }

    private void drawEntityAccents(int k, boolean additive) {
        int i = drawOrder[k];
        if (cur.kind[i] != Entity.Kind.CREATURE || cur.proneEntity[i]) return;
        int spr = entSpr[k];
        float[] eyes = PixelAtlas.entityEyes(spr);
        float[] weapons = PixelAtlas.weaponGlows(spr);
        if (eyes.length == 0 && weapons.length == 0) return;

        float dim = cur.frozenEntity[i] ? 0.45f : 1f;
        float eScale = entScale[k], sx = entSx[k], sy = entSy[k], rot = entRot[k];
        float ox = entPx[k] + TILE * 0.5f, oy = entPy[k];
        float c = MathUtils.cosDeg(rot), s = MathUtils.sinDeg(rot);
        int face = entFace[k];

        if (weapons.length > 0) {
            float bladePulse = (0.84f + 0.16f * MathUtils.sin(time * 3.1f + i * 1.7f)) * dim;
            float alpha = additive
                    ? (spr == PixelAtlas.GOBLIN_ASSASSIN_ATTACK ? 0.40f : 0.28f) * bladePulse
                    : 0.28f * bladePulse;
            if (additive) batch.setColor(0.95f, 0.14f, 0.16f, alpha);
            else batch.setColor(0.88f, 0.08f, 0.12f, alpha);
            for (int w = 0; w + 4 < weapons.length; w += 5) {
                float lx0 = face * (weapons[w] - 0.5f) * TILE * sx, ly0 = (1f - weapons[w + 1]) * TILE * sy;
                float lx1 = face * (weapons[w + 2] - 0.5f) * TILE * sx, ly1 = (1f - weapons[w + 3]) * TILE * sy;
                float x0 = ox + lx0 * c - ly0 * s, y0 = oy + lx0 * s + ly0 * c;
                float x1 = ox + lx1 * c - ly1 * s, y1 = oy + lx1 * s + ly1 * c;
                float dx = x1 - x0, dy = y1 - y0;
                float len = Math.max(4f, (float) Math.hypot(dx, dy));
                float ang = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                float gw = len * (additive ? 1.28f : 1.15f);
                float gh = weapons[w + 4] * TILE * eScale * (additive ? 2.1f : 1.15f);
                batch.draw(atlas.glow, (x0 + x1 - gw) * 0.5f, (y0 + y1 - gh) * 0.5f,
                        gw * 0.5f, gh * 0.5f, gw, gh, 1f, 1f, ang);
            }
        }

        if (eyes.length > 0) {
            boolean thrall = (i == 0);
            float eyePulse = (0.88f + 0.12f * MathUtils.sin(time * 4.2f + i * 2.1f)) * dim;
            boolean arcaneActive = thrall
                    && (cur.concentration > 0 || (thrallPose == PixelAtlas.POSE_CAST && thrallPoseAge < thrallPoseMax));
            for (int e = 0; e + 1 < eyes.length; e += 2) {
                float lx = face * (eyes[e] - 0.5f) * TILE * sx, ly = (1f - eyes[e + 1]) * TILE * sy;
                float ex = ox + lx * c - ly * s, ey = oy + lx * s + ly * c;
                if (additive) {
                    float bloom = 0.068f * TILE * eScale;
                    if (thrall) batch.setColor(0.44f, 0.78f, 0.96f, (arcaneActive ? 0.38f : 0.30f) * eyePulse);
                    else batch.setColor(0.92f, 0.16f, 0.18f, 0.26f * eyePulse);
                    batch.draw(atlas.glow, ex - bloom * 0.5f, ey - bloom * 0.5f, bloom, bloom);
                } else {
                    float core = 0.028f * TILE * eScale;
                    if (thrall) batch.setColor(0.36f, 0.72f, 0.92f, 0.46f * dim);
                    else batch.setColor(0.88f, 0.14f, 0.16f, 0.48f * dim);
                    batch.draw(atlas.glow, ex - core * 0.5f, ey - core * 0.5f, core, core);
                }
            }
        }
    }

    private void drawSpriteScene() {
        int animFrame = ((int) (time * 6.0f)) & 3;
        boolean thrallTorchLit = hasLitTorch(cur);
        if (thrallTorchLit && !cur.proneEntity[0]) updateThrallTorchPos(animFrame);
        renderLightMap(thrallTorchLit);

        batch.begin();
        beginMapScissor();

        // 1. Dungeon tiles (procedural variation), faded selective-color liquids, loot
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                float sx = mapScreenX(x), sy = mapScreenY(y);
                batch.setColor(Color.WHITE);

                if (cur.wall[t]) {
                    boolean front = y + 1 < cur.h && !cur.wall[(y + 1) * cur.w + x];
                    batch.draw(atlas.regions[front ? PixelAtlas.WALL_FRONT : PixelAtlas.WALL_TOP], sx, sy, TILE, TILE);
                } else {
                    int h = (x * 73856093) ^ (y * 19349663) ^ ((x + y * 31) * 83492791);
                    h ^= (h >>> 13);
                    float shade = 0.86f + (((h >>> 5) & 31) / 31f) * 0.14f;
                    float tint = ((((h >>> 10) & 15) / 15f) - 0.5f) * 0.045f;
                    int texIdx = (h & 1) == 0 ? PixelAtlas.FLOOR_0 : PixelAtlas.FLOOR_1;
                    float rot = ((h >>> 1) & 3) * 90f;
                    float fxS = ((h >>> 3) & 1) == 0 ? 1f : -1f;
                    float fyS = ((h >>> 4) & 1) == 0 ? 1f : -1f;
                    batch.setColor(Math.min(1f, shade + tint), shade, Math.min(1f, shade - tint), 1f);
                    batch.draw(atlas.regions[texIdx], sx, sy, TILE * 0.5f, TILE * 0.5f, TILE, TILE, fxS, fyS, rot);
                    if (((h >>> 14) & 7) < 4) {
                        float ow = (0.36f + (((h >>> 23) & 7) / 7f) * 0.32f) * TILE;
                        float oh = (0.36f + (((h >>> 26) & 7) / 7f) * 0.32f) * TILE;
                        float ox = 0.06f * TILE + (((h >>> 17) & 7) / 7f) * (0.88f * TILE - ow);
                        float oy = 0.06f * TILE + (((h >>> 20) & 7) / 7f) * (0.88f * TILE - oh);
                        batch.setColor(0f, 0f, 0f, 0.06f + (((h >>> 29) & 7) / 7f) * 0.10f);
                        batch.draw(atlas.glow, sx + ox, sy + oy, ow, oh);
                    }
                    if (y > 0 && cur.wall[(y - 1) * cur.w + x]) {
                        batch.setColor(0f, 0f, 0f, 0.36f);
                        batch.draw(atlas.white, sx, sy + TILE - 20, TILE, 20);
                        batch.setColor(0f, 0f, 0f, 0.16f);
                        batch.draw(atlas.white, sx, sy + TILE - 32, TILE, 12);
                    }
                    if (cur.ice[t]) {
                        batch.setColor(ICE_COL.r, ICE_COL.g, ICE_COL.b, 0.85f);
                        batch.draw(atlas.regions[PixelAtlas.ICE_TILE], sx, sy, TILE, TILE);
                        if (cur.tar[t]) {
                            batch.setColor(MIST_COL.r * 0.48f, MIST_COL.g * 0.48f, MIST_COL.b * 0.48f, 0.90f);
                            batch.draw(atlas.regions[PixelAtlas.TAR_TILE], sx + 16, sy + 16, TILE - 32, TILE - 32);
                        } else if (cur.acid[t]) {
                            batch.setColor(ACID_GLOW.r, ACID_GLOW.g, ACID_GLOW.b, 0.88f);
                            batch.draw(atlas.regions[PixelAtlas.ACID], sx + 16, sy + 16, TILE - 32, TILE - 32);
                        } else if (cur.oil[t]) {
                            batch.setColor(0.32f, 0.27f, 0.22f, 0.90f);
                            batch.draw(atlas.regions[PixelAtlas.OIL_SLICK], sx + 16, sy + 16, TILE - 32, TILE - 32);
                        }
                    } else if (cur.tar[t]) {
                        batch.setColor(MIST_COL.r * 0.48f, MIST_COL.g * 0.48f, MIST_COL.b * 0.48f, 0.90f);
                        batch.draw(atlas.regions[PixelAtlas.TAR_TILE], sx, sy, TILE, TILE);
                    } else if (cur.acid[t]) {
                        batch.setColor(ACID_GLOW.r, ACID_GLOW.g, ACID_GLOW.b, 0.88f);
                        batch.draw(atlas.regions[PixelAtlas.ACID], sx, sy, TILE, TILE);
                    } else if (cur.water[t]) {
                        batch.setColor(Math.min(1f, WATER_COL.r * 1.25f), Math.min(1f, WATER_COL.g * 1.25f), Math.min(1f, WATER_COL.b * 1.25f), 0.88f);
                        batch.draw(atlas.regions[PixelAtlas.WATER], sx, sy, TILE, TILE);
                    } else if (cur.oil[t]) {
                        batch.setColor(0.34f, 0.28f, 0.22f, 0.90f);
                        batch.draw(atlas.regions[PixelAtlas.OIL_SLICK], sx, sy, TILE, TILE);
                    }
                }

                if (cur.lootKind[t] != Scene.LOOT_NONE) {
                    float bob = Math.round(3f * MathUtils.sin(time * 3.5f + x + y));
                    Color lc = switch (cur.lootKind[t]) {
                        case Scene.LOOT_FLASK_OIL -> MIST_COL;
                        case Scene.LOOT_FLASK_WATER -> WATER_COL;
                        case Scene.LOOT_FLASK_ACID -> ACID_GLOW;
                        default -> Color.WHITE;
                    };
                    if (lc == Color.WHITE) batch.setColor(Color.WHITE);
                    else batch.setColor(0.72f + 0.28f * lc.r, 0.72f + 0.28f * lc.g, 0.72f + 0.28f * lc.b, 1f);
                    float ls = TILE * 0.68f;
                    batch.draw(atlas.regions[PixelAtlas.spriteForLoot(cur.lootKind[t])], sx + (TILE - ls) * 0.5f, sy + TILE * 0.10f + bob, ls, ls);
                }
            }
        }

        // Purely visual ground blood splats (under lightmap multiply and entity shadows)
        for (int b = 0; b < bloodCount; b++) {
            float sz = TILE * bloodScale[b];
            float bx = mapScreenX(bloodX[b]) - sz * 0.5f;
            float by = centerScreenY(bloodY[b]) - sz * 0.5f;
            float sh = bloodShade[b];
            batch.setColor(0.86f * sh, 0.11f * sh, 0.14f * sh, bloodAlpha[b]);
            batch.draw(atlas.regions[PixelAtlas.BLOOD_SPLAT_0 + bloodKind[b]],
                    bx, by, sz * 0.5f, sz * 0.5f, sz, sz, 1f, 1f, bloodRot[b]);
        }

        // Smooth circular radial lightmap multiply across floor/walls/puddles/loot
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.setColor(Color.WHITE);
        batch.draw(lightFbo.getColorBufferTexture(), MAP_X, viewH - MAP_Y - mapH(), mapW(), mapH(), 0f, 0f, 1f, 1f);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Self-lit floor fires & translucent gas clouds above lightmap
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                float sx = mapScreenX(x), sy = mapScreenY(y);
                if (cur.steam[t] || cur.superheatedSteam[t] || cur.oilMist[t]) {
                    Color gc = cur.superheatedSteam[t] ? SUPERHEATED_COL : cur.oilMist[t] ? MIST_COL : STEAM_COL;
                    float drift = Math.round(MathUtils.sin(time * 2.2f + x * 1.1f + y) * 6f);
                    batch.setColor(gc.r, gc.g, gc.b, 0.50f);
                    batch.draw(atlas.regions[PixelAtlas.GAS_PUFF], sx + drift, sy + 4, TILE, TILE);
                }
                if (cur.burning[t]) {
                    float fScale = 0.95f + 0.07f * MathUtils.sin(time * 12f + x * 2.1f + y);
                    float bFlip = (((int) (time * 5.2f + x + y)) & 1) == 0 ? 1f : -1f;
                    float fw = TILE * 0.72f, fh = TILE * 0.60f * fScale;
                    batch.setColor(0.98f, 0.58f, 0.25f, 0.92f);
                    batch.draw(atlas.regions[PixelAtlas.FIRE_0],
                            sx + (TILE - fw) * 0.5f, sy + TILE * 0.06f, fw * 0.5f, 0f, fw, fh, bFlip, 1f, 0f);
                }
            }
        }

        // 2. Y-sorted entities: two-layer ground shadows, movement ghosts, 1-pose motion, dynamic Brazier/Torch fire
        int entCount = 0;
        for (int i = 0; i < cur.n && entCount < MAX_ENT; i++) {
            if (cur.hp[i] > 0) drawOrder[entCount++] = i;
        }
        for (int a = 1; a < entCount; a++) {
            int key = drawOrder[a], b = a - 1;
            float ky = drawY(key);
            while (b >= 0 && drawY(drawOrder[b]) > ky) {
                drawOrder[b + 1] = drawOrder[b];
                b--;
            }
            drawOrder[b + 1] = key;
        }

        for (int k = 0; k < entCount; k++) {
            int i = drawOrder[k];
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            int p = prevIdx[i];
            boolean sliding = p >= 0 && cur.moved(i, prev) && anim < SLIDE;
            float hopY = sliding ? MathUtils.sin((anim / SLIDE) * MathUtils.PI) * 10f : 0f;
            float shadowFade = 1f - Math.min(0.45f, hopY / 20f);

            int entFrame = (animFrame + cur.id[i].hashCode()) & 3;
            int spr = entityDrawSprite(i, entFrame);
            float eScale = entityScale(spr);

            // Two-layer contact shadow: soft cast pool + tight dark occlusion core under soles
            float swOuter = (TILE - 16) * eScale, swInner = (TILE - 36) * eScale;
            batch.setColor(0f, 0f, 0f, 0.56f * shadowFade);
            batch.draw(atlas.glow, px + (TILE - swOuter) * 0.5f, py - 4, swOuter, 32 * (0.5f + 0.5f * eScale));
            batch.setColor(0f, 0f, 0f, 0.88f * shadowFade);
            batch.draw(atlas.glow, px + (TILE - swInner) * 0.5f, py + 2, swInner, 16 * (0.5f + 0.5f * eScale));

            int natFace = PixelAtlas.naturalFacing(spr);
            int faceSign = 1;
            if (i == 0) {
                faceSign = cur.proneEntity[0] ? 1 : thrallFacing * natFace;
            } else if (cur.kind[i] == Entity.Kind.CREATURE && !cur.proneEntity[i]) {
                int want = cur.x[0] > cur.x[i] ? 1 : -1;
                faceSign = want * natFace;
            }

            boolean aliveCreature = cur.kind[i] == Entity.Kind.CREATURE && !cur.frozenEntity[i] && !cur.proneEntity[i];
            float breathe = aliveCreature ? MathUtils.sin(time * 3.4f + i * 1.7f) : 0f;
            float sxScale = (1f - breathe * 0.014f) * eScale;
            float syScale = (1f + breathe * 0.022f) * eScale;
            float rot = cur.proneEntity[i] ? 90f : 0f;
            if (spr == PixelAtlas.THRALL_ATTACK && !cur.proneEntity[i]) {
                sxScale *= THRALL_ATTACK_SCALE;
                syScale *= THRALL_ATTACK_SCALE;
                hopY -= 0.025f * TILE;
            }

            if (!cur.proneEntity[i] && i == 0 && thrallPose != PixelAtlas.POSE_IDLE && thrallPoseAge < thrallPoseMax) {
                float pCurve = MathUtils.sin((thrallPoseAge / Math.max(0.01f, thrallPoseMax)) * MathUtils.PI);
                if (thrallPose == PixelAtlas.POSE_ATTACK) {
                    float holdCurve = lungeFactor(thrallPoseMax - thrallPoseAge, thrallPoseMax, true);
                    rot += -thrallFacing * 2.5f * holdCurve;
                } else if (thrallPose == PixelAtlas.POSE_CAST) {
                    hopY += 6f * pCurve;
                    syScale += 0.03f * pCurve;
                } else if (thrallPose == PixelAtlas.POSE_GUARD) {
                    syScale -= 0.05f * pCurve;
                    sxScale += 0.04f * pCurve;
                }
            }
            if (!cur.proneEntity[i] && i < MAX_ENT && hitFlash[i] > 0) {
                rot += -faceSign * 7f * (hitFlash[i] / 0.28f);
            }

            if (p >= 0 && cur.moved(i, prev) && anim < GHOST_LIFE) {
                float ga = 0.28f * (1f - anim / GHOST_LIFE);
                batch.setColor(0.85f, 0.88f, 0.92f, ga);
                batch.draw(atlas.regions[spr], mapScreenX(prev.x[p]), mapScreenY(prev.y[p]),
                        TILE * 0.5f, 0f, TILE, TILE, faceSign * eScale, eScale, 0f);
            }

            boolean casting = i == 0 && thrallPose == PixelAtlas.POSE_CAST && thrallPoseAge < thrallPoseMax
                    && !cur.frozenEntity[0] && !cur.proneEntity[0];
            float castPulse = casting ? MathUtils.sin((thrallPoseAge / thrallPoseMax) * MathUtils.PI) : 0f;

            if (i < MAX_ENT && hitFlash[i] > 0) {
                batch.setColor(1f, 0.86f, 0.86f, 1f);
            } else if (cur.frozenEntity[i]) {
                batch.setColor(0.58f, 0.78f, 0.88f, 1f);
            } else {
                float eLum = MathUtils.clamp(cur.light[cur.y[i] * cur.w + cur.x[i]] + 0.30f + 0.24f * castPulse, 0.74f, 1f);
                float warm = fireWarmthAt(drawX(i) + 0.5f, drawY(i) + 0.5f);
                batch.setColor(Math.min(1f, eLum * (1f + 0.20f * warm)),
                        Math.min(1f, eLum * (1f + 0.05f * warm)),
                        Math.min(1f, eLum * (1f - 0.14f * warm)), 1f);
            }
            float originY = cur.proneEntity[i] ? TILE * 0.5f : 0f;
            entSpr[k] = spr;
            entPx[k] = px;
            entPy[k] = py + hopY;
            entFace[k] = faceSign;
            entSx[k] = sxScale;
            entSy[k] = syScale;
            entScale[k] = eScale;
            entRot[k] = rot;
            batch.draw(atlas.regions[spr], px, py + hopY, TILE * 0.5f, originY, TILE, TILE,
                    faceSign * sxScale, syScale, rot);
            drawEntityAccents(k, false);

            // Lit Brazier (torch-style horizontal flip + subtle flicker on Fire.jpg fitted to bowl width)
            if (cur.kind[i] == Entity.Kind.PROP && cur.glyph[i] == 'i' && cur.litEntity[i]) {
                float bFlip = (((int) (time * 5.2f + i)) & 1) == 0 ? 1f : -1f;
                float fPulse = 0.94f + 0.08f * MathUtils.sin(time * 11f + i);
                float fw = 0.68f * TILE * BRAZIER_SCALE, fh = 0.50f * TILE * BRAZIER_SCALE * fPulse;
                batch.setColor(0.98f, 0.58f, 0.25f, 0.94f);
                batch.draw(atlas.regions[PixelAtlas.FIRE_0],
                        px + (TILE - fw) * 0.5f, py + 0.48f * TILE * BRAZIER_SCALE, fw * 0.5f, 0f, fw, fh, bFlip, 1f, 0f);
            }
            // Lit Thrall Torch (hand-drawn torch flame anchored to torch head on both idle & attack poses)
            if (i == 0 && thrallTorchLit && !cur.proneEntity[0]) {
                float fw = 0.22f * TILE, fh = 0.32f * TILE * (0.95f + 0.07f * MathUtils.sin(time * 13f));
                float torchFlip = (((int) (time * 6.5f)) & 1) == 0 ? 1f : -1f;
                batch.setColor(0.96f, 0.60f, 0.28f, 0.94f);
                batch.draw(atlas.regions[PixelAtlas.TORCH_FIRE],
                        tmpTorch[0] - fw * 0.5f, tmpTorch[1] - 0.07f * TILE, fw * 0.5f, 0f, fw, fh, torchFlip, 1f, 0f);
            }

            if (cur.frozenEntity[i]) {
                batch.setColor(ICE_COL.r, ICE_COL.g, ICE_COL.b, 0.28f);
                batch.draw(atlas.regions[PixelAtlas.ICE_TILE], px, py, TILE, TILE);
            }
            if (cur.burningEntity[i]) {
                float bFlip = (((int) (time * 5.5f + i)) & 1) == 0 ? 1f : -1f;
                float fw = TILE * 0.68f, fh = TILE * 0.58f;
                batch.setColor(0.98f, 0.58f, 0.25f, 0.72f);
                batch.draw(atlas.regions[PixelAtlas.FIRE_0],
                        px + (TILE - fw) * 0.5f, py + TILE * 0.10f, fw * 0.5f, 0f, fw, fh, bFlip, 1f, 0f);
            }

            // ID badge & unobstructed 5px top-edge HP bar (feet/heads/ground shadows stay clean)
            String tag = i == 0 ? "@" : cur.id[i];
            float pillW = tag.length() * 6 + 6;
            batch.setColor(0.05f, 0.07f, 0.1f, 0.80f);
            batch.draw(atlas.white, px + 3, py + TILE - 15, pillW, 12);
            if (cur.overwatchEntity[i]) {
                batch.draw(atlas.white, px + TILE - 14, py + TILE - 15, 11, 12);
            }
            if (cur.kind[i] == Entity.Kind.CREATURE || cur.hp[i] < cur.maxHp[i]) {
                float barX = px + pillW + 6;
                float barRight = cur.overwatchEntity[i] ? px + TILE - 17 : px + TILE - 3;
                float barW = Math.max(24f, barRight - barX);
                float barY = py + TILE - 5f;
                batch.setColor(BAR_BG);
                batch.draw(atlas.white, barX, barY, barW, 5f);
                batch.setColor(colorOf(cur, i));
                batch.draw(atlas.white, barX, barY, barW * cur.hp[i] / (float) Math.max(1, cur.maxHp[i]), 5f);
            }
        }

        // 3. Faded selective-color projectile & blast overlays + particles
        for (int i = 0; i < MAX_FX; i++) {
            if (fxAge[i] < 0f || fxAge[i] >= fxMaxAge[i]) continue;
            float u = fxAge[i] / fxMaxAge[i];
            float a = 1f - u;
            Color fc = fxCol[i];
            if (fxKind[i] == FX_FIREBALL) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                float sz = TILE * 0.62f;
                batch.setColor(FIRE.r, FIRE.g, FIRE.b, 0.92f);
                batch.draw(atlas.regions[PixelAtlas.FIRE_0 + (animFrame & 1)], fx - sz * 0.5f, fy - sz * 0.5f, sz, sz);
            } else if (fxKind[i] == FX_ELEM_BOLT) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                int spr = switch (fxParam[i]) {
                    case ELEM_ACID -> PixelAtlas.ACID;
                    case ELEM_WATER -> PixelAtlas.WATER;
                    case ELEM_OIL -> PixelAtlas.OIL_SLICK;
                    default -> PixelAtlas.LOOT_STONE;
                };
                float sz = TILE * 0.58f;
                batch.setColor(fc.r, fc.g, fc.b, 0.92f);
                batch.draw(atlas.regions[spr], fx - sz * 0.5f, fy - sz * 0.5f, sz, sz);
            } else if (fxKind[i] == FX_CROSS) {
                int ex = MathUtils.floor(fxX0[i]), ey = MathUtils.floor(fxY0[i]);
                int elem = fxParam[i];
                for (int c = 0; c < CROSS_DX.length; c++) {
                    int cx = ex + CROSS_DX[c], cy = ey + CROSS_DY[c];
                    if (cx < 0 || cx >= cur.w || cy < 0 || cy >= cur.h || cur.wall[cy * cur.w + cx]) continue;
                    float tx = mapScreenX(cx), ty = mapScreenY(cy);
                    batch.setColor(fc.r, fc.g, fc.b, (elem == ELEM_BLOOD ? 0.34f : 0.26f) * a);
                    batch.draw(atlas.white, tx + 3, ty + 3, TILE - 6, TILE - 6);
                    float os = TILE * 0.74f, op = (TILE - os) * 0.5f;
                    int cFrame = (animFrame + c) & 1;
                    if (elem == ELEM_FIRE) {
                        batch.setColor(FIRE.r, FIRE.g, FIRE.b, 0.85f * a);
                        batch.draw(atlas.regions[PixelAtlas.FIRE_0 + cFrame], tx + op, ty + op, os, os);
                    } else if (elem == ELEM_ACID) {
                        batch.setColor(ACID_GLOW.r, ACID_GLOW.g, ACID_GLOW.b, 0.82f * a);
                        batch.draw(atlas.regions[PixelAtlas.ACID], tx + op, ty + op, os, os);
                    } else if (elem == ELEM_WATER) {
                        batch.setColor(WATER_COL.r * 1.25f, WATER_COL.g * 1.25f, WATER_COL.b * 1.25f, 0.82f * a);
                        batch.draw(atlas.regions[PixelAtlas.WATER], tx + op, ty + op, os, os);
                    } else if (elem == ELEM_OIL) {
                        batch.setColor(MIST_COL.r * 0.55f, MIST_COL.g * 0.55f, MIST_COL.b * 0.55f, 0.82f * a);
                        batch.draw(atlas.regions[PixelAtlas.OIL_SLICK], tx + op, ty + op, os, os);
                    } else {
                        batch.setColor(fc.r, fc.g, fc.b, 0.50f * a);
                        batch.draw(atlas.regions[PixelAtlas.GAS_PUFF], tx, ty, TILE, TILE);
                    }
                }
            }
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (partAge[i] < 0f || partAge[i] >= partMaxAge[i]) continue;
            float a = 1f - partAge[i] / partMaxAge[i];
            Color pc = partCol[i];
            batch.setColor(pc.r, pc.g, pc.b, a * 0.88f);
            batch.draw(atlas.white, mapScreenX(partX[i]), centerScreenY(partY[i]), partSize[i], partSize[i]);
        }

        // 4. Two-stage warm additive orange fire glow + spell blooms (blends over covered elements)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                if (!cur.burning[t]) continue;
                float cx = mapScreenX(x) + TILE * 0.5f, cy = mapScreenY(y) + TILE * 0.36f;
                float flicker = 0.92f + 0.08f * MathUtils.sin(time * 11.5f + x * 1.9f + y * 2.3f);
                float outer = TILE * 2.0f, inner = TILE * 0.95f;
                batch.setColor(0.95f, 0.44f, 0.14f, 0.18f * flicker);
                batch.draw(atlas.glow, cx - outer * 0.5f, cy - outer * 0.5f, outer, outer);
                batch.setColor(1f, 0.62f, 0.24f, 0.30f * flicker);
                batch.draw(atlas.glow, cx - inner * 0.5f, cy - inner * 0.5f, inner, inner);
            }
        }
        for (int k = 0; k < entCount; k++) {
            int i = drawOrder[k];
            drawEntityAccents(k, true);
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            float flicker = 0.92f + 0.08f * MathUtils.sin(time * 11.5f + i * 1.9f);
            if (i == 0 && thrallTorchLit && !cur.proneEntity[0]) {
                float torchX = tmpTorch[0], torchY = tmpTorch[1] + 0.06f * TILE;
                float outer = TILE * 1.85f, inner = TILE * 0.78f;
                batch.setColor(0.96f, 0.48f, 0.15f, 0.18f * flicker);
                batch.draw(atlas.glow, torchX - outer * 0.5f, torchY - outer * 0.5f, outer, outer);
                batch.setColor(1f, 0.66f, 0.26f, 0.32f * flicker);
                batch.draw(atlas.glow, torchX - inner * 0.5f, torchY - inner * 0.5f, inner, inner);
            } else if (cur.kind[i] == Entity.Kind.PROP && cur.glyph[i] == 'i' && cur.litEntity[i]) {
                float cx = px + TILE * 0.5f, cy = py + TILE * 0.64f * BRAZIER_SCALE;
                float outer = TILE * 2.2f * BRAZIER_SCALE, inner = TILE * 1.0f * BRAZIER_SCALE;
                batch.setColor(0.96f, 0.46f, 0.14f, 0.22f * flicker);
                batch.draw(atlas.glow, cx - outer * 0.5f, cy - outer * 0.5f, outer, outer);
                batch.setColor(1f, 0.64f, 0.24f, 0.34f * flicker);
                batch.draw(atlas.glow, cx - inner * 0.5f, cy - inner * 0.5f, inner, inner);
            } else if (cur.litEntity[i]) {
                float outer = TILE * 1.8f;
                batch.setColor(0.95f, 0.46f, 0.15f, 0.18f * flicker);
                batch.draw(atlas.glow, px + TILE * 0.5f - outer * 0.5f, py + TILE * 0.5f - outer * 0.5f, outer, outer);
            } else if (i == 0 && cur.concentration > 0) {
                batch.setColor(BOLT_COL.r, BOLT_COL.g, BOLT_COL.b, 0.24f);
                batch.draw(atlas.glow, px - 30, py - 30, TILE + 60, TILE + 60);
            }
            if (i == 0 && thrallPose == PixelAtlas.POSE_CAST && thrallPoseAge < thrallPoseMax
                    && !cur.frozenEntity[0] && !cur.proneEntity[0]) {
                float castPulse = MathUtils.sin((thrallPoseAge / thrallPoseMax) * MathUtils.PI);
                Color cc = thrallCastElem == ELEM_FORCE ? BOLT_COL : elemColor(thrallCastElem);
                float pad = 24f + 28f * castPulse;
                batch.setColor(cc.r, cc.g, cc.b, 0.48f * castPulse);
                batch.draw(atlas.glow, px - pad, py - pad, TILE + pad * 2f, TILE + pad * 2f);
            }
        }
        for (int i = 0; i < MAX_FX; i++) {
            if (fxAge[i] < 0f || fxAge[i] >= fxMaxAge[i]) continue;
            float u = fxAge[i] / fxMaxAge[i];
            float a = 1f - u;
            Color fc = fxCol[i];
            if (fxKind[i] == FX_PSIONIC_SPIKE) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float tipU = Math.min(1f, u * 2.4f);
                float px = MathUtils.lerp(x0, x1, tipU), py = MathUtils.lerp(y0, y1, tipU);
                float dx = px - x0, dy = py - y0;
                float len = Math.max(8f, (float) Math.hypot(dx, dy));
                float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                float mx = (x0 + px) * 0.5f, my = (y0 + py) * 0.5f;
                float corrW = len + 44f, corrH = 56f;
                batch.setColor(BOLT_COL.r, BOLT_COL.g, BOLT_COL.b, 0.34f * a);
                batch.draw(atlas.glow, mx - corrW * 0.5f, my - corrH * 0.5f,
                        corrW * 0.5f, corrH * 0.5f, corrW, corrH, 1f, 1f, angle);
                for (int rIdx = 1; rIdx <= 4; rIdx++) {
                    float rt = rIdx * 0.25f;
                    float rx = MathUtils.lerp(x0, px, rt), ry = MathUtils.lerp(y0, py, rt);
                    float rw = 22f + u * 24f, rh = 44f + u * 50f;
                    batch.setColor(0.75f, 0.88f, 0.94f, 0.36f * a);
                    batch.draw(atlas.glow, rx - rw * 0.5f, ry - rh * 0.5f,
                            rw * 0.5f, rh * 0.5f, rw, rh, 1f, 1f, angle);
                }
            } else if (fxKind[i] == FX_BLOOD_BOLT) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float dx = x1 - x0, dy = y1 - y0;
                float len = Math.max(8f, (float) Math.hypot(dx, dy));
                float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f;
                float corrW = len + 34f, corrH = 48f;
                batch.setColor(BLOOD_BRIGHT.r, BLOOD_BRIGHT.g, BLOOD_BRIGHT.b, 0.34f * a);
                batch.draw(atlas.glow, mx - corrW * 0.5f, my - corrH * 0.5f,
                        corrW * 0.5f, corrH * 0.5f, corrW, corrH, 1f, 1f, angle);
            } else if (fxKind[i] == FX_FIREBALL) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                batch.setColor(FIRE.r, FIRE.g, FIRE.b, 0.52f * a + 0.14f);
                batch.draw(atlas.glow, fx - TILE * 0.52f, fy - TILE * 0.52f, TILE * 1.04f, TILE * 1.04f);
            } else if (fxKind[i] == FX_ELEM_BOLT) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                float pu = Math.max(0f, u - 0.16f);
                float tx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], pu));
                float ty = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], pu));
                batch.setColor(fc.r, fc.g, fc.b, 0.40f * a);
                batch.draw(atlas.glow, tx - TILE * 0.35f, ty - TILE * 0.35f, TILE * 0.7f, TILE * 0.7f);
                batch.setColor(fc.r, fc.g, fc.b, 0.54f * a + 0.14f);
                batch.draw(atlas.glow, fx - TILE * 0.48f, fy - TILE * 0.48f, TILE * 0.96f, TILE * 0.96f);
            } else if (fxKind[i] == FX_CRIT) {
                int tier = Math.max(1, fxParam[i] >>> 4);
                float cx = mapScreenX(fxX0[i]), cy = centerScreenY(fxY0[i]);
                float glow = TILE * (1.0f + 0.55f * u + 0.20f * tier);
                batch.setColor(fc.r, fc.g, fc.b, 0.58f * a);
                batch.draw(atlas.glow, cx - glow * 0.5f, cy - glow * 0.5f, glow, glow);
                float core = TILE * (0.68f - 0.24f * u);
                batch.setColor(EMBER.r, EMBER.g, 0.72f, 0.72f * a);
                batch.draw(atlas.glow, cx - core * 0.5f, cy - core * 0.5f, core, core);
            }
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (critFlashTime > 0) {
            batch.setColor(EMBER.r, EMBER.g, 0.45f, 0.16f * (critFlashTime / 0.14f));
            batch.draw(atlas.white, MAP_X, viewH - MAP_Y - mapH(), mapW(), mapH());
        }

        batch.flush();
        endMapScissor();
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawLineOverlays() {
        float pulse = 0.55f + 0.35f * (float) Math.sin(time * 7);
        int mw = mapW(), mh = mapH(), logW = logW(), logY = logY();
        int sideY = viewH - MAP_Y - mh, inputY = viewH - inputTop() - INPUT_H;
        int total = log.size(), visible = logLines(), scroll = scrollLines();
        int first = Math.max(0, total - visible - scroll);
        float hudX = sideX() + 12, barX = hudX + 26, barW = sideW() - 114;
        float apBarY = viewH - (MAP_Y + 58) - (hudFontH + 8) / 2f;
        int maxPips = Math.max(roundAp, ap);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(MAP_X + shakeX, sideY + shakeY, mw, mh);
        shapes.rect(sideX(), sideY, sideW(), mh);
        shapes.rect(MAP_X, logY, logW, logHeight());
        shapes.rect(MAP_X, inputY, logW, INPUT_H);

        for (int j = 0; j < visible && first + j < log.size(); ) {
            LogRow r = log.get(first + j);
            if (r.block == 0) { j++; continue; }
            int j1 = cardEnd(first, visible, j);
            float topY = rowMidY(j) + LINE_H / 2f - 1;
            float botY = rowMidY(j1) - LINE_H / 2f + 1;
            boolean clipTop = first + j > 0 && log.get(first + j - 1).block == r.block;
            boolean clipBot = first + j1 + 1 < log.size() && log.get(first + j1 + 1).block == r.block;
            float dim = r.round < round ? 0.52f : 1f;
            Color gc = groupColor(r.group);
            shapes.setColor(gc.r, gc.g, gc.b, 0.35f * dim);
            float x0 = MAP_X + 6, x1 = x0 + logW - 22;
            shapes.line(x0, botY, x0, topY);
            shapes.line(x1, botY, x1, topY);
            if (!clipTop) shapes.line(x0, topY, x1, topY);
            if (!clipBot) shapes.line(x0, botY, x1, botY);
            j = j1 + 1;
        }
        if (scroll > 0) {
            shapes.setColor(hoverJump ? THRALL : PROP);
            shapes.rect(jumpPillX(), jumpPillY(), jumpPillW(), JUMP_PILL_H);
        }
        shapes.setColor(BORDER);
        if (maxPips <= 10) {
            float pipStep = barW / Math.max(1, maxPips);
            float pipW = Math.max(4f, pipStep - 3f);
            for (int p = 0; p < maxPips; p++) shapes.rect(barX + p * pipStep, apBarY, pipW, 8);
        } else {
            shapes.rect(barX, apBarY, barW, 8);
        }

        shapes.flush();
        beginMapScissor();

        // Entity status frames & overwatch threat markers
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            if (cur.frozenEntity[i]) { shapes.setColor(ICE_COL.r, ICE_COL.g, ICE_COL.b, 0.90f); shapes.rect(px + 4, py + 4, TILE - 8, TILE - 8); }
            if (cur.stunnedEntity[i] && !cur.frozenEntity[i]) { shapes.setColor(STUN_COL.r, STUN_COL.g, STUN_COL.b, pulse); shapes.rect(px + 4, py + 4, TILE - 8, TILE - 8); }
            if (cur.bleedingEntity[i]) { shapes.setColor(BLEED_COL.r, BLEED_COL.g, BLEED_COL.b, 0.85f); shapes.rect(px + 7, py + 7, TILE - 14, TILE - 14); }
            if (cur.overwatchEntity[i]) {
                shapes.setColor(CREATURE.r, CREATURE.g, CREATURE.b, 0.32f * pulse);
                shapes.line(px + TILE * 0.5f, py + TILE * 0.5f, mapScreenX(cur.x[0] + 0.5f), centerScreenY(cur.y[0] + 0.5f));
            }
            if (cur.id[i].equals(actingId)) {
                shapes.setColor(TEXT.r, TEXT.g, TEXT.b, pulse);
                shapes.rect(px + 2, py + 2, TILE - 4, TILE - 4);
            }
        }

        // Live command targeting reticles
        if (!presenter.busy() && input.length() > 0) {
            int nT = findTargets(input, cur, targetX, targetY, targetX.length);
            float ox = mapScreenX(cur.x[0] + 0.5f), oy = centerScreenY(cur.y[0] + 0.5f);
            for (int k = 0; k < nT; k++) {
                float rx = mapScreenX(targetX[k]), ry = mapScreenY(targetY[k]);
                shapes.setColor(BOLT_COL.r, BOLT_COL.g, BOLT_COL.b, 0.38f * pulse);
                shapes.line(ox, oy, rx + TILE * 0.5f, ry + TILE * 0.5f);
                shapes.setColor(BOLT_COL.r, BOLT_COL.g, BOLT_COL.b, 0.88f * pulse);
                shapes.rect(rx + 3, ry + 3, TILE - 6, TILE - 6);
                shapes.line(rx + TILE * 0.5f, ry + 3, rx + TILE * 0.5f, ry + 16);
                shapes.line(rx + TILE * 0.5f, ry + TILE - 16, rx + TILE * 0.5f, ry + TILE - 3);
            }
        }

        // Active spell / combat VFX
        for (int i = 0; i < MAX_FX; i++) {
            if (fxAge[i] < 0f || fxAge[i] >= fxMaxAge[i]) continue;
            float u = fxAge[i] / fxMaxAge[i];
            float a = 1f - u;
            Color fc = fxCol[i];
            if (fxKind[i] == FX_BLOOD_BOLT) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float len = Math.max(1f, (float) Math.hypot(x1 - x0, y1 - y0));
                float nx = -(y1 - y0) / len, ny = (x1 - x0) / len;
                float px = x0, py = y0;
                for (int seg = 1; seg <= 5; seg++) {
                    float t = seg / 5f;
                    float jitter = seg == 5 ? 0f : MathUtils.sin(time * 55f + seg * 2.1f + i) * 10f;
                    float qx = MathUtils.lerp(x0, x1, t) + nx * jitter;
                    float qy = MathUtils.lerp(y0, y1, t) + ny * jitter;
                    shapes.setColor(BLOOD_DEEP.r, BLOOD_DEEP.g, BLOOD_DEEP.b, a * 0.85f);
                    shapes.line(px + nx * 2f, py + ny * 2f, qx + nx * 2f, qy + ny * 2f);
                    shapes.line(px - nx * 2f, py - ny * 2f, qx - nx * 2f, qy - ny * 2f);
                    shapes.setColor(BLOOD_BRIGHT.r, BLOOD_BRIGHT.g, BLOOD_BRIGHT.b, a);
                    shapes.line(px + nx, py + ny, qx + nx, qy + ny);
                    shapes.line(px - nx, py - ny, qx - nx, qy - ny);
                    shapes.line(px, py, qx, qy);
                    px = qx;
                    py = qy;
                }
                shapes.setColor(BLOOD_BRIGHT.r, BLOOD_BRIGHT.g, BLOOD_BRIGHT.b, a * 0.9f);
                shapes.circle(x1, y1, 8f + 10f * u, 16);
            } else if (fxKind[i] == FX_PSIONIC_SPIKE) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float tipU = Math.min(1f, u * 2.4f);
                float px = MathUtils.lerp(x0, x1, tipU), py = MathUtils.lerp(y0, y1, tipU);
                float dx = x1 - x0, dy = y1 - y0;
                float len = Math.max(1f, (float) Math.hypot(dx, dy));
                float ux = dx / len, uy = dy / len, nx = -uy, ny = ux;
                shapes.setColor(0.55f, 0.85f, 1f, a * 0.65f);
                shapes.line(x0 + nx * 3f, y0 + ny * 3f, px + nx * 2f, py + ny * 2f);
                shapes.line(x0 - nx * 3f, y0 - ny * 3f, px - nx * 2f, py - ny * 2f);
                for (int w = 1; w <= 3; w++) {
                    float wt = w * 0.30f;
                    float wx = MathUtils.lerp(x0, px, wt), wy = MathUtils.lerp(y0, py, wt);
                    shapes.circle(wx, wy, 4f + u * 11f, 14);
                }
                shapes.setColor(1f, 1f, 1f, a);
                shapes.line(x0, y0, px, py);
                float bx = px - ux * 15f, by = py - uy * 15f;
                shapes.line(px + ux * 3f, py + uy * 3f, bx + nx * 5f, by + ny * 5f);
                shapes.line(px + ux * 3f, py + uy * 3f, bx - nx * 5f, by - ny * 5f);
                shapes.setColor(0.75f, 0.94f, 1f, a * 0.85f);
                shapes.circle(x1, y1, 5f + 9f * u, 16);
            } else if (fxKind[i] == FX_RING) {
                float cx = mapScreenX(fxX0[i]), cy = centerScreenY(fxY0[i]);
                float rad = MathUtils.lerp(0.15f * TILE, fxX1[i] * TILE, Interpolation.pow2Out.apply(u));
                shapes.setColor(fc.r, fc.g, fc.b, a);
                shapes.circle(cx, cy, rad, 24);
                shapes.circle(cx, cy, rad * 0.72f, 20);
            } else if (fxKind[i] == FX_PROJ) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float arc = MathUtils.sin(u * MathUtils.PI) * 26f;
                float px = MathUtils.lerp(x0, x1, u), py = MathUtils.lerp(y0, y1, u) + arc;
                float pu = Math.max(0f, u - 0.14f);
                float tx = MathUtils.lerp(x0, x1, pu), ty = MathUtils.lerp(y0, y1, pu) + MathUtils.sin(pu * MathUtils.PI) * 26f;
                shapes.setColor(fc.r, fc.g, fc.b, 0.95f);
                shapes.line(tx, ty, px, py);
                shapes.circle(px, py, 4.5f, 10);
            } else if (fxKind[i] == FX_FIREBALL) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float px = MathUtils.lerp(x0, x1, u), py = MathUtils.lerp(y0, y1, u);
                float pu = Math.max(0f, u - 0.22f);
                float tx = MathUtils.lerp(x0, x1, pu), ty = MathUtils.lerp(y0, y1, pu);
                shapes.setColor(EMBER.r, EMBER.g, EMBER.b, a);
                shapes.line(tx, ty, px, py);
                shapes.circle(px, py, 10f + 5f * MathUtils.sin(u * MathUtils.PI), 16);
                shapes.setColor(FIRE.r, FIRE.g, FIRE.b, a * 0.8f);
                shapes.circle(px, py, 16f, 18);
            } else if (fxKind[i] == FX_ELEM_BOLT) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float px = MathUtils.lerp(x0, x1, u), py = MathUtils.lerp(y0, y1, u);
                float pu = Math.max(0f, u - 0.20f);
                float tx = MathUtils.lerp(x0, x1, pu), ty = MathUtils.lerp(y0, y1, pu);
                shapes.setColor(fc.r, fc.g, fc.b, a);
                shapes.line(tx, ty, px, py);
                shapes.circle(px, py, 9f + 4f * MathUtils.sin(u * MathUtils.PI), 14);
                shapes.circle(px, py, 14f, 16);
                if (u > 0.65f) {
                    shapes.circle(x1, y1, (u - 0.65f) * TILE * 1.4f, 18);
                }
            } else if (fxKind[i] == FX_CROSS) {
                int ex = MathUtils.floor(fxX0[i]), ey = MathUtils.floor(fxY0[i]);
                int elem = fxParam[i];
                shapes.setColor(fc.r, fc.g, fc.b, a);
                for (int c = 0; c < CROSS_DX.length; c++) {
                    int cx = ex + CROSS_DX[c], cy = ey + CROSS_DY[c];
                    if (cx < 0 || cx >= cur.w || cy < 0 || cy >= cur.h || cur.wall[cy * cur.w + cx]) continue;
                    float rx = mapScreenX(cx), ry = mapScreenY(cy);
                    float pad = 4f + u * 4f;
                    shapes.rect(rx + pad, ry + pad, TILE - pad * 2f, TILE - pad * 2f);
                    if (elem == ELEM_BLOOD || elem == ELEM_FORCE) {
                        shapes.line(rx + 8, ry + 8, rx + TILE - 8, ry + TILE - 8);
                        shapes.line(rx + 8, ry + TILE - 8, rx + TILE - 8, ry + 8);
                    }
                }
                if (elem == ELEM_BLOOD) {
                    shapes.setColor(BLOOD_DEEP.r, BLOOD_DEEP.g, BLOOD_DEEP.b, a);
                    shapes.circle(mapScreenX(fxX0[i]), centerScreenY(fxY0[i]), 0.4f * TILE + u * 1.1f * TILE, 20);
                }
            } else if (fxKind[i] == FX_DRAIN) {
                float x0 = mapScreenX(fxX0[i]), y0 = centerScreenY(fxY0[i]);
                float x1 = mapScreenX(fxX1[i]), y1 = centerScreenY(fxY1[i]);
                float dx = x1 - x0, dy = y1 - y0;
                float len = Math.max(1f, (float) Math.hypot(dx, dy));
                float nx = -dy / len, ny = dx / len;
                shapes.setColor(fc.r, fc.g, fc.b, a * 0.85f);
                float px = x0, py = y0;
                for (int seg = 1; seg <= 6; seg++) {
                    float t = seg / 6f;
                    float wave = seg == 6 ? 0f : MathUtils.sin(t * MathUtils.PI * 2f + time * 18f) * 7f;
                    float wx = MathUtils.lerp(x0, x1, t) + nx * wave;
                    float wy = MathUtils.lerp(y0, y1, t) + ny * wave;
                    shapes.line(px, py, wx, wy);
                    px = wx;
                    py = wy;
                }
                for (int orb = 0; orb < 3; orb++) {
                    float ou = MathUtils.clamp(u * 1.35f - orb * 0.16f, 0f, 1f);
                    float wave = MathUtils.sin(ou * MathUtils.PI * 2f + time * 18f) * 7f;
                    shapes.circle(MathUtils.lerp(x0, x1, ou) + nx * wave, MathUtils.lerp(y0, y1, ou) + ny * wave, 4.5f, 10);
                }
            } else if (fxKind[i] == FX_CRIT) {
                int tier = Math.max(1, fxParam[i] >>> 4);
                float cx = mapScreenX(fxX0[i]), cy = centerScreenY(fxY0[i]);
                float ease = Interpolation.pow2Out.apply(u);
                float diag = MathUtils.lerp(10f, 36f + tier * 10f, ease);
                float card = MathUtils.lerp(6f, 26f + tier * 8f, ease);
                shapes.setColor(fc.r, fc.g, fc.b, a);
                shapes.line(cx - diag, cy - diag, cx + diag, cy + diag);
                shapes.line(cx - diag, cy + diag, cx + diag, cy - diag);
                shapes.line(cx - card, cy, cx + card, cy);
                shapes.line(cx, cy - card, cx, cy + card);
                shapes.setColor(1f, 1f, 1f, a);
                float inner = diag * 0.65f;
                shapes.line(cx - inner, cy - inner, cx + inner, cy + inner);
                shapes.line(cx - inner, cy + inner, cx + inner, cy - inner);
                shapes.circle(cx, cy, MathUtils.lerp(12f, (0.75f + 0.22f * tier) * TILE, ease), 24);
            }
        }
        shapes.flush();
        endMapScissor();
        shapes.end();
    }

    private void text(String s, float x, float topY) {
        font.draw(batch, s, x, viewH - topY);
    }

    private void hudText(String s, float x, float topY) {
        hudFont.draw(batch, s, x, viewH - topY);
    }

    private static String trimHud(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    private void drawText() {
        int mw = mapW(), mh = mapH(), sideY = viewH - MAP_Y - mh;
        batch.begin();
        batch.setColor(Color.WHITE);

        // Map coordinate rulers (culled to visible map viewport)
        font.setColor(MUTED);
        for (int x = 0; x < cur.w; x++) {
            float midX = mapScreenX(x) + TILE * 0.5f;
            if (midX < MAP_X + 6f || midX > MAP_X + mw - 6f) continue;
            String xs = numStr(x);
            text(xs, midX - xs.length() * charW * 0.5f, MAP_Y - fontH - 4 - shakeY);
        }
        for (int y = 0; y < cur.h; y++) {
            float midOffset = (y - camY + 0.5f) * TILE;
            if (midOffset < 6f || midOffset > mh - 6f) continue;
            text(numStr(y), MAP_X - 20 + shakeX, MAP_Y + (y - camY) * TILE + (TILE - fontH) / 2f - shakeY);
        }

        // Entity labels & floating damage numbers (clipped to map viewport)
        batch.flush();
        beginMapScissor();
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            String label = i == 0 ? "@" : cur.id[i];
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            badgeFont.setColor(i == 0 ? THRALL : TEXT);
            badgeFont.draw(batch, label, px + 6, py + TILE - 4f);
            if (cur.overwatchEntity[i]) {
                badgeFont.setColor(CREATURE);
                badgeFont.draw(batch, "!", px + TILE - 11, py + TILE - 4f);
            }
        }
        for (int i = 0; i < MAX_FLOATERS; i++) {
            if (floatAge[i] >= FLOAT_LIFE) continue;
            float alpha = 1f - floatAge[i] / FLOAT_LIFE;
            boolean crit = floatText[i].startsWith("CRIT");
            Color fc = crit ? EMBER : floatText[i].startsWith("-") ? CREATURE : SAGE;
            font.setColor(fc.r, fc.g, fc.b, alpha);
            float xOff = crit ? 14f : 38f;
            font.draw(batch, floatText[i], mapScreenX(floatX[i]) + xOff,
                    mapScreenY(floatY[i]) + TILE - 14 + floatAge[i] * (crit ? 40f : 32f));
        }
        batch.flush();
        endMapScissor();

        // Right panel: Narrower compact Thrall HUD + Equipment / Pack
        float px = sideX() + 12, py = MAP_Y + 14;
        float barX = px + 26, barW = sideW() - 114, valX = barX + barW + 8;
        hudFont.setColor(TEXT);
        hudText("ROUND " + round, px, py);
        if (!game.world.thrall().alive()) {
            hudFont.setColor(CREATURE);
            hudText("LINK SEVERED", px + 74, py);
        } else if (thinking > 0) {
            hudFont.setColor(PROP);
            hudText("HOSTILES" + ".".repeat(thinking), px + 74, py);
        } else {
            badgeFont.setColor(MUTED);
            String audioLabel = audio.muted ? "F2:MUTE" : (audio.musicMuted ? "F2:SFX F3:OFF" : "F2:SFX F3:BGM");
            badgeFont.draw(batch, audioLabel, sideX() + sideW() - 92, viewH - py - 1);
        }

        hudFont.setColor(MUTED);
        hudText("HP", px, MAP_Y + 38);
        hudText("AP", px, MAP_Y + 58);
        hudFont.setColor(TEXT);
        hudText(hp + "/" + maxHp, valX, MAP_Y + 38);
        hudText(ap + "/" + roundAp, valX, MAP_Y + 58);

        hudFont.setColor(MUTED);
        hudText("MAG", px, MAP_Y + 80);
        hudText("TEMP", px + 78, MAP_Y + 80);
        hudText("CHAN", px, MAP_Y + 98);
        hudText("COND", px, MAP_Y + 116);
        hudFont.setColor(TEXT);
        hudText(numStr(cur.magic), px + 32, MAP_Y + 80);
        hudText(cur.temp + "C", px + 118, MAP_Y + 80);
        String chanText = cur.concentration > 0
                ? cur.channel + " " + cur.concentration + "%"
                : cur.channel;
        hudText(trimHud(chanText, 22), px + 40, MAP_Y + 98);
        hudFont.setColor(cur.thrallStunned || cur.thrallFrozen ? CREATURE : TEXT);
        hudText(trimHud(cur.shortConds, 22), px + 40, MAP_Y + 116);

        hudFont.setColor(MUTED);
        hudText("EQUIPMENT", px, MAP_Y + 144);
        hudText("R:", px, MAP_Y + 164);
        hudText("L:", px, MAP_Y + 182);
        hudFont.setColor("empty".equals(cur.rightHand) ? MUTED : cur.rightHand.contains("[LIT]") ? PROP : TEXT);
        hudText(trimHud(cur.rightHand, 24), px + 22, MAP_Y + 164);
        hudFont.setColor("empty".equals(cur.leftHand) ? MUTED : cur.leftHand.contains("[LIT]") ? PROP : TEXT);
        hudText(trimHud(cur.leftHand, 24), px + 22, MAP_Y + 182);

        hudFont.setColor(MUTED);
        hudText("PACK (" + cur.packCount + "/" + cur.packMax + ")", px, MAP_Y + 212);
        py = MAP_Y + 232;
        for (int i = 0; i < cur.packItems.length; i++) {
            if (viewH - py - 14 < sideY) break;
            String item = cur.packItems[i];
            hudFont.setColor(item.contains("[LIT]") ? PROP : TEXT);
            hudText("- " + trimHud(item, 24), px + 2, py);
            py += 19;
        }

        // Structured 2-column combat log
        int logW = logW(), scroll = scrollLines();
        int visible = logLines(), first = Math.max(0, log.size() - visible - scroll);
        for (int j = 0; j < visible && first + j < log.size(); j++) {
            LogRow r = log.get(first + j);
            float dim = r.round < round ? 0.52f : 1f;
            float lineTop = logTop() + 7 + j * LINE_H;
            font.setColor(r.color.r, r.color.g, r.color.b, dim);
            text(r.text, MAP_X + 16, lineTop);
            if (r.badge != null) {
                float pillW = r.badge.length() * charW + 14;
                float pillX = MAP_X + logW - 24 - pillW;
                Color bc = r.badgeColor;
                font.setColor(bc.r, bc.g, bc.b, dim);
                text(r.badge, pillX + 7, lineTop);
            }
        }
        if (scroll > 0) {
            font.setColor(hoverJump ? THRALL : PROP);
            text(jumpLabel(), jumpPillX() + 8, viewH - jumpPillY() - (JUMP_PILL_H + fontH) / 2f);
        }
        font.setColor(presenter.busy() ? MUTED : THRALL);
        float promptTop = inputTop() + (INPUT_H - fontH) / 2f;
        text("ARCHON>", MAP_X + 12, promptTop);
        font.setColor(presenter.busy() ? MUTED : TEXT);
        font.draw(batch, input, MAP_X + 12 + 8 * charW, viewH - promptTop);

        batch.end();
    }
}
