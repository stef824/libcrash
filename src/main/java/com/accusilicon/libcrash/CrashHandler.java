package com.accusilicon.libcrash;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Process;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by accu-zj on 18-1-24.
 */

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static volatile CrashHandler sInstance;
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private Context mContext;
    private static Class<? extends Activity> mRestartActivity;

    private CrashHandler(Context context) {
        mContext = context.getApplicationContext();
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public static void init(Context context){
        if (sInstance == null){
            synchronized (CrashHandler.class){
                if (sInstance == null){
                    sInstance = new CrashHandler(context);
                }
            }
        }
    }

    public static void setRestartActivity(Class<? extends Activity> activity) {
        mRestartActivity = activity;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        boolean handled = handleException(e);
        if (!handled && this != mDefaultHandler && mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(t, e);
        } else if (handled) {
            if (mRestartActivity != null) {
                AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                Intent restart = new Intent(mContext, mRestartActivity);
                restart.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, restart, PendingIntent.FLAG_ONE_SHOT);
                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1200, pendingIntent);
            }

            Process.killProcess(Process.myPid());
            System.exit(0);
            System.gc();
        }
    }

    private boolean handleException(Throwable e) {
        if (e == null) return false;
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        Throwable cause = e;
        do {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        } while (cause != null);
        printWriter.close();
        String crashLog = writer.toString();
        String logName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + System.currentTimeMillis() + ".log";
        try {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                String logDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/.crash";
                File logDirFile = new File(logDir);
                if (logDirFile.isFile()) {
                    logDirFile.delete();
                    logDirFile.mkdirs();
                } else if (!logDirFile.exists()) {
                    logDirFile.mkdirs();
                }

                if (logDirFile.isDirectory()) {
                    File file = new File(logDir + File.pathSeparator + logName);
                    if (!file.exists()) {
                        file.createNewFile();
                    } else if (file.isDirectory()) {
                        file.delete();
                        file.createNewFile();
                    }
                    FileOutputStream fos = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    bos.write(crashLog.getBytes());
                    bos.flush();
                    bos.close();
                }
            }
            return true;
        } catch (IOException ex) {

        }
        return false;
    }
}
