package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.fonts.a;
import com.kenjoy.kenjoycnc.fonts.v;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.n.q;
import java.awt.geom.Rectangle2D;
import java.security.MessageDigest;
import java.util.*;

/** Font-hash based glyph calibration and similarity transforms for note layout. */
final class NoteGlyphEffects {
    private NoteGlyphEffects() {}

    static final String ALGORITHM_VERSION="glyph-similarity-v7";
    private static final String ZH_REFERENCE="天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏山川河流水木金火土东西南北中大小多少上下左右永国明春风学书手写文字";
    private static final String CAP_REFERENCE="HEIFNLT";
    private static final String DIGIT_REFERENCE="0123456789";
    private static final String LOWER_REFERENCE="acemnorsuvwxz";
    private static final String ASCENDER_REFERENCE="bdhkl";
    private static final String DESCENDER_REFERENCE="gjpqy";
    private static final String REFERENCE_FONT_HASHES=
        "770ae29bb00990581809a15e703add4369a9e88f79762bf6a1a9ff62b55fedf7,"+
        "51e39d2325873b9e7b0393fd97b76dde8a25e68d7f90f5edabf580168dd0edeb,"+
        "591182f9ab6867186b10cc91c680fdec5e402dd1d91f6b1b011e91c912f4f6d2,"+
        "460a5240222950a86b655e5d76aa0a52bc74140d4a5277e75db27dac38dce694,"+
        "2ac146fce7bf9a2608365bbefb87ce51a5df92971b5e095fae763bc508248fae,"+
        "213f1cae44e1308448082213b71ac36a081352e0c765688555404143b000305c,"+
        "91075bf097af65f7f03567741ade6dd435c38297859b3afebee0fd8ee8d233de,"+
        "539071cd40d80defaa31a56f801d679ba709e53370cabe10a336b910aa8cc0c3,"+
        "0aa1e7403edd60639434de54d512679305ebaf0ae2a4cda19766c8f9cf7b1e3f,"+
        "ff1f0cd6d39bf5d73e76eda36767f1a1d833620129c91aebd7ff2568b9edc0e7";

    // Fixed targets are measured from the checked-in reference sample, never the active font pool.
    private static final float ZH_TARGET=.6166667f;
    private static final float CAP_TARGET=ZH_TARGET*.92f;
    private static final float DIGIT_TARGET=CAP_TARGET;
    private static final float LOWER_TARGET=CAP_TARGET*.68f;
    private static final float ASCENDER_TARGET=.6300000f;
    private static final float DESCENDER_TARGET=.6600000f;
    private static final String REFERENCE_FINGERPRINT=fingerprint(
        ALGORITHM_VERSION+"|"+ZH_REFERENCE+"|"+CAP_REFERENCE+"|"+DIGIT_REFERENCE+"|"+
        LOWER_REFERENCE+"|"+ASCENDER_REFERENCE+"|"+DESCENDER_REFERENCE+"|"+
        ZH_TARGET+"|"+CAP_TARGET+"|"+DIGIT_TARGET+"|"+LOWER_TARGET+"|"+
        ASCENDER_TARGET+"|"+DESCENDER_TARGET+"|"+REFERENCE_FONT_HASHES);
    private static final Map<String,Profile> PROFILES=new HashMap<String,Profile>();

    static String referenceFingerprint(){return REFERENCE_FINGERPRINT;}

    static final class Geometry {
        final e path;
        final float width,shift,anchorY;
        final float minX,minY,maxX,maxY;
        final boolean normalized;
        Geometry(e p,float w,float s,float y,float x0,float y0,float x1,float y1,boolean n){
            path=p;width=w;shift=s;anchorY=y;minX=x0;minY=y0;maxX=x1;maxY=y1;normalized=n;
        }
    }

