package com.brt.otademo.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import com.brt.otademo.R;
import com.brt.otademo.bean.MyBluetoothDevice;
import com.brt.otademo.ble.BleBaseAdapter;
import com.brt.otademo.ble.BluetoothIBridgeOTA;
import com.brt.otademo.utils.MyUtil;
import com.brt.otademo.utils.ToastUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class OTAActivity extends BaseActivity implements View.OnClickListener, BluetoothIBridgeOTA.Callback, BleBaseAdapter.DataReceiver {
    private static final String TAG = "OTAActivtiy";
    private static final String OTASharedPreference = "OTASharedPreference";
    private static final String OTA_FILE_SAVE_FLAG = "OTA_FILE_PATH";
    private static final int REQUEST_CODE = 1;
    private final int MAX_PROGRESS = 100;
    private MyBluetoothDevice otaDevice, newOtaDev;
    private BleBaseAdapter mBleBaseAdapter;
    private EditText etOTAName;
    private String filePath;
    private TextView tvDeviceName;
    private TextView tvMacAddress;
    private TextView tvOtaVersion;
    private TextView tvTip;
    private CheckBox cbSave;
    private Button btnSelectOTA;
    private Button btnStartOTA;
    private String OTAFileName;
    private boolean flag;
    private byte[] firmware;
    private ProgressDialog progressDialog;
    private SharedPreferences sp;
    private static final int MSG_OTA_SUCCESS = 1;
    private static final int MSG_OTA_FAILED = 2;
    private static final int MSG_OTA_PROGRESS = 3;
    private static final int MSG_CONNECTED = 4;
    private static final int MSG_DISCONNECTED = 5;
    private static final int MSG_SERVICE_DISCOVER = 6;
    private static final int MSG_OTA_BREAK = 7;

    private Handler mHandler;

    @Override
    protected void onPause() {
        super.onPause();
        mBleBaseAdapter.unregisterDataReceiver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_ota);
        HandlerThread sendThread = new HandlerThread("data_send");
        sendThread.start();
        mHandler = new Handler(sendThread.getLooper());

        initOtaData();
        initOtaView();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initOtaData() {
        mBleBaseAdapter = BleBaseAdapter.sharedInstance(OTAActivity.this);
        mBleBaseAdapter.registerDataReceiver(this);
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            otaDevice = bundle.getParcelable("otaDevice");
            Log.i(TAG, "initOtaData otaDevice = " + otaDevice);

            //StringBuilder devAddress = new StringBuilder(otaDevice.getDeviceAddress());
            //StringBuilder newAddress = devAddress.replace(0, 1, "D");

            //newOtaDev = MyBluetoothDevice.createDevice(newAddress.toString());
            otaDevice.setDeviceType(MyBluetoothDevice.DEVICE_TYPE_BLE);
            otaDevice.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_DISCONNECTED);
        }
        /*
        if (otaDevice.isConnected()) {
            mBleBaseAdapter.disconnectDevice(otaDevice);
            otaDevice.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_DISCONNECTED);
        }
        */
        if (otaDevice != null){
            checkVersoin();
        }
        sp = getSharedPreferences(OTASharedPreference, MODE_PRIVATE);
    }
    private void initOtaView() {
        setTitle(R.string.tv_OTA_activity);
        setBackArrow();
        tvDeviceName = findViewById(R.id.tv_ota_deviceName);
        tvMacAddress = findViewById(R.id.tv_mac_address);
        tvOtaVersion = findViewById(R.id.tv_ota_version);
        tvTip = findViewById(R.id.tip);
        cbSave = findViewById(R.id.cb_save);
        etOTAName = findViewById(R.id.et_ota_name);
        btnSelectOTA = findViewById(R.id.btn_select_ota);
        btnStartOTA = findViewById(R.id.btn_start_ota);
        btnSelectOTA.setOnClickListener(this);
        btnStartOTA.setOnClickListener(this);
        String fileName = sp.getString(OTA_FILE_SAVE_FLAG, "");
        Log.i(TAG, "fileName" + fileName);
        etOTAName.setText(fileName);
        etOTAName.setCursorVisible(false);
        etOTAName.setFocusable(false);
        etOTAName.setFocusableInTouchMode(false);
        tvDeviceName.setText(otaDevice.getmDeviceName());
        tvMacAddress.setText(otaDevice.getDeviceAddress());
        //tvOtaVersion.setText(selectDevice.firmwareRevision);
        //etOTAName.setText("BR8051A01_00_201221_r5669_OTA.bin");
        if (!TextUtils.isEmpty(etOTAName.getText().toString())) {
            btnStartOTA.setEnabled(true);
        } else {
            btnStartOTA.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tvDeviceName.setText("");
        tvMacAddress.setText("");
        mBleBaseAdapter.unregisterDataReceiver(this);
        mBleBaseAdapter.disconnectDevice(otaDevice);
        otaDevice = null;
        if (mBleBaseAdapter != null) {
            mBleBaseAdapter.stopOTA();
            Log.i(TAG,"==============mBluetoothAdapter.stopOTA();============");
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_select_ota:
                //跳转到OTA文件选择界面
                Intent fileChooseIntent = new Intent(OTAActivity.this, FileSelectActivity.class);
                startActivityForResult(fileChooseIntent, REQUEST_CODE);
                break;
            case R.id.btn_start_ota:
                startOTA();
                break;
            default:
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startOTA() {
        OTAFileName = etOTAName.getText().toString();
        if (TextUtils.isEmpty(OTAFileName)) {
            ToastUtils.showShortToast(this, R.string.toast_select_otaFile);
        }
        if (mBleBaseAdapter == null) {
            Toast.makeText(OTAActivity.this, R.string.toast_service_error, Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(OTAFileName);
        if (file.length() == 0) {
            ToastUtils.showShortToast(this, R.string.toast_wrong_file);
            return;
        }
        btnStartOTA.setEnabled(false);
        long file_length = file.length();
        if (file_length > 0) {
            try {
                RandomAccessFile file_src = new RandomAccessFile(OTAFileName, "r");
                firmware = new byte[(int) file_length];
                file_src.read(firmware);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            ToastUtils.showShortToast(this, R.string.toast_file_invalid);
            return;
        }
        //调试代码
//        try {
//            firmware = MyUtil.getBytesFromFile(MyUtil.getAssetsCacheFile(this, OTAFileName));
//        }catch (Exception e){
//            e.printStackTrace();
//        }
        //调试代码END


        if (firmware == null || firmware.length <= 0){
            ToastUtils.showShortToast(this, R.string.toast_file_invalid);
            return;
        }


        if (otaDevice.getmChipType() == 2 && !MyUtil.checkFileOtaData(firmware)){
            ToastUtils.showShortToast(this, R.string.toast_file_error_type);
            return;
        }

        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMax(MAX_PROGRESS);
            progressDialog.setTitle(getResources().getString(R.string.ota_dialog));
            progressDialog.setMessage(getResources().getString(R.string.ota_start));
            progressDialog.setCancelable(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setIndeterminate(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                    if (i == KeyEvent.KEYCODE_BACK) {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                        }
                        if (btnStartOTA != null) {
                            btnStartOTA.setEnabled(true);
                        }
                        if (mBleBaseAdapter != null){
                            mBleBaseAdapter.stopOTA();
                        }
                    }
                    return false;
                }
            });
            btnStartOTA.setEnabled(false);
            progressDialog.show();
        }
        mBleBaseAdapter.startOTA(otaDevice, firmware, this);
    }

    private void reSet(){
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBleBaseAdapter != null  && otaDevice != null){
                    byte[] otaCmd = "AT+RESET\\CR\r\n".getBytes();
                    mBleBaseAdapter.sendCmd(otaDevice, otaCmd, 1);
                }
            }
        },500);
    }

    private void checkVersoin() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBleBaseAdapter != null  && otaDevice != null){
                    byte[] otaCmd = "AT+GFWVER?\r\n".getBytes();
                    mBleBaseAdapter.sendCmd(otaDevice, otaCmd, 1);
                }
            }
        },100);
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_OTA_SUCCESS: {
                    Toast.makeText(OTAActivity.this, R.string.ota_succeed, Toast.LENGTH_SHORT).show();
                    if (mBleBaseAdapter != null && otaDevice != null) {
                        mBleBaseAdapter.disconnectDevice(otaDevice);  //OTA 成功后断链
                    }
                    if (btnStartOTA != null) {
                        btnStartOTA.setEnabled(true);
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (otaDevice.getmChipType() == 2){
                                successOTAPage();
                            }else {
                                finish();
                            }
                        }
                    },4000);
                    break;
                }
                case MSG_OTA_FAILED: {
                    int errorCode = (int) msg.obj;
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    Toast.makeText(OTAActivity.this, getErrDescription(errorCode), Toast.LENGTH_SHORT).show();
                    if (btnStartOTA != null) {
                        btnStartOTA.setEnabled(true);
                    }
                    break;
                }
                case MSG_OTA_PROGRESS: {
                    int progress = (int) msg.obj;
                    if (progressDialog != null && progress == 0) {
                        progressDialog.show();
                        progressDialog.setMessage(getResources().getString(R.string.ota_start));
                    }
                    if (progressDialog != null) {
                        progressDialog.setProgress(progress);
                    }
                    if (progressDialog != null && progress == MAX_PROGRESS) {
                        progressDialog.dismiss();
                        progressDialog = null;
                        Toast.makeText(OTAActivity.this, R.string.ota_finished, Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case MSG_CONNECTED: {
                    Toast.makeText(OTAActivity.this, R.string.link_established, Toast.LENGTH_SHORT).show();
                    break;
                }
                case MSG_DISCONNECTED: {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                        Toast.makeText(OTAActivity.this, R.string.link_dropped, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(OTAActivity.this, R.string.link_dropped, Toast.LENGTH_SHORT).show();
                    }
                    if (btnStartOTA != null) {
                        btnStartOTA.setEnabled(true);
                    }

                    break;
                }
                case MSG_SERVICE_DISCOVER: {
                    Toast.makeText(OTAActivity.this, R.string.searchservice_succeed, Toast.LENGTH_SHORT).show();
                    btnStartOTA.setEnabled(true);
                    break;
                }
                case MSG_OTA_BREAK:{
                    if (progressDialog != null){
                        progressDialog.dismiss();
                    }
                    break;
                }
                default:
                    break;
            }
        }
    };

    private String getErrDescription(int error) {
        String descriptionText = "";
        switch (error) {
            case BluetoothIBridgeOTA.ERROR_INVALID_PARAMETER:
                descriptionText = getText(R.string.error_invalid_parameter).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_DATA_SEND_TIMEOUT:
                descriptionText = getText(R.string.error_data_send_timeout).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_CONNECT_FAILED:
                descriptionText = getText(R.string.error_connect_failed).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_DISCOVER_SERVICE_FAILED:
                descriptionText = getText(R.string.error_discover_service_failed).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_DATA_SEND_FAILED:
                descriptionText = getText(R.string.error_data_send_failed).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_DEVICE_ALREADY_CONNECTED:
                descriptionText = getText(R.string.error_device_already_connected).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_USER_CANCEL:
                descriptionText = getText(R.string.error_uesr_cancel).toString();
                break;
            case BluetoothIBridgeOTA.ERROR_WRITE_DESCRIPTOR_FAILED:
                descriptionText = getText(R.string.error_write_descriptor_failed).toString();
                break;
            default:
                break;
        }
        return descriptionText;
    }

    /**
     * 获取到已选择的OTA 文件路径
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE) {
            filePath = data.getStringExtra("EXTRA_FILE_ABSOLUTEPATH");
            if (filePath != null) {
                etOTAName.setText(filePath);
                cbSave.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            SharedPreferences.Editor editor = getSharedPreferences(OTASharedPreference, MODE_PRIVATE).edit();
                            editor.putString(OTA_FILE_SAVE_FLAG, filePath);
                            editor.apply();
                            Toast.makeText(OTAActivity.this, R.string.link_dropped, Toast.LENGTH_SHORT).show();
                        } else {
                            sp.edit().clear().apply();
                        }
                    }
                });
            }
            if (!etOTAName.getText().toString().equals("") && btnStartOTA != null) {
                btnStartOTA.setEnabled(true);
            }
        }
    }

    private void append(String msg) {
        tvTip.append(msg + "\r\n");
    }


    @Override
    public void onOTAFail(int errorCode) {
        Log.i(TAG, "onOTAFail");
        if (handler != null) {
            Message msg = handler.obtainMessage(MSG_OTA_FAILED);
            msg.obj = errorCode;
            handler.sendMessage(msg);
        }
    }

    @Override
    public void onOTASuccess() {
        Log.i(TAG, "onOTASuccess");
        if (handler != null) {
            Message msg = handler.obtainMessage(MSG_OTA_SUCCESS);
            handler.sendMessage(msg);
        }
    }

    @Override
    public void onOTAProgress(int progress) {
        Log.i(TAG, "onOTAProgress:" + progress);
        if (handler != null) {
            Message msg = handler.obtainMessage(MSG_OTA_PROGRESS);
            msg.obj = progress;
            handler.sendMessage(msg);
        }
    }

    @Override
    public void onConnect() {
        Log.i(TAG, "onConnect");
        if (handler != null) {
            Message msg = handler.obtainMessage(MSG_CONNECTED);
            handler.sendMessage(msg);
        }
    }

    @Override
    public void onDisconnect() {
        Log.i(TAG, "onDisconnect");
        if (handler != null) {
            Message msg = handler.obtainMessage(MSG_DISCONNECTED);
            handler.sendMessage(msg);
        }
    }

    @Override
    public void onServiceDiscover() {
        Log.i(TAG, "onServiceDiscover");
        if (handler != null){
            Message msg = handler.obtainMessage(MSG_SERVICE_DISCOVER);
            handler.sendMessage(msg);
        }
    }


    @Override
    public void onDataReceived(MyBluetoothDevice device, byte[] bytes, final int length) {
        try {
            final String res = new String(bytes, 0 , length, "utf-8");
            final String result = MyUtil.replaceBlank(res);
            if (result.length() > 10){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvOtaVersion.setText(result);
                    }
                });
            }
            Log.d(TAG, "onDataReceived = " + result);
        }catch (Exception e){
            e.printStackTrace();
        }

    }


    private void successOTAPage() {
        if (otaDevice.getmChipType() == 2){
            Bundle bundle = new Bundle();
            bundle.putParcelable("otaDevice", otaDevice);
//            Intent intent = new Intent(OTAActivity.this, SuccessActivity.class);
//            intent.putExtras(bundle);
//            startActivity(intent);
        }
    }
}