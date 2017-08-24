package com.example.administrator.ble40;

import android.Manifest;
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView tv1;
    private TextView tv2;
    private TextView send;
    private BluetoothAdapter adapter;
    public BluetoothDevice deviceContent;
    private BluetoothGatt remoteBluetoothGatt;
    public static final UUID BP_UUID_SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");//血压服务
    public static final UUID BP_UUID_WRITE = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");//血压写
    public static final UUID BP_UUID_READ = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");//血压读(buqueding)
    public static final UUID BP_UUID_NOTIFY = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");//血压通知
    /**
     * 开始测量
     */
    public static final byte[] OPEN_CMD = {(byte) 0xFE, (byte) 0x81, 0x00, 0x00, 0x00, 0x01, 0x0F};


    public static final byte[] CLOSE_CMD = {(byte) 0xFE, (byte) 0x82, 0x00, 0x00, 0x00, 0x02, 0x0F};
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private TextView stop;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initListener();

    }


    private void initView() {
        tv1 = (TextView) findViewById(R.id.tv1);
        tv2 = (TextView) findViewById(R.id.tv2);
        send = (TextView) findViewById(R.id.send);
        stop = (TextView) findViewById(R.id.stop);
    }


    private void initListener() {

        tv1.setOnClickListener(this);
        tv2.setOnClickListener(this);
        send.setOnClickListener(this);
        stop.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.tv1://搜索蓝牙的点击事件

                SeachBLE();

                break;

            case R.id.tv2://连接蓝牙的点击事件

                ConnectionBLE();

                break;

            case R.id.send://发布指令

                send();

                break;


            case R.id.stop:

                stop();

                break;


        }
    }


    /**
     * 搜索蓝牙设备
     */

    private void SeachBLE() {
        //判断手机是否支持BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble不支持！", Toast.LENGTH_SHORT).show();
            return;
        } else {

            if (adapter == null) {
                //获取蓝牙管理者
                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                //一个Android系统只有一个BluetoothAdapter，通过BluetoothManager获取。
                adapter = bluetoothManager.getAdapter();
            }


            //没有获取到Adapter ,或者没有打开蓝牙adapter.isEnabled()反会
            if (!adapter.isEnabled()) {
                //会以Dialog样式显示一个Activity ， 我们可以在onActivityResult()方法去处理返回值
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 11);

            } else {//表示手机已经支持蓝牙并且已经打开蓝牙

                scannerBLE();

            }

        }
    }


    //监听上边dialog页面用户是点击了开启蓝牙还是点击了禁止开启
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 11:
                if (resultCode == this.RESULT_OK) {

                    Toast.makeText(this, "蓝牙已启用", Toast.LENGTH_SHORT).show();
                    //这里可以进行扫描附近蓝牙设备

                    scannerBLE();
                } else {
                    Toast.makeText(this, "蓝牙未启用", Toast.LENGTH_SHORT).show();
                }
                break;
        }

    }

    /**
     * 方法如果执行到这里，表示手机支持蓝牙LAB，并且设备打开了蓝牙设备,该方法进行蓝牙设备的周边搜索
     */

    private void scannerBLE() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //校验是否已具有模糊定位权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        12);
            } else {
                //具有权限
                StartBLE();
            }
        } else {
            //系统不高于6.0直接执行
            StartBLE();
        }


    }


    /**
     * 对返回的值进行处理，相当于StartActivityForResult
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        doNext(requestCode, grantResults);
    }


    /**
     * 权限申请的下一步处理
     *
     * @param requestCode  申请码
     * @param grantResults 申请结果
     */
    private void doNext(int requestCode, int[] grantResults) {
        if (requestCode == 12) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //同意权限
                StartBLE();
            } else {
                // 权限拒绝，提示用户开启权限
                denyPermission();
            }
        }
    }

    private void denyPermission() {

        Toast.makeText(this, "请去设置中心打开定位权限", Toast.LENGTH_SHORT).show();
    }


    /**
     * 真正的开始扫描，也拿到定位权限
     */
    private void StartBLE() {
        //开启蓝牙扫描
        adapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

                if (device != null) {

                    adapter.stopLeScan(this);

                }

                MainActivity.this.deviceContent = device;
                tv2.setText(device.getAddress());


            }
        });
    }


    /**
     * 根据Address地址来连接蓝牙
     */
    private void ConnectionBLE() {
        //获取远程的设备
        BluetoothDevice remoteDevice = adapter.getRemoteDevice(MainActivity.this.deviceContent.getAddress());
        //开始连接，反悔连接对象
        remoteBluetoothGatt = remoteDevice.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                //                分别表示已连接和已断开
                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    Log.e("bbbbbbbbbb", "蓝牙已经建立连接");
                    //连接到蓝牙后查找可以读写的服务，蓝牙有很多服务
                    remoteBluetoothGatt.discoverServices();

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                    Toast.makeText(MainActivity.this, "蓝牙已经断开连接", Toast.LENGTH_SHORT).show();


                }


            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                Log.e("bbbbbbb","onServicesDiscovered");

                //符合GATT协议
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    //Gatt协议对象，获取具体的Service
                    BluetoothGattService disService = gatt.getService(BP_UUID_SERVICE);

                    if (disService != null) {
                        //获取一个写的属性
                        mWriteCharacteristic = disService.getCharacteristic(BP_UUID_WRITE);


                        //获取一个回传数据的通知
                        mNotifyCharacteristic = disService.getCharacteristic(BP_UUID_NOTIFY);
                        //设置上边属性通知
                        gatt.setCharacteristicNotification(mNotifyCharacteristic, true);
                        //  获取对应属性的描述
                        BluetoothGattDescriptor descriptor = mNotifyCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        // 开启通知开关
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        //写入描述信息到协议
                        gatt.writeDescriptor(descriptor);

                    }


                }


            }


            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);

                Log.e("bbbbbbbbbb", status + "======" + descriptor.getUuid().toString());
            }

            @Override//设备发出通知时会调用到该接口
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                //获取蓝牙传递过来的值
                byte[] value = characteristic.getValue();


            }


        });




    }

    /**
     * 具体的连接方法
     */
    private void send() {
        if (remoteBluetoothGatt != null && mWriteCharacteristic != null) {

            Log.e("cim", "write result: " + mWriteCharacteristic.setValue(OPEN_CMD));
            Log.e("cim", "write characteristic result: " + remoteBluetoothGatt.writeCharacteristic(mWriteCharacteristic));
            remoteBluetoothGatt.connect();
        }

    }


    /**
     * 停止测量
     */
    private void stop() {

        if (remoteBluetoothGatt != null && mWriteCharacteristic != null) {

            Log.e("cim", "write result: " + mWriteCharacteristic.setValue(CLOSE_CMD));
            Log.e("cim", "write characteristic result: " + remoteBluetoothGatt.writeCharacteristic(mWriteCharacteristic));
            remoteBluetoothGatt.connect();
        }

    }


}
