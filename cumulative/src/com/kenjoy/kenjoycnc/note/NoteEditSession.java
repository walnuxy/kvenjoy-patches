package com.kenjoy.kenjoycnc.note;

import java.util.*;

/** Single-thread-owned semantic editing model. No Swing events, font loading or I/O. */
public final class NoteEditSession {
    public enum Kind { TYPE, BACKSPACE, DELETE, IME, PASTE, CUT, NEWLINE, FONT, UPDATE, OTHER }
    private static final int HISTORY_LIMIT=30;
    private static final long MERGE_NANOS=900_000_000L;
    private static final long HISTORY_BYTES=Math.min(64L*1024*1024,Runtime.getRuntime().maxMemory()/8);
    private NoteDocument document;
    private long revision,highWater;
    private long stateIdentity,nextStateIdentity,savedStateIdentity;
    private int anchor,caret;
    private String identity;
    private String[] inputFonts=new String[0];
    private int[] inputChances=new int[0];
    private final Deque<State> undo=new ArrayDeque<State>(),redo=new ArrayDeque<State>();
    private Set<String> availableCuts=new HashSet<String>();
    private Kind lastKind;
    private int lastCaret;
    private long lastEditNanos;

    private static final class State {
        final NoteDocument document;
        final int anchor,caret;
        final Set<String> cuts;
        final long identity;
        final long bytes;
        State(NoteDocument d,int a,int c,Set<String> cut,long identity){bytes=estimate(d);document=d.copy();anchor=a;caret=c;cuts=new HashSet<String>(cut);this.identity=identity;}
    }
    /** An optimistic external operation. Capture before showing a dialog or starting work. */
    public static final class Transaction {
        private final State before;
        private final long revision;
        private final String owner;
        private Transaction(State s,long r,String o){before=s;revision=r;owner=o;}
        public NoteDocument document(){return before.document.copy();}
        public long revision(){return revision;}
    }
    /** Keep this object in a private clipboard flavor; text remains available to other apps. */
    public static final class ClipboardContent {
        public final String text;
        private final String owner,cut;
        private final List<NoteDocument.Atom> atoms;
        public ClipboardContent(String text){this(NoteDocument.normalize(text==null?"":text),null,null,null);}
        private ClipboardContent(String text,String owner,String cut,List<NoteDocument.Atom> atoms){this.text=text;this.owner=owner;this.cut=cut;this.atoms=atoms;}
    }

