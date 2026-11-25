package com.kenjoy.kenjoycnc.processTabs;

import com.kenjoy.kenjoycnc.Re_h;
import com.kenjoy.kenjoycnc.L;
import com.kenjoy.kenjoycnc.data.PageInfo;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.model.Settings;
import com.kenjoy.kenjoycnc.note.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Original Note canvas ABI, with document-aware selection above the content layer. */
public final class g extends Re_h {
    private List k=new ArrayList();
    private List l=new ArrayList();
    private List m=new ArrayList();
    private NoteLayout.Page page;
    private NoteSelection selection;
    private Runnable changed,apply,reset;
    private Point pressed,current;
    private boolean dragged,control;
    private PageInfo paper;
    private long focused=-1;
    private NoteTextEditor editor;
    private NoteTab owner;
    private boolean editing;
    private boolean forbiddenZoneDrawing;
    private boolean forbiddenZoneGesture;
    private int forbiddenZonePage=-1;
    public void attachEditor(NoteTab tab){
        owner=tab;editor=new NoteTextEditor(tab);editor.setVisible(false);add(editor);
        editor.addMouseWheelListener(ev->{MouseWheelEvent forwarded=new MouseWheelEvent(this,ev.getID(),ev.getWhen(),ev.getModifiersEx(),ev.getX(),ev.getY(),ev.getClickCount(),ev.isPopupTrigger(),ev.getScrollType(),ev.getScrollAmount(),ev.getWheelRotation());dispatchEvent(forwarded);ev.consume();});
        MouseAdapter pan=new MouseAdapter(){public void mousePressed(MouseEvent e){if(SwingUtilities.isMiddleMouseButton(e))dispatchEvent(SwingUtilities.convertMouseEvent(editor,e,g.this));}public void mouseReleased(MouseEvent e){if(SwingUtilities.isMiddleMouseButton(e))dispatchEvent(SwingUtilities.convertMouseEvent(editor,e,g.this));}public void mouseDragged(MouseEvent e){if((e.getModifiersEx()&InputEvent.BUTTON2_DOWN_MASK)!=0)dispatchEvent(SwingUtilities.convertMouseEvent(editor,e,g.this));}};
        editor.addMouseListener(pan);editor.addMouseMotionListener(pan);
    }
    public NoteTextEditor textEditor(){return editor;}
    public boolean isEditing(){return editing;}
    public void setEditing(boolean active){
        if(editor==null)return;if(!active)editor.finishComposition();if(active)setForbiddenZoneDrawing(false);editing=active;editor.setVisible(active);
        editor.syncFromSession();refreshEditor();if(active)SwingUtilities.invokeLater(()->editor.requestFocusInWindow());else requestFocusInWindow();
    }
    public void setForbiddenZoneDrawing(boolean active){forbiddenZoneDrawing=active;forbiddenZoneGesture=false;forbiddenZonePage=-1;pressed=null;current=null;dragged=false;repaint();}
    public void refreshEditor(){if(editor!=null){Dimension d=getPreferredSize();if(editing)editor.setBounds(0,0,d.width,d.height);firePropertyChange("preferredSize",null,d);firePropertyChange("note.documentView",0,System.nanoTime());revalidate();repaint();}}
    public void syncEditor(){if(editor!=null){editor.syncFromSession();refreshEditor();}}
    public boolean isContinuousView(){return owner!=null;}
    public float displayScale(){return a;}
    public Dimension displayPaperSize(){if(owner==null)return new Dimension(Math.round(h),Math.round(i));Rectangle2D r=rotatedPaper();return new Dimension(Math.round((float)r.getWidth()),Math.round((float)r.getHeight()));}
    public void setDisplayScale(float scale){a=Math.max(.1f,Math.min(8f,scale));super.a(g);refreshEditor();}
    public void a(float angle){super.a(angle);refreshEditor();if(owner!=null)owner.onCanvasAngleChanged();}
    public Dimension getPreferredSize(){if(owner!=null&&owner.geometry()!=null){NoteEditGeometry geo=owner.geometry();Rectangle2D paper=rotatedPaper();float height=(float)paper.getHeight()*geo.layout.pages.size()+geo.pageGap*Math.max(0,geo.layout.pages.size()-1);return new Dimension(Math.max(1,Math.round((float)paper.getWidth()*a)+80),Math.max(1,Math.round(height*a)+60));}return super.getPreferredSize();}
    public void paint(Graphics raw){if(owner==null){super.paint(raw);return;}Graphics2D out=(Graphics2D)raw.create();try{out.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);out.setColor(NotePreviewColors.CANVAS);out.fillRect(0,0,getWidth(),getHeight());if(editing&&editor!=null){editor.paint(out);paintAllForbiddenZones(out);}else paintPreview(out);}finally{out.dispose();}}
    public void scrollToPage(int index){if(owner!=null){Rectangle r=previewPageBounds(index).getBounds();r.height=Math.max(1,Math.min(Math.max(1,getVisibleRect().height),r.height));scrollRectToVisible(r);}}
    public float processingAngle(){return g;}
    private Rectangle2D rotatedPaper(){NoteLayout layout=owner.geometry().layout;Rectangle2D paper=new Rectangle2D.Float(0,0,layout.pageWidth,layout.pageHeight);return AffineTransform.getRotateInstance(Math.toRadians(editing?0:g),layout.pageWidth/2,layout.pageHeight/2).createTransformedShape(paper).getBounds2D();}
    private AffineTransform previewTransform(int index){
        NoteEditGeometry geometry=owner.geometry();Rectangle2D paper=rotatedPaper();AffineTransform transform=new AffineTransform();
        transform.translate(40,30+index*(paper.getHeight()+geometry.pageGap)*a);transform.scale(a,a);transform.translate(-paper.getX(),-paper.getY());
        transform.rotate(Math.toRadians(editing?0:g),geometry.layout.pageWidth/2,geometry.layout.pageHeight/2);return transform;
    }
    private Rectangle2D previewPageBounds(int index){Rectangle2D paper=rotatedPaper();return new Rectangle2D.Double(40,30+index*(paper.getHeight()+owner.geometry().pageGap)*a,paper.getWidth()*a,paper.getHeight()*a);}
    /** Model paper coordinates to the current display only; never used for machine conversion. */
    public Point previewPoint(int index,float x,float y){AffineTransform transform;if(owner==null){transform=new AffineTransform();transform.translate(b.a,b.b);transform.scale(a,a);transform.translate(d.a,d.b);transform.rotate(Math.toRadians(g),h/2,i/2);}else transform=previewTransform(index);Point2D p=transform.transform(new Point2D.Float(x,y),null);return new Point((int)Math.round(p.getX()),(int)Math.round(p.getY()));}
    private static final class PreviewHit {final int index;final NoteLayout.Page page;final float x,y;PreviewHit(int i,NoteLayout.Page p,float x,float y){index=i;page=p;this.x=x;this.y=y;}}
    private PreviewHit previewHit(Point point){
        if(owner==null||owner.geometry().layout.pages.isEmpty())return null;NoteEditGeometry geometry=owner.geometry();Rectangle2D paper=rotatedPaper();
        int index=Math.max(0,Math.min(geometry.layout.pages.size()-1,(int)Math.floor((point.y-30)/(a*(paper.getHeight()+geometry.pageGap)))));
        try{Point2D local=previewTransform(index).inverseTransform(point,null);return new PreviewHit(index,geometry.layout.pages.get(index),(float)local.getX(),(float)local.getY());}catch(NoninvertibleTransformException error){return null;}
    }
    private String forbiddenZoneAt(PreviewHit hit){
        if(hit==null)return null;NoteDocument document=owner.document();
        if(document==null||document.page==null||document.forbiddenZones==null)return null;
        float scale=owner.geometry().layout.pageWidth/document.page.pageWidth;
        float x=hit.x/scale,y=hit.y/scale;
        if(x<0||y<0||x>document.page.pageWidth||y>document.page.pageHeight)return null;
        List<NoteForbiddenZones.ResolvedZone> zones=document.forbiddenZones.resolve(hit.index+1,document.page.pageWidth,document.page.pageHeight);
        for(int index=zones.size()-1;index>=0;index--){NoteForbiddenZones.ResolvedZone zone=zones.get(index);
            if(x>=zone.x&&x<=zone.x+zone.width&&y>=zone.y&&y<=zone.y+zone.height)return zone.id;
        }
        return null;
    }
    private void paintPreview(Graphics2D out){
        NoteEditGeometry geometry=owner.geometry();NoteDocument zoneDocument=owner.document();Rectangle clip=out.getClipBounds();
        for(int index=0;index<geometry.layout.pages.size();index++){
            Rectangle2D bounds=previewPageBounds(index);if(clip!=null&&!clip.intersects(bounds))continue;
            out.setColor(NotePreviewColors.SHADOW);out.fill(new Rectangle2D.Double(bounds.getX()+4,bounds.getY()+5,bounds.getWidth(),bounds.getHeight()));
            out.setColor(NotePreviewColors.PAPER);out.fill(bounds);out.setColor(index==owner.getCurrentPageIndex()?NotePreviewColors.ACCENT:NotePreviewColors.EDGE);out.draw(bounds);
            Graphics2D local=(Graphics2D)out.create();local.transform(previewTransform(index));NoteLayout.Page shown=geometry.layout.pages.get(index);
            paintForbiddenZones(local,zoneDocument,index);
            local.setColor(NotePreviewColors.GUIDE);for(NoteLayout.Line line:shown.lines){local.draw(new Line2D.Float(line.bounds.x,line.bounds.y+line.bounds.height,line.bounds.x+line.bounds.width,line.bounds.y+line.bounds.height));}
            local.setColor(NotePreviewColors.MUTED);for(NoteLayout.Line line:shown.lines)local.drawString("›",line.bounds.x-18,line.bounds.y+line.bounds.height*.7f);
            paintWriteProgress(local,index,shown);
            for(NoteLayout.Glyph glyph:shown.glyphs){
                if(glyph.replacementMarked&&(glyph.pending||!glyph.missing)){local.setColor(NotePreviewColors.REPLACED);local.draw(glyph.cell);}
                if(glyph.whitespace)continue;
                if(selection!=null&&selection.ids().contains(glyph.id)){local.setColor(NotePreviewColors.SELECTION);local.fill(glyph.cell);}
                if(glyph.pending){local.setColor(NotePreviewColors.PENDING);local.setFont(getFont().deriveFont(Math.max(5,glyph.cell.height*.72f)));local.drawString(glyph.text,glyph.cell.x,glyph.cell.y+glyph.cell.height*.76f);}
                else if(glyph.missing){local.setColor(NotePreviewColors.MISSING);local.draw(glyph.cell);local.drawString("?",glyph.cell.x+2,glyph.cell.y+glyph.cell.height*.7f);}
                else{local.setColor(NotePreviewColors.INK);local.draw(NoteLayout.path(Collections.singletonList(glyph.path)));}
                if(hasFocus()&&glyph.id==focused){local.setColor(NotePreviewColors.ACCENT);local.draw(glyph.cell);}
            }
            paintWriteProgressOverlays(local,index,shown);
            local.dispose();out.setColor(NotePreviewColors.MUTED);out.drawString("第 "+(index+1)+" 页"+(index==owner.getCurrentPageIndex()?" · 当前书写页":""),40,(int)Math.round(bounds.getMaxY()+17));
        }
        if(dragged&&pressed!=null&&current!=null){out.setColor(forbiddenZoneDrawing?new Color(205,55,55):new Color(30,100,200));out.draw(screenBox(pressed,current));}
    }
    private void paintWriteProgress(Graphics2D local,int pageIndex,NoteLayout.Page shown){
        if(owner==null||shown==null)return;
        NoteWriteProgress.Snapshot progress=owner.writeProgressForPage(pageIndex);
        long resumeId=owner.resumeGlyphIdForPage(pageIndex);
        if(progress==null&&resumeId<=0L)return;
        Stroke oldStroke=local.getStroke();
        try{
            for(NoteLayout.Glyph glyph:shown.glyphs){
                if(glyph.whitespace||glyph.missing||glyph.pending)continue;
                if(progress!=null&&progress.isConfirmed(glyph.id)){
                    local.setColor(NotePreviewColors.WRITE_CONFIRMED_FILL);local.fill(glyph.cell);
                    local.setColor(NotePreviewColors.WRITE_CONFIRMED_BAR);
                    local.setStroke(new BasicStroke(Math.max(1f,1.5f/Math.max(a,.2f))));
                    local.draw(new Line2D.Float(glyph.cell.x+1f,glyph.cell.y+glyph.cell.height-1f,glyph.cell.x+glyph.cell.width-1f,glyph.cell.y+glyph.cell.height-1f));
                }else if(progress!=null&&progress.isInFlight(glyph.id)){
                    local.setColor(NotePreviewColors.WRITE_INFLIGHT_FILL);local.fill(glyph.cell);
                }
                if(glyph.id==resumeId){
                    local.setColor(NotePreviewColors.WRITE_RESUME_FILL);local.fill(glyph.cell);
                }
            }
        }finally{local.setStroke(oldStroke);}
    }
    private void paintWriteProgressOverlays(Graphics2D local,int pageIndex,NoteLayout.Page shown){
        if(owner==null||shown==null)return;
        NoteWriteProgress.Snapshot progress=owner.writeProgressForPage(pageIndex);
        long resumeId=owner.resumeGlyphIdForPage(pageIndex);
        if(progress==null&&resumeId<=0L)return;
        Stroke oldStroke=local.getStroke();Font oldFont=local.getFont();
        try{
            for(NoteLayout.Glyph glyph:shown.glyphs){
                if(glyph.whitespace||glyph.missing||glyph.pending)continue;
                if(progress!=null&&progress.isInFlight(glyph.id)){
                    local.setColor(NotePreviewColors.WRITE_INFLIGHT_EDGE);
                    local.setStroke(new BasicStroke(Math.max(1.2f,1.8f/Math.max(a,.2f))));
                    local.draw(glyph.cell);
                }
                if(glyph.id==resumeId){
                    local.setColor(NotePreviewColors.WRITE_RESUME_EDGE);
                    local.setStroke(new BasicStroke(Math.max(1.6f,2.4f/Math.max(a,.2f))));
                    local.draw(glyph.cell);
                    Font tagFont=oldFont.deriveFont(Font.BOLD,Math.max(7f,Math.min(12f,glyph.cell.height*.36f)));
                    local.setFont(tagFont);
                    FontMetrics fm=local.getFontMetrics(tagFont);
                    String tag="▶续写";
                    float pad=2f;
                    float boxW=fm.stringWidth(tag)+pad*2f;
                    float boxH=fm.getHeight()+pad;
                    float boxX=Math.max(0f,glyph.cell.x);
                    float boxY=Math.max(0f,glyph.cell.y-boxH-1f);
                    local.setColor(NotePreviewColors.WRITE_BADGE_BG);
                    local.fill(new RoundRectangle2D.Float(boxX,boxY,boxW,boxH,3f,3f));
                    local.setColor(NotePreviewColors.WRITE_RESUME_EDGE);
                    local.drawString(tag,boxX+pad,boxY+fm.getAscent()+pad*.5f);
                }
            }
        }finally{local.setStroke(oldStroke);local.setFont(oldFont);}
    }
    private void paintAllForbiddenZones(Graphics2D out){
        NoteDocument document=owner.document();NoteEditGeometry geometry=owner.geometry();
        for(int index=0;index<geometry.layout.pages.size();index++){Graphics2D local=(Graphics2D)out.create();local.transform(previewTransform(index));paintForbiddenZones(local,document,index);local.dispose();}
    }
    private void paintForbiddenZones(Graphics2D local,NoteDocument document,int pageIndex){
        if(document==null||document.forbiddenZones==null||document.page==null)return;
        Stroke originalStroke=local.getStroke();
        try{
        float scale=owner.geometry().layout.pageWidth/document.page.pageWidth;
        Rectangle2D selectedArea=null;String selectedId=owner.selectedForbiddenZoneId();
        for(NoteForbiddenZones.ResolvedZone zone:document.forbiddenZones.resolve(pageIndex+1,document.page.pageWidth,document.page.pageHeight)){
            Rectangle2D area=new Rectangle2D.Float(zone.x*scale,zone.y*scale,zone.width*scale,zone.height*scale);
            Rectangle2D expanded=new Rectangle2D.Float((zone.x-zone.clearanceMm)*scale,(zone.y-zone.clearanceMm)*scale,(zone.width+zone.clearanceMm*2)*scale,(zone.height+zone.clearanceMm*2)*scale);
            local.setColor(new Color(210,45,45,55));local.fill(area);local.setColor(new Color(180,35,35,190));local.setStroke(new BasicStroke(1.1f));local.draw(area);
            if(zone.clearanceMm>0){local.setColor(new Color(180,35,35,135));local.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{4f,3f},0f));local.draw(expanded);}
            if(zone.id.equals(selectedId))selectedArea=area;
        }
        if(selectedArea!=null){local.setColor(new Color(255,205,65));local.setStroke(new BasicStroke(3f/Math.max(a,.1f)));local.draw(selectedArea);}
        }finally{local.setStroke(originalStroke);}
    }
    private static Rectangle screenBox(Point a,Point b){return new Rectangle(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.abs(a.x-b.x),Math.abs(a.y-b.y));}

    public g() {
        setFocusable(true);setName("NoteCharacterCanvas");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"clear-selection");
        getActionMap().put("clear-selection",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(selection!=null){selection.clear();notifySelection();}}});
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A,InputEvent.CTRL_DOWN_MASK),"all-page");
        getActionMap().put("all-page",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(page!=null&&selection!=null){List<Long> ids=new ArrayList<Long>();for(NoteLayout.Glyph glyph:page.glyphs)if(!glyph.whitespace)ids.add(glyph.id);selection.add(ids,false);notifySelection();}}});
        for(int key:new int[]{KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT,KeyEvent.VK_UP,KeyEvent.VK_DOWN}){final int direction=key;String action="navigate-"+key;getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key,0),action);getActionMap().put(action,new AbstractAction(){public void actionPerformed(ActionEvent ev){navigate(direction);}});}
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,InputEvent.CTRL_DOWN_MASK),"toggle-focused");getActionMap().put("toggle-focused",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(selection!=null&&focused>0){selection.click(focused,true);notifySelection();}}});
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"assign-font");getActionMap().put("assign-font",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(apply!=null)apply.run();}});
    }
    private void navigate(int key){if(page==null||selection==null)return;List<NoteLayout.Glyph> visible=new ArrayList<NoteLayout.Glyph>();for(NoteLayout.Glyph glyph:page.glyphs)if(!glyph.whitespace)visible.add(glyph);if(visible.isEmpty())return;int index=0;for(int i=0;i<visible.size();i++)if(visible.get(i).id==focused)index=i;NoteLayout.Glyph current=visible.get(index),next=current;if(key==KeyEvent.VK_LEFT||key==KeyEvent.VK_RIGHT)next=visible.get(Math.max(0,Math.min(visible.size()-1,index+(key==KeyEvent.VK_LEFT?-1:1))));else{double best=Double.MAX_VALUE;int line=current.line+(key==KeyEvent.VK_UP?-1:1);for(NoteLayout.Glyph glyph:visible)if(glyph.line==line){double d=Math.abs(glyph.cell.getCenterX()-current.cell.getCenterX());if(d<best){best=d;next=glyph;}}}focused=next.id;if(!selection.multiple)selection.click(focused,false);notifySelection();}
    public void mouseWheelMoved(MouseWheelEvent ev){a=Math.max(0.1f,Math.min(8f,a-ev.getWheelRotation()*0.1f));g();c();if(getParent()!=null)b();else repaint();}
    public void bind(NoteLayout.Page page,NoteSelection selection,Runnable changed,Runnable apply,Runnable reset,PageInfo info) {
        this.page=page;this.selection=selection;this.changed=changed;this.apply=apply;this.reset=reset;
        a(new ArrayList<e>(page.paths),info);
    }
    public final void a(List paths,PageInfo info) {
        k=paths;paper=info;a(info.pageWidth,info.pageHeight);l.clear();m.clear();
        float width=Settings.mm2px(info.pageWidth),height=Settings.mm2px(info.pageHeight);
        for(float[] corner:new float[][]{{0,0,-1,-1},{width,0,1,-1},{0,height,-1,1},{width,height,1,1}}) {
            e p=new e();p.a(corner[0]+corner[2]*35,corner[1]);p.b(corner[0],corner[1]);p.b(corner[0],corner[1]+corner[3]*35);l.add(p);
        }
        int count=info.lineNum;float lineHeight=count>0?(height-Settings.mm2px(info.topGap+info.bottomGap))/count:Settings.mm2px(info.lineGap);
        if(count<=0&&lineHeight>0)count=(int)((height-Settings.mm2px(info.topGap+info.bottomGap))/lineHeight);
        for(int i=0;i<=count;i++){e p=new e();float y=Settings.mm2px(info.topGap)+i*lineHeight;p.a(Settings.mm2px(info.leftGap),y);p.b(width-Settings.mm2px(info.rightGap),y);m.add(p);}
        if(getParent()!=null)b();else repaint();
    }
    protected final void a(Graphics2D out) {
        out.setColor(new Color(180,180,180));out.draw(NoteLayout.path(l));out.setColor(new Color(225,225,225));out.draw(NoteLayout.path(m));
        if(page!=null&&selection!=null) {
            out.setColor(new Color(90,155,240,85));for(NoteLayout.Glyph glyph:page.glyphs)if(selection.ids().contains(glyph.id))out.fill(glyph.cell);
            out.setColor(new Color(110,110,110));for(NoteLayout.Line line:page.lines)out.drawString("›",line.bounds.x-18,line.bounds.y+line.bounds.height*.7f);
        }
        a(out,false);
        if(page!=null){out.setColor(new Color(200,65,65));for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.missing){out.draw(glyph.cell);out.drawString("?",glyph.cell.x+2,glyph.cell.y+glyph.cell.height*.7f);}}
        if(hasFocus()&&page!=null){out.setColor(new Color(30,100,200));for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.id==focused)out.draw(glyph.cell);}
        if(dragged&&pressed!=null&&current!=null){out.setColor(new Color(30,100,200));out.draw(documentBox(pressed,current));}
    }
    protected final void a(Graphics2D out,boolean exporting) {out.setColor(Color.BLACK);out.draw(NoteLayout.path(k));}
    private static void a(Graphics2D out,List paths){out.draw(NoteLayout.path(paths));}
    public final List e() {return a(L.b(k));}
    private float[] point(Point p) {return a(new MouseEvent(this,MouseEvent.MOUSE_MOVED,System.currentTimeMillis(),0,p.x,p.y,0,false));}
    private Path2D documentBox(Point a,Point b) {
        Path2D path=new Path2D.Float();Point[] corners={a,new Point(b.x,a.y),b,new Point(a.x,b.y)};
        for(int i=0;i<corners.length;i++){float[] q=point(corners[i]);if(q==null)return path;if(i==0)path.moveTo(q[0],q[1]);else path.lineTo(q[0],q[1]);}path.closePath();return path;
    }
    private void notifySelection() {repaint();if(changed!=null)changed.run();}
    public void mousePressed(MouseEvent ev) {
        if(ev.getButton()==MouseEvent.BUTTON1){
            if((ev.getModifiersEx()&InputEvent.BUTTON2_DOWN_MASK)!=0)return;
            forbiddenZoneGesture=false;
            if(forbiddenZoneDrawing&&owner!=null&&!editing){PreviewHit hit=previewHit(ev.getPoint());if(hit==null||!previewPageBounds(hit.index).contains(ev.getPoint()))return;forbiddenZonePage=hit.index;owner.activatePreviewPage(hit.index);requestFocusInWindow();pressed=ev.getPoint();current=pressed;dragged=false;control=false;return;}
            if(owner!=null&&!editing){
                PreviewHit hit=previewHit(ev.getPoint());String id=forbiddenZoneAt(hit);
                if(id!=null){forbiddenZoneGesture=true;pressed=null;current=null;dragged=false;requestFocusInWindow();owner.selectForbiddenZoneFromCanvas(hit.index,id);repaint();if(ev.getClickCount()==2)owner.editForbiddenZoneFromCanvas(hit.index,id);return;}
                owner.selectForbiddenZoneFromCanvas(hit==null?owner.getCurrentPageIndex():hit.index,null);repaint();
            }
            if(owner!=null&&!editing){PreviewHit hit=previewHit(ev.getPoint());if(hit!=null&&previewPageBounds(hit.index).contains(ev.getPoint()))owner.activatePreviewPage(hit.index);}requestFocusInWindow();pressed=ev.getPoint();current=pressed;dragged=false;control=ev.isControlDown();return;
        }
        if(ev.getButton()==MouseEvent.BUTTON2){forbiddenZoneGesture=false;pressed=null;current=null;dragged=false;}
        super.mousePressed(ev);
        if(ev.isPopupTrigger()||ev.getButton()==MouseEvent.BUTTON3)popup(ev);
    }
    public void mouseDragged(MouseEvent ev) {
        if((ev.getModifiersEx()&InputEvent.BUTTON2_DOWN_MASK)!=0){super.mouseDragged(ev);return;}
        if(forbiddenZoneGesture)return;
        if(pressed!=null){current=ev.getPoint();dragged=pressed.distance(current)>=4;repaint();}else super.mouseDragged(ev);
    }
    public void mouseReleased(MouseEvent ev) {
        if(forbiddenZoneGesture&&ev.getButton()==MouseEvent.BUTTON1){forbiddenZoneGesture=false;super.mouseReleased(ev);return;}
        if(forbiddenZoneDrawing&&owner!=null&&!editing&&pressed!=null&&ev.getButton()==MouseEvent.BUTTON1){
            Point from=pressed,to=ev.getPoint();int selectedPage=forbiddenZonePage;boolean madeDrag=from.distance(to)>=4;pressed=null;current=null;dragged=false;forbiddenZoneDrawing=false;forbiddenZonePage=-1;repaint();
            if(!madeDrag||selectedPage<0){owner.failure(new IllegalArgumentException("请在纸张内拖拽一个矩形区域。"));super.mouseReleased(ev);return;}
            try{
                AffineTransform transform=previewTransform(selectedPage);Point2D a=transform.inverseTransform(from,null),b=transform.inverseTransform(to,null);NoteLayout layout=owner.geometry().layout;
                double x=Math.min(a.getX(),b.getX()),y=Math.min(a.getY(),b.getY()),right=Math.max(a.getX(),b.getX()),bottom=Math.max(a.getY(),b.getY());
                if(x<0||y<0||right>layout.pageWidth||bottom>layout.pageHeight||right-x<1||bottom-y<1)throw new IllegalArgumentException("拖拽矩形必须位于纸张内，且宽高至少 1 像素。");
                owner.createForbiddenZoneFromCanvas(selectedPage,(float)x,(float)y,(float)(right-x),(float)(bottom-y));
            }catch(Exception error){owner.failure(error);}super.mouseReleased(ev);return;
        }
        if(owner!=null&&!editing&&pressed!=null&&ev.getButton()==MouseEvent.BUTTON1&&selection!=null){
            current=ev.getPoint();
            if(dragged){
                Rectangle box=screenBox(pressed,current);List<Long> ids=new ArrayList<Long>();NoteLayout layout=owner.geometry().layout;
                for(int p=0;p<layout.pages.size();p++){if(!box.intersects(previewPageBounds(p)))continue;AffineTransform transform=previewTransform(p);for(NoteLayout.Glyph glyph:layout.pages.get(p).glyphs)if(!glyph.whitespace){Area intersection=new Area(transform.createTransformedShape(glyph.cell));intersection.intersect(new Area(box));if(!intersection.isEmpty())ids.add(glyph.id);}}
                selection.add(ids,control);
            }else{
                PreviewHit hit=previewHit(current);NoteLayout.Line gutter=null;
                if(hit!=null)for(NoteLayout.Line line:hit.page.lines)if(hit.x>=line.bounds.x-24&&hit.x<line.bounds.x&&hit.y>=line.bounds.y&&hit.y<line.bounds.getMaxY())gutter=line;
                if(gutter!=null)selection.add(gutter.ids,control);
                else{NoteLayout.Glyph glyph=hit==null?null:NoteLayout.hit(hit.page,hit.x,hit.y,6/Math.max(a,.1f));if(glyph!=null&&glyph.whitespace)glyph=null;if(glyph!=null)focused=glyph.id;selection.click(glyph==null?null:glyph.id,control);}
            }
            pressed=null;dragged=false;notifySelection();super.mouseReleased(ev);return;
        }
        if(pressed!=null&&ev.getButton()==MouseEvent.BUTTON1&&page!=null&&selection!=null) {
            current=ev.getPoint();float[] q=point(current);
            if(dragged) {
                Area box=new Area(documentBox(pressed,current));List<Long> ids=new ArrayList<Long>();
                for(NoteLayout.Glyph glyph:page.glyphs){Area hit=new Area(glyph.cell);hit.intersect(box);if(!hit.isEmpty())ids.add(glyph.id);}selection.add(ids,control);
            } else if(q!=null) {
                NoteLayout.Line gutter=null;for(NoteLayout.Line line:page.lines)if(q[0]>=line.bounds.x-24&&q[0]<line.bounds.x&&q[1]>=line.bounds.y&&q[1]<line.bounds.getMaxY())gutter=line;
                if(gutter!=null)selection.add(gutter.ids,control);else {NoteLayout.Glyph glyph=NoteLayout.hit(page,q[0],q[1],6/Math.max(a,0.1f));if(glyph!=null)focused=glyph.id;selection.click(glyph==null?null:glyph.id,control);}
            }
            pressed=null;dragged=false;notifySelection();
        }
        super.mouseReleased(ev);
    }
    private void popup(MouseEvent ev) {
        if(page==null||selection==null)return;PreviewHit hit=owner==null?null:previewHit(ev.getPoint());
        if(hit!=null&&previewPageBounds(hit.index).contains(ev.getPoint()))owner.activatePreviewPage(hit.index);
        final NoteLayout.Page selectedPage=hit==null?page:hit.page;float[] q=hit==null?a(ev):new float[]{hit.x,hit.y};NoteLayout.Glyph picked=q==null?null:NoteLayout.hit(selectedPage,q[0],q[1]);final NoteLayout.Glyph glyph=picked!=null&&picked.whitespace?null:picked;
        if(glyph!=null&&!selection.ids().contains(glyph.id)){selection.click(glyph.id,false);notifySelection();}
        JPopupMenu menu=new JPopupMenu();JMenuItem row=new JMenuItem("选中本行");row.setEnabled(glyph!=null);
        row.addActionListener(e->{selection.add(selectedPage.lines.get(glyph.line).ids,false);notifySelection();});menu.add(row);
        JMenuItem font=new JMenuItem("指定所选字字体…");font.setEnabled(!selection.ids().isEmpty());font.addActionListener(e->{if(apply!=null)apply.run();});menu.add(font);
        JMenuItem restore=new JMenuItem("恢复所选字默认字体");restore.setEnabled(!selection.ids().isEmpty());restore.addActionListener(e->{if(reset!=null)reset.run();});menu.add(restore);
        if(owner!=null){
            menu.addSeparator();
            JMenuItem resumeHere=new JMenuItem("从该字开始续写本页");
            resumeHere.setEnabled(glyph!=null&&!glyph.missing&&!glyph.pending&&!owner.isBusy());
            resumeHere.addActionListener(e->owner.setResumeGlyphId(glyph.id));
            menu.add(resumeHere);
            JMenuItem clearResume=new JMenuItem("清除续写起点（从头书写）");
            clearResume.setEnabled(owner.hasResumeProgress()&&!owner.isBusy());
            clearResume.addActionListener(e->owner.clearResumeProgress());
            menu.add(clearResume);
        }
        JMenuItem clear=new JMenuItem("清空选择");clear.addActionListener(e->{selection.clear();notifySelection();});menu.add(clear);menu.show(this,ev.getX(),ev.getY());
    }
    public NoteLayout.Page layoutPage() {return page;}
}
