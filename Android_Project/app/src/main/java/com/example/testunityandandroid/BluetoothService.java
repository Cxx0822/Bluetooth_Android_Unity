package com.example.testunityandandroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothServerSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.example.testunityandandroid.MainActivity;

public class BluetoothService {
    // Debugging Tag
    private static final String TAG = "BluetoothService";

    // RFCOMM Protocol
    // 蓝牙固定的uuid
    private static final UUID MY_UUID = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter mBtAdapter;
    private final Handler mHandler;

    // Threads
    // 3个子线程 连接，连接后，接受
    private BluetoothService.ConnectThread mConnectThread;
    private BluetoothService.ConnectedThread mConnectedThread;
    private BluetoothService.AcceptThread mAcceptThread;
    private int mState;

    // Connection State
    // 连接状态标志位
    private static final int STATE_NONE = 0;            // we're doing nothing
    private static final int STATE_LISTEN = 1;          // now listening for incoming
    private static final int STATE_CONNECTING = 2;      // now initiating an outgoing
    private static final int STATE_CONNECTED = 3;       // now connected to a remote

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Constructors
    public BluetoothService(Context ct, Handler h) {
        this.mHandler = h;
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean getDeviceState() {
        Log.i(TAG, "Check the Bluetooth support");

        if (mBtAdapter == null) {
            Log.d(TAG, "Bluetooth is not available");
            return false;

        } else {
            Log.d(TAG, "Bluetooth is available");
            return true;
        }
    }

    private synchronized void setState(int state) {
        this.mState = state;
        this.mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    public synchronized int getState() {
        return mState;
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // If Accept Tread is null, create and start
        if (mAcceptThread != null) {

        }
        else {
            this.mAcceptThread = new BluetoothService.AcceptThread();
            this.mAcceptThread.start();
        }

        this.setState(STATE_LISTEN);
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread == null) {

            } else {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) {

        } else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        // 新建ConnectThread线程
        mConnectThread = new BluetoothService.ConnectThread(device);
        mConnectThread.start();

        setState(STATE_CONNECTING);
    }

    @RequiresPermission("android.permission.BLUETOOTH")
    public synchronized void connected(BluetoothSocket socket,
                                       BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (this.mConnectThread != null) {
            this.mConnectThread.cancel();
            this.mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (this.mConnectedThread != null) {
            this.mConnectedThread.cancel();
            this.mConnectedThread = null;
        }

        // Cancel
        if(this.mAcceptThread != null) {
            this.mAcceptThread.cancel();
            this.mAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        // 开始ConnectedThread线程
        mConnectedThread = new BluetoothService.ConnectedThread(socket);
        mConnectedThread.start();
        Message msg = this.mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
        setState(STATE_CONNECTED);
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(this.mAcceptThread != null) {
            this.mAcceptThread.cancel();
            this.mAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    // 写入功能
    public void write(byte[] out) { // Create temporary object
        ConnectedThread r; // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
            r.write(out);
        } // Perform the write unsynchronized r.write(out); }
    }

    // 连接失败处理
    private void connectionFailed() {
        setState(STATE_LISTEN);
        Message msg = this.mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(this.TOAST, "Unable to connect device");
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
    }

    // 连接丢失处理
    private void connectionLost() {
        setState(STATE_LISTEN);
        Message msg = this.mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
    }

    // 关闭蓝牙
    public void close() { // Create temporary object
        ConnectedThread r; // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
            r.cancel();
        } // Perform the write unsynchronized r.write(out); }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = BluetoothService.this.mBtAdapter.listenUsingRfcommWithServiceRecord("BluetoothPlugin", BluetoothService.MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }

            this.mmServerSocket = tmp;
        }

        @RequiresPermission("android.permission.BLUETOOTH")
        public void run() {
            Log.d(TAG, "Accept Thread Begin");
            this.setName("AcceptThread");
            BluetoothSocket socket = null;

            while(BluetoothService.this.mState != STATE_CONNECTED) {
                try {
                    socket = this.mmServerSocket.accept();
                } catch (IOException e1) {
                    Log.e(TAG, "accept() failed", e1);
                    break;
                }

                if(socket != null) {
                    BluetoothService e = BluetoothService.this;
                    synchronized(BluetoothService.this) {
                        switch(BluetoothService.this.mState) {
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e2) {
                                    Log.e(TAG, "Could not close unwanted socket", e2);
                                }
                                break;
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                BluetoothService.this.connected(socket, socket.getRemoteDevice());
                        }
                    }
                }
            }

            Log.i(TAG, "Accept Thread End");
        }

        public void cancel() {
            Log.d(TAG, "cancel " + this);

            try {
                this.mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }

        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            this.mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                // 验证 Android SPP协议的UUID
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "Connect Thread Begin");
            this.setName("ConnectThread");
            BluetoothService.this.mBtAdapter.cancelDiscovery();

            try {
                // 连接蓝牙
                this.mmSocket.connect();
                Log.d(TAG, "Connect Success");

            } catch (IOException e) {
                // 连接失败
                connectionFailed();
                Log.d(TAG, "Connect Fail");

                try {
                    // 关闭蓝牙连接
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG,"unable to close() socket during connection failure", e2);
                }

                BluetoothService.this.start();
                return;
            }

            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // 连接后的操作（收发功能）
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        private BufferedReader bufferedReader;
        private StringBuilder stringBuilder = new StringBuilder();

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                // 建立Input Output流
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            char[] charBuffer = new char[128];
            int bytesRead = -1;

            // 循环等待Input流
            while (true) {
                try {
                    // BluetoothService.this.mHandler.obtainMessage(MainActivity.MESSAGE_READ, stringBuilder.toString()).sendToTarget();

                    // bytes = mmInStream.read(buffer);
                    //BluetoothService.this.mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    // 有新的Input流进入，这里需要注意字符串格式，需要和发送端保持一致，这里为UTF_8
                    bufferedReader = new BufferedReader(new InputStreamReader(mmSocket.getInputStream(), StandardCharsets.UTF_8));
                    bytesRead = bufferedReader.read(charBuffer);
                    // 将字符数组转为字符串
                    String readMessage = new String(charBuffer, 0, bytesRead);
                    BluetoothService.this.mHandler.obtainMessage(MainActivity.MESSAGE_READ, readMessage).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    // IO错误则视为连接失败，即已经断开连接了
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                // 写入Output流
                mmOutStream.write(buffer);
                // BluetoothService.this.mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}
