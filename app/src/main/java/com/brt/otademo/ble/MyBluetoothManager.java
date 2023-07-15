package com.brt.otademo.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.brt.otademo.Constants;
import com.brt.otademo.bean.MyBluetoothDevice;
import com.brt.otademo.ble.BleBaseAdapter.DataReceiver;
import com.brt.otademo.ble.BleBaseAdapter.MyHandler;
import com.brt.otademo.utils.MyUtil;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static java.lang.Thread.interrupted;
import static java.lang.Thread.sleep;


public class MyBluetoothManager {
    private static final String TAG = "MyBluetoothManager";
    private MyHandler mHandler;
    private static BluetoothManager mBluetoothManager;
    private ArrayList<DataReceiver> mDataReceivers;
    private ArrayList<ConnectionList.GattConnection> mGattConnections;
    private static ConnectionList mList;
    public static int rssiVal;
    private byte[] buffer;
    private  InputStream mmInStream;
    private  OutputStream mmOutStream;

    private static boolean is8051 = false;   //是否是8051芯片

    private static Context mContext;

    MyBluetoothManager(Context context, MyHandler handler) {
        this.mContext = context;
        this.mHandler = handler;
        mList = new ConnectionList();
        mList.clear();
        mGattConnections = new ArrayList<>();

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.i(TAG, "No have bluetoothManager");
            }
        }

    }

    void registerDataReceiver(DataReceiver dataReceiver) {
        if (mDataReceivers == null) {
            mDataReceivers = new ArrayList<>();
        }
        if (!mDataReceivers.contains(dataReceiver)) {
            mDataReceivers.add(dataReceiver);
        }
        for (ConnectionList.GattConnection gattConnection : mGattConnections){
            gattConnection.registerDataReceiver(dataReceiver);
        }
    }

    void unregisterDataReceiver(DataReceiver dataReceiver) {
        if (mDataReceivers == null) {
            return;
        }
        mDataReceivers.remove(dataReceiver);
        for (ConnectionList.GattConnection gattConnection : mGattConnections){
            gattConnection.unregisterDataReceiver(dataReceiver);
        }
    }

    void destroy() {
        mList.releaseAllConnections();
        mHandler = null;
        mDataReceivers = null;
    }

    boolean write(MyBluetoothDevice device, byte[] buffer, int cmdType) {
        Log.i(TAG, "write");
        return mList.writeCmd(device, buffer, cmdType);
    }

    void connect(MyBluetoothDevice device) {
        Log.i(TAG, "connect");
        if (device != null && !device.isConnected()) {
            device.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_CONNECTTING);
            ConnectionList.GattConnection foundExistConn = mList.foundDevice(device);
            if (foundExistConn != null) {
                foundExistConn.close();
            }
            if (mGattConnections != null){
                mGattConnections.add(new ConnectionList.GattConnection(mContext, device, mHandler, mDataReceivers));
            }
        } else {
            Log.i(TAG, "# connect  # device = " + device + " # or isConnected is true");
        }
    }

    void disconnect(MyBluetoothDevice device) {
        ConnectionList.GattConnection found = mList.foundDevice(device);
        Log.i("IBridgeGatt", "try to release gatt connection:" + found);
        if (found == null) {
            Log.e("IBridgeGatt", "The gatt device[" + device
                    + "] may has been closed.");
            return;
        }
        found.disconnect();
    }

    public int getRssiValue() {
        Log.i(TAG, "# getRssiValue # rssiVal = " + rssiVal);
        return rssiVal;
    }

    public static class ConnectionList {

        private List<GattConnection> mConnectedDevices = new ArrayList<>();
        private final byte[] LOCK = new byte[0];

        public boolean writeCmd(MyBluetoothDevice device, byte[] buffer, int cmdType) {
            boolean b = false;
            if (null == device || null == buffer)
                return b;
            GattConnection found = foundDevice(device);
            if (null != found) {
                b = found.write(buffer, cmdType);
            }
            return b;
        }

        private GattConnection foundDevice(MyBluetoothDevice device) {
            GattConnection found = null;
            synchronized (LOCK) {
                for (GattConnection ds : mConnectedDevices) {
                    Log.i(TAG, " # foundDevice # ds.getDevice = " + ds.getDevice());
                    Log.i(TAG, " # foundDevice # device = " + device);
                    if (device.equals(ds.getDevice())) {
                        found = ds;
                        break;
                    }
                }
            }
            return found;
        }

        public void clear() {
            synchronized (LOCK) {
                mConnectedDevices.clear();
            }
        }

        public void releaseAllConnections() {
            synchronized (LOCK) {
                for (final GattConnection ds : mConnectedDevices) {
                    if (ds != null) {
                        ds.disconnect();
                        ds.close();
                    }
                }
                mConnectedDevices.clear();
            }
        }

        public void addConnection(GattConnection connection) {
            GattConnection gattConnection = foundDevice(connection.getDevice());
            if (gattConnection != null) {
                synchronized (LOCK) {
                    Log.i(TAG, "GATT connection already exist");
                    mConnectedDevices.remove(gattConnection);
                }
            }
            synchronized (LOCK) {
                mConnectedDevices.add(connection);
            }
        }

        private void removeConnection(GattConnection connection) {
            GattConnection found = foundDevice(connection.getDevice());
            if (found != null) {
                synchronized (LOCK) {
                    mConnectedDevices.remove(found);
                }
            }
        }

        static class GattConnection {

            private GattConnection mGattConnection;
            MyBluetoothDevice mmDevice;
            private BluetoothAdapter mBluetoothAdapter;
            private BluetoothGatt mBluetoothGatt = null;
            private MyHandler mHandler;
            private ArrayList<DataReceiver> mDataReceivers;
            BluetoothGattService otaService;
            BluetoothGattService otaInfoService;
            BluetoothGattCharacteristic nOrWcharacteristic;
            BluetoothGattCharacteristic notifycharacteristic;
            BluetoothGattCharacteristic writeCharacteristic;
            BluetoothDevice dev;
            Context mContext;
            Bundle bundle;

            GattConnection(Context context, MyBluetoothDevice device,
                           MyHandler handler, ArrayList<DataReceiver> dataReceivers) {
                mGattConnection = this;
                mHandler = handler;
                mDataReceivers = dataReceivers;
                mBluetoothAdapter = mBluetoothManager.getAdapter();//获取BluetoothAdapter
                mmDevice = device;
                dev = mBluetoothAdapter.getRemoteDevice(mmDevice.getDeviceAddress());
                this.mContext = context;
                connectGatt(mContext);
            }

            public void registerDataReceiver(DataReceiver dataReceiver){
                if (mDataReceivers == null){
                    mDataReceivers = new ArrayList<>();
                }
                if (!mDataReceivers.contains(dataReceiver)){
                    mDataReceivers.add(dataReceiver);
                }
            }
            public void unregisterDataReceiver(DataReceiver dataReceiver){
                if (mDataReceivers.contains(dataReceiver)){
                    mDataReceivers.remove(dataReceiver);
                }
            }

            private void connectGatt(Context context) {
                if (Build.VERSION.SDK_INT < 21) {
                    mBluetoothGatt = dev.connectGatt(context, false, mGattCallback);
                    Log.i(TAG, "# GattConnection # SDK_INT < 21 # dev.connectGatt");
                } else if(Build.VERSION.SDK_INT >= 23) {
                    mBluetoothGatt = dev.connectGatt(context, false, mGattCallback, TRANSPORT_LE);
                } else {
                    Class<? extends BluetoothDevice> cls = BluetoothDevice.class;
                    try {
                        Method m = cls.getMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);
                        if (m != null) {
                            try {
                                mBluetoothGatt = (BluetoothGatt) m.invoke(dev, context, false, mGattCallback, 2);
                                Log.i(TAG, "# GattConnection # SDK_INT >= 21 # dev.connectGatt");
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            }

            private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    Log.i(TAG, "# onConnectionStateChange # state = " + status + " # newState = " + newState + " # gatt = " + gatt);
                    switch (status) {
                        case BluetoothGatt.GATT_SUCCESS:
                            switch (newState) {
                                case BluetoothProfile.STATE_CONNECTED:
                                    Log.i(TAG, "# onConnectionStateChange  # STATE_CONNECTED");
                                    mList.addConnection(mGattConnection);
                                    mmDevice.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_CONNECTED);

                                    is8051 = false;
                                    discoveryServices();// 查找服务
                                    if (mHandler != null) {
                                        Message msg = mHandler.obtainMessage(BleBaseAdapter.MESSAGE_DEVICE_CONNECTED);
                                        msg.obj = mmDevice;
                                        mHandler.sendMessage(msg);
                                    }
                                    return;
                                case BluetoothProfile.STATE_DISCONNECTED:
                                    Log.i(TAG, "# onConnectionStateChange  # STATE_DISCONNECTED");
                                    if (mList.foundDevice(mmDevice) != null) {
                                        if (isGattConnected()) {
                                            if (mBluetoothGatt != null) {
                                                mBluetoothGatt.disconnect();
                                            }
                                        }
                                    }
                                    mList.removeConnection(mGattConnection);
                                    mmDevice.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_DISCONNECTED);
                                    if (mHandler != null) {
                                        Message msg = mHandler.obtainMessage(BleBaseAdapter.MESSAGE_DEVICE_DISCONNECTED);
                                        msg.obj = mmDevice;
                                        mHandler.sendMessage(msg);
                                    }
                                    close();
                                    return;
                                default:
                                    mmDevice.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_DISCONNECTED);
                                    disconnect();
                                    close();
                                    return;
                            }
                        default:
                            close();
                            mmDevice.setConnectStatus(MyBluetoothDevice.ConnectStatus.STATUS_DISCONNECTED);
                            if (status == 133) {
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mmDevice != null) {
                                            new ConnectionList.GattConnection(mContext, mmDevice, mHandler, mDataReceivers);
                                        }
                                    }
                                }, 3000);
                            }
                            return;
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    if (gatt.equals(mBluetoothGatt) && status == BluetoothGatt.GATT_SUCCESS) {
                        onServicesFound(getSupportedGattServices());

                        Log.i(TAG, "# onServicesDiscovered  # status = " + status);
                    } else {
                        Log.i(TAG, "onGattServicesDiscoveredFailed status" + status);
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicWrite(gatt, characteristic, status);
                    Log.i(TAG, "# onCharacteristicWrite status = " + status);
                    if (status == 0 &&  mHandler != null) {
                        Message msg = mHandler.obtainMessage(BleBaseAdapter.MESSAGE_LE_WRITE_SUCCEED);
                        msg.obj = mmDevice;
                        mHandler.sendMessage(msg);
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    Log.i(TAG, "onCharacteristicChanged = " + MyUtil.bytesToHexString(characteristic.getValue()));
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0){
                        if (mDataReceivers != null){
                            for (DataReceiver er : mDataReceivers){
                                er.onDataReceived(mmDevice, data, data.length);
                            }
                        }
                    }
                }

                @Override
                public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorRead(gatt, descriptor, status);
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorWrite(gatt, descriptor, status);
                    Log.i(TAG, "# onDescriptorWrite status = " + status);

                    if (mHandler != null) {
                        Message msg = mHandler.obtainMessage(BleBaseAdapter.MESSAGE_LE_BLE_READY);
                        msg.obj = mmDevice;
                        mHandler.sendMessage(msg);
                    }
                }

                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                    super.onReadRemoteRssi(gatt, rssi, status);
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.readRemoteRssi();
                    }
                    Log.i(TAG, "# onReadRemoteRssi # rssi = " + rssi);
                    if (rssi <= 0) {
                        rssiVal = rssi;
                    }
                }
            };

            private List<BluetoothGattService> getSupportedGattServices() {
                Log.i(TAG, " getSupportedGattServices");
                List<BluetoothGattService> gattServices;
                if (mBluetoothGatt != null) {
                    gattServices = mBluetoothGatt.getServices();
                    Log.i(TAG, "# getSupportedGattServices  # gattServices = " + gattServices);
                } else {
                    gattServices = null;
                    Log.i(TAG, "# getSupportedGattServices # return null");
                }
                return gattServices;
            }

            void discoveryServices() {
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.discoverServices();
                    Log.i(TAG, "discoveryServices");
                }
            }

            void onWriteNotify(List<BluetoothGattCharacteristic> gattCharacteristics){
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String  characteristicUUID = gattCharacteristic.getUuid().toString();
                    Log.i(TAG, "characteristicUUID = " + characteristicUUID);
                    if (!characteristicUUID.isEmpty() && characteristicUUID.equalsIgnoreCase(Constants.CHARACTERISTIC_NOTIFY_WRITE)) {
                        nOrWcharacteristic = gattCharacteristic;
                        //notifycharacteristic = gattCharacteristic;
                        Log.i(TAG, "# onServicesFound  # notifycharacteristic = " + nOrWcharacteristic);
                        setCharacteristicNotify(nOrWcharacteristic, true);
                    }
                    if (!characteristicUUID.isEmpty() && characteristicUUID.equalsIgnoreCase(Constants.CHARACTERISTIC_WRITE)) {
                        writeCharacteristic = gattCharacteristic;
                    }
                }
            }

            void onServicesFound(List<BluetoothGattService> gattServices) {
                Log.i(TAG, " onServicesFound in ...");
                String serviceUUID = null;
                String otaServiceUUID = null;
                boolean bFindSvc = false;

                for (BluetoothGattService gattService : gattServices) {
                    serviceUUID = gattService.getUuid().toString();
                    if (!serviceUUID.isEmpty() && serviceUUID.equalsIgnoreCase(Constants.SERVICE_SPECIFIC)){
                        is8051 = true;
                        break;
                    }
                }
                if (is8051){
                    for (BluetoothGattService gattService : gattServices){
                        serviceUUID = gattService.getUuid().toString();
                        Log.i(TAG, "serviceUUID = "+ serviceUUID);
                        if (!serviceUUID.isEmpty() && serviceUUID.equalsIgnoreCase(Constants.SERVICE_OTA)) {
                            onWriteNotify(gattService.getCharacteristics());
                        }else if (!serviceUUID.isEmpty() && serviceUUID.equalsIgnoreCase(BluetoothIBridgeOTA.SERVICE_OTA)) {
                            bFindSvc = true;
                            otaServiceUUID = gattService.getUuid().toString();
                        }
                    }
                }else {
                    for (BluetoothGattService gattService : gattServices) {
                        Log.d(TAG, "Services ID = " + gattService.getUuid().toString());
                        serviceUUID = gattService.getUuid().toString();
                        if (!serviceUUID.isEmpty() && serviceUUID.equalsIgnoreCase(Constants.SERVICE_OTA)) {
                            otaServiceUUID = gattService.getUuid().toString();
                            onWriteNotify(gattService.getCharacteristics());
                            bFindSvc = true;
                        } else if (!serviceUUID.isEmpty() && serviceUUID.equalsIgnoreCase(BluetoothIBridgeOTA.SERVICE_OTA)) {
                            otaServiceUUID = gattService.getUuid().toString();
                            bFindSvc = true;
                            break;
                        }

                    }
                }

                if (bFindSvc && !TextUtils.isEmpty(otaServiceUUID)) {
                    Log.d(TAG, "otaServiceUUID = " + otaServiceUUID);
                    bundle = new Bundle();
                    bundle.putString("serviceUUID", otaServiceUUID);
                    if (mHandler != null) {
                        Message msg = mHandler.obtainMessage(BleBaseAdapter.MESSAGE_LE_SERVICES_DISCOVERED);
                        Log.i(TAG, "#onServicesFound # mmDevice = " + mmDevice);
                        if (is8051){
                            mmDevice.setmChipType(2);
                        }else {
                            mmDevice.setmChipType(1);
                        }
                        mmDevice.setmGattServices(gattServices);
                        msg.obj = mmDevice;
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                    }
                }
            }

            MyBluetoothDevice getDevice() {
                Log.i(TAG, "getDevice # mmDevice = " + mmDevice);
                return mmDevice;
            }

            void disconnect() {
                Log.i(TAG, "disconnect mBluetoothGatt" + mBluetoothGatt);
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                }
                Log.i(TAG, "disconnect...");
            }

            void close() {
                if (mBluetoothGatt != null) {
                    Log.i(TAG, "close()...");
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
            }

            private boolean isGattConnected() {
                Log.i(TAG, "isGattConnected");
                boolean isConnected = false;
                if (Build.VERSION.SDK_INT >= 18) {
                    BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (bluetoothManager != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        int state = bluetoothManager.getConnectionState(mmDevice.mBluetoothDevice, BluetoothGatt.GATT);
                        if (state == BluetoothGatt.STATE_CONNECTED) {
                            isConnected = true;
                            Log.i(TAG, "# isGattConnected  # current is connected");
                        } else {
                            Log.i(TAG, "# isGattConnected  # state = " + state);
                        }
                    }
                }
                Log.i(TAG, "# isGattConnected # isConnected = " + isConnected);
                return isConnected;
            }

            private void setCharacteristicNotify(BluetoothGattCharacteristic characteristic, boolean enable) {
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
                    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(Constants.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    }
                    mBluetoothGatt.writeDescriptor(descriptor);
                }
            }

            boolean write(byte[] bytes, int cmdType) {
                boolean result = false;

                if (writeCharacteristic != null) {
                    writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    writeCharacteristic.setValue(bytes);
                    if (writeCharacteristic(writeCharacteristic)) {
                        result = true;
                        Log.i(TAG, "# write writeCharacteristic success");
                    }
                } else {
                    Log.i(TAG, "# write writeCharacteristic is null");
                }

                    /*
                    switch (cmdType) {
                        case 0:// OTA
                            if (otaCharacteristic != null) {
                                otaCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                otaCharacteristic.setValue(bytes);
                                if (writeCharacteristic(otaCharacteristic)) {
                                    result = true;
                                    Log.i(TAG, "# write otaCharacteristic success");
                                }
                            } else {
                                Log.i(TAG, "# write otaCharacteristic is null");
                            }
                            break;
                        case 1:// OTA_INFO
                            if (writeCharacteristic != null) {
                                writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                writeCharacteristic.setValue(bytes);
                                if (writeCharacteristic(writeCharacteristic)) {
                                    result = true;
                                    Log.i(TAG, "# write otaInfoCharacteristic success");
                                }
                            } else {
                                Log.i(TAG, "# write otaInfoCharacteristic is null");
                            }
                            break;
                        default:
                            break;
                    }
                } else {
                    Log.i(TAG, "# write # data is null");
                    result = false;
                }
                */
                return result;
            }

            boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
                boolean flag = false;
                if (mBluetoothGatt != null) {
                    flag = mBluetoothGatt.writeCharacteristic(characteristic);
                }
                return flag;
            }
        }
    }

}
