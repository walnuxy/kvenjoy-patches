package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.g.e;
import java.awt.geom.*;
import java.util.*;

/** A completed layout: source identities are independent of page/line indices. */
public final class NoteLayout {
    public final List<Page> pages=new ArrayList<Page>();
    public final Set<String> missing=new LinkedHashSet<String>();
    public final Set<String> fallback=new LinkedHashSet<String>();
    private static String labels(Set<String> values){StringBuilder s=new StringBuilder();int count=0;for(String value:values){if(count++==16){s.append(" 等");break;}s.append(value).append(" (");value.codePoints().forEach(c->s.append(String.format("U+%04X ",c)));s.append(") ");}return s.toString();}
    public String notice(){return (fallback.isEmpty()?"":"\n已用内置笔画补全符号："+labels(fallback))+(missing.isEmpty()?"":"\n原文已保留；以下字符缺少字形，预览以红框标记，输出留空："+labels(missing));}
    public static final class Page {
        public final List<Glyph> glyphs=new ArrayList<Glyph>();
        public final List<e> paths=new ArrayList<e>();
        public final List<Line> lines=new ArrayList<Line>();
    }
    public static final class Line {
        public final int number;
        public final Rectangle2D.Float bounds;
        public final List<Long> ids=new ArrayList<Long>();
        public Line(int n,float x,float y,float width,float height) {number=n;bounds=new Rectangle2D.Float(x,y,width,height);}
    }
    public static final class Glyph {
        public final long id;
        public final String text;
        public final int line;
        public final Rectangle2D.Float cell;
        public final e path;
        public final NoteDocument.FontRef font;
        public Glyph(long id,String text,int line,Rectangle2D.Float cell,e path,NoteDocument.FontRef font) {this.id=id;this.text=text;this.line=line;this.cell=cell;this.path=path;this.font=font;}
    }
    public static Set<Long> box(Page page,Rectangle2D box) {
        Set<Long> ids=new LinkedHashSet<Long>();for(Glyph g:page.glyphs)if(box.intersects(g.cell))ids.add(g.id);return ids;
    }
    public static Glyph hit(Page page,float x,float y) {
        return hit(page,x,y,0);
    }
    public static Glyph hit(Page page,float x,float y,float tolerance) {
        Glyph found=null;double distance=Double.MAX_VALUE;
        for(Glyph g:page.glyphs)if(g.cell.contains(x,y)) {double d=Point2D.distanceSq(x,y,g.cell.getCenterX(),g.cell.getCenterY());if(d<distance){found=g;distance=d;}}
        if(found==null&&tolerance>0)for(Glyph g:page.glyphs){Rectangle2D r=new Rectangle2D.Double(g.cell.x-tolerance,g.cell.y-tolerance,g.cell.width+2*tolerance,g.cell.height+2*tolerance);if(r.contains(x,y)){double d=Point2D.distanceSq(x,y,g.cell.getCenterX(),g.cell.getCenterY());if(d<distance){found=g;distance=d;}}}
        return found;
    }
    public int pageOf(long id) {for(int p=0;p<pages.size();p++)for(Glyph g:pages.get(p).glyphs)if(g.id==id)return p;return -1;}
    public static Path2D path(List<e> paths) {
        Path2D.Float out=new Path2D.Float();
        for(e p:paths) {float[] xy=p.a();byte[] flags=p.b();for(int i=0;i<flags.length;i++)if(flags[i]==0)out.moveTo(xy[i*2],xy[i*2+1]);else out.lineTo(xy[i*2],xy[i*2+1]);}
        return out;
    }
}