    public NoteEditSession(){this(new NoteDocument());}
    public NoteEditSession(NoteDocument initial){reset(initial);}
    /** Read on the owning thread. Mutations must use this session's transactions. */
    public NoteDocument mutableDocument(){return document;}
    public NoteDocument snapshot(){return document.copy();}
    public long revision(){return revision;}
    /** Stable through layout completion and restored by undo/redo; useful for frozen job labels. */
    public long contentIdentity(){return stateIdentity;}
    public int anchor(){return anchor;}
    public int caret(){return caret;}
    public int selectionStart(){return Math.min(anchor,caret);}
    public int selectionEnd(){return Math.max(anchor,caret);}
    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}
    public int undoSize(){return undo.size();}
    public int redoSize(){return redo.size();}
    public boolean isDirty(){return stateIdentity!=savedStateIdentity;}
    public void markSaved(){savedStateIdentity=stateIdentity;breakGroup();}
    public void markUnsaved(){savedStateIdentity=-1;breakGroup();}
    public String[] inputFonts(){return inputFonts.clone();}
    public int[] inputChances(){return inputChances.clone();}

    public void reset(NoteDocument initial){
        Objects.requireNonNull(initial,"document");
        document=initial.copy();highWater=document.nextId;anchor=caret=0;
        identity=UUID.randomUUID().toString();availableCuts.clear();undo.clear();redo.clear();revision++;breakGroup();
        stateIdentity=++nextStateIdentity;savedStateIdentity=stateIdentity;
        setInputFonts(document.page.fontPath,document.page.fontChance);
    }
    public void setInputFonts(String[] names,int[] chances){
        String[] next=names==null?new String[0]:names.clone();int[] weights=new int[next.length];
        for(int i=0;i<weights.length;i++)weights[i]=chances!=null&&i<chances.length?Math.max(0,chances[i]):1;
        if(!Arrays.equals(inputFonts,next)||!Arrays.equals(inputChances,weights))breakGroup();
        inputFonts=next;inputChances=weights;
    }
    public void setSelection(int a,int c){
        a=nearestBoundary(a);c=nearestBoundary(c);
        if(anchor!=a||caret!=c)breakGroup();anchor=a;caret=c;
    }
    public void breakGroup(){lastKind=null;lastEditNanos=0;}
    public boolean isBoundary(int offset){
        if(offset<0||offset>document.text.length())return false;int pos=0;
        for(NoteDocument.Atom a:document.atoms){if(pos==offset)return true;pos+=a.text.length();}
        return pos==offset;
    }
    public int previousBoundary(int offset){
        offset=Math.max(0,Math.min(document.text.length(),offset));int pos=0;
        for(NoteDocument.Atom a:document.atoms){int end=pos+a.text.length();if(end>=offset)return pos;pos=end;}
        return pos;
    }
    public int nextBoundary(int offset){
        offset=Math.max(0,Math.min(document.text.length(),offset));int pos=0;
        for(NoteDocument.Atom a:document.atoms){pos+=a.text.length();if(pos>offset)return pos;}
        return document.text.length();
    }
    public int nearestBoundary(int offset){
        offset=Math.max(0,Math.min(document.text.length(),offset));int pos=0;
        for(NoteDocument.Atom a:document.atoms){int end=pos+a.text.length();if(offset<=end)return offset-pos<end-offset?pos:end;pos=end;}
        return document.text.length();
    }
    private void range(int offset,int length){
        if(offset<0||length<0||length>document.text.length()||offset>document.text.length()-length||!isBoundary(offset)||!isBoundary(offset+length))
            throw new IllegalArgumentException("编辑范围必须位于完整字符边界");
    }
    private static void validUnicode(String text){
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))throw new IllegalArgumentException("输入包含不完整字符");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("输入包含不完整字符");}
    }
    public boolean replaceSelection(String text,Kind kind){return replace(selectionStart(),selectionEnd()-selectionStart(),text,kind);}
    public boolean replace(int offset,int length,String text,Kind kind){
        range(offset,length);String inserted=NoteDocument.normalize(text==null?"":text);validUnicode(inserted);
        if(length==0&&inserted.isEmpty())return false;
        if((long)document.text.length()-length+inserted.length()>NoteDocument.MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        if(kind==null)kind=Kind.OTHER;
        if(inserted.indexOf('\n')>=0&&kind==Kind.TYPE)kind=Kind.NEWLINE;
        long now=System.nanoTime();boolean merge=canMerge(kind,offset,length,inserted,now);
        State before=merge?null:historyState();long firstNew=Math.max(document.nextId,highWater);document.nextId=firstNew;
        document.replace(offset,length,inserted);
        for(NoteDocument.Atom a:document.atoms)if(a.id>=firstNew){a.inputFonts=inputFonts.clone();a.inputChances=inputChances.clone();a.inputGeneration=document.generation;}
        highWater=Math.max(highWater,document.nextId);anchor=caret=nearestBoundary(offset+inserted.length());
        if(!merge)push(undo,before);redo.clear();revision++;stateIdentity=++nextStateIdentity;
        lastKind=kind;lastCaret=caret;lastEditNanos=now;return true;
    }
    private boolean canMerge(Kind kind,int offset,int length,String text,long now){
        if(lastKind!=kind||now-lastEditNanos>MERGE_NANOS||anchor!=caret||lastCaret!=caret||undo.isEmpty())return false;
        if(kind==Kind.TYPE)return length==0&&offset==caret&&text.indexOf('\n')<0;
        if(kind==Kind.BACKSPACE)return text.isEmpty()&&length>0&&offset+length==caret;
        if(kind==Kind.DELETE)return text.isEmpty()&&length>0&&offset==caret;
        return false;
    }
    private static long estimate(NoteDocument d){long bytes=4096L+d.text.length()*2L;for(NoteDocument.Atom a:d.atoms){bytes+=192;if(a.inputFonts!=null)bytes+=24L+8L*a.inputFonts.length;if(a.inputChances!=null)bytes+=24L+4L*a.inputChances.length;}return bytes;}
    private State state(){return new State(document,anchor,caret,availableCuts,stateIdentity);}
    private void reserveHistory(long incoming){
        // Release old snapshots before allocating the next copy, including at the 30-entry limit.
        long needed=incoming;for(State s:undo)needed+=s.bytes;for(State s:redo)needed+=s.bytes;
        while((needed>HISTORY_BYTES||undo.size()+redo.size()>=HISTORY_LIMIT)&&(!undo.isEmpty()||!redo.isEmpty())){State old=!undo.isEmpty()?undo.removeLast():redo.removeLast();needed-=old.bytes;}
    }
    private State historyState(){reserveHistory(estimate(document));return state();}
    private static void push(Deque<State> stack,State value){stack.push(value);while(stack.size()>HISTORY_LIMIT)stack.removeLast();}
    private void restore(State state){
        document=state.document.copy();document.nextId=Math.max(document.nextId,highWater);highWater=document.nextId;
        anchor=state.anchor;caret=state.caret;availableCuts=new HashSet<String>(state.cuts);stateIdentity=state.identity;revision++;breakGroup();
    }
    public boolean undo(){if(undo.isEmpty())return false;State target=undo.pop();push(redo,historyState());restore(target);return true;}
    public boolean redo(){if(redo.isEmpty())return false;State target=redo.pop();push(undo,historyState());restore(target);return true;}
    public Transaction capture(){breakGroup();return new Transaction(state(),revision,identity);}
    public boolean commit(Transaction transaction,NoteDocument candidate,Kind kind){
        if(transaction==null||transaction.revision!=revision||!identity.equals(transaction.owner))return false;
        Objects.requireNonNull(candidate,"candidate");
        NoteDocument next=candidate.copy();
        next.validate();
        if(kind==Kind.UPDATE)for(NoteDocument.Atom a:next.atoms)if(a.override==null){a.inputFonts=inputFonts.clone();a.inputChances=inputChances.clone();a.inputGeneration=next.generation;}
        next.nextId=Math.max(next.nextId,highWater);
        if(next.encode().equals(document.encode()))return false;
        reserveHistory(transaction.before.bytes);
        push(undo,transaction.before);redo.clear();document=next;highWater=next.nextId;
        anchor=nearestBoundary(anchor);caret=nearestBoundary(caret);revision++;stateIdentity=++nextStateIdentity;breakGroup();return true;
    }
    public boolean commit(NoteDocument candidate,Kind kind){return commit(capture(),candidate,kind);}
    /** Explicit Update; selection-font overrides and their samples remain protected. */
    public boolean rerandomize(){
        Transaction t=capture();NoteDocument d=t.document();d.rerandomize();
        return commit(t,d,Kind.UPDATE);
    }

    /** Accept only derived font/sample data; a layout cannot rewrite text, selection or settings. */
    public boolean adoptLayout(NoteDocument candidate,long sourceRevision){
        if(sourceRevision!=revision||candidate==null||document.generation!=candidate.generation||!document.text.equals(candidate.text)||document.atoms.size()!=candidate.atoms.size())return false;
        for(int i=0;i<document.atoms.size();i++){
            NoteDocument.Atom a=document.atoms.get(i),b=candidate.atoms.get(i);
            if(a.id!=b.id||!a.text.equals(b.text)||!samePolicy(a,b)||!sameFont(a.override,b.override))return false;
        }
        mergeMetadata(document,candidate);
        // Samples discovered after a rapid burst also belong to unresolved snapshots of those same atoms.
        for(State s:undo)mergeMetadata(s.document,candidate);
        for(State s:redo)mergeMetadata(s.document,candidate);
        return true;
    }
    private static boolean samePolicy(NoteDocument.Atom a,NoteDocument.Atom b){return Arrays.equals(a.inputFonts,b.inputFonts)&&Arrays.equals(a.inputChances,b.inputChances)&&Objects.equals(a.inputGeneration,b.inputGeneration);}
    private static boolean sameFont(NoteDocument.FontRef a,NoteDocument.FontRef b){return a==b||(a!=null&&b!=null&&Objects.equals(a.hash,b.hash)&&Objects.equals(a.relativePath,b.relativePath));}
    private static void mergeMetadata(NoteDocument target,NoteDocument prepared){
        if(target.generation!=prepared.generation)return;
        Map<Long,NoteDocument.Atom> source=new HashMap<Long,NoteDocument.Atom>();for(NoteDocument.Atom a:prepared.atoms)source.put(a.id,a);
        for(NoteDocument.Atom a:target.atoms){
            NoteDocument.Atom b=source.get(a.id);if(b==null||!a.text.equals(b.text)||!samePolicy(a,b)||!sameFont(a.override,b.override))continue;
            if(a.automatic==null&&b.automatic!=null)a.automatic=NoteDocument.copyFont(b.automatic);
            if(!a.sampled&&b.sampled){a.size=b.size;a.scaleX=b.scaleX;a.scaleY=b.scaleY;a.rotate=b.rotate;a.y=b.y;a.space=b.space;a.sampled=true;}
        }
        if(target.pool.isEmpty()&&!prepared.pool.isEmpty()){for(NoteDocument.FontRef f:prepared.pool)target.pool.add(NoteDocument.copyFont(f));target.chances=prepared.chances==null?null:prepared.chances.clone();}
    }

    public ClipboardContent copySelection(){return new ClipboardContent(document.text.substring(selectionStart(),selectionEnd()));}
    public ClipboardContent cutSelection(){
        int start=selectionStart(),end=selectionEnd();if(start==end)return new ClipboardContent("");
        List<NoteDocument.Atom> removed=new ArrayList<NoteDocument.Atom>();int pos=0;
        for(NoteDocument.Atom a:document.atoms){if(pos>=start&&pos<end)removed.add(NoteDocument.copyAtom(a));pos+=a.text.length();}
        String text=document.text.substring(start,end),token=UUID.randomUUID().toString();
        replace(start,end-start,"",Kind.CUT);availableCuts.add(token);breakGroup();
        return new ClipboardContent(text,identity,token,removed);
    }
    public boolean pasteText(String text){return replaceSelection(text,Kind.PASTE);}
    public boolean paste(ClipboardContent content){
        if(content==null)return false;
        if(content.cut==null||!identity.equals(content.owner)||!availableCuts.contains(content.cut))return pasteText(content.text);
        Set<Long> present=new HashSet<Long>();for(NoteDocument.Atom a:document.atoms)present.add(a.id);
        for(NoteDocument.Atom a:content.atoms)if(present.contains(a.id))return pasteText(content.text);
        int start=selectionStart(),end=selectionEnd();range(start,end-start);
        if((long)document.text.length()-(end-start)+content.text.length()>NoteDocument.MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        State before=historyState();long firstNew=Math.max(document.nextId,highWater);document.nextId=firstNew;
        Map<Integer,NoteDocument.Atom> moved=new HashMap<Integer,NoteDocument.Atom>();int pos=start;
        for(NoteDocument.Atom a:content.atoms){moved.put(pos,a);pos+=a.text.length();}
        document.replace(start,end-start,content.text);pos=0;
        for(int i=0;i<document.atoms.size();i++){
            NoteDocument.Atom a=document.atoms.get(i),original=moved.get(pos);
            if(original!=null&&a.text.equals(original.text))document.atoms.set(i,NoteDocument.copyAtom(original));
            else if(a.id>=firstNew){a.inputFonts=inputFonts.clone();a.inputChances=inputChances.clone();a.inputGeneration=document.generation;}
            pos+=a.text.length();
        }
        highWater=Math.max(highWater,document.nextId);
        availableCuts.remove(content.cut);anchor=caret=nearestBoundary(start+content.text.length());
        push(undo,before);redo.clear();revision++;stateIdentity=++nextStateIdentity;breakGroup();return true;
    }
    /** Call if the private clipboard flavor is explicitly discarded by this editor. */
    public void clipboardReplaced(){availableCuts.clear();}
}
