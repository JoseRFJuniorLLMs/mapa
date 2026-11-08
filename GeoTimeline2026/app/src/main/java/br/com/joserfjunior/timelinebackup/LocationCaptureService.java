package br.com.joserfjunior.timelinebackup;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import android.util.Log;

/** User-started, visibly notified collection; never starts at boot or silently. */
public final class LocationCaptureService extends Service {
    public static final String ACTION_STOP = "br.com.joserfjunior.timelinebackup.STOP_GPS";
    public static final String CHANNEL_ID = "timeline_location_active";
    private static final int NOTIFICATION_ID = 2026;
    private LocationManager manager;
    private LocationListener listener;

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        listener = location -> {
            try { TimelineStore.addLocation(this, location); }
            catch (Exception e) { Log.e("Timeline2026", "Não foi possível registrar localização", e); }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "GPS ativo — Linha do Tempo 2026", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent stop = new Intent(this, LocationCaptureService.class).setAction(ACTION_STOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Linha do Tempo: GPS em gravação")
                .setContentText("Localização sendo salva neste aparelho. Toque para abrir.")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "PARAR GPS", stopIntent).build())
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else startForeground(NOTIFICATION_ID, note);
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 30f, listener, Looper.getMainLooper());
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 50f, listener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e("Timeline2026", "GPS indisponível ou sem autorização", e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (manager != null && listener != null) manager.removeUpdates(listener);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}