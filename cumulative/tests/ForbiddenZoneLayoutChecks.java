import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.note.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

/** Headless regressions with real built-in strokes; no user fonts, windows or devices required. */
public final class ForbiddenZoneLayoutChecks {
    private ForbiddenZoneLayoutChecks(){}
    private static final float DPI=25.4f;
    private static NoteDocument base(){
        NoteDocument d=new NoteDocument();d.page=new com.kenjoy.kenjoycnc.data.PageInfo("禁写区偏移检查",160,100,10,10,10,10,5,75,true);
        d.page.space=.8f;d.format.autoGp=false;d.replace(0,0,"—");return d;
    }
    private static NoteDocument jitter(){
        NoteDocument d=base();d.appearance.randomMode=1;d.appearance.dx=.1f;
        d.appearance.size=d.appearance.gap=d.appearance.dy=d.appearance.rotation=0;
        d.atoms.get(0).effect=new NoteAppearance.State();d.atoms.get(0).effect.noise[5]=2.5f;return d;
    }
    private static void strip(NoteDocument d,float width){d.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("可写条带",10+width+.02f,0,150-width-.02f,100,0));}
    private static NoteLayout build(NoteDocument d,NoteFonts fonts)throws Exception{
        d.validate();NoteLayout l=new NoteLayoutEngine(fonts,DPI).build(d);
        if(!l.missing.isEmpty())throw new AssertionError("test requires real strokes, not missing placeholders: "+l.missing);
        NoteForbiddenZoneGeometry.validateLayout(d,l,DPI);return l;
    }
    private static NoteLayout.Glyph glyph(NoteLayout layout){
        for(NoteLayout.Page p:layout.pages)for(NoteLayout.Glyph g:p.glyphs)if(!g.whitespace)return g;
        throw new AssertionError("missing test glyph");
    }
    private static boolean translated(e actual,e original,float dx,float dy){
        if(!Arrays.equals(actual.b(),original.b()))return false;float[] a=actual.a(),b=original.a();if(a.length!=b.length||a.length<4)return false;
        for(int i=0;i<a.length;i++)if(Math.abs(a[i]-b[i]-(i%2==0?dx:dy))>.003f)return false;return true;
    }
    private static boolean noFit(NoteDocument d,NoteFonts fonts)throws Exception{
        try{build(d,fonts);return false;}catch(IOException expected){return expected.getMessage().contains("后续可写行");}
    }
    private static NoteDocument punctuation(String text,boolean forbidden){
        NoteDocument d=base();d.replace(0,d.text.length(),text);
        if(forbidden)d.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("贯穿所有行的中部障碍",62,0,36,100,0));
        return d;
    }
    private static void longPunctuation(NoteFonts fonts,boolean forbidden)throws Exception{
        StringBuilder text=new StringBuilder();for(int i=0;i<40;i++)text.append('.');
        NoteDocument d=punctuation(text.toString(),forbidden);NoteLayout layout=build(d,fonts);
        StringBuilder actual=new StringBuilder();boolean laterRow=false,left=false,right=false,inside=true;
        for(NoteLayout.Page page:layout.pages)for(NoteLayout.Glyph g:page.glyphs){
            actual.append(g.text);laterRow|=g.line>0;left|=g.cell.x<62;right|=g.cell.x>98;
            inside&=g.path.c()>0&&g.inkBounds.getMinX()>=10&&g.inkBounds.getMaxX()<=150&&g.inkBounds.getMinY()>=10&&g.inkBounds.getMaxY()<=90;
        }
        String context=forbidden?"with forbidden intervals":"without forbidden zones";
        NoteChecks.check("long punctuation wraps intact "+context,layout.pages.size()==1&&laterRow&&actual.toString().equals(d.text));
        NoteChecks.check("long punctuation retains real in-bounds strokes "+context,inside&&(!forbidden||(left&&right)));
    }
    private static void shortPunctuation(NoteFonts fonts)throws Exception{
        NoteDocument plain=punctuation("———————————.",false);NoteLayout layout=build(plain,fonts);
        NoteLayout.Glyph before=layout.pages.get(0).glyphs.get(9),anchor=layout.pages.get(0).glyphs.get(10),period=layout.pages.get(0).glyphs.get(11);
        NoteChecks.check("short punctuation group moves with its anchor to the next row",before.line==0&&anchor.line==1&&period.line==1&&period.cell.x>anchor.cell.x);
        NoteDocument zones=punctuation("————.",true);layout=build(zones,fonts);
        before=layout.pages.get(0).glyphs.get(2);anchor=layout.pages.get(0).glyphs.get(3);period=layout.pages.get(0).glyphs.get(4);
        NoteChecks.check("short punctuation group moves intact to the next forbidden interval",before.cell.x<62&&anchor.cell.x>98&&period.cell.x>anchor.cell.x&&anchor.line==0&&period.line==0);
    }
    public static void run(NoteFonts fonts)throws Exception{
        NoteDocument narrow=jitter();strip(narrow,12);NoteLayout fitted=build(narrow,fonts);NoteLayout.Glyph shifted=glyph(fitted);
        NoteDocument quiet=narrow.copy();quiet.appearance.dx=0;NoteLayout.Glyph basis=glyph(build(quiet,fonts));
        NoteChecks.check("forbidden strip attenuates horizontal jitter instead of rejecting a fitting glyph",fitted.pages.size()==1&&shifted.line==0&&shifted.path.c()>0&&shifted.inkBounds.getMaxX()<=22.001f);
        NoteChecks.check("horizontal attenuation preserves every stroke and the original glyph width",Math.abs(shifted.layoutWidth-basis.layoutWidth)<.001f&&translated(shifted.path,basis.path,.6f,0));
        NoteChecks.check("horizontal attenuation preserves frozen random settings",narrow.appearance.dx==.1f&&narrow.atoms.get(0).effect.noise[5]==2.5f);

        NoteDocument roomy=jitter();NoteLayout.Glyph unchanged=glyph(build(roomy,fonts));NoteDocument noJitter=roomy.copy();noJitter.appearance.dx=0;
        NoteChecks.check("requested horizontal jitter remains unchanged when there is room",translated(unchanged.path,glyph(build(noJitter,fonts)).path,1.2f,0));
        NoteDocument tooNarrow=jitter();strip(tooNarrow,11);
        NoteChecks.check("horizontal attenuation does not shrink a genuinely oversized base glyph",noFit(tooNarrow,fonts));

        NoteDocument pair=jitter();pair.replace(pair.text.length(),0,"—");pair.atoms.get(1).effect=new NoteAppearance.State();pair.atoms.get(1).effect.noise[5]=2.5f;strip(pair,24);
        NoteLayout paired=build(pair,fonts);NoteLayout.Glyph a=paired.pages.get(0).glyphs.get(0),b=paired.pages.get(0).glyphs.get(1);
        NoteChecks.check("attenuated horizontal jitter preserves the minimum inter-glyph ink gap",a.line==b.line&&b.inkBounds.x-a.inkBounds.getMaxX()>=pair.appearance.minGapMm-.001f);

        NoteDocument legacy=base();legacy.atoms.get(0).sampled=true;legacy.atoms.get(0).y=1;
        NoteLayout.Glyph original=glyph(build(legacy,fonts));
        legacy.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("行框之外的实际障碍",5,34,145,3,0));NoteLayout relocated=build(legacy,fonts);NoteLayout.Glyph moved=glyph(relocated);
        NoteChecks.check("legacy vertical ink outside the nominal row retries later writable rows",relocated.pages.size()==1&&moved.line==2&&moved.inkBounds.y>37);
        NoteChecks.check("legacy obstacle avoidance translates whole strokes without rerolling or clipping",legacy.atoms.get(0).sampled&&legacy.atoms.get(0).y==1&&translated(moved.path,original.path,0,32));

        NoteDocument local=base();local.atoms.get(0).sampled=true;local.atoms.get(0).y=1;
        local.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("行外局部障碍",10,34,5,3,0));NoteLayout.Glyph localMoved=glyph(build(local,fonts));
        NoteChecks.check("legacy actual ink can move past a partial obstacle on the same row",localMoved.line==0&&localMoved.inkBounds.x>15&&Math.abs(localMoved.inkBounds.y-34.4f)<.001f);
        NoteDocument irrelevant=base();irrelevant.atoms.get(0).sampled=true;irrelevant.atoms.get(0).y=1;
        irrelevant.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("远处障碍",100,34,10,3,0));
        NoteChecks.check("unaffected legacy placement remains unchanged with a distant forbidden zone",translated(glyph(build(irrelevant,fonts)).path,original.path,0,0));

        NoteDocument blocked=base();blocked.atoms.get(0).sampled=true;blocked.atoms.get(0).y=1;
        blocked.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("实际墨迹始终受阻",0,27,160,73,0));
        NoteChecks.check("legacy actual-ink obstruction still rejects an unsatisfiable repeating layout",noFit(blocked,fonts));
        e crossing=new e();crossing.a(0,35);crossing.b(155,35);boolean rejected=false;
        try{NoteForbiddenZoneGeometry.validatePagePaths(legacy,0,DPI,null,Collections.singletonList(crossing));}catch(IllegalStateException expected){rejected=true;}
        NoteChecks.check("final pen-down segment validation still rejects an actual forbidden crossing",rejected);
        longPunctuation(fonts,false);longPunctuation(fonts,true);shortPunctuation(fonts);
    }
    public static void main(String[] args)throws Exception{
        NoteChecks.evidence=Paths.get(System.getProperty("note.evidence","work/forbidden-install-20261114/review-geometry/layout-checks"));
        Files.createDirectories(NoteChecks.evidence);NoteChecks.results.clear();run(null);
        System.out.println("FORBIDDEN_ZONE_LAYOUT_CHECKS_COMPLETE "+NoteChecks.results.size()+" Java "+System.getProperty("java.version"));
    }
}
