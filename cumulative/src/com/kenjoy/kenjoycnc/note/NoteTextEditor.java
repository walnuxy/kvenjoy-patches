package com.kenjoy.kenjoycnc.note;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.im.InputMethodRequests;
import java.awt.font.TextHitInfo;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.List;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.*;

/** Swing input/IME client whose text positions are supplied by the note layout. */
public final class NoteTextEditor extends JTextArea {
    public interface Host {
        NoteEditSession session();
        NoteEditGeometry geometry();
        float scale();
        void changed();
        void selectionChanged();
        void history(boolean forward);
        void failure(Throwable error);
    }
    private Host host;
    private boolean syncing;
    private String composition="";
    private int compositionCaret;
    private boolean cancelingComposition;
    private NoteEditSession.Kind editKind=NoteEditSession.Kind.TYPE;
    private static final DataFlavor NOTE_FLAVOR=new DataFlavor(NoteEditSession.ClipboardContent.class,"Note character selection");
    public NoteTextEditor(Host owner) {
        host=owner;setName("NoteInlineText");setOpaque(false);setBorder(null);setMargin(new Insets(0,0,0,0));
        setFont(new Font("Dialog",Font.PLAIN,14));setForeground(NotePreviewColors.INK);setCaretColor(NotePreviewColors.ACCENT);setHighlighter(null);
        enableInputMethods(true);setFocusTraversalKeysEnabled(false);
        ((AbstractDocument)getDocument()).setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass b,int o,String s,AttributeSet a)throws BadLocationException{replace(b,o,0,s,a);}
            public void remove(FilterBypass b,int o,int n)throws BadLocationException{replace(b,o,n,"",null);}
            public void replace(FilterBypass b,int o,int n,String s,AttributeSet attrs)throws BadLocationException{
                String value=NoteDocument.normalize(s==null?"":s);
                if(syncing||host==null){b.replace(o,n,value,attrs);return;}
                try{
                    NoteEditSession.Kind kind=editKind;
                    if(value.indexOf('\n')>=0&&kind==NoteEditSession.Kind.TYPE)kind=NoteEditSession.Kind.NEWLINE;
                    if(value.isEmpty()&&n>0&&kind==NoteEditSession.Kind.TYPE)kind=o<getCaretPosition()?NoteEditSession.Kind.BACKSPACE:NoteEditSession.Kind.DELETE;
                    host.session().replace(o,n,value,kind);b.replace(o,n,value,attrs);host.changed();ensureCaretVisible();
                }catch(RuntimeException error){host.failure(error);Toolkit.getDefaultToolkit().beep();}
            }
        });
        addCaretListener(e->{if(!syncing&&host!=null){host.session().setSelection(e.getMark(),e.getDot());host.selectionChanged();repaint();}});
        bind(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK,"note-undo",()->host.history(false));
        bind(KeyEvent.VK_Y,InputEvent.CTRL_DOWN_MASK,"note-redo",()->host.history(true));
        bind(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK,"note-redo-shift",()->host.history(true));
        bind(KeyEvent.VK_ESCAPE,0,"note-cancel-composition",this::cancelComposition);
        bind(KeyEvent.VK_BACK_SPACE,0,"note-backspace",()->deleteAtom(true));bind(KeyEvent.VK_DELETE,0,"note-delete",()->deleteAtom(false));
        bind(KeyEvent.VK_LEFT,0,"note-left",()->moveAtom(false,false));bind(KeyEvent.VK_RIGHT,0,"note-right",()->moveAtom(true,false));
        bind(KeyEvent.VK_LEFT,InputEvent.SHIFT_DOWN_MASK,"note-select-left",()->moveAtom(false,true));bind(KeyEvent.VK_RIGHT,InputEvent.SHIFT_DOWN_MASK,"note-select-right",()->moveAtom(true,true));
        bind(KeyEvent.VK_HOME,0,"note-line-start",()->moveLineBoundary(false,false));bind(KeyEvent.VK_END,0,"note-line-end",()->moveLineBoundary(true,false));
        bind(KeyEvent.VK_HOME,InputEvent.SHIFT_DOWN_MASK,"note-select-line-start",()->moveLineBoundary(false,true));bind(KeyEvent.VK_END,InputEvent.SHIFT_DOWN_MASK,"note-select-line-end",()->moveLineBoundary(true,true));
        addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent event){finishComposition();}});
        setUI(new GeometryUI());syncFromSession();
    }
    public void updateUI(){if(host==null)super.updateUI();else setUI(new GeometryUI());}
    private void bind(int key,int modifiers,String name,Runnable action){getInputMap().put(KeyStroke.getKeyStroke(key,modifiers),name);getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    public void syncFromSession(){
        if(host==null)return;syncing=true;
        try{String text=host.session().mutableDocument().text;boolean sameText=getText().equals(text);int a=Math.min(text.length(),host.session().anchor()),c=Math.min(text.length(),host.session().caret());if(!sameText)setText(text);if(!sameText||getCaret().getMark()!=a||getCaretPosition()!=c){setCaretPosition(a);moveCaretPosition(c);}}
        finally{syncing=false;}revalidate();repaint();
    }
    public void finishComposition(){if(getInputContext()!=null)getInputContext().endComposition();composition="";repaint();}
    public void cancelComposition(){cancelingComposition=true;try{if(getInputContext()!=null)getInputContext().endComposition();}finally{composition="";compositionCaret=0;cancelingComposition=false;repaint();}}
    private void moveAtom(boolean right,boolean extend){finishComposition();NoteEditSession session=host.session();int target;if(!extend&&getSelectionStart()!=getSelectionEnd())target=right?getSelectionEnd():getSelectionStart();else target=right?session.nextBoundary(getCaretPosition()):session.previousBoundary(getCaretPosition());if(extend)moveCaretPosition(target);else setCaretPosition(target);}
    public boolean caretBackwardBias(){return getCaret() instanceof DefaultCaret&&((DefaultCaret)getCaret()).getDotBias()==Position.Bias.Backward;}
    private void moveLineBoundary(boolean end,boolean extend){finishComposition();NoteEditGeometry.Position p=host.geometry().lineBoundary(getCaretPosition(),caretBackwardBias(),end);Position.Bias bias=p.backward?Position.Bias.Backward:Position.Bias.Forward;if(getCaret() instanceof DefaultCaret){DefaultCaret caret=(DefaultCaret)getCaret();if(extend)caret.moveDot(p.offset,bias);else caret.setDot(p.offset,bias);caret.setMagicCaretPosition(null);}else if(extend)moveCaretPosition(p.offset);else setCaretPosition(p.offset);}
    private void deleteAtom(boolean backward){finishComposition();NoteEditSession session=host.session();int start=getSelectionStart(),end=getSelectionEnd();if(start==end){if(backward)start=session.previousBoundary(start);else end=session.nextBoundary(end);}if(start==end)return;NoteEditSession.Kind previous=editKind;editKind=backward?NoteEditSession.Kind.BACKSPACE:NoteEditSession.Kind.DELETE;try{syncing=true;try{select(start,end);}finally{syncing=false;}replaceSelection("");}finally{editKind=previous;}}
    public void ensureCaretVisible(){try{Rectangle r=getUI().modelToView(this,getCaretPosition(),caretBackwardBias()?Position.Bias.Backward:Position.Bias.Forward);if(r!=null){r.grow(10,10);scrollRectToVisible(r);}}catch(BadLocationException ignored){}}
    public String compositionText(){return composition;}
    protected void processInputMethodEvent(InputMethodEvent event){
        if(cancelingComposition){event.consume();composition="";return;}
        if(event.getID()!=InputMethodEvent.INPUT_METHOD_TEXT_CHANGED){if(event.getCaret()!=null)compositionCaret=event.getCaret().getInsertionIndex();event.consume();repaint();return;}
        AttributedCharacterIterator it=event.getText();StringBuilder value=new StringBuilder();
        if(it!=null)for(char ch=it.first();ch!=AttributedCharacterIterator.DONE;ch=it.next())value.append(ch);
        int count=Math.min(event.getCommittedCharacterCount(),value.length());
        composition="";
        if(count>0){NoteEditSession.Kind previous=editKind;editKind=NoteEditSession.Kind.IME;try{replaceSelection(value.substring(0,count));}finally{editKind=previous;}}
        composition=value.substring(count);compositionCaret=event.getCaret()==null?composition.length():event.getCaret().getInsertionIndex();event.consume();repaint();
    }
    public InputMethodRequests getInputMethodRequests(){return new InputMethodRequests(){
        public Rectangle getTextLocation(TextHitInfo offset){try{Rectangle r=getUI().modelToView(NoteTextEditor.this,getCaretPosition(),caretBackwardBias()?Position.Bias.Backward:Position.Bias.Forward);if(r==null)r=new Rectangle(0,0,1,20);int insertion=offset==null?compositionCaret:offset.getInsertionIndex();insertion=Math.max(0,Math.min(composition.length(),insertion));Font preedit=getFont().deriveFont(Math.max(8,r.height*.75f));int dx=getFontMetrics(preedit).stringWidth(composition.substring(0,insertion));Point p=new Point(r.x+dx,r.y);SwingUtilities.convertPointToScreen(p,NoteTextEditor.this);return new Rectangle(p.x,p.y,Math.max(1,r.width),Math.max(1,r.height));}catch(Exception e){return new Rectangle(0,0,1,20);}}
        public TextHitInfo getLocationOffset(int x,int y){return null;}
        public int getInsertPositionOffset(){return getCaretPosition();}
        public AttributedCharacterIterator getCommittedText(int begin,int end,AttributedCharacterIterator.Attribute[] attrs){String t=host.session().mutableDocument().text;return new AttributedString(t.substring(Math.max(0,begin),Math.min(t.length(),end))).getIterator();}
        public int getCommittedTextLength(){return host.session().mutableDocument().text.length();}
        public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attrs){return null;}
        public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attrs){String s=NoteTextEditor.this.getSelectedText();return s==null?null:new AttributedString(s).getIterator();}
    };}
    public void copy(){clipboard(false);}
    public void cut(){clipboard(true);}
    private void clipboard(boolean cut){
        if(host==null||getSelectionStart()==getSelectionEnd())return;finishComposition();
        host.session().setSelection(getCaret().getMark(),getCaretPosition());
        NoteEditSession.ClipboardContent content=host.session().copySelection();String text=getSelectedText();
        try{NoteTransfer transfer=new NoteTransfer(content,text);Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transfer,null);
            if(cut){transfer.content=host.session().cutSelection();syncFromSession();host.changed();}}
        catch(Exception error){host.failure(error);}
    }
    private static final class NoteTransfer implements Transferable {
        NoteEditSession.ClipboardContent content;final String text;
        NoteTransfer(NoteEditSession.ClipboardContent c,String s){content=c;text=s;}
        public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{NOTE_FLAVOR,DataFlavor.stringFlavor};}
        public boolean isDataFlavorSupported(DataFlavor f){return f.equals(NOTE_FLAVOR)||f.equals(DataFlavor.stringFlavor);}
        public Object getTransferData(DataFlavor f)throws UnsupportedFlavorException{if(f.equals(NOTE_FLAVOR))return content;if(f.equals(DataFlavor.stringFlavor))return text;throw new UnsupportedFlavorException(f);}
    }
    public void paste(){
        if(host==null)return;finishComposition();try{Transferable t=Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);if(t==null)return;
            host.session().setSelection(getCaret().getMark(),getCaretPosition());
            if(t.isDataFlavorSupported(NOTE_FLAVOR))host.session().paste((NoteEditSession.ClipboardContent)t.getTransferData(NOTE_FLAVOR));
            else if(t.isDataFlavorSupported(DataFlavor.stringFlavor))host.session().pasteText((String)t.getTransferData(DataFlavor.stringFlavor));
            syncFromSession();host.changed();
        }catch(Exception error){host.failure(error);}
    }
    private float scale(){return host==null?1:host.scale();}
    private Rectangle viewBounds(NoteEditGeometry.Position p){float s=scale();return new Rectangle(Math.round(40+p.bounds.x*s),Math.round(30+p.bounds.y*s),Math.max(1,Math.round(p.bounds.width*s)),Math.max(2,Math.round(p.bounds.height*s)));}
    private final class GeometryUI extends BasicTextAreaUI {public View create(Element element){return new GeometryView(element);}}
    private final class GeometryView extends View {
        GeometryView(Element e){super(e);}
        public float getPreferredSpan(int axis){NoteEditGeometry g=host==null?null:host.geometry();return g==null?100:(axis==View.X_AXIS?g.width*scale()+80:g.height*scale()+60);}
        public Shape modelToView(int pos,Shape area,Position.Bias bias)throws BadLocationException{return viewBounds(host.geometry().caret(pos,bias==Position.Bias.Backward));}
        public int viewToModel(float x,float y,Shape area,Position.Bias[] bias){NoteEditGeometry.Position p=host.geometry().hit((x-40)/scale(),(y-30)/scale());bias[0]=p.backward?Position.Bias.Backward:Position.Bias.Forward;return p.offset;}
        public int getNextVisualPositionFrom(int pos,Position.Bias b,Shape area,int direction,Position.Bias[] result)throws BadLocationException{
            if(pos<0){result[0]=Position.Bias.Forward;return 0;}NoteEditSession s=host.session();NoteEditGeometry geometry=host.geometry();NoteEditGeometry.Position p=geometry.caret(pos,b==Position.Bias.Backward);
            if(direction==SwingConstants.NORTH||direction==SwingConstants.SOUTH){Point target=getCaret().getMagicCaretPosition();float x=target==null?p.bounds.x:(target.x-40)/scale();p=geometry.nextLine(pos,b==Position.Bias.Backward,direction==SwingConstants.NORTH?-1:1,x);result[0]=p.backward?Position.Bias.Backward:Position.Bias.Forward;return p.offset;}
            result[0]=Position.Bias.Forward;return direction==SwingConstants.WEST?s.previousBoundary(pos):s.nextBoundary(pos);
        }
        public void paint(Graphics raw,Shape area){paintDocument((Graphics2D)raw);}
    }
    private void paintDocument(Graphics2D raw){
        if(host==null||host.geometry()==null)return;Graphics2D out=(Graphics2D)raw.create();NoteEditGeometry geom=host.geometry();float s=scale();
        out.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);out.translate(40,30);out.scale(s,s);
        Rectangle2D clip=out.getClipBounds();NoteDocument d=host.session().mutableDocument();
        List<Rectangle2D.Float> selected=geom.selection(getSelectionStart(),getSelectionEnd());
        for(int index=0;index<geom.layout.pages.size();index++){
            Rectangle2D paper=geom.pageBounds(index);if(clip!=null&&!clip.intersects(paper))continue;
            out.setColor(NotePreviewColors.SHADOW);out.fill(new Rectangle2D.Double(paper.getX()+4,paper.getY()+5,paper.getWidth(),paper.getHeight()));out.setColor(NotePreviewColors.PAPER);out.fill(paper);
            out.setColor(NotePreviewColors.EDGE);out.draw(paper);
            out.setColor(NotePreviewColors.SELECTION);for(Rectangle2D r:selected)if(r.intersects(paper))out.fill(r);
            Graphics2D page=(Graphics2D)out.create();page.translate(0,geom.pageOriginY(index));
            page.setColor(NotePreviewColors.GUIDE);for(NoteLayout.Line line:geom.layout.pages.get(index).lines)page.draw(new Line2D.Float(line.bounds.x,line.bounds.y+line.bounds.height,line.bounds.x+line.bounds.width,line.bounds.y+line.bounds.height));
            for(NoteLayout.Glyph glyph:geom.layout.pages.get(index).glyphs){
                if(glyph.replacementMarked&&(glyph.pending||!glyph.missing)){page.setColor(NotePreviewColors.REPLACED);page.draw(glyph.cell);}
                if(glyph.whitespace)continue;
                if(glyph.pending){page.setColor(NotePreviewColors.PENDING);page.setFont(getFont().deriveFont(Math.max(5,glyph.cell.height*.72f)));page.drawString(glyph.text,glyph.cell.x,glyph.cell.y+glyph.cell.height*.76f);}
                else if(glyph.missing){page.setColor(NotePreviewColors.MISSING);page.draw(glyph.cell);page.setFont(getFont().deriveFont(glyph.cell.height*.55f));page.drawString("?",glyph.cell.x+2,glyph.cell.y+glyph.cell.height*.7f);}
                else{page.setColor(NotePreviewColors.INK);page.draw(NoteLayout.path(java.util.Collections.singletonList(glyph.path)));}
            }
            page.dispose();out.setColor(NotePreviewColors.MUTED);out.setFont(getFont().deriveFont(11f));out.drawString("第 "+(index+1)+" 页",0,(float)paper.getMaxY()+17);
        }
        if(!composition.isEmpty()){
            NoteEditGeometry.Position p=geom.caret(getCaretPosition(),caretBackwardBias());out.setFont(getFont().deriveFont(Math.max(8,p.bounds.height*.75f)));out.setColor(NotePreviewColors.ACCENT);float x=p.bounds.x,y=p.bounds.y+p.bounds.height*.76f;out.drawString(composition,x,y);out.draw(new Line2D.Float(x,y+2,x+out.getFontMetrics().stringWidth(composition),y+2));
        }
        out.dispose();
    }
}
