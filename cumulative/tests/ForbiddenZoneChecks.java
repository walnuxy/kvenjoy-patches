import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.note.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** Focused model, layout and pen-down safety checks for forbidden zones. */
public final class ForbiddenZoneChecks {
    private ForbiddenZoneChecks(){}

    public static void main(String[] args)throws Exception{
        NoteChecks.evidence=Paths.get(System.getProperty("note.evidence","note-edit/runtime/forbidden-zone-evidence"));
        Files.createDirectories(NoteChecks.evidence);NoteChecks.results.clear();
        String fontsPath=System.getProperty("note.fonts");
        NoteFonts fonts=Boolean.getBoolean("note.fonts.fixture")?emptyFontFixture(NoteChecks.evidence.resolve("empty-fonts").toFile()):fontsPath==null||fontsPath.trim().isEmpty()?null:new NoteFonts(new File(fontsPath));
        if(fonts==null)throw new IllegalStateException("缺少 note.fonts 测试字库路径；离线检查可使用 -Dnote.fonts.fixture=true");
        try{
            run(fonts);
            Files.write(NoteChecks.evidence.resolve("COMPLETE.txt"),("PASS "+NoteChecks.results.size()+" forbidden-zone assertions; Java "+System.getProperty("java.version")+". No application UI or hardware.\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("FORBIDDEN_ZONE_CHECKS_COMPLETE "+NoteChecks.results.size());
        }finally{fonts.close();}
    }

    private static int version(String json){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\\"formatVersion\\\"\\s*:\\s*(\\d+)").matcher(json);return m.find()?Integer.parseInt(m.group(1)):-1;}
    private static boolean rejected(Runnable action){try{action.run();return false;}catch(RuntimeException expected){return true;}}
    private static NoteFonts emptyFontFixture(File directory)throws Exception{
        Files.createDirectories(directory.toPath());Class<?> catalogueType=Class.forName("com.kenjoy.kenjoycnc.fonts.FontCatalogue");
        Object catalogue=catalogueType.getMethod("at",File.class).invoke(null,directory);Class<?> snapshotType=Class.forName("com.kenjoy.kenjoycnc.fonts.FontCatalogue$Snapshot");
        Constructor<?> snapshotConstructor=snapshotType.getDeclaredConstructor(List.class,List.class,long.class,long.class,int.class);snapshotConstructor.setAccessible(true);
        Object snapshot=snapshotConstructor.newInstance(Collections.emptyList(),Collections.emptyList(),0L,0L,0);
        Field current=catalogueType.getDeclaredField("current");current.setAccessible(true);current.set(catalogue,snapshot);
        Constructor<NoteFonts> fontsConstructor=NoteFonts.class.getDeclaredConstructor(File.class,NoteFonts.class,boolean.class);fontsConstructor.setAccessible(true);
        return fontsConstructor.newInstance(directory,null,false);
    }
    private static byte[] jsonBytes(Object value){return new Gson().toJson(value).getBytes(StandardCharsets.UTF_8);}
    private static e transformPath(e source,AffineTransform transform){
        float[] sourceXY=source.a();byte[] flags=source.b();double[] xy=new double[sourceXY.length];for(int i=0;i<sourceXY.length;i++)xy[i]=sourceXY[i];
        transform.transform(xy,0,xy,0,flags.length);e result=new e();for(int i=0;i<flags.length;i++){float x=(float)xy[i*2],y=(float)xy[i*2+1];if(flags[i]==0)result.a(x,y);else result.b(x,y);}return result;
    }
    private static NoteDocument base(){
        NoteDocument d=new NoteDocument();d.page=new com.kenjoy.kenjoycnc.data.PageInfo("禁写区检查",160,100,10,10,10,10,5,75,true);d.page.space=.8f;d.format.autoGp=false;return d;
    }

    private static List<Path> upgradeBackups(Path folder)throws Exception{
        try(java.util.stream.Stream<Path> paths=Files.list(folder)){
            return paths.filter(p->p.getFileName().toString().contains("-before-effects-")).collect(java.util.stream.Collectors.toList());
        }
    }
    private static void checkUpgradeBackups()throws Exception{
        Path folder=Files.createTempDirectory(NoteChecks.evidence,"storage-upgrade-");
        NoteDocument article=base();Path file=folder.resolve("article.kjnote");article.save(file);byte[] v2=Files.readAllBytes(file);
        article.appearance.minGapMm=.65f;article.save(file);List<Path> backups=upgradeBackups(folder);
        NoteChecks.check("V2 to V3 upgrade retains one byte-exact V2 backup",backups.size()==1&&Arrays.equals(v2,Files.readAllBytes(backups.get(0)))&&version(new String(Files.readAllBytes(file),StandardCharsets.UTF_8))==3);
        byte[] v3=Files.readAllBytes(file);article.save(file);
        NoteChecks.check("repeated V3 save does not duplicate upgrade backups",upgradeBackups(folder).size()==1);
        article.forbiddenZones.addForPage(1,10,10,20,20);article.save(file);backups=upgradeBackups(folder);boolean exactV3=false;
        for(Path backup:backups)if(Arrays.equals(v3,Files.readAllBytes(backup)))exactV3=true;
        NoteChecks.check("V3 to V4 upgrade retains one additional byte-exact V3 backup",backups.size()==2&&exactV3&&version(new String(Files.readAllBytes(file),StandardCharsets.UTF_8))==4);
        article.save(file);
        NoteChecks.check("repeated V4 save does not duplicate upgrade backups",upgradeBackups(folder).size()==2);
    }

    private static void checkTemplateMirrorTypes()throws Exception{
        NoteForbiddenZones zones=new NoteForbiddenZones();zones.rules.add(new NoteForbiddenZones.Rule("镜像类型检查",10,10,30,20,.5f));
        for(boolean mirror:new boolean[]{true,false}){
            zones.rules.get(0).mirrorEven=mirror;
            NoteForbiddenZones.Template restored=NoteForbiddenZoneTemplateIO.decode(NoteForbiddenZoneTemplateIO.encode(new NoteForbiddenZones.Template(160,100,zones)));
            NoteChecks.check("template boolean mirrorEven="+mirror+" retains even-page geometry",restored.zones.rules.get(0).mirrorEven==mirror&&restored.zones.resolve(2,160,100).get(0).x==(mirror?120f:10f));
        }
        JsonObject json=new JsonParser().parse(new Gson().toJson(new NoteForbiddenZones.Template(160,100,zones))).getAsJsonObject();
        JsonObject rule=json.getAsJsonObject("zones").getAsJsonArray("rules").get(0).getAsJsonObject();
        for(String value:new String[]{"yes","tru","true","false"}){
            rule.addProperty("mirrorEven",value);
            NoteChecks.check("template rejects string mirrorEven="+value,ioRejected(()->NoteForbiddenZoneTemplateIO.decode(jsonBytes(json))));
        }
    }

    public static void run(NoteFonts fonts)throws Exception{
        checkUpgradeBackups();
        checkTemplateMirrorTypes();
        NoteDocument empty=base();String v2=empty.encode();NoteChecks.check("new document retains compact V2 storage before new features are used",version(v2)==2&&NoteDocument.decode(v2).forbiddenZones.isEmpty());

        NoteDocument v3doc=base();v3doc.appearance.minGapMm=.65f;String v3=v3doc.encode();NoteChecks.check("V3 appearance documents remain readable after V4 support",version(v3)==3&&NoteAppearance.key(NoteDocument.decode(v3).appearance).equals(NoteAppearance.key(v3doc.appearance)));

        NoteForbiddenZones.Rule all=new NoteForbiddenZones.Rule("左侧禁写",10,10,30,25,.5f);all.pageMode=NoteForbiddenZones.PageMode.ALL;all.mirrorEven=true;
        NoteForbiddenZones zones=new NoteForbiddenZones();zones.rules.add(all);
        NoteChecks.check("even-page mirror uses the physical page width",zones.resolve(1,160,100).get(0).x==10f&&zones.resolve(2,160,100).get(0).x==120f);
        NoteChecks.check("page exception disables inherited rules",zones.resolve(2,160,100).size()==1);zones.setException(2,Collections.singleton(all.id));
        NoteChecks.check("disabled inherited rule is absent on its exception page",zones.resolve(2,160,100).isEmpty());
        zones.addExceptionZone(2,new NoteForbiddenZones.Rect("仅第二页",70,20,12,10,.25f));
        NoteChecks.check("single-page extra zone coexists with disabled inherited rules",zones.resolve(2,160,100).size()==1&&zones.resolve(2,160,100).get(0).name.equals("仅第二页"));

        NoteDocument stored=base();stored.forbiddenZones=zones;String v4=stored.encode();NoteDocument reopened=NoteDocument.decode(v4);
        NoteChecks.check("V4 roundtrip retains rules, mirror, exceptions and extra zones",version(v4)==4&&reopened.encode().equals(v4));
        NoteChecks.check("document deep copy retains forbidden-zone state",stored.copy().encode().equals(v4));
        NoteForbiddenZones.Template template=new NoteForbiddenZones.Template(160,100,zones);NoteForbiddenZones scaled=template.convert(320,100,true),scaledY=template.convert(80,200,true);
        NoteChecks.check("template scaling preserves geometry and uses the larger axis for clearance",scaled.rules.get(0).x==20f&&scaled.rules.get(0).width==60f&&scaled.rules.get(0).clearanceMm==1f&&scaled.exception(2).additionalZones.get(0).x==140f&&scaledY.rules.get(0).clearanceMm==1f);
        NoteChecks.check("template validation rejects invalid source dimensions",rejected(()->{NoteForbiddenZones.Template bad=new NoteForbiddenZones.Template(160,100,zones);bad.sourcePageWidthMm=0;bad.validate();}));
        byte[] templateJson=NoteForbiddenZoneTemplateIO.encode(template);NoteForbiddenZones.Template templateCopy=NoteForbiddenZoneTemplateIO.decode(templateJson);
        NoteChecks.check("template JSON roundtrip retains rules and page exceptions",NoteForbiddenZoneTemplateIO.encode(templateCopy).length==templateJson.length&&templateCopy.zones.key().equals(template.zones.key()));
        Path templateFile=NoteChecks.evidence.resolve("forbidden-zones-template.json");NoteForbiddenZoneTemplateIO.save(templateFile,template);NoteChecks.check("template file save and load use validated atomic JSON",NoteForbiddenZoneTemplateIO.load(templateFile).zones.key().equals(template.zones.key()));
        NoteChecks.check("template IO rejects oversized and malformed UTF-8 files",ioRejected(()->NoteForbiddenZoneTemplateIO.decode(new byte[NoteForbiddenZoneTemplateIO.MAX_BYTES+1]))&&ioRejected(()->NoteForbiddenZoneTemplateIO.decode(new byte[]{(byte)0xc3,(byte)0x28})));
        NoteChecks.check("template IO rejects unsupported schema versions",ioRejected(()->{NoteForbiddenZones.Template bad=new NoteForbiddenZones.Template(160,100,zones);bad.schemaVersion=2;NoteForbiddenZoneTemplateIO.encode(bad);}));
        NoteForbiddenZones emptyCustom=new NoteForbiddenZones();NoteForbiddenZones.Rule emptyCustomRule=new NoteForbiddenZones.Rule("空自定义",10,10,10,10,0);emptyCustomRule.pageMode=NoteForbiddenZones.PageMode.CUSTOM;emptyCustom.rules.add(emptyCustomRule);
        NoteForbiddenZones nonCustomPages=new NoteForbiddenZones();NoteForbiddenZones.Rule invalidAll=new NoteForbiddenZones.Rule("多余页码",10,10,10,10,0);invalidAll.customPages.add(2);nonCustomPages.rules.add(invalidAll);
        NoteChecks.check("rule validation rejects empty CUSTOM pages and page lists on ALL/ODD/EVEN",rejected(()->emptyCustom.validate(160,100))&&rejected(()->nonCustomPages.validate(160,100)));
        String compactTemplate=new Gson().toJson(template);JsonObject missingZones=new JsonParser().parse(compactTemplate).getAsJsonObject();missingZones.remove("zones");
        NoteForbiddenZones duplicateIds=zones.copy();duplicateIds.rules.add(duplicateIds.rules.get(0).copy());
        NoteForbiddenZones emptyCustomJson=new NoteForbiddenZones();emptyCustomJson.rules.add(emptyCustomRule.copy());
        NoteForbiddenZones nonCustomJson=new NoteForbiddenZones();nonCustomJson.rules.add(invalidAll.copy());
        NoteChecks.check("template JSON rejects missing fields, duplicate IDs, invalid page modes and trailing data",ioRejected(()->NoteForbiddenZoneTemplateIO.decode(missingZones.toString().getBytes(StandardCharsets.UTF_8)))&&ioRejected(()->NoteForbiddenZoneTemplateIO.decode(jsonBytes(new NoteForbiddenZones.Template(160,100,duplicateIds))))&&ioRejected(()->NoteForbiddenZoneTemplateIO.decode(jsonBytes(new NoteForbiddenZones.Template(160,100,emptyCustomJson))))&&ioRejected(()->NoteForbiddenZoneTemplateIO.decode(jsonBytes(new NoteForbiddenZones.Template(160,100,nonCustomJson))))&&ioRejected(()->NoteForbiddenZoneTemplateIO.decode(compactTemplate.replace("\"pageMode\":\"ALL\"","\"pageMode\":\"UNKNOWN\"").getBytes(StandardCharsets.UTF_8)))&&ioRejected(()->NoteForbiddenZoneTemplateIO.decode((compactTemplate+" {}").getBytes(StandardCharsets.UTF_8))));

        NoteEditSession session=new NoteEditSession(base());NoteEditSession.Transaction tx=session.capture();NoteDocument candidate=tx.document();candidate.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("撤销检查",15,15,20,15,.5f));
        boolean committed=session.commit(tx,candidate,NoteEditSession.Kind.OTHER);boolean undo=committed&&session.undo()&&session.snapshot().forbiddenZones.isEmpty();boolean redo=undo&&session.redo()&&session.snapshot().forbiddenZones.rules.size()==1;
        NoteChecks.check("forbidden-zone changes participate in undo and redo",redo);

        NoteDocument geometry=base();geometry.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("中部障碍",60,10,40,70,.5f));
        List<NoteForbiddenZoneGeometry.Interval> intervals=NoteForbiddenZoneGeometry.availableIntervals(geometry,1,20,8,10,150,25.4f);
        NoteChecks.check("row intervals leave a strict gap outside the final validation boundary",intervals.size()==2&&Math.abs(intervals.get(0).right-59.48f)<.005f&&Math.abs(intervals.get(1).left-100.52f)<.005f&&intervals.get(0).right<59.49f&&intervals.get(1).left>100.51f);

