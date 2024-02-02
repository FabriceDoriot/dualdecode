package fr.bytel.dualdecode;

public interface AdPlayerListener {
    default String listenMediaId() {return "";}
    default void onPlaybackReady() {}
    default void onPlaybackStarted() {}
    default void onPlaybackError(int errorCode, String errorMessage) {}
    default void onPlaybackEnded(long lastPositionMs) {}
    default void onIsPlayingChanged(boolean isPlaying) {}
}
