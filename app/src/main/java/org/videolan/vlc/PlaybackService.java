/*****************************************************************************
 * PlaybackService.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.PopupManager;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.media.MediaWrapperList;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.VLCAppWidgetProvider;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlaybackService extends Service implements IVLCVout.Callback {

    private static final String TAG = "VLC/PlaybackService";

    private static final int SHOW_PROGRESS = 0;
    private static final int SHOW_TOAST = 1;
    public static final String ACTION_REMOTE_GENERIC =  Strings.buildPkgString("remote.");
    public static final String ACTION_REMOTE_BACKWARD = ACTION_REMOTE_GENERIC+"Backward";
    public static final String ACTION_REMOTE_PLAY = ACTION_REMOTE_GENERIC+"Play";
    public static final String ACTION_REMOTE_PLAYPAUSE = ACTION_REMOTE_GENERIC+"PlayPause";
    public static final String ACTION_REMOTE_PAUSE = ACTION_REMOTE_GENERIC+"Pause";
    public static final String ACTION_REMOTE_STOP = ACTION_REMOTE_GENERIC+"Stop";
    public static final String ACTION_REMOTE_FORWARD = ACTION_REMOTE_GENERIC+"Forward";
    public static final String ACTION_REMOTE_LAST_PLAYLIST = ACTION_REMOTE_GENERIC+"LastPlaylist";
    public static final String ACTION_REMOTE_LAST_VIDEO_PLAYLIST = ACTION_REMOTE_GENERIC+"LastVideoPlaylist";
    public static final String ACTION_REMOTE_SWITCH_VIDEO = ACTION_REMOTE_GENERIC+"SwitchToVideo";

    public interface Callback {
        void update();
        void updateProgress();
        void onMediaEvent(Media.Event event);
        void onMediaPlayerEvent(MediaPlayer.Event event);
    }

    private class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }
    public static PlaybackService getService(IBinder iBinder) {
        LocalBinder binder = (LocalBinder) iBinder;
        return binder.getService();
    }

    private SharedPreferences mSettings;
    private final IBinder mBinder = new LocalBinder();
    private MediaWrapperList mMediaList = new MediaWrapperList();
    private MediaWrapperList mMediaList1 = new MediaWrapperList();
    private MediaPlayer mMediaPlayer,mMediaPlayer1;
    private boolean mParsed = false;
    private boolean mParsed1 = false;
    private boolean mSeekable = false;
    private boolean mSeekable1 = false;
    private boolean mPausable = false;
    private boolean mPausable1 = false;
    private boolean mIsAudioTrack = false;
    private boolean mHasHdmiAudio = false;
    private boolean mSwitchingToVideo = false;
    private boolean mSwitchingToVideo1 = false;
    private boolean mVideoBackground = false;
    private boolean mVideoBackground1 = false;

    final private ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private boolean mDetectHeadset = true;
    private PowerManager.WakeLock mWakeLock;
    private final AtomicBoolean mExpanding = new AtomicBoolean(false);
    private final AtomicBoolean mExpanding1 = new AtomicBoolean(false);

    private static boolean mWasPlayingAudio = false; // used only if readPhoneState returns true

    // Index management
    /**
     * Stack of previously played indexes, used in shuffle mode
     */
    private Stack<Integer> mPrevious;
    private Stack<Integer> mPrevious1;
    private int mCurrentIndex; // Set to -1 if no media is currently loaded
    private int mCurrentIndex1;
    private int mPrevIndex; // Set to -1 if no previous media
    private int mPrevIndex1;
    private int mNextIndex; // Set to -1 if no next media
    private int mNextIndex1; // Set to -1 if no next media

    // Playback management

    MediaSessionCompat mMediaSession;
    protected MediaSessionCallback mSessionCallback;
    private static final long PLAYBACK_ACTIONS = PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;
    private boolean mShuffling = false;
    private int mRepeating = REPEAT_NONE;
    private Random mRandom = null; // Used in shuffling process
    private long mSavedTime = 0l;
    private long mSavedTime1 = 0l;
    private boolean mHasAudioFocus = false;
    // RemoteControlClient-related
    /**
     * RemoteControlClient is for lock screen playback control.
     */
    private RemoteControlClientReceiver mRemoteControlClientReceiver = null;
    /**
     * Last widget position update timestamp
     */
    private long mWidgetPositionTimestamp = Calendar.getInstance().getTimeInMillis();
    private ComponentName mRemoteControlClientReceiverComponent;
    private PopupManager mPopupManager,mPopupManager1;

    private static LibVLC LibVLC() {
        return VLCInstance.get();
    }

    private MediaPlayer newMediaPlayer() {
        Log.d(TAG,"newMediaPlayer.......");
        final MediaPlayer mp = new MediaPlayer(LibVLC());
        final String aout = VLCOptions.getAout(mSettings);
        if (mp.setAudioOutput(aout) && aout.equals("android_audiotrack")) {
            mIsAudioTrack = true;
            if (mHasHdmiAudio)
                mp.setAudioOutputDevice("hdmi");
        } else
            mIsAudioTrack = false;
        mp.getVLCVout().addCallback(this);

        return mp;
    }

    private MediaPlayer newMediaPlayer1() {
        Log.d(TAG,"newMediaPlayer1.......");
        final MediaPlayer mp = new MediaPlayer(LibVLC());
        final String aout = VLCOptions.getAout(mSettings);
        if (mp.setAudioOutput(aout) && aout.equals("android_audiotrack")) {
            mIsAudioTrack = true;
            if (mHasHdmiAudio)
                mp.setAudioOutputDevice("hdmi");
        } else
            mIsAudioTrack = false;
        mp.getVLCVout().addCallback(this);

        return mp;
    }

    private static boolean readPhoneState() {
        return !AndroidUtil.isFroyoOrLater();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mMediaPlayer = newMediaPlayer();
        mMediaPlayer.setEqualizer(VLCOptions.getEqualizer(this));

        mMediaPlayer1 = newMediaPlayer1();
        mMediaPlayer1.setEqualizer(VLCOptions.getEqualizer(this));

        if (!VLCInstance.testCompatibleCPU(this)) {
            stopSelf();
            return;
        }

        if (!AndroidDevices.hasTsp() && !AndroidDevices.hasPlayServices())
            AndroidDevices.setRemoteControlReceiverEnabled(true);

        mDetectHeadset = mSettings.getBoolean("enable_headset_detection", true);

        mCurrentIndex = -1;
        mCurrentIndex1 = -1;
        mPrevIndex = -1;
        mPrevIndex1 = -1;
        mNextIndex = -1;
        mNextIndex1 = -1;
        mPrevious = new Stack<Integer>();
        mPrevious1 = new Stack<Integer>();
        mRemoteControlClientReceiverComponent = new ComponentName(BuildConfig.APPLICATION_ID,
                RemoteControlClientReceiver.class.getName());

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        PowerManager pm = (PowerManager) VLCApplication.getAppContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        IntentFilter filter = new IntentFilter();
        filter.setPriority(Integer.MAX_VALUE);
        filter.addAction(ACTION_REMOTE_BACKWARD);
        filter.addAction(ACTION_REMOTE_PLAYPAUSE);
        filter.addAction(ACTION_REMOTE_PLAY);
        filter.addAction(ACTION_REMOTE_PAUSE);
        filter.addAction(ACTION_REMOTE_STOP);
        filter.addAction(ACTION_REMOTE_FORWARD);
        filter.addAction(ACTION_REMOTE_LAST_PLAYLIST);
        filter.addAction(ACTION_REMOTE_LAST_VIDEO_PLAYLIST);
        filter.addAction(ACTION_REMOTE_SWITCH_VIDEO);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_INIT);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);
        registerReceiver(mReceiver1, filter);
        registerV21();

        boolean stealRemoteControl = mSettings.getBoolean("enable_steal_remote_control", false);

        if (!AndroidUtil.isFroyoOrLater() || stealRemoteControl) {
            /* Backward compatibility for API 7 */
            filter = new IntentFilter();
            if (stealRemoteControl)
                filter.setPriority(Integer.MAX_VALUE);
            filter.addAction(Intent.ACTION_MEDIA_BUTTON);
            mRemoteControlClientReceiver = new RemoteControlClientReceiver();
            registerReceiver(mRemoteControlClientReceiver, filter);
        }

        if (readPhoneState()) {
            initPhoneListener();
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, mPhoneEvents);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        if(ACTION_REMOTE_PLAYPAUSE.equals(intent.getAction())){
            if (hasCurrentMedia())
                return START_STICKY;
            else
                loadLastPlaylist(TYPE_AUDIO);
        } else if (ACTION_REMOTE_PLAY.equals(intent.getAction())) {
            if (hasCurrentMedia())
                play();
            else
                loadLastPlaylist(TYPE_AUDIO);
        }
        updateWidget();
        return super.onStartCommand(intent, flags, startId);
    }

    public int onStartCommand1(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        if(ACTION_REMOTE_PLAYPAUSE.equals(intent.getAction())){
            if (hasCurrentMedia1())
                return START_STICKY;
            else
                loadLastPlaylist1(TYPE_AUDIO);
        } else if (ACTION_REMOTE_PLAY.equals(intent.getAction())) {
            if (hasCurrentMedia1())
                play1();
            else
                loadLastPlaylist1(TYPE_AUDIO);
        }
        updateWidget();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy......");
        stop();

        if (!AndroidDevices.hasTsp() && !AndroidDevices.hasPlayServices())
            AndroidDevices.setRemoteControlReceiverEnabled(false);

        if (mWakeLock.isHeld())
            mWakeLock.release();
        unregisterReceiver(mReceiver);
        unregisterReceiver(mReceiver1);
        if (mReceiverV21 != null)
            unregisterReceiver(mReceiverV21);
        if (mRemoteControlClientReceiver != null) {
            unregisterReceiver(mRemoteControlClientReceiver);
            mRemoteControlClientReceiver = null;
        }
        mMediaPlayer.release();
        mMediaPlayer1.release();

        if (readPhoneState()) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!hasCurrentMedia())
            stopSelf();
        return true;
    }

    public IVLCVout getVLCVout()  {
        Log.d(TAG,"getVLCVout.......");
        return mMediaPlayer.getVLCVout();
    }

    public IVLCVout getVLCVout1()  {
        Log.d(TAG,"getVLCVout1.......");
        return mMediaPlayer1.getVLCVout();
    }

    private final OnAudioFocusChangeListener mAudioFocusListener = AndroidUtil.isFroyoOrLater() ?
            createOnAudioFocusChangeListener() : null;

    @TargetApi(Build.VERSION_CODES.FROYO)
    private OnAudioFocusChangeListener createOnAudioFocusChangeListener() {
        return new OnAudioFocusChangeListener() {
            private boolean mLossTransient = false;
            private int mLossTransientVolume = -1;
            private boolean wasPlaying = false;

            @Override
            public void onAudioFocusChange(int focusChange) {
                /*
                 * Pause playback during alerts and notifications
                 */
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.i(TAG, "AUDIOFOCUS_LOSS");
                        // Pause playback
                        changeAudioFocus(false);
                        pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        // Pause playback
                        mLossTransient = true;
                        wasPlaying = isPlaying();
                        if (wasPlaying)
                            pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        // Lower the volume
                        if (mMediaPlayer.isPlaying()) {
                            mLossTransientVolume = mMediaPlayer.getVolume();
                            mMediaPlayer.setVolume(36);
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.i(TAG, "AUDIOFOCUS_GAIN: " + mLossTransientVolume + ", " + mLossTransient);
                        // Resume playback
                        if (mLossTransientVolume != -1) {
                            mMediaPlayer.setVolume(mLossTransientVolume);
                            mLossTransientVolume = -1;
                        } else if (mLossTransient) {
                            if (wasPlaying)
                                mMediaPlayer.play();
                            mLossTransient = false;
                        }
                        break;
                }
            }
        };
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private void changeAudioFocus(boolean acquire) {
        final AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (am == null)
            return;

        if (acquire) {
            if (!mHasAudioFocus) {
                final int result = am.requestAudioFocus(mAudioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    am.setParameters("bgm_state=true");
                    mHasAudioFocus = true;
                }
            }
        } else {
            if (mHasAudioFocus) {
                final int result = am.abandonAudioFocus(mAudioFocusListener);
                am.setParameters("bgm_state=false");
                mHasAudioFocus = false;
            }
        }
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void registerV21() {
        final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        registerReceiver(mReceiverV21, intentFilter);
    }

    private final BroadcastReceiver mReceiverV21 = AndroidUtil.isLolliPopOrLater() ? new BroadcastReceiver()
    {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;
            if (action.equalsIgnoreCase(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                mHasHdmiAudio = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1;
                if (mMediaPlayer != null && mIsAudioTrack)
                    mMediaPlayer.setAudioOutputDevice(mHasHdmiAudio ? "hdmi" : "stereo");
            }
        }
    } : null;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private boolean wasPlaying = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"onReceive.......");
            String action = intent.getAction();
            int state = intent.getIntExtra("state", 0);
            if( mMediaPlayer == null ) {
                Log.w(TAG, "Intent received, but VLC is not loaded, skipping.");
                return;
            }

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mMediaPlayer.isPlaying() && !hasCurrentMedia()) {
                context.startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (!hasCurrentMedia())
                    return;
                if (mMediaPlayer.isPlaying())
                    pause();
                else
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mMediaPlayer.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (hasCurrentMedia())
                    pause();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_BACKWARD)) {
                previous();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP) ||
                    action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                stop();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                next();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastPlaylist(TYPE_AUDIO);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_VIDEO_PLAYLIST)) {
                loadLastPlaylist(TYPE_VIDEO);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_SWITCH_VIDEO)) {
                removePopup();
                if (hasMedia()) {
                    getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    switchToVideo();
                }
            } else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_INIT)) {
                updateWidget();
            }

            /*
             * headset plug events
             */
            else if (mDetectHeadset && !mHasHdmiAudio) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.i(TAG, "Headset Removed.");
                    wasPlaying = isPlaying();
                    if (wasPlaying && hasCurrentMedia())
                        pause();
                } else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (wasPlaying && hasCurrentMedia() && mSettings.getBoolean("enable_play_on_headset_insertion", false))
                        play();
                }
            }
        }
    };

    private final BroadcastReceiver mReceiver1 = new BroadcastReceiver() {
        private boolean wasPlaying = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"onReceive1.......");
            String action = intent.getAction();
            int state = intent.getIntExtra("state", 0);
            if( mMediaPlayer1 == null ) {
                Log.w(TAG, "Intent received, but VLC is not loaded, skipping.");
                return;
            }

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mMediaPlayer1.isPlaying() && !hasCurrentMedia1()) {
                context.startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (!hasCurrentMedia1())
                    return;
                if (mMediaPlayer1.isPlaying())
                    pause1();
                else
                    play1();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mMediaPlayer1.isPlaying() && hasCurrentMedia1())
                    play1();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (hasCurrentMedia1())
                    pause1();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_BACKWARD)) {
                previous1();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP) ||
                    action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                stop1();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                next1();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastPlaylist1(TYPE_AUDIO);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_VIDEO_PLAYLIST)) {
                loadLastPlaylist1(TYPE_VIDEO);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_SWITCH_VIDEO)) {
                removePopup1();
                if (hasMedia1()) {
                    getCurrentMediaWrapper1().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    switchToVideo1();
                }
            } else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_INIT)) {
                updateWidget1();
            }

            /*
             * headset plug events
             */
            else if (mDetectHeadset && !mHasHdmiAudio) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.i(TAG, "Headset Removed.");
                    wasPlaying = isPlaying1();
                    if (wasPlaying && hasCurrentMedia1())
                        pause1();
                } else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (wasPlaying && hasCurrentMedia1() && mSettings.getBoolean("enable_play_on_headset_insertion", false))
                        play1();
                }
            }
        }
    };


    @Override
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
        hideNotification(false);
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        mSwitchingToVideo = false;
        mSwitchingToVideo1 = false;
    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vlcVout) {
    }

    private final Media.EventListener mMediaListener = new Media.EventListener() {
        @Override
        public void onEvent(Media.Event event) {
            boolean update = true;
            switch (event.type) {
                case Media.Event.MetaChanged:
                    /* Update Meta if file is already parsed */
                    if (mParsed && updateCurrentMeta(event.getMetaId()))
                        executeUpdate();
                    Log.i(TAG, "Media.Event.MetaChanged: " + event.getMetaId());
                    break;
                case Media.Event.ParsedChanged:
                    Log.i(TAG, "Media.Event.ParsedChanged");
                    updateCurrentMeta(-1);
                    mParsed = true;
                    break;
                default:
                    update = false;

            }
            if (update) {
                for (Callback callback : mCallbacks)
                    callback.onMediaEvent(event);
                if (mParsed && mMediaSession != null)
                    showNotification();
            }
        }
    };

    private final Media.EventListener mMediaListener1 = new Media.EventListener() {
        @Override
        public void onEvent(Media.Event event) {
            boolean update = true;
            switch (event.type) {
                case Media.Event.MetaChanged:
                    /* Update Meta if file is already parsed */
                    if (mParsed1 && updateCurrentMeta1(event.getMetaId()))
                        executeUpdate1();
                    Log.i(TAG, "Media.Event.MetaChanged: " + event.getMetaId());
                    break;
                case Media.Event.ParsedChanged:
                    Log.i(TAG, "Media1.Event.ParsedChanged");
                    updateCurrentMeta1(-1);
                    mParsed1 = true;
                    break;
                default:
                    update = false;

            }
            if (update) {
                for (Callback callback : mCallbacks)
                    callback.onMediaEvent(event);
                if (mParsed1 && mMediaSession != null)
                    showNotification1();
            }
        }
    };

    /**
     * Update current media meta and return true if player needs to be updated
     *
     * @param id of the Meta event received, -1 for none
     * @return true if UI needs to be updated
     */
    private boolean updateCurrentMeta(int id) {
        Log.d(TAG,"updateCurrentMeta........");
        if (id == Media.Meta.Publisher)
            return false;
        final MediaWrapper mw = getCurrentMedia();
        if (mw != null)
            mw.updateMeta(mMediaPlayer);
        return id != Media.Meta.NowPlaying || getCurrentMedia().getNowPlaying() != null;
    }

    private boolean updateCurrentMeta1(int id) {
        Log.d(TAG,"updateCurrentMeta1........");
        if (id == Media.Meta.Publisher)
            return false;
        final MediaWrapper mw = getCurrentMedia1();
        if (mw != null)
            mw.updateMeta(mMediaPlayer1);
        return id != Media.Meta.NowPlaying || getCurrentMedia1().getNowPlaying() != null;
    }

    private final MediaPlayer.EventListener mMediaPlayerListener = new MediaPlayer.EventListener() {
        KeyguardManager keyguardManager = (KeyguardManager) VLCApplication.getAppContext().getSystemService(Context.KEYGUARD_SERVICE);

        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    if(mSavedTime != 0l)
                        seek(mSavedTime);
                    mSavedTime = 0l;

                    Log.i(TAG, "MediaPlayer.Event.Playing");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();

                    final MediaWrapper mw = mMediaList.getMedia(mCurrentIndex);
                    if (mw != null) {
                        long length = mMediaPlayer.getLength();
                        MediaDatabase dbManager = MediaDatabase.getInstance();
                        MediaWrapper m = dbManager.getMedia(mw.getUri());
                        /**
                         * 1) There is a media to update
                         * 2) It has a length of 0
                         * (dynamic track loading - most notably the OGG container)
                         * 3) We were able to get a length even after parsing
                         * (don't want to replace a 0 with a 0)
                         */
                        if (m != null && m.getLength() == 0 && length > 0) {
                            dbManager.updateMedia(mw.getUri(),
                                    MediaDatabase.INDEX_MEDIA_LENGTH, length);
                        }
                    }

                    changeAudioFocus(true);
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    if (!keyguardManager.inKeyguardRestrictedInputMode() && !mVideoBackground && switchToVideo())
                        hideNotification(false);
                    else
                        showNotification();
                    mVideoBackground = false;
                    break;
                case MediaPlayer.Event.Paused:
                    Log.i(TAG, "MediaPlayer.Event.Paused");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    showNotification();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.i(TAG, "MediaPlayer.Event.Stopped");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EndReached:
                    Log.i(TAG, "MediaPlayer.Event.EndReached");
                    executeUpdateProgress();
                    determinePrevAndNextIndices(true);
                    next();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EncounteredError:
                    showToast(getString(
                            R.string.invalid_location,
                            mMediaList.getMRL(mCurrentIndex)), Toast.LENGTH_SHORT);
                    executeUpdate();
                    executeUpdateProgress();
                    next();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.TimeChanged:
                    break;
                case MediaPlayer.Event.PositionChanged:
                    updateWidgetPosition(event.getPositionChanged());
                    break;
                case MediaPlayer.Event.Vout:
                    break;
                case MediaPlayer.Event.ESAdded:
                    if (event.getEsChangedType() == Media.Track.Type.Video && (mVideoBackground || !switchToVideo())) {
                        /* Update notification content intent: resume video or resume audio activity */
                        updateMetadata();
                    }
                    break;
                case MediaPlayer.Event.ESDeleted:
                    break;
                case MediaPlayer.Event.PausableChanged:
                    mPausable = event.getPausable();
                    break;
                case MediaPlayer.Event.SeekableChanged:
                    mSeekable = event.getSeekable();
                    break;
            }
            for (Callback callback : mCallbacks)
                callback.onMediaPlayerEvent(event);
        }
    };

    private final MediaPlayer.EventListener mMediaPlayerListener1 = new MediaPlayer.EventListener() {
        KeyguardManager keyguardManager = (KeyguardManager) VLCApplication.getAppContext().getSystemService(Context.KEYGUARD_SERVICE);

        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    if(mSavedTime1 != 0l)
                        seek1(mSavedTime1);
                    mSavedTime1 = 0l;

                    Log.i(TAG, "MediaPlayer1.Event.Playing");
                    executeUpdate1();
                    publishState(event.type);
                    executeUpdateProgress();

                    final MediaWrapper mw = mMediaList1.getMedia(mCurrentIndex1);
                    Log.d(TAG,"mw......" + mw);
                    if (mw != null) {
                        long length = mMediaPlayer1.getLength();
                        Log.d(TAG,"length......." + length);
                        MediaDatabase dbManager = MediaDatabase.getInstance();
                        MediaWrapper m = dbManager.getMedia(mw.getUri());
                        /**
                         * 1) There is a media to update
                         * 2) It has a length of 0
                         * (dynamic track loading - most notably the OGG container)
                         * 3) We were able to get a length even after parsing
                         * (don't want to replace a 0 with a 0)
                         */
                        if (m != null && m.getLength() == 0 && length > 0) {
                            dbManager.updateMedia(mw.getUri(),
                                    MediaDatabase.INDEX_MEDIA_LENGTH, length);
                        }
                    }

                    changeAudioFocus(true);
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    if (!keyguardManager.inKeyguardRestrictedInputMode() && !mVideoBackground1 && switchToVideo1())
                        hideNotification(false);
                    else
                        showNotification1();
                    mVideoBackground1 = false;
                    break;
                case MediaPlayer.Event.Paused:
                    Log.i(TAG, "MediaPlayer1.Event.Paused");
                    executeUpdate1();
                    publishState(event.type);
                    executeUpdateProgress();
                    showNotification1();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.i(TAG, "MediaPlayer1.Event.Stopped");
                    executeUpdate1();
                    publishState(event.type);
                    executeUpdateProgress();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EndReached:
                    Log.i(TAG, "MediaPlayer1.Event.EndReached");
                    executeUpdateProgress();
                    determinePrevAndNextIndices1(true);
                    next1();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EncounteredError:
                    showToast(getString(
                            R.string.invalid_location,
                            mMediaList1.getMRL(mCurrentIndex1)), Toast.LENGTH_SHORT);
                    executeUpdate1();
                    executeUpdateProgress();
                    next1();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.TimeChanged:
                    break;
                case MediaPlayer.Event.PositionChanged:
                    updateWidgetPosition1(event.getPositionChanged());
                    break;
                case MediaPlayer.Event.Vout:
                    break;
                case MediaPlayer.Event.ESAdded:
                    if (event.getEsChangedType() == Media.Track.Type.Video && (mVideoBackground1 || !switchToVideo1())) {
                        /* Update notification content intent: resume video or resume audio activity */
                        updateMetadata1();
                    }
                    break;
                case MediaPlayer.Event.ESDeleted:
                    break;
                case MediaPlayer.Event.PausableChanged:
                    mPausable1 = event.getPausable();
                    break;
                case MediaPlayer.Event.SeekableChanged:
                    mSeekable1 = event.getSeekable();
                    break;
            }
            for (Callback callback : mCallbacks)
                callback.onMediaPlayerEvent(event);
        }
    };

    private final MediaWrapperList.EventListener mListEventListener = new MediaWrapperList.EventListener() {

        @Override
        public void onItemAdded(int index, String mrl) {
            Log.i(TAG, "CustomMediaListItemAdded");
            if(mCurrentIndex >= index && !mExpanding.get())
                mCurrentIndex++;

            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void onItemRemoved(int index, String mrl) {
            Log.i(TAG, "CustomMediaListItemDeleted");
            if (mCurrentIndex == index && !mExpanding.get()) {
                // The current item has been deleted
                mCurrentIndex--;
                determinePrevAndNextIndices();
                if (mNextIndex != -1)
                    next();
                else if (mCurrentIndex != -1) {
                    playIndex(mCurrentIndex, 0);
                } else
                    stop();
            }

            if(mCurrentIndex > index && !mExpanding.get())
                mCurrentIndex--;
            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void onItemMoved(int indexBefore, int indexAfter, String mrl) {
            Log.i(TAG, "CustomMediaListItemMoved");
            if (mCurrentIndex == indexBefore) {
                mCurrentIndex = indexAfter;
                if (indexAfter > indexBefore)
                    mCurrentIndex--;
            } else if (indexBefore > mCurrentIndex
                    && indexAfter <= mCurrentIndex)
                mCurrentIndex++;
            else if (indexBefore < mCurrentIndex
                    && indexAfter > mCurrentIndex)
                mCurrentIndex--;

            // If we are in random mode, we completely reset the stored previous track
            // as their indices changed.
            mPrevious.clear();

            determinePrevAndNextIndices();
            executeUpdate();
        }
    };

    private final MediaWrapperList.EventListener mListEventListener1 = new MediaWrapperList.EventListener() {

        @Override
        public void onItemAdded(int index, String mrl) {
            Log.i(TAG, "CustomMediaListItemAdded");
            if(mCurrentIndex1 >= index && !mExpanding1.get())
                mCurrentIndex1++;

            determinePrevAndNextIndices1();
            executeUpdate1();
        }

        @Override
        public void onItemRemoved(int index, String mrl) {
            Log.i(TAG, "CustomMediaListItemDeleted");
            if (mCurrentIndex1 == index && !mExpanding1.get()) {
                // The current item has been deleted
                mCurrentIndex1--;
                determinePrevAndNextIndices1();
                if (mNextIndex != -1)
                    next1();
                else if (mCurrentIndex1 != -1) {
                    playIndex1(mCurrentIndex1, 0);
                } else
                    stop1();
            }

            if(mCurrentIndex1 > index && !mExpanding1.get())
                mCurrentIndex1--;
            determinePrevAndNextIndices1();
            executeUpdate1();
        }

        @Override
        public void onItemMoved(int indexBefore, int indexAfter, String mrl) {
            Log.i(TAG, "CustomMediaListItemMoved");
            if (mCurrentIndex1 == indexBefore) {
                mCurrentIndex1 = indexAfter;
                if (indexAfter > indexBefore)
                    mCurrentIndex--;
            } else if (indexBefore > mCurrentIndex1
                    && indexAfter <= mCurrentIndex1)
                mCurrentIndex1++;
            else if (indexBefore < mCurrentIndex1
                    && indexAfter > mCurrentIndex1)
                mCurrentIndex1--;

            // If we are in random mode, we completely reset the stored previous track
            // as their indices changed.
            mPrevious1.clear();

            determinePrevAndNextIndices1();
            executeUpdate1();
        }
    };

    public boolean canSwitchToVideo() {
        return hasCurrentMedia() && mMediaPlayer.getVideoTracksCount() > 0;
    }

    public boolean canSwitchToVideo1() {
        return hasCurrentMedia1() && mMediaPlayer1.getVideoTracksCount() > 0;
    }

    @MainThread
    public boolean switchToVideo() {
        Log.d(TAG,"switchToVideo..........mCurrentIndex....." + mCurrentIndex);
        MediaWrapper media = mMediaList.getMedia(mCurrentIndex);
        if (media == null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !canSwitchToVideo())
            return false;
        mVideoBackground = false;
        if (isVideoPlaying()) {//Player is already running, just send it an intent
            setVideoTrackEnabled(true);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    VideoPlayerActivity.getIntent(VideoPlayerActivity.PLAY_FROM_SERVICE,
                            media, false, mCurrentIndex));
        } else if (!mSwitchingToVideo) {//Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    media.getUri(), mCurrentIndex);
            mSwitchingToVideo = true;
        }
        return true;
    }

    public boolean switchToVideo1() {
        Log.d(TAG,"switchToVideo1..........mCurrentIndex1......." + mCurrentIndex1);
        MediaWrapper media = mMediaList1.getMedia(mCurrentIndex1);
        if (media == null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !canSwitchToVideo1())
            return false;
        mVideoBackground1 = false;
        if (isVideoPlaying1()) {//Player is already running, just send it an intent
            setVideoTrackEnabled1(true);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    VideoPlayerActivity.getIntent(VideoPlayerActivity.PLAY_FROM_SERVICE,
                            media, false, mCurrentIndex1));
        } else if (!mSwitchingToVideo1) {//Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    media.getUri(), mCurrentIndex1);
            mSwitchingToVideo1 = true;
        }
        return true;
    }

    private void executeUpdate() {
        executeUpdate(true);
    }

    private void executeUpdate1() {
        executeUpdate1(true);
    }

    private void executeUpdate(Boolean updateWidget) {
        for (Callback callback : mCallbacks) {
            callback.update();
        }
        if (updateWidget)
            updateWidget();
        updateMetadata();
    }

    private void executeUpdate1(Boolean updateWidget) {
        for (Callback callback : mCallbacks) {
            callback.update();
        }
        if (updateWidget)
            updateWidget1();
        updateMetadata1();
    }

    private void executeUpdateProgress() {
        for (Callback callback : mCallbacks) {
            callback.updateProgress();
        }
    }

    /**
     * Return the current media.
     *
     * @return The current media or null if there is not any.
     */
    @Nullable
    private MediaWrapper getCurrentMedia() {
        return mMediaList.getMedia(mCurrentIndex);
    }

    private MediaWrapper getCurrentMedia1() {
        return mMediaList1.getMedia(mCurrentIndex1);
    }

    /**
     * Alias for mCurrentIndex >= 0
     *
     * @return True if a media is currently loaded, false otherwise
     */
    private boolean hasCurrentMedia() {
        return isValidIndex(mCurrentIndex);
    }

    private boolean hasCurrentMedia1() {
        return isValidIndex1(mCurrentIndex1);
    }

    private final Handler mHandler = new AudioServiceHandler(this);

    private static class AudioServiceHandler extends WeakHandler<PlaybackService> {
        public AudioServiceHandler(PlaybackService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            PlaybackService service = getOwner();
            if(service == null) return;

            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (service.mCallbacks.size() > 0) {
                        removeMessages(SHOW_PROGRESS);
                        service.executeUpdateProgress();
                        sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
                    }
                    break;
                case SHOW_TOAST:
                    final Bundle bundle = msg.getData();
                    final String text = bundle.getString("text");
                    final int duration = bundle.getInt("duration");
                    Toast.makeText(VLCApplication.getAppContext(), text, duration).show();
                    break;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showNotification() {
        Log.d(TAG,"showNotification........");
        if (mMediaPlayer.getVLCVout().areViewsAttached()) {
            hideNotification(false);
            return;
        }
        try {
            boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
            MediaMetadataCompat metaData = mMediaSession.getController().getMetadata();
            String title = metaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artist = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
            String album = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
            Bitmap cover = coverOnLockscreen ?
                    metaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) :
                    AudioUtil.getCover(this, getCurrentMedia(), 512);
            if (cover == null)
                cover = BitmapFactory.decodeResource(VLCApplication.getAppContext().getResources(), R.drawable.icon);
            Notification notification;

            //Watch notification dismissed
            PendingIntent piStop = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_REMOTE_STOP), PendingIntent.FLAG_UPDATE_CURRENT);

            // add notification to status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setSmallIcon(R.drawable.ic_stat_vlc)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(title)
                .setContentText(artist + " - " + album)
                .setLargeIcon(cover)
                .setTicker(title + " - " + artist)
                .setAutoCancel(!mMediaPlayer.isPlaying())
                .setOngoing(mMediaPlayer.isPlaying())
                .setDeleteIntent(piStop);


            PendingIntent pendingIntent;
            if (mVideoBackground || (canSwitchToVideo() && !mMediaList.getMedia(mCurrentIndex).hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO))) {
                /* Resume VideoPlayerActivity from ACTION_REMOTE_SWITCH_VIDEO intent */
                final Intent notificationIntent = new Intent(ACTION_REMOTE_SWITCH_VIDEO);
                pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                /* Resume AudioPlayerActivity */

                final Intent notificationIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                notificationIntent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            builder.setContentIntent(pendingIntent);

            PendingIntent piBackward = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piPlay = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piForward = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(R.drawable.ic_previous_w, getString(R.string.previous), piBackward);
            if (mMediaPlayer.isPlaying())
                builder.addAction(R.drawable.ic_pause_w, getString(R.string.pause), piPlay);
            else
                builder.addAction(R.drawable.ic_play_w, getString(R.string.play), piPlay);
            builder.addAction(R.drawable.ic_next_w, getString(R.string.next), piForward);

            if (AndroidDevices.showMediaStyle) {
                builder.setStyle(new NotificationCompat.MediaStyle()
                                .setMediaSession(mMediaSession.getSessionToken())
                                .setShowActionsInCompactView(new int[] {0,1,2})
                                .setShowCancelButton(true)
                                .setCancelButtonIntent(piStop)
                );
            }

            notification = builder.build();

            startService(new Intent(this, PlaybackService.class));
            if (!AndroidUtil.isLolliPopOrLater() || mMediaPlayer.isPlaying())
                startForeground(3, notification);
            else {
                stopForeground(false);
                NotificationManagerCompat.from(this).notify(3, notification);
            }
        } catch (IllegalArgumentException e){
            // On somme crappy firmwares, shit can happen
            Log.e(TAG, "Failed to display notification", e);
        }
    }

    private void showNotification1() {
        Log.d(TAG,"showNotification1........");
        if (mMediaPlayer1.getVLCVout().areViewsAttached()) {
            hideNotification(false);
            return;
        }
        try {
            boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
            MediaMetadataCompat metaData = mMediaSession.getController().getMetadata();
            String title = metaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artist = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
            String album = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
            Bitmap cover = coverOnLockscreen ?
                    metaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) :
                    AudioUtil.getCover(this, getCurrentMedia1(), 512);
            if (cover == null)
                cover = BitmapFactory.decodeResource(VLCApplication.getAppContext().getResources(), R.drawable.icon);
            Notification notification;

            //Watch notification dismissed
            PendingIntent piStop = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_REMOTE_STOP), PendingIntent.FLAG_UPDATE_CURRENT);

            // add notification to status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setSmallIcon(R.drawable.ic_stat_vlc)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(title)
                    .setContentText(artist + " - " + album)
                    .setLargeIcon(cover)
                    .setTicker(title + " - " + artist)
                    .setAutoCancel(!mMediaPlayer1.isPlaying())
                    .setOngoing(mMediaPlayer1.isPlaying())
                    .setDeleteIntent(piStop);


            PendingIntent pendingIntent;
            if (mVideoBackground1 || (canSwitchToVideo1() && !mMediaList1.getMedia(mCurrentIndex1).hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO))) {
                /* Resume VideoPlayerActivity from ACTION_REMOTE_SWITCH_VIDEO intent */
                final Intent notificationIntent = new Intent(ACTION_REMOTE_SWITCH_VIDEO);
                pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                /* Resume AudioPlayerActivity */

                final Intent notificationIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                notificationIntent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            builder.setContentIntent(pendingIntent);

            PendingIntent piBackward = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piPlay = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piForward = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(R.drawable.ic_previous_w, getString(R.string.previous), piBackward);
            if (mMediaPlayer1.isPlaying())
                builder.addAction(R.drawable.ic_pause_w, getString(R.string.pause), piPlay);
            else
                builder.addAction(R.drawable.ic_play_w, getString(R.string.play), piPlay);
            builder.addAction(R.drawable.ic_next_w, getString(R.string.next), piForward);

            if (AndroidDevices.showMediaStyle) {
                builder.setStyle(new NotificationCompat.MediaStyle()
                        .setMediaSession(mMediaSession.getSessionToken())
                        .setShowActionsInCompactView(new int[] {0,1,2})
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(piStop)
                );
            }

            notification = builder.build();

            startService(new Intent(this, PlaybackService.class));
            if (!AndroidUtil.isLolliPopOrLater() || mMediaPlayer1.isPlaying())
                startForeground(3, notification);
            else {
                stopForeground(false);
                NotificationManagerCompat.from(this).notify(3, notification);
            }
        } catch (IllegalArgumentException e){
            // On somme crappy firmwares, shit can happen
            Log.e(TAG, "Failed to display notification", e);
        }
    }

    private void hideNotification() {
        hideNotification(true);
    }

    /**
     * Hides the VLC notification and stops the service.
     *
     * @param stopPlayback True to also stop playback at the same time. Set to false to preserve playback (e.g. for vout events)
     */
    private void hideNotification(boolean stopPlayback) {
        stopForeground(true);
        NotificationManagerCompat.from(this).cancel(3);
        if(stopPlayback)
            stopSelf();
    }

    @MainThread
    public void pause() {
        if (mPausable) {
            savePosition();
            mHandler.removeMessages(SHOW_PROGRESS);
            // hideNotification(); <-- see event handler
            mMediaPlayer.pause();
            broadcastMetadata();
        }
    }

    public void pause1() {
        if (mPausable1) {
            savePosition1();
            mHandler.removeMessages(SHOW_PROGRESS);
            // hideNotification(); <-- see event handler
            mMediaPlayer1.pause();
            broadcastMetadata1();
        }
    }

    @MainThread
    public void play() {
        Log.d(TAG,"play...........");
        if(hasCurrentMedia()) {
            mMediaPlayer.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            updateMetadata();
            updateWidget();
            broadcastMetadata();
        }
    }

    public void play1() {
        Log.d(TAG,"play1...........");
        if(hasCurrentMedia1()) {
            mMediaPlayer1.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            updateMetadata1();
            updateWidget1();
            broadcastMetadata1();
        }
    }
    @MainThread
    public void stopPlayback() {
        Log.d(TAG,"stopPlayback.......");
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        removePopup();
        if (mMediaPlayer == null)
            return;
        savePosition();
        final Media media = mMediaPlayer.getMedia();
        if (media != null) {
            media.setEventListener(null);
            mMediaPlayer.setEventListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.setMedia(null);
            media.release();
        }
        mMediaList.removeEventListener(mListEventListener);
        mCurrentIndex = -1;
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        broadcastMetadata();
        executeUpdate();
        executeUpdateProgress();
        changeAudioFocus(false);
    }

    public void stopPlayback1() {
        Log.d(TAG,"stopPlayback1.......");
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        removePopup1();
        if (mMediaPlayer1 == null)
            return;
        savePosition1();
        final Media media = mMediaPlayer1.getMedia();
        if (media != null) {
            media.setEventListener(null);
            mMediaPlayer1.setEventListener(null);
            mMediaPlayer1.stop();
            mMediaPlayer1.setMedia(null);
            media.release();
        }
        mMediaList1.removeEventListener(mListEventListener1);
        mCurrentIndex1 = -1;
        mPrevious1.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        broadcastMetadata1();
        executeUpdate1();
        executeUpdateProgress();
        changeAudioFocus(false);
    }

    @MainThread
    public void stop() {
        Log.d(TAG,"stop.......");
        stopPlayback();
        stopSelf();
    }

    public void stop1() {
        Log.d(TAG,"stop1.......");
        stopPlayback1();
        stopSelf();
    }

    private void determinePrevAndNextIndices() {
        determinePrevAndNextIndices(false);
    }

    private void determinePrevAndNextIndices1() {
        determinePrevAndNextIndices1(false);
    }

    private void determinePrevAndNextIndices(boolean expand) {
        if (expand) {
            mExpanding.set(true);
            mNextIndex = expand();
            mExpanding.set(false);
        } else {
            mNextIndex = -1;
        }
        mPrevIndex = -1;

        if (mNextIndex == -1) {
            // No subitems; play the next item.
            int size = mMediaList.size();
            mShuffling &= size > 2;

            // Repeating once doesn't change the index
            if (mRepeating == REPEAT_ONE) {
                mPrevIndex = mNextIndex = mCurrentIndex;
            } else {

                if(mShuffling) {
                    if(!mPrevious.isEmpty()){
                        mPrevIndex = mPrevious.peek();
                        while (!isValidIndex(mPrevIndex)) {
                            mPrevious.remove(mPrevious.size() - 1);
                            if (mPrevious.isEmpty()) {
                                mPrevIndex = -1;
                                break;
                            }
                            mPrevIndex = mPrevious.peek();
                        }
                    }
                    // If we've played all songs already in shuffle, then either
                    // reshuffle or stop (depending on RepeatType).
                    if(mPrevious.size() + 1 == size) {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex = -1;
                            return;
                        } else {
                            mPrevious.clear();
                            mRandom = new Random(System.currentTimeMillis());
                        }
                    }
                    if(mRandom == null) mRandom = new Random(System.currentTimeMillis());
                    // Find a new index not in mPrevious.
                    do
                    {
                        mNextIndex = mRandom.nextInt(size);
                    }
                    while(mNextIndex == mCurrentIndex || mPrevious.contains(mNextIndex));

                } else {
                    // normal playback
                    if(mCurrentIndex > 0)
                        mPrevIndex = mCurrentIndex - 1;
                    if(mCurrentIndex + 1 < size)
                        mNextIndex = mCurrentIndex + 1;
                    else {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex = -1;
                        } else {
                            mNextIndex = 0;
                        }
                    }
                }
            }
        }
    }

    private void determinePrevAndNextIndices1(boolean expand) {
        if (expand) {
            mExpanding1.set(true);
            mNextIndex1 = expand1();
            mExpanding1.set(false);
        } else {
            mNextIndex1 = -1;
        }
        mPrevIndex1 = -1;

        if (mNextIndex1 == -1) {
            // No subitems; play the next item.
            int size = mMediaList1.size();
            mShuffling &= size > 2;

            // Repeating once doesn't change the index
            if (mRepeating == REPEAT_ONE) {
                mPrevIndex1 = mNextIndex1 = mCurrentIndex1;
            } else {

                if(mShuffling) {
                    if(!mPrevious1.isEmpty()){
                        mPrevIndex1 = mPrevious1.peek();
                        while (!isValidIndex1(mPrevIndex1)) {
                            mPrevious1.remove(mPrevious1.size() - 1);
                            if (mPrevious1.isEmpty()) {
                                mPrevIndex1 = -1;
                                break;
                            }
                            mPrevIndex1 = mPrevious1.peek();
                        }
                    }
                    // If we've played all songs already in shuffle, then either
                    // reshuffle or stop (depending on RepeatType).
                    if(mPrevious1.size() + 1 == size) {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex1 = -1;
                            return;
                        } else {
                            mPrevious1.clear();
                            mRandom = new Random(System.currentTimeMillis());
                        }
                    }
                    if(mRandom == null) mRandom = new Random(System.currentTimeMillis());
                    // Find a new index not in mPrevious.
                    do
                    {
                        mNextIndex1 = mRandom.nextInt(size);
                    }
                    while(mNextIndex1 == mCurrentIndex1 || mPrevious1.contains(mNextIndex1));

                } else {
                    // normal playback
                    if(mCurrentIndex1 > 0)
                        mPrevIndex1 = mCurrentIndex1 - 1;
                    if(mCurrentIndex + 1 < size)
                        mNextIndex1 = mCurrentIndex1 + 1;
                    else {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex1 = -1;
                        } else {
                            mNextIndex1 = 0;
                        }
                    }
                }
            }
        }
    }

    private boolean isValidIndex(int position) {
        return position >= 0 && position < mMediaList.size();
    }

    private boolean isValidIndex1(int position) {
        return position >= 0 && position < mMediaList1.size();
    }

    private void initMediaSession() {
        Log.d(TAG,"initMediaSession..........");
         ComponentName mediaButtonEventReceiver = new ComponentName(this,
                    RemoteControlClientReceiver.class);
        mSessionCallback = new MediaSessionCallback();
        mMediaSession = new MediaSessionCompat(this, "VLC", mediaButtonEventReceiver, null);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setCallback(mSessionCallback);
        try {
            mMediaSession.setActive(true);
        } catch (NullPointerException e) {
            // Some versions of KitKat do not support AudioManager.registerMediaButtonIntent
            // with a PendingIntent. They will throw a NullPointerException, in which case
            // they should be able to activate a MediaSessionCompat with only transport
            // controls.
            mMediaSession.setActive(false);
            mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mMediaSession.setActive(true);
        }
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            play();
        }
        @Override
        public void onPause() {
            pause();
        }

        @Override
        public void onStop() {
            Log.d(TAG,"MediaSessionCallback.......");
            stop();
        }

        @Override
        public void onSkipToNext() {
            next();
        }

        @Override
        public void onSkipToPrevious() {
            previous();
        }

        @Override
        public void onSeekTo(long pos) {
            seek(pos);
        }

        @Override
        public void onFastForward() {
            next();
        }

        @Override
        public void onRewind() {
            previous();
        }
    }

    protected void updateMetadata() {
        Log.d(TAG,"updateMetadata........");
        MediaWrapper media = getCurrentMedia();
        if (media == null)
            return;
        if (mMediaSession == null)
            initMediaSession();
        String title = media.getNowPlaying();
        if (title == null)
            title = media.getTitle();
        boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
        MediaMetadataCompat.Builder bob = new MediaMetadataCompat.Builder();
        bob.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, MediaUtils.getMediaGenre(this, media))
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, media.getTrackNumber())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaUtils.getMediaArtist(this, media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, MediaUtils.getMediaReferenceArtist(this, media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaUtils.getMediaAlbum(this, media))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getLength());
        if (coverOnLockscreen) {
            Bitmap cover = AudioUtil.getCover(this, media, 512);
            if (cover != null && cover.getConfig() != null) //In case of format not supported
                bob.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover.copy(cover.getConfig(), false));
        }
        mMediaSession.setMetadata(bob.build());
    }

    protected void updateMetadata1() {
        Log.d(TAG,"updateMetadata1........");
        MediaWrapper media = getCurrentMedia1();
        if (media == null)
            return;
        if (mMediaSession == null)
            initMediaSession();
        String title = media.getNowPlaying();
        if (title == null)
            title = media.getTitle();
        boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
        MediaMetadataCompat.Builder bob = new MediaMetadataCompat.Builder();
        bob.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, MediaUtils.getMediaGenre(this, media))
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, media.getTrackNumber())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaUtils.getMediaArtist(this, media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, MediaUtils.getMediaReferenceArtist(this, media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaUtils.getMediaAlbum(this, media))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getLength());
        if (coverOnLockscreen) {
            Bitmap cover = AudioUtil.getCover(this, media, 512);
            if (cover != null && cover.getConfig() != null) //In case of format not supported
                bob.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover.copy(cover.getConfig(), false));
        }
        mMediaSession.setMetadata(bob.build());
    }

    protected void publishState(int state) {
        if (mMediaSession == null)
            return;
        PlaybackStateCompat.Builder bob = new PlaybackStateCompat.Builder();
        bob.setActions(PLAYBACK_ACTIONS);
        switch (state) {
            case MediaPlayer.Event.Playing:
                bob.setState(PlaybackStateCompat.STATE_PLAYING, -1, 1);
                break;
            case MediaPlayer.Event.Stopped:
                bob.setState(PlaybackStateCompat.STATE_STOPPED, -1, 0);
                break;
            default:
            bob.setState(PlaybackStateCompat.STATE_PAUSED, -1, 0);
        }
        PlaybackStateCompat pbState = bob.build();
        mMediaSession.setPlaybackState(pbState);
        mMediaSession.setActive(state != PlaybackStateCompat.STATE_STOPPED);
    }

    private void notifyTrackChanged() {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        updateMetadata();
        updateWidget();
        broadcastMetadata();
    }

    private void notifyTrackChanged1() {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        updateMetadata1();
        updateWidget1();
        broadcastMetadata1();
    }

    private void onMediaChanged() {
        notifyTrackChanged();

        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private void onMediaChanged1() {
        notifyTrackChanged1();

        saveCurrentMedia1();
        determinePrevAndNextIndices1();
    }

    private void onMediaListChanged() {
        saveMediaList();
        determinePrevAndNextIndices();
        executeUpdate();
    }

    private void onMediaListChanged1() {
        saveMediaList1();
        determinePrevAndNextIndices1();
        executeUpdate1();
    }

    @MainThread
    public void next() {
        Log.d(TAG,"next........");
        int size = mMediaList.size();

        mPrevious.push(mCurrentIndex);
        mCurrentIndex = mNextIndex;
        if (size == 0 || mCurrentIndex < 0 || mCurrentIndex >= size) {
            if (mCurrentIndex < 0)
                saveCurrentMedia();
            Log.w(TAG, "Warning: invalid next index, aborted !");
            //Close video player if started
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(VideoPlayerActivity.EXIT_PLAYER));
            stop();
            return;
        }
        mVideoBackground = !isVideoPlaying() && canSwitchToVideo();
        playIndex(mCurrentIndex, 0);
        saveCurrentMedia();
    }

    public void next1() {
        Log.d(TAG,"next1........");
        int size = mMediaList1.size();

        mPrevious1.push(mCurrentIndex1);
        mCurrentIndex1 = mNextIndex1;
        if (size == 0 || mCurrentIndex1 < 0 || mCurrentIndex1 >= size) {
            if (mCurrentIndex1 < 0)
                saveCurrentMedia1();
            Log.w(TAG, "Warning: invalid next index, aborted !");
            //Close video player if started
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(VideoPlayerActivity.EXIT_PLAYER));
            stop1();
            return;
        }
        mVideoBackground1 = !isVideoPlaying1() && canSwitchToVideo1();
        playIndex1(mCurrentIndex1, 0);
        saveCurrentMedia1();
        return;
    }

    @MainThread
    public void previous() {
        Log.d(TAG,"previous.......");
        if (hasPrevious() && mCurrentIndex > 0 &&
                (!mMediaPlayer.isSeekable() || mMediaPlayer.getTime() < 2000l)) {
            int size = mMediaList.size();
            mCurrentIndex = mPrevIndex;
            if (mPrevious.size() > 0)
                mPrevious.pop();
            if (size == 0 || mPrevIndex < 0 || mCurrentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !");
                stop();
                return;
            }
            playIndex(mCurrentIndex, 0);
            saveCurrentMedia();
        } else
            setPosition(0f);
    }

    public void previous1() {
        Log.d(TAG,"previous.......");
        if (hasPrevious1() && mCurrentIndex1 > 0 &&
                (!mMediaPlayer1.isSeekable() || mMediaPlayer1.getTime() < 2000l)) {
            int size = mMediaList1.size();
            mCurrentIndex1 = mPrevIndex1;
            if (mPrevious1.size() > 0)
                mPrevious1.pop();
            if (size == 0 || mPrevIndex1 < 0 || mCurrentIndex1 >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !");
                stop1();
                return;
            }
            playIndex1(mCurrentIndex1, 0);
            saveCurrentMedia1();
        } else
            setPosition1(0f);
    }

    @MainThread
    public void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        savePosition();
        determinePrevAndNextIndices();
    }

    public void shuffle1() {
        if (mShuffling)
            mPrevious1.clear();
        mShuffling = !mShuffling;
        savePosition1();
        determinePrevAndNextIndices1();
    }

    @MainThread
    public void setRepeatType(int repeatType) {
        mRepeating = repeatType;
        savePosition();
        determinePrevAndNextIndices();
    }

    public void setRepeatType1(int repeatType) {
        mRepeating = repeatType;
        savePosition1();
        determinePrevAndNextIndices1();
    }

    private void updateWidget() {
        updateWidgetState();
        updateWidgetCover();
    }

    private void updateWidget1() {
        updateWidgetState1();
        updateWidgetCover1();
    }

    private void updateWidgetState() {
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE);

        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            i.putExtra("title", media.getTitle());
            i.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : MediaUtils.getMediaArtist(this, media));
        }
        else {
            i.putExtra("title", getString(R.string.widget_default_text));
            i.putExtra("artist", "");
        }
        i.putExtra("isplaying", mMediaPlayer.isPlaying());

        sendBroadcast(i);
    }

    private void updateWidgetState1() {
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE);

        if (hasCurrentMedia1()) {
            final MediaWrapper media = getCurrentMedia1();
            i.putExtra("title", media.getTitle());
            i.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : MediaUtils.getMediaArtist(this, media));
        }
        else {
            i.putExtra("title", getString(R.string.widget_default_text));
            i.putExtra("artist", "");
        }
        i.putExtra("isplaying", mMediaPlayer1.isPlaying());

        sendBroadcast(i);
    }

    private void updateWidgetCover() {
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_COVER);

        Bitmap cover = hasCurrentMedia() ? AudioUtil.getCover(this, getCurrentMedia(), 64) : null;
        i.putExtra("cover", cover);

        sendBroadcast(i);
    }

    private void updateWidgetCover1() {
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_COVER);

        Bitmap cover = hasCurrentMedia1() ? AudioUtil.getCover(this, getCurrentMedia(), 64) : null;
        i.putExtra("cover", cover);

        sendBroadcast(i);
    }

    private void updateWidgetPosition(float pos) {
        // no more than one widget update for each 1/50 of the song
        long timestamp = Calendar.getInstance().getTimeInMillis();
        if (!hasCurrentMedia()
                || timestamp - mWidgetPositionTimestamp < getCurrentMedia().getLength() / 50)
            return;

        updateWidgetState();

        mWidgetPositionTimestamp = timestamp;
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_POSITION);
        i.putExtra("position", pos);
        sendBroadcast(i);
    }

    private void updateWidgetPosition1(float pos) {
        // no more than one widget update for each 1/50 of the song
        long timestamp = Calendar.getInstance().getTimeInMillis();
        if (!hasCurrentMedia1()
                || timestamp - mWidgetPositionTimestamp < getCurrentMedia1().getLength() / 50)
            return;

        updateWidgetState1();

        mWidgetPositionTimestamp = timestamp;
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_POSITION);
        i.putExtra("position", pos);
        sendBroadcast(i);
    }

    private void broadcastMetadata() {
        MediaWrapper media = getCurrentMedia();
        if (media == null || media.getType() != MediaWrapper.TYPE_AUDIO)
            return;

        boolean playing = mMediaPlayer.isPlaying();

        Intent broadcast = new Intent("com.android.music.metachanged");
        broadcast.putExtra("track", media.getTitle());
        broadcast.putExtra("artist", media.getArtist());
        broadcast.putExtra("album", media.getAlbum());
        broadcast.putExtra("duration", media.getLength());
        broadcast.putExtra("playing", playing);

        sendBroadcast(broadcast);
    }

    private void broadcastMetadata1() {
        MediaWrapper media = getCurrentMedia1();
        if (media == null || media.getType() != MediaWrapper.TYPE_AUDIO)
            return;

        boolean playing = mMediaPlayer1.isPlaying();

        Intent broadcast1 = new Intent("com.android.music.metachanged");
        broadcast1.putExtra("track", media.getTitle());
        broadcast1.putExtra("artist", media.getArtist());
        broadcast1.putExtra("album", media.getAlbum());
        broadcast1.putExtra("duration", media.getLength());
        broadcast1.putExtra("playing", playing);

        sendBroadcast(broadcast1);
    }

    public synchronized void loadLastPlaylist(int type) {
        Log.d(TAG,"loadLastPlaylist..........");
        boolean audio = type == TYPE_AUDIO;
        String currentMedia = mSettings.getString(audio ? "current_song" : "current_media", "");
        if (currentMedia.equals(""))
            return;
        String[] locations = mSettings.getString(audio ? "audio_list" : "media_list", "").split(" ");
        if (locations.length == 0)
            return;

        List<String> mediaPathList = new ArrayList<String>(locations.length);
        for (int i = 0 ; i < locations.length ; ++i)
            mediaPathList.add(Uri.decode(locations[i]));

        mShuffling = mSettings.getBoolean(audio ? "audio_shuffling" : "media_shuffling", false);
        mRepeating = mSettings.getInt(audio ? "audio_repeating" : "media_repeating", REPEAT_NONE);
        int position = mSettings.getInt(audio ? "position_in_audio_list" : "position_in_media_list",
                Math.max(0, mediaPathList.indexOf(currentMedia)));
        long time = mSettings.getLong(audio ? "position_in_song" : "position_in_media", -1);
        mSavedTime = time;
        // load playlist
        loadLocations(mediaPathList, position);
        if (time > 0)
            seek(time);
        if (!audio) {
            boolean paused = mSettings.getBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying());
            float rate = mSettings.getFloat(PreferencesActivity.VIDEO_SPEED, getRate());
            if (paused)
                pause();
            if (rate != 1.0f)
                setRate(rate, false);
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", 0);
        editor.putLong(audio ? "position_in_song" : "position_in_media", 0);
        Util.commitPreferences(editor);
    }

    public synchronized void loadLastPlaylist1(int type) {
        Log.d(TAG,"loadLastPlaylist1..........");
        boolean audio = type == TYPE_AUDIO;
        String currentMedia = mSettings.getString(audio ? "current_song" : "current_media", "");
        if (currentMedia.equals(""))
            return;
        String[] locations = mSettings.getString(audio ? "audio_list" : "media_list", "").split(" ");
        if (locations.length == 0)
            return;

        List<String> mediaPathList = new ArrayList<String>(locations.length);
        for (int i = 0 ; i < locations.length ; ++i)
            mediaPathList.add(Uri.decode(locations[i]));

        mShuffling = mSettings.getBoolean(audio ? "audio_shuffling" : "media_shuffling", false);
        mRepeating = mSettings.getInt(audio ? "audio_repeating" : "media_repeating", REPEAT_NONE);
        int position = mSettings.getInt(audio ? "position_in_audio_list" : "position_in_media_list",
                Math.max(0, mediaPathList.indexOf(currentMedia)));
        long time = mSettings.getLong(audio ? "position_in_song" : "position_in_media", -1);
        mSavedTime1 = time;
        // load playlist
        loadLocations(mediaPathList, position);
        if (time > 0)
            seek1(time);
        if (!audio) {
            boolean paused = mSettings.getBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying1());
            float rate = mSettings.getFloat(PreferencesActivity.VIDEO_SPEED, getRate1());
            if (paused)
                pause1();
            if (rate != 1.0f)
                setRate1(rate, false);
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", 0);
        editor.putLong(audio ? "position_in_song" : "position_in_media", 0);
        Util.commitPreferences(editor);
    }

    private synchronized void saveCurrentMedia() {
        Log.d(TAG,"saveCurrentMedia........");
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "current_song" : "current_media", mMediaList.getMRL(Math.max(mCurrentIndex, 0)));
        Util.commitPreferences(editor);
    }

    private synchronized void saveCurrentMedia1() {
        Log.d(TAG,"saveCurrentMedia1........");
        boolean audio = true;
        for (int i = 0; i < mMediaList1.size(); i++) {
            if (mMediaList1.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "current_song" : "current_media", mMediaList1.getMRL(Math.max(mCurrentIndex1, 0)));
        Util.commitPreferences(editor);
    }

    private synchronized void saveMediaList() {
        Log.d(TAG,"saveMediaList........");
        if (getCurrentMedia() == null)
            return;
        StringBuilder locations = new StringBuilder();
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
            locations.append(" ").append(Uri.encode(mMediaList.getMRL(i)));
        }
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "audio_list" : "media_list", locations.toString().trim());
        Util.commitPreferences(editor);
    }

    private synchronized void saveMediaList1() {
        Log.d(TAG,"saveMediaList1........");
        if (getCurrentMedia1() == null)
            return;
        StringBuilder locations = new StringBuilder();
        boolean audio = true;
        for (int i = 0; i < mMediaList1.size(); i++) {
            if (mMediaList1.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
            locations.append(" ").append(Uri.encode(mMediaList1.getMRL(i)));
        }
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "audio_list" : "media_list", locations.toString().trim());
        Util.commitPreferences(editor);
    }

    private synchronized void savePosition(){
        Log.d(TAG,"savePosition........getCurrentMedia()..." + getCurrentMedia());
        if (getCurrentMedia() == null)
            return;
        SharedPreferences.Editor editor = mSettings.edit();
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        editor.putBoolean(audio ? "audio_shuffling" : "media_shuffling", mShuffling);
        editor.putInt(audio ? "audio_repeating" : "media_repeating", mRepeating);
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", mCurrentIndex);
        editor.putLong(audio ? "position_in_song" : "position_in_media", mMediaPlayer.getTime());
        if(!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying());
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, getRate());
        }
        Util.commitPreferences(editor);
    }

    private synchronized void savePosition1(){
        Log.d(TAG,"savePosition1........getCurrentMedia1()..." + getCurrentMedia1());
        if (getCurrentMedia1() == null)
            return;
        SharedPreferences.Editor editor = mSettings.edit();
        boolean audio = true;
        for (int i = 0; i < mMediaList1.size(); i++) {
            if (mMediaList1.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        editor.putBoolean(audio ? "audio_shuffling" : "media_shuffling", mShuffling);
        editor.putInt(audio ? "audio_repeating" : "media_repeating", mRepeating);
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", mCurrentIndex1);
        editor.putLong(audio ? "position_in_song" : "position_in_media", mMediaPlayer1.getTime());
        if(!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying1());
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, getRate1());
        }
        Util.commitPreferences(editor);
    }

    private boolean validateLocation(String location)
    {
        Log.d(TAG,"validateLocation........");
        /* Check if the MRL contains a scheme */
        if (!location.matches("\\w+://.+"))
            location = "file://".concat(location);
        if (location.toLowerCase(Locale.ENGLISH).startsWith("file://")) {
            /* Ensure the file exists */
            File f;
            try {
                f = new File(new URI(location));
            } catch (URISyntaxException e) {
                return false;
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (!f.isFile())
                return false;
        }
        return true;
    }

    private void showToast(String text, int duration) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("text", text);
        bundle.putInt("duration", duration);
        msg.setData(bundle);
        msg.what = SHOW_TOAST;
        mHandler.sendMessage(msg);
    }

    @MainThread
    public boolean isPlaying() {
        Log.d(TAG,"isPlaying.......");
        return mMediaPlayer.isPlaying();
    }

    public boolean isPlaying1() {
        Log.d(TAG,"isPlaying1.......");
        return mMediaPlayer1.isPlaying();
    }

    @MainThread
    public boolean isSeekable() {
        return mSeekable;
    }

    public boolean isSeekable1() {
        return mSeekable1;
    }

    @MainThread
    public boolean isPausable() {
        return mPausable;
    }

    public boolean isPausable1() {
        return mPausable1;
    }

    @MainThread
    public boolean isShuffling() {
        return mShuffling;
    }

    @MainThread
    public boolean canShuffle()  {
        return getMediaListSize() > 2;
    }

    @MainThread
    public int getRepeatType() {
        return mRepeating;
    }

    @MainThread
    public boolean hasMedia()  {
        return hasCurrentMedia();
    }

    public boolean hasMedia1()  {
        return hasCurrentMedia1();
    }

    @MainThread
    public boolean hasPlaylist()  {
        return getMediaListSize() > 1;
    }

    public boolean hasPlaylist1()  {
        return getMediaListSize1() > 1;
    }

    @MainThread
    public boolean isVideoPlaying() {
        Log.d(TAG,"isVideoPlaying........");
        return mMediaPlayer.getVLCVout().areViewsAttached();
    }

    public boolean isVideoPlaying1() {
        Log.d(TAG,"isVideoPlaying1........");
        return mMediaPlayer1.getVLCVout().areViewsAttached();
    }

    @MainThread
    public String getAlbum() {
        if (hasCurrentMedia())
            return MediaUtils.getMediaAlbum(PlaybackService.this, getCurrentMedia());
        else
            return null;
    }

    public String getAlbum1() {
        if (hasCurrentMedia1())
            return MediaUtils.getMediaAlbum(PlaybackService.this, getCurrentMedia1());
        else
            return null;
    }

    @MainThread
    public String getArtist() {
        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            return media.getNowPlaying() != null ?
                    media.getTitle()
                    : MediaUtils.getMediaArtist(PlaybackService.this, media);
        } else
            return null;
    }

    public String getArtist1() {
        if (hasCurrentMedia1()) {
            final MediaWrapper media = getCurrentMedia1();
            return media.getNowPlaying() != null ?
                    media.getTitle()
                    : MediaUtils.getMediaArtist(PlaybackService.this, media);
        } else
            return null;
    }

    @MainThread
    public String getArtistPrev() {
        if (mPrevIndex != -1)
            return MediaUtils.getMediaArtist(PlaybackService.this, mMediaList.getMedia(mPrevIndex));
        else
            return null;
    }

    public String getArtistPrev1() {
        if (mPrevIndex1 != -1)
            return MediaUtils.getMediaArtist(PlaybackService.this, mMediaList1.getMedia(mPrevIndex1));
        else
            return null;
    }

    @MainThread
    public String getArtistNext() {
        if (mNextIndex != -1)
            return MediaUtils.getMediaArtist(PlaybackService.this, mMediaList.getMedia(mNextIndex));
        else
            return null;
    }

    public String getArtistNext1() {
        if (mNextIndex1 != -1)
            return MediaUtils.getMediaArtist(PlaybackService.this, mMediaList1.getMedia(mNextIndex1));
        else
            return null;
    }

    @MainThread
    public String getTitle() {
        if (hasCurrentMedia())
            return getCurrentMedia().getNowPlaying() != null ? getCurrentMedia().getNowPlaying() : getCurrentMedia().getTitle();
        else
            return null;
    }

    public String getTitle1() {
        if (hasCurrentMedia1())
            return getCurrentMedia1().getNowPlaying() != null ? getCurrentMedia1().getNowPlaying() : getCurrentMedia1().getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitlePrev() {
        if (mPrevIndex != -1)
            return mMediaList.getMedia(mPrevIndex).getTitle();
        else
            return null;
    }

    public String getTitlePrev1() {
        if (mPrevIndex1 != -1)
            return mMediaList1.getMedia(mPrevIndex1).getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitleNext() {
        if (mNextIndex != -1)
            return mMediaList.getMedia(mNextIndex).getTitle();
        else
            return null;
    }

    public String getTitleNext1() {
        if (mNextIndex1 != -1)
            return mMediaList1.getMedia(mNextIndex1).getTitle();
        else
            return null;
    }

    @MainThread
    public Bitmap getCover() {
        if (hasCurrentMedia()) {
            return AudioUtil.getCover(PlaybackService.this, getCurrentMedia(), 512);
        }
        return null;
    }

    public Bitmap getCover1() {
        if (hasCurrentMedia1()) {
            return AudioUtil.getCover(PlaybackService.this, getCurrentMedia1(), 512);
        }
        return null;
    }

    @MainThread
    public Bitmap getCoverPrev() {
        if (mPrevIndex != -1)
            return AudioUtil.getCover(PlaybackService.this, mMediaList.getMedia(mPrevIndex), 64);
        else
            return null;
    }

    public Bitmap getCoverPrev1() {
        if (mPrevIndex1 != -1)
            return AudioUtil.getCover(PlaybackService.this, mMediaList1.getMedia(mPrevIndex1), 64);
        else
            return null;
    }

    @MainThread
    public Bitmap getCoverNext() {
        if (mNextIndex != -1)
            return AudioUtil.getCover(PlaybackService.this, mMediaList.getMedia(mNextIndex), 64);
        else
            return null;
    }

    public Bitmap getCoverNext1() {
        if (mNextIndex1 != -1)
            return AudioUtil.getCover(PlaybackService.this, mMediaList1.getMedia(mNextIndex1), 64);
        else
            return null;
    }

    @MainThread
    public synchronized void addCallback(Callback cb) {
        if (!mCallbacks.contains(cb)) {
            mCallbacks.add(cb);
            if (hasCurrentMedia())
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    }

    public synchronized void addCallback1(Callback cb) {
        if (!mCallbacks.contains(cb)) {
            mCallbacks.add(cb);
            if (hasCurrentMedia1())
                mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    }

    @MainThread
    public synchronized void removeCallback(Callback cb) {
        mCallbacks.remove(cb);
    }

    @MainThread
    public long getTime() {
        return mMediaPlayer.getTime();
    }

    public long getTime1() {
        return mMediaPlayer1.getTime();
    }

    @MainThread
    public long getLength() {
        return  mMediaPlayer.getLength();
    }

    public long getLength1() {
        return  mMediaPlayer1.getLength();
    }

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     * @param position The position to start playing at
     */
    @MainThread
    public void loadLocations(List<String> mediaPathList, int position) {
        Log.d(TAG,"loadLocations..........");
        ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
        MediaDatabase db = MediaDatabase.getInstance();
        String location = null;

        for (int i = 0; i < mediaPathList.size(); i++) {
            location = mediaPathList.get(i);
            MediaWrapper mediaWrapper = db.getMedia(Uri.parse(location));
            if (mediaWrapper == null) {
                if (!validateLocation(location)) {
                    Log.w(TAG, "Invalid location " + location);
                    showToast(getResources().getString(R.string.invalid_location, location), Toast.LENGTH_SHORT);
                    continue;
                }
                Log.d(TAG, "Creating on-the-fly Media object for " + location);
                mediaWrapper = new MediaWrapper(Uri.parse(location));
            }
            mediaList.add(mediaWrapper);
        }
        Log.d(TAG,"MediaUtils.isSecondPopup........" + MediaUtils.isSecondPopup);
        if(MediaUtils.isSecondPopup % 2 == 1)
            load1(mediaList, position);
        else
            load(mediaList, position);
    }

    @MainThread
    public void loadUri(Uri uri) {
        String path = uri.toString();
        if (TextUtils.equals(uri.getScheme(), "content")) {
            path = "file://"+ FileUtils.getPathFromURI(uri);
        }
        loadLocation(path);
    }

    @MainThread
    public void loadLocation(String mediaPath) {
        Log.d(TAG,"loadLocation.....");
        loadLocations(Collections.singletonList(mediaPath), 0);
    }

    @MainThread
    public void load(List<MediaWrapper> mediaList, int position) {
        Log.d(TAG,"load..........");
        Log.v(TAG, "Loading position " + ((Integer) position).toString() + " in " + mediaList.toString());

        if (hasCurrentMedia())
            savePosition();

        mMediaList.removeEventListener(mListEventListener);
        mMediaList.clear();
        MediaWrapperList currentMediaList = mMediaList;

        mPrevious.clear();

        for (int i = 0; i < mediaList.size(); i++) {
            currentMediaList.add(mediaList.get(i));
        }

        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (isValidIndex(position)) {
            mCurrentIndex = position;
        } else {
            Log.w(TAG, "Warning: positon " + position + " out of bounds");
            mCurrentIndex = 0;
        }

        // Add handler after loading the list
        mMediaList.addEventListener(mListEventListener);

        playIndex(mCurrentIndex, 0);
        saveMediaList();
        onMediaChanged();
    }

    public void load1(List<MediaWrapper> mediaList, int position) {
        Log.d(TAG,"load1..........");
        Log.v(TAG, "Loading position " + ((Integer) position).toString() + " in " + mediaList.toString());

        if (hasCurrentMedia1())
            savePosition1();

        mMediaList1.removeEventListener(mListEventListener1);
        mMediaList1.clear();
        MediaWrapperList currentMediaList = mMediaList1;

        mPrevious1.clear();

        for (int i = 0; i < mediaList.size(); i++) {
            currentMediaList.add(mediaList.get(i));
        }

        if (mMediaList1.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (isValidIndex1(position)) {
            mCurrentIndex1 = position;
        } else {
            Log.w(TAG, "Warning: positon " + position + " out of bounds");
            mCurrentIndex1 = 0;
        }

        // Add handler after loading the list
        mMediaList1.addEventListener(mListEventListener1);

        playIndex1(mCurrentIndex1, 0);
        saveMediaList1();
        onMediaChanged1();
    }

    @MainThread
    public void load(MediaWrapper media) {
        Log.d(TAG,"load123.......");
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load(arrayList, 0);
    }

    public void load1(MediaWrapper media) {
        Log.d(TAG,"load1234.......");
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load1(arrayList, 0);
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param index The index of the media
     * @param flags LibVLC.MEDIA_* flags
     */
    public void playIndex(int index, int flags) {
        Log.d(TAG,"playIndex........");
        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (isValidIndex(index)) {
            mCurrentIndex = index;
        } else {
            Log.w(TAG, "Warning: index " + index + " out of bounds");
            mCurrentIndex = 0;
        }

        String mrl = mMediaList.getMRL(index);
        if (mrl == null)
            return;
        final MediaWrapper mw = mMediaList.getMedia(index);
        if (mw == null)
            return;

        boolean isVideoPlaying = isVideoPlaying();
        if (!mVideoBackground && mw.getType() == MediaWrapper.TYPE_VIDEO && isVideoPlaying)
            mw.addFlags(MediaWrapper.MEDIA_VIDEO);

        if (mVideoBackground)
            mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);

        /* Pausable and seekable are true by default */
        mParsed = false;
        mSwitchingToVideo = false;
        mPausable = mSeekable = true;
        final Media media = new Media(VLCInstance.get(), mw.getUri());
        VLCOptions.setMediaOptions(media, this, flags | mw.getFlags());

        if (mw.getSlaves() != null) {
            for (Media.Slave slave : mw.getSlaves())
                media.addSlave(slave);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    MediaDatabase.getInstance().saveSlaves(mw);
                }
            });
        }
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Media.Slave> list = MediaDatabase.getInstance().getSlaves(mw.getLocation());
                for (Media.Slave slave : list)
                    mMediaPlayer.addSlave(slave.type, Uri.parse(slave.uri), false);
            }
        });

        media.setEventListener(mMediaListener);
        mMediaPlayer.setMedia(media);
        media.release();

        if (mw .getType() != MediaWrapper.TYPE_VIDEO || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)
                || isVideoPlaying) {
            mMediaPlayer.setEqualizer(VLCOptions.getEqualizer(this));
            mMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
            changeAudioFocus(true);
            mMediaPlayer.setEventListener(mMediaPlayerListener);
            if (!isVideoPlaying && mMediaPlayer.getRate() == 1.0F && mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, true))
                setRate(mSettings.getFloat(PreferencesActivity.KEY_AUDIO_PLAYBACK_RATE, 1.0F), true);
            mMediaPlayer.play();

            determinePrevAndNextIndices();
            if (mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true))
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        MediaDatabase.getInstance().addHistoryItem(mw);
                    }
                });
        } else {//Start VideoPlayer for first video, it will trigger playIndex when ready.
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    getCurrentMediaWrapper().getUri(), mCurrentIndex);
        }
    }

    public void playIndex1(int index, int flags) {
        Log.d(TAG,"playIndex1........");
        if (mMediaList1.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (isValidIndex1(index)) {
            mCurrentIndex1 = index;
        } else {
            Log.w(TAG, "Warning: index " + index + " out of bounds");
            mCurrentIndex1 = 0;
        }

        String mrl = mMediaList1.getMRL(index);
        if (mrl == null)
            return;
        final MediaWrapper mw = mMediaList1.getMedia(index);
        if (mw == null)
            return;

        boolean isVideoPlaying1 = isVideoPlaying1();
        if (!mVideoBackground1 && mw.getType() == MediaWrapper.TYPE_VIDEO && isVideoPlaying1)
            mw.addFlags(MediaWrapper.MEDIA_VIDEO);

        if (mVideoBackground1)
            mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);

        /* Pausable and seekable are true by default */
        mParsed1 = false;
        mSwitchingToVideo1 = false;
        mPausable1 = mSeekable1 = true;
        final Media media = new Media(VLCInstance.get(), mw.getUri());
        VLCOptions.setMediaOptions(media, this, flags | mw.getFlags());

        if (mw.getSlaves() != null) {
            for (Media.Slave slave : mw.getSlaves())
                media.addSlave(slave);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    MediaDatabase.getInstance().saveSlaves(mw);
                }
            });
        }
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Media.Slave> list = MediaDatabase.getInstance().getSlaves(mw.getLocation());
                for (Media.Slave slave : list)
                    mMediaPlayer1.addSlave(slave.type, Uri.parse(slave.uri), false);
            }
        });

        media.setEventListener(mMediaListener1);
        mMediaPlayer1.setMedia(media);
        media.release();

        if (mw .getType() != MediaWrapper.TYPE_VIDEO || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)
                || isVideoPlaying1) {
            mMediaPlayer1.setEqualizer(VLCOptions.getEqualizer(this));
            mMediaPlayer1.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
            changeAudioFocus(true);
            mMediaPlayer1.setEventListener(mMediaPlayerListener1);
            if (!isVideoPlaying1 && mMediaPlayer1.getRate() == 1.0F && mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, true))
                setRate1(mSettings.getFloat(PreferencesActivity.KEY_AUDIO_PLAYBACK_RATE, 1.0F), true);
            mMediaPlayer1.play();

            determinePrevAndNextIndices1();
            if (mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true))
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        MediaDatabase.getInstance().addHistoryItem(mw);
                    }
                });
        } else {//Start VideoPlayer for first video, it will trigger playIndex when ready.
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    getCurrentMediaWrapper1().getUri(), mCurrentIndex1);
        }
    }

    /**
     * Use this function to play a media inside whatever MediaList LibVLC is following.
     *
     * Unlike load(), it does not import anything into the primary list.
     */
    @MainThread
    public void playIndex(int index) {
        playIndex(index, 0);
    }

    public void playIndex1(int index) {
        playIndex1(index, 0);
    }

    /**
     * Use this function to show an URI in the audio interface WITHOUT
     * interrupting the stream.
     *
     * Mainly used by VideoPlayerActivity in response to loss of video track.
     */
    @MainThread
    public void showWithoutParse(int index) {
        Log.d(TAG,"showWithoutParse..........");
        setVideoTrackEnabled(false);
        MediaWrapper media = mMediaList.getMedia(index);

        if(media == null || !mMediaPlayer.isPlaying())
            return;
        // Show an URI without interrupting/losing the current stream
        Log.v(TAG, "Showing index " + index + " with playing URI " + media.getUri());
        mCurrentIndex = index;

        notifyTrackChanged();
        showNotification();
    }

    public void showWithoutParse1(int index) {
        Log.d(TAG,"showWithoutParse1..........");
        setVideoTrackEnabled1(false);
        MediaWrapper media = mMediaList1.getMedia(index);

        if(media == null || !mMediaPlayer1.isPlaying())
            return;
        // Show an URI without interrupting/losing the current stream
        Log.v(TAG, "Showing index " + index + " with playing URI " + media.getUri());
        mCurrentIndex1 = index;

        notifyTrackChanged1();
        showNotification1();
    }

    @MainThread
    public void switchToPopup(int index) {
        Log.d(TAG,"switchToPopup.......");
        showWithoutParse(index);
        showPopup();
    }

    public void switchToPopup1(int index) {
        Log.d(TAG,"switchToPopup1.......");
        showWithoutParse1(index);
        showPopup1();
    }

    @MainThread
    public void removePopup() {
        Log.d(TAG,"removePopup.......");
        if (mPopupManager != null) {
            mPopupManager.removePopup();
        }
        mPopupManager = null;
    }

    public void removePopup1() {
        Log.d(TAG,"removePopup1.......");
        if (mPopupManager1 != null) {
            mPopupManager1.removePopup1();
        }
        mPopupManager1 = null;
    }

    @MainThread
    public boolean isPlayingPopup() {
        return mPopupManager != null;
    }

    @MainThread
    public boolean isPlayingPopup1() {
        return mPopupManager1 != null;
    }

    @MainThread
    public void showPopup() {
        Log.d(TAG,"showPopup.......");
        if (mPopupManager == null)
            mPopupManager = new PopupManager(this);
        mPopupManager.showPopup();
    }

    public void showPopup1() {
        Log.d(TAG,"showPopup1.......");
        if (mPopupManager1 == null)
            mPopupManager1 = new PopupManager(this);
        mPopupManager1.showPopup1();
    }

    public void setVideoTrackEnabled(boolean enabled) {
        Log.d(TAG,"setVideoTrackEnabled.........");
        if (!hasMedia() || !isPlaying())
            return;
        if (enabled)
            getCurrentMedia().addFlags(MediaWrapper.MEDIA_VIDEO);
        else
            getCurrentMedia().removeFlags(MediaWrapper.MEDIA_VIDEO);
        mMediaPlayer.setVideoTrackEnabled(enabled);
    }

    public void setVideoTrackEnabled1(boolean enabled) {
        Log.d(TAG,"setVideoTrackEnabled1.........");
        if (!hasMedia1() || !isPlaying1())
            return;
        if (enabled)
            getCurrentMedia1().addFlags(MediaWrapper.MEDIA_VIDEO);
        else
            getCurrentMedia1().removeFlags(MediaWrapper.MEDIA_VIDEO);
        mMediaPlayer1.setVideoTrackEnabled(enabled);
    }
    /**
     * Append to the current existing playlist
     */
    @MainThread
    public void append(List<MediaWrapper> mediaList) {
        if (!hasCurrentMedia())
        {
            load(mediaList, 0);
            return;
        }

        for (int i = 0; i < mediaList.size(); i++) {
            MediaWrapper mediaWrapper = mediaList.get(i);
            mMediaList.add(mediaWrapper);
        }
        onMediaListChanged();
    }

    public void append1(List<MediaWrapper> mediaList) {
        if (!hasCurrentMedia1())
        {
            load1(mediaList, 0);
            return;
        }

        for (int i = 0; i < mediaList.size(); i++) {
            MediaWrapper mediaWrapper = mediaList.get(i);
            mMediaList1.add(mediaWrapper);
        }
        onMediaListChanged1();
    }

    @MainThread
    public void append(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append(arrayList);
    }

    public void append1(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append1(arrayList);
    }

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    public void moveItem(int positionStart, int positionEnd) {
        mMediaList.move(positionStart, positionEnd);
        PlaybackService.this.saveMediaList();
    }

    public void moveItem1(int positionStart, int positionEnd) {
        mMediaList1.move(positionStart, positionEnd);
        PlaybackService.this.saveMediaList1();
    }

    @MainThread
    public void insertItem(int position, MediaWrapper mw) {
        mMediaList.insert(position, mw);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    public void insertItem1(int position, MediaWrapper mw) {
        mMediaList1.insert(position, mw);
        saveMediaList1();
        determinePrevAndNextIndices1();
    }


    @MainThread
    public void remove(int position) {
        mMediaList.remove(position);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    public void remove1(int position) {
        mMediaList1.remove(position);
        saveMediaList1();
        determinePrevAndNextIndices1();
    }

    @MainThread
    public void removeLocation(String location) {
        mMediaList.remove(location);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    public void removeLocation1(String location) {
        mMediaList1.remove(location);
        saveMediaList1();
        determinePrevAndNextIndices1();
    }

    public int getMediaListSize() {
        return mMediaList.size();
    }

    public int getMediaListSize1() {
        return mMediaList1.size();
    }

    @MainThread
    public List<MediaWrapper> getMedias() {
        final ArrayList<MediaWrapper> ml = new ArrayList<MediaWrapper>();
        for (int i = 0; i < mMediaList.size(); i++) {
            ml.add(mMediaList.getMedia(i));
        }
        return ml;
    }

    public List<MediaWrapper> getMedias1() {
        final ArrayList<MediaWrapper> ml = new ArrayList<MediaWrapper>();
        for (int i = 0; i < mMediaList1.size(); i++) {
            ml.add(mMediaList1.getMedia(i));
        }
        return ml;
    }

    @MainThread
    public List<String> getMediaLocations() {
        ArrayList<String> medias = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            medias.add(mMediaList.getMRL(i));
        }
        return medias;
    }

    public List<String> getMediaLocations1() {
        ArrayList<String> medias = new ArrayList<String>();
        for (int i = 0; i < mMediaList1.size(); i++) {
            medias.add(mMediaList1.getMRL(i));
        }
        return medias;
    }

    @MainThread
    public String getCurrentMediaLocation() {
        return mMediaList.getMRL(mCurrentIndex);
    }

    public String getCurrentMediaLocation1() {
        return mMediaList1.getMRL(mCurrentIndex1);
    }

    @MainThread
    public int getCurrentMediaPosition() {
        return mCurrentIndex;
    }

    public int getCurrentMediaPosition1() {
        return mCurrentIndex1;
    }

    @MainThread
    public MediaWrapper getCurrentMediaWrapper() {
        return PlaybackService.this.getCurrentMedia();
    }

    public MediaWrapper getCurrentMediaWrapper1() {
        return PlaybackService.this.getCurrentMedia1();
    }

    @MainThread
    public void setTime(long time) {
        if (mSeekable)
            mMediaPlayer.setTime(time);
    }

    public void setTime1(long time) {
        if (mSeekable1)
            mMediaPlayer1.setTime(time);
    }

    @MainThread
    public boolean hasNext() {
        return mNextIndex != -1;
    }

    public boolean hasNext1() {
        return mNextIndex1 != -1;
    }

    @MainThread
    public boolean hasPrevious() {
        return mPrevIndex != -1;
    }

    public boolean hasPrevious1() {
        return mPrevIndex1 != -1;
    }

    @MainThread
    public void detectHeadset(boolean enable)  {
        mDetectHeadset = enable;
    }

    @MainThread
    public float getRate()  {
        return mMediaPlayer.getRate();
    }

    @MainThread
    public float getRate1()  {
        return mMediaPlayer1.getRate();
    }

    @MainThread
    public void setRate(float rate, boolean save) {
        Log.d(TAG,"setRate.........");
        mMediaPlayer.setRate(rate);
        if (save && mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, true))
            Util.commitPreferences(mSettings.edit().putFloat(PreferencesActivity.KEY_AUDIO_PLAYBACK_RATE, rate));
    }

    public void setRate1(float rate, boolean save) {
        Log.d(TAG,"setRate1.........");
        mMediaPlayer1.setRate(rate);
        if (save && mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, true))
            Util.commitPreferences(mSettings.edit().putFloat(PreferencesActivity.KEY_AUDIO_PLAYBACK_RATE, rate));
    }

    @MainThread
    public void navigate(int where) {
        mMediaPlayer.navigate(where);
    }

    @MainThread
    public MediaPlayer.Chapter[] getChapters(int title) {
        return mMediaPlayer.getChapters(title);
    }

    public MediaPlayer.Chapter[] getChapters1(int title) {
        return mMediaPlayer1.getChapters(title);
    }

    @MainThread
    public MediaPlayer.Title[] getTitles() {
        return mMediaPlayer.getTitles();
    }

    public MediaPlayer.Title[] getTitles1() {
        return mMediaPlayer1.getTitles();
    }

    @MainThread
    public int getChapterIdx() {
        return mMediaPlayer.getChapter();
    }

    public int getChapterIdx1() {
        return mMediaPlayer1.getChapter();
    }

    @MainThread
    public void setChapterIdx(int chapter) {
        mMediaPlayer.setChapter(chapter);
    }

    public void setChapterIdx1(int chapter) {
        mMediaPlayer1.setChapter(chapter);
    }

    @MainThread
    public int getTitleIdx() {
        return mMediaPlayer.getTitle();
    }

    public int getTitleIdx1() {
        return mMediaPlayer1.getTitle();
    }

    @MainThread
    public void setTitleIdx(int title) {
        mMediaPlayer.setTitle(title);
    }

    public void setTitleIdx1(int title) {
        mMediaPlayer1.setTitle(title);
    }

    @MainThread
    public int getVolume() {
        return mMediaPlayer.getVolume();
    }

    public int getVolume1() {
        return mMediaPlayer1.getVolume();
    }

    @MainThread
    public int setVolume(int volume) {
        return mMediaPlayer.setVolume(volume);
    }

    public int setVolume1(int volume) {
        return mMediaPlayer1.setVolume(volume);
    }

    @MainThread
    public void seek(long position) {
        seek(position, getLength());
    }

    public void seek1(long position) {
        seek1(position, getLength());
    }

    @MainThread
    public void seek(long position, double length) {
        if (length > 0.0D)
            setPosition((float) (position/length));
        else
            setTime(position);
    }

    public void seek1(long position, double length) {
        if (length > 0.0D)
            setPosition1((float) (position/length));
        else
            setTime1(position);
    }

    @MainThread
    public void saveTimeToSeek(long time) {
        mSavedTime = time;
    }

    public void saveTimeToSeek1(long time) {
        mSavedTime1 = time;
    }

    @MainThread
    public void setPosition(float pos) {
        if (mSeekable)
            mMediaPlayer.setPosition(pos);
    }

    public void setPosition1(float pos) {
        if (mSeekable1)
            mMediaPlayer1.setPosition(pos);
    }

    @MainThread
    public int getAudioTracksCount() {
        return mMediaPlayer.getAudioTracksCount();
    }

    public int getAudioTracksCount1() {
        return mMediaPlayer1.getAudioTracksCount();
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getAudioTracks() {
        return mMediaPlayer.getAudioTracks();
    }

    public MediaPlayer.TrackDescription[] getAudioTracks1() {
        return mMediaPlayer1.getAudioTracks();
    }

    @MainThread
    public int getAudioTrack() {
        return mMediaPlayer.getAudioTrack();
    }

    public int getAudioTrack1() {
        return mMediaPlayer1.getAudioTrack();
    }

    @MainThread
    public boolean setAudioTrack(int index) {
        return mMediaPlayer.setAudioTrack(index);
    }

    public boolean setAudioTrack1(int index) {
        return mMediaPlayer1.setAudioTrack(index);
    }

    @MainThread
    public int getVideoTracksCount() {
        return mMediaPlayer.getVideoTracksCount();
    }

    public int getVideoTracksCount1() {
        return mMediaPlayer1.getVideoTracksCount();
    }

    @MainThread
    public boolean addSubtitleTrack(String path, boolean select) {
        return mMediaPlayer.addSlave(Media.Slave.Type.Subtitle, path, select);
    }

    public boolean addSubtitleTrack1(String path, boolean select) {
        return mMediaPlayer1.addSlave(Media.Slave.Type.Subtitle, path, select);
    }

    @MainThread
    public boolean addSubtitleTrack(Uri uri,boolean select) {
        return mMediaPlayer.addSlave(Media.Slave.Type.Subtitle, uri, select);
    }

    public boolean addSubtitleTrack1(Uri uri,boolean select) {
        return mMediaPlayer1.addSlave(Media.Slave.Type.Subtitle, uri, select);
    }

    @MainThread
    public boolean addSubtitleTrack(String path) {
        return addSubtitleTrack(path, false);
    }

    @MainThread
    public boolean addSubtitleTrack(Uri uri) {
        return addSubtitleTrack(uri, false);
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getSpuTracks() {
        return mMediaPlayer.getSpuTracks();
    }

    public MediaPlayer.TrackDescription[] getSpuTracks1() {
        return mMediaPlayer1.getSpuTracks();
    }

    @MainThread
    public int getSpuTrack() {
        return mMediaPlayer.getSpuTrack();
    }

    public int getSpuTrack1() {
        return mMediaPlayer1.getSpuTrack();
    }

    @MainThread
    public boolean setSpuTrack(int index) {
        return mMediaPlayer.setSpuTrack(index);
    }

    public boolean setSpuTrack1(int index) {
        return mMediaPlayer1.setSpuTrack(index);
    }

    @MainThread
    public int getSpuTracksCount() {
        return mMediaPlayer.getSpuTracksCount();
    }

    public int getSpuTracksCount1() {
        return mMediaPlayer1.getSpuTracksCount();
    }

    @MainThread
    public boolean setAudioDelay(long delay) {
        return mMediaPlayer.setAudioDelay(delay);
    }

    public boolean setAudioDelay1(long delay) {
        return mMediaPlayer1.setAudioDelay(delay);
    }

    @MainThread
    public long getAudioDelay() {
        return mMediaPlayer.getAudioDelay();
    }

    public long getAudioDelay1() {
        return mMediaPlayer1.getAudioDelay();
    }

    @MainThread
    public boolean setSpuDelay(long delay) {
        return mMediaPlayer.setSpuDelay(delay);
    }

    public boolean setSpuDelay1(long delay) {
        return mMediaPlayer1.setSpuDelay(delay);
    }

    @MainThread
    public long getSpuDelay() {
        return mMediaPlayer.getSpuDelay();
    }

    public long getSpuDelay1() {
        return mMediaPlayer1.getSpuDelay();
    }

    @MainThread
    public void setEqualizer(MediaPlayer.Equalizer equalizer) {
        mMediaPlayer.setEqualizer(equalizer);
    }

    public void setEqualizer1(MediaPlayer.Equalizer equalizer) {
        mMediaPlayer.setEqualizer(equalizer);
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    public int expand() {
        Log.d(TAG,"expand..........");
        final Media media = mMediaPlayer.getMedia();
        if (media == null)
            return -1;
        final MediaList ml = media.subItems();
        media.release();
        int ret;

        if (ml.getCount() > 0) {
            mMediaList.remove(mCurrentIndex);
            for (int i = ml.getCount() - 1; i >= 0; --i) {
                final Media child = ml.getMediaAt(i);
                child.parse();
                mMediaList.insert(mCurrentIndex, new MediaWrapper(child));
                child.release();
            }
            ret = 0;
        } else {
            ret = -1;
        }
        ml.release();
        return ret;
    }

    public int expand1() {
        Log.d(TAG,"expand1..........");
        final Media media = mMediaPlayer1.getMedia();
        if (media == null)
            return -1;
        final MediaList ml = media.subItems();
        media.release();
        int ret;

        if (ml.getCount() > 0) {
            mMediaList.remove(mCurrentIndex1);
            for (int i = ml.getCount() - 1; i >= 0; --i) {
                final Media child = ml.getMediaAt(i);
                child.parse();
                mMediaList1.insert(mCurrentIndex1, new MediaWrapper(child));
                child.release();
            }
            ret = 0;
        } else {
            ret = -1;
        }
        ml.release();
        return ret;
    }

    public void restartMediaPlayer() {
        Log.d(TAG,"restartMediaPlayer.........");
        stop();
        mMediaPlayer.release();
        mMediaPlayer = newMediaPlayer();
        /* TODO RESUME */
    }

    public void restartMediaPlayer1() {
        Log.d(TAG,"restartMediaPlayer1.........");
        stop1();
        mMediaPlayer1.release();
        mMediaPlayer1 = newMediaPlayer1();
        /* TODO RESUME */
    }

    public static class Client {
        public static final String TAG1 = "PlaybackService.Client";

        @MainThread
        public interface Callback {
            void onConnected(PlaybackService service);
            void onDisconnected();
        }

        private boolean mBound = false;
        private final Callback mCallback;
        private final Context mContext;

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                if (!mBound)
                    return;

                final PlaybackService service = PlaybackService.getService(iBinder);
                if (service != null)
                    mCallback.onConnected(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mBound = false;
                mCallback.onDisconnected();
            }
        };

        private static Intent getServiceIntent(Context context) {
            return new Intent(context, PlaybackService.class);
        }

        private static void startService(Context context) {
            context.startService(getServiceIntent(context));
        }

        private static void stopService(Context context) {
            context.stopService(getServiceIntent(context));
        }

        public Client(Context context, Callback callback) {
            if (context == null || callback == null)
                throw new IllegalArgumentException("Context and callback can't be null");
            mContext = context;
            mCallback = callback;
        }

        @MainThread
        public void connect() {
            if (mBound)
                throw new IllegalStateException("already connected");
            startService(mContext);
            mBound = mContext.bindService(getServiceIntent(mContext), mServiceConnection, BIND_AUTO_CREATE);
        }

        @MainThread
        public void disconnect() {
            if (mBound) {
                mBound = false;
                mContext.unbindService(mServiceConnection);
            }
        }

        public static void restartService(Context context) {
            stopService(context);
            startService(context);
        }
    }

    int mPhoneEvents = PhoneStateListener.LISTEN_CALL_STATE;
    PhoneStateListener mPhoneStateListener;

    private void initPhoneListener() {
        mPhoneStateListener = new PhoneStateListener(){
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (!mMediaPlayer.isPlaying() || !hasCurrentMedia())
                    return;
                if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK)
                    pause();
                else if (state == TelephonyManager.CALL_STATE_IDLE)
                    play();
            }
        };
    }
}
