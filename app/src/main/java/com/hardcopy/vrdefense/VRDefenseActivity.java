package com.hardcopy.vrdefense;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.hardcopy.vrdefense.bluetooth.BluetoothManager;
import com.hardcopy.vrdefense.bluetooth.ConnectionInfo;
import com.hardcopy.vrdefense.bluetooth.Constants;
import com.hardcopy.vrdefense.bluetooth.TransactionBuilder;
import com.hardcopy.vrdefense.bluetooth.TransactionReceiver;
import com.hardcopy.vrdefense.utils.Settings;

import org.rajawali3d.util.RajLog;
import org.rajawali3d.vr.VRActivity;

public class VRDefenseActivity extends VRActivity {
    private static final String TAG = "VRDefenseActivity";

    private Context mContext;
    private VRDefenseRenderer mRenderer;
    private BtHandler mHandler;
    private Settings mSettings;

    // Bluetooth
    private BluetoothManager mBtManager = null;
    private ConnectionInfo mConnectionInfo = null;		// Remembers connection info when BT connection is made
    private TransactionBuilder mTransactionBuilder = null;
    private TransactionReceiver mTransactionReceiver = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        mContext = getApplicationContext();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mRenderer = new VRDefenseRenderer(this);
        setRenderer(mRenderer);
        mRenderer.setHandler(mHandler);

        setConvertTapIntoTrigger(true);

        initialize();
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        RajLog.i("onCardboardTrigger");
    }

    @Override
    public void onPause() {
        mRenderer.pauseAudio();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mRenderer.resumeAudio();
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

    /*****************************************************
     *	Private methods
     ******************************************************/
    private void initialize() {
        // Make instances
        mConnectionInfo = ConnectionInfo.getInstance(mContext);
        mSettings = Settings.getInstance(mContext);

        mHandler = new BtHandler();
        mBtManager = BluetoothManager.getInstance(mContext, mHandler);
        if(mBtManager != null)
            mBtManager.setHandler(mHandler);

        // Get local Bluetooth adapter
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            return;
        }
    }

    /*****************************************************
     *	Public methods
     ******************************************************/
    public void finalize() {
        // Stop the bluetooth session
        if (mBtManager != null) {
            //mBtManager.stop();
            mBtManager.setHandler(null);
        }
        mBtManager = null;
        mContext = null;
        mConnectionInfo = null;
        mSettings = null;
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
                // Received packets from remote
                case BluetoothManager.MESSAGE_READ:
                    Log.d(TAG, "BT - MESSAGE_READ: ");

                    byte[] readBuf = (byte[]) msg.obj;
                    int readCount = msg.arg1;
                    if(msg.arg1 > 0) {
                        String strMsg = new String(readBuf, 0, msg.arg1);
                        // parse string
                        if(strMsg.contains("b")) {
                            mRenderer.fire();
                        } else if(strMsg.contains("c")) {
                            // update score
                            int score = mRenderer.getScore();
                            int top_score = mSettings.getTopScore();
                            if(score > top_score) {
                                mSettings.setTopScore(score);
                            }
                            mSettings.setLastScore(score);

                            // release resources
                            try {
                                mRenderer.finish();
                            } catch(Throwable e) {
                                e.printStackTrace();
                            }
                            finish();
                        }
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
