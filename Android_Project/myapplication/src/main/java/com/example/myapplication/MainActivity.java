package com.example.myapplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;

import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

public class MainActivity extends UnityPlayerActivity {

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private static final String TAG = "BluetoothPlugin";
    private static final String TARGET = "BluetoothModel";

    private boolean IsScan = false;

    private String mConnectedDeviceName = null;

    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothService mBtService = null;

    private ArrayList<String> singleAddress = new ArrayList();

    private String pairedDevicesName = "";

    private Map<String, String> pairedDevicesMap = new HashMap<String,String>();

    private String readMessage = "";

    private String connectStatus = "false";

    // 处理消息模块Handle
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MESSAGE_STATE_CHANGE:
                    UnityPlayer.UnitySendMessage(TARGET, "OnStateChanged", String.valueOf(msg.arg1));
                    break;
                case MESSAGE_READ:
                    // Log.d(TAG, msg.obj.toString());
                    //byte[] readBuf = (byte[])msg.obj;
                    //String readMessage = new String(readBuf, 0, msg.arg1);
                    // UnityPlayer.UnitySendMessage(TARGET, "OnReadMessage", readMessage);
                    //Toast.makeText(MainActivity.this.getApplicationContext(), readMessage, Toast.LENGTH_SHORT).show();
                    setReadMessage(msg.obj.toString());
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[])msg.obj;
                    String writeMessage = new String(writeBuf);
                    // UnityPlayer.UnitySendMessage(TARGET, "OnSendMessage", writeMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    MainActivity.this.mConnectedDeviceName = msg.getData().getString("device_name");
                    setConnectStatus("true");
                    Toast.makeText(MainActivity.this.getApplicationContext(), "Connected to " + MainActivity.this.mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this.getApplicationContext(), msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                    break;
            }

        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @RequiresPermission("android.permission.BLUETOOTH")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if("android.bluetooth.device.action.FOUND".equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                MainActivity.this.singleAddress.add(device.getName() + "\n" + device.getAddress());
                UnityPlayer.UnitySendMessage(TARGET, "OnFoundDevice", device.getName() + ",\n" + device.getAddress());

            } else if("android.bluetooth.adapter.action.DISCOVERY_FINISHED".equals(action)) {
                if(MainActivity.this.IsScan) {
                    UnityPlayer.UnitySendMessage(TARGET, "OnScanFinish", "");
                }

                if(MainActivity.this.singleAddress.size() == 0) {
                    UnityPlayer.UnitySendMessage(TARGET, "OnFoundNoDevice", "");
                }
            }

        }
    };

    // 1. Starting Point in Unity Script
    // 开启蓝牙设备
    @RequiresPermission("android.permission.BLUETOOTH")
    public void StartPlugin() {
        if(Looper.myLooper() == null) {
            Looper.prepare();
        }

        this.SetupPlugin();
    }


    // 2. Setup Plugin
    // Get Default Bluetooth Adapter and start Service
    @RequiresPermission("android.permission.BLUETOOTH")
    public void SetupPlugin() {
        // Bluetooth Adapter
        this.mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // if Bluettoth Adapter is avaibale, start Service
        if(this.mBtAdapter == null) {
            Toast.makeText(MainActivity.this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
        } else {
            if (!this.mBtAdapter.isEnabled()) {
                this.mBtAdapter.enable();
                if (this.mBtService == null) {
                    this.startService();
                }
                Toast.makeText(MainActivity.this, "Open bluetooth success", Toast.LENGTH_SHORT).show();
            }else {
                if (this.mBtService == null) {
                    this.startService();
                }
                Toast.makeText(MainActivity.this, "Open bluetooth success", Toast.LENGTH_SHORT).show();
            }
            // Log.d(TAG, "SetupPlugin SUCCESS");
        }
    }


    // 3. Setup and Start Bluetooth Service
    private void startService() {
        // Log.d(TAG, "setupService()");
        this.mBtService = new BluetoothService(this, this.mHandler);
        this.mOutStringBuffer = new StringBuffer("");
    }

    public String DeviceName() {
        return this.mBtAdapter.getName();
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public String GetDeviceConnectedName() {
        return !this.mBtAdapter.isEnabled()?"You Must Enable The BlueTooth":(this.mBtService.getState() != 3?"Not Connected":this.mConnectedDeviceName);
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public boolean IsEnabled() {
        return this.mBtAdapter.isEnabled();
    }

    public boolean IsConnected() {
        return this.mBtService.getState() == 3;
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public void stopThread() {
        if(this.mBtService != null) {
            this.mBtService.stop();
            this.mBtService = null;
        }

        if(this.mBtAdapter != null) {
            this.mBtAdapter = null;
        }

        this.SetupPlugin();
    }

    // 扫描蓝牙设备
    @RequiresPermission(allOf = {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"})
    public String ScanDevice() {
        // Toast.makeText(MainActivity.this, "Start - ScanDevice()", Toast.LENGTH_SHORT).show();
        // Log.d(TAG, "Start - ScanDevice()");
        if(this.mBtAdapter == null || !this.mBtAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "You Must Enable The BlueTooth", Toast.LENGTH_SHORT).show();
            // Log.d(TAG, "You Must Enable The BlueTooth");
            // return "You Must Enable The BlueTooth";
        } else {
            this.IsScan = true;
            this.singleAddress.clear();
            IntentFilter filter = new IntentFilter("android.bluetooth.device.action.FOUND");
            this.registerReceiver(this.mReceiver, filter);
            filter = new IntentFilter("android.bluetooth.adapter.action.DISCOVERY_FINISHED");
            this.registerReceiver(this.mReceiver, filter);

            // 获取扫描到的蓝牙设备名称和ID
            this.mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = this.mBtAdapter.getBondedDevices();
            if(pairedDevices.size() > 0) {
                pairedDevicesName = "";
                for (BluetoothDevice device : pairedDevices) {
                    pairedDevicesName = pairedDevicesName + device.getName() + ",";
                    pairedDevicesMap.put(device.getName(), device.getAddress());
                }
            }

            this.doDiscovery();
            Toast.makeText(MainActivity.this, "ScanDevice SUCCESS", Toast.LENGTH_SHORT).show();
            // Log.d(TAG, "ScanDevice SUCCESS");
            // return "SUCCESS";
        }
        // Toast.makeText(MainActivity.this, pairedDevicesName.toString(), Toast.LENGTH_SHORT).show();
        // Log.d(TAG, pairedDevicesName);
        return pairedDevicesName;
    }

    // 根据MAC地址连接蓝牙设备
    @RequiresPermission(allOf = {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"})
    public void Connect(String TheAddress) {
        if(this.mBtAdapter == null || !this.mBtAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "You Must Enable The BlueTooth", Toast.LENGTH_SHORT).show();
        }else {
            if(this.mBtAdapter.isDiscovering()) {
                this.mBtAdapter.cancelDiscovery();
            }

            this.IsScan = false;
            String address = TheAddress.substring(TheAddress.length() - 17);
            this.mConnectedDeviceName = TheAddress.split(",")[0];
            BluetoothDevice device = this.mBtAdapter.getRemoteDevice(address);

            this.mBtService.connect(device);
            // Toast.makeText(MainActivity.this, "Connect SUCCESS", Toast.LENGTH_SHORT).show();
        }
    }

    // 关闭蓝牙连接
    @RequiresPermission("android.permission.BLUETOOTH")
    public void disConnect() {
        if(this.mBtAdapter == null || !this.mBtAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "You Must Enable The BlueTooth", Toast.LENGTH_SHORT).show();
            //  Log.d(TAG, "You Must Enable The BlueTooth");

        } else if(this.mBtService.getState() != 3) {
            Toast.makeText(MainActivity.this, "Not Connected", Toast.LENGTH_SHORT).show();
            //  Log.d(TAG, "Not Connected");

        } else {
            this.mBtService.close();

            Toast.makeText(MainActivity.this, "DisConnect SUCCESS", Toast.LENGTH_SHORT).show();
            // Log.d(TAG, "sendMessage SUCCESS");
        }
    }

    @RequiresPermission(allOf = {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"})
    private void doDiscovery() {
        if(this.mBtAdapter.isDiscovering()) {
            this.mBtAdapter.cancelDiscovery();
        }

        this.mBtAdapter.startDiscovery();
    }

    @RequiresPermission(allOf = {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"})
    String BluetoothSetName(String name) {
        if(!this.mBtAdapter.isEnabled()) {
            return "You Must Enable The BlueTooth";
        } else if(this.mBtService.getState() != 3) {
            return "Not Connected";
        } else {
            this.mBtAdapter.setName(name);
            return "SUCCESS";
        }
    }

    // 关闭蓝牙设备
    @RequiresPermission(allOf = {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"})
    public void DisableBluetooth() {
        if(this.mBtAdapter == null || !this.mBtAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "You Must Enable The BlueTooth", Toast.LENGTH_SHORT).show();
        } else {
            if(this.mBtAdapter != null) {
                this.mBtAdapter.cancelDiscovery();
            }

            if(this.mBtAdapter.isEnabled()) {
                this.mBtAdapter.disable();
            }

            Toast.makeText(MainActivity.this, "DisableBluetooth SUCCESS", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public String BluetoothEnable() {
        try {
            if(!this.mBtAdapter.isEnabled()) {
                Intent e = new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE");
                this.startActivityForResult(e, 2);
            }

            return "SUCCESS";

        } catch (Exception e) {
            return "Faild";
        }
    }

    public void showMessage(final String message) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("WrongConstant")
    @RequiresPermission("android.permission.BLUETOOTH")
    public String ensureDiscoverable() {
        if(!this.mBtAdapter.isEnabled()) {
            return "You Must Enable The BlueTooth";
        } else {
            if(this.mBtAdapter.getScanMode() != 23) {
                Intent discoverableIntent = new Intent("android.bluetooth.adapter.action.REQUEST_DISCOVERABLE");
                discoverableIntent.putExtra("android.bluetooth.adapter.extra.DISCOVERABLE_DURATION", 300);
                this.startActivity(discoverableIntent);
            }

            return "SUCCESS";
        }
    }

    // 发送蓝牙数据
    @RequiresPermission("android.permission.BLUETOOTH")
    public void sendMessage(String message) {
        if(this.mBtAdapter == null || !this.mBtAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "You Must Enable The BlueTooth", Toast.LENGTH_SHORT).show();
            //  Log.d(TAG, "You Must Enable The BlueTooth");

        } else if(this.mBtService.getState() != 3) {
            Toast.makeText(MainActivity.this, "Not Connected", Toast.LENGTH_SHORT).show();
            //  Log.d(TAG, "Not Connected");

        } else {
            if(message.length() > 0) {
                byte[] send = message.getBytes();
                this.mBtService.write(send);
                this.mOutStringBuffer.setLength(0);
            }

            // Toast.makeText(MainActivity.this, "sendMessage SUCCESS", Toast.LENGTH_SHORT).show();
            // Log.d(TAG, "sendMessage SUCCESS");
        }
    }

    // 与Unity的接口
    public void onOpen(){
        StartPlugin();
    }

    public String onScan() {
        return ScanDevice();
    }

    public String onGetDevicesAddress(String deviceName) {
        return pairedDevicesMap.get((deviceName));
    }

    public void onConnect(String deviceName) {
        String deviceAddress = onGetDevicesAddress(deviceName);
        Connect(deviceAddress);
    }

    public String getConnectStatus() {
        return connectStatus;
    }

    public void setConnectStatus(String connectStatus) {
        this.connectStatus = connectStatus;
    }

    public void onSendMessage(String message) {
        sendMessage(message);
    }

    public String getReadMessage() {
        return readMessage;
    }

    public void setReadMessage(String readMessage) {
        this.readMessage = readMessage;
    }

    public String onReadMessage(){
        return getReadMessage();
    }

    public void onDisconnect() {
        disConnect();
    }
}

