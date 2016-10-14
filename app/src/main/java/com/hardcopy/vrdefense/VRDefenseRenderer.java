package com.hardcopy.vrdefense;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;

import com.google.vrtoolkit.cardboard.HeadTransform;
import com.hardcopy.vrdefense.world.World;

import org.rajawali3d.util.MeshExporter;
import org.rajawali3d.vr.renderer.VRRenderer;


public class VRDefenseRenderer extends VRRenderer {

    private Handler mHandler;
    private Callback mCallback;
    private World mWorld;
    private float[] mHeadViewUpVec = new float[3];
    private float[] mHeadViewForwardVec = new float[3];

    public interface Callback {
        public void onEvent(int event, int argInt0, int argInt1, Object argObj);
    }


    public VRDefenseRenderer(Context context) {
        super(context);
    }

    @Override
    public void initScene() {
        mWorld = new World(getContext(), this);
        mWorld.initialize();
        if(mCallback != null) {
            mWorld.setCallback(mCallback);
        }
    }

    @Override
    public void onRender(long elapsedTime, double deltaTime) {
        super.onRender(elapsedTime, deltaTime);

        mWorld.update(mHeadViewForwardVec, mHeadViewUpVec);
        if(mWorld.getGameStatus() == World.GAME_STATUS_END)
            pauseGame();
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset,
                                 int yPixelOffset) {

    }

    @Override public void onTouchEvent(MotionEvent event) {
        Log.d("VR", "x="+event.getX()+", y="+event.getY());
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        headTransform.getUpVector(mHeadViewUpVec, 0);
        headTransform.getForwardVector(mHeadViewForwardVec, 0);
        super.onNewFrame(headTransform);
    }

    public void setHandler(Handler h) {
        mHandler = h;
    }

    public void setCallback(Callback c) {
        mCallback = c;
        if(mWorld != null) {
            mWorld.setCallback(c);
        } else {
            Log.d("VRDefense", "mWorld is null !!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    public void pauseGame() {
        mWorld.pauseGame();
    }

    public void finish() throws Throwable {
        mWorld.pauseGame();
        mWorld.finish();
        mWorld = null;
        stopRendering();
        finalize();
    }

    public void fire() {
        if(mWorld != null)
            mWorld.fire();
    }

    public void pauseAudio() {
        if(mWorld != null)
            mWorld.pauseAudio();
    }

    public void resumeAudio() {
        if(mWorld != null)
            mWorld.resumeAudio();
    }

    public int getScore() {
        if(mWorld == null)
            return -1;
        return mWorld.getScore();
    }

}
