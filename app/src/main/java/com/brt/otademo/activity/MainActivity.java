package com.brt.otademo.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.brt.otademo.Constants;
import com.brt.otademo.R;
import com.brt.otademo.adapter.MyAdapter;
import com.brt.otademo.bean.MyBluetoothDevice;
import com.brt.otademo.ble.BleBaseAdapter;
import com.brt.otademo.utils.MyUtil;
import com.brt.otademo.utils.ToastUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;

import static java.lang.Thread.sleep;

public class MainActivity extends BaseActivity implements View.OnClickListener, MyAdapter.OnItemClickListener, BleBaseAdapter.EventReceiver {
    public static final String TAG = "MainActivity";
    private final int REQUEST_ENABLE_BT = 0xa01;
    private static final int CMD_TYPE_OTA = 0;
    private static final int CMD_TYPE_OTA_INFO = 1;
    public static final int PERMISSION_REQUEST_CODE = 2;
    private static final int REQUEST_CODE_LOCATION_SETTINGS = 3;
    private static final int MSG_SEND_OTA_CMD = 4;
    private static final int MSG_RECONNECT = 5;
    private static final int MSG_OTA = 6;
    private long ExitTime = 0;
    private static final int SCAN_PERIOD = 5000;
    private RecyclerView mRecyclerView;
    private Button mScanBtn;
    private BleBaseAdapter mAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private MyHandler mHandler;
    private MyAdapter myAdapter;
    private ArrayList<MyBluetoothDevice> mDevices;
    private SharedPreferences.Editor editor;
    private SharedPreferences sp;
    private MyBluetoothDevice dev;
    private ProgressDialog mProgressDialog;
    private boolean isSendCmd1 , isSendCmd2;
    private boolean isNoOTA = true;


    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.registerEventReceiver(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.unregisterEventReceiver(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_main);
        setTitle(R.string.title_ota);
        checkBLEFeature();
        checkBluetoothPermission();
        checkStoragePermission();
        if (!isLocationEnable(this)) {
            setLocationService();
        }
        initMainView();
        initMainData();
    }

    private void initMainView() {
        mRecyclerView = findViewById(R.id.devices_list_rv);
        mScanBtn = findViewById(R.id.ss_btn);
        mScanBtn.setOnClickListener(this);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(MainActivity.this,
                LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        myAdapter = new MyAdapter(this, mDevices);
        mHandler = new MyHandler();
        mProgressDialog = new ProgressDialog(this);
    }

    @SuppressLint("CommitPrefEdits")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initMainData() {
        mAdapter = BleBaseAdapter.sharedInstance(this);
        mDevices = new ArrayList<>();

        editor = getSharedPreferences("device", MODE_PRIVATE).edit();
        sp = getSharedPreferences("device", MODE_PRIVATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Iterator<MyBluetoothDevice> it = mDevices.iterator();
        while (it.hasNext()) {
            MyBluetoothDevice d = it.next();
            if (d != null && d.isConnected() && mAdapter != null) {
                mAdapter.disconnectDevice(d);
            }
        }
        if (mAdapter != null) {
            mAdapter.unregisterEventReceiver(this);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View v) {
        // 开始scan
        startScan();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mAdapter.stopScan();
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mAdapter.startScan();
        } else {
            mScanning = false;
            mAdapter.stopScan();
        }
        return mScanning;
    }

    private void clearDevices() {
        if (mDevices != null) {
            ArrayList<MyBluetoothDevice> newList = new ArrayList<>();
            Iterator<MyBluetoothDevice> it = mDevices.iterator();
            while (it.hasNext()) {
                MyBluetoothDevice d = it.next();
                if (d != null && d.isConnected()) {
                    newList.add(d);
                }
            }
            synchronized (mDevices) {
                mDevices = newList;
            }
        }
    }

    /**
     * 发起连接 ==> 发送 AT + OTA = 1 指令 ==> 断开链接 ==> 再次连接上次保存的设备 ==> 进行OTA升级 ==> 断开连接
     * @param view
     * @param position
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onItemClick(View view, int position) {
        if (mAdapter != null) {
            mAdapter.stopScan();
        }

        if (mScanning) {
            mAdapter.stopScan();
            mScanning = false;
        }
        MyBluetoothDevice dev = MyAdapter.getItem(position);
        if (dev != null && mAdapter != null) {
            Log.i(TAG, "connecting...");
            if (mAdapter.connectDevice(dev)) { // 1. 建立连接
                dev.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_CONNECTED);
                Log.i(TAG, "dev.isConnected = " + dev.isConnected());
                editor.putString("ble_address", dev.getDeviceAddress());
                Log.i(TAG,"dev.Address = " + dev.getDeviceAddress());
                editor.apply();
                mProgressDialog.setMessage("connecting...");
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressDialog.show();

                // 2. 发送 AT + OTA = 1 指令

                //Message msg = mHandler.obtainMessage(MSG_SEND_OTA_CMD);
                //msg.obj = dev;
                //mHandler.sendMessage(msg);
                // 3. 等待300ms后断开连接
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void reConnect() {
        Log.i(TAG, "reConnect in...");
        if (mAdapter != null || mScanning) {
            mAdapter.stopScan();
            mScanning = false;
        }
        startScan();
        String lastAddress = sp.getString("ble_address", "");
        //Log.i(TAG, "lastAddress = " + lastAddress);
        // 将 C0 改成 D0

        if (!TextUtils.isEmpty(lastAddress) && mDevices.size() > 0) {
            for (MyBluetoothDevice device : mDevices) {
                String otaAddress = device.getDeviceAddress();
                String newLastDevAddress = lastAddress.substring(1, lastAddress.length());
                String newOtaAddress = otaAddress.substring(1, otaAddress.length());
                if (newLastDevAddress.equals(newOtaAddress)) {
                    dev = device;
                    Log.i(TAG, "reConnect # dev = " +dev);
                }
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startScan() {
        clearDevices();

        /**
         * BLE scan
         **/

        if (mAdapter.isEnabled() && mAdapter.bleIsSupported()) {
            if (scanLeDevice(true)) {
                mScanBtn.setEnabled(false);
            }
            myAdapter = new MyAdapter(this, mDevices);
            myAdapter.setOnItemClickListener(this);
            mRecyclerView.setAdapter(myAdapter);
        } else {
            ToastUtils.showShortToast(this, R.string.toast_enable_bt);
        }

    }

    public static byte[] hexStringToByte(String hex) {
        int len = (hex.length() / 2);
        byte[] result = new byte[len];
        char[] achar = hex.toCharArray();
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            result[i] = (byte) (toByte(achar[pos]) << 4 | toByte(achar[pos + 1]));
        }
        return result;
    }

