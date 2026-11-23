package com.archon.ui;

import com.archon.app.Game;
import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.Entity;
import com.archon.model.Vec2;
import com.archon.model.World;
import com.archon.verb.ExitCode;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;
import java.util.List;

/**
 * Windowed front end. Renders in WIN_W x WIN_H virtual space with 2x supersampled fonts,
 * 16x16 pixel-art sprites, dynamic tile lighting, fixed-array particles/VFX, live command
 * targeting telegraphs, and procedural synth audio (F2 mute, F3 music toggle).
 */
public final class GdxView extends ApplicationAdapter implements View {
    public static final int WIN_W = 1140, WIN_H = 820;
    private static final int TILE = 64, MAP_X = 32, MAP_Y = 26, LINE_H = 22, MAX_FLOATERS = 16;
    private static final int INPUT_TOP = WIN_H - 48, INPUT_H = 34, SCROLLBAR_W = 8, JUMP_PILL_H = 22;
    private static final float SLIDE = 0.4f, GHOST_LIFE = 0.8f, FLOAT_LIFE = 1.6f, SCROLL_LINES = 1.0f;
    private static final int MAX_PARTICLES = 192, MAX_FX = 16, MAX_ENT = 32, MAX_CRIT_QUEUE = 8;
    private static final int FX_BLOOD_BOLT = 1, FX_PSIONIC_SPIKE = 2, FX_PROJ = 3,
            FX_FIREBALL = 4, FX_ELEM_BOLT = 5, FX_RING = 6, FX_CROSS = 7, FX_DRAIN = 8, FX_CRIT = 9;
    private static final int ELEM_FORCE = 0, ELEM_FIRE = 1, ELEM_WATER = 2,
            ELEM_ACID = 3, ELEM_OIL = 4, ELEM_BLOOD = 5, ELEM_ITEM = 6;
    private static final int[] CROSS_DX = {0, 0, 0, 1, -1}, CROSS_DY = {0, -1, 1, 0, 0};
    private static final String[] NUMS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
            "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31"
    };

    private static final Color BG = Color.valueOf("090B0E"), PANEL = Color.valueOf("11151C"),
            BORDER = Color.valueOf("232B38"), BAR_BG = Color.valueOf("0A0C10"),
            FIRE = Color.valueOf("F07148"), EMBER = Color.valueOf("FACC15"),
            WATER_COL = Color.valueOf("1E4E79"), ICE_COL = Color.valueOf("7EC8E3"),
            STEAM_COL = Color.valueOf("C8D6E5"), SUPERHEATED_COL = Color.valueOf("F0B88A"),
            ACID_GLOW = Color.valueOf("A3E635"),
            MIST_COL = Color.valueOf("D97706"), STUN_COL = Color.valueOf("FACC15"),
            BLEED_COL = Color.valueOf("DC2626"), BOLT_COL = Color.valueOf("67E8F9"),
            BLOOD_BRIGHT = Color.valueOf("F43F5E"), BLOOD_DEEP = Color.valueOf("991B1B"),
            THRALL = Color.valueOf("4CC9B0"), CREATURE = Color.valueOf("E05A5A"),
            PROP = Color.valueOf("D4A359"), DOOR = Color.valueOf("8C7A6B"),
            TEXT = Color.valueOf("D8DEE9"), MUTED = Color.valueOf("5E6C80"), SAGE = Color.valueOf("68B088");

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
    private BitmapFont font, badgeFont;
    private final SynthAudio audio = new SynthAudio();
    private float charW = 8f, fontH = 14f;
    private final FitViewport viewport = new FitViewport(WIN_W, WIN_H);

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

    private final float[] hitFlash = new float[MAX_ENT],
            lungeX = new float[MAX_ENT], lungeY = new float[MAX_ENT], lungeAge = new float[MAX_ENT];
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
        MonoFont primary = MonoFont.build(14);
        font = primary.font;
        charW = primary.charW;
        fontH = primary.fontH;
        badgeFont = MonoFont.build(10).font;
        audio.init();

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
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (!draggingScroll) return false;
                viewport.unproject(tmpMouse.set(screenX, screenY));
                dragScrollbar(tmpMouse.y);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT || !draggingScroll) return false;
                draggingScroll = false;
                viewport.unproject(tmpMouse.set(screenX, screenY));
                hoverScroll = maxScroll() > 0 && inScrollbar(tmpMouse.x, tmpMouse.y);
                return true;
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
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

    void stepAnim(float dt) {
        anim += updateEffects(dt);
    }

    int thrallDrawSprite(int entFrame) {
        if (cur.frozenEntity[0]) return PixelAtlas.THRALL_0;
        if (cur.proneEntity[0]) return PixelAtlas.THRALL_0 + entFrame;
        if (thrallPose != PixelAtlas.POSE_IDLE && thrallPoseAge < thrallPoseMax) {
            int f = Math.min(3, (int) ((thrallPoseAge / thrallPoseMax) * 4f));
            return PixelAtlas.thrallSprite(thrallPose, f);
        }
        if (prevIdx[0] >= 0 && cur.moved(0, prev) && anim < SLIDE) {
            int f = Math.min(3, (int) ((anim / SLIDE) * 4f));
            return PixelAtlas.thrallSprite(PixelAtlas.POSE_MOVE, f);
        }
        if (hitFlash[0] > 0) return PixelAtlas.THRALL_HURT;
        return PixelAtlas.THRALL_0 + entFrame;
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
        font.dispose();
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
                playSfxAt(SynthAudio.SHOOT, 0.62f, 0.12f, cur != null ? cur.x[0] + 0.5f : 0f);
                if (cur != null) {
                    spawnFx(FX_RING, cur.x[0] + 0.5f, cur.y[0] + 0.5f, 1.1f, 0f, 0.3f, CREATURE);
                    triggerShake(0.18f, 3f);
                }
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
        audio.playTierDelayed(SynthAudio.CRIT, tier - 1, curVfxDelay, 0.70f + 0.06f * tier, 1f + (nextRand01() - 0.5f) * 0.06f, panFor(cx));
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
                    playSfxAtPitched(SynthAudio.ZAP, 0.74f, pipePitch, 0.12f, tx);
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
                    playSfxAtPitched(SynthAudio.BLOOD_BOLT, 0.52f, pipePitch * 0.92f, 0.10f, tx);
                } else if (elem == ELEM_FIRE) {
                    spawnCrossBurst(ex, ey, 6, EMBER, -0.9f);
                    playSfxAtPitched(SynthAudio.FIRE, 0.60f, pipePitch, 0.12f, tx);
                } else {
                    spawnCrossBurst(ex, ey, 5, ec, -0.6f);
                    if (elem == ELEM_ACID) playSfxAtPitched(SynthAudio.SIZZLE, 0.54f, pipePitch, 0.12f, tx);
                    else if (elem == ELEM_WATER || elem == ELEM_OIL) playSfxAtPitched(SynthAudio.SPLASH, 0.54f, pipePitch, 0.12f, tx);
                }
                triggerShake(0.24f, 4f);
                playSfxAtPitched(SynthAudio.BOOM, 0.68f, pipePitch, 0.12f, tx);
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
                setThrallPose(PixelAtlas.POSE_ATTACK, 0.36f, ex, nTargets > 0, elem);
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
                playSfxAtPitched(SynthAudio.SIPHON, 0.62f, pipePitch, 0.10f, tx);
                if (elem == ELEM_FIRE || elem == ELEM_ACID || elem == ELEM_WATER || elem == ELEM_OIL) {
                    int hint = elem == ELEM_FIRE ? SynthAudio.FIRE
                            : elem == ELEM_ACID ? SynthAudio.SIZZLE : SynthAudio.SPLASH;
                    playSfxAtPitched(hint, 0.26f, pipePitch, 0.12f, tx);
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
                setThrallPose(PixelAtlas.POSE_ATTACK, 0.36f, ex, nTargets > 0, elem);
                if (nTargets > 0) {
                    lungeX[0] = (tx - sx) * 0.45f;
                    lungeY[0] = (ty - sy) * 0.45f;
                    lungeAge[0] = 0.22f;
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
        if (ai < 0 || ai >= MAX_ENT) return;
        boolean ranged = false;
        for (int i = 0; i < e.lines().size(); i++) {
            String l = e.lines().get(i);
            if (l.contains("fires") || l.contains("shoots") || l.contains("arrow") || l.contains("bow")) {
                ranged = true;
                break;
            }
        }
        float ax = e.to().x() + 0.5f, ay = e.to().y() + 0.5f;
        float tx = cur.x[0] + 0.5f, ty = cur.y[0] + 0.5f;
        if (ranged) {
            spawnFx(FX_PROJ, ax, ay, tx, ty, 0.28f, PROP);
            playSfxAt(SynthAudio.SHOOT, 0.52f, 0.14f, ax);
        } else if (Math.abs(e.to().x() - cur.x[0]) <= 1 && Math.abs(e.to().y() - cur.y[0]) <= 1) {
            lungeX[ai] = (tx - ax) * 0.45f;
            lungeY[ai] = (ty - ay) * 0.45f;
            lungeAge[ai] = 0.22f;
            playSfxAt(SynthAudio.SWING, 0.40f, 0.16f, ax);
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
        float impactDelay = lastPipeDelay > 0f ? lastPipeDelay + 0.10f : 0f;
        int firstDamagedIdx = -1, firstDamagedDelta = 0, firstDamagedSlot = -1, normalHitCount = 0;
        for (int i = 0; i < s.n; i++) {
            prevIdx[i] = p == null ? -1 : p.indexOf(s.id[i]);
            if (i == 0 && prevIdx[0] >= 0 && s.moved(0, p)) {
                int dx = s.x[0] - p.x[prevIdx[0]];
                if (dx != 0) thrallFacing = dx > 0 ? 1 : -1;
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
                    curVfxDelay = impactDelay;
                    spawnBurst(s.x[i] + 0.5f, s.y[i] + 0.5f, 14, bloodOrSpark, -0.4f);
                    int critSlot = consumeCritFor(s.name[i]);
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
            else if (lastImpactElem == ELEM_BLOOD) playSfxAtPitched(SynthAudio.BLOOD_BOLT, 0.30f, 0.92f, 0.10f, tx);
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
                            (nextRand01() - 0.5f) * 0.3f, -0.8f - nextRand01() * 0.6f, 0.55f, 3f, fc);
                } else if ((cur.steam[t] || cur.superheatedSteam[t] || cur.oilMist[t]) && nextRand01() < 0.22f) {
                    Color gc = cur.superheatedSteam[t] ? SUPERHEATED_COL : cur.oilMist[t] ? MIST_COL : STEAM_COL;
                    spawnParticle(x + 0.15f + nextRand01() * 0.7f, y + 0.2f + nextRand01() * 0.6f,
                            (nextRand01() - 0.5f) * 0.25f, -0.35f, 0.75f, 4f, gc);
                } else if (cur.acid[t] && nextRand01() < 0.14f) {
                    spawnParticle(x + 0.2f + nextRand01() * 0.6f, y + 0.3f + nextRand01() * 0.4f,
                            0f, -0.45f, 0.45f, 2.5f, ACID_GLOW);
                }
            }
        }
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            if (cur.burningEntity[i] && nextRand01() < 0.6f) {
                spawnParticle(cur.x[i] + 0.3f + nextRand01() * 0.4f, cur.y[i] + 0.4f,
                        (nextRand01() - 0.5f) * 0.3f, -0.9f, 0.5f, 3f, EMBER);
            } else if (cur.frozenEntity[i] && nextRand01() < 0.25f) {
                spawnParticle(cur.x[i] + 0.2f + nextRand01() * 0.6f, cur.y[i] + 0.2f + nextRand01() * 0.6f,
                        0f, -0.25f, 0.5f, 2.5f, ICE_COL);
            } else if (cur.bleedingEntity[i] && nextRand01() < 0.25f) {
                spawnParticle(cur.x[i] + 0.3f + nextRand01() * 0.4f, cur.y[i] + 0.5f,
                        0f, 0.45f, 0.4f, 2.5f, BLEED_COL);
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

    private float mapScreenX(float tx) { return MAP_X + tx * TILE + shakeX; }

    private float mapScreenY(float ty) { return WIN_H - MAP_Y - (ty + 1) * TILE + shakeY; }

    private float centerScreenY(float ty) { return WIN_H - MAP_Y - ty * TILE + shakeY; }

    private int mapW() { return cur.w * TILE; }

    private int mapH() { return cur.h * TILE; }

    private int sideX() { return MAP_X + mapW() + 16; }

    private int sideW() { return WIN_W - sideX() - 20; }

    private int logTop() { return MAP_Y + mapH() + 14; }

    private int logW() { return WIN_W - MAP_X - 20; }

    private int logHeight() { return INPUT_TOP - 10 - logTop(); }

    private int logY() { return WIN_H - logTop() - logHeight(); }

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

    private float drawX(int i) {
        int p = prevIdx[i];
        float base = (p < 0 || anim >= SLIDE) ? cur.x[i]
                : prev.x[p] + (cur.x[i] - prev.x[p]) * Interpolation.smooth.apply(anim / SLIDE);
        if (i < MAX_ENT && lungeAge[i] > 0) {
            base += lungeX[i] * MathUtils.sin((lungeAge[i] / 0.22f) * MathUtils.PI);
        }
        return base;
    }

    private float drawY(int i) {
        int p = prevIdx[i];
        float base = (p < 0 || anim >= SLIDE) ? cur.y[i]
                : prev.y[p] + (cur.y[i] - prev.y[p]) * Interpolation.smooth.apply(anim / SLIDE);
        if (i < MAX_ENT && lungeAge[i] > 0) {
            base += lungeY[i] * MathUtils.sin((lungeAge[i] / 0.22f) * MathUtils.PI);
        }
        return base;
    }

    private float rowMidY(int j) {
        return WIN_H - (logTop() + 7 + j * LINE_H) - fontH / 2f;
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
        int logW = logW(), logY = logY();
        int sideY = WIN_H - MAP_Y - mapH(), inputY = WIN_H - INPUT_TOP - INPUT_H;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(sideX(), sideY, sideW(), mapH());
        shapes.rect(MAP_X, logY, logW, logHeight());
        shapes.rect(MAP_X, inputY, logW, INPUT_H);
        shapes.setColor(THRALL.r, THRALL.g, THRALL.b, presenter.busy() ? 0.25f : 0.85f);
        shapes.rect(MAP_X, inputY, 3, INPUT_H);
        if (!presenter.busy() && ((int) (blinkTime * 2.5f)) % 2 == 0) {
            float promptTop = INPUT_TOP + (INPUT_H - fontH) / 2f;
            shapes.setColor(THRALL);
            shapes.rect(MAP_X + 12 + (8 + cursor) * charW, WIN_H - promptTop - fontH - 1, 2, fontH + 2);
        }

        int total = log.size(), visible = logLines();
        if (total > visible) {
            shapes.setColor(BAR_BG);
            shapes.rect(trackX(), trackY(), SCROLLBAR_W, trackH());
            shapes.setColor(draggingScroll ? THRALL : hoverScroll ? TEXT : MUTED);
            shapes.rect(trackX(), thumbY(), SCROLLBAR_W, thumbH());
        }

        // HUD status bars
        float hudX = sideX() + 16, barX = hudX + 92;
        float hpBarY = WIN_H - (MAP_Y + 44) - (fontH + 10) / 2f;
        float apBarY = WIN_H - (MAP_Y + 68) - (fontH + 10) / 2f;
        shapes.setColor(BAR_BG);
        shapes.rect(barX, hpBarY, 120, 10);
        shapes.setColor(hp * 3 <= maxHp ? CREATURE : THRALL);
        shapes.rect(barX, hpBarY, 120 * MathUtils.clamp(hp / (float) Math.max(1, maxHp), 0f, 1f), 10);
        int maxPips = Math.max(roundAp, ap);
        int pipStep = maxPips > 6 ? 16 : 19;
        int pipW = maxPips > 6 ? 12 : 14;
        for (int p = 0; p < maxPips; p++) {
            shapes.setColor(p < ap ? THRALL : BAR_BG);
            shapes.rect(barX + p * pipStep, apBarY, pipW, 10);
        }
        shapes.setColor(BORDER);
        shapes.rect(sideX() + 12, WIN_H - (MAP_Y + 152), sideW() - 24, 1);
        shapes.rect(sideX() + 12, WIN_H - (MAP_Y + 238), sideW() - 24, 1);

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

    private void drawSpriteScene() {
        int animFrame = ((int) (time * 5.0f)) & 3;
        batch.begin();

        // 1. Dungeon tiles, liquids, loot, gas, fire, dynamic lighting
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                int tileFrame = (animFrame + x * 3 + y * 5) & 3;
                float sx = mapScreenX(x), sy = mapScreenY(y);
                float lum = cur.light[t];
                if (lum > 0.42f) lum *= 0.95f + 0.05f * MathUtils.sin(time * 9f + x * 1.7f + y * 2.3f);
                float r = Math.min(1f, lum), g = Math.min(1f, lum * 0.97f), b = Math.min(1f, lum * 0.94f);
                batch.setColor(r, g, b, 1f);

                if (cur.wall[t]) {
                    boolean front = y + 1 < cur.h && !cur.wall[(y + 1) * cur.w + x];
                    batch.draw(atlas.regions[front ? PixelAtlas.WALL_FRONT : PixelAtlas.WALL_TOP], sx, sy, TILE, TILE);
                } else {
                    batch.draw(atlas.regions[(x + y) % 2 == 0 ? PixelAtlas.FLOOR_0 : PixelAtlas.FLOOR_1], sx, sy, TILE, TILE);
                    if (y > 0 && cur.wall[(y - 1) * cur.w + x]) {
                        batch.setColor(0f, 0f, 0f, 0.28f);
                        batch.draw(atlas.white, sx, sy + TILE - 12, TILE, 12);
                        batch.setColor(0f, 0f, 0f, 0.12f);
                        batch.draw(atlas.white, sx, sy + TILE - 20, TILE, 8);
                        batch.setColor(r, g, b, 1f);
                    }
                    if (cur.ice[t]) {
                        batch.draw(atlas.regions[PixelAtlas.ICE_TILE], sx, sy, TILE, TILE);
                        if (cur.tar[t]) batch.draw(atlas.regions[PixelAtlas.TAR_TILE], sx + 8, sy + 8, 48, 48);
                        else if (cur.acid[t]) batch.draw(atlas.regions[PixelAtlas.ACID_0 + tileFrame], sx + 8, sy + 8, 48, 48);
                        else if (cur.oil[t]) batch.draw(atlas.regions[PixelAtlas.OIL_SLICK], sx + 8, sy + 8, 48, 48);
                    } else if (cur.tar[t]) {
                        batch.draw(atlas.regions[PixelAtlas.TAR_TILE], sx, sy, TILE, TILE);
                    } else if (cur.acid[t]) {
                        batch.draw(atlas.regions[PixelAtlas.ACID_0 + tileFrame], sx, sy, TILE, TILE);
                    } else if (cur.water[t]) {
                        batch.draw(atlas.regions[PixelAtlas.WATER_0 + tileFrame], sx, sy, TILE, TILE);
                    } else if (cur.oil[t]) {
                        batch.draw(atlas.regions[PixelAtlas.OIL_SLICK], sx, sy, TILE, TILE);
                    }
                }

                if (cur.lootKind[t] != Scene.LOOT_NONE) {
                    float bob = Math.round(2f * MathUtils.sin(time * 3.5f + x + y));
                    batch.setColor(r, g, b, 1f);
                    batch.draw(atlas.regions[PixelAtlas.spriteForLoot(cur.lootKind[t])], sx + 8, sy + 6 + bob, 48, 48);
                }

                if (cur.steam[t] || cur.superheatedSteam[t] || cur.oilMist[t]) {
                    Color gc = cur.superheatedSteam[t] ? SUPERHEATED_COL : cur.oilMist[t] ? MIST_COL : STEAM_COL;
                    float drift = Math.round(MathUtils.sin(time * 2.2f + x * 1.1f + y) * 3f);
                    batch.setColor(gc.r, gc.g, gc.b, 0.48f);
                    batch.draw(atlas.regions[PixelAtlas.GAS_PUFF], sx + drift, sy + 2, TILE, TILE);
                }

                if (cur.burning[t]) {
                    batch.setColor(1f, 1f, 1f, 0.88f);
                    batch.draw(atlas.regions[PixelAtlas.FIRE_0 + tileFrame], sx, sy, TILE, TILE);
                }
            }
        }

        // 2. Entities: contact shadows, movement ghosts, sprites, frozen/burning overlays, HP bar
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            batch.setColor(0f, 0f, 0f, 0.44f);
            batch.draw(atlas.glow, px + 8, py - 2, TILE - 16, 16);

            int entFrame = (animFrame + cur.id[i].hashCode()) & 3;
            int spr = i == 0 ? thrallDrawSprite(entFrame) : PixelAtlas.spriteForEntity(cur, i, entFrame);
            float scaleX = (i == 0 && !cur.proneEntity[0]) ? thrallFacing : 1f;
            int p = prevIdx[i];
            if (p >= 0 && cur.moved(i, prev) && anim < GHOST_LIFE) {
                float ga = 0.35f * (1f - anim / GHOST_LIFE);
                batch.setColor(1f, 1f, 1f, ga);
                batch.draw(atlas.regions[spr], mapScreenX(prev.x[p]), mapScreenY(prev.y[p]),
                        TILE * 0.5f, TILE * 0.5f, TILE, TILE, scaleX, 1f, 0f);
            }

            boolean casting = i == 0 && thrallPose == PixelAtlas.POSE_CAST && thrallPoseAge < thrallPoseMax
                    && !cur.frozenEntity[0] && !cur.proneEntity[0];
            float castPulse = casting ? MathUtils.sin((thrallPoseAge / thrallPoseMax) * MathUtils.PI) : 0f;

            if (i < MAX_ENT && hitFlash[i] > 0) {
                batch.setColor(1f, 0.95f, 0.95f, 1f);
            } else if (cur.frozenEntity[i]) {
                batch.setColor(0.28f, 0.68f, 1f, 1f);
            } else {
                float eLum = MathUtils.clamp(cur.light[cur.y[i] * cur.w + cur.x[i]] + 0.22f + 0.26f * castPulse, 0.62f, 1f);
                batch.setColor(eLum, eLum, eLum, 1f);
            }
            float rot = cur.proneEntity[i] ? 90f : 0f;
            batch.draw(atlas.regions[spr], px, py, TILE * 0.5f, TILE * 0.5f, TILE, TILE, scaleX, 1f, rot);

            if (cur.frozenEntity[i]) {
                batch.setColor(0.55f, 0.90f, 1f, 0.36f);
                batch.draw(atlas.regions[PixelAtlas.ICE_TILE], px, py, TILE, TILE);
            }
            if (cur.burningEntity[i]) {
                batch.setColor(1f, 1f, 1f, 0.80f);
                batch.draw(atlas.regions[PixelAtlas.FIRE_0 + ((entFrame + 1) & 3)], px, py + 4, TILE, TILE);
            }

            if (cur.kind[i] == Entity.Kind.CREATURE || cur.hp[i] < cur.maxHp[i]) {
                batch.setColor(BAR_BG);
                batch.draw(atlas.white, px + 6, py + 2, TILE - 12, 4);
                batch.setColor(colorOf(cur, i));
                batch.draw(atlas.white, px + 6, py + 2, (TILE - 12) * cur.hp[i] / (float) Math.max(1, cur.maxHp[i]), 4);
            }
            // Dark pill backing for compact ID & overwatch tags
            String tag = i == 0 ? "@" : cur.id[i];
            batch.setColor(0.05f, 0.07f, 0.1f, 0.78f);
            batch.draw(atlas.white, px + 2, py + TILE - 15, tag.length() * 6 + 6, 13);
            if (cur.overwatchEntity[i]) {
                batch.draw(atlas.white, px + TILE - 14, py + TILE - 15, 11, 13);
            }
        }

        // 3. Crisp 3x/4x projectile & cross sprites + particles
        for (int i = 0; i < MAX_FX; i++) {
            if (fxAge[i] < 0f || fxAge[i] >= fxMaxAge[i]) continue;
            float u = fxAge[i] / fxMaxAge[i];
            float a = 1f - u;
            Color fc = fxCol[i];
            if (fxKind[i] == FX_FIREBALL) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                batch.setColor(1f, 1f, 1f, 0.95f);
                batch.draw(atlas.regions[PixelAtlas.FIRE_0 + animFrame], fx - 24, fy - 24, 48, 48);
            } else if (fxKind[i] == FX_ELEM_BOLT) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                int spr = switch (fxParam[i]) {
                    case ELEM_ACID -> PixelAtlas.ACID_0 + animFrame;
                    case ELEM_WATER -> PixelAtlas.WATER_0 + animFrame;
                    case ELEM_OIL -> PixelAtlas.OIL_SLICK;
                    default -> PixelAtlas.LOOT_STONE;
                };
                batch.setColor(1f, 1f, 1f, 0.96f);
                batch.draw(atlas.regions[spr], fx - 24, fy - 24, 48, 48);
            } else if (fxKind[i] == FX_CROSS) {
                int ex = MathUtils.floor(fxX0[i]), ey = MathUtils.floor(fxY0[i]);
                int elem = fxParam[i];
                for (int c = 0; c < CROSS_DX.length; c++) {
                    int cx = ex + CROSS_DX[c], cy = ey + CROSS_DY[c];
                    if (cx < 0 || cx >= cur.w || cy < 0 || cy >= cur.h || cur.wall[cy * cur.w + cx]) continue;
                    float tx = mapScreenX(cx), ty = mapScreenY(cy);
                    batch.setColor(fc.r, fc.g, fc.b, (elem == ELEM_BLOOD ? 0.42f : 0.32f) * a);
                    batch.draw(atlas.white, tx + 2, ty + 2, TILE - 4, TILE - 4);
                    int cFrame = (animFrame + c) & 3;
                    if (elem == ELEM_FIRE) {
                        batch.setColor(1f, 1f, 1f, 0.88f * a);
                        batch.draw(atlas.regions[PixelAtlas.FIRE_0 + cFrame], tx + 8, ty + 8, 48, 48);
                    } else if (elem == ELEM_ACID) {
                        batch.setColor(1f, 1f, 1f, 0.85f * a);
                        batch.draw(atlas.regions[PixelAtlas.ACID_0 + cFrame], tx + 8, ty + 8, 48, 48);
                    } else if (elem == ELEM_WATER) {
                        batch.setColor(1f, 1f, 1f, 0.85f * a);
                        batch.draw(atlas.regions[PixelAtlas.WATER_0 + cFrame], tx + 8, ty + 8, 48, 48);
                    } else if (elem == ELEM_OIL) {
                        batch.setColor(1f, 1f, 1f, 0.85f * a);
                        batch.draw(atlas.regions[PixelAtlas.OIL_SLICK], tx + 8, ty + 8, 48, 48);
                    } else {
                        batch.setColor(fc.r, fc.g, fc.b, 0.55f * a);
                        batch.draw(atlas.regions[PixelAtlas.GAS_PUFF], tx, ty, TILE, TILE);
                    }
                }
            }
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (partAge[i] < 0f || partAge[i] >= partMaxAge[i]) continue;
            float a = 1f - partAge[i] / partMaxAge[i];
            Color pc = partCol[i];
            batch.setColor(pc.r, pc.g, pc.b, a);
            batch.draw(atlas.white, mapScreenX(partX[i]), centerScreenY(partY[i]), partSize[i], partSize[i]);
        }

        // 4. Additive Linear radial glow pass (torches, fires, cast auras, spell blooms, crit flares)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                if (!cur.burning[t]) continue;
                float sx = mapScreenX(x), sy = mapScreenY(y);
                batch.setColor(1f, 0.55f, 0.18f, 0.34f);
                batch.draw(atlas.glow, sx - 20, sy - 20, TILE + 40, TILE + 40);
            }
        }
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            if (cur.litEntity[i]) {
                batch.setColor(1f, 0.60f, 0.20f, 0.34f);
                batch.draw(atlas.glow, px - 28, py - 28, TILE + 56, TILE + 56);
            } else if (i == 0 && cur.concentration > 0) {
                batch.setColor(0.30f, 0.90f, 0.85f, 0.30f);
                batch.draw(atlas.glow, px - 22, py - 22, TILE + 44, TILE + 44);
            }
            if (i == 0 && thrallPose == PixelAtlas.POSE_CAST && thrallPoseAge < thrallPoseMax
                    && !cur.frozenEntity[0] && !cur.proneEntity[0]) {
                float castPulse = MathUtils.sin((thrallPoseAge / thrallPoseMax) * MathUtils.PI);
                Color cc = thrallCastElem == ELEM_FORCE ? BOLT_COL : elemColor(thrallCastElem);
                float pad = 18f + 22f * castPulse;
                batch.setColor(cc.r, cc.g, cc.b, 0.65f * castPulse);
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
                float corrW = len + 34f, corrH = 44f;
                batch.setColor(0.55f, 0.88f, 1f, 0.42f * a);
                batch.draw(atlas.glow, mx - corrW * 0.5f, my - corrH * 0.5f,
                        corrW * 0.5f, corrH * 0.5f, corrW, corrH, 1f, 1f, angle);
                for (int rIdx = 1; rIdx <= 4; rIdx++) {
                    float rt = rIdx * 0.25f;
                    float rx = MathUtils.lerp(x0, px, rt), ry = MathUtils.lerp(y0, py, rt);
                    float rw = 16f + u * 18f, rh = 34f + u * 40f;
                    batch.setColor(0.85f, 0.96f, 1f, 0.44f * a);
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
                float corrW = len + 26f, corrH = 38f;
                batch.setColor(0.85f, 0.10f, 0.16f, 0.42f * a);
                batch.draw(atlas.glow, mx - corrW * 0.5f, my - corrH * 0.5f,
                        corrW * 0.5f, corrH * 0.5f, corrW, corrH, 1f, 1f, angle);
            } else if (fxKind[i] == FX_FIREBALL) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                batch.setColor(1f, 0.55f, 0.15f, 0.72f * a + 0.18f);
                batch.draw(atlas.glow, fx - TILE * 0.6f, fy - TILE * 0.6f, TILE * 1.2f, TILE * 1.2f);
            } else if (fxKind[i] == FX_ELEM_BOLT) {
                float fx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], u));
                float fy = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], u));
                float pu = Math.max(0f, u - 0.16f);
                float tx = mapScreenX(MathUtils.lerp(fxX0[i], fxX1[i], pu));
                float ty = centerScreenY(MathUtils.lerp(fxY0[i], fxY1[i], pu));
                batch.setColor(fc.r, fc.g, fc.b, 0.52f * a);
                batch.draw(atlas.glow, tx - TILE * 0.4f, ty - TILE * 0.4f, TILE * 0.8f, TILE * 0.8f);
                batch.setColor(fc.r, fc.g, fc.b, 0.72f * a + 0.16f);
                batch.draw(atlas.glow, fx - TILE * 0.55f, fy - TILE * 0.55f, TILE * 1.1f, TILE * 1.1f);
            } else if (fxKind[i] == FX_CRIT) {
                int tier = Math.max(1, fxParam[i] >>> 4);
                float cx = mapScreenX(fxX0[i]), cy = centerScreenY(fxY0[i]);
                float glow = TILE * (1.2f + 0.7f * u + 0.25f * tier);
                batch.setColor(fc.r, fc.g, fc.b, 0.80f * a);
                batch.draw(atlas.glow, cx - glow * 0.5f, cy - glow * 0.5f, glow, glow);
                float core = TILE * (0.85f - 0.30f * u);
                batch.setColor(1f, 0.96f, 0.72f, 0.95f * a);
                batch.draw(atlas.glow, cx - core * 0.5f, cy - core * 0.5f, core, core);
            }
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (critFlashTime > 0) {
            batch.setColor(1f, 0.90f, 0.45f, 0.20f * (critFlashTime / 0.14f));
            batch.draw(atlas.white, mapScreenX(0), mapScreenY(cur.h - 1), mapW(), mapH());
        }

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawLineOverlays() {
        float pulse = 0.55f + 0.35f * (float) Math.sin(time * 7);
        int logW = logW(), logY = logY(), sideY = WIN_H - MAP_Y - mapH(), inputY = WIN_H - INPUT_TOP - INPUT_H;
        int total = log.size(), visible = logLines(), scroll = scrollLines();
        int first = Math.max(0, total - visible - scroll);
        float hudX = sideX() + 16, barX = hudX + 92;
        float apBarY = WIN_H - (MAP_Y + 68) - (fontH + 10) / 2f;
        int maxPips = Math.max(roundAp, ap), pipStep = maxPips > 6 ? 16 : 19, pipW = maxPips > 6 ? 12 : 14;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(MAP_X + shakeX, sideY + shakeY, mapW(), mapH());
        shapes.rect(sideX(), sideY, sideW(), mapH());
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
        for (int p = 0; p < maxPips; p++) shapes.rect(barX + p * pipStep, apBarY, pipW, 10);

        // Entity status frames & overwatch threat markers
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            if (cur.frozenEntity[i]) { shapes.setColor(ICE_COL.r, ICE_COL.g, ICE_COL.b, 0.95f); shapes.rect(px + 3, py + 3, TILE - 6, TILE - 6); }
            if (cur.stunnedEntity[i] && !cur.frozenEntity[i]) { shapes.setColor(STUN_COL.r, STUN_COL.g, STUN_COL.b, pulse); shapes.rect(px + 3, py + 3, TILE - 6, TILE - 6); }
            if (cur.bleedingEntity[i]) { shapes.setColor(BLEED_COL.r, BLEED_COL.g, BLEED_COL.b, 0.9f); shapes.rect(px + 5, py + 5, TILE - 10, TILE - 10); }
            if (cur.overwatchEntity[i]) {
                shapes.setColor(CREATURE.r, CREATURE.g, CREATURE.b, 0.30f * pulse);
                shapes.line(px + TILE * 0.5f, py + TILE * 0.5f, mapScreenX(cur.x[0] + 0.5f), centerScreenY(cur.y[0] + 0.5f));
            }
            if (cur.id[i].equals(actingId)) {
                shapes.setColor(TEXT.r, TEXT.g, TEXT.b, pulse);
                shapes.rect(px + 1, py + 1, TILE - 2, TILE - 2);
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
                shapes.setColor(BOLT_COL.r, BOLT_COL.g, BOLT_COL.b, 0.90f * pulse);
                shapes.rect(rx + 2, ry + 2, TILE - 4, TILE - 4);
                shapes.line(rx + TILE * 0.5f, ry + 2, rx + TILE * 0.5f, ry + 10);
                shapes.line(rx + TILE * 0.5f, ry + TILE - 10, rx + TILE * 0.5f, ry + TILE - 2);
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
                shapes.circle(cx, cy, MathUtils.lerp(8f, (0.85f + 0.25f * tier) * TILE, ease), 24);
            }
        }
        shapes.end();
    }

    private void text(String s, float x, float topY) {
        font.draw(batch, s, x, WIN_H - topY);
    }

    private void drawText() {
        batch.begin();
        batch.setColor(Color.WHITE);

        // Map coordinate rulers
        font.setColor(MUTED);
        for (int x = 0; x < cur.w; x++) {
            String xs = numStr(x);
            text(xs, mapScreenX(x) + (TILE - xs.length() * charW) / 2f, MAP_Y - fontH - 4 - shakeY);
        }
        for (int y = 0; y < cur.h; y++) text(numStr(y), MAP_X - 18 + shakeX, MAP_Y + y * TILE + (TILE - fontH) / 2f - shakeY);

        // Entity labels & floating damage numbers
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            String label = i == 0 ? "@" : cur.id[i];
            float px = mapScreenX(drawX(i)), py = mapScreenY(drawY(i));
            badgeFont.setColor(i == 0 ? THRALL : TEXT);
            badgeFont.draw(batch, label, px + 5, py + TILE - 3.5f);
            if (cur.overwatchEntity[i]) {
                badgeFont.setColor(CREATURE);
                badgeFont.draw(batch, "!", px + TILE - 11, py + TILE - 3.5f);
            }
        }
        for (int i = 0; i < MAX_FLOATERS; i++) {
            if (floatAge[i] >= FLOAT_LIFE) continue;
            float alpha = 1f - floatAge[i] / FLOAT_LIFE;
            boolean crit = floatText[i].startsWith("CRIT");
            Color fc = crit ? EMBER : floatText[i].startsWith("-") ? CREATURE : SAGE;
            font.setColor(fc.r, fc.g, fc.b, alpha);
            float xOff = crit ? 4f : 14f;
            font.draw(batch, floatText[i], mapScreenX(floatX[i]) + xOff,
                    mapScreenY(floatY[i]) + TILE - 6 + floatAge[i] * (crit ? 30f : 24f));
        }

        // Right panel: Compact Thrall HUD + Equipment / Pack
        float px = sideX() + 16, py = MAP_Y + 18;
        font.setColor(TEXT);
        text("ROUND " + round, px, py);
        if (!game.world.thrall().alive()) {
            font.setColor(CREATURE);
            text("LINK SEVERED", px + 96, py);
        } else if (thinking > 0) {
            font.setColor(PROP);
            text("HOSTILES" + ".".repeat(thinking), px + 96, py);
        } else {
            badgeFont.setColor(MUTED);
            String audioLabel = audio.muted ? "F2:MUTE" : (audio.musicMuted ? "F2:SFX F3:OFF" : "F2:SFX F3:BGM");
            badgeFont.draw(batch, audioLabel, sideX() + sideW() - 118, WIN_H - py - 2);
        }

        font.setColor(MUTED);
        text("VITALITY", px, MAP_Y + 44);
        text("ACTION PTS", px, MAP_Y + 68);
        font.setColor(TEXT);
        text(hp + "/" + maxHp, px + 220, MAP_Y + 44);
        text(ap + "/" + roundAp, px + 220, MAP_Y + 68);

        font.setColor(MUTED);
        text("MAGIC", px, MAP_Y + 90);
        text("TEMP", px + 116, MAP_Y + 90);
        text("CHANNEL", px, MAP_Y + 110);
        text("CONDS", px, MAP_Y + 130);
        font.setColor(TEXT);
        text(numStr(cur.magic), px + 56, MAP_Y + 90);
        text(cur.temp + "C", px + 164, MAP_Y + 90);
        String chanText = cur.concentration > 0
                ? cur.channel + "  CONC " + cur.concentration + "%"
                : cur.channel;
        text(chanText, px + 74, MAP_Y + 110);
        font.setColor(cur.thrallStunned || cur.thrallFrozen ? CREATURE : TEXT);
        String condLine = cur.shortConds.length() > 26 ? cur.shortConds.substring(0, 24) + ".." : cur.shortConds;
        text(condLine, px + 58, MAP_Y + 130);

        font.setColor(MUTED);
        text("EQUIPMENT", px, MAP_Y + 164);
        text("R.HAND", px, MAP_Y + 188);
        text("L.HAND", px, MAP_Y + 210);
        font.setColor("empty".equals(cur.rightHand) ? MUTED : cur.rightHand.contains("[LIT]") ? PROP : TEXT);
        text(cur.rightHand, px + 66, MAP_Y + 188);
        font.setColor("empty".equals(cur.leftHand) ? MUTED : cur.leftHand.contains("[LIT]") ? PROP : TEXT);
        text(cur.leftHand, px + 66, MAP_Y + 210);

        font.setColor(MUTED);
        text("PACK (" + cur.packCount + "/" + cur.packMax + ")", px, MAP_Y + 248);
        py = MAP_Y + 272;
        for (int i = 0; i < cur.packItems.length; i++) {
            String item = cur.packItems[i];
            font.setColor(item.contains("[LIT]") ? PROP : TEXT);
            text("- " + item, px + 4, py);
            py += LINE_H;
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
            text(jumpLabel(), jumpPillX() + 8, WIN_H - jumpPillY() - (JUMP_PILL_H + fontH) / 2f);
        }
        font.setColor(presenter.busy() ? MUTED : THRALL);
        float promptTop = INPUT_TOP + (INPUT_H - fontH) / 2f;
        text("ARCHON>", MAP_X + 12, promptTop);
        font.setColor(presenter.busy() ? MUTED : TEXT);
        font.draw(batch, input, MAP_X + 12 + 8 * charW, WIN_H - promptTop);

        batch.end();
    }
}
