package com.hardcopy.vrdefense.bluetooth;

import java.io.Serializable;

/**
 * Created by hardcopyworld.com on 2016-06-08.
 */
public class BtSerializable implements Serializable {
    private static final long serialVersionUID = 1L;

    private BluetoothManager mObject;

    public BluetoothManager getObject() {
        return mObject;
    }
    public void setObject(BluetoothManager obj) {
        this.mObject = obj;
    }
}
