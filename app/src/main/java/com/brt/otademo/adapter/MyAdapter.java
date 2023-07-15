package com.brt.otademo.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.brt.otademo.R;
import com.brt.otademo.bean.MyBluetoothDevice;

import java.util.ArrayList;

/**
 * Created by DELL on 2018/5/3.
 */

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> implements View.OnClickListener{
    private static final String TAG = "MyAdapter";
    private Context mContext;
    private LayoutInflater layoutInflater;
    private static ArrayList<MyBluetoothDevice> bleList = new ArrayList<>();
    private OnItemClickListener onItemClickListener = null;

    public MyAdapter(Context context, ArrayList<MyBluetoothDevice> devices) {
        this.mContext = context;
        this.bleList = devices;
        this.layoutInflater = LayoutInflater.from(mContext);
    }

    @Override
    public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = layoutInflater.inflate(R.layout.select_item, parent, false);
        ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(this);
        return holder;
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onBindViewHolder(MyAdapter.ViewHolder holder, int position) {
        if (getItemCount() > 0) {
            MyBluetoothDevice device = bleList.get(position);
            String name = device.getmDeviceName();
            String address = device.getDeviceAddress();
            //int rssi = device.getRssi();

            if (name != null && name.length() > 0) {
                holder.tvName.setText(name);
                holder.tvName.setTextColor(R.color.blue);
            } else {
                holder.tvName.setText("unknown");
                holder.tvName.setTextColor(R.color.blue);
            }

            //address += "\r\n" + "RSSI = " + rssi;
            holder.tvInfo.setText(address);

            if (device.isConnected()) {
                holder.itemView.setBackgroundColor(Color.argb(0xFF, 0x33, 0xB5, 0xE5));
            } else {
                holder.itemView.setBackgroundColor(Color.argb(0x0A, 0x0A, 0x0A, 0x0A));
            }
        }
        holder.itemView.setTag(position);
    }

    @Override
    public int getItemCount() {
        if (bleList != null && !bleList.isEmpty()) {
            Log.i(TAG, "getItemCount # bleList.size = " + bleList.size());
            return bleList.size();
        } else {
            return 0;
        }
    }

    @Override
    public void onClick(View v) {
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(v, (Integer) v.getTag());
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvInfo;
        public ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.ble_name_tv);
            tvInfo = itemView.findViewById(R.id.ble_info_tv);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public static MyBluetoothDevice getItem(int position) {
        return bleList.get(position);
    }

    public interface OnItemClickListener{
        void onItemClick(View view, int position);
    }
}
