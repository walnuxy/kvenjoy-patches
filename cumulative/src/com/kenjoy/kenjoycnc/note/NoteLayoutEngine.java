package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.fonts.v;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.n.q;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;

/** Note-specific layout. Shared legacy g.a state is deliberately not used. */
public final class NoteLayoutEngine {
    private final NoteFonts fonts;
    private final float dpi;
    private final Set<Long> unavailable=new HashSet<Long>();
    private final Set<Long> punctuationFallback=new HashSet<Long>();
    private static boolean invisible(String text) {return text.codePoints().allMatch(c->Character.getType(c)==Character.FORMAT);}
    private static boolean builtin(char ch) {return ".．·•‧∙⋅…—–−".indexOf(ch)>=0;}
    /** Keep the document character; borrow this font's period only when its fullwidth form is absent. */
    private static char fontCharacter(v font,char ch){return ch=='．'&&(!font.a(ch)||font.b(ch)==null)?'.':ch;}
    public NoteLayoutEngine(NoteFonts fonts,float dpi) {this.fonts=fonts;this.dpi=dpi;}
    private float px(float mm) {return mm*dpi/25.4f;}
    private static boolean finite(float f) {return !Float.isNaN(f)&&!Float.isInfinite(f);}
    private float sample(Random r,float[] range) throws IOException {if(range==null||range.length!=2||!finite(range[0])||!finite(range[1]))throw new IOException("随机参数无效");return range[0]+r.nextFloat()*(range[1]-range[0]);}
    private Random random(NoteDocument d,long id) {return new Random(id*0x9e3779b97f4a7c15L+d.generation*0x632be59bd9b4e019L);}
    private Random random(NoteDocument d,NoteDocument.Atom a) {return new Random(a.id*0x9e3779b97f4a7c15L+(a.inputGeneration==null?d.generation:a.inputGeneration.longValue())*0x632be59bd9b4e019L);}
    private float lineOffset(NoteDocument d,long anchor){return random(d,anchor^0x51edL).nextFloat()*px(d.shapes.randomFirstPos);}
    private static final class Pool {
        final List<NoteDocument.FontRef> refs;
        final List<NoteFonts.Entry> entries=new ArrayList<NoteFonts.Entry>();
        final int[] weights;
        Pool(NoteFonts fonts,List<NoteDocument.FontRef> refs,int[] weights)throws Exception{this.refs=new ArrayList<NoteDocument.FontRef>();List<Integer> active=new ArrayList<Integer>();for(int i=0;i<refs.size();i++){int weight=weights==null?1:i<weights.length?Math.max(0,weights[i]):0;if(weight<=0)continue;this.refs.add(refs.get(i));entries.add(fonts.resolve(refs.get(i)));active.add(weight);}this.weights=new int[active.size()];for(int i=0;i<active.size();i++)this.weights[i]=active.get(i);}
    }
    private Pool namedPool(String[] names,int[] weights)throws Exception{
        List<String> active=new ArrayList<String>();List<Integer> chances=new ArrayList<Integer>();
        for(int i=0;i<names.length;i++){int weight=weights==null?1:i<weights.length?Math.max(0,weights[i]):0;if(weight>0){active.add(names[i]);chances.add(weight);}}
        int[] selected=new int[chances.size()];for(int i=0;i<selected.length;i++)selected[i]=chances.get(i);
        return new Pool(fonts,fonts.byNames(active.toArray(new String[active.size()])),selected);
    }
    private static final class PoolKey {
        final List<String> names;final int[] weights;
        PoolKey(String[] names,int[] weights){this.names=Arrays.asList(names.clone());this.weights=weights==null?null:weights.clone();}
        public int hashCode(){return names.hashCode()*31+Arrays.hashCode(weights);}
        public boolean equals(Object other){return other instanceof PoolKey&&names.equals(((PoolKey)other).names)&&Arrays.equals(weights,((PoolKey)other).weights);}
    }
    /** Positive weights are probabilities; an all-zero candidate set has no choice. */
    static int weightedIndex(Random random,int[] weights,boolean[] eligible){
        long total=0;for(int i=0;i<eligible.length;i++)if(eligible[i])total+=weights==null?1:i<weights.length?Math.max(0,weights[i]):0;
        if(total==0)return -1;
        double pick=random.nextDouble()*total;
        for(int i=0;i<eligible.length;i++)if(eligible[i]){pick-=weights==null?1:i<weights.length?Math.max(0,weights[i]):0;if(pick<0)return i;}
        return -1;
    }
    private void prepare(NoteDocument d) throws Exception {
        unavailable.clear();punctuationFallback.clear();
        Pool legacy=null;Map<PoolKey,Pool> strategies=new HashMap<PoolKey,Pool>();
        for(NoteDocument.Atom a:d.atoms) {
            if(invisible(a.text))continue;
            if(a.text.length()!=1) {unavailable.add(a.id);continue;}
            char ch=a.text.charAt(0);if(Character.isWhitespace(ch)||Character.isSpaceChar(ch))continue;
            Random r=random(d,a);
            if(a.override==null && a.automatic==null) {
                try{
                    Pool pool;
                    if(a.inputFonts==null){if(legacy==null){if(d.pool.isEmpty()&&d.page.fontPath!=null&&d.page.fontPath.length>0){legacy=namedPool(d.page.fontPath,d.page.fontChance);d.pool=legacy.refs;d.chances=legacy.weights.clone();}else legacy=new Pool(fonts,d.pool,d.chances);}pool=legacy;}
                    else{PoolKey key=new PoolKey(a.inputFonts,a.inputChances);pool=strategies.get(key);if(pool==null){pool=namedPool(a.inputFonts,a.inputChances);strategies.put(key,pool);}}
                    boolean[] eligible=new boolean[pool.entries.size()];
                    for(int i=0;i<eligible.length;i++)if(pool.weights==null||(i<pool.weights.length&&pool.weights[i]>0)){v font=pool.entries.get(i).font();char resolved=fontCharacter(font,ch);eligible[i]=font.a(resolved)&&font.b(resolved)!=null;}
                    // Font choice must not change the shape stream when a saved automatic font is already bound.
                    int index=weightedIndex(new Random(random(d,a).nextLong()^0x6a09e667f3bcc909L),pool.weights,eligible);
                    if(index<0){if(builtin(ch))punctuationFallback.add(a.id);else unavailable.add(a.id);continue;}
                    a.automatic=pool.refs.get(index);
                }catch(Exception fontFailure){if(builtin(ch))punctuationFallback.add(a.id);else unavailable.add(a.id);continue;}
            }
            NoteDocument.FontRef chosen=a.override!=null?a.override:a.automatic;
            try{v font=fonts.resolve(chosen).font();char resolved=fontCharacter(font,ch);if(!font.a(resolved)||font.b(resolved)==null){if(a.override==null&&builtin(ch))punctuationFallback.add(a.id);else unavailable.add(a.id);continue;}}
            catch(Exception fontFailure){if(a.override==null&&builtin(ch))punctuationFallback.add(a.id);else unavailable.add(a.id);continue;}
            if(!a.sampled) {
                a.size=sample(r,d.shapes.randomFontSize);a.scaleX=sample(r,d.shapes.randomXScale);a.scaleY=sample(r,d.shapes.randomYScale);
                a.rotate=sample(r,d.shapes.randomRotate);a.space=sample(r,d.shapes.randomSpace);a.y=(r.nextFloat()*2-1)*d.shapes.randomY;a.sampled=true;
            }
            if(!finite(a.size)||!finite(a.scaleX)||!finite(a.scaleY)||!finite(a.rotate)||!finite(a.space)||!finite(a.y)||a.size<=0||a.scaleX<=0||a.scaleY<=0||a.space<0)
                throw new IOException("字符随机尺寸或间距无效："+a.text);
        }
    }
    private static final class Form {e path;float width,shift;NoteDocument.FontRef font;boolean pending,missing,fallback;}
    private Form form(NoteDocument d,NoteDocument.Atom a,float height) throws Exception {
        Form f=new Form();char ch=a.text.charAt(0);float size=d.page.fontSize/100f;
        if(invisible(a.text))return f;
        if(unavailable.contains(a.id)){f.width=height*size;f.path=new e();return f;}
        if(punctuationFallback.contains(a.id)) {
            boolean period=ch=='.'||ch=='．';
            float unit=height*size;f.path=new e();f.width=unit*(ch=='．'||ch=='…'||ch=='—'?1f:ch=='.'?.35f:.5f);
            // Sentence periods touch the baseline; existing middle dots stay raised.
            float y=-unit*(period?.03f:.3f);
            if("—–−".indexOf(ch)>=0){f.path.a(unit*.05f,y);f.path.b(f.width-unit*.05f,y);}
            else {int count=ch=='…'?3:1;float radius=unit*(ch=='•'?.065f:period?.03f:.025f);for(int n=0;n<count;n++){float cx=ch=='．'?unit*.25f:f.width*(n+.5f)/count;for(int k=0;k<=12;k++){double angle=k*Math.PI/6;float x=cx+radius*(float)Math.cos(angle),py=y+radius*(float)Math.sin(angle);if(k==0)f.path.a(x,py);else f.path.b(x,py);}}}
            return f;
        }
        if(ch=='\t'){f.width=height*size*0.3f+px(d.shapes.tabWidthAdd);return f;}
        if(Character.isWhitespace(ch)||Character.isSpaceChar(ch)){f.width=height*size*0.3f+px(d.shapes.spaceWidthAdd);return f;}
        f.font=a.override!=null?a.override:a.automatic;v font;
        try{font=fonts.resolve(f.font).font();f.path=font.b(fontCharacter(font,ch)).a().e();}
        catch(Exception fontFailure){unavailable.add(a.id);f.width=height*size;f.path=new e();return f;}
        float scale=height/font.d*a.size*size;f.path.d(scale*a.scaleX,scale*a.scaleY);f.path.a(a.rotate,0,0);
        q bounds=f.path.f();f.shift=Math.max(0,-bounds.a);f.width=bounds.a()+Math.max(0,bounds.a);
        if(!finite(f.width)||f.width<0)throw new IOException("字形尺寸无效："+a.text);
        if(f.width==0)f.width=Math.max(2,height*size*.3f);
        if(d.shapes.chars!=null&&d.shapes.chars.indexOf(ch)>=0){f.shift+=px(d.shapes.widthAdd);f.width+=px(d.shapes.widthAdd);}
        if(d.shapes.simple>0)f.path.a(d.shapes.simple);
        return f;
    }
    private interface Forms {Form get(NoteDocument.Atom atom,float height)throws Exception;}
    public NoteLayout build(final NoteDocument d) throws Exception {
        prepare(d);
        return layout(d,new Forms(){public Form get(NoteDocument.Atom atom,float height)throws Exception{
            Form f=form(d,atom,height);f.missing=unavailable.contains(atom.id);f.fallback=punctuationFallback.contains(atom.id);
            if(f.path!=null)f.path.c(f.shift,height*.75f*d.shapes.baseLine+atom.y*height);
            return f;
        }},false);
    }
    static String shapeKey(NoteDocument.Atom a){
        NoteDocument.FontRef f=a.override!=null?a.override:a.automatic;
        return a.text+"/"+(f==null?"":f.hash)+"/"+a.sampled+"/"+a.size+"/"+a.scaleX+"/"+a.scaleY+"/"+a.rotate+"/"+a.y+"/"+a.space+"/"+a.inputGeneration+"/"+Arrays.toString(a.inputFonts)+"/"+Arrays.toString(a.inputChances);
    }
    private String geometryKey(NoteDocument d,float height){
        NoteDocument.ShapeSettings s=d.shapes;
        return dpi+"/"+height+"/"+d.page.fontSize+"/"+s.baseLine+"/"+s.widthAdd+"/"+s.spaceWidthAdd+"/"+s.tabWidthAdd+"/"+s.simple+"/"+s.chars;
    }
    /** No font catalogue, font file or global settings access on the immediate input path. */
    NoteLayout approximate(final NoteDocument d,final NoteLayout previous) {
        final Map<Long,NoteLayout.Glyph> cached=new HashMap<Long,NoteLayout.Glyph>();
        if(previous!=null)for(NoteLayout.Page page:previous.pages)for(NoteLayout.Glyph glyph:page.glyphs)cached.put(glyph.id,glyph);
        try{return layout(d,new Forms(){public Form get(NoteDocument.Atom atom,float height){
            Form f=new Form();float size=height*d.page.fontSize/100f;char ch=atom.text.charAt(0);
            if(invisible(atom.text))return f;
            if(atom.text.equals("\t")){f.width=size*.3f+px(d.shapes.tabWidthAdd);return f;}
            if(atom.text.length()==1&&(Character.isWhitespace(ch)||Character.isSpaceChar(ch))){f.width=size*.3f+px(d.shapes.spaceWidthAdd);return f;}
            NoteLayout.Glyph old=cached.get(atom.id);
            if(old!=null&&!old.pending&&previous.geometryKey.equals(geometryKey(d,height))&&old.shapeKey.equals(shapeKey(atom))){
                f.width=old.layoutWidth;f.path=old.path.e();f.path.c(-old.cell.x,-old.cell.y);f.font=old.font;f.missing=old.missing;f.fallback=previous.fallback.contains(atom.text);return f;
            }
            f.pending=true;int cp=atom.text.codePointAt(0);f.width=Math.max(2,size*(cp<128?.52f:1f));return f;
        }},true);}catch(Exception error){throw new IllegalArgumentException("无法生成编辑布局："+error.getMessage(),error);}
    }
    private static NoteLayout.Line line(NoteLayout.Page page,int number,float left,float top,float width,float height,float gap,int start){
        NoteLayout.Line line=new NoteLayout.Line(number,left,top+number*(height+gap),width,height);
        line.start=line.end=line.contentEnd=start;page.lines.add(line);return line;
    }
    private static void stop(NoteLayout.Line line,int offset,float x){
        if(!line.carets.isEmpty()){NoteLayout.CaretStop last=line.carets.get(line.carets.size()-1);if(last.offset==offset){line.carets.set(line.carets.size()-1,new NoteLayout.CaretStop(offset,x));return;}}
        line.carets.add(new NoteLayout.CaretStop(offset,x));
    }
    private static NoteLayout.Page page(NoteLayout result,int offset){NoteLayout.Page page=new NoteLayout.Page();page.start=page.end=offset;result.pages.add(page);return page;}
    private NoteLayout layout(NoteDocument d,Forms provider,boolean approximate)throws Exception {
        float left=px(d.page.leftGap),top=px(d.page.topGap),right=px(d.page.pageWidth-d.page.rightGap),bottom=px(d.page.pageHeight-d.page.bottomGap);
        if(!finite(dpi)||dpi<=0||!finite(left)||!finite(top)||!finite(right)||!finite(bottom)||left<0||top<0||right<=left||bottom<=top||!finite(d.page.fontSize)||d.page.fontSize<=0)
            throw new IOException("页面尺寸、边距或字体大小无效");
        int lines=d.page.lineNum;float height=lines>0?(bottom-top)/lines:px(d.page.lineGap),gap=lines>0?0:px(d.page.lineGapOffset);
        if(!finite(height)||!finite(gap)||height<=0||height+gap<=0)throw new IOException("行距无效");
        if(lines<=0)lines=(int)((bottom-top)/(height+gap));if(lines<=0)throw new IOException("页面容不下一行文字");
        NoteLayout result=new NoteLayout();result.pageWidth=px(d.page.pageWidth);result.pageHeight=px(d.page.pageHeight);result.textLength=d.text.length();result.approximate=approximate;result.generation=d.generation;result.geometryKey=geometryKey(d,height);
        // Each atom has one local form. Looking ahead for alignment/punctuation must not resolve fonts repeatedly.
        Form[] forms=new Form[d.atoms.size()];
        for(int i=0;i<forms.length;i++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("排版已取消");
            NoteDocument.Atom atom=d.atoms.get(i);char ch=atom.text.charAt(0);
            forms[i]=atom.text.equals("\n")||atom.text.equals("\f")?new Form():provider.get(atom,height);
            if(forms[i].missing)result.missing.add(atom.text);if(forms[i].fallback)result.fallback.add(atom.text);
        }
        NoteLayout.Page page=page(result,0);int row=0,offset=0;float x=left;boolean paragraph=true,first=true;
        NoteLayout.Line line=line(page,0,left,top,right-left,height,gap,0);stop(line,0,left);
        String punctuation=",，;；.。!！?？>》}]】)）、…”°%：:";
        for(int i=0;i<d.atoms.size();i++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("排版已取消");
            NoteDocument.Atom atom=d.atoms.get(i);char ch=atom.text.charAt(0);int end=offset+atom.text.length();Form f=forms[i];
            if(atom.text.equals("\n")||atom.text.equals("\f")) {
                NoteLayout.Glyph glyph=new NoteLayout.Glyph(atom.id,atom.text,row,new Rectangle2D.Float(x,line.bounds.y,2,height),new e(),null);
                glyph.start=offset;glyph.end=end;glyph.whitespace=true;glyph.replacementMarked=atom.replacementMarked;glyph.shapeKey=shapeKey(atom);page.glyphs.add(glyph);
                stop(line,offset,x);line.end=end;line.contentEnd=offset;line.hardBreak=true;line.pageBreak=ch=='\f';page.end=end;
                row++;if(ch=='\f'||row>=lines){page=page(result,end);row=0;}
                line=line(page,row,left,top,right-left,height,gap,end);x=left;stop(line,end,x);paragraph=true;first=false;offset=end;continue;
            }
            if(paragraph) {
                x=left+(first?0:lineOffset(d,atom.id));
                if(first && d.format.firstLineAlign!=null) {
                    String align=d.format.firstLineAlign.name();
                    if(align.equals("CENTER")||align.equals("RIGHT")) {
                        float width=0;for(int j=i;j<d.atoms.size()&&!d.atoms.get(j).text.equals("\n")&&!d.atoms.get(j).text.equals("\f");j++){NoteDocument.Atom a=d.atoms.get(j);width+=forms[j].width+(invisible(a.text)?0:px(d.page.space)*a.space);if(width>right-left)break;}
                        if(width<=right-left)x+=align.equals("CENTER")?(right-left-width)/2:(right-left-width);
                    } else if(align.equals("TAB"))x+=d.format.gpNum*(height*d.page.fontSize/100f*.3f+px(d.shapes.tabWidthAdd));
                } else if(d.format.autoGp)x+=d.format.gpNum*(height*d.page.fontSize/100f*.3f+px(d.shapes.tabWidthAdd));
                paragraph=false;first=false;stop(line,offset,x);
            }
            if(!finite(f.width)||f.width<0)throw new IOException("字形宽度无效："+atom.text);
            if(f.width>right-left){if(approximate&&f.pending)f.width=right-left;else throw new IOException("单字宽于可用行宽："+atom.text+"。请减小字号或调整页面。");}
            float groupWidth=f.width;
            for(int j=i+1;j<d.atoms.size()&&punctuation.indexOf(d.atoms.get(j).text.charAt(0))>=0;j++){float next=px(d.page.space)*d.atoms.get(j-1).space+forms[j].width;if(groupWidth+next>right-left)break;groupWidth+=next;}
            // Do not manufacture an empty line when indentation alone overflows the first atom.
            if(x+groupWidth>right&&line.contentEnd>line.start) {
                stop(line,offset,x);line.end=line.contentEnd=offset;line.softBreak=true;page.end=offset;
                row++;if(row>=lines){page=page(result,offset);row=0;}
                line=line(page,row,left,top,right-left,height,gap,offset);x=left+lineOffset(d,atom.id);stop(line,offset,x);
            }
            if(x+f.width>right)x=left;
            stop(line,offset,x);
            e path=f.path==null?new e():f.path.e();if(f.path!=null)path.c(x,line.bounds.y);
            float advance=f.width+(invisible(atom.text)?0:px(d.page.space)*atom.space);
            NoteLayout.Glyph glyph=new NoteLayout.Glyph(atom.id,atom.text,row,new Rectangle2D.Float(x,line.bounds.y,Math.max(f.width,2),height),path,f.font);
            glyph.start=offset;glyph.end=end;glyph.advance=advance;glyph.layoutWidth=f.width;glyph.pending=f.pending;glyph.missing=f.missing;
            glyph.whitespace=invisible(atom.text)||(atom.text.length()==1&&(Character.isWhitespace(ch)||Character.isSpaceChar(ch)));glyph.replacementMarked=atom.replacementMarked;glyph.shapeKey=shapeKey(atom);
            page.glyphs.add(glyph);if(f.path!=null&&!f.missing&&!f.pending)page.paths.add(path);
            if(!glyph.whitespace)line.ids.add(atom.id);
            x+=advance;stop(line,end,x);line.end=line.contentEnd=end;page.end=end;offset=end;
        }
        return result;
    }
}
