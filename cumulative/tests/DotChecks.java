package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.data.PageInfo;
import com.kenjoy.kenjoycnc.processTabs.NoteTab;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Focused period fallback checks using production image and Gcode consumers; no devices. */
public final class DotChecks {
    private static int checks;
    private static void check(String name,boolean pass){if(!pass)throw new AssertionError(name);checks++;System.out.println("DOT PASS "+name);}
    private static NoteDocument document(String text){NoteDocument d=new NoteDocument();d.page=new PageInfo("Periods",100,60,10,10,10,10,5,100,true);d.page.space=0;d.format.autoGp=false;d.replace(0,0,text);return d;}
    private static Rectangle2D bounds(NoteLayout.Glyph glyph){return NoteLayout.path(Collections.singletonList(glyph.path)).getBounds2D();}
    public static void main(String[] args)throws Exception{
        com.kenjoy.kenjoycnc.o.c.c="kvenjoy";
        com.kenjoy.kenjoycnc.data.MachineData machine=new com.kenjoy.kenjoycnc.data.MachineData();machine.name="DotChecks";machine.type=com.kenjoy.kenjoycnc.machine.MachineType.Grbl;com.kenjoy.kenjoycnc.o.c.e=machine;
        java.lang.reflect.Field settings=com.kenjoy.kenjoycnc.model.Settings.class.getDeclaredField("inst");settings.setAccessible(true);settings.set(null,new com.kenjoy.kenjoycnc.model.Settings());
        // Install only offline command formatters, without constructing a transport or connecting.
        Object controller=com.kenjoy.kenjoycnc.machine.g.a();com.kenjoy.kenjoycnc.machine.grbl.GrblSettings grbl=new com.kenjoy.kenjoycnc.machine.grbl.GrblSettings();grbl.reset();
        String[] slots={"c","h","f"};Object[] values={new com.kenjoy.kenjoycnc.machine.grbl.b(),new com.kenjoy.kenjoycnc.machine.grbl.a(),grbl};for(int i=0;i<slots.length;i++){java.lang.reflect.Field field=controller.getClass().getDeclaredField(slots[i]);field.setAccessible(true);field.set(controller,values[i]);}
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        NoteDocument d=document(".\uFF0E");String original=d.encode();
        NoteLayout layout=new NoteLayoutEngine(null,96).build(d);
        System.out.println("PERIOD_REPRO missing="+layout.missing+" paths="+layout.pages.get(0).paths.size());
        check("halfwidth and fullwidth periods have fallback paths",layout.missing.isEmpty()&&layout.fallback.contains(".")&&layout.fallback.contains("\uFF0E")&&layout.pages.get(0).paths.size()==2);
        check("fallback preserves original code points identity and metadata",original.equals(d.encode()));
        for(NoteLayout.Glyph glyph:layout.pages.get(0).glyphs){
            Rectangle2D ink=bounds(glyph);double baseline=glyph.cell.y+glyph.cell.height*.75;
            check("period is a nonzero closed vector contour "+glyph.text,glyph.path.a().length==26&&ink.getWidth()>0&&ink.getHeight()>0&&Math.abs(ink.getMaxY()-baseline)<.001);
            check("period ink is near baseline rather than middle dot "+glyph.text,ink.getMinY()>baseline-glyph.cell.height*.1&&ink.getMinX()>=glyph.cell.x&&ink.getMaxX()<=glyph.cell.getMaxX());
        }
        check("fullwidth period retains wider advance",layout.pages.get(0).glyphs.get(1).advance>layout.pages.get(0).glyphs.get(0).advance);
        BufferedImage image=NoteTab.image(layout,d,0,96);int dark=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)<0xcccccc)dark++;
        check("production image exporter draws visible period ink",dark>4);ImageIO.write(image,"png",output.resolve("periods.png").toFile());
        com.kenjoy.kenjoycnc.c.a generator=new com.kenjoy.kenjoycnc.c.c(com.kenjoy.kenjoycnc.L.b(layout.pages.get(0).paths),0,false);generator.a();String code=generator.g();
        check("production Gcode generator contains drawing moves",code.contains("G1")&&code.contains("X")&&code.contains("Y"));Files.write(output.resolve("periods.gcode"),code.getBytes("UTF-8"));
        NoteDocument manual=document(".\uFF0E");NoteDocument.FontRef absent=new NoteDocument.FontRef("missing.gfont",String.join("",Collections.nCopies(64,"a")),"Missing");for(NoteDocument.Atom atom:manual.atoms)atom.override=absent;String manualBefore=manual.encode();NoteLayout manualLayout=new NoteLayoutEngine(null,96).build(manual);
        check("missing explicit manual font is not silently substituted",manualLayout.missing.size()==2&&manualLayout.fallback.isEmpty()&&manualLayout.pages.get(0).paths.isEmpty()&&manualBefore.equals(manual.encode()));
        NoteDocument automatic=document(".\uFF0E");for(NoteDocument.Atom atom:automatic.atoms)atom.automatic=absent;String automaticBefore=automatic.encode();NoteLayout automaticLayout=new NoteLayoutEngine(null,96).build(automatic);
        check("missing automatic font falls back without rewriting bindings",automaticLayout.missing.isEmpty()&&automaticLayout.pages.get(0).paths.size()==2&&automaticBefore.equals(automatic.encode()));
        NoteDocument middle=document("\u00B7");NoteLayout middleLayout=new NoteLayoutEngine(null,96).build(middle);NoteLayout.Glyph mg=middleLayout.pages.get(0).glyphs.get(0);
        check("existing middle dot remains raised",bounds(mg).getMaxY()<mg.cell.y+mg.cell.height*.6);
        NoteFonts fonts=new NoteFonts(Paths.get(args[1]).toFile());try{
            NoteFonts.Entry entry=null;for(NoteFonts.Entry candidate:fonts.entries())if(candidate.file.getName().equals("Plot-HersheyScript1.gfont")){entry=candidate;break;}
            check("real font fixture supports ASCII period",entry!=null&&entry.font().a('.')&&entry.font().b('.')!=null);
            NoteDocument real=document(".");real.pool.add(fonts.ref(entry));NoteLayout realLayout=new NoteLayoutEngine(fonts,96).build(real);
            check("real available font takes priority over builtin period",realLayout.missing.isEmpty()&&realLayout.fallback.isEmpty()&&real.atoms.get(0).automatic!=null&&realLayout.pages.get(0).paths.size()==1&&realLayout.pages.get(0).paths.get(0).a().length>0);
            check("real fixture lacks fullwidth period",!entry.font().a('\uFF0E')||entry.font().b('\uFF0E')==null);
            NoteDocument alias=document("\uFF0E");alias.pool.add(fonts.ref(entry));long aliasId=alias.atoms.get(0).id;NoteLayout aliasLayout=new NoteLayoutEngine(fonts,96).build(alias);
            check("fullwidth period borrows selected font ASCII ink before builtin",aliasLayout.missing.isEmpty()&&aliasLayout.fallback.isEmpty()&&alias.atoms.get(0).automatic.hash.equals(real.atoms.get(0).automatic.hash)&&Arrays.equals(aliasLayout.pages.get(0).paths.get(0).a(),realLayout.pages.get(0).paths.get(0).a()));
            check("borrowed period retains original Unicode and atom identity",alias.text.equals("\uFF0E")&&alias.atoms.get(0).text.equals("\uFF0E")&&alias.atoms.get(0).id==aliasId);
            NoteDocument captured=document("\uFF0E");captured.atoms.get(0).inputFonts=new String[]{entry.file.getName()};captured.atoms.get(0).inputChances=new int[]{1};NoteLayout capturedLayout=new NoteLayoutEngine(fonts,96).build(captured);
            check("captured input policy treats ASCII period font as eligible for fullwidth",capturedLayout.fallback.isEmpty()&&capturedLayout.missing.isEmpty()&&Arrays.equals(capturedLayout.pages.get(0).paths.get(0).a(),realLayout.pages.get(0).paths.get(0).a()));
            ImageIO.write(NoteTab.image(aliasLayout,alias,0,96),"png",output.resolve("borrowed-period.png").toFile());
            com.kenjoy.kenjoycnc.c.a borrowedGenerator=new com.kenjoy.kenjoycnc.c.c(com.kenjoy.kenjoycnc.L.b(aliasLayout.pages.get(0).paths),0,false);borrowedGenerator.a();
            com.kenjoy.kenjoycnc.c.a nativeGenerator=new com.kenjoy.kenjoycnc.c.c(com.kenjoy.kenjoycnc.L.b(realLayout.pages.get(0).paths),0,false);nativeGenerator.a();
            check("borrowed period exports exact same real Gcode as font ASCII period",borrowedGenerator.g().equals(nativeGenerator.g())&&borrowedGenerator.g().contains("G1 X"));Files.write(output.resolve("borrowed-period.gcode"),borrowedGenerator.g().getBytes("UTF-8"));
            alias.atoms.get(0).override=fonts.ref(entry);String aliasBound=alias.encode();NoteLayout aliasManual=new NoteLayoutEngine(fonts,96).build(alias);
            check("manual bound font borrows its own ASCII period without changing metadata",aliasManual.missing.isEmpty()&&aliasManual.fallback.isEmpty()&&aliasBound.equals(alias.encode())&&Arrays.equals(aliasManual.pages.get(0).paths.get(0).a(),realLayout.pages.get(0).paths.get(0).a()));
            real.atoms.get(0).override=fonts.ref(entry);String bound=real.encode();NoteLayout boundLayout=new NoteLayoutEngine(fonts,96).build(real);
            check("available manual font remains bound with original sample",boundLayout.fallback.isEmpty()&&boundLayout.missing.isEmpty()&&bound.equals(real.encode()));
        }finally{fonts.close();}
        System.out.println("DOT_CHECKS_COMPLETE "+checks+" Java "+System.getProperty("java.version"));
    }
}
