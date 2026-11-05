import com.kenjoy.kenjoycnc.note.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Persistent missing-character replacement model regression; no UI, font I/O or devices. */
public final class ReplacementChecks {
    private static int checks;
    private static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);checks++;System.out.println("REPLACEMENT PASS "+name);}
    private static NoteDocument doc(String text){NoteDocument d=new NoteDocument();d.replace(0,0,text);return d;}
    private static Set<Long> ids(long... values){Set<Long> result=new HashSet<Long>();for(long id:values)result.add(id);return result;}
    private static int marked(NoteDocument d){int result=0;for(NoteDocument.Atom a:d.atoms)if(a.replacementMarked)result++;return result;}
    private static void invalidMarks(String encoded,String marks){JsonObject value=new JsonParser().parse(encoded).getAsJsonObject();value.add("replacementMarks",new JsonParser().parse(marks));boolean rejected=false;try{NoteDocument.decode(value.toString());}catch(IllegalArgumentException|IllegalStateException expected){rejected=true;}check("invalid replacement mark references rejected "+marks,rejected);}
    public static void main(String[] args)throws Exception{
        NoteDocument original=doc("A😀\nB😀");original.atoms.get(0).sampled=true;original.atoms.get(0).size=1.3f;original.atoms.get(3).override=new NoteDocument.FontRef("fixture.gfont",String.join("",Collections.nCopies(64,"a")),"fixture");
        String old=original.encode();long next=original.nextId;NoteDocument d=original.copy();NoteDocument.Atom kept=d.atoms.get(0),lastKept=d.atoms.get(3);d.replaceMissing(ids(d.atoms.get(1).id,d.atoms.get(4).id),".");
        check("all selected occurrences replaced across line boundaries",d.text.equals("A.\nB.")&&marked(d)==2);
        check("unaffected atoms retain identity object font and sample",d.atoms.get(0)==kept&&d.atoms.get(3)==lastKept&&kept.sampled&&kept.size==1.3f&&lastKept.override!=null);
        check("replacement atoms allocate fresh IDs without inherited font samples",d.atoms.get(1).id==next&&d.atoms.get(4).id==next+1&&d.nextId==next+2&&d.atoms.get(1).automatic==null&&d.atoms.get(1).override==null&&!d.atoms.get(1).sampled&&d.atoms.get(1).inputFonts==null);
        check("deep copy retains replacement markers",marked(d.copy())==2);
        String saved=d.encode();NoteDocument reopened=NoteDocument.decode(saved);check("compact save reopen retains marks and exact document",marked(reopened)==2&&saved.equals(reopened.encode()));
        JsonObject compact=new JsonParser().parse(saved).getAsJsonObject();boolean thirteen=true;for(JsonElement row:compact.getAsJsonArray("atoms"))thirteen&=row.getAsJsonArray().size()==13;
        check("marks are optional top-level IDs without changing thirteen atom columns",compact.get("formatVersion").getAsInt()==2&&compact.getAsJsonArray("replacementMarks").size()==2&&thirteen);
        check("old compact document without marker field stays compatible",!old.contains("replacementMarks")&&marked(NoteDocument.decode(old))==0);
        check("legacy full JSON remains readable",marked(NoteDocument.decode(new Gson().toJson(d)))==2);
        invalidMarks(saved,"[999999]");invalidMarks(saved,"[-1]");invalidMarks(saved,"["+next+","+next+"]");invalidMarks(saved,"[1.5]");
        NoteDocument spaces=doc("XY");spaces.replaceMissing(ids(1,2)," ");check("space replacement is allowed and remains marked",spaces.text.equals("  ")&&marked(spaces)==2);
        NoteDocument untouched=doc("ABC");String unchanged=untouched.encode();untouched.replaceMissing(ids(999),".");check("unknown target IDs do not change document semantics",unchanged.equals(untouched.encode()));
        for(String text:new String[]{"","..","\n","\t","\u200D","\uFEFF","\u0301","\uD800","\u2028"}){boolean rejected=false;try{NoteDocument.validateReplacement(text);}catch(IllegalArgumentException expected){rejected=true;}check("invalid replacement rejected "+Arrays.toString(text.toCharArray()),rejected);}
        NoteDocument.validateReplacement("😀");check("single supplementary code point passes model validation",true);
        NoteDocument exhausted=doc("AB");exhausted.nextId=Long.MAX_VALUE-1;String exhaustedBefore=exhausted.encode();boolean failed=false;try{exhausted.replaceMissing(ids(1,2),".");}catch(IllegalArgumentException expected){failed=true;}check("ID exhaustion after first candidate replacement is atomic",failed&&exhaustedBefore.equals(exhausted.encode()));
        NoteDocument full=doc(String.join("",Collections.nCopies(NoteDocument.MAX_TEXT_CHARS,"A")));String fullBefore=full.encode();failed=false;try{full.replaceMissing(ids(1),"😀");}catch(IllegalArgumentException expected){failed=true;}check("UTF16 length overflow leaves document unchanged",failed&&fullBefore.equals(full.encode()));
        NoteEditSession session=new NoteEditSession(original);NoteEditSession.Transaction transaction=session.capture();NoteDocument candidate=transaction.document();candidate.replaceMissing(ids(candidate.atoms.get(1).id,candidate.atoms.get(4).id),".");check("replacement is one semantic transaction",session.commit(transaction,candidate,NoteEditSession.Kind.OTHER)&&session.undoSize()==1&&marked(session.mutableDocument())==2);
        String changed=session.snapshot().encode();check("undo restores original characters and removes their markers",session.undo()&&session.mutableDocument().text.equals(original.text)&&marked(session.mutableDocument())==0);
        check("redo restores replacement IDs fonts and markers",session.redo()&&changed.equals(session.mutableDocument().encode()));
        Path directory=Paths.get(args[0]);Files.createDirectories(directory);Path file=directory.resolve("marked.kjnote");reopened.save(file);check("actual file save load keeps replacement markers",saved.equals(NoteDocument.load(file).encode()));
        System.out.println("REPLACEMENT_CHECKS_COMPLETE "+checks+" Java "+System.getProperty("java.version"));
    }
}
