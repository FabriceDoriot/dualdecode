package fr.bytel.dualdecode;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class AdReplacer implements AdPlayerListener {
    final protected Handler handler = new Handler(Looper.getMainLooper());
    private long testStartTime;
    private TextView mTimerText;
    private TextView mTimestampText;

    private static final String LOG_TAG = "DualDecode.Ads";
    private final RelativeLayout mVideoContainer;
    private final TreeSet<Ad> mAdCalls = new TreeSet<>();
    private AdPlayer adPlayer;
    private final Runnable displayTimeRunnable = this::displayTime;
    public static boolean mStarted;

    private static final String[] urls = {
            "https://bytel-ads-sample.s3.eu-west-3.amazonaws.com/creatives/cdn_20s/AD_FTV_PUB_LIVE1473518/asSbTHcbw8Blo5UtpiNR.m3u8",
            "https://bytel-ads-sample.s3.eu-west-3.amazonaws.com/creatives/cdn_15s/AD_FTV_PUB_LIVE1471518/rwXnlmkxCFOEKEFwiWR0.m3u8"
    };
    private static final int[] durations = {20000, 15000};
    private static int url_idx = 0;

    public AdReplacer(RelativeLayout videoContainer) {
        mVideoContainer = videoContainer;
        start();
    }

    public void stopPlay(Ad acs, boolean isError){
        Log.w(LOG_TAG, "stopPlay("+isError+"):"+acs);
        acs.stop();
        acs.release();
    }

    public void onReleaseAd(Ad acs){
        Log.w(LOG_TAG, "onReleaseAd:"+acs);
        mAdCalls.remove(acs);
        if (mAdCalls.isEmpty()) stop();
    }

    @Override
    public void onPlaybackError(int errorCode, String errorMessage) {
        stop();
    }

    void start() {
        if (mStarted) return;
        mStarted = true;
        Log.w(LOG_TAG, "******* start ******");
        adPlayer = new AdPlayer();
        adPlayer.setSurfaceView(createAdSurface());
        adPlayer.hideAdSurface();
        adPlayer.addListener(this);
        handler.postDelayed(()-> addAds(1, 2), 1000);
        handler.postDelayed(this::stop, 150000);
    }

    private SurfaceView createAdSurface() {
        SurfaceView AdsVideoSurfaceView = new SurfaceView(mVideoContainer.getContext());
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mVideoContainer.addView(AdsVideoSurfaceView,lp);
        return AdsVideoSurfaceView;
    }

    private void addAds(final int fromSeqNum, final int toSeqNum) {
        List<Ad> playList = new ArrayList<>();
        long now = System.currentTimeMillis();
        long start = now+5000;
        for (int seqNum = fromSeqNum; seqNum<=toSeqNum; seqNum++) {
            String url = urls[url_idx % urls.length];
            int duration = durations[url_idx % durations.length];
            Ad ad = new Ad(this, url, duration, seqNum);
            mAdCalls.add(ad);
            playList.add(ad);
            ad.attachPlayer(adPlayer);
            long systemTimeAtPTS = start + Ad.PLAYER_SHOW_MARGIN_MS;
            ad.systemTimeMsAtPTS = systemTimeAtPTS;
            handler.postDelayed(ad, start-now);
            start = systemTimeAtPTS + ad.duration+5000 - Ad.PLAYER_SHOW_MARGIN_MS;
            url_idx++;
        }
        adPlayer.setPlaylist(playList);
        startDisplay(mVideoContainer);
    }

    public void stop() {
        if (!mStarted) return;
        mStarted = false;
        Log.w(LOG_TAG, "******* stop *******");
        handler.removeCallbacksAndMessages(null);
        final TreeSet<Ad> adCalls = new TreeSet<>(mAdCalls);
        if (!adCalls.isEmpty()) {
            for (Ad acs : adCalls) {
                acs.release();
            }
        }
        stopDisplay();
        adPlayer.setSurfaceView(null);
        adPlayer.removeListener(this);
        adPlayer.release();
        adPlayer = null;
        handler.post(mVideoContainer::removeAllViews);
    }

    protected void startDisplay(View container) {
        if (container != null) {
            testStartTime = System.nanoTime();
            mTimerText = container.getRootView().findViewById(R.id.timer_text);
            mTimestampText = container.getRootView().findViewById(R.id.timestamp_text);
            handler.post(displayTimeRunnable);
        }
    }

    protected void stopDisplay() {
        handler.removeCallbacks(displayTimeRunnable);
        testStartTime = 0;
    }

    void displayAd(Ad acs) {
        Utils.runInLooperThread(handler, ()->{
            String showingAd = acs == null || acs.released || acs.systemTimeNsAtShow < 0 ?
                    "--------" : "visibility:"+((acs.systemTimeNsAtShow-testStartTime)/1000000L);
            mTimestampText.setText(showingAd);
        });
    }

    private void displayTime() {
        handler.removeCallbacks(displayTimeRunnable);
        if (testStartTime > 0) {
            long time = (System.nanoTime() - testStartTime)/1000000L;
            mTimerText.setText("test time:"+time);
            handler.postDelayed(displayTimeRunnable, 40);
        }
    }

}