    private static int toByte(char c) {
        byte b = (byte) "0123456789ABCDEF".indexOf(c);
        return b;
    }
    private class MyHandler extends Handler {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_SEND_OTA_CMD:
                    MyBluetoothDevice device = (MyBluetoothDevice) msg.obj;
                    if (device.isConnected()) {
                        byte[] otaCmd = hexStringToByte("1D284102000007231D28410200000723"); //#60000480240000a416  // AT+OTA=1
                        Log.i(TAG, " otaCmd = " + otaCmd);
                        do {
                            isSendCmd1 = mAdapter.sendCmd(device, otaCmd, CMD_TYPE_OTA_INFO);
                            Log.i(TAG, "isSendCmd1 = " + isSendCmd1);
                            try {
                                sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } while (device.isConnected());
                    }
                    mProgressDialog.dismiss();
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("otaDevice", device);
                    Intent intent = new Intent(MainActivity.this, OTAActivity.class);
                    intent.putExtras(bundle);
                    startActivity(intent);
                    break;
            case MSG_RECONNECT:
                if (isSendCmd2) {
                    try {
                        sleep(300);
                        mAdapter.disconnectDevice(dev);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    reConnect();
                }
                break;
            case MSG_OTA:
                if (dev != null && dev.isConnected()) {
                    mProgressDialog.dismiss();
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                  /*  Bundle bundle = new Bundle();
                    bundle.putParcelable("otaDevice", dev);
                    Intent intent = new Intent(MainActivity.this, OTAActivity.class);
                    intent.putExtras(bundle);
                    startActivity(intent);*/
                }
                break;
            }
        }
    }

    @Override
    public void onBluetoothOn() {

    }

    @Override
    public void onBluetoothOff() {

    }

    @Override
    public void onDiscoveryFinished() {
        mScanBtn.setEnabled(true);
    }

    @Override
    public void onDeviceFound(MyBluetoothDevice device) {
        if (!deviceExisted(device)) {
            synchronized (mDevices) {
                mDevices.add(device);
                //Log.i(TAG, "# onDeviceFound # mDevices = " + mDevices);
            }
            myAdapter.notifyDataSetChanged();
        } else {
            //Log.i(TAG, "# onDeviceFound # device = " + device);
        }
    }