    /**
     * Transform a copied raw glyph into line-reference pixels. The caller applies dx/dy
     * after choosing the advance cell, so later left-edge alignment cannot erase jitter.
     */
    static Geometry geometry(String fontHash,v font,char ch,e raw,float lineHeight,float referenceHeight,
                             float baseLine,int normalizeMode,float normalization,float sizeFraction,float angleDegrees) {
        return geometry(fontHash,font,ch,raw,lineHeight,referenceHeight,baseLine,normalizeMode,normalization,sizeFraction,angleDegrees,.80f,.80f);
    }
    static Geometry geometry(String fontHash,v font,char ch,e raw,float lineHeight,float referenceHeight,
                             float baseLine,int normalizeMode,float normalization,float sizeFraction,float angleDegrees,float latinSize,float numberSize) {
        if(raw==null||raw.c()==0)return empty(lineHeight,baseLine,false);
        float[] xy=raw.a();byte[] flags=raw.b();if(xy.length!=flags.length*2)return empty(lineHeight,baseLine,false);
        q bounds=raw.f();if(!validBounds(bounds))return empty(lineHeight,baseLine,false);
        boolean normalize=normalizeMode!=0&&normalization>0f;
        float sourceScale=font!=null&&font.d>0?referenceHeight/font.d:1f;
        float alpha=normalize?clamp(normalization,0f,1f):0f;
        Kind kind=kind(ch);
        float finalScaleFactor=1f,anchor=0f;
        Profile profile=font==null?null:profile(fontHash,font);
        // A decorative lowercase glyph can descend even when its character is not g/j/p/q/y.
        // Classify it against this font's fixed lowercase baseline profile, never the active font pool.
        if(kind==Kind.LOWER&&profile!=null&&profile.lower.hasScale&&font!=null&&font.d>0f&&
            (bounds.d-profile.lower.anchor)/font.d>.12f)kind=Kind.DESCENDER;
        boolean cjkFrame=kind==Kind.ZH;
        boolean calibratedFrame=false;
        if(alpha>0f&&profile!=null) {
            Group group=profile.group(kind,ch);
            if(group!=null&&group.hasScale) {
                if(kind==Kind.PUNCT) {
                    // Punctuation keeps its own font-relative size and vertical offset. Calibrate
                    // it against the chosen body group, never against its often tiny or flat ink box.
                    float target=punctuationTarget(group,profile);
                    if(font.d>0f&&Float.isFinite(target)&&group.medianFraction>0f) {
                        finalScaleFactor=target/group.medianFraction;
                        cjkFrame=group==profile.zh;
                        anchor=group.anchor;
                        calibratedFrame=true;
                    }
                } else if(kind==Kind.ZH) {
                    float glyphExtent=Math.max(bounds.a(),bounds.b());
                    if(glyphExtent>1.0e-6f&&font.d>0) {
                        float currentFraction=glyphExtent/font.d;
                        float target=target(kind);
                        finalScaleFactor=normalizeMode==2?target/currentFraction:target/group.medianFraction;
                        if(!Float.isFinite(finalScaleFactor)||finalScaleFactor<=0f)finalScaleFactor=1f;
                        anchor=normalizeMode==2?(bounds.b+bounds.d)*.5f:group.anchor;
                        calibratedFrame=true;
                    }
                } else if(kind==Kind.CAP||kind==Kind.DIGIT||kind==Kind.LOWER) {
                    // Strict size correction and its baseline anchor must use the same glyph.
                    // Mixing a glyph-specific scale with a font-median anchor moves the baseline.
                    float extent=normalizeMode==2?bounds.b()/font.d:group.medianFraction;
                    if(extent>1.0e-6f){finalScaleFactor=target(kind)/extent;anchor=normalizeMode==2?bounds.d:group.anchor;calibratedFrame=true;}
                } else if(kind==Kind.ASCENDER||kind==Kind.DESCENDER) {
                    // Preserve lowercase body size, ascenders and descenders with one x-height map.
                    Group body=profile.lower.hasScale?profile.lower:group;
                    finalScaleFactor=(body==profile.lower?LOWER_TARGET:target(kind))/body.medianFraction;
                    // Some handwriting fonts have unusually long stems/tails. Keep their shape,
                    // but bound total extent so body calibration cannot enlarge them excessively.
                    float extent=bounds.b()/font.d;
                    if(extent>1.0e-6f)finalScaleFactor=Math.min(finalScaleFactor,target(kind)/extent);
                    anchor=body.anchor;calibratedFrame=true;
                }
            }
        }
        boolean numericPunctuation=ch=='.'||ch==',';
        // ASCII numeric punctuation keeps its scale control even with a Chinese reference group.
        float scriptScale=calibratedFrame&&(!cjkFrame||numericPunctuation)
            ?((kind==Kind.DIGIT||numericPunctuation)?numberSize:latinSize)/.92f:1f;
        finalScaleFactor*=scriptScale;
        float scale=sourceScale+(alpha*sourceScale*(finalScaleFactor-1f));
        float translateY=-alpha*sourceScale*finalScaleFactor*anchor;
        float randomScale=1f+sizeFraction;
        if(!Float.isFinite(randomScale)||randomScale<=0f)randomScale=1f;
        double radians=Math.toRadians(Float.isFinite(angleDegrees)?angleDegrees:0f);
        double cos=Math.cos(radians),sin=Math.sin(radians);
        e transformed=new e();
        for(int i=0;i<flags.length;i++) {
            double x=xy[i*2]*scale;
            double y=xy[i*2+1]*scale+translateY;
            float outX=(float)((cos*x-sin*y)*randomScale);
            float outY=(float)((sin*x+cos*y)*randomScale);
            if(!Float.isFinite(outX)||!Float.isFinite(outY))return empty(lineHeight,baseLine,alpha>0f);
            if(flags[i]==0)transformed.a(outX,outY);else transformed.b(outX,outY);
        }
        q result=transformed.f();if(!validBounds(result))return empty(lineHeight,baseLine,alpha>0f);
        float shift=Math.max(0f,-result.a);
        float width=result.a() + Math.max(0f,result.a);
        if(!Float.isFinite(width)||width<0f)width=0f;
        float legacyBaseline=lineHeight*.75f*baseLine;
        float bodyCenter=legacyBaseline-referenceHeight*.30f;
        // One reference frame for mixed scripts; Latin baseline follows from its cap/body height.
        float normalizedBaseline=calibratedFrame?(cjkFrame?bodyCenter:bodyCenter+referenceHeight*CAP_TARGET*scriptScale*.5f):legacyBaseline;
        float baseline=legacyBaseline+alpha*(normalizedBaseline-legacyBaseline);
        return new Geometry(transformed,width,shift,baseline,result.a,result.b,result.c,result.d,alpha>0f);
    }

