package com.kenjoy.kenjoycnc.fonts;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read converted fonts with the unmodified application loader and render real strokes. */
public final class FontLibraryChecks {
    public static void main(String[] args) throws Exception {


        // Bundled JRE entropy initialization calls a modified NetworkInterface and stalls.
        // Prefer its existing native Windows RNG in this test JVM only.
        java.security.Provider windows = java.security.Security.getProvider("SunMSCAPI");
        java.security.Security.removeProvider("SunMSCAPI");
        java.security.Security.insertProviderAt(windows, 1);
        Path root = Paths.get(args[0]);
        java.util.List<Path> paths = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".gfont") && p.getFileName().toString().startsWith("Plot-")).forEach(paths::add);
        }
        Collections.sort(paths);
        int tileWidth = 400, tileHeight = 135, cols = 3, rows = (paths.size()+cols-1)/cols;
        BufferedImage sheet = new BufferedImage(cols*tileWidth, rows*tileHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,14));
        int fonts=0, glyphs=0;
        for (Path path : paths) {
            v font = new v();
            try (InputStream in = Files.newInputStream(path)) { font.c(in); }
            if (!"kvenjoy".equals(font.e) || font.b()==0 || font.d!=1000) throw new AssertionError("Bad header "+path);
            java.util.List all = font.c();
            int count = 0;
            for (Object item : all) {
                a glyph = (a)item;
                if (!glyph.d()) glyph.c();
                if (glyph.b.length != glyph.c.length*2) throw new AssertionError("Bad geometry "+path);
                for (float n : glyph.b) if (Float.isInfinite(n) || Float.isNaN(n)) throw new AssertionError("Nonfinite "+path);
                if (glyph.c.length>0 && (glyph.c[0]!=0 || glyph.a()==null)) throw new AssertionError("Bad pen start "+path);
                count++;
            }
            if (count!=font.b()) throw new AssertionError("Glyph count "+path);
            int x = (fonts%cols)*tileWidth, y = (fonts/cols)*tileHeight;
            g.setColor(new Color(40,50,60));g.drawString(font.b,x+12,y+22);
            java.util.List<a> samples = new ArrayList<a>();
            String text = path.toString().contains("中文") ? "中文字体书写" : "Abc123";
            for(char c:text.toCharArray()) if(font.a(c)) samples.add(font.b(c));
            if(samples.size()<3) { samples.clear(); for(Object obj:all) { a glyph=(a)obj; if(glyph.c.length>0) samples.add(glyph);if(samples.size()==6) break; } }
            int i=0;
            for(a glyph:samples) {
                Path2D.Float drawing = new Path2D.Float();
                for(int p=0;p<glyph.c.length;p++) {
                    float gx=x+38+i*60+glyph.b[p*2]*.052f, gy=y+105+glyph.b[p*2+1]*.065f;
                    if(glyph.c[p]==0) drawing.moveTo(gx,gy); else drawing.lineTo(gx,gy);
                }
                g.draw(drawing);i++;
            }
            g.setColor(Color.LIGHT_GRAY);g.drawLine(x,y+tileHeight-1,x+tileWidth,y+tileHeight-1);
            System.out.println("PASS "+font.b+" glyphs="+count);
            fonts++;glyphs+=count;font.f();
        }
        g.dispose();ImageIO.write(sheet,"png",new File(args[1]));
        if(fonts!=70) throw new AssertionError("Expected 70 new fonts, got "+fonts);
        System.out.println("SUMMARY PASS fonts="+fonts+" glyphs="+glyphs);
        System.exit(0);
    }
}
