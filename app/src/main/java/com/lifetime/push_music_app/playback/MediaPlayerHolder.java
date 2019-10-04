package com.lifetime.push_music_app.playback;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import com.lifetime.push_music_app.models.Song;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MediaPlayerHolder implements PlayerAdapter,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;

    private final Context mContext;
    private final MusicService mMusicService;
    private final AudioManager mAudioManager;
    private MediaPlayer mMediaPlayer;
    private PlaybackInfoListener mPlaybackInfoListener;
    private ScheduledExecutorService mExecutor;
    private Runnable mSeekBarPositionUpdateTask;
    private Song mSelectedSong;
    private List<Song> mSongs;
    private boolean sReplaySong = false;
    private @PlaybackInfoListener.State
    int mState;
    private NotificationReceiver mNotificationActionsReceiver;
    private MusicNotificationManager mMusicNotificationManager;
    private int mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;
    private boolean mPlayOnFocusGain;

    MediaPlayerHolder(@NonNull final MusicService musicService) {
        mMusicService = musicService;
        mContext = mMusicService.getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    private void registerActionsReceiver() {
        mNotificationActionsReceiver = new NotificationReceiver();
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(MusicNotificationManager.PREV_ACTION);
        intentFilter.addAction(MusicNotificationManager.PLAY_PAUSE_ACTION);
        intentFilter.addAction(MusicNotificationManager.NEXT_ACTION);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        mMusicService.registerReceiver(mNotificationActionsReceiver, intentFilter);
    }

    private void unregisterActionsReceiver() {
        if (mMusicService != null && mNotificationActionsReceiver != null) {
            try {
                mMusicService.unregisterReceiver(mNotificationActionsReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void registerNotificationActionsReceiver(final boolean isReceiver) {

        if (isReceiver) {
            registerActionsReceiver();
        } else {
            unregisterActionsReceiver();
        }
    }

    @Override
    public final Song getCurrentSong() {
        return mSelectedSong;
    }



    @Override
    public void setCurrentSong(@NonNull final Song song, @NonNull final List<Song> songs) {
        mSelectedSong = song;
        mSongs = songs;
    }

    @Override
    public void onCompletion(@NonNull final MediaPlayer mediaPlayer) {
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.COMPLETED);
            mPlaybackInfoListener.onPlaybackCompleted();
        }

        if (sReplaySong) {
            if (isMediaPlayer()) {
                resetSong();
            }
            sReplaySong = false;
        } else {
            skip(true);
        }
    }

    @Override
    public void onResumeActivity() {
        startUpdatingCallbackWithPosition();
    }

    @Override
    public void onPauseActivity() {
        stopUpdatingCallbackWithPosition();
    }

    public void setPlaybackInfoListener(@NonNull final PlaybackInfoListener listener) {
        mPlaybackInfoListener = listener;
    }

    private void setStatus(final @PlaybackInfoListener.State int state) {

        mState = state;
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener.onStateChanged(state);
        }
    }

    private void resumeMediaPlayer() {
        if (!isPlaying()) {
            mMediaPlayer.start();
            setStatus(PlaybackInfoListener.State.RESUMED);
            mMusicService.startForeground(MusicNotificationManager.NOTIFICATION_ID, mMusicNotificationManager.createNotification());
        }
    }

    private void pauseMediaPlayer() {
        setStatus(PlaybackInfoListener.State.PAUSED);
        mMediaPlayer.pause();
        mMusicService.stopForeground(false);
        mMusicNotificationManager.getNotificationManager().notify(MusicNotificationManager.NOTIFICATION_ID, mMusicNotificationManager.createNotification());
    }

    private void resetSong() {
        mMediaPlayer.seekTo(0);
        mMediaPlayer.start();
        setStatus(PlaybackInfoListener.State.PLAYING);
    }

    private void startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        if (mSeekBarPositionUpdateTask == null) {
            mSeekBarPositionUpdateTask = new Runnable() {
                @Override
                public void run() {
                    updateProgressCallbackTask();
                }
            };
        }

        mExecutor.scheduleAtFixedRate(
                mSeekBarPositionUpdateTask,
                0,
                1000,
                TimeUnit.MILLISECONDS
        );
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private void stopUpdatingCallbackWithPosition() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
            mSeekBarPositionUpdateTask = null;
        }
    }

    private void updateProgressCallbackTask() {
        if (isMediaPlayer() && mMediaPlayer.isPlaying()) {
            int currentPosition = mMediaPlayer.getCurrentPosition();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onPositionChanged(currentPosition);
            }
        }
    }

    @Override
    public void instantReset() {
        if (isMediaPlayer()) {
            if (mMediaPlayer.getCurrentPosition() < 5000) {
                skip(false);
            } else {
                resetSong();
            }
        }
    }

    @Override
    public void initMediaPlayer() {

        try {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
            } else {
                mMediaPlayer = new MediaPlayer();

                mMediaPlayer.setOnPreparedListener(this);
                mMediaPlayer.setOnCompletionListener(this);
                mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
                mMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                mMusicNotificationManager = mMusicService.getMusicNotificationManager();
            }
            mMediaPlayer.setDataSource(mSelectedSong.path);
            mMediaPlayer.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public final MediaPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {

        startUpdatingCallbackWithPosition();
        setStatus(PlaybackInfoListener.State.PLAYING);
    }

    @Override
    public void release() {
        if (isMediaPlayer()) {
            mMediaPlayer.release();
            mMediaPlayer = null;
            unregisterActionsReceiver();
        }
    }

    @Override
    public boolean isPlaying() {
        return isMediaPlayer() && mMediaPlayer.isPlaying();
    }

    @Override
    public void resumeOrPause() {

        if (isPlaying()) {
            pauseMediaPlayer();
        } else {
            resumeMediaPlayer();
        }
    }

    @Override
    public final @PlaybackInfoListener.State
    int getState() {
        return mState;
    }

    @Override
    public boolean isMediaPlayer() {
        return mMediaPlayer != null;
    }

    @Override
    public void reset() {
        sReplaySong = !sReplaySong;
    }

    @Override
    public boolean isReset() {
        return sReplaySong;
    }

    @Override
    public void skip(final boolean isNext) {
        getSkipSong(isNext);
    }

    private void getSkipSong(final boolean isNext) {
        final int currentIndex = mSongs.indexOf(mSelectedSong);

        int index;

        try {
            index = isNext ? currentIndex + 1 : currentIndex - 1;
            mSelectedSong = mSongs.get(index);
        } catch (IndexOutOfBoundsException e) {
            mSelectedSong = currentIndex != 0 ? mSongs.get(0) : mSongs.get(mSongs.size() - 1);
            e.printStackTrace();
        }
        initMediaPlayer();
    }

    @Override
    public void seekTo(final int position) {
        if (isMediaPlayer()) {
            mMediaPlayer.seekTo(position);
        }
    }

    @Override
    public int getPlayerPosition() {
        return mMediaPlayer.getCurrentPosition();
    }

    private class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            final String action = intent.getAction();

            if (action != null) {

                switch (action) {
                    case MusicNotificationManager.PREV_ACTION:
                        instantReset();
                        break;
                    case MusicNotificationManager.PLAY_PAUSE_ACTION:
                        resumeOrPause();
                        break;
                    case MusicNotificationManager.NEXT_ACTION:
                        skip(true);
                        break;
                }
            }
        }
    }
}
