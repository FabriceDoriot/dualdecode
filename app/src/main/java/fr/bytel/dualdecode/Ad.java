package fr.bytel.dualdecode;

import android.util.Log;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Ad implements AdPlayerListener, Comparable<Ad>, Runnable {
    public static final long PLAYER_START_MARGIN_MS = 40;
    public static final long PLAYER_SHOW_MARGIN_MS = 180;
    public static final ExecutorService adSessionExecutor = Executors.newCachedThreadPool();
    final String LOG_TAG;
    final String url;
    final int duration;
    final int number;
    final String mediaId;
    AdPlayer player;
    boolean ready;
    boolean started;
    boolean stopping;
    boolean stopped;
    boolean error;
    boolean released;
    long systemTimeMsAtPTS = -1;
    long systemTimeNsAtShow = -1;
    long systemTimeMsAtPlaying = -1;
    long systemTimeMsAtEndOfStream = -1;
    final AdReplacer adReplacer;

    public Ad(AdReplacer adManager, String url, int duration, int aNumber) {
        this.adReplacer = adManager;
        this.url = url;
        this.number = aNumber;
        this.mediaId = "Ad#"+aNumber;
        this.duration = duration;
        this.LOG_TAG = mediaId;
    }

    public void release() {
        synchronized (this) {
            if (released) return;
            released = true;
        }
        stop();
        if (player != null) {
            player.removeListener(this);
            player = null;
        }
        adReplacer.onReleaseAd(this);
    }

    public boolean waiting() {
        return !started && !stopping && !stopped && !released;
    }

    public boolean ready() {
        return ready && !stopping && !stopped && !released;
    }

    @Override
    public int compareTo(Ad o) {
        if (o == null) return 1;
        return number - o.number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ad)) return false;
        Ad that = (Ad) o;
        return mediaId.equals(that.mediaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaId);
    }

    @Override
    public String toString() {
        return "Ad{" + mediaId + ", started=" + started + ", stopped=" + stopped +
                ", error=" + error + "}";
    }

    @Override
    public String listenMediaId() {
        return mediaId;
    }

    @Override
    public void onPlaybackReady() {
        ready = true;
    }

    @Override
    public void onPlaybackStarted() {
        started = true;
    }

    @Override
    public void onPlaybackEnded(long lastPosMs) {
        systemTimeMsAtEndOfStream = System.currentTimeMillis();
        adSessionExecutor.submit(() -> {
            if (!stopping) {
                adReplacer.stopPlay(this, false);
            }
        });
    }

    @Override
    public void onPlaybackError(int errorCode, String errorMessage) {
        error = true;
        Log.d(LOG_TAG, "onPlaybackError error="+errorCode);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            systemTimeMsAtPlaying = System.currentTimeMillis();
            long playing = systemTimeMsAtPlaying - systemTimeMsAtPTS;
            String accuracy = (playing <= 0 ? "1:" : "0:") + ":" + playing;
            Log.d(LOG_TAG, "systemTimeMsAtPlaying set to "+systemTimeMsAtPlaying+", accuracy:"+accuracy);
        }
    }

    void attachPlayer(final AdPlayer player){
        this.player = player;
        this.player.addListener(this);
    }

    void start() {
        player.showAdSurface();
        systemTimeNsAtShow = System.nanoTime();
        adReplacer.displayAd(this);
        adSessionExecutor.submit(()->{
            long playerStartDelay = (systemTimeMsAtPTS-PLAYER_START_MARGIN_MS)-System.currentTimeMillis();
            Log.d(LOG_TAG, "start playback in "+playerStartDelay+" ms.");
            if (playerStartDelay > 0) {
                synchronized (this) {
                    try {
                        wait(playerStartDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Log.w(LOG_TAG, "no start margin "+playerStartDelay);
            }
            player.playItem(mediaId);
        });
    }

    void stop() {
        synchronized (this) {
            if (stopping) return;
            stopping = true;
        }
        if (player != null) {
            player.hideAdSurface();
        }
        if (started) {
            started = false;
            stopped = true;
        } else if (!released) {
            Log.d(LOG_TAG, "stop: not started");
        }
    }

    @Override
    public void run() {
        if (waiting() && ready()) {
            start();
        } else {
            Log.e(LOG_TAG, "Not Ready !");
            stop();
            release();
            adReplacer.stop();
        }
    }
}
