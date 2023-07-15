package com.brt.otademo.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;


import com.brt.otademo.R;
import com.brt.otademo.utils.MyUtil;

import java.io.File;
import java.util.List;


/**
 * Created by DELL on 2017/11/20.
 */

public class FileListAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private List<String> items;
    private List<String> paths;
    private List<String> sizes;
    private int isZoom = 0;

    private Bitmap mIcon_folder;
    private Bitmap mIcon_file;
    private Bitmap mIcon_image;
    private Bitmap mIcon_video;
    private Bitmap mIcon_audio;
    private Bitmap mIcon_apk;
    private Bitmap mIcon_package;


    public FileListAdapter(Context context, List<String> items, List<String> paths, List<String> sizes, int zoom) {
        mInflater = LayoutInflater.from(context);
        this.items = items;
        this.paths = paths;
        this.sizes = sizes;
        this.isZoom = zoom;

        mIcon_folder = BitmapFactory.decodeResource(context.getResources(), R.drawable.folder);
        mIcon_file = BitmapFactory.decodeResource(context.getResources(), R.drawable.file);
        mIcon_image = BitmapFactory.decodeResource(context.getResources(), R.drawable.image);
        mIcon_video = BitmapFactory.decodeResource(context.getResources(), R.drawable.video);
        mIcon_audio = BitmapFactory.decodeResource(context.getResources(), R.drawable.audio);
        mIcon_apk = BitmapFactory.decodeResource(context.getResources(), R.drawable.apk);
        mIcon_package = BitmapFactory.decodeResource(context.getResources(), R.drawable.pack);

    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Bitmap bitmap;
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.folder_view_item, null);
            holder = new ViewHolder();
            holder.fv_icon = convertView.findViewById(R.id.fv_icon);
            holder.fv_title =  convertView.findViewById(R.id.fv_title);
            holder.fv_text =  convertView.findViewById(R.id.fv_text);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File file = new File(paths.get(position));
        if (!file.getParent().equals("/sdcard/") && !file.getParent().equals("/sdcard")) {
            if (position == 0) {
                holder.fv_icon.setImageResource(R.drawable.up);
                holder.fv_title.setText(R.string.tv_up);
                holder.fv_text.setText("");
            } else {
                File file1 = new File(paths.get(position));
                holder.fv_title.setText(file1.getName());
                if (file1.isDirectory()) {
                    holder.fv_icon.setImageBitmap(mIcon_folder);
                    holder.fv_text.setText("");
                } else {
                    String fileType = MyUtil.getMIMEType(file1, false);
                    try {
                        holder.fv_text.setText(sizes.get(position));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    switch (fileType) {
                        case "image":
                            if (isZoom == 1) {
                                bitmap = MyUtil.fitSizePic(file1);
                                if (bitmap != null) {
                                    holder.fv_icon.setImageBitmap(bitmap);
                                } else {
                                    holder.fv_icon.setImageBitmap(mIcon_image);
                                }
                            } else {
                                holder.fv_icon.setImageBitmap(mIcon_image);
                            }
                            break;
                        case "audio":
                            holder.fv_icon.setImageBitmap(mIcon_audio);
                            break;
                        case "video":
                            holder.fv_icon.setImageBitmap(mIcon_video);
                            break;
                        case "apk":
                            holder.fv_icon.setImageBitmap(mIcon_apk);
                            break;
                        case "file":
                            holder.fv_icon.setImageBitmap(mIcon_file);
                            break;
                        case "package":
                            holder.fv_icon.setImageBitmap(mIcon_package);
                            break;
                        default:
                            holder.fv_icon.setImageBitmap(mIcon_file);
                            break;
                    }
                }
            }
        } else {
            File file1 = new File(paths.get(position));
            holder.fv_title.setText(file1.getName());
            String fileType = MyUtil.getMIMEType(file1, false);
            if (file1.isDirectory()) {
                holder.fv_icon.setImageBitmap(mIcon_folder);
                holder.fv_text.setText("");
            } else {
                holder.fv_text.setText(sizes.get(position));
                switch (fileType) {
                    case "image":
                        if (isZoom == 1) {
                            bitmap = MyUtil.fitSizePic(file1);
                            if (bitmap != null) {
                                holder.fv_icon.setImageBitmap(bitmap);
                            } else {
                                holder.fv_icon.setImageBitmap(mIcon_image);
                            }
                        } else {
                            holder.fv_icon.setImageBitmap(mIcon_image);
                        }
                        break;
                    case "audio":
                        holder.fv_icon.setImageBitmap(mIcon_audio);
                        break;
                    case "video":
                        holder.fv_icon.setImageBitmap(mIcon_video);
                        break;
                    case "apk":
                        holder.fv_icon.setImageBitmap(mIcon_apk);
                        break;
                    case "file":
                        holder.fv_icon.setImageBitmap(mIcon_file);
                        break;
                    case "package":
                        holder.fv_icon.setImageBitmap(mIcon_package);
                        break;
                    default:
                        holder.fv_icon.setImageBitmap(mIcon_file);
                        break;
                }
            }
        }
        return convertView;
    }

    private class ViewHolder {
        ImageView fv_icon;
        TextView fv_title;
        TextView fv_text;
    }
}
