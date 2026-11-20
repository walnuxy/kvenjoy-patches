package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.fonts.v;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.n.q;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;

/** Note-specific layout. Shared legacy g.a state is deliberately not used. */
public final class NoteLayoutEngine {
    private static final float GEOMETRY_EPSILON=1.0e-3f;
    private static final String EFFECT_LAYOUT_VERSION="effect-fit-v5";
    private static final int NOISE_FIT_ITERATIONS=22;
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
            boolean managed=managed(NoteAppearance.effective(d,a));
            if(!a.sampled&&!managed) {
                a.size=sample(r,d.shapes.randomFontSize);a.scaleX=sample(r,d.shapes.randomXScale);a.scaleY=sample(r,d.shapes.randomYScale);
                a.rotate=sample(r,d.shapes.randomRotate);a.space=sample(r,d.shapes.randomSpace);a.y=(r.nextFloat()*2-1)*d.shapes.randomY;a.sampled=true;
            }
            if(!managed&&(!finite(a.size)||!finite(a.scaleX)||!finite(a.scaleY)||!finite(a.rotate)||!finite(a.space)||!finite(a.y)||a.size<=0||a.scaleX<=0||a.scaleY<=0||a.space<0))
                throw new IOException("字符随机尺寸或间距无效："+a.text);
        }
    }
    private static boolean managed(NoteAppearance.Config c){return c!=null&&(c.normalizeMode!=0||c.randomMode!=0);}
    private static boolean visible(NoteDocument.Atom a){return a!=null&&!invisible(a.text)&&!(a.text.length()==1&&(Character.isWhitespace(a.text.charAt(0))||Character.isSpaceChar(a.text.charAt(0))));}
    private static final class Form {
        e path,rawPath;float width,shift;float minX,minY,maxX,maxY,anchorY,dx,dy,gapAfter,minGap,widthExtra;
        float referenceHeight,lineHeight,baseLine,sizeFraction,angleDegrees,simpleTolerance,baseFitScale=1f,baseFitShiftY;
        v effectFont;char effectChar;NoteAppearance.Config effectConfig;
        NoteDocument.FontRef font;boolean pending,missing,fallback,effectManaged,effectPlacedLocal,baseFitReady;
    }
    private Form form(NoteDocument d,NoteDocument.Atom a,float height) throws Exception {
        NoteAppearance.Config config=NoteAppearance.effective(d,a);
        if(managed(config))return effectForm(d,a,height,config);
        return legacyForm(d,a,height);
    }
    private Form legacyForm(NoteDocument d,NoteDocument.Atom a,float height) throws Exception {
        Form f=new Form();char ch=a.text.charAt(0);float size=d.page.fontSize/100f;
        if(invisible(a.text))return f;
        if(unavailable.contains(a.id)){f.width=height*size;f.path=new e();return f;}
        if(punctuationFallback.contains(a.id)) {
            Form vector=vectorFallback(ch,height*size);f.path=vector.path;f.width=vector.width;
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
    private Form effectForm(NoteDocument d,NoteDocument.Atom a,float height,NoteAppearance.Config config) throws Exception {
        Form f=new Form();f.effectManaged=true;char ch=a.text.charAt(0);float unit=height*d.page.fontSize/100f;
        float[] change=NoteAppearance.values(d,a,config);float randomScale=Math.max(.01f,1f+change[0]);
        f.dx=change[2]*unit;f.dy=change[3]*unit;
        f.minGap=Math.max(0f,px(config.minGapMm));
        if(invisible(a.text))return f;
        if(a.text.length()!=1){f.missing=unavailable.contains(a.id);f.width=unit*randomScale;f.gapAfter=effectGap(d,config,change,unit);return f;}
        if(ch=='\t'){f.width=(unit*.3f+px(d.shapes.tabWidthAdd))*randomScale;return f;}
        if(Character.isWhitespace(ch)||Character.isSpaceChar(ch)){f.width=(unit*.3f+px(d.shapes.spaceWidthAdd))*randomScale;return f;}
        e raw=null;v font=null;NoteDocument.FontRef chosen=a.override!=null?a.override:a.automatic;f.font=chosen;
        if(unavailable.contains(a.id)){
            f.missing=true;f.width=unit*randomScale;f.gapAfter=effectGap(d,config,change,unit);
        }else if(punctuationFallback.contains(a.id)){
            Form vector=vectorFallback(ch,unit);raw=vector.path;f.width=vector.width;
            f.fallback=true;f.font=null;
        }else {
            try{font=fonts.resolve(chosen).font();char resolved=fontCharacter(font,ch);raw=font.b(resolved).a().e();f.effectChar=resolved;}
            catch(Exception fontFailure){unavailable.add(a.id);f.missing=true;f.width=unit*randomScale;f.gapAfter=effectGap(d,config,change,unit);return f;}
        }
        if(raw==null||raw.c()==0){unavailable.add(a.id);f.missing=true;f.width=unit*randomScale;f.gapAfter=effectGap(d,config,change,unit);return f;}
        f.rawPath=raw;f.effectFont=font;f.effectConfig=NoteAppearance.copy(config);if(font==null)f.effectChar=ch;f.referenceHeight=unit;f.lineHeight=height;f.baseLine=d.shapes.baseLine;f.sizeFraction=change[0];f.angleDegrees=change[4];f.simpleTolerance=d.shapes.simple;
        f.widthExtra=d.shapes.chars!=null&&d.shapes.chars.indexOf(ch)>=0?px(d.shapes.widthAdd):0f;
        refreshEffectGeometry(f,1f);
        f.gapAfter=effectGap(d,config,change,unit);
        if(!finite(f.width)||f.width<0f)throw new IOException("字形尺寸无效："+a.text);
        return f;
    }
    private static void refreshEffectGeometry(Form f,float noiseFactor){
        NoteGlyphEffects.Geometry g;
        float size=f.sizeFraction*noiseFactor,angle=f.angleDegrees*noiseFactor;
        if(f.effectFont==null)g=NoteGlyphEffects.randomGeometry(f.rawPath,f.lineHeight,f.referenceHeight,f.baseLine,size,angle);
        else g=NoteGlyphEffects.geometry(f.font==null?null:f.font.hash,
            f.effectFont,f.effectChar,f.rawPath,f.lineHeight,f.referenceHeight,f.baseLine,
            f.effectConfig.normalizeMode,f.effectConfig.normalization,size,angle,f.effectConfig.latinSize,f.effectConfig.numberSize);
        f.path=g.path;f.anchorY=g.anchorY;
        if(f.path!=null&&f.simpleTolerance>0f)f.path.a(f.simpleTolerance);
        if(f.baseFitReady)f.path=NoteGlyphEffects.affine(f.path,f.baseFitScale,0f,0f,f.baseFitShiftY);
        Rectangle2D.Float b=NoteGlyphEffects.bounds(f.path);f.minX=b.x;f.minY=b.y;f.maxX=b.x+b.width;f.maxY=b.y+b.height;
        f.shift=Math.max(0f,-f.minX)+f.widthExtra;f.width=b.width+Math.max(0f,f.minX)+f.widthExtra;
        if(f.width<=0f&&f.path!=null&&f.path.c()>0)f.width=Math.max(2f,f.referenceHeight*.3f);
    }
    private static float fitInset(float width,float height,float maximum){
        return Math.max(0f,Math.min(width*.001f,height*maximum));
    }
    private static boolean baseGeometryFits(Form f,float width,float inset){
        if(!hasInk(f))return true;
        float epsilon=Math.max(1.0e-7f,Math.abs(f.lineHeight)*1.0e-7f);
        return f.shift+f.maxX<=width-inset+epsilon&&f.anchorY+f.minY>=inset-epsilon&&f.anchorY+f.maxY<=f.lineHeight-inset+epsilon;
    }
    private static float reduceNoise(float requested,float minimum,float maximum,String character)throws IOException {
        float low=Math.max(minimum,Math.min(0f,requested));
        float high=Math.min(maximum,Math.max(0f,requested));
        if(low>high) {
            if(low-high<=GEOMETRY_EPSILON)return 0f;
            throw new IOException("字形本身超出页面或行边界："+character+"（不会裁切笔画；requested="+requested+", min="+minimum+", max="+maximum+")");
        }
        return Math.max(low,Math.min(high,requested));
    }
    private static void fitNoiseGeometry(Form f,float availableWidth)throws IOException {
        float inset=fitInset(availableWidth,f.lineHeight,.0002f);
        if(f.effectPlacedLocal||!hasInk(f)||baseGeometryFits(f,availableWidth,inset))return;
        float requested=f.sizeFraction,rotation=f.angleDegrees;
        refreshEffectGeometry(f,0f);
        if(!baseGeometryFits(f,availableWidth,inset)){
            refreshEffectGeometry(f,1f);
            throw new IOException("字形本身超出行高或行宽："+f.effectChar+"（不会裁切笔画）");
        }
        float low=0f,high=1f;
        for(int i=0;i<NOISE_FIT_ITERATIONS;i++){
            float mid=(low+high)*.5f;refreshEffectGeometry(f,mid);
            if(baseGeometryFits(f,availableWidth,inset))low=mid;else high=mid;
        }
        refreshEffectGeometry(f,low);f.sizeFraction=requested;f.angleDegrees=rotation;
    }
    private static void fitBaseGeometry(Form f,float availableWidth)throws IOException {
        if(f.effectPlacedLocal||!hasInk(f)||f.baseFitReady)return;
        float requestedSize=f.sizeFraction,requestedAngle=f.angleDegrees;
        refreshEffectGeometry(f,0f);
        float inset=fitInset(availableWidth,f.lineHeight,.001f);
        float shapeWidth=Math.max(.001f,f.width-f.widthExtra),shapeRoom=availableWidth-f.widthExtra-2f*inset;
        if(shapeRoom<=0f)throw new IOException("字形留白设置超过可用行宽："+f.effectChar);
        float inkHeight=Math.max(0f,f.maxY-f.minY),verticalRoom=Math.max(0f,f.lineHeight-2f*inset),factor=1f;
        if(shapeWidth>shapeRoom)factor=Math.min(factor,shapeRoom/shapeWidth);
        if(inkHeight>verticalRoom&&inkHeight>0f)factor=Math.min(factor,verticalRoom/inkHeight);
        float fittedMin=f.anchorY+factor*f.minY,fittedMax=f.anchorY+factor*f.maxY,shiftY=0f;
        if(fittedMin<inset)shiftY=inset-fittedMin;
        else if(fittedMax>f.lineHeight-inset)shiftY=f.lineHeight-inset-fittedMax;
        if(factor<=0f||!Float.isFinite(factor)||!Float.isFinite(shiftY))throw new IOException("字形本身无法适配行框："+f.effectChar);
        f.baseFitScale=factor;f.baseFitShiftY=shiftY;f.baseFitReady=true;f.sizeFraction=requestedSize;f.angleDegrees=requestedAngle;
        // First prove the deterministic fitted basis itself is valid. Requested size/rotation
        // jitter is handled independently by fitNoiseGeometry(), which can attenuate it.
        refreshEffectGeometry(f,0f);
        if(!baseGeometryFits(f,availableWidth,inset))throw new IOException("字形本身无法适配页面或行框："+f.effectChar+"（未裁切笔画）");
        refreshEffectGeometry(f,1f);
    }
    private float effectGap(NoteDocument d,NoteAppearance.Config c,float[] change,float unit){
        return Math.max(Math.max(0f,px(c.minGapMm)),Math.max(0f,px(d.page.space))+change[1]*unit);
    }
    private static Form vectorFallback(char ch,float unit){
        Form f=new Form();boolean period=ch=='.'||ch=='．';f.path=new e();f.width=unit*(ch=='．'||ch=='…'||ch=='—'?1f:ch=='.'?.35f:.5f);
        float y=-unit*(period?.03f:.3f);
        if("—–−".indexOf(ch)>=0){f.path.a(unit*.05f,y);f.path.b(f.width-unit*.05f,y);}
        else {int count=ch=='…'?3:1;float radius=unit*(ch=='•'?.065f:period?.03f:.025f);for(int n=0;n<count;n++){float cx=ch=='．'?unit*.25f:f.width*(n+.5f)/count;for(int k=0;k<=12;k++){double angle=k*Math.PI/6;float x=cx+radius*(float)Math.cos(angle),py=y+radius*(float)Math.sin(angle);if(k==0)f.path.a(x,py);else f.path.b(x,py);}}}
        return f;
    }
    private float gapAfter(NoteDocument d,NoteDocument.Atom a,Form f){
        return f.effectManaged?f.gapAfter:(invisible(a.text)?0f:px(d.page.space)*a.space);
    }
    private static float rightExtent(Form f){return f.effectManaged?f.shift+f.maxX+Math.max(0f,f.dx):f.width;}
    private static boolean hasInk(Form f){return f!=null&&f.path!=null&&f.path.c()>0;}
    private interface Forms {Form get(NoteDocument.Atom atom,float height)throws Exception;}
    public NoteLayout build(final NoteDocument d) throws Exception {
        prepare(d);
        return layout(d,new Forms(){public Form get(NoteDocument.Atom atom,float height)throws Exception{
            Form f=form(d,atom,height);f.missing=unavailable.contains(atom.id);f.fallback=punctuationFallback.contains(atom.id);
            if(f.path!=null&&!f.effectManaged)f.path.c(f.shift,height*.75f*d.shapes.baseLine+atom.y*height);
            return f;
        }},false);
    }
    static String shapeKey(NoteDocument d,NoteDocument.Atom a){
        NoteDocument.FontRef f=a.override!=null?a.override:a.automatic;
        return a.text+"/"+(f==null?"":f.hash)+"/"+a.sampled+"/"+a.size+"/"+a.scaleX+"/"+a.scaleY+"/"+a.rotate+"/"+a.y+"/"+a.space+"/"+a.inputGeneration+"/"+Arrays.toString(a.inputFonts)+"/"+Arrays.toString(a.inputChances)+"/"+NoteAppearance.key(d,a);
    }
    private String geometryKey(NoteDocument d,float height){
        NoteDocument.ShapeSettings s=d.shapes;
        return dpi+"/"+height+"/"+d.page.pageWidth+"/"+d.page.pageHeight+"/"+d.page.leftGap+"/"+d.page.rightGap+"/"+d.page.topGap+"/"+d.page.bottomGap+"/"+d.page.lineNum+"/"+d.page.lineGap+"/"+d.page.lineGapOffset+"/"+d.page.fontSize+"/"+s.baseLine+"/"+s.widthAdd+"/"+s.spaceWidthAdd+"/"+s.tabWidthAdd+"/"+s.simple+"/"+s.chars+"/"+NoteAppearance.key(d.appearance)+"/"+(d.forbiddenZones==null?"null":d.forbiddenZones.key())+"/"+EFFECT_LAYOUT_VERSION+"/"+NoteGlyphEffects.ALGORITHM_VERSION+"/"+NoteGlyphEffects.referenceFingerprint();
    }
    /** No font catalogue, font file or global settings access on the immediate input path. */
    NoteLayout approximate(final NoteDocument d,final NoteLayout previous) {
        final Map<Long,NoteLayout.Glyph> cached=new HashMap<Long,NoteLayout.Glyph>();
        if(previous!=null)for(NoteLayout.Page page:previous.pages)for(NoteLayout.Glyph glyph:page.glyphs)cached.put(glyph.id,glyph);
        try{return layout(d,new Forms(){public Form get(NoteDocument.Atom atom,float height){
            Form f=new Form();float size=height*d.page.fontSize/100f;char ch=atom.text.charAt(0);NoteAppearance.Config config=NoteAppearance.effective(d,atom);f.effectManaged=managed(config);
            float[] change=NoteAppearance.values(d,atom,config);float randomScale=Math.max(.01f,1f+change[0]);f.dx=change[2]*height*d.page.fontSize/100f;f.dy=change[3]*height*d.page.fontSize/100f;f.gapAfter=f.effectManaged?Math.max(Math.max(0f,px(config.minGapMm)),Math.max(0f,px(d.page.space))+change[1]*height*d.page.fontSize/100f):0f;f.minGap=f.effectManaged?Math.max(0f,px(config.minGapMm)):0f;
            if(invisible(atom.text))return f;
            if(atom.text.equals("\t")){f.width=(size*.3f+px(d.shapes.tabWidthAdd))*randomScale;return f;}
            if(atom.text.length()==1&&(Character.isWhitespace(ch)||Character.isSpaceChar(ch))){f.width=(size*.3f+px(d.shapes.spaceWidthAdd))*randomScale;return f;}
            NoteLayout.Glyph old=cached.get(atom.id);
            if(old!=null&&!old.pending&&previous.geometryKey.equals(geometryKey(d,height))&&old.shapeKey.equals(shapeKey(d,atom))){
                f.width=old.layoutWidth;f.path=old.path.e();f.font=old.font;f.missing=old.missing;f.fallback=previous.fallback.contains(atom.text);
                if(f.effectManaged){f.path=NoteGlyphEffects.translated(f.path,-old.cell.x,-old.cell.y);f.effectPlacedLocal=true;Rectangle2D.Float b=NoteGlyphEffects.bounds(f.path);f.minX=b.x;f.maxX=b.x+b.width;f.minY=b.y;f.maxY=b.y+b.height;f.shift=0f;f.dx=f.dy=f.anchorY=0f;}
                else f.path.c(-old.cell.x,-old.cell.y);
                return f;
            }
            f.pending=true;int cp=atom.text.codePointAt(0);f.width=Math.max(2,size*(cp<128?.52f:1f))*randomScale;return f;
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
    private static final class RowCursor {
        NoteLayout.Page page;NoteLayout.Line line;int pageNumber,row,intervalIndex,probeAtomIndex=-1,probePageLimit;
        List<NoteForbiddenZoneGeometry.Interval> intervals;float x,lineInkRight=Float.NEGATIVE_INFINITY;boolean positioned;
    }
    private List<NoteForbiddenZoneGeometry.Interval> rowIntervals(NoteDocument d,int pageNumber,int row,float top,float height,float gap,float left,float right){
        return NoteForbiddenZoneGeometry.availableIntervals(d,pageNumber,top+row*(height+gap),height,left,right,dpi);
    }
    /** Legacy offsets may put ink outside its nominal row; test that actual band before placing it. */
    private float legacyPlacementX(NoteDocument d,RowCursor cursor,Rectangle2D.Float ink,NoteForbiddenZoneGeometry.Interval interval,float candidateX,float width){
        for(NoteForbiddenZoneGeometry.Interval free:NoteForbiddenZoneGeometry.availableIntervals(d,cursor.pageNumber,cursor.line.bounds.y+ink.y,Math.max(.001f,ink.height),interval.left,interval.right,dpi)){
            float x=Math.max(candidateX,free.left-ink.x);
            if(x+width<=interval.right+GEOMETRY_EPSILON&&x+ink.x+ink.width<=free.right)return x;
        }
        return Float.NaN;
    }
    private boolean pageHasUsableRow(NoteDocument d,int pageNumber,int lines,float top,float height,float gap,float left,float right){
        for(int row=0;row<lines;row++)if(!rowIntervals(d,pageNumber,row,top,height,gap,left,right).isEmpty())return true;
        return false;
    }
    private static boolean hasRepeatingRule(NoteForbiddenZones zones){
        if(zones==null||zones.rules==null)return false;
        for(NoteForbiddenZones.Rule rule:zones.rules)if(rule!=null&&rule.pageMode!=NoteForbiddenZones.PageMode.CUSTOM)return true;
        return false;
    }
    private static int lastPageToProbe(int currentPage,int lastExplicitPage,boolean repeatingRules){
        long base=Math.max(currentPage,lastExplicitPage),limit=base+(repeatingRules?2L:1L);
        return (int)Math.min(NoteForbiddenZones.MAX_PAGE_NUMBER,limit);
    }
    private void ensurePageMayBeUsed(NoteDocument d,int pageNumber,int lines,float top,float height,float gap,float left,float right)throws IOException{
        if(pageHasUsableRow(d,pageNumber,lines,top,height,gap,left,right))return;
        int last=NoteForbiddenZoneGeometry.lastExplicitPage(d.forbiddenZones);
        if(pageNumber<=last||!hasRepeatingRule(d.forbiddenZones))return;
        for(int next=pageNumber+1;next<=Math.min(NoteForbiddenZones.MAX_PAGE_NUMBER,pageNumber+2);next++)
            if(pageHasUsableRow(d,next,lines,top,height,gap,left,right))return;
        throw new IOException("禁写区覆盖了连续页面的全部可写行，无法继续排版；请缩小禁写区或调整页面规则。");
    }
    private RowCursor startRow(NoteDocument d,NoteLayout result,NoteLayout.Page page,int pageNumber,int row,int offset,int lines,float left,float top,float right,float height,float gap)throws IOException{
        while(true){
            if(pageNumber<=0||pageNumber>NoteForbiddenZones.MAX_PAGE_NUMBER)throw new IOException("禁写区排版超过最大页面数");
            List<NoteForbiddenZoneGeometry.Interval> intervals=rowIntervals(d,pageNumber,row,top,height,gap,left,right);
            NoteLayout.Line current=line(page,row,left,top,right-left,height,gap,offset);
            if(!intervals.isEmpty()){
                RowCursor cursor=new RowCursor();cursor.page=page;cursor.line=current;cursor.pageNumber=pageNumber;cursor.row=row;cursor.intervals=intervals;cursor.x=intervals.get(0).left;stop(current,offset,cursor.x);return cursor;
            }
            current.start=current.end=current.contentEnd=offset;current.softBreak=true;page.end=offset;stop(current,offset,left);
            row++;
            if(row>=lines){
                page.end=offset;pageNumber++;if(pageNumber>NoteForbiddenZones.MAX_PAGE_NUMBER)throw new IOException("禁写区排版超过最大页面数");
                ensurePageMayBeUsed(d,pageNumber,lines,top,height,gap,left,right);page=page(result,offset);row=0;
            }
        }
    }
    private RowCursor nextRow(NoteDocument d,NoteLayout result,RowCursor current,int offset,boolean forcePage,int lines,float left,float top,float right,float height,float gap)throws IOException{
        current.line.end=current.line.contentEnd=offset;if(!current.line.hardBreak)current.line.softBreak=true;current.page.end=offset;
        int pageNumber=current.pageNumber,row=current.row+1;NoteLayout.Page page=current.page;
        if(forcePage||row>=lines){pageNumber++;if(pageNumber>NoteForbiddenZones.MAX_PAGE_NUMBER)throw new IOException("禁写区排版超过最大页面数");ensurePageMayBeUsed(d,pageNumber,lines,top,height,gap,left,right);page=page(result,offset);row=0;}
        return startRow(d,result,page,pageNumber,row,offset,lines,left,top,right,height,gap);
    }
    private float paragraphIndent(NoteDocument d,boolean first,NoteDocument.Atom atom,Form[] forms,int index,NoteForbiddenZoneGeometry.Interval interval,float height){
        if(first&&d.format.firstLineAlign!=null){
            String align=d.format.firstLineAlign.name();
            if(align.equals("CENTER")||align.equals("RIGHT")){
                float width=0;for(int j=index;j<d.atoms.size()&&!d.atoms.get(j).text.equals("\n")&&!d.atoms.get(j).text.equals("\f");j++){
                    NoteDocument.Atom a=d.atoms.get(j);width+=forms[j].width+gapAfter(d,a,forms[j]);if(width>interval.width())break;
                }
                if(width<=interval.width())return align.equals("CENTER")?(interval.width()-width)/2:(interval.width()-width);
            }else if(align.equals("TAB"))return d.format.gpNum*(height*d.page.fontSize/100f*.3f+px(d.shapes.tabWidthAdd));
        }else if(d.format.autoGp)return d.format.gpNum*(height*d.page.fontSize/100f*.3f+px(d.shapes.tabWidthAdd));
        return 0f;
    }
    private NoteLayout layout(NoteDocument d,Forms provider,boolean approximate)throws Exception {
        if(d==null||d.page==null||d.forbiddenZones==null)throw new IOException("缺少文章或禁写区状态");
        try{d.forbiddenZones.validate(d.page.pageWidth,d.page.pageHeight);}catch(IllegalArgumentException invalid){throw new IOException(invalid.getMessage(),invalid);}
        float left=px(d.page.leftGap),top=px(d.page.topGap),right=px(d.page.pageWidth-d.page.rightGap),bottom=px(d.page.pageHeight-d.page.bottomGap);
        if(!finite(dpi)||dpi<=0||!finite(left)||!finite(top)||!finite(right)||!finite(bottom)||left<0||top<0||right<=left||bottom<=top||!finite(d.page.fontSize)||d.page.fontSize<=0)
            throw new IOException("页面尺寸、边距或字体大小无效");
        int lines=d.page.lineNum;float height=lines>0?(bottom-top)/lines:px(d.page.lineGap),gap=lines>0?0:px(d.page.lineGapOffset);
        if(!finite(height)||!finite(gap)||height<=0||height+gap<=0||lines>10000)throw new IOException("行距或每页行数无效");
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
        ensurePageMayBeUsed(d,1,lines,top,height,gap,left,right);
        NoteLayout.Page page=page(result,0);int offset=0;boolean paragraph=true,first=true;
        int lastExplicitPage=NoteForbiddenZoneGeometry.lastExplicitPage(d.forbiddenZones);boolean repeatingRules=hasRepeatingRule(d.forbiddenZones);
        RowCursor cursor=startRow(d,result,page,1,0,0,lines,left,top,right,height,gap);
        page=cursor.page;
        String punctuation=",，;；.。!！?？>》}]】)）、…”°%：:";
        int splittablePunctuationEnd=-1;
        for(int i=0;i<d.atoms.size();i++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("排版已取消");
            NoteDocument.Atom atom=d.atoms.get(i);char ch=atom.text.charAt(0);int end=offset+atom.text.length();Form f=forms[i];
            if(atom.text.equals("\n")||atom.text.equals("\f")) {
                NoteLayout.Glyph glyph=new NoteLayout.Glyph(atom.id,atom.text,cursor.row,new Rectangle2D.Float(cursor.x,cursor.line.bounds.y,2,height),new e(),null);
                glyph.start=offset;glyph.end=end;glyph.whitespace=true;glyph.replacementMarked=atom.replacementMarked;glyph.shapeKey=shapeKey(d,atom);page.glyphs.add(glyph);
                stop(cursor.line,offset,cursor.x);cursor.line.end=end;cursor.line.contentEnd=offset;cursor.line.hardBreak=true;cursor.line.pageBreak=ch=='\f';page.end=end;
                cursor=nextRow(d,result,cursor,end,ch=='\f',lines,left,top,right,height,gap);page=cursor.page;paragraph=true;first=false;offset=end;continue;
            }
            float bodyWidth=right-left;
            if(f.effectManaged&&!f.effectPlacedLocal&&hasInk(f))fitBaseGeometry(f,bodyWidth);
            if(f.effectManaged&&!f.effectPlacedLocal&&hasInk(f))fitNoiseGeometry(f,bodyWidth);
            if(!finite(f.width)||f.width<0)throw new IOException("字形宽度无效："+atom.text);
            if(f.width>bodyWidth){if(approximate&&f.pending)f.width=bodyWidth;else throw new IOException("字符宽于页面可写宽度："+atom.text+"。请缩小字号或调整页面。");}
            float groupWidth=f.width;
            if(i>splittablePunctuationEnd){
                int groupEnd=i;
                for(int j=i+1;j<d.atoms.size()&&punctuation.indexOf(d.atoms.get(j).text.charAt(0))>=0;j++){
                    groupWidth+=gapAfter(d,d.atoms.get(j-1),forms[j-1])+forms[j].width;groupEnd=j;
                }
                // Keep ordinary punctuation with its anchor. An oversized run must wrap;
                // keep its tail splittable too, including across narrow forbidden intervals.
                if(groupWidth>bodyWidth){splittablePunctuationEnd=groupEnd;groupWidth=f.width;}
            }
            float currentWidth=Math.max(groupWidth,rightExtent(f));
            float minimumWidth=f.effectManaged&&!f.effectPlacedLocal&&hasInk(f)?Math.max(groupWidth,f.shift+f.maxX):currentWidth;
            if(minimumWidth>bodyWidth+GEOMETRY_EPSILON)throw new IOException("连续标点组宽于页面可写宽度："+atom.text+"。请调整禁写区或字号。");
            Rectangle2D.Float legacyInk=!f.effectManaged&&hasInk(f)&&d.forbiddenZones.hasData()?NoteGlyphEffects.bounds(f.path):null;
            float maxIntervalWidth=0f;for(int intervalIndex=cursor.intervalIndex;intervalIndex<cursor.intervals.size();intervalIndex++)maxIntervalWidth=Math.max(maxIntervalWidth,cursor.intervals.get(intervalIndex).width());
            int selected=-1;float selectedX=0f;
            // Keep the requested placement when it fits; only then try attenuating horizontal noise.
            for(int attempt=0;attempt<2&&selected<0;attempt++){
            if(attempt==1&&minimumWidth>=currentWidth)break;
            float placementWidth=attempt==0?currentWidth:minimumWidth;
            for(int intervalIndex=cursor.intervalIndex;placementWidth<=maxIntervalWidth+GEOMETRY_EPSILON&&intervalIndex<cursor.intervals.size();intervalIndex++){
                NoteForbiddenZoneGeometry.Interval interval=cursor.intervals.get(intervalIndex);
                float candidateX;
                if(paragraph)candidateX=interval.left+paragraphIndent(d,first,atom,forms,i,interval,height);
                else if(intervalIndex>cursor.intervalIndex)candidateX=interval.left;
                else candidateX=cursor.positioned?cursor.x:interval.left+lineOffset(d,atom.id);
                if(intervalIndex==cursor.intervalIndex&&f.effectManaged&&hasInk(f)&&Float.isFinite(cursor.lineInkRight))
                    candidateX=Math.max(candidateX,cursor.lineInkRight+f.minGap-(f.shift+f.minX+(attempt==0?f.dx:Math.min(0f,f.dx))));
                if(!cursor.positioned&&candidateX+placementWidth>interval.right+GEOMETRY_EPSILON&&candidateX>interval.left&&interval.left+placementWidth<=interval.right+GEOMETRY_EPSILON)
                    candidateX=interval.left;
                if(legacyInk!=null)candidateX=legacyPlacementX(d,cursor,legacyInk,interval,candidateX,placementWidth);
                if(candidateX+placementWidth<=interval.right+GEOMETRY_EPSILON){selected=intervalIndex;selectedX=candidateX;break;}
            }
            }
            if(selected<0){
                if(cursor.probeAtomIndex!=i){cursor.probeAtomIndex=i;cursor.probePageLimit=lastPageToProbe(cursor.pageNumber,lastExplicitPage,repeatingRules);}
                int probeLimit=cursor.probePageLimit;
                if(cursor.pageNumber>=probeLimit&&cursor.row>=lines-1)throw new IOException("字符或连续标点组无法放入任何后续可写行："+atom.text+"。请缩小禁写区或调整字号。");
                RowCursor next=nextRow(d,result,cursor,offset,false,lines,left,top,right,height,gap);
                if(next.pageNumber>probeLimit)throw new IOException("字符或连续标点组无法放入任何后续可写行："+atom.text+"。请缩小禁写区或调整字号。");
                next.probeAtomIndex=i;next.probePageLimit=probeLimit;cursor=next;page=cursor.page;
                i--;continue;
            }
            cursor.probeAtomIndex=-1;
            if(selected>cursor.intervalIndex){cursor.line.softBreak=true;cursor.intervalIndex=selected;cursor.lineInkRight=Float.NEGATIVE_INFINITY;cursor.x=cursor.intervals.get(selected).left;stop(cursor.line,offset,cursor.x);}
            cursor.x=selectedX;cursor.positioned=true;paragraph=false;first=false;stop(cursor.line,offset,cursor.x);
            e path=f.path==null?new e():f.path.e();float actualDx=0f,actualDy=0f;
            if(f.effectManaged&&f.path!=null) {
                if(f.effectPlacedLocal)path=NoteGlyphEffects.translated(f.path,cursor.x,cursor.line.bounds.y);
                else {
                    actualDx=f.dx;actualDy=f.dy;
                    if(hasInk(f)) {
                        NoteForbiddenZoneGeometry.Interval active=cursor.intervals.get(cursor.intervalIndex);
                        float minDx=active.left-(cursor.x+f.shift+f.minX),maxDx=active.right-(cursor.x+f.shift+f.maxX);
                        if(minDx>maxDx)throw new IOException("变换后的字形宽于可用行宽："+atom.text+"。请减小字号或调整页面。");
                        actualDx=reduceNoise(actualDx,minDx,maxDx,atom.text);
                        float topLimit=Math.max(top,cursor.line.bounds.y),bottomLimit=Math.min(bottom,cursor.line.bounds.y+height);
                        float minDy=topLimit-(cursor.line.bounds.y+f.anchorY+f.minY),maxDy=bottomLimit-(cursor.line.bounds.y+f.anchorY+f.maxY);
                        if(minDy>maxDy+GEOMETRY_EPSILON)throw new IOException("规整后的字形超出行高："+atom.text+"（不会裁切笔画；minDy="+minDy+", maxDy="+maxDy+")");
                        actualDy=reduceNoise(actualDy,minDy,maxDy,atom.text);
                    }
                    path=NoteGlyphEffects.translated(f.path,cursor.x+f.shift+actualDx,cursor.line.bounds.y+f.anchorY+actualDy);
                }
            } else if(f.path!=null)path.c(cursor.x,cursor.line.bounds.y);
            float spacing=gapAfter(d,atom,f);float advance=f.width+spacing;
            NoteLayout.Glyph glyph=new NoteLayout.Glyph(atom.id,atom.text,cursor.row,new Rectangle2D.Float(cursor.x,cursor.line.bounds.y,Math.max(f.width,2),height),path,f.font);
            glyph.start=offset;glyph.end=end;glyph.advance=advance;glyph.layoutWidth=f.width;glyph.pending=f.pending;glyph.missing=f.missing;glyph.inkBounds=NoteGlyphEffects.bounds(path);
            glyph.whitespace=invisible(atom.text)||(atom.text.length()==1&&(Character.isWhitespace(ch)||Character.isSpaceChar(ch)));glyph.replacementMarked=atom.replacementMarked;glyph.shapeKey=shapeKey(d,atom);
            page.glyphs.add(glyph);if(f.path!=null&&!f.missing&&!f.pending)page.paths.add(path);
            if(!glyph.whitespace)cursor.line.ids.add(atom.id);
            if(!glyph.whitespace&&path!=null&&path.c()>0)cursor.lineInkRight=Math.max(cursor.lineInkRight,glyph.inkBounds.x+glyph.inkBounds.width);
            cursor.x+=advance;stop(cursor.line,end,cursor.x);cursor.line.end=cursor.line.contentEnd=end;page.end=end;offset=end;
        }
        NoteForbiddenZoneGeometry.validateLayout(d,result,dpi);
        return result;
    }
}
