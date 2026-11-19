package com.archon.ui;

import com.archon.app.Game;
import com.archon.event.GameEvent;
import com.archon.exec.RoundState;
import com.archon.model.Entity;
import com.archon.model.World;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * Pixel front end. Everything is laid out in a fixed WIN_W x WIN_H virtual space (FitViewport scales it):
 * map (ids, sliding movement, origin ghosts, damage numbers), side panel (HUD, legend),
 * structured scrollable log, and a separate command line.
 */
public final class GdxView extends ApplicationAdapter implements View {
    public static final int WIN_W = 1100, WIN_H = 720;
    private static final int TILE = 48, MAP_X = 32, MAP_Y = 28, LINE_H = 22, MAX_FLOATERS = 16;
    private static final int INPUT_TOP = WIN_H - 54, INPUT_H = 34, SCROLLBAR_W = 8, JUMP_PILL_H = 22;
    private static final float SLIDE = 0.4f, FLOAT_LIFE = 1.6f, SCROLL_LINES = 1.0f;

    private static final Color BG = Color.valueOf("090B0E"), PANEL = Color.valueOf("11151C"),
            BORDER = Color.valueOf("232B38"), FLOOR = Color.valueOf("131820"), FLOOR_ALT = Color.valueOf("151A23"),
            WALL = Color.valueOf("1E2530"), WALL_BEVEL = Color.valueOf("2A3342"),
            TOKEN_BG = Color.valueOf("10141B"), BAR_BG = Color.valueOf("0A0C10"),
            OIL = Color.valueOf("1F1A12"), OIL_RIM = Color.valueOf("3D311E"), FIRE = Color.valueOf("F07148"),
            WATER_COL = Color.valueOf("1E4E79"), ICE_COL = Color.valueOf("7EC8E3"),
            ACID_COL = Color.valueOf("5FA832"), TAR_COL = Color.valueOf("3B2F2F"),
            STEAM_COL = Color.valueOf("C8D6E5"), CAUSTIC_COL = Color.valueOf("A3E635"),
            MIST_COL = Color.valueOf("D97706"), STUN_COL = Color.valueOf("FACC15"),
            BLEED_COL = Color.valueOf("DC2626"),
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
    private BitmapFont font;
    private int charW = 8, fontH = 14;
    private final FitViewport viewport = new FitViewport(WIN_W, WIN_H);

    private Scene cur, prev;
    private int[] prevIdx = new int[0];
    private float anim = SLIDE, time, blinkTime;
    private int hp, maxHp, ap, roundAp, round, thinking, curGroup = G_NONE, curBlock, nextBlock = 1;
    private String actingId = "";
    private final ArrayList<LogRow> log = new ArrayList<>();
    private LogRow openStage;
    private float logScroll; // lines scrolled back from the newest
    private boolean draggingScroll, hoverScroll, hoverJump;
    private float dragOffset;
    private final Vector2 tmpMouse = new Vector2();
    private int lastJumpLines = -1;
    private String jumpLabel = "";

