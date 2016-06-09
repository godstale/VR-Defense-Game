package com.hardcopy.vrdefense.world;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.vector.Vector3;

import java.util.Random;

/**
 * Created by hardcopyworld.com on 2016-06-07.
 */
public class Missile extends GameObject {
    public static final int FROM_FRIENDLY = 1;
    public static final int FROM_ENEMY = 2;
    public static final int FROM_DESTROYER = 101;   // doesn't check collision
    public static final int FROM_MOTHERSHIP = 111;  // doesn't check collision

    private static final long TIME_TO_LIVE = 3000;

    public int from = FROM_FRIENDLY;
    public Vector3 loc;
    public Vector3 velo;
    public Vector3 accel;
    public Vector3 target;

    private Vector3 pre_loc;
    private double SPEED = 0.45;
    private long start_time = 0;


    /**
     * This class holds status parameters of enemy and controls the movement
     * @param x     x axis value of initial position
     * @param y     y axis value of initial position
     * @param z     z axis value of initial position
     * @param firedFrom     fired from
     * @param obj   3D object instance
     */
    public Missile(double x, double y, double z, int firedFrom, Object3D obj) {
        super(obj);

        from = firedFrom;
        loc = new Vector3(x, y, z);
        pre_loc = new Vector3(x, y, z);
        velo = new Vector3(0,0,0);
        target = new Vector3(0,0,0);
        start_time = System.currentTimeMillis();
        mode = MODE_MOVING;
    }

    public void setTarget(double x, double y, double z) {
        target.x = x; target.y = y; target.z = z;
        velo.subtractAndSet(target, loc);
        velo.normalize();
        velo.multiply(SPEED);
    }

    public void setSpeed(double speed) {
        SPEED = speed;
    }

    public int update() {
        double distance = loc.distanceTo(target);
        if(mode != MODE_DESTROYED) {
            if(distance < 1.5 || start_time + TIME_TO_LIVE < System.currentTimeMillis()) {
                mode = MODE_DESTROYED;
            }
        } else {  // Destroyed!!
            return mode;
        }
        //Log.d("VR", "Mag="+mag+", mode="+mode);
        pre_loc.x = loc.x; pre_loc.y = loc.y; pre_loc.z = loc.z;
        loc.add(velo);
        moveObject();

        return mode;
    }

    private void moveObject() {
        if(object == null)
            return;
        // locate object
        object.setPosition(loc);
        // set look-at direction
        //object.setLookAt(Vector3.subtractAndCreate(loc, velo));
    }
}
