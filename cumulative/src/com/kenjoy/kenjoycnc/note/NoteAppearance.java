package com.kenjoy.kenjoycnc.note;

import java.util.*;

/** Persistent, deterministic appearance settings and per-atom random state. */
public final class NoteAppearance {
    private NoteAppearance() {}

    /** Document defaults. Numeric amplitudes use fractions except rotation and millimetres. */
    public static final class Config {
        public int normalizeMode=0;
        public float normalization=1f;
        public float latinSize=.80f,numberSize=.80f;
        public int randomMode=0;
        public float size=.06f, gap=.06f, dx=.02f, dy=.05f;
        public float rotation=1.2f, correlation=.75f;
        public int continuity=5;
        public float minGapMm=.1f;
        public int version=1;

        public Config() {}
    }

    /** Frozen channels: expansion, pose, vertical, then size/gap/x/y/rotation residuals. */
    public static final class State {
        public long epoch=1;
        public float[] noise=new float[8];
        public float[] continuity=new float[8];

        public State() {}
    }

    private static final long GOLDEN=0x9E3779B97F4A7C15L;
    private static final float MAX_GAUSSIAN=12f;

    public static Config copy(Config source) {
        if(source==null)return new Config();
        Config c=new Config();
        c.normalizeMode=source.normalizeMode;c.normalization=source.normalization;
        c.latinSize=source.latinSize;c.numberSize=source.numberSize;
        c.randomMode=source.randomMode;c.size=source.size;c.gap=source.gap;
        c.dx=source.dx;c.dy=source.dy;c.rotation=source.rotation;
        c.correlation=source.correlation;c.continuity=source.continuity;
        c.minGapMm=source.minGapMm;c.version=source.version;
        return c;
    }

    static State copyState(State source) {
        if(source==null)return null;
        State s=new State();s.epoch=source.epoch;
        s.noise=source.noise==null?null:source.noise.clone();
        s.continuity=source.continuity==null?null:source.continuity.clone();
        return s;
    }

    public static void validate(Config c) {
        if(c==null)throw new IllegalArgumentException("字形效果参数缺失");
        if(c.version!=1||c.normalizeMode<0||c.normalizeMode>2||c.randomMode<0||c.randomMode>2)
            throw new IllegalArgumentException("字形效果版本或模式无效");
        finiteRange(c.normalization,0f,1f,"规整程度");
        finiteRange(c.latinSize,.40f,1.20f,"英文相对大小");finiteRange(c.numberSize,.40f,1.20f,"数字相对大小");
        finiteRange(c.size,0f,.20f,"大小误差");
        finiteRange(c.gap,0f,.20f,"字距误差");
        finiteRange(c.dx,0f,.10f,"横移误差");
        finiteRange(c.dy,0f,.15f,"上下误差");
        finiteRange(c.rotation,0f,5f,"旋转误差");
        finiteRange(c.correlation,0f,1f,"关联程度");
        if(c.continuity<1||c.continuity>12)throw new IllegalArgumentException("连续程度必须在 1 到 12 之间");
        finiteRange(c.minGapMm,0f,5f,"最小间隙");
    }

    private static void finiteRange(float value,float min,float max,String name) {
        if(!Float.isFinite(value)||value<min||value>max)
            throw new IllegalArgumentException(name+"超出有效范围");
    }

    public static boolean active(Config c) {
        if(c==null)return false;
        return (c.normalizeMode!=0&&c.normalization>0f)
            ||(c.randomMode!=0&&(c.size>0f||c.gap>0f||c.dx>0f||c.dy>0f||c.rotation>0f));
    }

    public static boolean hasEffects(NoteDocument d) {
        if(d==null)return false;
        if(active(d.appearance))return true;
        for(NoteDocument.Atom a:d.atoms)if(a!=null&&active(a.appearanceOverride))return true;
        return false;
    }

    /** Content-stable cache component for geometry derived from a configuration. */
    public static String key(Config c) {
        if(c==null)c=new Config();
        return c.version+":"+c.normalizeMode+":"+bits(c.normalization)+":"+c.randomMode+":"+
            bits(c.size)+":"+bits(c.gap)+":"+bits(c.dx)+":"+bits(c.dy)+":"+
            bits(c.rotation)+":"+bits(c.correlation)+":"+c.continuity+":"+bits(c.minGapMm)+":"+bits(c.latinSize)+":"+bits(c.numberSize);
    }

