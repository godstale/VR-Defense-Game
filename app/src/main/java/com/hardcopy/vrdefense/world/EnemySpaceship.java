package com.hardcopy.vrdefense.world;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.vector.Vector3;

import java.util.Random;

/**
 * Created by hardcopyworld.com on 2016-06-05.
 */
public class EnemySpaceship extends GameObject {
    public static final int ENEMY_TYPE_DARKFIGHTER = 0;
    public static final int ENEMY_TYPE_SPACESHIP1 = 1;
    public static final int ENEMY_TYPE_SPACESHIP2 = 2;
    public static final int ENEMY_TYPE_SPACESHIP3 = 3;
    public static final int ENEMY_TYPE_SPACESHIP4 = 4;
    public static final int ENEMY_TYPE_SPACESHIP5 = 5;
    public static final int ENEMY_TYPE_SPACESHIP6 = 6;

    private static final double WAYPOINT_DISTANCE = 60;

    private static final double MAX_SPEED = 0.18;
    private static final double MIN_SPEED = 0.11;
    private static final double TURN_THRESHOLD = 3;
    private static final long ATTACKED_ANIM_DURATION = 300;
    private static final double ACCEL_DEFAULT = 0.0032;
    private static final double ATTACK_ACCEL = 0.5;
    private static final long MISSILE_LAUNCH_INTERVAL = 3000;

    public Vector3 loc;
    public Vector3 velo;
    public Vector3 accel;
    public Vector3 target;
    public Vector3 attack;
    public int health = 100;

    private Vector3 pre_loc;
    private double y_offset;
    private double y_offset_temp;
    private double max_velo;
    private long start_time;
    private long attacked_time;   // attacked time by others
    private long attack_time;     // last attack time

    /**
     * This class holds status parameters of enemy and controls the movement
     * @param x     x axis value of initial position
     * @param y     y axis value of initial position
     * @param z     z axis value of initial position
     * @param offset        minimum distance to target (height(y axis) value)
     * @param max_velocity  max velocity
     * @param delay         delay object movement for specified time (milli-second)
     * @param obj   3D object instance
     */
    public EnemySpaceship(double x, double y, double z, double offset, double max_velocity, long delay, Object3D obj) {
        super(obj);

        loc = new Vector3(x, y, z);
        pre_loc = new Vector3(x, y, z);
        velo = new Vector3(0,0,0);
        target = new Vector3(0,0,0);
        attack = new Vector3(0,0,0);
        y_offset = offset;
        y_offset_temp = offset;
        max_velo = max_velocity;
        start_time = System.currentTimeMillis() + delay;
        if(max_velo <= 0) max_velo = MAX_SPEED;
        mode = MODE_ATTACK;
    }

    public void setTarget(double x, double y, double z) {
        target.x = x; target.y = y + y_offset; target.z = z;
    }

    public boolean isReadyToAttack() {
        if(mode == MODE_ATTACK) {
           if(attack_time + MISSILE_LAUNCH_INTERVAL < System.currentTimeMillis())
               return true;
        }
        return false;
    }

    public void setAttackTime() {
        attack_time = System.currentTimeMillis();
    }

    public void attacked(int damage) {
        health -= damage;
        if(health < 0) {
            mode = MODE_DYING;
            y_offset = -100;
        }
        setShowBoundingVolume(true);
        attacked_time = System.currentTimeMillis();
    }

    public void setAttackVector(Vector3 vec) {
        attack.setAll(vec.x, vec.y, vec.z);
    }

    public int update() {
        if(start_time > System.currentTimeMillis()) {
            return mode;         // do not move until object is ready.
        }
        // Hide bounding box after ATTACKED_ANIM_DURATION
        if(attacked_time + ATTACKED_ANIM_DURATION < System.currentTimeMillis()) {
            setShowBoundingVolume(false);
        }
        // Update movement
        Vector3 directionToTarget = Vector3.subtractAndCreate(target, loc);
        double mag = directionToTarget.normalize();
        if(mode == MODE_ATTACK) {
            if(mag < TURN_THRESHOLD) {
                mode = MODE_TURN;
            } else {
                accel = directionToTarget.multiply(ACCEL_DEFAULT);
                velo.add(accel);
            }
        } else if(mode == MODE_TURN) {
            if(mag > WAYPOINT_DISTANCE) {
                mode = MODE_ATTACK;
                y_offset = y_offset * -1;
            }
        } else if(mode == MODE_DYING) {
            if(mag < 2) {
                mode = MODE_DESTROYED;
            } else {
                accel = directionToTarget.multiply(ACCEL_DEFAULT);
                velo.add(accel);
            }
        } else {  // Destroyed!!
            if(mag < 2) {   // revive!!
                mode = MODE_ATTACK;
                health = 100;
                y_offset = y_offset_temp;
                Random rand = new Random();
                double x_offset = rand.nextInt(100) + target.x/2;
                double y_offset = rand.nextInt(70) - 20;
                double z_offset = rand.nextInt(100) + target.z/2;
                loc.setAll(x_offset, y_offset, z_offset);
            }
        }
        // attack effect (knock-back)
//        if(attacked_time + ATTACKED_ANIM_DURATION > System.currentTimeMillis()
//                && (mode == MODE_ATTACK || mode == MODE_TURN)) {
//            attack.normalize();
//            attack.multiply(ATTACK_ACCEL);
//            velo.add(attack);
//        }
        //Log.d("VR", "Mag="+mag+", mode="+mode);
        limitSpeed();
        pre_loc.x = loc.x; pre_loc.y = loc.y; pre_loc.z = loc.z;
        loc.add(velo);
        moveObject();

        return mode;
    }

    private void limitSpeed() {
        double mag = velo.normalize();
        if(mag > max_velo) mag = max_velo;  // limit max speed
        if(mag < MIN_SPEED) mag = MIN_SPEED;      // limit min speed
        velo.multiply(mag);
    }

    private void moveObject() {
        if(object == null)
            return;
        // locate object
        object.setPosition(loc);
        // set look-at direction
        object.setLookAt(Vector3.subtractAndCreate(loc, velo));
    }
}
