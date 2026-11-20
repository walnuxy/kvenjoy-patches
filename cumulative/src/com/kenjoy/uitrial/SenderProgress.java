package com.kenjoy.uitrial;

import java.util.Collection;

/** Progress is acknowledged queue work; only the vendor Complete event finishes a job. */
public final class SenderProgress {
    private SenderProgress() {}
    public static float fraction(int total,int queued,int paused,int awaiting){
        if(total<=0)return 0f;
        long remaining=(long)queued+paused+awaiting;
        return (float)Math.max(0d,Math.min(1d,1d-remaining/(double)total));
    }
    public static boolean allEmpty(Collection<?> queued,Collection<?> paused,Collection<?> awaiting){
        return queued.isEmpty()&&paused.isEmpty()&&awaiting.isEmpty();
    }
}
