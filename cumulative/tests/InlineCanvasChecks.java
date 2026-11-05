package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.data.PageInfo;
import com.kenjoy.kenjoycnc.processTabs.NoteTab;
import com.kenjoy.kenjoycnc.processTabs.g;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.text.*;

/** Real Swing text component checks on the EDT, with synthetic IME events (not native IME certification). */
public final class InlineCanvasChecks {
    private static int checks;private static final List<String> failures=new ArrayList<String>();
    private static void check(String name,boolean ok){checks++;System.out.println("INLINE_CANVAS "+(ok?"PASS ":"FAIL ")+name);if(!ok)failures.add(name);}
    private static final class Fixture implements NoteTextEditor.Host {
        final NoteEditSession session;NoteEditGeometry geometry;NoteTextEditor editor;float scale=1;
        final List<Throwable> errors=new ArrayList<Throwable>();
        Fixture(String text){
            NoteDocument d=new NoteDocument();d.page=new PageInfo("Inline",100,60,10,10,10,10,5,100,true);d.page.space=0;d.format.autoGp=false;d.replace(0,0,text);
            session=new NoteEditSession(d);reflow();editor=new NoteTextEditor(this);editor.setSize(400,1600);editor.doLayout();
        }
        void reflow(){geometry=new NoteEditGeometry(NoteEditGeometry.approximate(session.mutableDocument(),25.4f,geometry==null?null:geometry.layout),12);}
        public NoteEditSession session(){return session;}public NoteEditGeometry geometry(){return geometry;}public float scale(){return scale;}
        public void changed(){reflow();}public void selectionChanged(){}public void failure(Throwable error){errors.add(error);}
        public void history(boolean forward){if(forward)session.redo();else session.undo();reflow();editor.syncFromSession();}
        void action(int key,int modifiers){Object binding=editor.getInputMap().get(KeyStroke.getKeyStroke(key,modifiers));Action action=binding==null?null:editor.getActionMap().get(binding);if(action==null)throw new AssertionError("Missing key action "+key+" "+binding);action.actionPerformed(new ActionEvent(editor,ActionEvent.ACTION_PERFORMED,""));}
        void ime(String value,int committed,int caret)throws Exception{Method m=NoteTextEditor.class.getDeclaredMethod("processInputMethodEvent",InputMethodEvent.class);m.setAccessible(true);AttributedString text=value==null?null:new AttributedString(value);m.invoke(editor,new InputMethodEvent(editor,InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,text==null?null:text.getIterator(),committed,caret<0?null:TextHitInfo.leading(caret),null));}
    }
    /** Constructor is deliberately bypassed: this fixture must not start the app's recovery or font workers. */
    public static final class PreviewHost extends NoteTab {
        Fixture fixture;g canvas;int active;
        public PreviewHost(){super(null);}
        public NoteEditSession session(){return fixture.session;}public NoteEditGeometry geometry(){return fixture.geometry;}public float scale(){return canvas.displayScale();}
        public void changed(){fixture.changed();}public void selectionChanged(){}public void history(boolean forward){}public void failure(Throwable e){throw new AssertionError(e);}
        public int getCurrentPageIndex(){return active;}public void activatePreviewPage(int page){active=page;}
    }
    public static void main(String[] args)throws Exception{run();}
    public static void run()throws Exception{
        final Throwable[] error={null};Runnable body=()->{try{checks();}catch(Throwable e){error[0]=e;}};
        if(SwingUtilities.isEventDispatchThread())body.run();else SwingUtilities.invokeAndWait(body);
        if(error[0]!=null)throw new AssertionError("Inline Swing check crashed",error[0]);
        if(!failures.isEmpty())throw new AssertionError("Inline Swing failures: "+failures);
        System.out.println("INLINE_CANVAS_CHECKS_PASS "+checks);
    }
    private static void checks()throws Exception{
        Fixture f=new Fixture("A \tB\n\nC\nD\nE\nF\nG");
        check("custom text view spans continuous pages",f.editor.getPreferredSize().height>f.geometry.layout.pageHeight+60);
        for(int offset=0;offset<=f.editor.getText().length();offset++){
            Rectangle r=f.editor.modelToView(offset);NoteEditGeometry.Position p=f.geometry.caret(offset,false);
            check("modelToView uses current page geometry "+offset,r!=null&&Math.abs(r.x-(40+p.bounds.x))<=1&&Math.abs(r.y-(30+p.bounds.y))<=1);
            check("viewToModel source roundtrip "+offset,f.editor.viewToModel(new Point(r.x,r.y+2))==offset);
        }
        f.scale=2;f.editor.revalidate();Rectangle r=f.editor.modelToView(7);NoteEditGeometry.Position p=f.geometry.caret(7,false);
        check("zoomed caret coordinates scale once",Math.abs(r.x-(40+p.bounds.x*2))<=1&&Math.abs(r.y-(30+p.bounds.y*2))<=1);
        check("zoomed click inverse scales once",f.editor.viewToModel(new Point(r.x,r.y+3))==7);

        Fixture typing=new Fixture("");typing.editor.replaceSelection("a");typing.editor.replaceSelection("b");typing.editor.replaceSelection("c");
        check("Swing text and session agree after consecutive typing",typing.editor.getText().equals("abc")&&typing.session.mutableDocument().text.equals("abc"));
        check("consecutive Swing typing merges one semantic undo",typing.session.undoSize()==1);
        typing.action(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK);check("Ctrl Z updates Swing and semantic text",typing.editor.getText().isEmpty()&&typing.session.mutableDocument().text.isEmpty());
        typing.action(KeyEvent.VK_Y,InputEvent.CTRL_DOWN_MASK);check("Ctrl Y restores source and caret",typing.editor.getText().equals("abc")&&typing.editor.getCaretPosition()==3);
        typing.editor.replaceSelection("\r\nZ");check("Swing insertion normalizes CRLF once",typing.editor.getText().equals("abc\nZ")&&typing.editor.getText().equals(typing.session.mutableDocument().text));

        Fixture backward=new Fixture("A😀B");backward.editor.setCaretPosition(3);backward.action(KeyEvent.VK_BACK_SPACE,0);
        check("Backspace removes complete surrogate atom",backward.editor.getText().equals("AB")&&backward.errors.isEmpty());
        Fixture forward=new Fixture("A😀B");forward.editor.setCaretPosition(1);forward.action(KeyEvent.VK_DELETE,0);
        check("Delete removes complete surrogate atom",forward.editor.getText().equals("AB")&&forward.errors.isEmpty());
        Fixture combining=new Fixture("A\u0301B");combining.editor.setCaretPosition(2);combining.action(KeyEvent.VK_BACK_SPACE,0);
        check("Backspace removes complete combining atom",combining.editor.getText().equals("B")&&combining.errors.isEmpty());
        Fixture move=new Fixture("A😀B");move.editor.setCaretPosition(3);move.action(KeyEvent.VK_LEFT,InputEvent.SHIFT_DOWN_MASK);
        check("Shift Left selects a complete source atom",move.editor.getSelectionStart()==1&&move.editor.getSelectionEnd()==3);
        move.editor.replaceSelection("中");check("selection replacement keeps model source synchronized",move.editor.getText().equals("A中B")&&move.editor.getText().equals(move.session.mutableDocument().text));

        Fixture ime=new Fixture("AB");ime.editor.select(0,1);long before=ime.session.revision();ime.ime("ni",0,2);
        check("preedit does not enter text or undo",ime.editor.compositionText().equals("ni")&&ime.editor.getText().equals("AB")&&ime.session.revision()==before&&!ime.session.canUndo());
        ime.ime("你",1,-1);check("IME commit replaces source selection once",ime.editor.compositionText().isEmpty()&&ime.editor.getText().equals("你B")&&ime.session.mutableDocument().text.equals("你B")&&ime.session.undoSize()==1);
        ime.ime("hao",0,3);ime.ime(null,0,-1);check("IME cancellation leaves committed text intact",ime.editor.compositionText().isEmpty()&&ime.editor.getText().equals("你B")&&ime.session.undoSize()==1);
        ime.editor.setCaretPosition(1);ime.ime("好ma",1,2);check("partial IME commit preserves pending suffix separately",ime.editor.getText().equals("你好B")&&ime.editor.compositionText().equals("ma"));
        check("IME context exposes only committed source",ime.editor.getInputMethodRequests().getCommittedTextLength()==3);
        ime.ime(null,0,-1);ime.action(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK);check("IME transaction undo does not restore preedit",ime.editor.getText().equals("你B")&&ime.editor.compositionText().isEmpty());

        Fixture pages=new Fixture("A\nB\nC\nD\nE\nF\nG");Rectangle first=pages.editor.modelToView(0),last=pages.editor.modelToView(12);
        pages.editor.dispatchEvent(new MouseEvent(pages.editor,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,first.x,first.y+2,1,false,MouseEvent.BUTTON1));
        pages.editor.dispatchEvent(new MouseEvent(pages.editor,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,last.x,last.y+2,0,false,MouseEvent.NOBUTTON));
        pages.editor.dispatchEvent(new MouseEvent(pages.editor,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,last.x,last.y+2,1,false,MouseEvent.BUTTON1));
        check("Swing mouse selection crosses a display page gap",pages.editor.getSelectionStart()==0&&pages.editor.getSelectionEnd()==12);
        pages.editor.replaceSelection("Z");check("cross page deletion uses source range without synthetic gap",pages.editor.getText().equals("ZG")&&pages.session.mutableDocument().text.equals("ZG"));

        Fixture paint=new Fixture("A\nB\nC\nD\nE\nF");BufferedImage image=new BufferedImage(400,400,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();graphics.setColor(Color.MAGENTA);graphics.fillRect(0,0,400,400);paint.editor.paint(graphics);graphics.dispose();
        check("first editable paper has a black background",dark(image.getRGB(45,35)));
        check("second editable paper has the same black background",dark(image.getRGB(45,107)));
        check("input and paint checks do not leak component exceptions",paint.errors.isEmpty()&&ime.errors.isEmpty()&&typing.errors.isEmpty()&&pages.errors.isEmpty());
        navigationChecks();
        wrapBoundaryChecks();
        previewChecks();
    }
    private static void wrapBoundaryChecks()throws Exception{
        Fixture f=new Fixture("abcdefghijklmno");f.session.mutableDocument().page.pageWidth=40;f.session.mutableDocument().shapes.randomFirstPos=0;f.reflow();f.editor.syncFromSession();
        BufferedImage image=new BufferedImage(400,1600,BufferedImage.TYPE_INT_RGB);Graphics2D out=image.createGraphics();f.editor.paint(out);out.dispose();
        int split=f.geometry.layout.pages.get(0).lines.get(0).contentEnd;DefaultCaret caret=(DefaultCaret)f.editor.getCaret();
        f.editor.setCaretPosition(1);f.action(KeyEvent.VK_END,0);
        check("End reaches complete soft row end with backward bias",caret.getDot()==split&&caret.getDotBias()==Position.Bias.Backward);
        f.editor.setCaretPosition(1);f.action(KeyEvent.VK_END,InputEvent.SHIFT_DOWN_MASK);
        check("Shift End includes the last character of a wrapped row",f.editor.getSelectionStart()==1&&f.editor.getSelectionEnd()==split&&caret.getDotBias()==Position.Bias.Backward);
        f.editor.setCaretPosition(split+1);f.action(KeyEvent.VK_HOME,0);
        check("Home reaches current wrapped row start",caret.getDot()==split&&caret.getDotBias()==Position.Bias.Forward);
        f.editor.setCaretPosition(split+1);f.action(KeyEvent.VK_HOME,InputEvent.SHIFT_DOWN_MASK);
        check("Shift Home selects only the current wrapped row prefix",f.editor.getSelectionStart()==split&&f.editor.getSelectionEnd()==split+1);
        NoteEditGeometry.Position p=f.geometry.caret(split,true);int x=Math.round(40+p.bounds.x),y=Math.round(30+p.bounds.y+p.bounds.height/2);
        f.editor.dispatchEvent(new MouseEvent(f.editor,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1));
        f.editor.dispatchEvent(new MouseEvent(f.editor,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
        check("clicking visual soft row end supplies backward bias",caret.getDot()==split&&caret.getDotBias()==Position.Bias.Backward);
        f.editor.syncFromSession();check("unchanged session sync preserves clicked wrap side",caret.getDot()==split&&caret.getDotBias()==Position.Bias.Backward);
        Rectangle visible=f.editor.getUI().modelToView(f.editor,caret.getDot(),caret.getDotBias());Point screen=new Point(visible.x,visible.y);SwingUtilities.convertPointToScreen(screen,f.editor);
        Rectangle candidate=f.editor.getInputMethodRequests().getTextLocation(TextHitInfo.leading(0));check("IME candidate anchor follows the backward soft row end",candidate.x==screen.x&&candidate.y==screen.y&&candidate.height==visible.height);
        final Rectangle[] scroll={null};JPanel parent=new JPanel(null){public void scrollRectToVisible(Rectangle r){scroll[0]=new Rectangle(r);}};parent.add(f.editor);f.editor.setLocation(0,0);f.editor.ensureCaretVisible();Rectangle expectedScroll=new Rectangle(visible);expectedScroll.grow(10,10);
        check("ensure caret visible scrolls to the backward soft row end",expectedScroll.equals(scroll[0]));
        f.action(KeyEvent.VK_HOME,0);check("Home from backward side remains on preceding visual row",caret.getDot()==0);
    }
    private static void navigationChecks()throws Exception{
        Fixture f=new Fixture("ABCDEFGHIJ\nA\nABCDEFGHIJ");f.scale=2;
        BufferedImage image=new BufferedImage(400,1600,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();f.editor.paint(graphics);graphics.dispose();
        f.editor.setCaretPosition(8);f.action(KeyEvent.VK_DOWN,0);
        check("Down reaches the short intervening row",f.editor.getCaretPosition()==12);
        f.action(KeyEvent.VK_DOWN,0);
        check("Down through a short row preserves the original column at zoom",f.editor.getCaretPosition()==21);
        f.action(KeyEvent.VK_UP,InputEvent.SHIFT_DOWN_MASK);f.action(KeyEvent.VK_UP,InputEvent.SHIFT_DOWN_MASK);
        check("Shift Up preserves column and selection through a short row",f.editor.getCaretPosition()==8&&f.editor.getSelectionEnd()==21);
        f.editor.setCaretPosition(0);f.action(KeyEvent.VK_DOWN,0);f.action(KeyEvent.VK_DOWN,0);
        check("explicit caret movement resets the vertical target column",f.editor.getCaretPosition()==13);
    }
    private static void previewChecks()throws Exception{
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        PreviewHost host=(PreviewHost)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,PreviewHost.class);
        java.lang.reflect.Field machine=Class.forName("com.kenjoy.kenjoycnc.o.c").getDeclaredField("e");Object oldMachine=machine.get(null);machine.set(null,new com.kenjoy.kenjoycnc.data.MachineData());
        java.lang.reflect.Field cache=com.kenjoy.kenjoycnc.data.Storage.class.getDeclaredField("storeCache");cache.setAccessible(true);java.util.Map stores=(java.util.Map)cache.get(null);Object oldAxes=stores.put(com.kenjoy.kenjoycnc.data.AxesData.class,new com.kenjoy.kenjoycnc.data.AxesData());
        java.lang.reflect.Field settings=com.kenjoy.kenjoycnc.model.Settings.class.getDeclaredField("inst");settings.setAccessible(true);Object oldSettings=settings.get(null);com.kenjoy.kenjoycnc.model.Settings isolated=new com.kenjoy.kenjoycnc.model.Settings();isolated.useSysDpi=false;settings.set(null,isolated);
        try{
        host.fixture=new Fixture("A\nB\nC\nD\nE\nF\nG");host.canvas=new g();g canvas=host.canvas;canvas.attachEditor(host);
        NoteSelection selected=new NoteSelection();canvas.bind(host.geometry().layout.pages.get(0),selected,()->{},()->{},()->{},host.session().mutableDocument().page);
        canvas.setDisplayScale(1);canvas.setSize(canvas.getPreferredSize());
        check("non editing preview is continuous",canvas.isContinuousView()&&!canvas.isEditing()&&canvas.getPreferredSize().height>130);
        canvas.a(90f);Dimension rotated=canvas.displayPaperSize();
        check("preview rotates each paper width and height",rotated.width==60&&rotated.height==100);
        Point first=canvas.previewPoint(0,15,15),second=canvas.previewPoint(1,15,15);
        check("rotated pages remain vertically stacked",first.x==second.x&&second.y-first.y==112);
        NoteLayout.Glyph target=host.geometry().layout.pages.get(1).glyphs.get(0);Point point=canvas.previewPoint(1,(float)target.cell.getCenterX(),(float)target.cell.getCenterY());
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,point.x,point.y,1,false,MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,point.x,point.y,1,false,MouseEvent.BUTTON1));
        check("rotated second page click selects its source glyph",selected.ids().contains(target.id)&&host.active==1);
        com.kenjoy.kenjoycnc.g.e path=new com.kenjoy.kenjoycnc.g.e();path.a(4,8);path.b(17,26);
        java.util.List<com.kenjoy.kenjoycnc.g.e> before=canvas.a(java.util.Collections.singletonList(path.e()));float[] beforePoints=before.get(0).a().clone();
        canvas.setEditing(true);Dimension upright=canvas.displayPaperSize();Point editFirst=canvas.previewPoint(0,15,15),editSecond=canvas.previewPoint(1,15,15);
        check("editing is upright while processing angle remains ninety",upright.width==100&&upright.height==60&&canvas.processingAngle()==90f&&editSecond.y-editFirst.y==72);
        java.util.List<com.kenjoy.kenjoycnc.g.e> during=canvas.a(java.util.Collections.singletonList(path.e()));
        check("editing transform never changes machining path",java.util.Arrays.equals(beforePoints,during.get(0).a()));
        canvas.setEditing(false);check("leaving editing restores rotated multipage preview",canvas.isContinuousView()&&canvas.displayPaperSize().equals(rotated)&&canvas.previewPoint(1,15,15).equals(second));
        java.util.List<com.kenjoy.kenjoycnc.g.e> after=canvas.a(java.util.Collections.singletonList(path.e()));check("leaving editing preserves machining path",java.util.Arrays.equals(beforePoints,after.get(0).a()));
        darkPreviewChecks(host,canvas);
        replacementMarkerChecks(host,canvas);
        }finally{machine.set(null,oldMachine);if(oldAxes==null)stores.remove(com.kenjoy.kenjoycnc.data.AxesData.class);else stores.put(com.kenjoy.kenjoycnc.data.AxesData.class,oldAxes);settings.set(null,oldSettings);}
    }
    private static boolean dark(int rgb){Color color=new Color(rgb);return Math.max(color.getRed(),Math.max(color.getGreen(),color.getBlue()))<32;}
    private static BufferedImage raster(Component component){BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D out=image.createGraphics();component.paint(out);out.dispose();return image;}
    private static boolean ink(BufferedImage image,Rectangle area,boolean light){for(int y=Math.max(0,area.y);y<Math.min(image.getHeight(),area.y+area.height);y++)for(int x=Math.max(0,area.x);x<Math.min(image.getWidth(),area.x+area.width);x++){Color c=new Color(image.getRGB(x,y));if(light?Math.min(c.getRed(),Math.min(c.getGreen(),c.getBlue()))>120:Math.max(c.getRed(),Math.max(c.getGreen(),c.getBlue()))<80)return true;}return false;}
    private static void darkPreviewChecks(PreviewHost host,g canvas)throws Exception{
        host.fixture=new Fixture("—");host.fixture.geometry=new NoteEditGeometry(new NoteLayoutEngine(null,25.4f).build(host.session().mutableDocument()),12);
        canvas.a(0f);canvas.setDisplayScale(2);canvas.bind(host.geometry().layout.pages.get(0),new NoteSelection(),()->{},()->{},()->{},host.session().mutableDocument().page);canvas.syncEditor();canvas.setSize(canvas.getPreferredSize());
        NoteLayout.Glyph glyph=host.geometry().layout.pages.get(0).glyphs.get(0);float[] original=glyph.path.a().clone();String source=host.session().mutableDocument().encode();Point origin=canvas.previewPoint(0,glyph.cell.x,glyph.cell.y);Rectangle cell=new Rectangle(origin.x,origin.y,(int)Math.ceil(glyph.cell.width*2)+1,(int)Math.ceil(glyph.cell.height*2)+1);
        canvas.setEditing(false);BufferedImage preview=raster(canvas);
        check("non editing preview paper and surrounding canvas are black",dark(preview.getRGB(42,32))&&dark(preview.getRGB(5,5)));
        check("resolved handwriting is light on the black preview",ink(preview,cell,true));
        canvas.setEditing(true);BufferedImage editing=raster(canvas);
        check("editing preview retains black paper and light handwriting",dark(editing.getRGB(42,32))&&ink(editing,cell,true));
        BufferedImage exported=NoteTab.image(host.geometry().layout,host.session().mutableDocument(),0,25.4f);
        check("dark preview still exports white paper and black ink",exported.getRGB(1,1)==Color.WHITE.getRGB()&&ink(exported,glyph.cell.getBounds(),false));
        check("preview colors do not mutate source or machining geometry",source.equals(host.session().mutableDocument().encode())&&java.util.Arrays.equals(original,glyph.path.a()));
    }
    private static boolean hasColor(BufferedImage image,Rectangle area,Color expected){for(int y=Math.max(0,area.y);y<Math.min(image.getHeight(),area.y+area.height);y++)for(int x=Math.max(0,area.x);x<Math.min(image.getWidth(),area.x+area.width);x++){Color c=new Color(image.getRGB(x,y));if(Math.abs(c.getRed()-expected.getRed())<=12&&Math.abs(c.getGreen()-expected.getGreen())<=12&&Math.abs(c.getBlue()-expected.getBlue())<=12)return true;}return false;}
    private static Rectangle displayedCell(g canvas,NoteLayout.Glyph glyph){Point a=canvas.previewPoint(0,glyph.cell.x,glyph.cell.y),b=canvas.previewPoint(0,(float)glyph.cell.getMaxX(),(float)glyph.cell.getMaxY());return new Rectangle(Math.min(a.x,b.x)-1,Math.min(a.y,b.y)-1,Math.abs(a.x-b.x)+3,Math.abs(a.y-b.y)+3);}
    private static void bindMarkerFixture(PreviewHost host,g canvas,boolean exact)throws Exception{
        host.fixture.geometry=new NoteEditGeometry(exact?new NoteLayoutEngine(null,25.4f).build(host.session().mutableDocument()):NoteEditGeometry.approximate(host.session().mutableDocument(),25.4f,null),12);
        canvas.bind(host.geometry().layout.pages.get(0),new NoteSelection(),()->{},()->{},()->{},host.session().mutableDocument().page);canvas.syncEditor();canvas.setSize(canvas.getPreferredSize());
    }
    private static void replacementMarkerChecks(PreviewHost host,g canvas)throws Exception{
        canvas.a(0f);canvas.setDisplayScale(2);host.fixture=new Fixture("X X");NoteDocument d=host.session().mutableDocument();
        d.replaceMissing(java.util.Collections.singleton(d.atoms.get(0).id),".");d.replaceMissing(java.util.Collections.singleton(d.atoms.get(2).id)," ");bindMarkerFixture(host,canvas,true);
        NoteLayout layout=host.geometry().layout;NoteLayout.Glyph dot=layout.pages.get(0).glyphs.get(0),space=layout.pages.get(0).glyphs.get(2);float[] original=dot.path.a().clone();
        check("replacement marker reaches resolved dot and whitespace geometry",dot.replacementMarked&&!dot.missing&&space.replacementMarked&&space.whitespace);
        for(boolean editing:new boolean[]{false,true}){canvas.setEditing(editing);BufferedImage image=raster(canvas);check((editing?"editing":"preview")+" shows blue frame on replacement dot",hasColor(image,displayedCell(canvas,dot),NotePreviewColors.REPLACED));check((editing?"editing":"preview")+" shows blue frame on replacement space",hasColor(image,displayedCell(canvas,space),NotePreviewColors.REPLACED));}
        canvas.setEditing(false);canvas.a(90f);canvas.setSize(canvas.getPreferredSize());check("replacement frame follows rotated preview geometry",hasColor(raster(canvas),displayedCell(canvas,dot),NotePreviewColors.REPLACED));canvas.a(0f);canvas.setSize(canvas.getPreferredSize());
        NoteLayout immediate=NoteEditGeometry.approximate(d,25.4f,layout);check("immediate reflow retains marker and cached handwriting",immediate.pages.get(0).glyphs.get(0).replacementMarked&&!immediate.pages.get(0).glyphs.get(0).pending&&immediate.pages.get(0).glyphs.get(2).replacementMarked);
        BufferedImage marked=NoteTab.image(layout,d,0,25.4f);dot.replacementMarked=false;space.replacementMarked=false;BufferedImage plain=NoteTab.image(layout,d,0,25.4f);
        check("real image export excludes blue replacement frames",!hasColor(marked,new Rectangle(0,0,marked.getWidth(),marked.getHeight()),NotePreviewColors.REPLACED)&&java.util.Arrays.equals(marked.getRGB(0,0,marked.getWidth(),marked.getHeight(),null,0,marked.getWidth()),plain.getRGB(0,0,plain.getWidth(),plain.getHeight(),null,0,plain.getWidth())));
        check("replacement frames do not alter machining paths",java.util.Arrays.equals(original,dot.path.a())&&layout.pages.get(0).paths.size()==1);
        host.fixture=new Fixture("X");host.session().mutableDocument().atoms.get(0).replacementMarked=true;bindMarkerFixture(host,canvas,false);NoteLayout.Glyph pending=host.geometry().layout.pages.get(0).glyphs.get(0);
        check("pending replacement is marked before handwriting completes",pending.pending&&pending.replacementMarked&&hasColor(raster(canvas),displayedCell(canvas,pending),NotePreviewColors.REPLACED));
        bindMarkerFixture(host,canvas,true);NoteLayout.Glyph missing=host.geometry().layout.pages.get(0).glyphs.get(0);
        for(boolean editing:new boolean[]{false,true}){canvas.setEditing(editing);BufferedImage image=raster(canvas);Rectangle cell=displayedCell(canvas,missing);check((editing?"editing":"preview")+" gives missing red frame priority over replacement blue",missing.missing&&hasColor(image,cell,NotePreviewColors.MISSING)&&!hasColor(image,cell,NotePreviewColors.REPLACED));}
    }
}
