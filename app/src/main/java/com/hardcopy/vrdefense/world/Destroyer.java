package com.hardcopy.vrdefense.world;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.vector.Vector3;

/**
 * Created by hardcopyworld.com on 2016-06-07.
 */
public class Destroyer extends GameObject {
    private static final long ATTACKED_ANIM_DURATION = 300;
    private static final long REATTACK_INTERVAL = 11000;
    private static final long ATTACK_INTERVAL = 500;
    private static final int CONTINUOUS_ATTACK_COUNT_MAX = 5;

    public Vector3 loc;
    public int health = 1000000;

    private long attacked_time;
    private long next_attack_time;
    private int attack_count;


    /**
     * This class holds status parameters of enemy and controls the movement
     * @param obj   3D object instance
     */
    public Destroyer(Object3D obj) {
        super(obj);
        loc = new Vector3(0, 0, 0);
        object = obj;
        initBoundingBox();
    }

    public int update() {
        if(attacked_time + ATTACKED_ANIM_DURATION < System.currentTimeMillis()) {
            setShowBoundingVolume(false);
        }
        return mode;
    }

    public boolean isAttackingTime() {
        if(next_attack_time < System.currentTimeMillis()) {
            return true;
        }
        return false;
    }

    public void setAttackTime() {
        attack_count++;
        if(attack_count < CONTINUOUS_ATTACK_COUNT_MAX) {
            next_attack_time = System.currentTimeMillis() + ATTACK_INTERVAL;
        } else {
            next_attack_time = System.currentTimeMillis() + REATTACK_INTERVAL;
            attack_count = 0;
        }
    }

    public void attacked(int damage) {
        health -= damage;
        if(health < 0) {
            mode = MODE_DESTROYED;
        }
        setShowBoundingVolume(true);
        attacked_time = System.currentTimeMillis();
    }

    private void moveObject() {
        // Destroyer doesn't move.
        // No need to set the position
    }

}
