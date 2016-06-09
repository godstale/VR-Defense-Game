package com.hardcopy.vrdefense;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hardcopy.vrdefense.bluetooth.BluetoothManager;
import com.hardcopy.vrdefense.bluetooth.BtSerializable;
import com.hardcopy.vrdefense.bluetooth.ConnectionInfo;
import com.hardcopy.vrdefense.bluetooth.Constants;
import com.hardcopy.vrdefense.bluetooth.TransactionBuilder;
import com.hardcopy.vrdefense.utils.Settings;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Context mContext;
    private Settings mSettings;
    private BluetoothManager mBtManager;
    private BtHandler mBtHandler;
    private ConnectionInfo mConnectionInfo;
    private TransactionBuilder mTransactionBuilder;

    private LinearLayout mLayoutMenu;
    private TextView mTextTitle;
    private Button mButtonStart;
    private Button mButtonBt;
    private Button mButtonCredit;
    private Button mButtonExit;
    private LinearLayout mLayoutInfo;
    private TextView mTextScore;
    private TextView mTextBt;
    private LinearLayout mLayoutCredit;
    private Button mButtonCreditExit;
    private String mDeviceAddress;
    private int mBtStatus = BluetoothManager.STATE_NONE;
    private String mBtStatusString;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Initialize UI
        mLayoutMenu = (LinearLayout) findViewById(R.id.layout_menu);
        mButtonStart = (Button) findViewById(R.id.button_start);
        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, VRDefenseActivity.class);
                startActivity(intent);
            }
        });
        mButtonBt = (Button) findViewById(R.id.button_bt);
        mButtonBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!btAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT);
                } else {
                    setupBT();
                }
            }
        });
        mButtonCredit = (Button) findViewById(R.id.button_credit);
        mButtonCredit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLayoutMenu.setVisibility(View.GONE);
                mLayoutInfo.setVisibility(View.GONE);
                mLayoutCredit.setVisibility(View.VISIBLE);
            }
        });
        mButtonExit = (Button) findViewById(R.id.button_exit);
        mButtonExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mLayoutInfo = (LinearLayout) findViewById(R.id.layout_info);
        mTextScore = (TextView) findViewById(R.id.text_score);
        mTextBt = (TextView) findViewById(R.id.text_bt_status);

        mLayoutCredit = (LinearLayout) findViewById(R.id.layout_credit);
        mButtonCreditExit = (Button) findViewById(R.id.button_exit_credit);
        mButtonCreditExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLayoutCredit.setVisibility(View.GONE);
                mLayoutMenu.setVisibility(View.VISIBLE);
                mLayoutInfo.setVisibility(View.VISIBLE);
            }
        });

        // Prepare game
        initialize();
    }

    @Override
    public void onResume() {
        super.onResume();

        showScore();
        if(mBtManager != null) {
            mBtStatus = mBtManager.getState();
            if(mBtHandler != null)
                mBtManager.setHandler(mBtHandler);
        }
        showBtStatus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        finalize();
    }

    @Override
    public void onLowMemory (){
        super.onLowMemory();
        // onDestroy is not always called when applications are finished by Android system.
        finalize();
    }


    private void initialize() {
        // Make instances
        mBtHandler = new BtHandler();
        mSettings = Settings.getInstance(mContext);
        mConnectionInfo = ConnectionInfo.getInstance(mContext);

        // Get local Bluetooth adapter
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, Constants.REQUEST_ENABLE_BT);
        }
    }

    public void finalize() {
        // Stop the bluetooth session
        if (mBtManager != null) {
            mBtManager.stop();
            mBtManager.setHandler(null);
        }
        mBtManager = null;
        mContext = null;
        mConnectionInfo = null;
        mSettings = null;
    }

    private void showScore() {
        String score = new String();
        score += "Last score = ";
        score += mSettings.getLastScore();
        score += "\nTop score = ";
        score += mSettings.getTopScore();
        mTextScore.setText(score);
    }

    private void showBtStatus() {
        switch (mBtStatus) {
            case BluetoothManager.STATE_NONE:
                mBtStatusString = "none";
                break;
            case BluetoothManager.STATE_LISTEN:
                mBtStatusString = "listening";
                break;
            case BluetoothManager.STATE_CONNECTING:
                mBtStatusString = "connecting";
                break;
            case BluetoothManager.STATE_CONNECTED:
                mBtStatusString = "connected";
                break;
        }

        String status = new String();
        status += "BT controller : ";
        status += mBtStatusString;
        mTextBt.setText(status);
    }

    public void setupBT() {
        // Initialize the BluetoothManager to perform bluetooth connections
        if(mBtManager == null)
            mBtManager = BluetoothManager.getInstance(this, mBtHandler);
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, Constants.REQUEST_CONNECT_DEVICE);
    }

    /**
     * Initiate a connection to a remote device.
     * @param address  Device's MAC address to connect
     */
    private void connectDevice(String address) {
        Log.d(TAG, "Service - connect to " + address);

        // Get the BluetoothDevice object
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter != null) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            if(device != null && mBtManager != null) {
                mBtManager.connect(device);
            }
        }
    }

    /**
     * Connect to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    private void connectDevice(BluetoothDevice device) {
        if(device != null && mBtManager != null) {
            mBtManager.connect(device);
        }
    }

    /**
     * Send message to remote device using Bluetooth
     */
    private void sendMessageToRemote(String message) {
        sendMessageToDevice(message);
    }

    /**
     * Send message to device.
     * @param message		message to send
     */
    private void sendMessageToDevice(String message) {
        if(message == null || message.length() < 1)
            return;

        TransactionBuilder.Transaction transaction = mTransactionBuilder.makeTransaction();
        transaction.begin();
        transaction.setMessage(message);
        transaction.settingFinished();
        transaction.sendTransaction();
    }

    /**
     * Receives result from external activity
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult " + resultCode);

        switch(requestCode) {
            case Constants.REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.e(TAG, "BT is not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                }
                break;
            case Constants.REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Attempt to connect to the device
                    if(address != null) {
                        if(mConnectionInfo != null)
                            mConnectionInfo.setDeviceAddress(address);
                        connectDevice(address);
                    }
                }
                break;
        }	// End of switch(requestCode)
    }

    /*****************************************************
     *	Inner classes
     ******************************************************/

    /**
     * Receives messages from bluetooth manager
     */
    class BtHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                // Bluetooth state changed
                case BluetoothManager.MESSAGE_STATE_CHANGE:
                    // Bluetooth state Changed
                    Log.d(TAG, "Service - MESSAGE_STATE_CHANGE: " + msg.arg1);

                    switch (msg.arg1) {
                        case BluetoothManager.STATE_NONE:
                            mBtStatus = BluetoothManager.STATE_NONE;
                            showBtStatus();
                            break;
                        case BluetoothManager.STATE_LISTEN:
                            mBtStatus = BluetoothManager.STATE_LISTEN;
                            showBtStatus();
                            break;
                        case BluetoothManager.STATE_CONNECTING:
                            mBtStatus = BluetoothManager.STATE_CONNECTING;
                            showBtStatus();
                            break;
                        case BluetoothManager.STATE_CONNECTED:
                            mBtStatus = BluetoothManager.STATE_CONNECTED;
                            showBtStatus();
                            break;
                    }
                    break;

                // If you want to send data to remote
                case BluetoothManager.MESSAGE_WRITE:
                    break;

                // Received packets from remote
                case BluetoothManager.MESSAGE_READ:
                    Log.d(TAG, "BT - MESSAGE_READ: ");
                    byte[] readBuf = (byte[]) msg.obj;
                    int readCount = msg.arg1;
                    if(msg.arg1 > 0) {
                        String strMsg = new String(readBuf, 0, msg.arg1);
                        // parse string
                        if(strMsg.contains("b")) {
                            Intent intent = new Intent(MainActivity.this, VRDefenseActivity.class);
                            startActivity(intent);
                        } else if(strMsg.contains("c")) {
                        }
                    }
                    break;

                case BluetoothManager.MESSAGE_DEVICE_NAME:
                    Log.d(TAG, "MESSAGE_DEVICE_NAME: ");

                    // save connected device's name and notify using toast
                    String deviceAddress = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_ADDRESS);
                    String deviceName = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_NAME);

                    if(deviceName != null && deviceAddress != null) {
                        // Remember device's address and name
                        mConnectionInfo.setDeviceAddress(deviceAddress);
                        mConnectionInfo.setDeviceName(deviceName);

                        Toast.makeText(mContext,
                                "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;

                case BluetoothManager.MESSAGE_TOAST:
                    Log.d(TAG, "BT - MESSAGE_TOAST: ");

                    Toast.makeText(mContext,
                            msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;

            }	// End of switch(msg.what)

            super.handleMessage(msg);
        }
    }	// End of class MainHandler
}