    private final int[] floatX = new int[MAX_FLOATERS], floatY = new int[MAX_FLOATERS];
    private final String[] floatText = new String[MAX_FLOATERS];
    private final float[] floatAge = new float[MAX_FLOATERS];
    private int nextFloater;

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
    }

    // ---- lifecycle ----

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = buildMonoFont(14);

        game = new Game(seed, magicArena);
        presenter = new Presenter(this, game.world);
        game.bus.subscribe(presenter::handle);
        game.start();
        frame(game.world, game.round);

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char c) {
                if (c == '\r' || c == '\n') submit();
                else editInputChar(c);
                return true;
            }

            @Override
            public boolean keyDown(int key) {
                if (key == Input.Keys.LEFT) moveCursor(-1);
                if (key == Input.Keys.RIGHT) moveCursor(1);
                if (key == Input.Keys.FORWARD_DEL) deleteForward();
                if (key == Input.Keys.UP && historyPos > 0) recall(--historyPos);
                if (key == Input.Keys.DOWN && historyPos < history.size()) recall(++historyPos);
                if (key == Input.Keys.PAGE_UP) setLogScroll(logScroll + Math.max(1, logLines() - 1));
                if (key == Input.Keys.PAGE_DOWN) setLogScroll(logScroll - Math.max(1, logLines() - 1));
                if (key == Input.Keys.HOME) setLogScroll(maxScroll());
                if (key == Input.Keys.END) setLogScroll(0f);
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

    /** Rasterizes a crisp anti-aliased system monospace font (Menlo / Monaco / Monospaced) into a LibGDX BitmapFont. */
    private BitmapFont buildMonoFont(int sizePx) {
        System.setProperty("java.awt.headless", "true");
        Font awtFont = new Font(Font.MONOSPACED, Font.PLAIN, sizePx);
        for (String name : new String[]{"JetBrains Mono", "SF Mono", "Menlo", "Monaco", "Consolas"}) {
            Font candidate = new Font(name, Font.PLAIN, sizePx);
            if (!Font.DIALOG.equals(candidate.getFamily())) { awtFont = candidate; break; }
        }
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = scratch.createGraphics();
        g0.setFont(awtFont);
        FontMetrics fm = g0.getFontMetrics();
        charW = Math.max(7, fm.charWidth('M'));
        int ascent = fm.getAscent(), descent = fm.getDescent(), cellH = ascent + descent + 4, cellW = charW + 4;
        fontH = ascent + descent;
        g0.dispose();

        int texW = 256, texH = 256, cols = texW / cellW;
        BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(awtFont);
        g.setColor(java.awt.Color.WHITE);

        BitmapFont bf = new BitmapFont();
        bf.setUseIntegerPositions(true);
        TextureRegion reg = bf.getRegion();
        int glyphH = ascent + descent;
        for (int c = 32; c < 127; c++) {
            int idx = c - 32, gx = (idx % cols) * cellW, gy = (idx / cols) * cellH;
            g.drawString(String.valueOf((char) c), gx + 1, gy + ascent);
            BitmapFont.Glyph glyph = bf.getData().getGlyph((char) c);
            if (glyph != null) {
                glyph.srcX = gx + 1;
                glyph.srcY = gy;
                glyph.width = charW;
                glyph.height = glyphH;
                glyph.xoffset = 0;
                glyph.yoffset = -glyphH;
                glyph.xadvance = charW;
            }
        }
        g.dispose();

        Pixmap pm = new Pixmap(texW, texH, Pixmap.Format.RGBA8888);
        int[] argb = img.getRGB(0, 0, texW, texH, null, 0, texW);
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                int p = argb[y * texW + x];
                pm.drawPixel(x, y, (p << 8) | ((p >>> 24) & 0xFF));
            }
        }
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        reg.setTexture(tex);
        for (int c = 32; c < 127; c++) {
            BitmapFont.Glyph glyph = bf.getData().getGlyph((char) c);
            if (glyph != null) {
                bf.getData().setGlyphRegion(glyph, reg);
                glyph.yoffset = -glyphH; // restore after setGlyphRegion overwrote it!
            }
        }
        bf.getData().lineHeight = cellH;
        bf.getData().capHeight = glyphH;
        bf.getData().ascent = 0;
        bf.getData().descent = -descent;
        return bf;
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
        addRow(K_CMD, G_PLAYER, "> " + raw, THRALL, null, null);
        game.submit(raw);
        dirty = true;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    // ---- View: events and scenes ----

    private int maxChars(boolean hasBadge) {
        return Math.max(40, (logW() - (hasBadge ? 190 : 40)) / charW);
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

    private void addMultiline(int kind, int group, String text, Color color) {
        for (String line : text.split("\\R")) addRow(kind, group, line, color, null, null);
    }

    /** Merges primary stage action + status pill, wrapping extra narrations underneath. */
    @Override
    public void handle(GameEvent event) {
        switch (event) {
            case GameEvent.Narrative e -> {
                String text = e.text();
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
                    openStage = null; // subsequent narrations (e.g. crippled/blinded effects) get their own line
                } else {
                    openStage = null;
                    String prefix = curGroup == G_NONE ? "" : "    ";
                    for (String line : text.split("\\R")) addRow(K_TEXT, curGroup, prefix + line, c, null, null);
                }
            }
            case GameEvent.Rejected e -> {
                openStage = null;
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
                addRow(K_ALERT, G_PLAYER, "  " + e.reason(), CREATURE, "BREAK | -" + e.forfeited() + " AP", CREATURE);
            }
            case GameEvent.InterruptFired e -> {
                openStage = null;
                addRow(K_ALERT, G_ENV, "  " + e.source() + ": " + e.description(), PROP, "REACT | -" + e.damage() + " HP", PROP);
            }
            case GameEvent.EnemyPhaseStart e -> {
                openStage = null;
                curGroup = G_HOSTILE;
                curBlock = 0;
                addRow(K_PHASE, G_HOSTILE, "HOSTILE PHASE", CREATURE, null, null);
            }
            case GameEvent.ActorBeat e -> {
                actingId = e.id();
                curGroup = G_HOSTILE;
                curBlock = nextBlock++;
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
                for (String l : e.lines()) addRow(K_ALERT, G_ENV, "  " + l, FIRE, "HAZARD", FIRE);
            }
            case GameEvent.Thinking e -> thinking = e.dots();
            case GameEvent.RoundStart e -> {
                thinking = 0;
                actingId = "";
                curGroup = G_NONE;
                curBlock = 0;
                openStage = null;
                round = e.round();
                ap = e.ap();
                addRow(K_ROUND, G_NONE, "ROUND " + e.round(), THRALL, e.ap() + " AP READY", THRALL);
            }
            case GameEvent.ThrallDied e -> {
                curBlock = 0;
                addRow(K_ALERT, G_NONE, "THE THRALL COLLAPSES. THE LINK GOES DARK.", CREATURE, "FATAL", CREATURE);
            }
            default -> { }
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
        anim = 0;
        prevIdx = new int[s.n];
        for (int i = 0; i < s.n; i++) {
            prevIdx[i] = p == null ? -1 : p.indexOf(s.id[i]);
            int delta = p == null ? 0 : s.hpDelta(i, p);
            if (delta != 0) {
                floatX[nextFloater] = s.x[i];
                floatY[nextFloater] = s.y[i];
                floatText[nextFloater] = (delta > 0 ? "+" : "") + delta;
                floatAge[nextFloater] = 0;
                nextFloater = (nextFloater + 1) % MAX_FLOATERS;
            }
        }
    }

    // ---- frame loop ----

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        time += dt;
        anim += dt;
        blinkTime += dt;
        for (int i = 0; i < MAX_FLOATERS; i++) floatAge[i] += dt;

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
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        drawShapes();
        drawText();
    }

    private float screenX(float tx) { return MAP_X + tx * TILE; }

    private float screenY(float ty) { return WIN_H - MAP_Y - (ty + 1) * TILE; }

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
        if (p < 0 || anim >= SLIDE) return cur.x[i];
        return prev.x[p] + (cur.x[i] - prev.x[p]) * Interpolation.smooth.apply(anim / SLIDE);
    }

    private float drawY(int i) {
        int p = prevIdx[i];
        if (p < 0 || anim >= SLIDE) return cur.y[i];
        return prev.y[p] + (cur.y[i] - prev.y[p]) * Interpolation.smooth.apply(anim / SLIDE);
    }

    private static void drawToken(ShapeRenderer sr, Entity.Kind kind, float px, float py) {
        if (kind == Entity.Kind.DOOR) sr.rect(px + 4, py + 14, TILE - 8, TILE - 28);
        else if (kind == Entity.Kind.PROP) sr.rect(px + 9, py + 11, TILE - 18, TILE - 20);
        else sr.rect(px + 6, py + 10, TILE - 12, TILE - 16);
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

    private void drawShapes() {
        float pulse = 0.55f + 0.35f * (float) Math.sin(time * 7);
        int logW = logW(), logY = logY();
        int sideY = WIN_H - MAP_Y - mapH(), inputY = WIN_H - INPUT_TOP - INPUT_H;

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

        // HUD status bars (centered vertically on fontH)
        float hudX = sideX() + 16, barX = hudX + 100;
        float hpBarY = WIN_H - (MAP_Y + 44) - (fontH + 10) / 2f;
        float apBarY = WIN_H - (MAP_Y + 68) - (fontH + 10) / 2f;
        shapes.setColor(BAR_BG);
        shapes.rect(barX, hpBarY, 150, 10);
        shapes.setColor(hp * 3 <= maxHp ? CREATURE : THRALL);
        shapes.rect(barX, hpBarY, 150 * MathUtils.clamp(hp / (float) Math.max(1, maxHp), 0f, 1f), 10);
        int maxPips = Math.max(roundAp, ap);
        int pipStep = maxPips > 6 ? 18 : 22;
        int pipW = maxPips > 6 ? 14 : 16;
        for (int p = 0; p < maxPips; p++) {
            shapes.setColor(p < ap ? THRALL : BAR_BG);
            shapes.rect(barX + p * pipStep, apBarY, pipW, 10);
        }
        shapes.setColor(BORDER);
        shapes.rect(sideX() + 12, WIN_H - (MAP_Y + 148), sideW() - 24, 1);

        // Roster row highlight + aligned mini HP bars
        float rowTop = MAP_Y + 158;
        float rBarX = sideX() + sideW() - 175;
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            rowTop += LINE_H;
            float midY = WIN_H - rowTop - fontH / 2f;
            if (cur.id[i].equals(actingId)) {
                shapes.setColor(BORDER.r, BORDER.g, BORDER.b, 0.65f);
                shapes.rect(sideX() + 8, midY - LINE_H / 2f, sideW() - 16, LINE_H - 1);
            }
            Color c = colorOf(cur, i);
            shapes.setColor(c.r, c.g, c.b, 0.9f);
            shapes.rect(sideX() + 14, midY - 6, 3, 12);
            shapes.setColor(BAR_BG);
            shapes.rect(rBarX, midY - 3, 46, 6);
            shapes.setColor(c);
            shapes.rect(rBarX, midY - 3, 46 * (cur.hp[i] / (float) cur.maxHp[i]), 6);
        }

        // Structured log card backgrounds, banners, and right-aligned pill backgrounds
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
                int pillW = r.badge.length() * charW + 14;
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

        // Map tiles
        for (int y = 0; y < cur.h; y++) {
            for (int x = 0; x < cur.w; x++) {
                int t = y * cur.w + x;
                float sx = screenX(x), sy = screenY(y);
                if (cur.wall[t]) {
                    shapes.setColor(WALL_BEVEL);
                    shapes.rect(sx + 1, sy + 1, TILE - 2, TILE - 2);
                    shapes.setColor(WALL);
                    shapes.rect(sx + 2, sy + 1, TILE - 3, TILE - 4);
                } else {
                    shapes.setColor((x + y) % 2 == 0 ? FLOOR : FLOOR_ALT);
                    shapes.rect(sx + 1, sy + 1, TILE - 2, TILE - 2);
                    if (cur.toxicTar[t]) {
                        shapes.setColor(TAR_COL);
                        shapes.rect(sx + 4, sy + 4, TILE - 8, TILE - 8);
                    } else if (cur.ice[t]) {
                        shapes.setColor(ICE_COL);
                        shapes.rect(sx + 4, sy + 4, TILE - 8, TILE - 8);
                        if (cur.acid[t]) {
                            shapes.setColor(ACID_COL);
                            shapes.rect(sx + 10, sy + 10, TILE - 20, TILE - 20);
                        } else if (cur.oil[t]) {
                            shapes.setColor(OIL);
                            shapes.rect(sx + 10, sy + 10, TILE - 20, TILE - 20);
                        }
                    } else if (cur.acid[t]) {
                        shapes.setColor(ACID_COL);
                        shapes.rect(sx + 5, sy + 5, TILE - 10, TILE - 10);
                    } else if (cur.water[t]) {
                        shapes.setColor(WATER_COL);
                        shapes.rect(sx + 5, sy + 5, TILE - 10, TILE - 10);
                    } else if (cur.oil[t]) {
                        shapes.setColor(OIL_RIM);
                        shapes.rect(sx + 4, sy + 4, TILE - 8, TILE - 8);
                        shapes.setColor(OIL);
                        shapes.rect(sx + 6, sy + 6, TILE - 12, TILE - 12);
                    }
                }
                if (cur.steam[t]) {
                    shapes.setColor(STEAM_COL.r, STEAM_COL.g, STEAM_COL.b, 0.38f);
                    shapes.rect(sx + 2, sy + 2, TILE - 4, TILE - 4);
                } else if (cur.causticGas[t]) {
                    shapes.setColor(CAUSTIC_COL.r, CAUSTIC_COL.g, CAUSTIC_COL.b, 0.38f);
                    shapes.rect(sx + 2, sy + 2, TILE - 4, TILE - 4);
                } else if (cur.oilMist[t]) {
                    shapes.setColor(MIST_COL.r, MIST_COL.g, MIST_COL.b, 0.38f);
                    shapes.rect(sx + 2, sy + 2, TILE - 4, TILE - 4);
                }
                if (cur.burning[t]) {
                    shapes.setColor(FIRE.r, FIRE.g, FIRE.b, pulse * 0.45f);
                    shapes.rect(sx + 3, sy + 3, TILE - 6, TILE - 6);
                }
                if (cur.loot[t]) {
                    shapes.setColor(PROP);
                    shapes.rect(sx + TILE - 12, sy + 4, 6, 6);
                }
            }
        }

        // Entity tokens
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            float px = screenX(drawX(i)), py = screenY(drawY(i));
            Color c = colorOf(cur, i);
            shapes.setColor(TOKEN_BG);
            drawToken(shapes, cur.kind[i], px, py);
            shapes.setColor(c.r, c.g, c.b, 0.22f);
            drawToken(shapes, cur.kind[i], px, py);
            if (cur.kind[i] == Entity.Kind.CREATURE || cur.hp[i] < cur.maxHp[i]) {
                shapes.setColor(BAR_BG);
                shapes.rect(px + 6, py + 4, TILE - 12, 3);
                shapes.setColor(c);
                shapes.rect(px + 6, py + 4, (TILE - 12) * cur.hp[i] / (float) cur.maxHp[i], 3);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(MAP_X, sideY, mapW(), mapH());
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

        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            float px = screenX(drawX(i)), py = screenY(drawY(i));
            Color c = colorOf(cur, i);
            int p = prevIdx[i];
            if (p >= 0 && cur.moved(i, prev)) {
                shapes.setColor(c.r, c.g, c.b, 0.35f);
                drawToken(shapes, cur.kind[i], screenX(prev.x[p]), screenY(prev.y[p]));
            }
            shapes.setColor(c.r, c.g, c.b, 0.9f);
            drawToken(shapes, cur.kind[i], px, py);
            if (cur.frozenEntity[i]) {
                shapes.setColor(ICE_COL.r, ICE_COL.g, ICE_COL.b, 0.95f);
                shapes.rect(px + 4, py + 4, TILE - 8, TILE - 8);
            }
            if (cur.stunnedEntity[i] && !cur.frozenEntity[i]) {
                shapes.setColor(STUN_COL.r, STUN_COL.g, STUN_COL.b, pulse);
                shapes.rect(px + 4, py + 4, TILE - 8, TILE - 8);
            }
            if (cur.bleedingEntity[i]) {
                shapes.setColor(BLEED_COL.r, BLEED_COL.g, BLEED_COL.b, 0.9f);
                shapes.rect(px + 5, py + 5, TILE - 10, TILE - 10);
            }
            if (cur.burningEntity[i]) {
                shapes.setColor(FIRE.r, FIRE.g, FIRE.b, pulse);
                shapes.rect(px + 3, py + 3, TILE - 6, TILE - 6);
            }
            if (cur.id[i].equals(actingId)) {
                shapes.setColor(TEXT.r, TEXT.g, TEXT.b, pulse);
                shapes.rect(px + 2, py + 2, TILE - 4, TILE - 4);
            }
        }
        shapes.end();
    }

    private void text(String s, float x, float topY) {
        font.draw(batch, s, x, WIN_H - topY);
    }

    private void drawText() {
        batch.begin();

        // Map coordinate rulers
        font.setColor(MUTED);
        for (int x = 0; x < cur.w; x++) text(String.valueOf(x), screenX(x) + (TILE - String.valueOf(x).length() * charW) / 2f, MAP_Y - fontH - 4);
        for (int y = 0; y < cur.h; y++) text(String.valueOf(y), MAP_X - 18, MAP_Y + y * TILE + (TILE - fontH) / 2f);

        // Entity labels & floating damage numbers
        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            String label = i == 0 ? "@" : cur.id[i];
            font.setColor(i == 0 ? THRALL : TEXT);
            float tokenCenterFromTop = WIN_H - (screenY(drawY(i)) + 26f);
            text(label, screenX(drawX(i)) + (TILE - label.length() * charW) / 2f, tokenCenterFromTop - fontH / 2f);
        }
        for (int i = 0; i < MAX_FLOATERS; i++) {
            if (floatAge[i] >= FLOAT_LIFE) continue;
            float alpha = 1f - floatAge[i] / FLOAT_LIFE;
            Color fc = floatText[i].startsWith("-") ? CREATURE : SAGE;
            font.setColor(fc.r, fc.g, fc.b, alpha);
            text(floatText[i], screenX(floatX[i]) + 14, WIN_H - screenY(floatY[i]) - TILE + 6 - floatAge[i] * 24);
        }

        // Right panel: HUD + aligned tactical roster
        float px = sideX() + 16, py = MAP_Y + 18;
        font.setColor(TEXT);
        text("ROUND " + round, px, py);
        if (!game.world.thrall().alive()) {
            font.setColor(CREATURE);
            text("LINK SEVERED", px + 110, py);
        } else if (thinking > 0) {
            font.setColor(PROP);
            text("HOSTILES ACTING " + ".".repeat(thinking), px + 110, py);
        }

        font.setColor(MUTED);
        text("VITALITY", px, MAP_Y + 44);
        text("ACTION PTS", px, MAP_Y + 68);
        font.setColor(TEXT);
        text(hp + "/" + maxHp, px + 262, MAP_Y + 44);
        text(ap + "/" + roundAp, px + 262, MAP_Y + 68);

        font.setColor(MUTED);
        text("MAGIC", px, MAP_Y + 90);
        text("TEMP", px + 140, MAP_Y + 90);
        text("CHANNEL", px, MAP_Y + 110);
        text("CONDS", px, MAP_Y + 130);
        font.setColor(TEXT);
        text(String.valueOf(cur.magic), px + 60, MAP_Y + 90);
        text(cur.temp + "C", px + 190, MAP_Y + 90);
        String chanText = cur.concentration > 0
                ? cur.channel + "  CONC " + cur.concentration + "%"
                : cur.channel;
        text(chanText, px + 76, MAP_Y + 110);
        font.setColor(cur.thrallStunned || cur.thrallFrozen ? CREATURE : TEXT);
        text(cur.shortConds, px + 60, MAP_Y + 130);

        py = MAP_Y + 158;
        float hpColX = sideX() + sideW() - 175, posColX = sideX() + sideW() - 52;
        font.setColor(MUTED);
        text("ID", px + 6, py);
        text("ENTITY", px + 54, py);
        text("HP", hpColX, py);
        text("POS", posColX, py);

        for (int i = 0; i < cur.n; i++) {
            if (cur.hp[i] <= 0) continue;
            py += LINE_H;
            font.setColor(colorOf(cur, i));
            text(i == 0 ? "self" : cur.id[i], px + 6, py);
            font.setColor(TEXT);
            text(cur.name[i], px + 54, py);
            font.setColor(MUTED);
            text(cur.hp[i] + "/" + cur.maxHp[i], hpColX + 54, py);
            text(cur.x[i] + "," + cur.y[i], posColX, py);
        }

        // Structured 2-column combat log + auto-dimmed previous rounds
        int logW = logW(), scroll = scrollLines();
        int visible = logLines(), first = Math.max(0, log.size() - visible - scroll);
        for (int j = 0; j < visible && first + j < log.size(); j++) {
            LogRow r = log.get(first + j);
            float dim = r.round < round ? 0.52f : 1f;
            float lineTop = logTop() + 7 + j * LINE_H;
            font.setColor(r.color.r, r.color.g, r.color.b, dim);
            text(r.text, MAP_X + 16, lineTop);
            if (r.badge != null) {
                int pillW = r.badge.length() * charW + 14;
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
        text(input.toString(), MAP_X + 12 + 8 * charW, promptTop);

        batch.end();
    }
}
