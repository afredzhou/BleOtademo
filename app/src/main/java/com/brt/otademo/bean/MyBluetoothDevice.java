package com.brt.otademo.bean;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Created by DELL on 2018/3/21.
 */

public class MyBluetoothDevice implements Parcelable{
    public static final UUID SPPUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public BluetoothDevice mBluetoothDevice;
    public String mDeviceAddress;
    public String mDeviceName;
    private byte[] mScanRecord;
    public String uuid;
    public int mRssi;
    private int mDeviceType;
    private float distance;//距离
    private int portraitId;//头像id
    private boolean mIsConnected;
    private int mChipType;  // 1->8041   2->8051
    private List<BluetoothGattService> mGattServices;
    private static List<MyBluetoothDevice> mList = new ArrayList<>();
    private ConnectStatus mConnectStatus = ConnectStatus.STATUS_DISCONNECTED;
    private BondStatus mBondStatus = BondStatus.STATE_BONDNONE;

    private static final int PROTOCOL_OFFSET = 3;
    private static final int AD_LENGTH_INDEX = PROTOCOL_OFFSET;         //3
    private static final int AD_TYPE_INDEX = 1 + PROTOCOL_OFFSET;       //4
    private static final int BEACON_CODE_INDEX = 4 + PROTOCOL_OFFSET;   //7
    private static final int UUID_START_INDEX = 6 + PROTOCOL_OFFSET;    //9
    private static final int UUID_STOP_INDEX  = UUID_START_INDEX + 15;  //24
    private static final int ARGS_START_INDEX = UUID_STOP_INDEX + 1;    //25
    private static final int TXPOWER_INDEX = ARGS_START_INDEX + 4;      //29


    private static final int AD_LENGTH_VALUE = 0x1a;
    private static final int AD_TYPE_VALUE = 0xff;
    private static final int BEACON_CODE_VALUE = 0x0215;

    public static int DEVICE_TYPE_BLE = 1;

    public static final int PAIRING_VARIANT_PIN = 0;
    public static final int PAIRING_VARIANT_PASSKEY = 1;
    public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
    public static final int PAIRING_VARIANT_CONSENT = 3;
    public static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;
    public static final int PAIRING_VARIANT_DISPLAY_PIN = 5;
    public static final int PAIRING_VARIANT_OOB_CONSENT = 6;


    public byte[] buffer;
    public int length;

    public enum BondStatus {
        STATE_BONDED, STATE_BONDING, STATE_BONDNONE, STATE_BONDFAILED, STATE_BOND_OVERTIME, STATE_BOND_CANCLED
    }

    public MyBluetoothDevice(BluetoothDevice device) {
        mDeviceAddress = device.getAddress();
        mBluetoothDevice = device;

        mDeviceName = mBluetoothDevice.getName();
        mScanRecord = null;
        mRssi = 0;

        mChipType = 0;
    }

