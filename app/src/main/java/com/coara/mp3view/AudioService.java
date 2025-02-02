package com.coara.mp3view;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.net.Uri;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

public class AudioService extends Service {
    private static final String TAG = "AudioService";
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    private String playbackStatus = "STOP";

    // 通知からの操作を処理するBroadcastReceiver
    private final BroadcastReceiver notificationActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("ACTION");
            if (action != null) {
                switch (action) {
                    case "PLAY":
                        if (playbackStatus.equals("PAUSE")) {
                            if (mediaPlayer != null) {
                                mediaPlayer.start();
                            }
                        } else {
                            if (currentFile != null) {
                                playAudio(currentFile);
                            }
                        }
                        playbackStatus = "PLAY";
                        updateNotification();
                        sendStateBroadcast("PLAY");
                        break;
                    case "PAUSE":
                        pauseAudio();
                        break;
                    case "STOP":
                        stopAudio();
                        break;
                }
            }
        }
    };

    public class AudioBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannel(); // 通知チャンネルを作成
        updateNotification(); // サービス開始時に通知を表示
        IntentFilter filter = new IntentFilter("AUDIO_CONTROL");
        registerReceiver(notificationActionReceiver, filter); // 通知からの操作を処理するレシーバを登録
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void playAudio(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "Invalid file path provided for playback");
            return;
        }

        Log.d(TAG, "playAudio: " + filePath);

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        mediaPlayer = new MediaPlayer();
        try {
            Uri uri = Uri.parse(filePath);
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            currentFile = filePath;
            playbackStatus = "PLAY";
            updateNotification();
            sendStateBroadcast("PLAY");
        } catch (Exception e) {
            Log.e(TAG, "Error in playAudio", e);
            playbackStatus = "STOP";
            updateNotification();
        }
    }

    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d(TAG, "pauseAudio");
            mediaPlayer.pause();
            playbackStatus = "PAUSE";
            updateNotification();
            sendStateBroadcast("PAUSE");
        }
    }

    public void stopAudio() {
        if (mediaPlayer != null) {
            Log.d(TAG, "stopAudio");
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            currentFile = null;
            playbackStatus = "STOP";
            updateNotification();
            sendStateBroadcast("STOP");
        }
    }

    private void updateNotification() {
        int iconRes;
        String notificationText = "No track playing";
        String currentTime = "00:00";
        String duration = "00:00";

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            iconRes = R.drawable.ic_playing;
            notificationText = "Now playing: " + currentFile;
            currentTime = formatTime(mediaPlayer.getCurrentPosition() / 1000);
            duration = formatTime(mediaPlayer.getDuration() / 1000);
        } else if (playbackStatus.equals("PAUSE")) {
            iconRes = R.drawable.ic_paused;
            notificationText = "Paused: " + currentFile;
            currentTime = formatTime(mediaPlayer.getCurrentPosition() / 1000);
            duration = formatTime(mediaPlayer.getDuration() / 1000);
        } else {
            iconRes = R.drawable.ic_stopped;
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MP3 Player")
                .setContentText(notificationText)
                .setSmallIcon(iconRes)
                .addAction(createAction("▶ Play", "PLAY"))
                .addAction(createAction("⏸ Pause", "PAUSE"))
                .addAction(createAction("⏹ Stop", "STOP"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(playbackStatus.equals("PLAY"))
                .setContentText(notificationText + " - " + currentTime + "/" + duration)
                .setContentIntent(getPendingIntent())
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private NotificationCompat.Action createAction(String title, String action) {
        Intent intent = new Intent("AUDIO_CONTROL");
        intent.putExtra("ACTION", action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, action.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(0, title, pendingIntent);
    }

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendStateBroadcast(String state) {
        Intent intent = new Intent("ACTION_AUDIO_STATE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
        // レシーバを解除
        unregisterReceiver(notificationActionReceiver);
    }
}
