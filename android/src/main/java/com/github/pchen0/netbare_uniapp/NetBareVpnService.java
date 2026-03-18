/*
 * 与 netbare-sample 中 AppService 类似：继承 NetBareService，提供前台通知。
 * 在插件 Module 的 AndroidManifest.xml 中声明此 Service 及 intent-filter 后，NetBare.start() 会启动本服务。
 */
package com.github.pchen0.netbare_uniapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.github.megatronking.netbare.NetBareService;

/**
 * VPN 前台服务，用于维持抓包连接并显示通知（类似 netbare-sample 的 AppService）。
 * 需在 AndroidManifest.xml 中声明并配置 intent-filter，见 uniapp-module 目录下的说明。
 */
public class NetBareVpnService extends NetBareService {

    private static final String CHANNEL_ID = "netbare.vpn.channel";
    private static final int NOTIFICATION_ID = 0x0B01;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID,
                        "RunForge VPN",
                        NotificationManager.IMPORTANCE_LOW
                ));
            }
        }
    }

    @Override
    protected int notificationId() {
        return NOTIFICATION_ID;
    }

    @Override
    protected Notification createNotification() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        PendingIntent pending = PendingIntent.getActivity(
                this, 0,
                launch != null ? launch : new Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("")
                .setContentText("")
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }
}