    protected MyBluetoothDevice(Parcel in) {
        mBluetoothDevice = in.readParcelable(BluetoothDevice.class.getClassLoader());
        mDeviceAddress = in.readString();
        mDeviceName = in.readString();
        mScanRecord = in.createByteArray();
        uuid = in.readString();
        mRssi = in.readInt();
        mChipType = in.readInt();
        mIsConnected = in.readByte() != 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mGattServices = in.createTypedArrayList(BluetoothGattService.CREATOR);
        }
    }

    public static final Creator<MyBluetoothDevice> CREATOR = new Creator<MyBluetoothDevice>() {
        @Override
        public MyBluetoothDevice createFromParcel(Parcel in) {
            return new MyBluetoothDevice(in);
        }

        @Override
        public MyBluetoothDevice[] newArray(int size) {
            return new MyBluetoothDevice[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mBluetoothDevice, flags);
        dest.writeString(mDeviceAddress);
        dest.writeString(mDeviceName);
        dest.writeByteArray(mScanRecord);
        dest.writeString(uuid);
        dest.writeInt(mRssi);
        dest.writeInt(mChipType);
        dest.writeInt(mIsConnected ? 1 : 0);
        //dest.writeTypedList(mGattServices);
    }


    public enum ConnectStatus {
        STATUS_DISCONNECTED, STATUS_CONNECTED, STATUS_DISCONNECTTING, STATUS_CONNECTTING, STATUS_CONNECTFAILED, STATE_BONDED, STATE_BONDING, STATE_BONDNONE
    }

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    public void setDeviceAddress(String mDeviceAddress) {
        this.mDeviceAddress = mDeviceAddress;
    }

    public String getmDeviceName() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothDevice = adapter.getRemoteDevice(mDeviceAddress);
        mDeviceName = mBluetoothDevice.getName();
        return mDeviceName;
    }

    public void setmDeviceName(String mDeviceName) {
        this.mDeviceName = mDeviceName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public int getRssi() {
        return mRssi;
    }

    public int getmChipType() {
        return mChipType;
    }

    public void setmChipType(int mChipType){
        this.mChipType = mChipType;
    }

    public void setRssi(int rssi) {
        this.mRssi = rssi;
    }

    public int getDeviceType() {
        return this.mDeviceType;
    }

    public void setDeviceType(int deviceType) {
        this.mDeviceType = deviceType;
    }

    public boolean isConnected() {
        return this.mIsConnected;
    }

    public List<BluetoothGattService> getmGattServices() {
        return mGattServices;
    }


    public void setmGattServices(List<BluetoothGattService> mGattServices) {
        this.mGattServices = mGattServices;
    }

    public byte[] getScanRecord() {
        return mScanRecord;
    }

    public void setScanRecord(byte[] scanRecord) {
        mScanRecord = scanRecord;
    }

    public static MyBluetoothDevice createDevice(BluetoothDevice device) {
        if (null != device) {
            Iterator<MyBluetoothDevice> it = mList.iterator();
            while (it.hasNext()) {
                MyBluetoothDevice idev = it.next();
                if (idev != null && idev.isSameDevice(device)) {
                    return idev;
                }
            }
            MyBluetoothDevice newDev = new MyBluetoothDevice(device);
            mList.add(newDev);
            return newDev;
        }
        return null;
    }

    public static MyBluetoothDevice createDevice(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            return createDevice(adapter.getRemoteDevice(address));
        }
        return null;
    }

    public String toString() {
        String name = ((mDeviceName == null) ? "Device" : mDeviceName);
        String address = ((mDeviceAddress == null) ? "00:00:00:00:00:00"
                : mDeviceAddress);
        return super.toString() + " [" + name + " - " + address + "]";
    }

    public boolean isSameDevice(BluetoothDevice device) {
        String addr = ((mDeviceAddress == null) ? "00:00:00:00:00:00"
                : mDeviceAddress);
        String another = ((device.getAddress() == null) ? "00:00:00:00:00:00"
                : device.getAddress());
        return addr.equals(another);
    }

    public boolean isBonded() {
        if (mBluetoothDevice != null) {
            return mBluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED;
        }
        return false;
    }

    public static boolean isBeacon(final byte[] data) {
        if (data == null) {
            return false;
        }
        if ((data[AD_LENGTH_INDEX] & 0xff) != AD_LENGTH_VALUE) return false;

        if ((data[AD_TYPE_INDEX] & 0xff) != AD_TYPE_VALUE) return false;

        final int code = ((data[BEACON_CODE_INDEX] << 8) & 0x0000ff00) | ((data[BEACON_CODE_INDEX + 1]) & 0x000000ff);
        if(code != BEACON_CODE_VALUE) return false;

        return true;
    }

    public void setConnectStatus(ConnectStatus d) {
        mConnectStatus = d;
        mIsConnected = (ConnectStatus.STATUS_CONNECTED == d);
        Log.i("MyBluetoothDevice", "setConnectStatus  # mIsConnected = "  + mIsConnected);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof MyBluetoothDevice)) {
            return false;
        }
        String addr = ((mDeviceAddress == null) ? "00:00:00:00:00:00" : mDeviceAddress);
        MyBluetoothDevice dev = (MyBluetoothDevice) o;
        String anotherAddr = ((dev.mDeviceAddress == null) ? "00:00:00:00:00:00" : dev.mDeviceAddress);
        return addr.equals(anotherAddr);

    }

    /**
     * 根据rssi 获得距离
     */
    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public int getPortraitId() {
        return portraitId;
    }

    public void setPortraitId(int portraitId) {
        this.portraitId = portraitId;
    }

    public boolean isValidDevice() {
        return true;
    }

    ConnectStatus getConnectStatus() {
        return mConnectStatus;
    }

    public void setBondStatus() {
        if (mBluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            mBondStatus = BondStatus.STATE_BONDED;
        }
        if (mBluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
            mBondStatus = BondStatus.STATE_BONDING;
        }
        if (mBluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE) {
            mBondStatus = BondStatus.STATE_BONDNONE;
        }
    }

    public void setBondStatus(BondStatus d) {
        mBondStatus = d;
    }

    public BondStatus getBondStatus() {
        return mBondStatus;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setPin(byte[] pin) {
        try {
            Class c = Class.forName(mBluetoothDevice.getClass().getName());
            Method setPin = c.getMethod("setPin", byte[].class);
            setPin.setAccessible(true);
            setPin.invoke(mBluetoothDevice, pin);
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void setPairingConfirmation(boolean confirm) {
        try {
            Class c = Class.forName(mBluetoothDevice.getClass().getName());
            Method setPairingConfirmation = c.getMethod("setPairingConfirmation", boolean.class);
            setPairingConfirmation.setAccessible(true);
            setPairingConfirmation.invoke(mBluetoothDevice, confirm);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
