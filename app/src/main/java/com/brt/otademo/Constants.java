package com.brt.otademo;

import com.brt.otademo.ble.BluetoothIBridgeOTA;

/**
 * Created by DELL on 2018/5/3.
 */

public class Constants {
    //0000FF00-0000-1000-8000-00805F9B34FB
    public static final String SERVICE_OTA = "0000FF80-0000-1000-8000-00805f9b34fb";
    public static final String CHARACTERISTIC_NOTIFY_WRITE = "0000FF81-0000-1000-8000-00805f9b34fb";
    public static final String CHARACTERISTIC_WRITE = "0000FF82-0000-1000-8000-00805f9b34fb";
    public static final String DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = "00002902-0000-1000-8000-00805f9b34fb";//0x2902

    public static final String SERVICE_SPECIFIC = "000018f0-0000-1000-8000-00805f9b34fb";

}