    /** Includes the frozen per-atom streams for caches that store final glyph geometry. */
    public static String key(NoteDocument d,NoteDocument.Atom a) {
        Config c=effective(d,a);StringBuilder out=new StringBuilder(key(c));
        if(a==null||a.effect==null)return out.append(":missing").toString();
        State s=a.effect;out.append(':').append(s.epoch);
        append(out,s.noise);append(out,s.continuity);
        return out.toString();
    }

    private static int bits(float value){return Float.floatToIntBits(value);}
    private static void append(StringBuilder out,float[] values){
        if(values==null){out.append(":null");return;}
        for(float value:values)out.append(':').append(bits(value));
    }

    public static Config effective(NoteDocument d,NoteDocument.Atom a) {
        Config chosen=a!=null&&a.appearanceOverride!=null?a.appearanceOverride:d==null?null:d.appearance;
        return copy(chosen);
    }

    /** Initialize missing states, reroll selected streams, or rebuild frozen continuity fields. */
    public static void initialize(NoteDocument d,Set<Long> ids,boolean rerandomize,boolean rebuildContinuity) {
        if(d==null)throw new IllegalArgumentException("文章缺失");
        validate(d.appearance);
        Set<Long> targets=ids==null?allIds(d):new HashSet<Long>(ids);
        Set<Long> created=new HashSet<Long>();
        for(NoteDocument.Atom a:d.atoms) {
            if(!targets.contains(a.id))continue;
            Config c=effective(d,a);validate(c);
            State previous=a.effect;
            if((previous==null&&c.randomMode!=0)||(rerandomize&&c.randomMode!=0)) {
                if(d.appearanceSeed==0)d.appearanceSeed=java.util.concurrent.ThreadLocalRandom.current().nextLong();
                if(d.appearanceSeed==0)d.appearanceSeed=GOLDEN;
                long epoch=previous==null?1:nextEpoch(previous.epoch);
                a.effect=generate(d.appearanceSeed,a.id,epoch);
                created.add(a.id);
            }
        }
        if(rebuildContinuity)rebuildContinuity(d,targets);
        else if(!created.isEmpty())rebuildContinuity(d,created);
    }

    private static long nextEpoch(long epoch) {
        if(epoch<=0||epoch==Long.MAX_VALUE)throw new IllegalArgumentException("字形随机代次已耗尽");
        return epoch+1;
    }

    private static Set<Long> allIds(NoteDocument d) {
        Set<Long> result=new HashSet<Long>();for(NoteDocument.Atom a:d.atoms)result.add(a.id);return result;
    }

    private static void rebuildContinuity(NoteDocument d,Set<Long> targets) {
        State previous=null;
        for(NoteDocument.Atom a:d.atoms) {
            State state=a.effect;
            if(a.text!=null&&a.text.indexOf('\n')>=0) {
                if(state!=null&&targets.contains(a.id))state.continuity=state.noise.clone();
                previous=null;
                continue;
            }
            if(state==null)continue;
            if(targets.contains(a.id)) {
                Config c=effective(d,a);validate(c);
                double alpha=(c.continuity-1)/11.0*.92;
                double residual=StrictMath.sqrt(Math.max(0.0,1.0-alpha*alpha));
                state.continuity=state.noise.clone();
                if(previous!=null)state.continuity[2]=(float)(alpha*previous.continuity[2]+residual*state.noise[2]);
            }
            previous=state;
        }
    }

    private static State generate(long articleSeed,long atomId,long epoch) {
        State state=new State();state.epoch=epoch;
        long seed=mix64(articleSeed^mix64(atomId)^mix64(epoch*GOLDEN));
        SplitMix random=new SplitMix(seed);
        for(int i=0;i<8;i++)state.noise[i]=(float)random.gaussian();
        return state;
    }

    static State restore(long articleSeed,long atomId,long epoch,float frozenVertical) {
        if(!Float.isFinite(frozenVertical)||Math.abs(frozenVertical)>MAX_GAUSSIAN)throw new IllegalArgumentException("字符连续状态无效");
        State state=generate(articleSeed,atomId,epoch);state.continuity=state.noise.clone();state.continuity[2]=frozenVertical;return state;
    }

