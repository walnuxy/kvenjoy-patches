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
    public g() {
        setFocusable(true);setName("NoteCharacterCanvas");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"clear-selection");
        getActionMap().put("clear-selection",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(selection!=null){selection.clear();notifySelection();}}});
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A,InputEvent.CTRL_DOWN_MASK),"all-page");
        getActionMap().put("all-page",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(page!=null&&selection!=null){List<Long> ids=new ArrayList<Long>();for(NoteLayout.Glyph glyph:page.glyphs)ids.add(glyph.id);selection.add(ids,false);notifySelection();}}});
        for(int key:new int[]{KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT,KeyEvent.VK_UP,KeyEvent.VK_DOWN}){final int direction=key;String action="navigate-"+key;getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key,0),action);getActionMap().put(action,new AbstractAction(){public void actionPerformed(ActionEvent ev){navigate(direction);}});}
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,InputEvent.CTRL_DOWN_MASK),"toggle-focused");getActionMap().put("toggle-focused",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(selection!=null&&focused>0){selection.click(focused,true);notifySelection();}}});
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"assign-font");getActionMap().put("assign-font",new AbstractAction(){public void actionPerformed(ActionEvent ev){if(apply!=null)apply.run();}});
    }
    private void navigate(int key){if(page==null||page.glyphs.isEmpty()||selection==null)return;int index=0;for(int i=0;i<page.glyphs.size();i++)if(page.glyphs.get(i).id==focused)index=i;NoteLayout.Glyph current=page.glyphs.get(index),next=current;if(key==KeyEvent.VK_LEFT||key==KeyEvent.VK_RIGHT)next=page.glyphs.get(Math.max(0,Math.min(page.glyphs.size()-1,index+(key==KeyEvent.VK_LEFT?-1:1))));else{double best=Double.MAX_VALUE;int line=current.line+(key==KeyEvent.VK_UP?-1:1);for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.line==line){double d=Math.abs(glyph.cell.getCenterX()-current.cell.getCenterX());if(d<best){best=d;next=glyph;}}}focused=next.id;if(!selection.multiple)selection.click(focused,false);notifySelection();}
    public void mouseWheelMoved(MouseWheelEvent ev){a=Math.max(0.1f,Math.min(8f,a-ev.getWheelRotation()*0.1f));g();c();if(getParent()!=null)b();else repaint();}
    public void bind(NoteLayout.Page page,NoteSelection selection,Runnable changed,Runnable apply,Runnable reset,PageInfo info) {
        this.page=page;this.selection=selection;this.changed=changed;this.apply=apply;this.reset=reset;this.pressed=null;
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
        if(page!=null){out.setColor(new Color(200,65,65));for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.path.a().length==0){out.draw(glyph.cell);out.drawString("?",glyph.cell.x+2,glyph.cell.y+glyph.cell.height*.7f);}}
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
        if(ev.getButton()==MouseEvent.BUTTON1){requestFocusInWindow();pressed=ev.getPoint();current=pressed;dragged=false;control=ev.isControlDown();return;}
        super.mousePressed(ev);
        if(ev.isPopupTrigger()||ev.getButton()==MouseEvent.BUTTON3)popup(ev);
    }
    public void mouseDragged(MouseEvent ev) {
        if(pressed!=null){current=ev.getPoint();dragged=pressed.distance(current)>=4;repaint();}else super.mouseDragged(ev);
    }
    public void mouseReleased(MouseEvent ev) {
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
        if(page==null||selection==null)return;float[] q=a(ev);NoteLayout.Glyph glyph=q==null?null:NoteLayout.hit(page,q[0],q[1]);
        if(glyph!=null&&!selection.ids().contains(glyph.id)){selection.click(glyph.id,false);notifySelection();}
        JPopupMenu menu=new JPopupMenu();JMenuItem row=new JMenuItem("选中本行");row.setEnabled(glyph!=null);
        row.addActionListener(e->{selection.add(page.lines.get(glyph.line).ids,false);notifySelection();});menu.add(row);
        JMenuItem font=new JMenuItem("指定所选字字体…");font.setEnabled(!selection.ids().isEmpty());font.addActionListener(e->{if(apply!=null)apply.run();});menu.add(font);
        JMenuItem restore=new JMenuItem("恢复所选字默认字体");restore.setEnabled(!selection.ids().isEmpty());restore.addActionListener(e->{if(reset!=null)reset.run();});menu.add(restore);
        JMenuItem clear=new JMenuItem("清空选择");clear.addActionListener(e->{selection.clear();notifySelection();});menu.add(clear);menu.show(this,ev.getX(),ev.getY());
    }
    public NoteLayout.Page layoutPage() {return page;}
}
