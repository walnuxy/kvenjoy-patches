import com.kenjoy.kenjoycnc.KenjoyDraw;
import com.kenjoy.kenjoycnc.note.*;
import com.kenjoy.kenjoycnc.processTabs.NoteTab;
import com.kenjoy.kenjoycnc.processTabs.g;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.text.AttributedString;
import java.util.*;
import javax.swing.*;

/** Exercises the actual input component and note pipeline in the isolated application. */
public final class InlineEditorChecks {
    static NoteTab tab;static g canvas;static NoteTextEditor editor;static Path out;
    static final java.util.List<String> rows=new ArrayList<String>();
    static void edt(Runnable r)throws Exception{SwingUtilities.invokeAndWait(r);}
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void check(String name,boolean pass)throws Exception{rows.add((pass?"PASS":"FAIL")+"\t"+name);Files.write(out.resolve("inline-editor-checks.tsv"),rows,java.nio.charset.StandardCharsets.UTF_8);if(!pass)throw new AssertionError(name);}
    static void waitDone()throws Exception{long end=System.currentTimeMillis()+60000;while(true){boolean[] busy={false};edt(()->busy[0]=tab.isBusy());if(!busy[0])return;if(System.currentTimeMillis()>end)throw new AssertionError("inline layout timeout");Thread.sleep(40);}}
    static String text(){return tab.document().text;}
    static void missingReplacementChecks(NoteFonts fonts)throws Exception{
        NoteDocument clean=NoteChecks.sample(fonts),fixture=clean.copy();fixture.replace(0,0,"😀");fixture.replace(fixture.text.length(),0,"🧪");
        edt(()->tab.setDocument(fixture));waitDone();
        NoteDocument baseline=tab.document();
        check("replacement fixture has missing characters across pages",tab.noteLayout().pages.size()>1&&tab.noteLayout().pages.get(0).glyphs.stream().anyMatch(g->g.missing)&&tab.noteLayout().pages.get(tab.noteLayout().pages.size()-1).glyphs.stream().anyMatch(g->g.missing));
        final String original=tab.document().encode();final int history=tab.session().undoSize();
        for(String invalid:new String[]{"","ab","\n","\u0301","\u200D","\uD800"})edt(()->tab.replaceMissingCharacters(invalid));
        check("invalid replacement input leaves document and undo unchanged",original.equals(tab.document().encode())&&history==tab.session().undoSize());
        edt(()->tab.replaceMissingCharacters("🧪"));waitDone();
        check("unsupported replacement rejects the entire operation",tab.lastFailure()!=null&&original.equals(tab.document().encode())&&history==tab.session().undoSize());
        edt(()->{tab.session().setInputFonts(new String[0],new int[0]);tab.replaceMissingCharacters("春");});waitDone();
        check("replacement uses current input policy and rejects empty font pool",tab.lastFailure()!=null&&original.equals(tab.document().encode())&&history==tab.session().undoSize());
        Thread.sleep(1000); // Let prior edits finish their recovery writes before this independent operation.
        String fontName=new java.io.File(fixture.pool.get(0).relativePath).getName();
        edt(()->{tab.session().setInputFonts(new String[]{fontName},new int[]{1});tab.replaceMissingCharacters("春");});waitDone();
        NoteDocument replaced=tab.document();long marked=replaced.atoms.stream().filter(a->a.replacementMarked).count();
        check("one replacement operation repairs all pages and marks changed atoms",tab.isOutputReady()&&marked==2&&replaced.text.startsWith("春")&&replaced.text.endsWith("春")&&tab.session().undoSize()==history+1);
        check("replacement preserves every unaffected source atom",baseline.atoms.stream().filter(a->!a.text.equals("😀")&&!a.text.equals("🧪")).allMatch(a->new com.google.gson.Gson().toJson(a).equals(new com.google.gson.Gson().toJson(replaced.atom(a.id)))));
        check("replacement atoms retain the selected input font policy",replaced.atoms.stream().filter(a->a.replacementMarked).allMatch(a->Arrays.equals(a.inputFonts,new String[]{fontName})&&Arrays.equals(a.inputChances,new int[]{1})&&a.automatic!=null));
        long recoveryDeadline=System.currentTimeMillis()+6000;boolean recovered=false;
        while(System.currentTimeMillis()<recoveryDeadline){NoteRecovery.Snapshot recovery=NoteRecovery.load();if(recovery!=null&&recovery.document.text.equals(replaced.text)&&recovery.document.atoms.stream().filter(a->a.replacementMarked).count()==2){recovered=true;break;}Thread.sleep(40);}
        check("replacement automatically saves recovery text and blue markers before explicit save",recovered);
        String committed=replaced.encode();edt(()->tab.history(false));waitDone();
        check("replacement undo restores original missing characters without blue marks",tab.document().text.equals(fixture.text)&&tab.document().atoms.stream().noneMatch(a->a.replacementMarked)&&!tab.isOutputReady());
        edt(()->tab.history(true));waitDone();check("replacement redo restores exact fonts samples and blue marks",tab.document().encode().equals(committed)&&tab.isOutputReady());
        Path article=out.resolve("inline-replaced.kjnote");edt(()->{try{tab.saveDocument(article);}catch(Exception e){throw new RuntimeException(e);}});
        NoteDocument saved=NoteDocument.load(article);check("saved replacement retains blue markers",saved.encode().equals(committed)&&saved.atoms.stream().filter(a->a.replacementMarked).count()==2);
        edt(()->tab.setDocument(clean));waitDone();
        final boolean[] opened={false};edt(()->opened[0]=tab.openDocument(article.toFile()));waitDone();
        check("real article reopen preserves repaired text and blue markers",opened[0]&&tab.isOutputReady()&&tab.document().encode().equals(committed));
    }
    public static void run()throws Exception{
        out=Paths.get(System.getProperty("note.evidence"));Files.createDirectories(out);
        edt(()->{try{JComboBox modes=(JComboBox)field(KenjoyDraw.a(),"e");for(int i=0;i<modes.getItemCount();i++)if(modes.getItemAt(i) instanceof NoteTab){modes.setSelectedIndex(i);tab=(NoteTab)modes.getItemAt(i);break;}canvas=(g)field(tab,"notePanel");editor=canvas.textEditor();}catch(Exception e){throw new RuntimeException(e);}});
        NoteDocument original=tab.document();NoteFonts fonts=tab.catalogue();NoteDocument sample=NoteChecks.sample(fonts);
        edt(()->tab.setDocument(sample));waitDone();check("sample layout is output ready",tab.isOutputReady());
        AffineTransform[] transform={null};edt(()->{canvas.a(90f);transform[0]=tab.machineTransform();tab.setInlineEditing(true);});
        check("editing preserves processing rotation",canvas.processingAngle()==90f&&transform[0].equals(tab.machineTransform()));
        check("continuous view contains all pages",tab.geometry().layout.pages.size()>1&&canvas.getPreferredSize().height>tab.geometry().layout.pageHeight*canvas.displayScale());
        long first=tab.document().atoms.get(0).id;String base=text();
        edt(()->{editor.setCaretPosition(0);editor.replaceSelection("天地");});
        check("confirmed text is available before layout completes",text().equals("天地"+base));
        check("unaffected character identity preserved immediately",tab.document().atoms.get(2).id==first);
        check("temporary geometry matches the current text",tab.geometry().layout.textLength==text().length());
        waitDone();check("typing automatically reaches output-ready layout",tab.isOutputReady());
        String[] insertedFont={tab.document().atoms.get(0).automatic.hash};
        edt(()->tab.history(false));waitDone();check("one undo removes the input transaction",text().equals(base));
        edt(()->tab.history(true));waitDone();check("redo restores the same random font",text().equals("天地"+base)&&tab.document().atoms.get(0).automatic.hash.equals(insertedFont[0]));
        edt(()->{editor.setCaretPosition(text().length());for(int i=0;i<15;i++)editor.replaceSelection("春");});waitDone();
        check("rapid consecutive typing loses no characters",text().endsWith("春春春春春春春春春春春春春春春"));
        int beforeIme=text().length();
        edt(()->{editor.setCaretPosition(text().length());editor.dispatchEvent(new InputMethodEvent(editor,InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,new AttributedString("你好ni").getIterator(),2,java.awt.font.TextHitInfo.leading(2),null));});
        check("IME commits confirmed text once",text().length()==beforeIme+2&&text().endsWith("你好"));
        check("IME preedit remains outside the document",editor.compositionText().equals("ni")&&!text().endsWith("ni"));
        edt(()->editor.getActionMap().get("note-cancel-composition").actionPerformed(new ActionEvent(editor,0,"")));waitDone();
        check("IME cancellation retains confirmed text",editor.compositionText().isEmpty()&&text().endsWith("你好"));
        edt(()->{editor.select(0,2);editor.copy();editor.setCaretPosition(text().length());editor.paste();});waitDone();
        check("copy paste creates fresh character identities",text().endsWith("天地")&&tab.document().atoms.get(tab.document().atoms.size()-2).id!=tab.document().atoms.get(0).id);
        NoteDocument beforeMove=tab.document();long moving=beforeMove.atoms.get(0).id;String movedFont=beforeMove.atoms.get(0).automatic.hash;
        edt(()->{editor.select(0,2);editor.cut();editor.setCaretPosition(text().length());editor.paste();});waitDone();
        check("same-document cut moves original identity and font",tab.document().atoms.get(tab.document().atoms.size()-2).id==moving&&tab.document().atoms.get(tab.document().atoms.size()-2).automatic.hash.equals(movedFont));
        edt(()->{editor.setCaretPosition(text().length());editor.replaceSelection("😀");});waitDone();
        check("unsupported confirmed character is retained",text().endsWith("😀")&&!tab.noteLayout().missing.isEmpty());
        check("missing character blocks new output",!tab.isOutputReady());
        Path missing=out.resolve("inline-missing.kjnote");edt(()->{try{tab.saveDocument(missing);}catch(Exception e){throw new RuntimeException(e);}});
        check("missing text can be saved and loaded",NoteDocument.load(missing).text.endsWith("😀"));
        boolean rejected=false;try{tab.renderToImage(0);}catch(IllegalStateException expected){rejected=true;}check("image entry cannot silently omit missing text",rejected);
        edt(()->{editor.setCaretPosition(text().length());tab.session().replace(tab.session().previousBoundary(text().length()),2,"",NoteEditSession.Kind.BACKSPACE);tab.changed();canvas.syncEditor();});waitDone();
        check("deleting unsupported whole character restores readiness",tab.isOutputReady());
        final boolean[] mapped={false};edt(()->{try{int offset=tab.document().text.indexOf('\n')+1;Rectangle r=editor.modelToView(offset);int hit=editor.viewToModel(new Point(r.x,r.y+r.height/2));mapped[0]=hit==offset;}catch(Exception e){throw new RuntimeException(e);}});
        check("text position roundtrip uses current page geometry",mapped[0]);
        edt(()->{Dimension size=canvas.getPreferredSize();canvas.setSize(size);BufferedImage img=new BufferedImage(Math.min(1600,size.width),Math.min(1600,size.height),BufferedImage.TYPE_INT_RGB);Graphics2D p=img.createGraphics();canvas.paint(p);p.dispose();try{javax.imageio.ImageIO.write(img,"png",out.resolve("inline-edit-preview.png").toFile());}catch(Exception e){throw new RuntimeException(e);}});
        check("editing paint produces a reviewable image",Files.size(out.resolve("inline-edit-preview.png"))>1000);
        edt(()->tab.setInlineEditing(false));check("leaving edit mode preserves processing transform",canvas.processingAngle()==90f&&transform[0].equals(tab.machineTransform()));
        missingReplacementChecks(fonts);
        edt(()->{canvas.a(0f);tab.setDocument(original);});waitDone();
    }
}