    private static long mix64(long value) {
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;
        value=(value^(value>>>27))*0x94D049BB133111EBL;
        return value^(value>>>31);
    }

    private static final class SplitMix {
        private long state;
        private boolean spareReady;
        private double spare;
        SplitMix(long seed){state=seed;}
        private long nextLong(){state+=GOLDEN;return mix64(state);}
        private double uniform(){return ((nextLong()>>>11)+.5)*0x1.0p-53;}
        double gaussian(){
            if(spareReady){spareReady=false;return clamp(spare);}
            double radius=StrictMath.sqrt(-2.0*StrictMath.log(uniform())),angle=2.0*StrictMath.PI*uniform();
            spare=radius*StrictMath.sin(angle);spareReady=true;return clamp(radius*StrictMath.cos(angle));
        }
        private static double clamp(double value){return Math.max(-MAX_GAUSSIAN,Math.min(MAX_GAUSSIAN,value));}
    }

    /** Return relative size, gap and translation changes plus an angle in degrees. */
    public static float[] values(NoteDocument d,NoteDocument.Atom a,Config c) {
        float[] result=new float[5];
        if(a==null||a.effect==null||c==null||c.randomMode==0)return result;
        State state=a.effect;
        if(state.noise==null||state.noise.length!=8||state.continuity==null||state.continuity.length!=8)return result;
        double[] unit=new double[5];
        if(c.randomMode==1) {
            for(int i=0;i<5;i++)unit[i]=state.noise[i+3];
        } else {
            double common=StrictMath.sqrt(c.correlation),residual=StrictMath.sqrt(1.0-c.correlation);
            unit[0]=common*state.noise[0]+residual*state.noise[3];
            unit[1]=common*state.noise[0]+residual*state.noise[4];
            unit[2]=common*state.noise[1]+residual*state.noise[5];
            unit[3]=common*state.continuity[2]+residual*state.noise[6];
            double rotationFactor=(.75*state.noise[1]+.18*state.continuity[2])/StrictMath.sqrt(.75*.75+.18*.18);
            unit[4]=common*rotationFactor+residual*state.noise[7];
        }
        double max=0;for(double value:unit)max=Math.max(max,Math.abs(value));
        double shrink=max>2.5?2.5/max:1.0;
        float[] magnitude={c.size,c.gap,c.dx,c.dy,c.rotation};
        for(int i=0;i<5;i++) {
            result[i]=(float)(magnitude[i]*unit[i]*shrink/2.5);
        }
        return result;
    }

    public static void rerandomizeEffects(NoteDocument d,Set<Long> targets) {
        initialize(d,targets,true,true);
    }

    /** Make a batch copy with distinct appearance streams while retaining selected glyphs and fonts. */
    public static NoteDocument forCopy(NoteDocument source,int copyIndex) {
        if(source==null||copyIndex<0)throw new IllegalArgumentException("副本编号无效");
        NoteDocument copy=source.copy();
        if(copyIndex==0)return copy;
        boolean random=false;for(NoteDocument.Atom a:copy.atoms)if(effective(copy,a).randomMode!=0){random=true;break;}
        if(!random)return copy;
        copy.appearanceSeed=mix64(source.appearanceSeed^mix64((long)copyIndex+0x632BE59BD9B4E019L));
        initialize(copy,null,true,true);
        return copy;
    }

    static long legacySeed(NoteDocument d) {
        long hash=0xCBF29CE484222325L;
        String body=d.text==null?"":d.text;
        for(int i=0;i<body.length();i++){hash^=body.charAt(i);hash*=0x100000001B3L;}
        hash^=d.generation;hash*=0x100000001B3L;
        if(d.atoms!=null)for(NoteDocument.Atom a:d.atoms)if(a!=null){hash^=a.id;hash*=0x100000001B3L;}
        return mix64(hash);
    }

    static void validateState(State state) {
        if(state==null)return;
        if(state.epoch<=0||!validVector(state.noise)||!validVector(state.continuity))
            throw new IllegalArgumentException("字符随机状态无效");
    }

    private static boolean validVector(float[] values) {
        if(values==null||values.length!=8)return false;
        for(float value:values)if(!Float.isFinite(value)||Math.abs(value)>MAX_GAUSSIAN)return false;
        return true;
    }
}
