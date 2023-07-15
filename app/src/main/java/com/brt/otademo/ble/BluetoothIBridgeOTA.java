package com.brt.otademo.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.brt.otademo.bean.MyBluetoothDevice;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dell on 2017/6/21.
 */

public class BluetoothIBridgeOTA {
    private static final String TAG = "BluetoothIBridgeOTA";
    private BleBaseAdapter mAdapter;
    private MyBluetoothDevice mMyBluetoothDevice;
    private Callback mCallback;
    private byte[] mOTAData;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mOTAService;
    private BluetoothGattCharacteristic mOTACharacteristic;
    private int mOTAMtu = 20;
    private OTAThread mOTAThread;
    protected final Handler mTimeoutHandler = new Handler(Looper.getMainLooper());
    protected final Runnable mOTAAckTimeoutRunnable = new OTAAckTimeoutRunnable();
    protected int mAckTimeinterval = 2000;
    int progressStatus = 0;
    private String m_BrDeviceAddress;

    public static final int ERROR_INVALID_PARAMETER = 1;
    public static final int ERROR_DATA_SEND_TIMEOUT = 2;
    public static final int ERROR_CONNECT_FAILED = 3;
    public static final int ERROR_DISCOVER_SERVICE_FAILED = 4;
    public static final int ERROR_DATA_SEND_FAILED = 5;
    public static final int ERROR_DEVICE_ALREADY_CONNECTED = 6;
    public static final int ERROR_USER_CANCEL = 7;
    public static final int ERROR_WRITE_DESCRIPTOR_FAILED = 8;

    private static final int MSG_INVALID_PARAMETER = 1;
    private static final int MSG_CONNECT_FAILED = 2;
    private static final int MSG_DSICOVER_SERVICE_FAILED = 3;

    private static final int MSG_ON_CHARACTERISTIC_WRITE = 4;
    private static final int MSG_ON_CHARACTERISTIC_CHANGED = 5;

    private final static int MSG_UPDATE_OTA_PROGRESS_BAR = 6;
    private final static int MSG_OTA_START_COMMAND_DATA_INVALID = 7;
    private final static int MSG_OTA_START_COMMAND_SEND_FAILED = 8;
    private final static int MSG_OTA_DATA_COMMAND_SEND_FAILED = 9;
    private final static int MSG_ON_DESCRIPTOR_WRITE_FAILED = 10;

    private MyOTAEventHandler myOTAEventHandler;

    public static String SERVICE_OTA = "0000FF10-0000-1000-8000-00805f9b34fb";
    public static String CHARACTERISTIC_OTA = "0000FF11-0000-1000-8000-00805f9b34fb";
    public static String DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = "00002902-0000-1000-8000-00805f9b34fb";//0x2902
    private static final int OTA_MUT_REQ = 255;

    public static BluetoothIBridgeOTA mInstance = null;
    private Context mContext;
    private final static int OTA_PACKET_HEADER_LENGTH = 4;
    private final static int OTA_PACKET_CS_LENGTH = 1;
    final static int MAX_PROGRESS = 100;
    private boolean mIsOTA = false;

