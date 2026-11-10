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
    private static final int INPUT_TOP = WIN_H - 54, INPUT_H = 34, SCROLLBAR_W = 6;
    private static final float SLIDE = 0.4f, FLOAT_LIFE = 1.6f;

    private static final Color BG = Color.valueOf("090B0E"), PANEL = Color.valueOf("11151C"),
            BORDER = Color.valueOf("232B38"), FLOOR = Color.valueOf("131820"), FLOOR_ALT = Color.valueOf("151A23"),
            WALL = Color.valueOf("1E2530"), WALL_BEVEL = Color.valueOf("2A3342"),
            TOKEN_BG = Color.valueOf("10141B"), BAR_BG = Color.valueOf("0A0C10"),
            OIL = Color.valueOf("1F1A12"), OIL_RIM = Color.valueOf("3D311E"), FIRE = Color.valueOf("F07148"),
            THRALL = Color.valueOf("4CC9B0"), CREATURE = Color.valueOf("E05A5A"),
            PROP = Color.valueOf("D4A359"), DOOR = Color.valueOf("8C7A6B"),
            TEXT = Color.valueOf("D8DEE9"), MUTED = Color.valueOf("5E6C80"), SAGE = Color.valueOf("68B088");

    // Log entry kinds & gutter groups
    private static final int K_ROUND = 0, K_PHASE = 1, K_CMD = 2, K_STAGE = 3, K_ACTOR = 4, K_TEXT = 5, K_ALERT = 6;
    private static final int G_NONE = 0, G_PLAYER = 1, G_HOSTILE = 2, G_ENV = 3;

    private static final class LogRow {
        final int round, kind, group;
        String text, badge;
        Color color, badgeColor;

        LogRow(int round, int kind, int group, String text, Color color, String badge, Color badgeColor) {
            this.round = round;
            this.kind = kind;
            this.group = group;
            this.text = text;
            this.color = color;
            this.badge = badge;
            this.badgeColor = badgeColor;
        }
    }

    private final long seed;
    private Game game;
    private Presenter presenter;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private int charW = 8, fontH = 14;
    private final FitViewport viewport = new FitViewport(WIN_W, WIN_H);

    private Scene cur, prev;
    private int[] prevIdx = new int[0];
    private float anim = SLIDE, time;
    private int hp, maxHp, ap, roundAp, round, thinking, curGroup = G_NONE;
    private String actingId = "";
    private final ArrayList<LogRow> log = new ArrayList<>();
    private LogRow openStage;
    private int logScroll; // lines scrolled back from the newest

    private final int[] floatX = new int[MAX_FLOATERS], floatY = new int[MAX_FLOATERS];
    private final String[] floatText = new String[MAX_FLOATERS];
    private final float[] floatAge = new float[MAX_FLOATERS];
    private int nextFloater;

    private final StringBuilder input = new StringBuilder();
    private final ArrayList<String> history = new ArrayList<>();
    private int historyPos;
    private boolean dirty;

    public GdxView(long seed) {
        this.seed = seed;
        java.util.Arrays.fill(floatAge, FLOAT_LIFE);
    }

    // ---- lifecycle ----

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = buildMonoFont(14);

        game = new Game(seed);
        presenter = new Presenter(this, game.world);
        game.bus.subscribe(presenter::handle);
        game.start();
        frame(game.world, game.round);

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char c) {
                if (c == '\r' || c == '\n') submit();
                else if (c == '\b') { if (input.length() > 0) input.setLength(input.length() - 1); }
                else if (c >= 32 && c != 127) input.append(c);
                return true;
            }

            @Override
            public boolean keyDown(int key) {
                if (key == Input.Keys.UP && historyPos > 0) recall(--historyPos);
                if (key == Input.Keys.DOWN && historyPos < history.size()) recall(++historyPos);
                if (key == Input.Keys.PAGE_UP) logScroll += logLines() - 1;
                if (key == Input.Keys.PAGE_DOWN) logScroll -= logLines() - 1;
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                logScroll -= (int) Math.signum(amountY) * 3;
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

    private void recall(int i) {
        input.setLength(0);
        if (i < history.size()) input.append(history.get(i));
    }

    private void submit() {
        String raw = input.toString().trim();
        input.setLength(0);
        if (raw.isEmpty() || presenter.busy() || !game.world.thrall().alive()) return;
        if (raw.equalsIgnoreCase("quit") || raw.equalsIgnoreCase("exit")) {
            Gdx.app.exit();
            return;
        }
        history.add(raw);
        historyPos = history.size();
        curGroup = G_PLAYER;
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
        int logW = WIN_W - MAP_X - 20;
        return Math.max(40, (logW - (hasBadge ? 190 : 40)) / charW);
    }

    private LogRow addRow(int kind, int group, String text, Color color, String badge, Color badgeColor) {
        int limit = maxChars(badge != null || kind == K_STAGE);
        if (text.length() <= limit) {
            LogRow r = new LogRow(round, kind, group, text, color, badge, badgeColor);
            log.add(r);
            if (logScroll > 0) logScroll++;
            return r;
        }
        int split = text.lastIndexOf(' ', limit);
        if (split <= 6) split = limit;
        LogRow first = new LogRow(round, kind, group, text.substring(0, split), color, badge, badgeColor);
        log.add(first);
        if (logScroll > 0) logScroll++;
        addRow(K_TEXT, group, "      " + text.substring(split).trim(), color, null, null);
        return first;
    }

    private void addMultiline(int kind, int group, String text, Color color) {
        for (String line : text.split("\\R")) addRow(kind, group, line, color, null, null);
    }

    /** Structured combat log; merges primary stage action + status pill, wrapping extra narrations underneath. */
    @Override
    public void handle(GameEvent event) {
        switch (event) {
            case GameEvent.Narrative e -> {
                if (openStage != null && !e.text().contains("\n")) {
                    String combined = openStage.text.isEmpty() ? "  " + e.text() : openStage.text + "  -  " + e.text();
                    if (combined.length() <= maxChars(true)) {
                        openStage.text = combined;
                    } else {
                        addRow(K_TEXT, curGroup, "    " + e.text(), PROP, null, null);
                    }
                    openStage = null; // subsequent narrations (e.g. crippled/blinded effects) get their own line
                } else {
                    openStage = null;
                    String prefix = curGroup == G_NONE ? "" : "    ";
                    Color c = curGroup == G_PLAYER && (e.text().endsWith("!") || e.text().contains("crippled") || e.text().contains("blind")) ? PROP : TEXT;
                    for (String line : e.text().split("\\R")) addRow(K_TEXT, curGroup, prefix + line, c, null, null);
                }
            }
            case GameEvent.Rejected e -> {
                openStage = null;
                String msg = e.reason() + (e.hint() == null ? "" : " (" + e.hint() + ")");
                addRow(K_ALERT, G_PLAYER, "  " + msg, CREATURE, "REJECT | " + e.code().name(), CREATURE);
            }
            case GameEvent.StageStart e -> {
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
                    if (target.text.isEmpty()) target.text = "  " + e.render();
                    target.badge = pill;
                    target.badgeColor = pc;
                    target.color = TEXT;
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
                addRow(K_PHASE, G_HOSTILE, "HOSTILE PHASE", CREATURE, null, null);
            }
            case GameEvent.ActorBeat e -> {
                actingId = e.id();
                curGroup = G_HOSTILE;
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
                for (String l : e.lines()) addRow(K_ALERT, G_ENV, "  " + l, FIRE, "HAZARD", FIRE);
            }
            case GameEvent.Thinking e -> thinking = e.dots();
            case GameEvent.RoundStart e -> {
                thinking = 0;
                actingId = "";
                curGroup = G_NONE;
                openStage = null;
                round = e.round();
                ap = e.ap();
                addRow(K_ROUND, G_NONE, "ROUND " + e.round(), THRALL, e.ap() + " AP READY", THRALL);
            }
            case GameEvent.ThrallDied e -> addRow(K_ALERT, G_NONE, "THE THRALL COLLAPSES. THE LINK GOES DARK.", CREATURE, "FATAL", CREATURE);
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
        for (int i = 0; i < MAX_FLOATERS; i++) floatAge[i] += dt;

        presenter.update(dt);
        if (dirty && !presenter.busy()) {
            frame(game.world, game.round);
            dirty = false;
        }
        logScroll = MathUtils.clamp(logScroll, 0, Math.max(0, log.size() - logLines()));

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

    private int logHeight() { return INPUT_TOP - 10 - logTop(); }

    private int logLines() { return (logHeight() - 12) / LINE_H; }

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

    private void drawShapes() {
        float pulse = 0.55f + 0.35f * (float) Math.sin(time * 7);
        int logW = WIN_W - MAP_X - 20, logY = WIN_H - logTop() - logHeight();
        int sideY = WIN_H - MAP_Y - mapH(), inputY = WIN_H - INPUT_TOP - INPUT_H;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(sideX(), sideY, sideW(), mapH());
        shapes.rect(MAP_X, logY, logW, logHeight());
        shapes.rect(MAP_X, inputY, logW, INPUT_H);
        shapes.setColor(THRALL.r, THRALL.g, THRALL.b, presenter.busy() ? 0.25f : 0.85f);
        shapes.rect(MAP_X, inputY, 3, INPUT_H);

        int total = log.size(), visible = logLines();
        if (total > visible) {
            float trackH = logHeight() - 8, thumbH = Math.max(18, trackH * visible / total);
            float frac = logScroll / (float) (total - visible);
            shapes.setColor(BORDER);
            shapes.rect(MAP_X + logW - SCROLLBAR_W - 4, logY + 4 + (trackH - thumbH) * frac, SCROLLBAR_W, thumbH);
        }

        // HUD status bars (centered vertically on fontH)
        float hudX = sideX() + 16, barX = hudX + 100;
        float hpBarY = WIN_H - (MAP_Y + 44) - (fontH + 10) / 2f;
        float apBarY = WIN_H - (MAP_Y + 68) - (fontH + 10) / 2f;
        shapes.setColor(BAR_BG);
        shapes.rect(barX, hpBarY, 150, 10);
        shapes.setColor(hp * 3 <= maxHp ? CREATURE : THRALL);
        shapes.rect(barX, hpBarY, 150 * MathUtils.clamp(hp / (float) Math.max(1, maxHp), 0f, 1f), 10);
        for (int p = 0; p < roundAp; p++) {
            shapes.setColor(p < ap ? THRALL : BAR_BG);
            shapes.rect(barX + p * 22, apBarY, 16, 10);
        }
        shapes.setColor(BORDER);
        shapes.rect(sideX() + 12, WIN_H - (MAP_Y + 90), sideW() - 24, 1);

        // Roster row highlight + aligned mini HP bars
        float rowTop = MAP_Y + 102;
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

        // Structured log banners, left gutter brackets, and right-aligned pill backgrounds
        int first = Math.max(0, log.size() - visible - logScroll);
        for (int j = 0; j < visible && first + j < log.size(); j++) {
            LogRow r = log.get(first + j);
            float dim = r.round < round ? 0.52f : 1f;
            float lineTop = logTop() + 7 + j * LINE_H;
            float midY = WIN_H - lineTop - fontH / 2f;
            float boxY = midY - (LINE_H - 2) / 2f;

            if (r.kind == K_ROUND) {
                shapes.setColor(BORDER.r, BORDER.g, BORDER.b, 0.55f * dim);
                shapes.rect(MAP_X + 6, boxY, logW - 20, LINE_H - 2);
                shapes.setColor(THRALL.r, THRALL.g, THRALL.b, dim);
                shapes.rect(MAP_X + 6, boxY, 4, LINE_H - 2);
            } else if (r.kind == K_PHASE) {
                shapes.setColor(CREATURE.r, CREATURE.g, CREATURE.b, 0.14f * dim);
                shapes.rect(MAP_X + 6, boxY, logW - 20, LINE_H - 2);
                shapes.setColor(CREATURE.r, CREATURE.g, CREATURE.b, dim);
                shapes.rect(MAP_X + 6, boxY, 3, LINE_H - 2);
            } else if (r.kind == K_CMD) {
                shapes.setColor(THRALL.r, THRALL.g, THRALL.b, 0.09f * dim);
                shapes.rect(MAP_X + 6, boxY, logW - 20, LINE_H - 2);
            }

            if (r.group != G_NONE && r.kind != K_ROUND && r.kind != K_PHASE) {
                Color gc = groupColor(r.group);
                shapes.setColor(gc.r, gc.g, gc.b, 0.75f * dim);
                shapes.rect(MAP_X + 8, midY - LINE_H / 2f, 2, LINE_H);
            }

            if (r.badge != null) {
                int pillW = r.badge.length() * charW + 14;
                float pillX = MAP_X + logW - 22 - pillW;
                Color bc = r.badgeColor;
                shapes.setColor(bc.r, bc.g, bc.b, 0.16f * dim);
                shapes.rect(pillX, midY - (LINE_H - 6) / 2f, pillW, LINE_H - 6);
            }
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
                    if (cur.oil[t]) {
                        shapes.setColor(OIL_RIM);
                        shapes.rect(sx + 4, sy + 4, TILE - 8, TILE - 8);
                        shapes.setColor(OIL);
                        shapes.rect(sx + 6, sy + 6, TILE - 12, TILE - 12);
                    }
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
        for (int p = 0; p < roundAp; p++) shapes.rect(barX + p * 22, apBarY, 16, 10);

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

        py = MAP_Y + 102;
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
        int logW = WIN_W - MAP_X - 20;
        int visible = logLines(), first = Math.max(0, log.size() - visible - logScroll);
        for (int j = 0; j < visible && first + j < log.size(); j++) {
            LogRow r = log.get(first + j);
            float dim = r.round < round ? 0.52f : 1f;
            float lineTop = logTop() + 7 + j * LINE_H;
            font.setColor(r.color.r, r.color.g, r.color.b, dim);
            text(r.text, MAP_X + 16, lineTop);
            if (r.badge != null) {
                int pillW = r.badge.length() * charW + 14;
                float pillX = MAP_X + logW - 22 - pillW;
                Color bc = r.badgeColor;
                font.setColor(bc.r, bc.g, bc.b, dim);
                text(r.badge, pillX + 7, lineTop);
            }
        }
        if (logScroll > 0) {
            font.setColor(PROP);
            text("[scrolled -" + logScroll + " | PgDn / wheel]", WIN_W - 280, logTop() + 7 + (visible - 1) * LINE_H);
        }
        font.setColor(presenter.busy() ? MUTED : THRALL);
        float promptTop = INPUT_TOP + (INPUT_H - fontH) / 2f;
        text("ARCHON>", MAP_X + 12, promptTop);
        font.setColor(presenter.busy() ? MUTED : TEXT);
        text(input + (!presenter.busy() && ((int) (time * 2.5f)) % 2 == 0 ? "_" : ""), MAP_X + 12 + 8 * charW, promptTop);

        batch.end();
    }
}
