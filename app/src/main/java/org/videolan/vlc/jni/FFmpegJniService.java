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

package org.videolan.vlc.jni;

import android.app.Service;
import android.content.Intent;

import android.os.Environment;
import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FFmpegJniService extends Service {

    private static final String TAG = "FFmpegJniService";
    private static boolean hasThread = false;
    private static int timeCount = 0;
    private long h264FileSize = 0;
    private long h264FileSize_second = 0;

    public static int getNumbers(String content) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            return Integer.parseInt(matcher.group(0));
        }
        return -1;
    }

    public boolean hasH264File() {
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
        File file = new File(dir);
        if (file.exists()) {
            LinkedList<File> list = new LinkedList<File>();
            File[] files = file.listFiles();
            for (File file2 : files) {
                if (file2.isDirectory()) {
                    continue;
                } else {
                    if (file2.toString().contains(".h264")) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    public void moveH264File(String dir) {
        String dir_tmp = Environment.getExternalStorageDirectory().getAbsolutePath();
        File file = new File(dir_tmp);
        if (file.exists()) {
            LinkedList<File> list = new LinkedList<File>();
            File[] files = file.listFiles();
            for (File file2 : files) {
                if (file2.isDirectory()) {
                    continue;
                } else {
                    if (file2.toString().contains(".h264")) {
                        try {
                            String cmd = "mv " + file2.toString() + " " + dir;
                            Runtime.getRuntime().exec(cmd);
                            Log.e(TAG, "david1218 h264 cmd = " + cmd);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public boolean hasAacFile() {
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
        File file = new File(dir);
        if (file.exists()) {
            LinkedList<File> list = new LinkedList<File>();
            File[] files = file.listFiles();
            for (File file2 : files) {
                if (file2.isDirectory()) {
                    continue;
                } else {
                    if (file2.toString().contains(".aac")) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    public void moveAacFile(String dir) {
        String dir_tmp = Environment.getExternalStorageDirectory().getAbsolutePath();
        File file = new File(dir_tmp);
        if (file.exists()) {
            LinkedList<File> list = new LinkedList<File>();
            File[] files = file.listFiles();
            for (File file2 : files) {
                if (file2.isDirectory()) {
                    continue;
                } else {
                    if (file2.toString().contains(".aac")) {
                        try {
                            String cmd = "mv " + file2.toString() + " " + dir;
                            Runtime.getRuntime().exec(cmd);
                            Log.e(TAG, "david1218 aac cmd = " + cmd);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static int getNumbers1(String str) {
        char[] chr2 = new char[20];
        str.getChars(20,str.length(),chr2,0);
        String str_result = new String(chr2);
        return getNumbers(str_result);
    }

    public void sort_list(ArrayList<String> list) {
        Collections.sort(list, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                char[] chr1 = new char[20];
                ((String)o1).getChars(20,((String)o1).length(),chr1,0);
                String str1 = new String(chr1);
                //Log.e(TAG, "str1 = [" + str1 + "]");

                char[] chr2 = new char[20];
                ((String)o2).getChars(20,((String)o2).length(),chr2,0);
                String str2 = new String(chr2);
                //Log.e(TAG, "str2 = [" + str2 + "]");

                int s1 = getNumbers(str1);
                int s2 = getNumbers(str2);
                if(s1 >= s2) {
                    return 1;
                }
                else {
                    return -1;
                }
            }
        });
    }

    public void traverseFolder(String dir, ArrayList<String> h264FileNames, ArrayList<String> aacFileNames, ArrayList<String> h264FileNames_second, ArrayList<String> aacFileNames_second) {
        File file = new File(dir);
        if (file.exists()) {
            LinkedList<File> list = new LinkedList<File>();
            File[] files = file.listFiles();
            for (File file2 : files) {
                if (file2.isDirectory()) {
                    continue;
                } else {
                    if (file2.toString().contains("second.h264")) {
                        //Log.e(TAG, "second.h264 : " + file2.toString());
                        h264FileNames_second.add(file2.toString());
                    } else if (file2.toString().contains("second.aac")) {
                        //Log.e(TAG, "second.aac : " + file2.toString());
                        aacFileNames_second.add(file2.toString());
                    } else if (file2.toString().contains(".h264")) {
                        //Log.e(TAG, ".h264 : " + file2.toString());
                        h264FileNames.add(file2.toString());
                    } else if (file2.toString().contains(".aac")) {
                        //Log.e(TAG, ".aac : " + file2.toString());
                        aacFileNames.add(file2.toString());
                    }
                }
            }

            sort_list(h264FileNames);
            sort_list(aacFileNames);
            sort_list(h264FileNames_second);
            sort_list(aacFileNames_second);
        }
    }

    private String getOutputFileName(){
        Time t=new Time();
        t.setToNow();
        int year=t.year;
        int month=t.month +1;
        int day=t.monthDay;
        int hour=t.hour;
        int minute=t.minute;
        int second=t.second;
        //Log.i(TAG, ""+year+month+day+hour+minute+second);
        String filename=""+year+month+day+hour+minute+second;
        String outputName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename + ".mp4";
        return outputName;
    }

    private String getOutputFileName_second(){
        Time t=new Time();
        t.setToNow();
        int year=t.year;
        int month=t.month +1;
        int day=t.monthDay;
        int hour=t.hour;
        int minute=t.minute;
        int second=t.second;
        //Log.i(TAG, ""+year+month+day+hour+minute+second);
        String filename=""+year+month+day+hour+minute+second;
        String outputName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename + "_second.mp4";
        return outputName;
    }

    private void mvOldH264AndAacFile() {
        if (!hasH264File() && !hasAacFile())
            return;
        Time t=new Time();
        t.setToNow();
        int year=t.year;
        int month=t.month +1;
        int day=t.monthDay;
        int hour=t.hour;
        int minute=t.minute;
        int second=t.second;
        //Log.i(TAG, ""+year+month+day+hour+minute+second);
        String dir=Environment.getExternalStorageDirectory().getAbsolutePath() + "/"+year+month+day+hour+minute+second;
        String cmd1 = "mkdir " + dir;
        try {
            Runtime.getRuntime().exec(cmd1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        moveH264File(dir);
        moveAacFile(dir);

    }

    public void getMp4FromFfmpeg() {
        if (hasThread)
            return;

        mvOldH264AndAacFile();

        new Thread(new Runnable() {
            private ArrayList<String> h264FileNames = new ArrayList<String>();
            private ArrayList<String> aacFileNames = new ArrayList<String>();
            private ArrayList<String> h264FileNames_second = new ArrayList<String>();
            private ArrayList<String> aacFileNames_second = new ArrayList<String>();
            private String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
            private String[] commands = new String[10];
            @Override
            public void run() {
                hasThread = true;
                while(true) {
                    //Log.e(TAG, "in run thread");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    h264FileNames.clear();
                    aacFileNames.clear();
                    h264FileNames_second.clear();
                    aacFileNames_second.clear();
                    traverseFolder(dir, h264FileNames, aacFileNames, h264FileNames_second, aacFileNames_second);

                    if (!h264FileNames.isEmpty() && h264FileNames.size() > 1 && !aacFileNames.isEmpty() && aacFileNames.size() > 1) {
                        for(int i=0; i<h264FileNames.size()-1; i++){
                            boolean hasMatchAacFile = false;
                            String h264 = h264FileNames.get(i);
                            String aac = aacFileNames.get(i);
                            Log.e(TAG, "david1218 h264 file = " + h264);
                            Log.e(TAG, "david1218 aac file = " + aac);
                            if (getNumbers1(h264) != getNumbers1(aac)) {
                                for (int j = 0; j < aacFileNames.size()-1; j++) {
                                    if (getNumbers1(h264) == (getNumbers1(aacFileNames.get(j)))) {
                                        aac = aacFileNames.get(j);
                                        hasMatchAacFile = true;
                                        Log.e(TAG, "david1218 h264 equal aac and will break loop");
                                        break;
                                    }
                                }
                            } else {
                                hasMatchAacFile = true;
                            }

                            Log.e(TAG, "david1218 1111111111111111111111");
                            if (!hasMatchAacFile)
                                continue;

                            Log.e(TAG, "david1218 2222222222222222222222");
                            commands[0] = "ffmpeg";
                            commands[1] = "-i";
                            commands[2] = aac;
                            commands[3] = "-i";
                            commands[4] = h264;
                            commands[5] = "-map";
                            commands[6] = "0:0";
                            commands[7] = "-map";
                            commands[8] = "1:0";
                            commands[9] = getOutputFileName();
                            Log.e(TAG, "call FFmpegJni.run(commands) begin");
                            int result = FFmpegJni.run(commands);
                            Log.e(TAG, "call FFmpegJni.run(commands) end");
                            try {
                                //Log.e(TAG, "will delete the aac file and h264 file");
                                Runtime.getRuntime().exec("rm " + aac + " " + h264);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (!h264FileNames_second.isEmpty() && h264FileNames_second.size() > 1 && !aacFileNames_second.isEmpty() && aacFileNames_second.size() > 1) {
                        for(int i=0; i<h264FileNames_second.size()-1; i++) {
                            boolean hasMatchAacFile_second = false;
                            String h264_second = h264FileNames_second.get(i);
                            String aac_second = aacFileNames_second.get(i);
                            if (getNumbers1(h264_second) != getNumbers1(aac_second)) {
                                for (int j = 0; j < aacFileNames_second.size()-1; j++) {
                                    if (getNumbers1(h264_second) == (getNumbers1(aacFileNames_second.get(j)))) {
                                        aac_second = aacFileNames_second.get(j);
                                        hasMatchAacFile_second = true;
                                        break;
                                    }
                                }
                            } else {
                                hasMatchAacFile_second = true;
                            }

                            if (!hasMatchAacFile_second)
                                continue;

                            commands[0] = "ffmpeg";
                            commands[1] = "-i";
                            commands[2] = aac_second;
                            commands[3] = "-i";
                            commands[4] = h264_second;
                            commands[5] = "-map";
                            commands[6] = "0:0";
                            commands[7] = "-map";
                            commands[8] = "1:0";
                            commands[9] = getOutputFileName_second();
                            Log.e(TAG, "call second FFmpegJni.run(commands) begin");
                            int result = FFmpegJni.run(commands);
                            Log.e(TAG, "call second FFmpegJni.run(commands) end");
                            try {
                                //Log.e(TAG, "second will delete the aac file and h264 file");
                                Runtime.getRuntime().exec("rm " + aac_second + " " + h264_second);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (!h264FileNames_second.isEmpty() && 1 == h264FileNames_second.size() && !h264FileNames.isEmpty() && 1 == h264FileNames.size()) {
                        File h264 = new File(h264FileNames.get(0));
                        File h264_second = new File(h264FileNames_second.get(0));
                        if (0 == h264FileSize)
                            h264FileSize = h264.length();

                        if (0 == h264FileSize_second)
                        h264FileSize_second = h264_second.length();

                        if (timeCount > 30)
                            break;
                        if (h264FileSize == h264.length() && h264FileSize_second == h264_second.length()) {
                            timeCount++;
                        } else {
                            h264FileSize = 0;
                            h264FileSize_second = 0;
                            timeCount = 0;
                        }
                    } else {
                        h264FileSize = 0;
                        h264FileSize_second = 0;
                        timeCount = 0;
                    }

                }

                //last to merge the mp4 file
                h264FileNames.clear();
                aacFileNames.clear();
                h264FileNames_second.clear();
                aacFileNames_second.clear();

                traverseFolder(dir, h264FileNames, aacFileNames, h264FileNames_second, aacFileNames_second);

                if (!h264FileNames.isEmpty() && !aacFileNames.isEmpty()) {
                    for(int i=0; i<h264FileNames.size(); i++){
                        boolean hasMatchAacFile = false;
                        String h264 = h264FileNames.get(i);
                        String aac = aacFileNames.get(i);

                        if (getNumbers1(h264) != getNumbers1(aac)) {
                            for (int j = 0; j < aacFileNames.size(); j++) {
                                if (getNumbers1(h264) == (getNumbers1(aacFileNames.get(j)))) {
                                    aac = aacFileNames.get(j);
                                    hasMatchAacFile = true;
                                    Log.e(TAG, "david1218 h264 equal aac and will break loop");
                                    break;
                                }
                            }
                        } else {
                            hasMatchAacFile = true;
                        }

                        Log.e(TAG, "david1218 1111111111111111111111");
                        if (!hasMatchAacFile)
                            continue;

                        Log.e(TAG, "david1218 2222222222222222222222");
                        commands[0] = "ffmpeg";
                        commands[1] = "-i";
                        commands[2] = aac;
                        commands[3] = "-i";
                        commands[4] = h264;
                        commands[5] = "-map";
                        commands[6] = "0:0";
                        commands[7] = "-map";
                        commands[8] = "1:0";
                        commands[9] = getOutputFileName();
                        Log.e(TAG, "call FFmpegJni.run(commands) begin");
                        int result = FFmpegJni.run(commands);
                        Log.e(TAG, "call FFmpegJni.run(commands) end");
                        try {
                            //Log.e(TAG, "will delete the aac file and h264 file");
                            Runtime.getRuntime().exec("rm " + aac + " " + h264);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (!h264FileNames_second.isEmpty() && !aacFileNames_second.isEmpty()) {
                    for(int i=0; i<h264FileNames_second.size(); i++) {
                        boolean hasMatchAacFile_second = false;
                        String h264_second = h264FileNames_second.get(i);
                        String aac_second = aacFileNames_second.get(i);
                        if (getNumbers1(h264_second) != getNumbers1(aac_second)) {
                            for (int j = 0; j < aacFileNames_second.size(); j++) {
                                if (getNumbers1(h264_second) == (getNumbers1(aacFileNames_second.get(j)))) {
                                    aac_second = aacFileNames_second.get(j);
                                    hasMatchAacFile_second = true;
                                    break;
                                }
                            }
                        } else {
                            hasMatchAacFile_second = true;
                        }

                        if (!hasMatchAacFile_second)
                            continue;

                        commands[0] = "ffmpeg";
                        commands[1] = "-i";
                        commands[2] = aac_second;
                        commands[3] = "-i";
                        commands[4] = h264_second;
                        commands[5] = "-map";
                        commands[6] = "0:0";
                        commands[7] = "-map";
                        commands[8] = "1:0";
                        commands[9] = getOutputFileName_second();
                        Log.e(TAG, "call second FFmpegJni.run(commands) begin");
                        int result = FFmpegJni.run(commands);
                        Log.e(TAG, "call second FFmpegJni.run(commands) end");
                        try {
                            //Log.e(TAG, "second will delete the aac file and h264 file");
                            Runtime.getRuntime().exec("rm " + aac_second + " " + h264_second);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }).start();
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "david1218 onCreate");
        getMp4FromFfmpeg();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        Log.e(TAG, "david1218 onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"david1218 onDestroy......");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
