package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.g.e;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;

/** Shared obstacle layout and final pen-down path validation for forbidden zones. */
public final class NoteForbiddenZoneGeometry {
    private static final float EPSILON_PX=.01f;
    /** Layout must stay strictly outside the expanded polygon used by final path validation. */
    private static final float LAYOUT_GUARD_PX=EPSILON_PX*2f;

    public static final class Interval {
        public final float left,right;
        Interval(float left,float right){this.left=left;this.right=right;}
        public float width(){return right-left;}
    }
    private static final class Obstacle {
        final float left,right;
        Obstacle(float left,float right){this.left=left;this.right=right;}
    }
    private NoteForbiddenZoneGeometry(){}

    private static float px(float mm,float dpi){return mm*dpi/25.4f;}

    /** Horizontal paper intervals available to one complete line-height band. */
    public static List<Interval> availableIntervals(NoteDocument document,int page,float top,float height,
                                                     float left,float right,float dpi){
        if(document==null||document.page==null||document.forbiddenZones==null)throw new IllegalArgumentException("缺少禁写区文档状态");
        if(page<=0||page>NoteForbiddenZones.MAX_PAGE_NUMBER||!Float.isFinite(dpi)||dpi<=0||!Float.isFinite(top)||!Float.isFinite(height)||height<=0||!Float.isFinite(left)||!Float.isFinite(right)||right<=left)
            throw new IllegalArgumentException("禁写区排版坐标无效");
        List<NoteForbiddenZones.ResolvedZone> zones=document.forbiddenZones.resolve(page,document.page.pageWidth,document.page.pageHeight);
        if(zones.isEmpty())return Collections.singletonList(new Interval(left,right));
        List<Obstacle> obstacles=new ArrayList<Obstacle>();
        float bottom=top+height;
        if(!Float.isFinite(bottom))throw new IllegalArgumentException("禁写区排版行框超出数值范围");
        for(NoteForbiddenZones.ResolvedZone zone:zones){
            float zoneTop=px(zone.top(),dpi),zoneBottom=px(zone.bottom(),dpi);
            if(zoneBottom<top-EPSILON_PX||zoneTop>bottom+EPSILON_PX)continue;
            float a=Math.max(left,px(zone.left(),dpi)-LAYOUT_GUARD_PX);
            float b=Math.min(right,px(zone.right(),dpi)+LAYOUT_GUARD_PX);
            if(b>a)obstacles.add(new Obstacle(a,b));
        }
        if(obstacles.isEmpty())return Collections.singletonList(new Interval(left,right));
        Collections.sort(obstacles,new Comparator<Obstacle>(){public int compare(Obstacle a,Obstacle b){return Float.compare(a.left,b.left);}});
        List<Interval> free=new ArrayList<Interval>();float cursor=left;
        for(Obstacle obstacle:obstacles){
            if(obstacle.right<=cursor)continue;
            if(obstacle.left>cursor)free.add(new Interval(cursor,Math.min(right,obstacle.left)));
            cursor=Math.max(cursor,obstacle.right);if(cursor>=right)break;
        }
        if(cursor<right)free.add(new Interval(cursor,right));
        return free;
    }

    /** Last page with an explicit custom-page rule or page exception. */
    public static int lastExplicitPage(NoteForbiddenZones zones){
        int last=0;if(zones==null)return last;
        if(zones.rules!=null)for(NoteForbiddenZones.Rule rule:zones.rules)
            if(rule!=null&&rule.pageMode==NoteForbiddenZones.PageMode.CUSTOM&&rule.customPages!=null)
                for(Integer page:rule.customPages)if(page!=null)last=Math.max(last,page);
        if(zones.exceptions!=null)for(NoteForbiddenZones.PageException exception:zones.exceptions)
            if(exception!=null)last=Math.max(last,exception.page);
        return last;
    }

