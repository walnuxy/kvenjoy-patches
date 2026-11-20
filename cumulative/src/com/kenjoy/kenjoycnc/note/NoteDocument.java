package com.kenjoy.kenjoycnc.note;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.kenjoy.kenjoycnc.data.PageInfo;
import com.kenjoy.kenjoycnc.data.WordContentFormat;
import com.kenjoy.kenjoycnc.data.FontsSetData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Document state only; selection, Swing components and paths are never serialized. */
public final class NoteDocument {
    public static final int FORMAT = 1;
    public static final long MAX_DOCUMENT_BYTES = 16L * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 100_000;
    public int formatVersion = FORMAT;
    public long nextId = 1;
    public long generation = 0;
    /** Stable seed for the independent appearance-effect random stream. */
    public long appearanceSeed = 0;
    public String text = "";
    public PageInfo page = new PageInfo();
    public WordContentFormat format = new WordContentFormat();
    public ShapeSettings shapes = new ShapeSettings();
    public NoteAppearance.Config appearance = new NoteAppearance.Config();
    /** Page-aware pen-down exclusions. Null is rejected; older formats default to an empty set. */
    public NoteForbiddenZones forbiddenZones = new NoteForbiddenZones();
    public List<Atom> atoms = new ArrayList<Atom>();
    public List<FontRef> pool = new ArrayList<FontRef>();
    public int[] chances;

