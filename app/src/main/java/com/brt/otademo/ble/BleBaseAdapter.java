package com.brt.otademo.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.SyncStateContract;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;


import com.brt.otademo.Constants;
import com.brt.otademo.bean.MyBluetoothDevice;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class BleBaseAdapter {
    private static final String TAG = "BleBaseAdapter";
    private BluetoothAdapter mAdapter;
    private static BleBaseAdapter bleBaseAdapter;
    private List<MyBluetoothDevice> mList;
    private boolean isBtEnable = false;
    private MyHandler mHandler;
    private Context mContext;
    private boolean mReceiverTag = false;
    private ArrayList<EventReceiver> mEventReceiverList;
    private MyBluetoothManager mConnManager;
    //private BluetoothAdapter.LeScanCallback mLeScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean mDiscoveryOnlyBonded;


    public static final int MESSAGE_BLUETOOTH_ON = 1;
    public static final int MESSAGE_BLUETOOTH_OFF = 2;
    public static final int MESSAGE_DEVICE_CONNECTED = 3;
    public static final int MESSAGE_DEVICE_DISCONNECTED = 4;
    public static final int MESSAGE_DEVICE_CONNECT_FAILED = 5;
    public static final int MESSAGE_DEVICE_FOUND = 6;
    public static final int MESSAGE_DISCOVERY_FINISHED = 7;
    public static final int MESSAGE_WRITE_FAILED = 8;
    public static final int MESSAGE_LE_SERVICES_DISCOVERED = 9;
    public static final int MESSAGE_LE_BLE_READY = 10;
    public static final int MESSAGE_LE_WRITE_SUCCEED = 11;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public BleBaseAdapter(Context context) {
        this.mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new MyHandler(this, context);
        mConnManager = new MyBluetoothManager(context, mHandler);
        mList = new ArrayList<>();
        registerReceiver();
        mBluetoothLeScanner = mAdapter.getBluetoothLeScanner();
    }

    /**
     * 获取到 bleBaseAdapter
     *
     * @param context
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static BleBaseAdapter sharedInstance(Context context) {
        Log.i("BleBaseAdapter", "Enter sharedInstance");
        if (bleBaseAdapter == null) {
            Log.i("BleBaseAdapter", "new");
            if (context != null) {
                bleBaseAdapter = new BleBaseAdapter(context);
            }
        } else {
            Log.i("BleBaseAdapter", "exist");
        }
        Log.i("BleBaseAdapter", "Leave sharedInstance");
        return bleBaseAdapter;
    }

    /**
     * 扫描回调
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.i(TAG, "# startScan # onLeScan # device = " + device + ",type=" + device.getType());
            MyBluetoothDevice dev = createDevice(device);
            dev.setScanRecord(scanRecord);
            dev.setRssi(rssi);

            Message msg = mHandler.obtainMessage(BleBaseAdapter.MESSAGE_DEVICE_FOUND);
            msg.obj = dev;
            mHandler.sendMessage(msg);

            String devAddress = dev.getDeviceAddress();
            BluetoothIBridgeOTA bluetoothIBridgeOTA = BluetoothIBridgeOTA.getSharedInstance(mContext);
            // 将 C0 改成 D0
            if (bluetoothIBridgeOTA != null) {
                String otaAddress = bluetoothIBridgeOTA.getBrDeviceAddress();
                Log.i(TAG, "# bluetoothIBridgeOTA # address = " + otaAddress);
                if (!TextUtils.isEmpty(otaAddress)) {
                    //String newLastDevAddress = devAddress.substring(1, devAddress.length());
                   // String newOtaAddress = otaAddress.substring(1, otaAddress.length());
                    if (devAddress.equals(otaAddress)) {
                        bluetoothIBridgeOTA.startOTA(dev);
                        Log.i(TAG, "reConnect # dev = " +dev);
                    }
                }
            }
        }
    };

    private ScanCallback scanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            MyBluetoothDevice dev = createDevice(result.getDevice());
            ScanRecord record = result.getScanRecord();
            dev.setScanRecord(record.getBytes());
            dev.setRssi(result.getRssi());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    /**
     * 根据 UUID 搜索 设备
     *
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean startScan() {
        Log.i(TAG, "StartScan() in ...");
       // List<ScanFilter> scanFilters = new ArrayList<>();
        //scanFilters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(Constants.SERVICE)).build());
        boolean result = false;
        if (isEnabled() && bleIsSupported() && mAdapter != null) {
            /*
            if (Build.VERSION.SDK_INT >= 21) {
                mBluetoothLeScanner.startScan(scanFilters, null, scanCallback);
                result = true;
            } else {
                final boolean b = mAdapter.startLeScan(new UUID[]{UUID.fromString(Constants.REK_SERVICE)}, mLeScanCallback);
                Log.i(TAG, "# mAdapter.startLeScan = " + b);
                result = true;
            }
            */
            //final boolean b = mAdapter.startLeScan(new UUID[]{UUID.fromString(Constants.SERVICE)}, mLeScanCallback);
            final boolean b = mAdapter.startLeScan(null, mLeScanCallback);
            Log.i(TAG, "# mAdapter.startLeScan = " + b);
            result = true;
        }
        return result;
    }

    /**
     * 停止扫描
     */
    public void stopScan() {
        if (isEnabled() && bleIsSupported()) {
            /*
            if (Build.VERSION.SDK_INT >= 21) {
                stopScanning();
            } else {

            }
            */
            mAdapter.stopLeScan(mLeScanCallback);
            Log.i(TAG, "stopScan()");
            if (!mAdapter.isDiscovering()) {
                onEventReceived(MESSAGE_DISCOVERY_FINISHED, null, null, null);
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopScanning() {
        if (mBluetoothLeScanner != null) {
            mBluetoothLeScanner.startScan(scanCallback);
            Log.i(TAG, "stopScan()");
        }
    }

    public boolean startDiscovery() {
        return this.startDiscovery(false);
    }

    public boolean startDiscovery(boolean onlyBonded) {
        Log.i("BluetoothIBridgeAdapter", "startDiscovery...");
        boolean result = false;
        if(this.isEnabled()) {
            this.mDiscoveryOnlyBonded = onlyBonded;
            if(this.mAdapter.isDiscovering()) {
                Log.i("BluetoothIBridgeAdapter", "stop previous discovering");
                this.mAdapter.cancelDiscovery();
            }

            if(onlyBonded) {
                Log.i("BluetoothIBridgeAdapter", "startDiscovery only bonded");
            } else {
                Log.i("BluetoothIBridgeAdapter", "startDiscovery");
            }

            this.mAdapter.startDiscovery();
            result = true;
        } else {
            Log.e("BluetoothIBridgeAdapter", "bluetooth is not enabled");
        }

        Log.i("BluetoothIBridgeAdapter", "startDiscovery.");
        return result;
    }


    public void stopDiscovery() {
        Log.i("BluetoothIBridgeAdapter", "stopDiscovery ...");
        if(this.isEnabled()) {
            this.mAdapter.cancelDiscovery();
        } else {
            Log.e("BluetoothIBridgeAdapter", "bluetooth is not enabled");
        }

        Log.i("BluetoothIBridgeAdapter", "stopDiscovery.");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean startOTA(MyBluetoothDevice device, byte[] ota_data, BluetoothIBridgeOTA.Callback callback) {
        boolean ret = false;
        if (device != null && ota_data != null) {
            if (device.getDeviceType() != MyBluetoothDevice.DEVICE_TYPE_BLE) {
                Log.i("BluetoothIBridgeAdapter", "invalid OTA device type");
                if (callback != null) {
                    callback.onOTAFail(1);
                }

                return false;
            } else {
                if (!device.isConnected()) {
                    BluetoothIBridgeOTA bluetoothIBridgeOTA = BluetoothIBridgeOTA.getSharedInstance(this.mContext);
                    if (bluetoothIBridgeOTA != null) {
                        bluetoothIBridgeOTA.setAdapter(this);
                        bluetoothIBridgeOTA.setCallback(callback);
                        bluetoothIBridgeOTA.setOTAData(ota_data);
                        bluetoothIBridgeOTA.setOTADevice(device);
                        bluetoothIBridgeOTA.startOTA(device);
                        //this.startScan();
                    }

                    ret = true;
                } else {
                    callback.onOTAFail(MESSAGE_DEVICE_FOUND);
                }

                return ret;
            }
        } else {
            Log.i("BluetoothIBridgeAdapter", "OTA parameter invalid");
            if (callback != null) {
                callback.onOTAFail(1);
            }

            return false;
        }
    }


    public void stopOTA() {
        BluetoothIBridgeOTA bluetoothIBridgeOTA = BluetoothIBridgeOTA.getSharedInstance(this.mContext);
        if (bluetoothIBridgeOTA != null) {
            bluetoothIBridgeOTA.stopOTA();
        }

    }


    /**
     * 连接设备
     *
     * @param device
     * @return
     */
    public boolean connectDevice(MyBluetoothDevice device) {
        boolean result = false;
        if (isEnabled() && device != null) {
            Log.i(TAG, "Device is start to connect");
            //stopScan();//在连接前停止扫描
            if (mConnManager != null) {
                mConnManager.connect(device);
                result = true;
            } else {
                Log.i(TAG, "mConnManager is null");
            }
        } else {
            Log.i(TAG, "Connect failed " + "# isEnabled = " + isEnabled() + " # device = " + device);
            onEventReceived(MESSAGE_DEVICE_CONNECT_FAILED, device, "parameter invalid", null);
        }
        return result;
    }

    public void disconnectDevice(MyBluetoothDevice device) {
        if (isEnabled() && device != null && mConnManager != null) {
            mConnManager.disconnect(device);
            Log.i(TAG, "DisconnectDevice");
        }
    }

    public boolean sendCmd(MyBluetoothDevice device, byte[] buffer, int cmdType) {
        boolean b = false;
        if (isEnabled() && device != null && mConnManager != null) {
            b = mConnManager.write(device, buffer, cmdType);
            Log.i(TAG, "sendCmd");
        }
        return b;
    }

    public MyBluetoothDevice createDevice(BluetoothDevice device) {
        if (device != null) {
            Iterator list = mList.iterator();
            while (list.hasNext()) {
                MyBluetoothDevice idev = (MyBluetoothDevice) list.next();
                if (idev != null && idev.isSameDevice(device)) {
                    return idev;
                }
            }
            MyBluetoothDevice newDevice = new MyBluetoothDevice(device);
            mList.add(newDevice);
            Log.i(TAG, "createDevice ...");
            return newDevice;
        } else {
            Log.i(TAG, "createDevice # device is null");
        }
        return null;
    }

    public void clearDeviceList() {
        mList.clear();
    }

    public void destroy() {
        Log.i(TAG, "destroy()");
        unregisterReceiver();
        mContext = null;
        if (mConnManager != null) {
            mConnManager.destroy();
            mConnManager = null;
        }
        BleBaseAdapter.bleBaseAdapter = null;
    }

    public int getRssiVal() {
        int result = 0;
        if (mConnManager != null) {
            result = mConnManager.getRssiValue();
            Log.i(TAG, "# getRssiVal ...");
        }
        return result;
    }

    /**
     * 蓝牙是否打开
     *
     * @return
     */
    public boolean isEnabled() {
        Log.i(TAG, " isEnabled");
        if (mAdapter != null) {
            isBtEnable = mAdapter.isEnabled();
        }
        return isBtEnable;
    }

    public void setEnabled(boolean enabled) {
        Log.i(TAG, " setEnabled # enabled = " + enabled);
        if (isEnabled() == enabled) {
            Log.i(TAG, "Bluetooth is already enabled");
            return;
        }
        if (mAdapter == null) {
            Log.e(TAG, "bluetooth adapter is null");
        }
        if (enabled) {
            Log.i(TAG, "enable bluetooth");
            mAdapter.enable();
        } else {
            Log.i(TAG, "disable bluetooth");
            mAdapter.disable();
        }
        Log.i(TAG, "setEnabled.");
    }

    /**
     * 手机是否支持BLE
     *
     * @return
     */
    public boolean bleIsSupported() {
        Log.i(TAG, "bleIsSupported");
        if (Build.VERSION.SDK_INT >= 18) {
            return true;
        } else {
            Log.e(TAG, "BLE can not be supported");
            return false;
        }
    }

    public static class MyHandler extends Handler {
        public static final String BUNDLE_EXCEPTION = "exception";
        private final WeakReference<BleBaseAdapter> mAdapter;

        public MyHandler(BleBaseAdapter adapter, Context context) {
            super(context.getMainLooper());
            mAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String exceptionMessage = null;
            String serviceUUID = null;
            Bundle bundle = msg.getData();
            if (bundle != null) {
                exceptionMessage = bundle.getString(MyHandler.BUNDLE_EXCEPTION);
                serviceUUID = bundle.getString("serviceUUID");
            }

            BleBaseAdapter adapter = mAdapter.get();
            Log.i(TAG, "Receive message : " + BleBaseAdapter.messageString(msg.what));
            MyBluetoothDevice device = (MyBluetoothDevice) msg.obj;
            switch (msg.what) {
                case MESSAGE_DEVICE_CONNECTED:
                    break;
            }
            if (adapter != null) {
                adapter.onEventReceived(msg.what, device, exceptionMessage, serviceUUID);
            }
        }
    }

    private void registerReceiver() {
        Log.i(TAG, "# registerReceiver() in ...");
        if (mContext != null && !mReceiverTag) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
            intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

            if (broadcastReceiver != null) {
                mContext.registerReceiver(broadcastReceiver, intentFilter);
                Log.i(TAG, " registerReceiver finished");
                mReceiverTag = true;
            } else {
                Log.i(TAG, "broadcastReceiver is null");
            }
        } else {
            Log.i(TAG, "mContext = " + mContext + " # mReceiverTag = " + mReceiverTag);
        }
        Log.i(TAG, "# registerReceiver() out ...");
    }

    public void unregisterReceiver() {
        Log.i(TAG, "# unregisterReceiver() in ...");
        if (mReceiverTag) {
            if (mContext != null && broadcastReceiver != null) {
                mContext.unregisterReceiver(broadcastReceiver);
                mReceiverTag = false;
            } else {
                Log.i(TAG, "mContext = " + mContext + " # broadcastReceiver = " + broadcastReceiver);
            }
        } else {
            Log.i(TAG, " mReceiverTag is false ");
        }
        Log.i(TAG, "# unregisterReceiver() out ...");
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String exceptionMessage = null;
            String action = intent.getAction();
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                exceptionMessage = bundle.getString(MyHandler.BUNDLE_EXCEPTION);
            }

            Log.i(TAG, "broadcast message:" + action);
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                Log.i(TAG, "# BroadcastReceiver   # ACTION_FOUND");
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                onEventReceived(MESSAGE_DEVICE_FOUND, createDevice(dev), exceptionMessage, null);
            }
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                Log.i(TAG, "# BroadcastReceiver   # ACTION_DISCOVERY_FINISHED");
                onEventReceived(MESSAGE_DISCOVERY_FINISHED, null, exceptionMessage, null);
            }
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "# BroadcastReceiver   # ACTION_STATE_CHANGED  # STATE_ON");
                    isBtEnable = true;
                    onEventReceived(MESSAGE_BLUETOOTH_ON, null, exceptionMessage, null);
                }
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    Log.i(TAG, "# BroadcastReceiver   # ACTION_STATE_CHANGED  # STATE_OFF");
                    isBtEnable = false;
                    if (mConnManager != null) {
                        mConnManager.destroy();
                    }
                    onEventReceived(MESSAGE_BLUETOOTH_OFF, null, exceptionMessage, null);
                }
            }
        }
    };

    private static String messageString(int message) {
        switch (message) {
            case MESSAGE_DEVICE_CONNECTED:
                return "MESSAGE_DEVICE_CONNECTED";
            case MESSAGE_DEVICE_DISCONNECTED:
                return "MESSAGE_DEVICE_DISCONNECTED";
            case MESSAGE_DEVICE_CONNECT_FAILED:
                return "MESSAGE_DEVICE_CONNECT_FAILED";
        }
        return "MESSAGE";
    }

    public void onDataReceived(MyBluetoothDevice device, byte[] data){
        if (mEventReceiverList != null){
            for (int i = 0; i < mEventReceiverList.size(); i++){

            }
        }
    }

    public void onEventReceived(int what, MyBluetoothDevice device, String exceptionMessage, String serviceUUID) {
        if (mEventReceiverList != null) {
            for (int i = 0; i < mEventReceiverList.size(); i++) {
                EventReceiver eventReceiver = mEventReceiverList.get(i);
                switch (what) {
                    case MESSAGE_BLUETOOTH_ON:
                        eventReceiver.onBluetoothOn();
                        break;
                    case MESSAGE_BLUETOOTH_OFF:
                        eventReceiver.onBluetoothOff();
                        break;
                    case MESSAGE_DEVICE_CONNECTED:
                        eventReceiver.onDeviceConnected(device);
                        break;
                    case MESSAGE_DEVICE_DISCONNECTED:
                        eventReceiver.onDeviceDisconnected(device, exceptionMessage);
                        break;
                    case MESSAGE_DEVICE_CONNECT_FAILED:
                        eventReceiver.onDeviceConnectFailed(device, exceptionMessage);
                        break;
                    case MESSAGE_DEVICE_FOUND:
                        if (device != null) {
                            //Log.i(TAG, "#onEventReceived #device = " + device);
                            eventReceiver.onDeviceFound(device);
                        }
                        break;
                    case MESSAGE_DISCOVERY_FINISHED:
                        eventReceiver.onDiscoveryFinished();
                        break;
                    case MESSAGE_WRITE_FAILED:
                        eventReceiver.onWriteFailed(device, exceptionMessage);
                        break;
                    case MESSAGE_LE_SERVICES_DISCOVERED:
                        if (device != null) {
                            eventReceiver.onLeServiceDiscovered(device, exceptionMessage, serviceUUID);
                        }
                        break;
                    case MESSAGE_LE_BLE_READY:
                        if (device != null) {
                            eventReceiver.onBleIsReady(device, exceptionMessage);
                        }
                        break;
                    case MESSAGE_LE_WRITE_SUCCEED:
                        if (device != null) {
                            eventReceiver.onWriteSucceed(device, exceptionMessage);
                        }
                        break;
                }
            }
        }
    }

    public void registerEventReceiver(EventReceiver receiver) {
        Log.i(TAG, "registerEventReceiver()");
        if (receiver == null) {
            Log.i(TAG, "receiver is null");
        }
        if (mEventReceiverList == null) {
            mEventReceiverList = new ArrayList<>();
        }
        if (!mEventReceiverList.contains(receiver)) {
            mEventReceiverList.add(receiver);
        }
    }

    public void unregisterEventReceiver(EventReceiver receiver) {
        Log.i(TAG, "unregisterEventReceiver()");
        if (mEventReceiverList != null) {
            mEventReceiverList.remove(receiver);
        }
    }

    public void registerDataReceiver(DataReceiver dataReceiver) {
        if (mConnManager != null) {
            mConnManager.registerDataReceiver(dataReceiver);
            Log.i(TAG, "registerDataReceiver");
        }
    }

    public void unregisterDataReceiver(DataReceiver dataReceiver) {
        if (mConnManager != null) {
            mConnManager.unregisterDataReceiver(dataReceiver);
            Log.i(TAG, "unregisterDataReceiver");
        }
    }

    public interface EventReceiver {
        void onBluetoothOn();

        void onBluetoothOff();

        void onDiscoveryFinished();

        void onDeviceFound(MyBluetoothDevice device);

        void onDeviceConnected(MyBluetoothDevice device);

        void onDeviceDisconnected(MyBluetoothDevice device, String exceptionMsg);

        void onDeviceConnectFailed(MyBluetoothDevice device, String exceptionMsg);

        void onWriteFailed(MyBluetoothDevice device, String exceptionMsg);

        void onWriteSucceed(MyBluetoothDevice device, String exceptionMsg);

        void onLeServiceDiscovered(MyBluetoothDevice device, String exceptionMsg, String serviceUUID);

        void onBleIsReady(MyBluetoothDevice device, String exceptionMsg);
    }

    public interface DataReceiver {
        void onDataReceived(MyBluetoothDevice device, byte[] bytes, int length);
    }

}