    /** Verify every actual pen-down segment against the page's expanded forbidden rectangles. */
    public static void validatePagePaths(NoteDocument document,int pageIndex,float dpi,AffineTransform transform,List<e> paths){
        if(document==null||document.page==null||document.forbiddenZones==null||paths==null)throw new IllegalArgumentException("缺少禁写区路径校验数据");
        if(pageIndex<0||pageIndex>=NoteForbiddenZones.MAX_PAGE_NUMBER||!Float.isFinite(dpi)||dpi<=0)throw new IllegalArgumentException("禁写区路径页码或分辨率无效");
        try{document.forbiddenZones.validate(document.page.pageWidth,document.page.pageHeight);}
        catch(RuntimeException invalid){throw new IllegalStateException("禁写区规则无效，已阻止输出："+invalid.getMessage(),invalid);}
        List<NoteForbiddenZones.ResolvedZone> zones=document.forbiddenZones.resolve(pageIndex+1,document.page.pageWidth,document.page.pageHeight);
        if(zones.isEmpty())return;
        AffineTransform matrix=transform==null?new AffineTransform():new AffineTransform(transform);
        double[] matrixValues=new double[6];matrix.getMatrix(matrixValues);
        for(double value:matrixValues)if(!Double.isFinite(value))throw new IllegalStateException("机器坐标变换无效，已阻止输出");
        double determinant=matrix.getDeterminant();if(!Double.isFinite(determinant)||determinant==0d)throw new IllegalStateException("机器坐标变换不可逆，已阻止输出");
        List<Path2D.Double> polygons=new ArrayList<Path2D.Double>(zones.size());
        for(NoteForbiddenZones.ResolvedZone zone:zones)polygons.add(polygon(zone,dpi,matrix));
        for(int pathIndex=0;pathIndex<paths.size();pathIndex++){
            e path=paths.get(pathIndex);if(path==null)throw new IllegalStateException("禁写区路径包含空路径");
            final float[] xy;final byte[] flags;
            try{xy=path.a();flags=path.b();}
            catch(RuntimeException malformed){throw new IllegalStateException("禁写区路径坐标数据不可读取，已阻止输出",malformed);}
            if(xy==null||flags==null||xy.length!=flags.length*2)throw new IllegalStateException("禁写区路径命令长度无效");
            double previousX=0,previousY=0;boolean hasPrevious=false;
            for(int command=0;command<flags.length;command++){
                double x=xy[command*2],y=xy[command*2+1];
                if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalStateException("禁写区路径包含非有限坐标");
                if(flags[command]==0){previousX=x;previousY=y;hasPrevious=true;continue;}
                if(flags[command]!=1)throw new IllegalStateException("禁写区路径包含未知绘制命令");
                if(!hasPrevious)throw new IllegalStateException("禁写区路径在落笔前缺少抬笔定位，已阻止输出");
                if(hasPrevious){
                    for(int zoneIndex=0;zoneIndex<polygons.size();zoneIndex++)if(intersects(polygons.get(zoneIndex),previousX,previousY,x,y)){
                        NoteForbiddenZones.ResolvedZone zone=zones.get(zoneIndex);
                        throw new IllegalStateException("第 "+(pageIndex+1)+" 页的笔迹路径 #"+(pathIndex+1)+" 线段 ("+previousX+", "+previousY+") → ("+x+", "+y+") 穿过禁写区“"+zone.name+"”；已阻止输出。请调整版式或区域位置。");
                    }
                }
                previousX=x;previousY=y;hasPrevious=true;
            }
        }
    }

    public static void validateLayout(NoteDocument document,NoteLayout layout,float dpi){
        if(layout==null||layout.pages==null)throw new IllegalArgumentException("缺少笔迹布局");
        for(int page=0;page<layout.pages.size();page++){
            NoteLayout.Page value=layout.pages.get(page);
            try{validatePagePaths(document,page,dpi,null,value.paths);}
            catch(IllegalStateException failure){
                String message=failure.getMessage();int marker=message==null?-1:message.indexOf("路径 #");
                if(marker>=0){
                    int start=marker+4,end=start;while(end<message.length()&&Character.isDigit(message.charAt(end)))end++;
                    try{
                        int index=Integer.parseInt(message.substring(start,end))-1;
                        if(index>=0&&index<value.paths.size())for(NoteLayout.Glyph glyph:value.glyphs)if(glyph.path==value.paths.get(index)){
                            throw new IllegalStateException("字符“"+glyph.text+"”（ID "+glyph.id+"，字格 "+glyph.cell+"，墨迹边界 "+glyph.inkBounds+"）"+message,failure);
                        }
                    }catch(NumberFormatException ignored){}
                }
                throw failure;
            }
        }
    }

    private static Path2D.Double polygon(NoteForbiddenZones.ResolvedZone zone,float dpi,AffineTransform transform){
        double left=px(zone.left(),dpi)-EPSILON_PX,top=px(zone.top(),dpi)-EPSILON_PX;
        double right=px(zone.right(),dpi)+EPSILON_PX,bottom=px(zone.bottom(),dpi)+EPSILON_PX;
        Point2D[] points={new Point2D.Double(left,top),new Point2D.Double(right,top),new Point2D.Double(right,bottom),new Point2D.Double(left,bottom)};
        transform.transform(points,0,points,0,points.length);
        for(Point2D point:points)if(!Double.isFinite(point.getX())||!Double.isFinite(point.getY()))throw new IllegalStateException("禁写区机器坐标超出数值范围，已阻止输出");
        Path2D.Double polygon=new Path2D.Double();polygon.moveTo(points[0].getX(),points[0].getY());
        for(int i=1;i<points.length;i++)polygon.lineTo(points[i].getX(),points[i].getY());polygon.closePath();return polygon;
    }

    private static boolean intersects(Path2D.Double polygon,double x1,double y1,double x2,double y2){
        if(polygon.contains(x1,y1)||polygon.contains(x2,y2))return true;
        double[] coordinates=new double[6];double firstX=0,firstY=0,previousX=0,previousY=0;boolean first=true;
        for(java.awt.geom.PathIterator iterator=polygon.getPathIterator(null);!iterator.isDone();iterator.next()){
            int type=iterator.currentSegment(coordinates);
            if(type==java.awt.geom.PathIterator.SEG_MOVETO){firstX=previousX=coordinates[0];firstY=previousY=coordinates[1];first=false;}
            else if(type==java.awt.geom.PathIterator.SEG_LINETO){
                if(Line2D.linesIntersect(x1,y1,x2,y2,previousX,previousY,coordinates[0],coordinates[1]))return true;
                previousX=coordinates[0];previousY=coordinates[1];
            }else if(type==java.awt.geom.PathIterator.SEG_CLOSE&&!first){
                if(Line2D.linesIntersect(x1,y1,x2,y2,previousX,previousY,firstX,firstY))return true;
            }
        }
        return false;
    }
}
