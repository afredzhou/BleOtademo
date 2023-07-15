package com.brt.otademo.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.brt.otademo.R;
import com.brt.otademo.adapter.FileListAdapter;
import com.brt.otademo.utils.MyUtil;
import com.brt.otademo.utils.ToastUtils;

import java.io.File;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileSelectActivity extends BaseActivity implements AdapterView.OnItemClickListener{
    private static final String TAG = "FileSelectActivity";
    public static final int MSG_TO_UPPER_FOLER = 1;
    public static final int MSG_NO_SDCARD = 2;
    private List<String> items = null;
    private List<String> paths = null;
    private List<String> sizes = null;
    private ArrayList<Integer> positionList;
    private int mPosition = 0;
    private int isZoom = 0;
    private String curPath;
    private String sdPath;
    private File file;
    private GridView gridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_file_select);
        initFileView();
    }

    private void initFileView() {
        Log.i(TAG, "initFileView");
        setTitle(R.string.tv_fileSelect_activity);
        setBackArrow();

        gridView = findViewById(R.id.gv_ota_file);
        gridView.setOnItemClickListener(this);
        sdPath = getSDPath();
        if (sdPath != null) {
            getFileDir(sdPath);
        } else {
            Message msg = new Message();
            msg.what = MSG_NO_SDCARD;
            handler.sendMessage(msg);
        }
        positionList = new ArrayList<>();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) {
            if (curPath != null && !curPath.equals(sdPath)) {
                Message msg = new Message();
                msg.what = MSG_TO_UPPER_FOLER;
                handler.sendMessage(msg);
                Log.i(TAG,"position == 0 is clicked");
            }
        } else {
            file = new File(paths.get(position));
            if (file.isDirectory()) {
                String tmpPath = file.getPath();
                if (getFileDir(tmpPath)) {
                    positionList.add(mPosition);
                }
            } else {
                fileOrDirHandle(position, "long");
            }
        }
    }

    private void fileOrDirHandle(final int position, final String flag) {
        DialogInterface.OnClickListener listListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (file.isDirectory()) {
                    return;
                } else {
                    if (which == 0) {
                        Intent intent = new Intent();
                        file = new File(paths.get(position));
                        String strAbsolutePath = file.getAbsolutePath();
                        intent.putExtra("EXTRA_FILE_ABSOLUTEPATH", strAbsolutePath);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                }
            }
        };

        if (flag.equals("long")) {
            file = new File(paths.get(position));

            String[] listOperations;
            if (file.isDirectory()) {
                listOperations = new String[1];
                listOperations[0] = this.getString(R.string.delete);
            } else {
                listOperations = new String[1];
                listOperations[0] = this.getString(R.string.open);
            }

            new AlertDialog.Builder(FileSelectActivity.this)
                    .setTitle(file.getName())
                    .setIcon(R.drawable.selectfile)
                    .setItems(listOperations, listListener)
                    .setPositiveButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_TO_UPPER_FOLER:
                    File file = new File(curPath);
                    getFileDir(file.getParent());
                    Log.i(TAG, "click this go back ");
                    break;
                case MSG_NO_SDCARD:
                    Toast.makeText(FileSelectActivity.this, R.string.toast_delete_sdCard, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    private String getSDPath() {
        File sdDir;
        boolean sdCardExist = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();
        } else {
            return null;
        }
        return sdDir.toString();
    }

    private boolean getFileDir(String filePath) {
        Log.i(TAG, "getFileDir ========== filePath:"+ filePath);
         File file = new File(filePath);
        File[] files = file.listFiles();
        if (files == null) {
            return false;
        }

        if (items != null) {
            items.clear();
        } else {
            items = new ArrayList<>();
        }

        if (paths != null) {
            paths.clear();
        } else {
            paths = new ArrayList<>();
        }

        if (sizes != null) {
            sizes.clear();
        } else {
            sizes = new ArrayList<>();
        }

        curPath = filePath;
        if (files.length == 0) {
            List<Map<String, Object>> listItems = new ArrayList<>();
            Map<String, Object> listItem = new HashMap<>();
            listItem.put("srcName", R.drawable.up);
            listItem.put("name", this.getString(R.string.tv_up));
            listItems.add(listItem);

            SimpleAdapter adapter = new SimpleAdapter(FileSelectActivity.this, listItems, R.layout.folder_view_item,
                    new String[]{"srcName", "name"}, new int[]{R.id.fv_icon, R.id.fv_title});

            gridView.setAdapter(adapter);
        } else {
            Arrays.sort(files, new FileComparator());
            items.add("position");
            paths.add(files[0].getPath());
            sizes.add("");

            for (int i = 0; i < files.length; i++) {
                items.add(files[i].getName());
                paths.add(files[i].getPath());
                if (files[i].isDirectory()) {
                    sizes.add("");
                } else if (files[i].isFile()) {
                    sizes.add(MyUtil.fileSizeMsg(files[i]));
                }
            }
            FileListAdapter fileListAdapter = new FileListAdapter(FileSelectActivity.this, items, paths, sizes, isZoom);
            gridView.setAdapter(fileListAdapter);
        }
        return true;
    }

    private class FileComparator implements Comparator<Object> {
        Collator collator = Collator.getInstance();

        @Override
        public int compare(Object o1, Object o2) {
            File file1 = (File) o1;
            File file2 = (File) o2;

            if (file1.isDirectory() && file2.isFile()) {
                return -1;
            } else if (file1.isFile() && file2.isDirectory()) {
                return 1;
            } else {
                String tempName1 = file1.getName();
                String tempName2 = file2.getName();

                CollationKey c1 = collator.getCollationKey(tempName1);
                CollationKey c2 = collator.getCollationKey(tempName2);
                long result = collator.compare(
                        c1.getSourceString(), c2.getSourceString()
                );
                return (result < 0) ? -1 : (result > 0) ? 1 : 0;
            }

        }
    }
}