    public static BluetoothIBridgeOTA getSharedInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BluetoothIBridgeOTA(context);
        }
        return mInstance;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    public void setAdapter(BleBaseAdapter adapter) {
        this.mAdapter = adapter;
    }

    public void setOTADevice(MyBluetoothDevice device) {
        if (device.isConnected()) {
            int iCount = 0;
            do {
                try {
                    Thread.sleep(1000);
                    if (!device.isConnected()) {
                        break;
                    }
                } catch (Exception e) {
                }
                iCount++;
            } while (iCount < 30);
        }
        m_BrDeviceAddress = device.getDeviceAddress();
        Log.i(TAG, "# setOTADevice = " + m_BrDeviceAddress);
    }

    public String getBrDeviceAddress() {
        return this.m_BrDeviceAddress;
    }

    public MyBluetoothDevice getOTADevice() {
        if (m_BrDeviceAddress == null || m_BrDeviceAddress.equals("")) {
            return null;
        }

        if (mMyBluetoothDevice != null) {
            StringBuilder stringBuilder = new StringBuilder(m_BrDeviceAddress);
            stringBuilder.setCharAt(0, 'C');
        }
        return this.mMyBluetoothDevice;
    }

    public void setOTAData(byte[] data) {
        this.mOTAData = data;
    }

    public BluetoothIBridgeOTA(Context context) {
        mMyBluetoothDevice = null;
        this.mContext = context;
        myOTAEventHandler = new MyOTAEventHandler();
    }

    public void destroy() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mOTAService = null;
        mOTACharacteristic = null;
        mOTAData = null;
        mMyBluetoothDevice = null;
        mInstance = null;
        m_BrDeviceAddress = null;
    }

    public boolean startOTA(MyBluetoothDevice dev) {
        Log.i(TAG, "startOTA");
        if (!mIsOTA) {
            mAdapter.stopScan();
            mMyBluetoothDevice = dev;
            if (mMyBluetoothDevice != null) {
                connect();
                mIsOTA = true;
                return true;
            } else {
                if (mMyBluetoothDevice == null) {
                    Log.i(TAG, "mIBridgedevice == null");
                    myOTAEventHandler.sendEmptyMessage(MSG_INVALID_PARAMETER);
                } else {
                    Log.i(TAG, "device type error");
                    myOTAEventHandler.sendEmptyMessage(MSG_INVALID_PARAMETER);
                }
            }
        } else {
            Log.i(TAG, "haven't start OTA and mIsOTA = true");
        }
        return false;
    }

    public void stopOTA() {
        Log.i(TAG, "stopOTA");
        if (mOTAThread != null) {
            cancelOTAAckTimeoutTask();
            mOTAThread.closeThread();
            OTAThread hThread = new OTAThread();
            hThread.sendOTACompleteCommand();
            if (mCallback != null) {
                mCallback.onOTAFail(ERROR_USER_CANCEL);
            }
            hThread = null;
        } else {
            Log.i(TAG, "OTA thread is null");
        }


        if (mBluetoothGatt != null) {
            Log.i(TAG, "mBluetoothGatt is not null");
            mBluetoothGatt.disconnect();
            //mBluetoothGatt.close();
            //mBluetoothGatt = null;
            if (mCallback != null) {
                mCallback.onDisconnect();
                mIsOTA = false;
            }
        } else {
            Log.i(TAG, "mBluetoothGatt is null");
        }
        mOTAService = null;
        mOTACharacteristic = null;
        mOTAMtu = 20;
        mMyBluetoothDevice = null;
        m_BrDeviceAddress = null;
    }

    private void connectFailed() {
        Log.i(TAG, "connectFailed");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        if (myOTAEventHandler != null) {
            myOTAEventHandler.sendEmptyMessage(MSG_CONNECT_FAILED);
        }
    }

    private void onServiceDiscoverFailed() {
        Log.i(TAG, "onServiceDiscoverFailed");//otaService
        refreshDeviceCache();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
        if (myOTAEventHandler != null) {
            myOTAEventHandler.sendEmptyMessage(MSG_DSICOVER_SERVICE_FAILED);
        }
    }

    private void onRequestMTUFailed() {
        Log.i(TAG, "onRequestMTUFailed"); //reuqest mtu failed use 20 by default
        this.onConnect();
    }

    private void disconnect() {
        Log.i(TAG, "disconnect");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    private void onDisconnect() {
        Log.i(TAG, "onDisconnect");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        if (mOTAThread != null) {
            cancelOTAAckTimeoutTask();
            mOTAThread.closeThread();
        }
        mOTAService = null;
        mOTACharacteristic = null;
        mOTAMtu = 20;
        mOTAData = null;
        mMyBluetoothDevice = null;
        mInstance = null;
        m_BrDeviceAddress = null;
    }

    public boolean refreshDeviceCache() {
        if (mBluetoothGatt != null) {
            try {
                Method localMethod = mBluetoothGatt.getClass().getMethod("refresh");
                if (localMethod != null) {
                    boolean bool = (Boolean) localMethod.invoke(mBluetoothGatt);
                    return bool;
                }
                Log.i(TAG, "refreshDeviceCache complete");
            } catch (Exception localException) {
                Log.i(TAG, "An exception occured while refreshing device");
            }
        }
        return false;
    }

    private boolean setCharacteristicNotify(BluetoothGattCharacteristic characteristic, boolean bStart) {
        Log.i(TAG, "setCharacteristicNotify, bStart=" + bStart);
        boolean ret = false;
        BluetoothGattDescriptor cccDescriptor = null;
        List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
        if (descriptors.size() > 0) {
            for (int i = 0; i < descriptors.size(); i++) {
                BluetoothGattDescriptor descriptor = descriptors.get(i);
                if (descriptor.getUuid().toString().equalsIgnoreCase(DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION)) {
                    cccDescriptor = descriptor;
                    break;
                }
            }
        }
        if (cccDescriptor != null) {
            if (mBluetoothGatt != null
                    && mBluetoothGatt.setCharacteristicNotification(characteristic, bStart)) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                Log.i(TAG, "change OTA characteristic writetype WRITE_TYPE_DEFAULT");
                if (bStart) {
                    cccDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    cccDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                }
                if (!mBluetoothGatt.writeDescriptor(cccDescriptor)) {
                    Log.i(TAG, "writeDescriptor failed");
                } else {
                    ret = true;
                }
            }
        }
        return ret;
    }

    private void connect() {
        Log.i(TAG, "connect");
        BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mMyBluetoothDevice
                .getDeviceAddress());
        Log.i(TAG, "connect: dev " + dev);
        if (dev != null) {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothGatt = dev.connectGatt(this.mContext, false, mBluetoothGattCallback);
            } else {
                Class<? extends BluetoothDevice> cls = BluetoothDevice.class;
                Method m = null;
                try {
                    m = cls.getMethod("connectGatt", Context.class, boolean.class,
                            BluetoothGattCallback.class, int.class);
                    if (m != null) {
                        try {
                            mBluetoothGatt = (BluetoothGatt) m.invoke(dev, mContext, false, mBluetoothGattCallback, 2);
                            Log.i(TAG, "connect # mBluetoothGatt :" + mBluetoothGatt);
                        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
            if (mCallback != null) {
                mCallback.onConnect();
            }
        } else {
            this.connectFailed();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean requestMTU(int mtu) {
        Log.i(TAG, "requestMTU");
        if (mBluetoothGatt == null) {
            return false;
        }
        return mBluetoothGatt.requestMtu(mtu);
    }

    private boolean discoverGattServices() {
        Log.i(TAG, "discoverGattServices");
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.discoverServices();
        }
        return false;
    }

    private void onConnect() {
        Log.i(TAG, "onConnect");
        if (mOTACharacteristic != null) {
            if (!setCharacteristicNotify(mOTACharacteristic, true)) {
                Log.i(TAG, "setCharacteristicNotify failed");
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                    if (mCallback != null) {
                        mCallback.onDisconnect();
                        mIsOTA = false;
                    }
                }
                return;
            }
        }
        if (mOTAThread == null) {
            progressStatus = 0;
            mOTAThread = new OTAThread();
            mOTAThread.start();
        }
    }

    private void onServiceFound(List<BluetoothGattService> services) {
        Log.i(TAG, "onServiceFound");
        if (services != null && !services.isEmpty()) {
            for (int i = 0; i < services.size(); i++) {
                BluetoothGattService service = services.get(i);
                if (service.getUuid().toString().equalsIgnoreCase(SERVICE_OTA)) { //FIXME
                    mOTAService = service;
                    break;
                }
            }
            if (mOTAService != null) {
                List<BluetoothGattCharacteristic> characteristics = mOTAService.getCharacteristics();
                if (characteristics != null) {
                    for (int i = 0; i < characteristics.size(); i++) {
                        BluetoothGattCharacteristic characteristic = characteristics.get(i);
                        if (characteristic.getUuid().toString().equalsIgnoreCase(CHARACTERISTIC_OTA)) {
                            mOTACharacteristic = characteristic;
                            break;
                        }
                    }
                }
            }  
        }
        if (mOTAService != null && mOTACharacteristic != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                this.requestMTU(OTA_MUT_REQ);
            } else {
                this.onConnect();
            }
            if (mCallback != null) {
                mCallback.onServiceDiscover();
            }
            Log.i(TAG, "onServiceFound # mOTAService succeed: " + mOTAService + "===== mOTACharacteristic: " + mOTACharacteristic);
         } else {
            Log.i(TAG, "onServiceFound # mOTAService failed : " + mOTAService + "===== mOTACharacteristic: " + mOTACharacteristic);
            onServiceDiscoverFailed();
        }
    }

    private class MyOTAEventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INVALID_PARAMETER:
                    if (mCallback != null) {
                        destroy();
                        mCallback.onOTAFail(ERROR_INVALID_PARAMETER);
                    }
                    mIsOTA = false;
                    break;
                case MSG_CONNECT_FAILED:
                    if (mCallback != null) {
                        destroy();
                        mCallback.onOTAFail(ERROR_CONNECT_FAILED);
                    }
                    mIsOTA = false;
                    break;
                case MSG_DSICOVER_SERVICE_FAILED:
                    if (mCallback != null) {
                        destroy();
                        mCallback.onOTAFail(ERROR_DISCOVER_SERVICE_FAILED);
                    }
                    mIsOTA = false;
                    break;
                case MSG_ON_CHARACTERISTIC_WRITE:
                    int status = (int) msg.obj;
                    onCommandSent(status);
                    break;
                case MSG_ON_CHARACTERISTIC_CHANGED:
                    byte[] data = (byte[]) msg.obj;
                    onAckPacketReceived(data);
                    break;
                case MSG_OTA_START_COMMAND_DATA_INVALID:
                case MSG_OTA_START_COMMAND_SEND_FAILED:
                case MSG_OTA_DATA_COMMAND_SEND_FAILED: {
                    disconnect();
                    if (mCallback != null) {
                        destroy();
                        mCallback.onOTAFail(ERROR_DATA_SEND_FAILED);
                    }
                    mIsOTA = false;
                    break;
                }
                case MSG_UPDATE_OTA_PROGRESS_BAR:
                    int progress = (int) msg.obj;
                    if (mCallback != null) {
                        mCallback.onOTAProgress(progress);
                    }
                    if (progress == MAX_PROGRESS) {
                        Log.i(TAG, "MAX_PROGRESS");
                        refreshDeviceCache();
                        disconnect();
                        onOTAComplete();
                        mIsOTA = false;
                    }
                    break;
                case MSG_ON_DESCRIPTOR_WRITE_FAILED:
                    disconnect();
                    if (mCallback != null) {
                        destroy();
                        mCallback.onOTAFail(ERROR_WRITE_DESCRIPTOR_FAILED);
                    }
                    mIsOTA = false;
                    break;
                default:
                    break;
            }
        }
    }

    private void onOTAComplete() {
        Log.i(TAG, "onOTAComplete");
        if (mCallback != null) {
            destroy();
            mCallback.onOTASuccess();
        }
    }

    private void onOTATransfer(int progress) {
        Log.i(TAG, "onOTATransfer progress:" + progress);
        Message msg = myOTAEventHandler.obtainMessage(MSG_UPDATE_OTA_PROGRESS_BAR);
        msg.obj = (Object) progressStatus;
        myOTAEventHandler.sendMessage(msg);
    }

    private void onOTAError(int msg) {
        Log.i(TAG, "onOTAError msg:" + msg);
        Message message = new Message();
        message.what = msg;
        myOTAEventHandler.sendMessage(message);
    }

    private void onCommandSent(int status) {
        Log.i(TAG, "onCommandSent");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (mOTAThread != null) {
                mOTAThread.onWriteResume();
            } else {
                Log.i(TAG, "++++++++++++++ onCommandSent error");
            }
        } else {
            Log.i(TAG, "++++++++++++++ onCommandSent error status is not success");
        }
    }

    private void onAckPacketReceived(byte[] data) {
        Log.i(TAG, "onAckPacketReceived");
        if (data != null && data.length > 3) {
            byte result = data[2];
            if (data[0] == (byte) 0xFC) { //OTA启动
                if (data[1] == (byte) 0xF1) {
                    if (mOTAThread != null) {
                        mOTAThread.setOTAParameter(mOTAMtu/*(byte)data[3]*/, (byte) data[4]); //设置OTA的参数
                        OTAData dataEvent = mOTAThread.getLoopList(0);
                        if (dataEvent != null) {
                            if (dataEvent.type == OTAData.COMMAND_TYPE_START_OTA
                                    && dataEvent.status == OTAData.COMMAND_STATUS_SENT) {
                                dataEvent.status = OTAData.COMMAND_SEND_SUCCEED;
                                mOTAThread.onCommandNotify(0);
                                Log.i(TAG, "++++++++++ receive OTA start ack");
                            } else {
                                Log.i(TAG, "++++++++++ Start OTA command event error");
                            }
                        } else {
                            Log.i(TAG, "++++++++++ dataEvent is null");
                        }
                    }
                } else if (data[1] == (byte) 0xF2) { //OTA数据传输
                    cancelOTAAckTimeoutTask();
                    int index = 0;
                    int high = 0;
                    int low = 0;
                    if (data[4] < 0) {
                        high = (int) (256 + data[4]);
                    } else {
                        high = data[4];
                    }
                    if (data[3] < 0) {
                        low = (int) (256 + data[3]);
                    } else {
                        low = data[3];
                    }
                    index = (high << 8) + low;
                    if (index == 10159) {
                        Log.i(TAG, "trace error");
                    }
                    if (result == 0x00) {
                        Log.i(TAG, "index=" + Integer.toString(index));
                        if (mOTAThread != null) {
                            mOTAThread.setCurrentIndex(index);
                        }
                    } else {
                        Log.i(TAG, "OTA data transfer failed index=" + Integer.toString(index));
                        if (mOTAThread != null) {
                            mOTAThread.setCurrentIndex(-1);
                        }
                    }
                    Log.i(TAG, "++++++++++ get OTA ack from chip");
                    if (mOTAThread != null) {
                        OTAData dataEvent = mOTAThread.getLoopList(index + 1);
                        if (dataEvent != null) {
                            if (dataEvent.type == OTAData.COMMAND_TYPE_OTA_DATA
                                    && dataEvent.status == OTAData.COMMAND_STATUS_SENT) {
                                dataEvent.status = OTAData.COMMAND_SEND_SUCCEED;
                                Log.i(TAG, "++++++++++ receive OTA start ack, set " + (index + 1));
                                mOTAThread.onCommandNotify(index + 1);
                            } else {
                                Log.i(TAG, "++++++++++ Start OTA command event error");
                            }
                        } else {
                            Log.i(TAG, "++++++++++ dataEvent is null");
                        }
                    }
                }
            } else if (data[0] == (byte) 0xF3) { //OTA结束
                Log.i(TAG, "++++++++++++++ F3");
                cancelOTAAckTimeoutTask();
                if (mOTAThread != null) {
                    int count = mOTAThread.getLoopListCount();
                    if (count > 0) {
                        OTAData dataEvent = mOTAThread.getLoopList(count - 1);
                        if (dataEvent != null) {
                            if (dataEvent.type == OTAData.COMMAND_TYPE_OTA_DATA
                                    && dataEvent.status == OTAData.COMMAND_STATUS_SENT) {
                                dataEvent.status = OTAData.COMMAND_SEND_SUCCEED;
                                mOTAThread.onCommandNotify(count - 1);
                            } else {
                                Log.i(TAG, "++++++++++ Start OTA command event error");
                            }
                        } else {
                            Log.i(TAG, "++++++++++ dataEvent is null");
                        }
                    } else {
                        Log.i(TAG, "++++++++++ LoopListCount is null");
                    }
                }
            }
        }
    }

    private List<BluetoothGattService> getSupportedGattService() {
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.getServices();
        }
        return null;
    }

    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "onConnectionStateChange, newState = " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Attempting to start service discovery: " + discoverGattServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (myOTAEventHandler != null) {
                    myOTAEventHandler.sendEmptyMessage(MSG_CONNECT_FAILED);
                }
                //refreshDeviceCache();//清除蓝牙缓存
                onDisconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(TAG, "onServicesDiscovered, status = " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onServiceFound(getSupportedGattService());
            } else {
                onServiceDiscoverFailed();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "onCharacteristicRead, status=" + status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "onCharacteristicWrite, status=" + status);
            if (characteristic.getUuid().toString().equalsIgnoreCase(CHARACTERISTIC_OTA)) {
                if (myOTAEventHandler != null) {
                    Message msg = myOTAEventHandler.obtainMessage(MSG_ON_CHARACTERISTIC_WRITE);
                    msg.obj = status;
                    myOTAEventHandler.sendMessage(msg);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, "onCharacteristicChanged");
            if (characteristic.getUuid().toString().equalsIgnoreCase(CHARACTERISTIC_OTA)) {
                if (myOTAEventHandler != null) {
                    Message msg = myOTAEventHandler.obtainMessage(MSG_ON_CHARACTERISTIC_CHANGED);
                    byte[] data = characteristic.getValue();
                    Log.i(TAG, "data:" + bytesToHexString(data));
                    msg.obj = (Object) data;
                    myOTAEventHandler.sendMessage(msg);
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "onDescriptorWrite, status:" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                if (characteristic != null && characteristic.getUuid().toString()
                        .equalsIgnoreCase(CHARACTERISTIC_OTA)) {
                    if (gatt.equals(mBluetoothGatt)) {
                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        Log.i(TAG, "change OTA characteristic writetype to no response");
                    }
                }
            } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                if (myOTAEventHandler != null) {
                    Message msg = myOTAEventHandler.obtainMessage(MSG_ON_DESCRIPTOR_WRITE_FAILED);
                    myOTAEventHandler.sendMessage(msg);
                }
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG, "onMtuChanged, mtu:" + mtu + " status:" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mOTAMtu = (mtu - 3);
                onConnect();
            } else {
                onRequestMTUFailed();
            }
            //super.onMtuChanged(gatt, mtu, status);
        }
    };

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

    private void cancelOTAAckTimeoutTask() {
        Log.i(TAG, "cancelOTAAckTimeoutTask");
        this.mTimeoutHandler.removeCallbacksAndMessages(null);
    }

    private final class OTAAckTimeoutRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "OTAAckTimeoutRunnable, call disconnect");
            disconnect();
            if (mCallback != null) {
                destroy();
                mCallback.onOTAFail(ERROR_DATA_SEND_TIMEOUT);
            }
        }
    }


    private boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        Log.i(TAG, "writeCharacteristic");
        boolean ret = false;
        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        Log.i(TAG, "start write characteristic...");
        ret = mBluetoothGatt.writeCharacteristic(characteristic);
        return ret;
    }

    public interface Callback {
        void onOTAFail(int errorCode);

        void onOTASuccess();

        void onOTAProgress(int progress);

        void onConnect();

        void onDisconnect();

        void onServiceDiscover();
    }


    private class OTAData {
        private static final int COMMAND_TYPE_START_OTA = 1;
        private static final int COMMAND_TYPE_OTA_DATA = 2;
        private static final int COMMAND_TYPE_OTA_END = 3;

        private static final int COMMAND_STATUS_READY = 0;
        private static final int COMMAND_STATUS_SENT = 1;
        private static final int COMMAND_STATUS_TIMEOUT = 2;
        private static final int COMMAND_SEND_SUCCEED = 3;

        int index;
        int type;
        byte[] data;
        int status;
        Object command_wait;
        Object write_wait;
    }

    private class OTAThread extends Thread {
        private boolean mCancelFlag = false;
        private boolean isPause = false;
        private boolean isClose = false;
        private int ota_mtu = 0;
        private int ota_credit = 0;
        private int packetIndex = 0;
        private boolean bInit = false;
        private int packetCount = 0;
        private int currentIndex = 0;
        private boolean waitSendDataFlag = false;

        private List<OTAData> loopList = new ArrayList<OTAData>();

        public OTAThread() {

        }

        public synchronized OTAData getLoopList(int index) {
            if (loopList != null && loopList.size() > 0) {
                return loopList.get(index);
            } else {
                return null;
            }
        }

        public synchronized OTAData removeLoopList(int index) {
            if (loopList != null && loopList.size() > 0) {
                return loopList.remove(index);
            } else {
                return null;
            }
        }

        public synchronized void onThreadPause() {
            isPause = true;
        }

        public synchronized int getLoopListCount() {
            if (loopList != null && loopList.size() > 0) {
                return loopList.size();
            } else {
                return -1;
            }
        }

        public synchronized void setOTAParameter(int mtu, int credit) {
            this.ota_mtu = mtu;
            this.ota_credit = credit;
        }

        private void onThreadWait() {
            Log.i(TAG, "onThreadWait");
            try {
                synchronized (this) {
                    isPause = true;
                    this.wait();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void onCommandWait(int index, boolean bEnableTimeout) {
            Log.i(TAG, "onCommandWait bEnableTimeout:" + bEnableTimeout + " index=" + index);
            OTAData dataEvent = loopList.get(index);
            if (dataEvent.command_wait != null) {
                try {
                    synchronized (dataEvent.command_wait) {
                        if (bEnableTimeout) {
                            mTimeoutHandler.postDelayed(mOTAAckTimeoutRunnable, mAckTimeinterval);
                        }
                        if (dataEvent.status == OTAData.COMMAND_STATUS_SENT) {
                            Log.i(TAG, "onCommandWait dataEvent command wait");
                            dataEvent.command_wait.wait();
                        } else {
                            Log.i(TAG, "+++OTA status already changed: " + dataEvent.status);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void onCommandNotify(int index) {
            Log.i(TAG, "onCommandNotify " + index);
            OTAData dataEvent = loopList.get(index);
            if (dataEvent.command_wait != null) {
                try {
                    synchronized (dataEvent.command_wait) {
                        dataEvent.command_wait.notify();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.i(TAG, "+++++ onCommandNotify command_wait is null");
            }
        }

        private void onWriteWait(int index) {
            Log.i(TAG, "onWriteWait" + index);
            OTAData dataEvent = loopList.get(index);
            if (dataEvent.write_wait != null) {
                try {
                    synchronized (dataEvent.write_wait) {
                        dataEvent.write_wait.wait(3000);
                        if (dataEvent.status == OTAData.COMMAND_STATUS_READY) {
                            dataEvent.status = OTAData.COMMAND_STATUS_TIMEOUT;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void onWriteResume() {
            Log.i(TAG, "onWriteResume");
            if (loopList != null) {
                for (int i = 0; i < loopList.size(); i++) {
                    OTAData dataEvent = loopList.get(i);
                    if (dataEvent != null && (dataEvent.status == OTAData.COMMAND_STATUS_READY)) {
                        dataEvent.status = OTAData.COMMAND_STATUS_SENT;
                        onWritNotify(dataEvent);
                        break;
                    }
                }
            }
        }

        private void onWritNotify(OTAData data) {
            Log.i(TAG, "onWritNotify");
            if (data.write_wait != null) {
                try {
                    synchronized (data.write_wait) {
                        data.write_wait.notify();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public synchronized void onThreadResume() {
            Log.i(TAG, "onThreadResume");
            if (isPause) {
                try {
                    synchronized (this) {
                        isPause = false;
                        this.notify();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public synchronized boolean getSendDataWaitFlag() {
            return waitSendDataFlag;
        }

        public byte CalculateCheckWord(byte[] data) {
            int totalvalue = 0;
            int bitmask = 0x00000000FF;
            for (int i = 0; i < data.length; i++) {
                totalvalue += (int) (data[i] & 0xff);
            }
            return (byte) (totalvalue & bitmask);
        }

        public byte[] getOTAStartCommandData() {
            Log.i(TAG, "Enter getOTAStartCommandData");
            byte[] ret = {(byte) 0xF1, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00};
            byte[] total_length = new byte[4];
            if (mOTAData != null) {
                int length = mOTAData.length;
                total_length[3] = (byte) ((length & 0xFF000000) >> 24);
                total_length[2] = (byte) ((length & 0x00FF0000) >> 16);
                total_length[1] = (byte) ((length & 0x0000FF00) >> 8);
                total_length[0] = (byte) ((length & 0x000000FF));
                for (int i = 0; i < total_length.length; i++) {
                    ret[i + 2] = total_length[i];
                }
                ret[ret.length - OTA_PACKET_CS_LENGTH] = CalculateCheckWord(ret);
            } else {
                Log.i(TAG, "mOTAData is null");
            }
            Log.i(TAG, "Leave getOTAStartCommandData");
            return ret;
        }

        public byte[] getOTAStopCommandData(byte error_code) {
            Log.i(TAG, "Enter getOTAStopCommandData");
            byte[] ret = {(byte) 0xF3, 0x01, 0x00, 0x00};
            ret[2] = error_code;
            ret[ret.length - OTA_PACKET_CS_LENGTH] = CalculateCheckWord(ret);
            Log.i(TAG, "Leave getOTAStopCommandData");
            return ret;
        }

        public byte[] getOTATransferData(int index) {
            Log.i(TAG, "Enter getOTATransferData # packetCount=" + packetCount + " # index=" + index);
            if (ota_mtu != 0 && mOTAData != null) {
                byte[] ret = null;
                if (index == (packetCount - 1)) {
                    if ((mOTAData.length % (ota_mtu - OTA_PACKET_HEADER_LENGTH)) == 0) {
                        ret = new byte[ota_mtu];
                        System.arraycopy(mOTAData, index * (ota_mtu - OTA_PACKET_HEADER_LENGTH), ret, (OTA_PACKET_HEADER_LENGTH - OTA_PACKET_CS_LENGTH), (ota_mtu - OTA_PACKET_HEADER_LENGTH));
                    } else {
                        int packet_size = (mOTAData.length % (ota_mtu - OTA_PACKET_HEADER_LENGTH));
                        ret = new byte[packet_size + OTA_PACKET_HEADER_LENGTH];
                        System.arraycopy(mOTAData, index * (ota_mtu - OTA_PACKET_HEADER_LENGTH), ret, (OTA_PACKET_HEADER_LENGTH - OTA_PACKET_CS_LENGTH), packet_size);
                    }
                } else {
                    ret = new byte[ota_mtu];
                    System.arraycopy(mOTAData, index * (ota_mtu - OTA_PACKET_HEADER_LENGTH), ret, (OTA_PACKET_HEADER_LENGTH - OTA_PACKET_CS_LENGTH), (ota_mtu - OTA_PACKET_HEADER_LENGTH));
                }
                ret[0] = (byte) 0xF2;
                ret[2] = (byte) ((index & 0x0000FF00) >> 8);
                ret[1] = (byte) (index & 0x000000FF);

                ret[ret.length - OTA_PACKET_CS_LENGTH] = CalculateCheckWord(ret);
                Log.i(TAG, "Leave getOTATransferData");
                return ret;
            }
            Log.i(TAG, "Leave getOTATransferData, null");
            return null;
        }

        public byte[] getOTACompleteData() {
            Log.i(TAG, "Enter getOTACompleteData");
            byte[] ret = {(byte) 0xF3, 0x01, 0x00, 0x00};
            ret[ret.length - OTA_PACKET_CS_LENGTH] = CalculateCheckWord(ret);
            Log.i(TAG, "Leave getOTACompleteData");
            return ret;
        }

        /**
         * 关闭线程
         */
        public synchronized void closeThread() {
            Log.i(TAG, "closeThread");
            try {
                //notify();
                int iSize = loopList.size();
                if (iSize > 0) {
                    OTAData dataEvent = loopList.get(iSize - 1);
                    if (dataEvent.status == OTAData.COMMAND_STATUS_READY) {
                        if (dataEvent.write_wait != null) {
                            try {
                                synchronized (dataEvent.write_wait) {
                                    dataEvent.write_wait.notify();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (dataEvent.command_wait != null) {
                            try {
                                synchronized (dataEvent.command_wait) {
                                    dataEvent.command_wait.notify();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (dataEvent.status == OTAData.COMMAND_STATUS_SENT) {
                        if (dataEvent.command_wait != null) {
                            try {
                                synchronized (dataEvent.command_wait) {
                                    dataEvent.command_wait.notify();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                setClose(true);
                //interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public synchronized void setCurrentIndex(int cur) {
            Log.i(TAG, "setCurrentIndex cur:" + cur);
            if (cur >= this.currentIndex) {
                this.currentIndex = cur;
            } else {
                Log.i(TAG, "currentIndex error=" + Integer.toString(cur));
            }
        }

        public boolean isClose() {
            return isClose;
        }

        public void setClose(boolean isClose) {
            this.isClose = isClose;
        }

        public void run() {
            try {
                Thread.sleep(300); //Sleep 100 to wait wirte descriptor finished.
            } catch (Exception e) {
                e.printStackTrace();
            }
            while (!isClose && !isInterrupted()) {
                if (!bInit) {
                    //1. 开始下载固件
                    if (sendOTAStartCommond()) {
                        if (isClose) {
                            break;
                        }
                        if (ota_credit != 0 && ota_mtu != 0) {
                            if ((mOTAData.length % (ota_mtu - OTA_PACKET_HEADER_LENGTH)) == 0) {
                                packetCount = (mOTAData.length / (ota_mtu - OTA_PACKET_HEADER_LENGTH));
                            } else {
                                packetCount = (mOTAData.length / (ota_mtu - OTA_PACKET_HEADER_LENGTH)) + 1;
                            }
                            bInit = true;
                            onOTATransfer(0);
                        } else {
                            onOTAError(MSG_OTA_START_COMMAND_DATA_INVALID);
                            break;
                        }
                    } else {
                        onOTAError(MSG_OTA_START_COMMAND_SEND_FAILED);
                        break;
                    }
                } else {
                    if (packetIndex < packetCount) {
                        if (packetIndex == (packetCount - 1)) {
                        }
                        if (!sendOTATransferCommand(packetIndex)) {
                            Log.i(TAG, "sendOTATransferCommand error");
                            onOTAError(MSG_OTA_DATA_COMMAND_SEND_FAILED);
                            break;
                        }
                        if (isClose) {
                            Log.i(TAG, "OTAThread is close");
                            break;
                        }
                        currentIndex = packetIndex;
                        packetIndex++;
                        if (packetIndex != packetCount) {
                            if (currentIndex != (packetIndex - 1)) {
                                if (currentIndex < (packetIndex - 1)) {
                                    packetIndex = currentIndex + 1;
                                } else if (currentIndex > (packetIndex - 1)) {
                                    Log.i(TAG, "return index over send");
                                }
                            } else {
                                Message msg = new Message();
                                progressStatus = MAX_PROGRESS * packetIndex / packetCount;
                                onOTATransfer(progressStatus);
                            }
                        }
                        if (packetIndex >= packetCount) {
                            progressStatus = MAX_PROGRESS * packetIndex / packetCount;
                        }
                    }
                    if (progressStatus >= MAX_PROGRESS) {
                        onOTATransfer(MAX_PROGRESS);
                        break;
                    }
                }
            }
            Log.i(TAG, "OTAThread quit");
            loopList.clear();
            mOTAThread = null;
            //mIBridgedevice = null;
        }

        public boolean sendOTAStartCommond() {
            boolean ret = false;
            Log.i(TAG, "++++++++ Enter sendOTAStartCommond");
            //1. Add StartOTA command
            OTAData startEvent = new OTAData();
            startEvent.type = OTAData.COMMAND_TYPE_START_OTA;
            startEvent.status = OTAData.COMMAND_STATUS_READY;
            startEvent.data = getOTAStartCommandData();
            if (startEvent.data != null && mOTACharacteristic != null) {
                startEvent.write_wait = new Object();
                loopList.add(startEvent);
                if (mOTACharacteristic.setValue(startEvent.data)) {
                    if (writeCharacteristic(mOTACharacteristic)) {
                        if (startEvent.status == OTAData.COMMAND_STATUS_READY) {
                            startEvent.write_wait = new Object();
                            onWriteWait(0);
                        } else {
                            Log.i(TAG, "Already receive cmd write succeed msg");
                        }
                        if (startEvent.status == OTAData.COMMAND_STATUS_SENT) {
                            startEvent.command_wait = new Object(); //for wait command
                            onCommandWait(0, false);
                        } else {
                            Log.i(TAG, "Already receive cmd event");
                        }
                        //startEvent.status = OTAData.COMMAND_STATUS_SENT;
                        ret = true;
                    } else {
                        Log.i(TAG, "writeCharacteristic failed");
                    }
                } else {
                    Log.i(TAG, "mOTACharacteristic.setValue  failed");
                }
            } else {
                Log.i(TAG, "data is null or mOTACharacteristic is null");
            }
            Log.i(TAG, "++++++++ Leave sendOTAStartCommond");
            return ret;
        }

        public boolean sendOTATransferCommand(int index) {
            boolean ret = false;
            boolean bACKExist = false;
            OTAData dataEvent = new OTAData();
            dataEvent.type = OTAData.COMMAND_TYPE_OTA_DATA;
            dataEvent.status = OTAData.COMMAND_STATUS_READY;
            if (index == (packetCount - 1)) {
                Log.i(TAG, "current is last packet");
            }
            // Log.i(TAG, "sendOTATransferCommand index=" + index);
            dataEvent.data = getOTATransferData(index);
            //Log.i(TAG, bytesToHexString(dataEvent.data));
            if (dataEvent.data != null && mOTACharacteristic != null) {
                if (((index + 1) % ota_credit == 0) || (index == (packetCount - 1))) {
                    bACKExist = true;
                }
                loopList.add(dataEvent);
                if (mOTACharacteristic != null && mOTACharacteristic.setValue(dataEvent.data)) {
                    //mOTAWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    if (index == (packetCount - 1)) {
                        Log.i(TAG, bytesToHexString(dataEvent.data));
                    }
                    if (writeCharacteristic(mOTACharacteristic)) {
                        if (dataEvent.status == OTAData.COMMAND_STATUS_READY) {
                            dataEvent.write_wait = new Object();
                            onWriteWait(index + 1);
                        } else {
                            Log.i(TAG, "Already receive cmd write succeed msg");
                        }
                        if (bACKExist && dataEvent.status == OTAData.COMMAND_STATUS_SENT) {
                            dataEvent.command_wait = new Object();
                            onCommandWait(index + 1, false);
                        } else {
                            if (bACKExist) {
                                Log.i(TAG, "Already receive cmd event");
                            }
                        }
                        ret = true;
                    }
                }
            }
            return ret;
        }

        public boolean sendOTACompleteCommand() {
            boolean ret = false;
            byte[] data = getOTACompleteData();
            if (data != null && mOTACharacteristic != null) {
                if (mOTACharacteristic.setValue(data)) {
                    ret = writeCharacteristic(mOTACharacteristic);
                }
            }
            return ret;
        }
    }
}
