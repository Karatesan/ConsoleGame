package com.archon.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** 2x supersampled mipmapped monospace font builder (sharp on macOS Retina and 1x displays). */
final class MonoFont {
    final BitmapFont font;
    final float charW, fontH;

    private MonoFont(BitmapFont font, float charW, float fontH) {
        this.font = font;
        this.charW = charW;
        this.fontH = fontH;
    }

    static MonoFont build(int sizePx) {
        System.setProperty("java.awt.headless", "true");
        int scale = 2;
        int hiPx = sizePx * scale;
        Font awtFont = new Font(Font.MONOSPACED, Font.PLAIN, hiPx);
        for (String name : new String[]{"JetBrains Mono", "SF Mono", "Menlo", "Monaco", "Consolas"}) {
            Font candidate = new Font(name, Font.PLAIN, hiPx);
            if (!Font.DIALOG.equals(candidate.getFamily())) { awtFont = candidate; break; }
        }
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = scratch.createGraphics();
        g0.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g0.setFont(awtFont);
        FontMetrics fm = g0.getFontMetrics();
        int hiCharW = Math.max(12, fm.charWidth('M'));
        int ascent = fm.getAscent(), descent = fm.getDescent(), glyphH = ascent + descent;
        int cellW = hiCharW + 6, cellH = glyphH + 6;
        g0.dispose();

        int texW = 512, texH = 512, cols = texW / cellW;
        BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(awtFont);
        g.setColor(java.awt.Color.WHITE);

        BitmapFont bf = new BitmapFont();
        bf.setUseIntegerPositions(false);
        TextureRegion reg = bf.getRegion();
        for (int c = 32; c < 127; c++) {
            int idx = c - 32, gx = (idx % cols) * cellW, gy = (idx / cols) * cellH;
            g.drawString(String.valueOf((char) c), gx + 2, gy + 2 + ascent);
            BitmapFont.Glyph glyph = bf.getData().getGlyph((char) c);
            if (glyph != null) {
                glyph.srcX = gx + 2;
                glyph.srcY = gy + 2;
                glyph.width = hiCharW;
                glyph.height = glyphH;
                glyph.xoffset = 0;
                glyph.yoffset = -glyphH;
                glyph.xadvance = hiCharW;
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
        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        reg.getTexture().dispose();
        reg.setTexture(tex);
        for (int c = 32; c < 127; c++) {
            BitmapFont.Glyph glyph = bf.getData().getGlyph((char) c);
            if (glyph != null) {
                bf.getData().setGlyphRegion(glyph, reg);
                glyph.yoffset = -glyphH;
            }
        }
        bf.getData().lineHeight = cellH;
        bf.getData().capHeight = glyphH;
        bf.getData().ascent = 0;
        bf.getData().descent = -descent;
        bf.getData().setScale(1f / scale);
        return new MonoFont(bf, hiCharW / (float) scale, glyphH / (float) scale);
    }
}