    @Override
    public void onDeviceConnected(MyBluetoothDevice device) {
        Log.i(TAG, "# connected");
        if (!deviceExisted(device)) {
            synchronized (mDevices) {
                mDevices.add(device);
            }
            myAdapter.notifyDataSetChanged();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDeviceDisconnected(MyBluetoothDevice device, String exceptionMsg) {
        Log.i(TAG, "# disconnected");
        if (myAdapter != null) {
            myAdapter.notifyDataSetChanged();
        }

        if (isNoOTA) {
            mProgressDialog.dismiss();
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Toast.makeText(this, R.string.toast_ota_enter_and_rescan, Toast.LENGTH_LONG).show();
            startScan();
        }
    }

    @Override
    public void onDeviceConnectFailed(MyBluetoothDevice device, String exceptionMsg) {

    }

    @Override
    public void onWriteFailed(MyBluetoothDevice device, String exceptionMsg) {
        Log.i(TAG, "# onWriteFailed");
    }

    @Override
    public void onLeServiceDiscovered(MyBluetoothDevice device, String exceptionMsg, String serviceUUID) {
        Log.i(TAG, "service uuid:" + serviceUUID);
        if (!TextUtils.isEmpty(serviceUUID) && serviceUUID.equalsIgnoreCase(Constants.SERVICE_OTA)) {
            Log.i(TAG, "# onLeServiceDiscovered");
            isNoOTA = true;
        } else {
            isNoOTA = false;
            mProgressDialog.dismiss();
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Bundle bundle = new Bundle();
            bundle.putParcelable("otaDevice", device);
            Intent intent = new Intent(MainActivity.this, OTAActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);
        }
    }

    @Override
    public void onWriteSucceed(MyBluetoothDevice device, String exceptionMsg)
    {
        Log.i(TAG, "# onWriteSucceed");
        if (device.isConnected() && isNoOTA) {
            /*
            byte[] otaCmd = "AT+OTA=1\r\n".getBytes(); //#60000480240000a416  // AT+OTA=1
            Log.i(TAG, " otaCmd = " + otaCmd);
            isSendCmd2 = mAdapter.sendCmd(device, otaCmd, CMD_TYPE_OTA_INFO);
            Log.i(TAG, "onWriteSucceed isSendCmd2 = " + isSendCmd2);
            */
            mAdapter.disconnectDevice(device);
        }
    }

    @Override
    public void onBleIsReady(MyBluetoothDevice device, String exceptionMsg) {
        Log.i(TAG, "# onBleIsReady");

        if (device.isConnected() && isNoOTA) {
            byte[] otaCmd = "AT+OTA=1\r\n".getBytes(); //hexStringToByte("1D284102000007231D28410200000723"); //#60000480240000a416  // AT+OTA=1
            Log.i(TAG, " otaCmd = " + otaCmd);
            isSendCmd2 = mAdapter.sendCmd(device, otaCmd, CMD_TYPE_OTA_INFO);
            Log.i(TAG, "onWriteSucceed isSendCmd2 = " + isSendCmd2);
       /*     do {
                //isSendCmd1 = mAdapter.sendCmd(device, otaCmd, CMD_TYPE_OTA_INFO);
                isSendCmd2 = mAdapter.sendCmd(device, otaCmd, CMD_TYPE_OTA_INFO);
                Log.i(TAG, "isSendCmd2 = " + isSendCmd2);
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (device.isConnected());*/
            /*
            if (device != null && mAdapter != null) {
                if (mAdapter.connectDevice(device)) { // 1. 建立连接
                    device.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_CONNECTED);
                }
            }
            */
            // 5. 跳转到OTA界面，选择OTA文件
            //mHandler.sendMessage(mHandler.obtainMessage(MSG_OTA));


           /* mProgressDialog.dismiss();
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Bundle bundle = new Bundle();
            bundle.putParcelable("otaDevice", device);
            Intent intent = new Intent(MainActivity.this, OTAActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);*/

            // 4. 重新发起连接
            //mHandler.sendMessage(mHandler.obtainMessage(MSG_RECONNECT));
        }
    }

    private boolean deviceExisted(MyBluetoothDevice device) {
        boolean result = false;
        if (device != null) {
            Iterator<MyBluetoothDevice> it = mDevices.iterator();
            while (it.hasNext()) {
                MyBluetoothDevice d = it.next();
                if (d != null && d.equals(device)) {
                    result = true;
                }
            }
        } else {
            Log.i(TAG, "deviceExisted # device is null");
            result = false;
        }
        return result;
    }

    /**
     * 检查蓝牙相关
     */
    private void checkBLEFeature() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ToastUtils.showShortToast(MainActivity.this, R.string.ble_not_supported);
        }

        final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (mBluetoothAdapter == null) {
            ToastUtils.showShortToast(MainActivity.this, R.string.error_bluetooth_not_supported);
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            }
        }
    }


    /**
     * 检查位置权限是否授权
     */
    private void checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(this, "Android6.0以后需要打开位置权限才能使用Ble搜索",Toast.LENGTH_SHORT).show();
                }

                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    /**
     * 检查位置权限是否授权
     */
    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "需要权限访问固件存储位置",Toast.LENGTH_SHORT).show();
            }

            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //用户允许改权限，0表示允许，-1表示拒绝 PERMISSION_GRANTED = 0， PERMISSION_DENIED = -1
                // 授权后的操作
            } else {
                // 未授权的操作
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * 检查是否打开定位
     */
    public static final boolean isLocationEnable(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = false;
        boolean gpsProvider = false;
        if (locationManager != null) {
            networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }
        return networkProvider || gpsProvider;
    }

    /**
     * 打开定位
     */
    private void setLocationService() {
        Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        this.startActivityForResult(locationIntent, REQUEST_CODE_LOCATION_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_LOCATION_SETTINGS) {
            if (isLocationEnable(this)) {
                // 定位已打开
            } else {
                // 未打开
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((System.currentTimeMillis() - ExitTime) > 2000) {
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                ExitTime = System.currentTimeMillis();
            } else {
                System.exit(0);
            }
        }
        return true;
    }
}
