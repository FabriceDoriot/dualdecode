package fr.bytel.dualdecode;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@SuppressLint("UnsafeOptInUsageError")
public final class AdPlayer implements Player.Listener {
    private static final String LOG_TAG = "AdPlayer";
    private final Handler mEventHandler;
    private final ExoPlayer mPlayer;
    private final DefaultBandwidthMeter mBandwidthMeter;
    private final SurfaceView mSurfaceView;
    private boolean mPlaying;
    private String mNotifiedEndId = null;
    private String mPreviousMediaId = null;
    private String mPlayingMediaId  = null;
    private String mCurrentMediaId  = null;
    private final List<AdPlayerListener> mListeners = new LinkedList<>();
    private final Set<String> _waitStartedItems = new HashSet<>();
    private final Set<String> _waitReadyItems = new HashSet<>();

    public AdPlayer(SurfaceView surfaceView) {
        Log.setLogLevel(Log.LOG_LEVEL_ALL);
        Context appContext = DualActivity.getAppContext();
        mEventHandler = new Handler(Looper.getMainLooper());
        mBandwidthMeter = new DefaultBandwidthMeter.Builder(appContext).build();
        DefaultTrackSelector mTrackSelector = new DefaultTrackSelector(appContext);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder().
                setBufferDurationsMs(4000,1400, 1500,0).build();
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
        mPlayer.setVideoSurfaceView(surfaceView);
        mSurfaceView = surfaceView;
    }

    void setPlaylist(List<Ad> ads) {
        if (ads.size() > 0) {
            Log.d(LOG_TAG, "set playlist: "+ads.size());
            final List<MediaSource> adList = new LinkedList<>();
            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().
                    setTransferListener(mBandwidthMeter).
                    setUserAgent(MediaLibraryInfo.VERSION_SLASHY);
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
            logD("showAdSurface");
            mSurfaceView.setVisibility(View.VISIBLE);
            logSurface();
        });
    }

    void hideAdSurface() {
        Utils.runInLooperThread(mEventHandler,()-> {
            logD("hideAdSurface");
            mSurfaceView.setVisibility(View.INVISIBLE);
            logSurface();
        });
    }

    private void logSurface() {
        boolean isValid =
                mSurfaceView.getHolder().getSurface() != null &&
                        mSurfaceView.getHolder().getSurface().isValid();
        logV("surface.valid =" + isValid);
        logV("surface.vs =" + mSurfaceView.getVisibility());
    }

    public void release() {
        Utils.runInLooperThread(mEventHandler,()->{
            mPlayer.stop();
            hideAdSurface();
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
        logD("onMediaItemTransition "+(mediaItem == null ? "null " : mediaItem.mediaId)+", reason="+reason);
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
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
        logW("notifyError code="+errorCode+", mediaId="+mediaId);
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

    private String logTag() {
        return LOG_TAG + "("+mCurrentMediaId+")";
    }

    private void logV(String logMessage) {
        Log.d(logTag(), logMessage);
    }

    private void logD(String logMessage) {
        Log.i(logTag(), logMessage);
    }

    private void logW(String logMessage) {
        Log.w(logTag(), logMessage, null);
    }

    private static final class MSEventListener implements MediaSourceEventListener {
        final AdPlayer session;
        final String mediaId;

        MSEventListener(AdPlayer session, String mediaId) {
            this.session = session;
            this.mediaId = mediaId;
        }

        @Override
        public void onLoadCompleted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                                    @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData) {
            session.onLoadCompleted(mediaLoadData, mediaId);
        }
    }
}
