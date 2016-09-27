package com.recorder.video.me.thevideorecorderas;

import android.content.Context;
import android.content.res.Resources;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {
    public static String genFileName() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_hhmmss");
        return format.format(new Date()).toString();
    }

    public static String formatDuration(long seconds) {
        long hour, min, sec;
        hour = seconds / 3600;
        min = (seconds % 3600) / 60;
        sec = seconds % 60;
        String hourYesOrNo = "";
        if (hour > 0)
            hourYesOrNo = formatHMS(hour) + ":";

        StringBuffer sbf = new StringBuffer();
        sbf.append(hourYesOrNo).append(formatHMS(min)).append(":")
                .append(formatHMS(sec));
        return sbf.toString();

    }

    private static String formatHMS(long val) {
        if (val < 10)
            return "0" + val;
        return val + "";
    }

    public static String getFileSize(Context context, long length) {
        Resources resouces = context.getResources();
        float fileSize = length / (1024f * 1024f);// in MB
        if (fileSize < 0.1f) {
            return resouces.getString(R.string.x_mbs, "0.1");
        } else {
            float x = 0;
            try {
                x = Float.parseFloat(String.format("%.1f", fileSize).replace(",", "."));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            if ((x * 10) % 10 == 0) return resouces.getString(R.string.x_mbs, (int) x);
            return resouces.getString(R.string.x_mbs, x);
        }
    }
}