    static Geometry randomGeometry(e raw,float lineHeight,float referenceHeight,float baseLine,
                                   float sizeFraction,float angleDegrees) {
        return geometry(null,null,'?',raw,lineHeight,referenceHeight,baseLine,0,0f,sizeFraction,angleDegrees);
    }

    static e translated(e source,float dx,float dy) {
        if(source==null||source.c()==0)return new e();
        float[] xy=source.a();byte[] flags=source.b();e out=new e();
        for(int i=0;i<flags.length;i++) {
            float x=xy[i*2]+dx,y=xy[i*2+1]+dy;
            if(flags[i]==0)out.a(x,y);else out.b(x,y);
        }
        return out;
    }

    static e affine(e source,float scale,float angleDegrees,float dx,float dy) {
        if(source==null||source.c()==0)return new e();
        float[] xy=source.a();byte[] flags=source.b();e out=new e();double radians=Math.toRadians(angleDegrees),cos=Math.cos(radians),sin=Math.sin(radians);
        for(int i=0;i<flags.length;i++) {
            double x=xy[i*2]*scale,y=xy[i*2+1]*scale;
            float px=(float)(cos*x-sin*y+dx),py=(float)(sin*x+cos*y+dy);
            if(flags[i]==0)out.a(px,py);else out.b(px,py);
        }
        return out;
    }

    static Rectangle2D.Float bounds(e path) {
        if(path==null||path.c()==0)return new Rectangle2D.Float();
        q b=path.f();if(!validBounds(b))return new Rectangle2D.Float();
        return new Rectangle2D.Float(b.a,b.b,Math.max(0f,b.a()),Math.max(0f,b.b()));
    }

    private static Geometry empty(float lineHeight,float baseLine,boolean normalized){
        return new Geometry(new e(),0f,0f,lineHeight*.75f*baseLine,0,0,0,0,normalized);
    }