        NoteDocument text=NoteChecks.sample(fonts);text.page=new com.kenjoy.kenjoycnc.data.PageInfo("禁写区绕排",160,100,10,10,10,10,5,75,true);text.page.space=.8f;text.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("中部障碍",60,10,40,70,.5f));
        NoteLayout layout=new NoteLayoutEngine(fonts,25.4f).build(text);
        boolean bothSides=false;for(NoteLayout.Page p:layout.pages)for(NoteLayout.Line line:p.lines){boolean leftSide=false,rightSide=false;for(NoteLayout.Glyph glyph:p.glyphs)if(glyph.line==line.number&&!glyph.whitespace){if(glyph.cell.getMaxX()<=59.49f)leftSide=true;if(glyph.cell.x>=100.51f)rightSide=true;}if(leftSide&&rightSide)bothSides=true;}
        NoteForbiddenZoneGeometry.validateLayout(text,layout,25.4f);
        NoteChecks.check("layout moves complete glyphs into a second interval on the same row",bothSides);
        NoteChecks.check("layout paths pass final pen-down geometry validation",true);

        NoteDocument narrow=base();narrow.forbiddenZones.rules.add(new NoteForbiddenZones.Rule("首行窄缝",10,10,134,15,0));narrow.replace(0,0,"甲");
        NoteLayout narrowLayout=new NoteLayoutEngine(fonts,25.4f).build(narrow);NoteLayout.Glyph firstGlyph=narrowLayout.pages.get(0).glyphs.get(0);
        NoteChecks.check("a glyph skips a narrow first-row gap and moves intact to a wider later row",firstGlyph.line==1&&firstGlyph.cell.y>=26f);
        NoteDocument noFit=base();NoteForbiddenZones.Rule narrowEverywhere=new NoteForbiddenZones.Rule("持续窄缝",10,10,134,80,0);narrowEverywhere.pageMode=NoteForbiddenZones.PageMode.ALL;noFit.forbiddenZones.rules.add(narrowEverywhere);noFit.replace(0,0,"甲");
        boolean noFutureFit=false;try{new NoteLayoutEngine(fonts,25.4f).build(noFit);}catch(java.io.IOException expected){noFutureFit=expected.getMessage().contains("后续可写行");}
        NoteChecks.check("a glyph that cannot fit any future row fails after the repeating page cycle",noFutureFit);

        NoteDocument full=base();NoteForbiddenZones.Rule cover=new NoteForbiddenZones.Rule("整页覆盖",0,0,160,100,0);cover.pageMode=NoteForbiddenZones.PageMode.ALL;full.forbiddenZones.rules.add(cover);full.replace(0,0,"测试");
        boolean blocked=false;try{new NoteLayoutEngine(fonts,25.4f).build(full);}catch(java.io.IOException expected){blocked=expected.getMessage().contains("全部可写行");}
        NoteChecks.check("a repeating rule that blocks every row fails without endless pagination",blocked);

        NoteDocument odd=base();NoteForbiddenZones.Rule oddCover=new NoteForbiddenZones.Rule("奇数页整页",0,0,160,100,0);oddCover.pageMode=NoteForbiddenZones.PageMode.ODD;odd.forbiddenZones.rules.add(oddCover);odd.replace(0,0,"测试");
        NoteLayout oddLayout=new NoteLayoutEngine(fonts,25.4f).build(odd);
        NoteChecks.check("fully blocked odd page can continue on the next available even page",oddLayout.pages.size()>=2&&oddLayout.pages.get(0).glyphs.isEmpty()&&oddLayout.pages.get(1).glyphs.stream().anyMatch(glyph->!glyph.whitespace));

        NoteDocument pathDoc=base();NoteForbiddenZones.Rule pathZone=new NoteForbiddenZones.Rule("线段障碍",20,20,20,20,0);pathDoc.forbiddenZones.rules.add(pathZone);
        e lifts=new e();lifts.a(0,0);lifts.a(30,30); // Two moveTo commands are both pen-up.
        NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(lifts));
        e crossing=new e();crossing.a(0,30);crossing.b(50,30);
        boolean crossed=rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(crossing)));
        NoteChecks.check("final path check ignores moveTo travel but rejects a crossing lineTo segment",crossed);
        e edge=new e();edge.a(0,20);edge.b(50,20);
        boolean touched=rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(edge)));
        NoteChecks.check("final path check rejects a line tangent to the expanded boundary",touched);
        e transformed=new e();transformed.a(0,30);transformed.b(50,30);
        AffineTransform translation=AffineTransform.getTranslateInstance(100,0);e translatedPath=transformPath(transformed,translation);
        boolean machineCrossed=rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,translation,Collections.singletonList(translatedPath)));
        NoteChecks.check("machine-coordinate validator compares transformed paths with the transformed forbidden polygon",machineCrossed);
        AffineTransform rotation=AffineTransform.getRotateInstance(Math.PI/2);e rotatedPath=transformPath(transformed,rotation);
        NoteChecks.check("machine-coordinate validator handles 90-degree rotation",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,rotation,Collections.singletonList(rotatedPath))));
        AffineTransform shearScale=new AffineTransform(1.5,.2,.4,1.2,30,10);e shearedPath=transformPath(transformed,shearScale);
        NoteChecks.check("machine-coordinate validator handles scale and shear",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,shearScale,Collections.singletonList(shearedPath))));
        NoteChecks.check("machine-coordinate validator rejects singular transforms",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,new AffineTransform(0,0,0,0,0,0),Collections.singletonList(transformed))));
        NoteChecks.check("machine-coordinate validator rejects non-finite transforms",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,new AffineTransform(Double.NaN,0,0,1,0,0),Collections.singletonList(transformed))));
        e emptyPath=new e();NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(emptyPath));
        e noMove=new e();Field coordinates=e.class.getDeclaredField("c");coordinates.setAccessible(true);coordinates.set(noMove,new float[]{30,30});
        Field commands=e.class.getDeclaredField("d");commands.setAccessible(true);commands.set(noMove,new byte[]{1});Field coordinateLength=e.class.getDeclaredField("a");coordinateLength.setAccessible(true);coordinateLength.setInt(noMove,2);Field commandCount=e.class.getDeclaredField("b");commandCount.setAccessible(true);commandCount.setInt(noMove,1);
        NoteChecks.check("empty paths are harmless while lineTo without moveTo fails closed",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(noMove))));
        e nullCoordinates=new e();coordinates.set(nullCoordinates,null);
        NoteChecks.check("unreadable path coordinate arrays fail closed",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(nullCoordinates))));
        e nullFlags=new e();commands.set(nullFlags,null);
        e unknownCommand=new e();coordinates.set(unknownCommand,new float[]{30,30});commands.set(unknownCommand,new byte[]{2});coordinateLength.setInt(unknownCommand,2);commandCount.setInt(unknownCommand,1);
        e nonFinitePath=new e();nonFinitePath.a(Float.NaN,30);nonFinitePath.b(50,30);
        NoteChecks.check("null command arrays, unknown commands and non-finite path coordinates fail closed",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(nullFlags)))&&rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(unknownCommand)))&&rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(pathDoc,0,25.4f,null,Collections.singletonList(nonFinitePath))));
        NoteDocument malformedOutput=pathDoc.copy();malformedOutput.forbiddenZones.rules.get(0).customPages.add(1);
        NoteChecks.check("final output validation rejects malformed rule state even before resolving zones",rejected(()->NoteForbiddenZoneGeometry.validatePagePaths(malformedOutput,0,25.4f,null,Collections.<e>emptyList())));

        Path roundtrip=NoteChecks.evidence.resolve("forbidden-zones-v4.kjnote");stored.save(roundtrip);
        NoteChecks.check("V4 file save and reload preserve the full rule model",NoteDocument.load(roundtrip).encode().equals(stored.encode()));
    }
    private interface IOAction {void run()throws Exception;}
    private static boolean ioRejected(IOAction action){try{action.run();return false;}catch(Exception expected){return true;}}
}
