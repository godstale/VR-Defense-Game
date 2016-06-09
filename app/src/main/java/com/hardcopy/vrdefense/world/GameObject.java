package com.hardcopy.vrdefense.world;

import org.rajawali3d.Object3D;
import org.rajawali3d.bounds.IBoundingVolume;
import org.rajawali3d.math.vector.Vector3;

/**
 * Created by hardcopyworld.com on 2016-06-07.
 */
public abstract class GameObject {
    public static final int MODE_ATTACK = 1;
    public static final int MODE_TURN = 101;
    public static final int MODE_MOVING = 201;
    public static final int MODE_DYING = 1001;
    public static final int MODE_DESTROYED = 1101;

    public Object3D object;
    protected int mode = MODE_MOVING;
    protected IBoundingVolume mBBox;

    /**
     * This class holds status parameters of object
     * @param obj   3D object instance
     */
    public GameObject(Object3D obj) {
        object = obj;
        initBoundingBox();
    }

    public abstract int update();

    public void initBoundingBox() {
        mBBox = object.getGeometry().getBoundingBox();
        mBBox.transform(object.getModelMatrix());
    }

    public IBoundingVolume getBoundingBox() {
        return mBBox;
    }

    public void setShowBoundingVolume(boolean isShow) {
        if(object != null)
            object.setShowBoundingVolume(isShow);
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int m) {
        mode = m;
    }

    public boolean isAlive() {
        if(mode < MODE_DYING)
            return true;
        return false;
    }
}