    private enum Kind { ZH,CAP,DIGIT,LOWER,ASCENDER,DESCENDER,PUNCT,OTHER }
    private static Kind kind(char ch) {
        if(Character.UnicodeScript.of(ch)==Character.UnicodeScript.HAN)return Kind.ZH;
        int type=Character.getType(ch);
        if(type==Character.CONNECTOR_PUNCTUATION||type==Character.DASH_PUNCTUATION||type==Character.START_PUNCTUATION||type==Character.END_PUNCTUATION||type==Character.INITIAL_QUOTE_PUNCTUATION||type==Character.FINAL_QUOTE_PUNCTUATION||type==Character.OTHER_PUNCTUATION||type==Character.MATH_SYMBOL||type==Character.CURRENCY_SYMBOL||type==Character.MODIFIER_SYMBOL||type==Character.OTHER_SYMBOL)return Kind.PUNCT;
        if(Character.isDigit(ch))return Kind.DIGIT;
        if(Character.isUpperCase(ch))return Kind.CAP;
        if(Character.isLowerCase(ch))return "bdfhklt".indexOf(ch)>=0?Kind.ASCENDER:"gjpqy".indexOf(ch)>=0?Kind.DESCENDER:Kind.LOWER;
        return Kind.OTHER;
    }
    private static float target(Kind k){switch(k){case ZH:return ZH_TARGET;case CAP:return CAP_TARGET;case DIGIT:return DIGIT_TARGET;case ASCENDER:return ASCENDER_TARGET;case DESCENDER:return DESCENDER_TARGET;case LOWER:return LOWER_TARGET;default:return Float.NaN;}}
    private static Group punctuationGroup(char ch,Profile profile){
        if(isCjkPunctuation(ch)&&profile.zh.hasScale)return profile.zh;
        if(profile.cap.hasScale)return profile.cap;
        if(profile.lower.hasScale)return profile.lower;
        if(profile.digit.hasScale)return profile.digit;
        if(profile.zh.hasScale)return profile.zh;
        return null;
    }
    private static float punctuationTarget(Group group,Profile profile){
        if(group==profile.zh)return ZH_TARGET;
        if(group==profile.cap)return CAP_TARGET;
        if(group==profile.lower)return LOWER_TARGET;
        if(group==profile.digit)return DIGIT_TARGET;
        return Float.NaN;
    }

    private static final class Group {
        float medianFraction,anchor;
        boolean hasScale;
    }
    private static final class Profile {
        final Group zh=new Group(),cap=new Group(),digit=new Group(),lower=new Group(),ascender=new Group(),descender=new Group();
        Group group(Kind kind,char ch){
            switch(kind){case ZH:return zh;case CAP:return cap;case DIGIT:return digit;case LOWER:return lower;case ASCENDER:return ascender;case DESCENDER:return descender;
                case PUNCT: return punctuationGroup(ch,this);
                default:return null;}
        }
    }

    private static Profile profile(String hash,v font) {
        if(font==null||font.d<=0)return null;
        String key=hash!=null&&hash.matches("[0-9a-f]{64}")?hash:null;
        if(key!=null)synchronized(PROFILES){Profile known=PROFILES.get(key);if(known!=null)return known;}
        Profile result=new Profile();
        collect(font,ZH_REFERENCE,result.zh,true,true);
        collect(font,CAP_REFERENCE,result.cap,false,false);
        collect(font,DIGIT_REFERENCE,result.digit,false,false);
        collect(font,LOWER_REFERENCE,result.lower,false,false);
        collect(font,ASCENDER_REFERENCE,result.ascender,false,false);
        collect(font,DESCENDER_REFERENCE,result.descender,false,false);
        if(key!=null)synchronized(PROFILES){Profile old=PROFILES.get(key);if(old!=null)return old;PROFILES.put(key,result);}
        return result;
    }

    private static void collect(v font,String chars,Group group,boolean cjk,boolean centers) {
        List<Float> extents=new ArrayList<Float>(),anchors=new ArrayList<Float>();
        for(int i=0;i<chars.length();i++) {
            char ch=chars.charAt(i);try {
                if(!font.a(ch))continue;a glyph=font.b(ch);if(glyph==null)continue;e path=glyph.a();if(path==null||path.c()==0)continue;
                q b=path.f();if(!validBounds(b))continue;
                float extent=cjk?Math.max(b.a(),b.b()):b.b();if(!Float.isFinite(extent)||extent<=1.0e-5f)continue;
                extents.add(extent/font.d);anchors.add(centers?(b.b+b.d)/2f:b.d);
            }catch(RuntimeException ignored) { }
        }
        if(!extents.isEmpty()) {
            group.medianFraction=median(extents);group.anchor=median(anchors);group.hasScale=Float.isFinite(group.medianFraction)&&group.medianFraction>0f;
        }
    }

    private static boolean isCjkPunctuation(char ch) {
        Character.UnicodeBlock block=Character.UnicodeBlock.of(ch);
        return block==Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION||block==Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS||block==Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
    private static boolean validBounds(q b){return b!=null&&Float.isFinite(b.a)&&Float.isFinite(b.b)&&Float.isFinite(b.c)&&Float.isFinite(b.d)&&b.c>=b.a&&b.d>=b.b;}
    private static float median(List<Float> input){Collections.sort(input);int n=input.size();return n%2==1?input.get(n/2):(input.get(n/2-1)+input.get(n/2))*.5f;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static String fingerprint(String text){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
}
