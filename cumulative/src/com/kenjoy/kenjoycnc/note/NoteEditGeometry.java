package com.kenjoy.kenjoycnc.note;

import java.awt.geom.Rectangle2D;
import java.util.*;

/** One immutable geometry index for a displayed revision. Coordinates exclude zoom and viewport scroll. */
public final class NoteEditGeometry {
    public final NoteLayout layout;
    public final float width,height,pageGap;
    public static final class Position {
        public final int offset,page,line;
        public final boolean backward;
        public final Rectangle2D.Float bounds;
        Position(int offset,int page,int line,boolean backward,Rectangle2D.Float bounds){this.offset=offset;this.page=page;this.line=line;this.backward=backward;this.bounds=bounds;}
    }
    private static final class Row {
        final int page;final NoteLayout.Line line;
        final List<Position> stops=new ArrayList<Position>();
        Row(int page,NoteLayout.Line line){this.page=page;this.line=line;}
    }
    private final List<Row> rows=new ArrayList<Row>();
    private final List<List<Row>> pageRows=new ArrayList<List<Row>>();
    private final NavigableMap<Integer,List<Position>> byOffset=new TreeMap<Integer,List<Position>>();
    public NoteEditGeometry(NoteLayout layout,float pageGap){
        if(layout==null)throw new IllegalArgumentException("缺少编辑布局");
        this.layout=layout;this.pageGap=Math.max(0,pageGap);width=layout.pageWidth;
        height=Math.max(0,layout.pages.size()*layout.pageHeight+Math.max(0,layout.pages.size()-1)*this.pageGap);
        for(int p=0;p<layout.pages.size();p++){
            List<Row> indexed=new ArrayList<Row>();pageRows.add(indexed);
            for(NoteLayout.Line line:layout.pages.get(p).lines){
                Row row=new Row(p,line);rows.add(row);indexed.add(row);
                for(NoteLayout.CaretStop stop:line.carets){
                    Position position=new Position(stop.offset,p,line.number,line.softBreak&&stop.offset==line.contentEnd,new Rectangle2D.Float(stop.x,pageOriginY(p)+line.bounds.y,1,line.bounds.height));
                    row.stops.add(position);List<Position> alternatives=byOffset.get(stop.offset);if(alternatives==null){alternatives=new ArrayList<Position>();byOffset.put(stop.offset,alternatives);}alternatives.add(position);
                }
            }
        }
    }
    public static NoteLayout approximate(NoteDocument document,float dpi,NoteLayout previous){return new NoteLayoutEngine(null,dpi).approximate(document,previous);}
    public float pageOriginY(int page){return page*(layout.pageHeight+pageGap);}
    public Rectangle2D.Float pageBounds(int page){return new Rectangle2D.Float(0,pageOriginY(page),layout.pageWidth,layout.pageHeight);}
    private Position empty(){return new Position(0,0,0,false,new Rectangle2D.Float(0,0,1,Math.max(1,layout.pageHeight)));}
    public Position caret(int offset,boolean backward){
        if(byOffset.isEmpty())return empty();
        Map.Entry<Integer,List<Position>> entry=byOffset.floorEntry(Math.max(0,Math.min(offset,layout.textLength)));
        if(entry==null)entry=byOffset.firstEntry();
        List<Position> alternatives=entry.getValue();return alternatives.get(backward?0:alternatives.size()-1);
    }
    public int pageOf(int offset){return caret(offset,false).page;}
    private static Position nearest(Row row,float x){
        Position found=null;float distance=Float.MAX_VALUE;
        for(Position p:row.stops){float d=Math.abs(p.bounds.x-x);if(d<=distance){found=p;distance=d;}}
        return found;
    }
    public Position hit(float x,float y){
        if(rows.isEmpty())return empty();
        int p=Math.max(0,Math.min(pageRows.size()-1,Math.round((y-layout.pageHeight*.5f)/(layout.pageHeight+pageGap))));
        Row best=null;float distance=Float.MAX_VALUE;
        for(Row row:pageRows.get(p)){
            float top=pageOriginY(p)+row.line.bounds.y,bottom=top+row.line.bounds.height;
            float d=y<top?top-y:y>bottom?y-bottom:0;
            if(d<distance){best=row;distance=d;}
        }
        if(best==null)return caret(layout.pages.get(p).start,false);
        Position found=nearest(best,x);return found==null?caret(best.line.start,false):found;
    }
    private int rowIndex(Position p){for(int i=0;i<rows.size();i++)if(rows.get(i).page==p.page&&rows.get(i).line.number==p.line)return i;return 0;}
    public Position nextLine(int offset,boolean backward,int direction,float desiredX){
        Position current=caret(offset,backward);if(rows.isEmpty())return current;
        int i=Math.max(0,Math.min(rows.size()-1,rowIndex(current)+(direction<0?-1:1)));
        Position next=nearest(rows.get(i),Float.isNaN(desiredX)?current.bounds.x:desiredX);return next==null?current:next;
    }
    public Position lineBoundary(int offset,boolean backward,boolean end){
        Position current=caret(offset,backward);if(rows.isEmpty())return current;Row row=rows.get(rowIndex(current));
        return row.stops.isEmpty()?current:row.stops.get(end?row.stops.size()-1:0);
    }
    /** Rectangles are merged per visual row; explicit line/page breaks get a visible narrow selection. */
    public List<Rectangle2D.Float> selection(int anchor,int focus){
        int start=Math.min(anchor,focus),end=Math.max(anchor,focus);List<Rectangle2D.Float> result=new ArrayList<Rectangle2D.Float>();if(start==end)return result;
        for(int p=0;p<layout.pages.size();p++){
            Map<Integer,Rectangle2D.Float> selected=new LinkedHashMap<Integer,Rectangle2D.Float>();
            for(NoteLayout.Glyph glyph:layout.pages.get(p).glyphs)if(glyph.start<end&&glyph.end>start){
                float w=Math.max(glyph.cell.width,glyph.advance);Rectangle2D.Float r=new Rectangle2D.Float(glyph.cell.x,glyph.cell.y+pageOriginY(p),Math.max(2,w),glyph.cell.height);
                Rectangle2D.Float old=selected.get(glyph.line);if(old==null)selected.put(glyph.line,r);else Rectangle2D.union(old,r,old);
            }
            result.addAll(selected.values());
        }
        return result;
    }
}
