import com.kenjoy.kenjoycnc.note.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Storage checks use only a caller-owned isolated directory, never the user recovery file. */
public final class NoteRecoveryChecks {
    private static int count;
    private static void check(String name,boolean value){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("isolated output directory required");
        Path root=Paths.get(args[0]).toAbsolutePath();Files.createDirectories(root);
        Path directory=Files.createTempDirectory(root,"recovery-checks-");
        check("new editor defaults to recovery",NoteRecovery.closePolicy(directory)==NoteRecovery.ClosePolicy.AUTO_RECOVER);
        check("missing recovery returns no document",NoteRecovery.load(directory)==null);
        Path article=directory.resolve("article.kjnote");Files.write(article,"original article".getBytes(StandardCharsets.UTF_8));byte[] original=Files.readAllBytes(article);
        NoteRecovery.Snapshot state=new NoteRecovery.Snapshot();state.document=new NoteDocument();state.document.replace(0,0,"甲\n😀乙");
        NoteDocument.Atom atom=state.document.atoms.get(0);atom.sampled=true;atom.size=.97f;atom.rotate=3;atom.automatic=new NoteDocument.FontRef("中文/示例.gfont",String.join("",java.util.Collections.nCopies(64,"a")),"示例");atom.override=atom.automatic;
        state.file=article.toString();state.savedState="saved \"state\"\n";state.dirty=true;state.editing=true;state.revision=12;state.anchor=1;state.dot=4;state.page=2;state.scrollX=14;state.scrollY=810;state.processingAngle=90;
        NoteRecovery.save(directory,state);NoteRecovery.Snapshot loaded=NoteRecovery.load(directory);
        check("confirmed text and sampled glyph metadata round trip",loaded.document.encode().equals(state.document.encode()));
        check("association and dirty state round trip",loaded.file.equals(state.file)&&loaded.dirty&&loaded.documentId.equals(state.documentId)&&loaded.savedState.equals(state.savedState));
        check("caret direction view and processing state round trip",loaded.anchor==1&&loaded.dot==4&&loaded.page==2&&loaded.scrollX==14&&loaded.scrollY==810&&loaded.processingAngle==90&&loaded.editing&&loaded.revision==12);
        check("recovery never overwrites associated article",Arrays.equals(original,Files.readAllBytes(article)));
        com.google.gson.JsonObject compact=new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(directory.resolve("recovery.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        check("recovery embeds compact document format 2",compact.getAsJsonObject("document").get("formatVersion").getAsInt()==2);
        byte[] legacy=new com.google.gson.Gson().toJson(state).getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("recovery.json"),legacy);NoteRecovery.Snapshot legacyLoaded=NoteRecovery.load(directory);
        check("legacy wrapper and document format 1 remain readable",legacyLoaded.document.encode().equals(state.document.encode())&&legacyLoaded.file.equals(state.file)&&legacyLoaded.savedState.equals(state.savedState));
        NoteRecovery.save(directory,legacyLoaded);
        byte[] brokenCompact="{\"formatVersion\":1,\"documentId\":\"broken-v2\",\"document\":{\"formatVersion\":2,\"atoms\":\"invalid\"}}".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("recovery.json"),brokenCompact);boolean badCompact=false;try{NoteRecovery.load(directory);}catch(IOException expected){badCompact=true;}
        check("invalid compact document reports failure without replacing recovery",badCompact&&Arrays.equals(brokenCompact,Files.readAllBytes(directory.resolve("recovery.json"))));
        NoteRecovery.save(directory,state);
        state.document.replace(0,1,"丙");check("loaded recovery owns independent document",loaded.document.text.startsWith("甲"));
        byte[] before=Files.readAllBytes(directory.resolve("recovery.json"));state.dot=999;boolean rejected=false;try{NoteRecovery.save(directory,state);}catch(IOException expected){rejected=true;}
        check("invalid state preserves prior recovery",rejected&&Arrays.equals(before,Files.readAllBytes(directory.resolve("recovery.json"))));state.dot=4;
        NoteRecovery.setClosePolicy(directory,NoteRecovery.ClosePolicy.ASK_SAVE);check("ask before close preference persists",NoteRecovery.closePolicy(directory)==NoteRecovery.ClosePolicy.ASK_SAVE);
        NoteRecovery.setClosePolicy(directory,NoteRecovery.ClosePolicy.AUTO_RECOVER);check("auto recovery preference persists",NoteRecovery.closePolicy(directory)==NoteRecovery.ClosePolicy.AUTO_RECOVER);
        byte[] corrupt="{broken".getBytes(StandardCharsets.UTF_8);Files.write(directory.resolve("recovery.json"),corrupt);rejected=false;try{NoteRecovery.load(directory);}catch(IOException expected){rejected=true;}
        check("corrupt recovery is reported and retained",rejected&&Arrays.equals(corrupt,Files.readAllBytes(directory.resolve("recovery.json"))));
        NoteRecovery.save(directory,state);NoteRecovery.clear(directory);
        check("clear removes only recovery",NoteRecovery.load(directory)==null&&NoteRecovery.closePolicy(directory)==NoteRecovery.ClosePolicy.AUTO_RECOVER&&Arrays.equals(original,Files.readAllBytes(article)));
        try(java.util.stream.Stream<Path> files=Files.list(directory)){check("atomic writes leave no temporary files",files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
        Path blocked=directory.resolve("blocked");Files.createDirectories(blocked.resolve("recovery.json"));Files.write(blocked.resolve("recovery.json/keep"),new byte[]{1});rejected=false;try{NoteRecovery.save(blocked,state);}catch(IOException expected){rejected=true;}
        check("failed replacement preserves existing target",rejected&&Files.exists(blocked.resolve("recovery.json/keep")));
        try(java.util.stream.Stream<Path> files=Files.list(blocked)){check("failed replacement removes its temporary file",files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
        NoteRecovery.Snapshot large=new NoteRecovery.Snapshot();large.document=new NoteDocument();char[] characters=new char[NoteDocument.MAX_TEXT_CHARS];Arrays.fill(characters,'春');large.document.replace(0,0,new String(characters));
        String[] names={"手写一.gfont","手写二.gfont","手写三.gfont"};int[] weights={1,3,7};NoteDocument.FontRef[] refs=new NoteDocument.FontRef[3];
        for(int i=0;i<refs.length;i++){refs[i]=new NoteDocument.FontRef("中文/"+names[i],String.join("",java.util.Collections.nCopies(64,String.valueOf((char)('a'+i)))),names[i]);large.document.pool.add(refs[i]);}
        large.document.page.fontPath=names;large.document.page.fontChance=weights;large.document.chances=weights;
        for(int i=0;i<large.document.atoms.size();i++){NoteDocument.Atom a=large.document.atoms.get(i);a.automatic=refs[i%3];if(i%19==0)a.override=refs[(i+1)%3];a.inputFonts=names;a.inputChances=weights;a.inputGeneration=0L;a.sampled=true;a.size=.97f;a.scaleX=1.01f;a.scaleY=.99f;a.rotate=3;a.y=.02f;a.space=1;}
        large.savedState=large.document.encode();large.file=article.toString();large.dirty=true;large.editing=true;large.anchor=large.dot=characters.length;large.revision=100000;large.page=99;large.scrollY=50000;
        check("100k sampled article encodes within document limit",large.savedState.getBytes(StandardCharsets.UTF_8).length<=NoteDocument.MAX_DOCUMENT_BYTES);
        NoteRecovery.save(directory,large);NoteRecovery.Snapshot largeLoaded=NoteRecovery.load(directory);
        check("100k article recovery round trip retains fonts policies and samples",largeLoaded.document.encode().equals(large.savedState)&&largeLoaded.document.atoms.size()==characters.length);
        check("100k recovery retains saved baseline and caret",largeLoaded.savedState.equals(large.savedState)&&largeLoaded.dot==characters.length&&largeLoaded.anchor==characters.length&&largeLoaded.page==99&&largeLoaded.scrollY==50000&&largeLoaded.dirty);
        check("large recovery never writes associated article",Arrays.equals(original,Files.readAllBytes(article)));
        System.out.println("NOTE_RECOVERY_100K_BYTES "+Files.size(directory.resolve("recovery.json")));

        NoteWriteProgress.clear(directory);
        check("missing write progress returns null",NoteWriteProgress.load(directory)==null);
        NoteWriteProgress.Snapshot wp=new NoteWriteProgress.Snapshot();
        wp.documentId=state.documentId;wp.textHash=NoteWriteProgress.textHash("甲乙丙丁");
        wp.pageIndex=0;wp.pageCount=1;wp.processingAngle=0f;wp.status="INTERRUPTED";wp.reason="USB 连接已断开";
        long[] ids={10L,11L,12L,13L};String[] texts={"甲","乙","丙","丁"};
        for(int i=0;i<4;i++){
            NoteWriteProgress.GlyphEntry entry=new NoteWriteProgress.GlyphEntry();
            entry.id=ids[i];entry.text=texts[i];entry.line=i/2;entry.column=(i%2)+1;entry.pageOrder=i+1;
            entry.startCmd=5+i*6;entry.endCmd=10+i*6;wp.glyphs.add(entry);
        }
        wp.updateCommandCounts(30,16,19);wp.resumeGlyphId=wp.recommendedResumeGlyphId();
        check("confirmed and in-flight glyph ranges align with sender counts",
            wp.isConfirmed(10L)&&wp.isConfirmed(11L)&&!wp.isConfirmed(12L)&&!wp.isConfirmed(13L)
            &&!wp.isInFlight(10L)&&!wp.isInFlight(11L)&&wp.isInFlight(12L)&&!wp.isInFlight(13L)
            &&wp.recommendedResumeGlyphId()==12L&&wp.nextResumeGlyphId()==13L);
        wp.updateCommandCounts(0,0,0);
        check("monotonic progress guard ignores post-clear zero counts",wp.totalCommands==30&&wp.ackedCommands==16&&wp.sentCommands==19);
        NoteWriteProgress.save(directory,wp);
        NoteWriteProgress.Snapshot wpLoaded=NoteWriteProgress.load(directory);
        check("write progress round trip preserves glyphs and command counts",
            wpLoaded!=null&&wpLoaded.documentId.equals(wp.documentId)&&wpLoaded.ackedCommands==16&&wpLoaded.sentCommands==19
            &&wpLoaded.resumeGlyphId==12L&&wpLoaded.glyphs.size()==4&&wpLoaded.isConfirmed(11L)&&wpLoaded.isInFlight(12L));
        try(java.util.stream.Stream<Path> files=Files.list(directory)){check("write progress atomic write leaves no tmp files",files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
        byte[] corruptWp="{corrupt-wp".getBytes(StandardCharsets.UTF_8);Files.write(directory.resolve("write-progress.json"),corruptWp);
        boolean badWp=false;try{NoteWriteProgress.load(directory);}catch(IOException expected){badWp=true;}
        check("corrupt write progress is rejected and preserved",badWp&&Arrays.equals(corruptWp,Files.readAllBytes(directory.resolve("write-progress.json"))));
        NoteWriteProgress.clear(directory);
        check("clear write progress removes only progress file",NoteWriteProgress.load(directory)==null);
        System.out.println("NOTE_RECOVERY_CHECKS "+count);
    }
}
