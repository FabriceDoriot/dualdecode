package fr.bytel.dualdecode;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AdPlayer implements Player.Listener {
    private static final String LOG_TAG = "AdPlayer";
    private final Handler mEventHandler;
    private final ExoPlayer mPlayer;
    private final DefaultTrackSelector mTrackSelector;
    private final DefaultBandwidthMeter mBandwidthMeter;
    private boolean mPlaying;
    private SurfaceView mSurfaceView;
    private String mNotifiedEndId = null;
    private String mPreviousMediaId = null;
    private String mPlayingMediaId  = null;
    private String mCurrentMediaId  = null;
    private final List<AdPlayerListener> mListeners = new LinkedList<>();
    private final Set<String> _waitStartedItems = new HashSet<>();
    private final Set<String> _waitReadyItems = new HashSet<>();

    public AdPlayer() {
        Log.setLogLevel(Log.LOG_LEVEL_ALL);
        Context appContext = DualActivity.getAppContext();
        mEventHandler = new Handler(Looper.getMainLooper());
        mBandwidthMeter = new DefaultBandwidthMeter.Builder(appContext).build();
        mTrackSelector = new DefaultTrackSelector(appContext);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder().
                setBufferDurationsMs(4000,DEFAULT_MAX_BUFFER_MS, 1500,0).build();
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(appContext).
                forceEnableMediaCodecAsynchronousQueueing().
                setEnableDecoderFallback(true);
        renderersFactory.setMediaCodecSelector(BcmMediaCodecSelector.BCM_VIDEO_REDUX);
        mPlayer = new ExoPlayer.Builder(appContext, renderersFactory).
                setLooper(mEventHandler.getLooper()).
                setBandwidthMeter(mBandwidthMeter).
                setTrackSelector(mTrackSelector).
                setLoadControl(loadControl).build();
        mPlayer.addListener(this);
    }

    void setPlaylist(List<Ad> ads) {
        if (ads.size() > 0) {
            Log.d(LOG_TAG, "set playlist: "+ads.size());
            final List<MediaSource> adList = new LinkedList<>();
            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().
                    setTransferListener(mBandwidthMeter).
                    setUserAgent(ExoPlayerLibraryInfo.VERSION_SLASHY);
            MediaSource.Factory hlsFct = new HlsMediaSource.Factory(httpDataSourceFactory);
            for (Ad ad : ads) {
                _waitReadyItems.add(ad.mediaId);
                _waitStartedItems.add(ad.mediaId);
                MediaItem.Builder mb = new MediaItem.Builder().setUri(ad.url).setTag(ad).setMediaId(ad.mediaId);
                MediaSource source = hlsFct.createMediaSource(mb.build());
                source.addEventListener(mEventHandler, new MSEventListener(this, ad.mediaId));
                adList.add(source);
            }
            mCurrentMediaId = adList.get(0).getMediaItem().mediaId;
            mEventHandler.post(() -> {
                mPlayer.addMediaSources(adList);
                mPlayer.prepare();
            });
        }
    }

    void playItem(final String mediaId) {
        Utils.runInLooperThread(mEventHandler, ()->{
            mPlayingMediaId = mediaId;
            mPlayer.play();
        });
    }

    void showAdSurface() {
        Utils.runInLooperThread(mEventHandler,()-> {
            if (mSurfaceView != null) {
                logD("showAdSurface");
                mSurfaceView.setVisibility(View.VISIBLE);
                logSurface();
            }
        });
    }

    void hideAdSurface() {
        Utils.runInLooperThread(mEventHandler,()-> {
            if (mSurfaceView != null) {
                logD("hideAdSurface");
                mSurfaceView.setVisibility(View.INVISIBLE);
                logSurface();
            }
        });
    }

    private void logSurface() {
        if (mSurfaceView != null) {
            boolean isValid =
                    mSurfaceView.getHolder().getSurface() != null &&
                            mSurfaceView.getHolder().getSurface().isValid();
            logV("surface.valid =" + isValid);
            logV("surface.vs =" + mSurfaceView.getVisibility());
        }
    }

    public void release() {
        Utils.runInLooperThread(mEventHandler,()->{
            mPlayer.stop();
            hideAdSurface();
            mSurfaceView = null;
            mPlayer.removeListener(this);
            synchronized (mListeners) {
                mListeners.clear();
            }
            mPlayer.release();
            mEventHandler.removeCallbacksAndMessages(null);
        });
    }

    public void addListener(AdPlayerListener adPlayerListener) {
        synchronized (mListeners) {
            mListeners.add(adPlayerListener);
        }
    }

    public void removeListener(AdPlayerListener adPlayerListener) {
        synchronized (mListeners) {
            mListeners.remove(adPlayerListener);
        }
    }

    private void onLoadCompleted(MediaLoadData mediaLoadData, String mediaId) {
        if (mediaLoadData != null && mediaId != null &&
                    mediaLoadData.dataType == C.DATA_TYPE_MEDIA &&
                    mediaLoadData.trackType >= C.TRACK_TYPE_DEFAULT &&
                    _waitReadyItems.contains(mediaId)) {
            notifyPlayerReady(mediaId);
        }
    }

    private String getPlayingMediaId() {
        String[] result = new String[1];
        Utils.runInLooperThreadAndWait(mEventHandler, ()->{
            MediaItem mi = mPlayer.getCurrentMediaItem();
            result[0] = mi == null ? "" : mi.mediaId;
        });
        return result[0];
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_ENDED) {
            mPlaying = false;
            mPreviousMediaId = mCurrentMediaId;
            notifyEnded(-1);
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        notifyError(error.errorCode, error.getMessage(), getPlayingMediaId());
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        logD("onMediaItemTransition "+mediaItem.mediaId+", reason="+reason);
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        logD("onPositionDiscontinuity "+reason);
        if(reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
                oldPosition.mediaItem != null && newPosition.mediaItem != null &&
                !Objects.equals(oldPosition.mediaItem.mediaId, newPosition.mediaItem.mediaId)) {
            mPreviousMediaId = oldPosition.mediaItem.mediaId;
            mCurrentMediaId = newPosition.mediaItem.mediaId;
            logD("onPositionDiscontinuity change mediaItem old="+mPreviousMediaId+", new="+mCurrentMediaId);
            hideAdSurface();
            if (!mCurrentMediaId.equals(mPlayingMediaId)) {
                logD("onPositionDiscontinuity: pause next Ad now");
                mPlayer.pause();
            }
            notifyEnded(oldPosition.positionMs);
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        logD("onIsPlayingChanged "+isPlaying);
        List<AdPlayerListener> listeners = getListeners(mCurrentMediaId);
        for (AdPlayerListener listener : listeners) {
            listener.onIsPlayingChanged(isPlaying);
        }
        if (isPlaying && mCurrentMediaId != null && _waitStartedItems.contains(mCurrentMediaId)) {
            notifyPlayerStarted();
        }
    }

    void notifyPlayerReady(String mediaId) {
        if (_waitReadyItems.contains(mediaId)) {
            logD("notifyPlayerReady mediaId="+mediaId);
            _waitReadyItems.remove(mediaId);
            List<AdPlayerListener> listeners = getListeners(mediaId);
            for (AdPlayerListener listener : listeners) {
                listener.onPlaybackReady();
            }
        }
    }

    void notifyPlayerStarted() {
        if (_waitStartedItems.contains(mCurrentMediaId)) {
            logD("notifyPlayerStarted mCurrentMediaId:" + mCurrentMediaId);
            _waitStartedItems.remove(mCurrentMediaId);
            List<AdPlayerListener> listeners = getListeners(mCurrentMediaId);
            if (!listeners.isEmpty()) {
                for (AdPlayerListener listener : listeners) {
                    listener.onPlaybackStarted();
                }
            }
        }
    }

    void notifyError(int errorCode, String errorMessage, String mediaId) {
        logW("notifyError code="+errorCode+", mediaId="+mediaId, null);
        final List<AdPlayerListener> listeners = getListeners(mediaId);
        for (AdPlayerListener listener : listeners) {
            listener.onPlaybackError(errorCode, errorMessage);
        }
    }

    void notifyEnded(long lastPositionMs) {
        logD("notifyEnded mPlaying="+mPlaying+" ended MediaId="+ mPreviousMediaId);
        if (mPreviousMediaId == null || !mPreviousMediaId.equals(mNotifiedEndId)) {
            List<AdPlayerListener> listeners = getListeners(mPreviousMediaId);
            for (AdPlayerListener listener : listeners) {
                listener.onPlaybackEnded(lastPositionMs);
            }
        }
        mNotifiedEndId = mPreviousMediaId;
        mPreviousMediaId = null;
    }

    private List<AdPlayerListener> getListeners(String mediaId) {
        List<AdPlayerListener> listeners;
        synchronized (mListeners) {
            listeners = new ArrayList<>(mListeners);
        }
        if (TextUtils.isEmpty(mediaId)) {
            mediaId = getPlayingMediaId();
        }
        if (!TextUtils.isEmpty(mediaId)) {
            Iterator<AdPlayerListener> listenersIt = listeners.iterator();
            while (listenersIt.hasNext()) {
                String listenId = listenersIt.next().listenMediaId();
                if (!TextUtils.isEmpty(listenId) && !listenId.equals(mediaId)) {
                    listenersIt.remove();
                }
            }
        }
        return listeners;
    }

    public void setSurfaceView(final SurfaceView surface) {
        if (mSurfaceView != surface) {
            mSurfaceView = surface;
            Utils.runInLooperThread(mEventHandler, ()->mPlayer.setVideoSurfaceView(mSurfaceView));
        }
    }

    private String logTag() {
        return LOG_TAG + "("+mCurrentMediaId+")";
    }

    private void logV(String logMessage) {
        Log.d(logTag(), logMessage);
    }

    private void logD(String logMessage) {
        Log.i(logTag(), logMessage);
    }

    private void logW(String logMessage, Throwable error) {
        Log.w(logTag(), logMessage, error);
    }

    private static final class MSEventListener implements MediaSourceEventListener {
        final AdPlayer session;
        final String mediaId;

        MSEventListener(AdPlayer session, String mediaId) {
            this.session = session;
            this.mediaId = mediaId;
        }

        @Override
        public void onLoadCompleted(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            session.onLoadCompleted(mediaLoadData, mediaId);
        }
    }
}
