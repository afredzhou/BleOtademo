package com.brt.otademo.utils;

/**
 * Created by Administrator on 2016/8/23.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyUtil {

    public static String getMIMEType(File f, boolean isOpen) {
        String type = "";
        String fName = f.getName();
        String end = fName.substring(fName.lastIndexOf(".") + 1, fName.length()).toLowerCase();
        if(isOpen) {
            switch (end) {
                case "m4a":
                case "mp3":
                case "mid":
                case "xmf":
                case "ogg":
                case "wav":
                case "wma":
                    type = "audio";
                    break;
                case "3gp":
                case "mp4":
                    type = "video";
                    break;
                case "jpg":
                case "gif":
                case "png":
                case "jpeg":
                case "bmp":
                    type = "image";
                    break;
                default:
                    type = "*";
                    break;
            }
            type += "/*";
        }else{
            switch (end) {
                case "m4a":
                case "mp3":
                case "mid":
                case "xmf":
                case "ogg":
                case "wav":
                case "wma":
                    type = "audio";
                    break;
                case "3gp":
                case "mp4":
                    type = "video";
                    break;
                case "jpg":
                case "gif":
                case "png":
                case "jpeg":
                case "bmp":
                    type = "image";
                    break;
                case "apk":
                    type = "apk";
                    break;
                default:
                    type = "*";
                    break;
            }
            type += "/*";
        }
        return type;
    }

    public static Bitmap fitSizePic(File f) {
        Bitmap resizeBmp = null;
        BitmapFactory.Options opts = new BitmapFactory.Options();

        if(f.length() < 20480) {         //0-20k
            opts.inSampleSize = 1;
        } else if(f.length() < 51200) {   //20-50k
            opts.inSampleSize = 2;
        } else if(f.length() < 307200) {  //50-300k
            opts.inSampleSize = 4;
        } else if(f.length() < 819200) {  //300-800k
            opts.inSampleSize = 6;
        } else if(f.length() < 1048576) { //800-1024k
            opts.inSampleSize = 8;
        } else {
            opts.inSampleSize = 10;
        }
        resizeBmp = BitmapFactory.decodeFile(f.getPath(),opts);
        return resizeBmp;
    }


    public static String fileSizeMsg(File f) {
        int sub_index = 0;
        String show = "";
        if(f.isFile()) {
            long length = f.length();
            if(length >= 1073741824) {
                sub_index = (String.valueOf((float)length / 1073741824)).indexOf(".");
                show = ((float)length / 1073741824 + "000").substring(0, sub_index + 3) + "GB";
            }else if(length >= 1048576) {
                sub_index = (String.valueOf((float)length / 1048576)).indexOf(".");
                show =((float)length / 1048576 + "000").substring(0, sub_index + 3) + "MB";
            }else if(length >= 1024) {
                sub_index = (String.valueOf((float)length / 1024)).indexOf(".");
                show = ((float)length / 1024 + "000").substring(0, sub_index + 3) +"KB";
            }else if(length < 1024){
                show = String.valueOf(length) + "B";
            }
        }
        return show;
    }

    public static boolean checkDirPath(String newName)
    {
        boolean ret = false;
        if(newName.indexOf("\\") == -1) {
            ret = true;
        }
        return ret;
    }


    public static boolean checkFilePath(String newName)
    {
        boolean ret = false;
        if(newName.indexOf("\\") == -1){
            ret = true;
        }
        return ret;
    }

    public static byte[] getBytesFromFile(String fileName) throws Exception {
        byte[] result;
        if (fileName.length() == 0) {
            throw new Exception("升级文件有空");
        }
        try {
            File file = new File(fileName);
            InputStream inputStream = new FileInputStream(file);
            long fileLength = file.length();
            int length = (int) fileLength;
            if (length != fileLength) {
                throw new Exception("文件超出范围");
            }
            result = new byte[length];
            int bytesRead = inputStream.read(result);
            inputStream.close();

            // if the number of bytes read does not correspond to the length of the file, the result is not complete
            // if length is the maximum value possible, bytesRead is -1.
            if (bytesRead == length || (bytesRead == -1 && length == Integer.MAX_VALUE)) {
                return result;
            } else {
                throw new Exception("文件加载错误");
            }
        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }
    }


    public static String replaceBlank(String str){
        String dest = "";
        if (str != null){
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return  dest;
    }

    public static String getAssetsCacheFile(Context context, String fileName)   {
        File cacheFile = new File(context.getCacheDir(), fileName);
        try {
            InputStream inputStream = context.getAssets().open(fileName);
            try {
                FileOutputStream outputStream = new FileOutputStream(cacheFile);
                try {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buf)) > 0) {
                        outputStream.write(buf, 0, len);
                    }
                } finally {
                    outputStream.close();
                }
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cacheFile.getAbsolutePath();
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }


    public static boolean checkFileOtaData(byte[] ota_data){
        if (ota_data.length < 4087){
            return false;
        }
        boolean ret = false;
        byte[] checkData = new byte[7];
        System.arraycopy(ota_data, 4080, checkData, 0, 7);
        if (Arrays.equals(checkData, new byte[]{0x38,0x30,0x35,0x31,0x41,0x30,0x31})){
            ret = true;
        }
        return ret;
    }



}