    public static final class FontRef {
        public String relativePath, hash, name;
        public FontRef() {}
        public FontRef(String path, String sha, String label) { relativePath=path; hash=sha; name=label; }
    }
    public static final class Atom {
        public long id;
        public String text;
        public FontRef automatic, override;
        /** Null means an older document uses the document-level font pool. */
        public String[] inputFonts;
        public int[] inputChances;
        /** Creation seed survives moving an unresolved character across an Update. */
        public Long inputGeneration;
        /** Stable per-atom random streams for the optional appearance effects. */
        public NoteAppearance.State effect;
        /** Null means that this atom inherits the document appearance settings. */
        public NoteAppearance.Config appearanceOverride;
        public float size=1, scaleX=1, scaleY=1, rotate=0, y=0, space=1;
        public boolean sampled;
        /** Persistent annotation for a character created by whole-article missing-glyph replacement. */
        public boolean replacementMarked;
        public boolean drawable() { return text.length()==1 && Character.getType(text.charAt(0))!=Character.FORMAT && !Character.isWhitespace(text.charAt(0)) && !Character.isSpaceChar(text.charAt(0)); }
    }
    public static final class ShapeSettings {
        public float baseLine=1, randomY=0, randomFirstPos=0, widthAdd=0, spaceWidthAdd=0, tabWidthAdd=0, simple=0;
        public float[] randomFontSize={1,1}, randomXScale={1,1}, randomYScale={1,1}, randomRotate={0,0}, randomSpace={1,1};
        public String chars="，,.。";
        public static ShapeSettings capture(FontsSetData source) {
            if(source==null)return null;
            ShapeSettings copy=new ShapeSettings();
            copy.baseLine=source.baseLine;copy.randomY=source.randomY;copy.randomFirstPos=source.randomFirstPos;
            copy.widthAdd=source.widthAdd;copy.spaceWidthAdd=source.spaceWidthAdd;
            copy.tabWidthAdd=source.tabWidthAdd;copy.simple=source.simple;copy.chars=source.chars;
            copy.randomFontSize=cloneArray(source.randomFontSize);copy.randomXScale=cloneArray(source.randomXScale);
            copy.randomYScale=cloneArray(source.randomYScale);copy.randomRotate=cloneArray(source.randomRotate);
            copy.randomSpace=cloneArray(source.randomSpace);return copy;
        }
        public ShapeSettings copy(){
            ShapeSettings copy=new ShapeSettings();
            copy.baseLine=baseLine;copy.randomY=randomY;copy.randomFirstPos=randomFirstPos;
            copy.widthAdd=widthAdd;copy.spaceWidthAdd=spaceWidthAdd;copy.tabWidthAdd=tabWidthAdd;
            copy.simple=simple;copy.chars=chars;
            copy.randomFontSize=cloneArray(randomFontSize);copy.randomXScale=cloneArray(randomXScale);
            copy.randomYScale=cloneArray(randomYScale);copy.randomRotate=cloneArray(randomRotate);
            copy.randomSpace=cloneArray(randomSpace);return copy;
        }
    }
    private static float[] cloneArray(float[] values){return values==null?null:values.clone();}
    public static PageInfo copyPage(PageInfo source){
        if(source==null)return null;
        PageInfo copy=new PageInfo();copy.name=source.name;
        copy.pageWidth=source.pageWidth;copy.pageHeight=source.pageHeight;
        copy.leftGap=source.leftGap;copy.rightGap=source.rightGap;
        copy.topGap=source.topGap;copy.bottomGap=source.bottomGap;
        copy.lineGap=source.lineGap;copy.lineNum=source.lineNum;copy.fontSize=source.fontSize;
        copy.baseLine=source.baseLine;copy.isDefault=source.isDefault;
        copy.fontPath=source.fontPath==null?null:source.fontPath.clone();
        copy.fontChance=source.fontChance==null?null:source.fontChance.clone();
        copy.space=source.space;copy.lineGapOffset=source.lineGapOffset;copy.contentAlign=source.contentAlign;
        copy.alignOffsetX=source.alignOffsetX;copy.alignOffsetY=source.alignOffsetY;
        copy.alignOffsetXMax=source.alignOffsetXMax;copy.alignOffsetYMax=source.alignOffsetYMax;
        copy.rotation=source.rotation;return copy;
    }
    public static WordContentFormat copyFormat(WordContentFormat source){
        if(source==null)return null;
        WordContentFormat copy=new WordContentFormat();copy.charset=source.charset;
        copy.autoGp=source.autoGp;copy.gpNum=source.gpNum;copy.firstLineAlign=source.firstLineAlign;
        return copy;
    }
    public NoteDocument copy() {
        validate();
        NoteDocument d=new NoteDocument();
        d.formatVersion=formatVersion;d.nextId=nextId;d.generation=generation;d.text=text;
        d.appearanceSeed=appearanceSeed;d.appearance=NoteAppearance.copy(appearance);
        d.page=copyPage(page);d.format=copyFormat(format);d.shapes=shapes.copy();
        d.forbiddenZones=forbiddenZones.copy();
        for(Atom a:atoms)d.atoms.add(copyAtom(a));
        for(FontRef f:pool)d.pool.add(copyFont(f));
        d.chances=chances==null?null:chances.clone();return d;
    }
    public static FontRef copyFont(FontRef f) {return f==null?null:new FontRef(f.relativePath,f.hash,f.name);}
    public static Atom copyAtom(Atom a) {
        Atom b=new Atom();b.id=a.id;b.text=a.text;b.automatic=copyFont(a.automatic);b.override=copyFont(a.override);
        b.inputFonts=a.inputFonts==null?null:a.inputFonts.clone();b.inputChances=a.inputChances==null?null:a.inputChances.clone();b.inputGeneration=a.inputGeneration;
        b.effect=NoteAppearance.copyState(a.effect);b.appearanceOverride=a.appearanceOverride==null?null:NoteAppearance.copy(a.appearanceOverride);
        b.size=a.size;b.scaleX=a.scaleX;b.scaleY=a.scaleY;b.rotate=a.rotate;b.y=a.y;b.space=a.space;b.sampled=a.sampled;b.replacementMarked=a.replacementMarked;return b;
    }
    /** Storage v2 remains byte-compatible until appearance state or forbidden zones are stored. */
    public String encode() {
        validate();Tables tables=new Tables();
        boolean version4=hasForbiddenZoneData();
        boolean version3=version4||hasAppearanceData();
        for(FontRef f:pool)tables.font(f);
        for(Atom a:atoms){tables.font(a.automatic);tables.font(a.override);tables.policy(a);tables.appearance(a);}
        StringWriter output=new StringWriter();Gson gson=new Gson();
        try{
            JsonWriter w=new JsonWriter(output);w.setSerializeNulls(true);w.beginObject();
            w.name("formatVersion").value(version4?4:version3?3:2);w.name("nextId").value(nextId);w.name("generation").value(generation);
            if(version3)w.name("appearanceSeed").value(appearanceSeed);
            w.name("text").value(text);
            w.name("page");gson.toJson(page,PageInfo.class,w);w.name("format");gson.toJson(format,WordContentFormat.class,w);w.name("shapes");gson.toJson(shapes,ShapeSettings.class,w);
            if(version3){w.name("appearance");gson.toJson(appearance,NoteAppearance.Config.class,w);}
            if(version3){w.name("appearanceOverrides").beginArray();for(NoteAppearance.Config c:tables.appearanceOverrides)gson.toJson(c,NoteAppearance.Config.class,w);w.endArray();}
            if(version4){w.name("forbiddenZones");gson.toJson(forbiddenZones,NoteForbiddenZones.class,w);}
            w.name("fontRefs").beginArray();for(FontRef f:tables.fonts)gson.toJson(f,FontRef.class,w);w.endArray();
            w.name("inputPolicies").beginArray();for(Policy p:tables.policies){w.beginObject();w.name("fonts");writeStrings(w,p.fonts);w.name("chances");writeInts(w,p.chances);w.endObject();}w.endArray();
            w.name("pool").beginArray();for(FontRef f:pool)w.value(tables.font(f));w.endArray();w.name("chances");writeInts(w,chances);
            w.name("atoms").beginArray();
            for(Atom a:atoms){
                w.beginArray();w.value(a.id);w.value(a.text);w.value(tables.font(a.automatic));w.value(tables.font(a.override));w.value(tables.policy(a));
                if(a.inputGeneration==null)w.nullValue();else w.value(a.inputGeneration);
                w.value(Float.valueOf(a.size));w.value(Float.valueOf(a.scaleX));w.value(Float.valueOf(a.scaleY));w.value(Float.valueOf(a.rotate));w.value(Float.valueOf(a.y));w.value(Float.valueOf(a.space));w.value(a.sampled);
                if(version3){writeEffectState(w,a.effect);w.value(tables.appearance(a));}
                w.endArray();
            }
            w.endArray();boolean marked=false;for(Atom a:atoms)marked|=a.replacementMarked;
            if(marked){w.name("replacementMarks").beginArray();for(Atom a:atoms)if(a.replacementMarked)w.value(a.id);w.endArray();}
            w.endObject();w.close();return output.toString();
        }catch(IOException impossible){throw new IllegalStateException("文章编码失败",impossible);}
    }
    private boolean hasAppearanceData() {
        if(!NoteAppearance.key(appearance).equals(NoteAppearance.key(new NoteAppearance.Config())))return true;
        for(Atom a:atoms)if(a.effect!=null||a.appearanceOverride!=null)return true;
        return false;
    }
    private boolean hasForbiddenZoneData(){return forbiddenZones!=null&&forbiddenZones.hasData();}
    private static void writeEffectState(JsonWriter w,NoteAppearance.State state)throws IOException {
        if(state==null){w.nullValue();return;}
        w.beginArray();w.value(state.epoch);w.value(Float.valueOf(state.continuity[2]));w.endArray();
    }
    private static final class Policy {
        final String[] fonts;final int[] chances;
        Policy(String[] f,int[] c){fonts=f;chances=c;}
        public int hashCode(){return 31*Arrays.hashCode(fonts)+Arrays.hashCode(chances);}
        public boolean equals(Object o){if(!(o instanceof Policy))return false;Policy p=(Policy)o;return Arrays.equals(fonts,p.fonts)&&Arrays.equals(chances,p.chances);}
    }
    private static final class Tables {
        final List<FontRef> fonts=new ArrayList<FontRef>();final Map<List<String>,Integer> fontIds=new HashMap<List<String>,Integer>();
        final List<Policy> policies=new ArrayList<Policy>();final Map<Policy,Integer> policyIds=new HashMap<Policy,Integer>();
        final List<NoteAppearance.Config> appearanceOverrides=new ArrayList<NoteAppearance.Config>();final Map<String,Integer> appearanceIds=new HashMap<String,Integer>();
        int font(FontRef f){if(f==null)return -1;List<String> key=Arrays.asList(f.relativePath,f.hash,f.name);Integer id=fontIds.get(key);if(id==null){id=fonts.size();fonts.add(f);fontIds.put(key,id);}return id;}
        int policy(Atom a){if(a.inputFonts==null&&a.inputChances==null)return -1;Policy key=new Policy(a.inputFonts,a.inputChances);Integer id=policyIds.get(key);if(id==null){id=policies.size();policies.add(key);policyIds.put(key,id);}return id;}
        int appearance(Atom a){if(a.appearanceOverride==null)return -1;String key=NoteAppearance.key(a.appearanceOverride);Integer id=appearanceIds.get(key);if(id==null){id=appearanceOverrides.size();appearanceOverrides.add(NoteAppearance.copy(a.appearanceOverride));appearanceIds.put(key,id);}return id;}
    }
    private static void writeStrings(JsonWriter w,String[] values)throws IOException{if(values==null){w.nullValue();return;}w.beginArray();for(String s:values)w.value(s);w.endArray();}
    private static void writeInts(JsonWriter w,int[] values)throws IOException{if(values==null){w.nullValue();return;}w.beginArray();for(int n:values)w.value(n);w.endArray();}
    public static NoteDocument decode(String json) {
        if(json==null)throw new IllegalArgumentException("文章文档版本或内容不兼容");
        try{
            int version=1;JsonReader probe=new JsonReader(new StringReader(json));probe.beginObject();
            while(probe.hasNext()){String name=probe.nextName();if(name.equals("formatVersion")){version=readInt(probe);break;}probe.skipValue();}probe.close();
            NoteDocument d;
            if(version==1){d=new Gson().fromJson(json,NoteDocument.class);if(d!=null)migrateLegacyAppearance(d);}
            else if(version==2){d=readCompact(json,version);migrateLegacyAppearance(d);}
            else if(version==3||version==4)d=readCompact(json,version);
            else throw new IllegalArgumentException("不支持的文章文档版本："+version);
            if(d==null)throw new IllegalArgumentException("文章文档版本或内容不兼容");d.validate();return d;
        }catch(IOException error){throw new IllegalArgumentException("文章文档格式无效",error);}
    }
    private static void migrateLegacyAppearance(NoteDocument d) {
        if(d.atoms==null)throw new IllegalArgumentException("文章文档版本或内容不兼容");
        d.appearance=new NoteAppearance.Config();
        d.appearanceSeed=NoteAppearance.legacySeed(d);
        for(Atom a:d.atoms){a.effect=null;a.appearanceOverride=null;}
    }
    private static long readLong(JsonReader r)throws IOException{
        if(r.peek()!=JsonToken.NUMBER)throw new IllegalArgumentException("文章整数无效");
        try{return new java.math.BigDecimal(r.nextString()).longValueExact();}catch(ArithmeticException|NumberFormatException error){throw new IllegalArgumentException("文章整数无效",error);}
    }
    private static int readInt(JsonReader r)throws IOException{long n=readLong(r);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException("文章索引越界");return (int)n;}
    private static float readFloat(JsonReader r)throws IOException{if(r.peek()!=JsonToken.NUMBER)throw new IllegalArgumentException("文章数值无效");float n=Float.parseFloat(r.nextString());if(!Float.isFinite(n))throw new IllegalArgumentException("文章数值必须有限");return n;}
    private static String readString(JsonReader r)throws IOException{if(r.peek()!=JsonToken.STRING)throw new IllegalArgumentException("文章文字无效");return r.nextString();}
    private static int[] readInts(JsonReader r)throws IOException{
        if(r.peek()==JsonToken.NULL){r.nextNull();return null;}List<Integer> values=new ArrayList<Integer>();r.beginArray();while(r.hasNext())values.add(readInt(r));r.endArray();int[] out=new int[values.size()];for(int i=0;i<out.length;i++)out[i]=values.get(i);return out;
    }
    private static String[] readStrings(JsonReader r)throws IOException{
        if(r.peek()==JsonToken.NULL){r.nextNull();return null;}List<String> values=new ArrayList<String>();r.beginArray();while(r.hasNext()){if(r.peek()==JsonToken.NULL){r.nextNull();values.add(null);}else values.add(readString(r));}r.endArray();return values.toArray(new String[0]);
    }
    private static final class StoredAtom {final Atom atom=new Atom();int automatic,override,policy,appearance=-1;long effectEpoch;float frozenVertical;boolean hasEffect;}
    private static NoteDocument readCompact(String json,int version)throws IOException{
        NoteDocument d=new NoteDocument();Gson gson=new Gson();List<FontRef> refs=new ArrayList<FontRef>();List<Policy> policies=new ArrayList<Policy>();List<NoteAppearance.Config> appearanceOverrides=new ArrayList<NoteAppearance.Config>();List<StoredAtom> stored=new ArrayList<StoredAtom>();int[] poolIds=null;Set<Long> replacementMarks=new HashSet<Long>();
        JsonReader r=new JsonReader(new StringReader(json));Set<String> fields=new HashSet<String>();r.beginObject();
        while(r.hasNext()){
            String name=r.nextName();if(!fields.add(name))throw new IllegalArgumentException("文章字段重复："+name);
            if(name.equals("formatVersion")){if(readInt(r)!=version)throw new IllegalArgumentException("文章版本无效");}
            else if(name.equals("nextId"))d.nextId=readLong(r);
            else if(name.equals("generation"))d.generation=readLong(r);
            else if(name.equals("appearanceSeed"))d.appearanceSeed=readLong(r);
            else if(name.equals("text"))d.text=readString(r);
            else if(name.equals("page"))d.page=gson.fromJson(r,PageInfo.class);
            else if(name.equals("format"))d.format=gson.fromJson(r,WordContentFormat.class);
            else if(name.equals("shapes"))d.shapes=gson.fromJson(r,ShapeSettings.class);
            else if(name.equals("appearance"))d.appearance=gson.fromJson(r,NoteAppearance.Config.class);
            else if(name.equals("appearanceOverrides")){r.beginArray();while(r.hasNext()){if(r.peek()!=JsonToken.BEGIN_OBJECT)throw new IllegalArgumentException("字符字形设置无效");appearanceOverrides.add(gson.fromJson(r,NoteAppearance.Config.class));}r.endArray();}
            else if(name.equals("forbiddenZones"))d.forbiddenZones=gson.fromJson(r,NoteForbiddenZones.class);
            else if(name.equals("chances"))d.chances=readInts(r);
            else if(name.equals("pool"))poolIds=readInts(r);
            else if(name.equals("replacementMarks")){r.beginArray();while(r.hasNext()){long id=readLong(r);if(id<=0||replacementMarks.size()>=MAX_TEXT_CHARS||!replacementMarks.add(id))throw new IllegalArgumentException("替换标记引用无效");}r.endArray();}
            else if(name.equals("fontRefs")){r.beginArray();while(r.hasNext()){if(r.peek()!=JsonToken.BEGIN_OBJECT)throw new IllegalArgumentException("字体引用无效");refs.add(gson.fromJson(r,FontRef.class));}r.endArray();}
            else if(name.equals("inputPolicies")){
                r.beginArray();while(r.hasNext()){
                    r.beginObject();Set<String> seen=new HashSet<String>();String[] fonts=null;int[] chances=null;
                    while(r.hasNext()){String field=r.nextName();if(!seen.add(field))throw new IllegalArgumentException("输入策略字段重复");if(field.equals("fonts"))fonts=readStrings(r);else if(field.equals("chances"))chances=readInts(r);else r.skipValue();}
                    r.endObject();if(!seen.contains("fonts")||!seen.contains("chances"))throw new IllegalArgumentException("输入策略不完整");policies.add(new Policy(fonts,chances));
                }r.endArray();
            }else if(name.equals("atoms")){
                r.beginArray();while(r.hasNext()){
                    if(stored.size()>=MAX_TEXT_CHARS)throw new IllegalArgumentException("文章字符记录过多");
                    StoredAtom s=new StoredAtom();Atom a=s.atom;r.beginArray();a.id=readLong(r);a.text=readString(r);s.automatic=readInt(r);s.override=readInt(r);s.policy=readInt(r);
                    if(r.peek()==JsonToken.NULL)r.nextNull();else a.inputGeneration=readLong(r);
                    a.size=readFloat(r);a.scaleX=readFloat(r);a.scaleY=readFloat(r);a.rotate=readFloat(r);a.y=readFloat(r);a.space=readFloat(r);
                    if(r.peek()!=JsonToken.BOOLEAN)throw new IllegalArgumentException("字符采样标记无效");a.sampled=r.nextBoolean();
                    if(version>=3){
                        if(r.peek()==JsonToken.NULL){r.nextNull();s.hasEffect=false;}
                        else{r.beginArray();s.effectEpoch=readLong(r);s.frozenVertical=readFloat(r);if(r.hasNext())throw new IllegalArgumentException("字符随机状态列数无效");r.endArray();s.hasEffect=true;}
                        s.appearance=readInt(r);
                    }
                    if(r.hasNext())throw new IllegalArgumentException("字符记录列数不兼容");r.endArray();stored.add(s);
                }r.endArray();
            }else r.skipValue();
        }
        r.endObject();if(r.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("文章末尾存在多余内容");r.close();
        for(String required:new String[]{"formatVersion","nextId","generation","text","page","format","shapes","fontRefs","inputPolicies","pool","atoms"})if(!fields.contains(required))throw new IllegalArgumentException("文章缺少字段："+required);
        if(version>=3&&(!fields.contains("appearanceSeed")||!fields.contains("appearance")||!fields.contains("appearanceOverrides")))throw new IllegalArgumentException("文章缺少字形效果状态");
        if(version>=4&&!fields.contains("forbiddenZones"))throw new IllegalArgumentException("文章缺少禁写区状态");
        if(poolIds==null)throw new IllegalArgumentException("文章字体池无效");for(int index:poolIds)d.pool.add(copyFont(reference(refs,index,"字体")));
        for(StoredAtom s:stored){Atom a=s.atom;a.automatic=copyFont(reference(refs,s.automatic,"字体"));a.override=copyFont(reference(refs,s.override,"字体"));Policy p=reference(policies,s.policy,"输入策略");if(p!=null){a.inputFonts=p.fonts==null?null:p.fonts.clone();a.inputChances=p.chances==null?null:p.chances.clone();}if(s.hasEffect)a.effect=NoteAppearance.restore(d.appearanceSeed,a.id,s.effectEpoch,s.frozenVertical);a.appearanceOverride=s.appearance==-1?null:NoteAppearance.copy(reference(appearanceOverrides,s.appearance,"字符字形设置"));a.replacementMarked=replacementMarks.remove(a.id);d.atoms.add(a);}
        if(!replacementMarks.isEmpty())throw new IllegalArgumentException("替换标记引用不存在的字符");
        return d;
    }
    private static <T>T reference(List<T> values,int index,String kind){if(index==-1)return null;if(index<0||index>=values.size())throw new IllegalArgumentException(kind+"引用索引无效："+index);return values.get(index);}
    public void validate() {
        if(formatVersion!=FORMAT || atoms==null || page==null || format==null || shapes==null || pool==null || appearance==null || forbiddenZones==null)
            throw new IllegalArgumentException("文章文档版本或内容不兼容");
        NoteAppearance.validate(appearance);
        forbiddenZones.validate(page.pageWidth,page.pageHeight);
        if(text==null||text.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        Set<Long> ids=new HashSet<Long>();int offset=0;long max=0;
        for(Atom a:atoms) {
            if(a==null || a.text==null || a.text.isEmpty() || a.id<=0 || !ids.add(a.id)) throw new IllegalArgumentException("文章字符身份无效");
            if(a.effect!=null)NoteAppearance.validateState(a.effect);
            if(a.appearanceOverride!=null)NoteAppearance.validate(a.appearanceOverride);
            if(a.effect==null&&NoteAppearance.effective(this,a).randomMode!=0)throw new IllegalArgumentException("启用随机误差的字符缺少冻结状态");
            if(!text.regionMatches(offset,a.text,0,a.text.length()))throw new IllegalArgumentException("文章正文与字符记录不一致");
            offset+=a.text.length();max=Math.max(max,a.id);
        }
        if(offset!=text.length() || nextId<=max) throw new IllegalArgumentException("文章正文与字符记录不一致");
    }
    public static String normalize(String s) { return s.replace("\r\n","\n").replace('\r','\n'); }
    private static boolean controlBoundary(int cp){int type=Character.getType(cp);return type==Character.CONTROL||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR;}
    /** Apply an exact editor change. Unaffected atoms retain identity; no content-based diff. */
    public void replace(int offset,int length,String inserted) {
        if(offset<0 || length<0 || length>text.length() || offset>text.length()-length) throw new IllegalArgumentException("编辑位置无效");
        String value=normalize(inserted);
        if((long)text.length()-length+value.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        Map<Integer,Atom> retained=new HashMap<Integer,Atom>();int pos=0;
        for(Atom a:atoms) {
            int end=pos+a.text.length();
            if(end<=offset) retained.put(pos,a);
            else if(pos>=offset+length && !(length==0 && pos<offset && end>offset)) retained.put(pos+value.length()-length,a);
            pos=end;
        }
        String changed=text.substring(0,offset)+value+text.substring(offset+length);long allocated=nextId;
        List<Atom> result=new ArrayList<Atom>();
        Set<Long> created=new HashSet<Long>();
        for(int i=0;i<changed.length();) {
            int start=i,cp=changed.codePointAt(i);i+=Character.charCount(cp);
            while(!controlBoundary(cp)&&i<changed.length()) {
                int q=changed.codePointAt(i),type=Character.getType(q);
                if(type==Character.NON_SPACING_MARK || type==Character.COMBINING_SPACING_MARK || type==Character.ENCLOSING_MARK || q==0xFE0F) i+=Character.charCount(q);
                else if(q==0x200D && i+1<changed.length()&&!controlBoundary(changed.codePointAt(i+1))) { i++;i+=Character.charCount(changed.codePointAt(i)); }
                else break;
            }
            String part=changed.substring(start,i);Atom a=retained.get(start);
            if(a==null || !part.equals(a.text)) {if(allocated<=0||allocated==Long.MAX_VALUE)throw new IllegalArgumentException("文章字符编号已耗尽");a=new Atom();a.id=allocated++;a.text=part;created.add(a.id);}
            result.add(a);
        }
        atoms=result;text=changed;nextId=allocated;
        if(!created.isEmpty())NoteAppearance.initialize(this,created,false,true);
    }
    public void rerandomize() {
        generation++;
        for(Atom a:atoms) if(a.override==null) { a.automatic=null;a.sampled=false;a.inputGeneration=generation; }
    }
    public static void validateReplacement(String replacement){
        if(replacement==null||replacement.isEmpty()||replacement.codePointCount(0,replacement.length())!=1)throw new IllegalArgumentException("请输入一个替代字符（可以是空格）");
        int cp=replacement.codePointAt(0),type=Character.getType(cp);
        if(controlBoundary(cp)||type==Character.FORMAT||type==Character.SURROGATE||type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK)
            throw new IllegalArgumentException("替代字符不能是控制符或独立组合符");
    }
    /** Atomic linear replacement. The caller supplies IDs from the complete layout, not just the visible page. */
    public void replaceMissing(Set<Long> ids,String replacement){
        Objects.requireNonNull(ids,"ids");validateReplacement(replacement);
        List<Atom> changed=new ArrayList<Atom>(atoms.size());StringBuilder body=new StringBuilder();long allocated=nextId;Set<Long> created=new HashSet<Long>();
        for(Atom original:atoms){Atom a=original;
            if(ids.contains(a.id)){if(allocated<=0||allocated==Long.MAX_VALUE)throw new IllegalArgumentException("文章字符编号已耗尽");a=new Atom();a.id=allocated++;a.text=replacement;a.replacementMarked=true;created.add(a.id);}
            changed.add(a);body.append(a.text);
            if(body.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        }
        atoms=changed;text=body.toString();nextId=allocated;
        if(!created.isEmpty())NoteAppearance.initialize(this,created,false,true);
    }
    /** One pass over the whole article; preserve every unaffected glyph identity. */
    public void clearMissing(Set<Long> ids,boolean spaces) {
        List<Atom> kept=new ArrayList<Atom>();StringBuilder body=new StringBuilder();Set<Long> created=new HashSet<Long>();
        for(Atom a:atoms){
            if(ids.contains(a.id)){
                if(!spaces)continue;
                if(nextId<=0||nextId==Long.MAX_VALUE)throw new IllegalArgumentException("文章字符编号已耗尽");
                a=new Atom();a.id=nextId++;a.text=" ";created.add(a.id);
            }
            kept.add(a);body.append(a.text);
        }
        atoms=kept;text=body.toString();
        if(!created.isEmpty())NoteAppearance.initialize(this,created,false,true);
    }
    public Atom atom(long id) { for(Atom a:atoms) if(a.id==id)return a;return null; }
    public void save(Path target) throws IOException {
        Path absolute=target.toAbsolutePath();Path parent=absolute.getParent();Files.createDirectories(parent);
        String encoded=encode();byte[] data=encoded.getBytes(StandardCharsets.UTF_8);
        if(data.length>MAX_DOCUMENT_BYTES)throw new IOException("文章序列化后超过 16 MB 上限，无法保存");
        byte[] legacyFingerprint=null;
        int targetVersion=storageVersion(new StringReader(encoded));
        if(targetVersion>=3&&Files.exists(absolute)&&targetVersion>storageVersion(Files.newBufferedReader(absolute,StandardCharsets.UTF_8)))legacyFingerprint=backupLegacyArticle(absolute,parent);
        Path temp=parent.resolve(absolute.getFileName()+".tmp-"+Long.toHexString(System.nanoTime()));
        try {
            try(OutputStream out=Files.newOutputStream(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {out.write(data);}
            if(legacyFingerprint!=null&&!Arrays.equals(legacyFingerprint,fileHash(absolute)))throw new IOException("文章原件在升级备份后发生变化，已取消覆盖");
            try {Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    /** Reads the storage version without converting the document to its current in-memory format. */
    private static int storageVersion(Reader input) {
        try(JsonReader r=new JsonReader(input)) {
            if(r.peek()!=JsonToken.BEGIN_OBJECT)return 0;r.beginObject();
            while(r.hasNext()){String name=r.nextName();if(name.equals("formatVersion"))return readInt(r);r.skipValue();}
        }catch(Exception malformed){return 0;}
        return 0;
    }
    /** Creates a non-overwriting, verified same-directory copy before a storage-format upgrade. */
    private static byte[] backupLegacyArticle(Path source,Path parent)throws IOException {
        byte[] fingerprint=fileHash(source);String file=source.getFileName().toString();int dot=file.lastIndexOf('.');
        String stem=dot>0?file.substring(0,dot):file,extension=dot>0?file.substring(dot):".kjnote";
        String timestamp=new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.ROOT).format(new Date());
        IOException collision=null;
        for(int i=0;i<1000;i++) {
            Path backup=parent.resolve(stem+"-before-effects-"+timestamp+(i==0?"":"-"+i)+extension);
            try {
                Files.copy(source,backup,StandardCopyOption.COPY_ATTRIBUTES);
                if(!Arrays.equals(fingerprint,fileHash(backup)))throw new IOException("文章原件备份校验失败");
                if(!Arrays.equals(fingerprint,fileHash(source)))throw new IOException("文章原件在备份期间发生变化");
                return fingerprint;
            }catch(FileAlreadyExistsException collisionError){collision=collisionError;}
            catch(IOException failure){try{Files.deleteIfExists(backup);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
        }
        throw new IOException("无法为旧格式文章创建唯一备份",collision);
    }
    private static byte[] fileHash(Path file)throws IOException {
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(file)){byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1)digest.update(chunk,0,n);}
            return digest.digest();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static NoteDocument load(Path file) throws IOException {
        if(Files.size(file)>MAX_DOCUMENT_BYTES)throw new IOException("文章文件超过 16 MB 上限");
        try(InputStream in=Files.newInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[8192];int n;long total=0;
            while((n=in.read(chunk))!=-1){total+=n;if(total>MAX_DOCUMENT_BYTES)throw new IOException("文章文件超过 16 MB 上限");out.write(chunk,0,n);}
            return decode(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
}
