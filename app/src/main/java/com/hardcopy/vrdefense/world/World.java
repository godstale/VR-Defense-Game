package com.hardcopy.vrdefense.world;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hardcopy.vrdefense.R;

import com.google.vrtoolkit.cardboard.audio.CardboardAudioEngine;

import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.SplineTranslateAnimation3D;
import org.rajawali3d.bounds.IBoundingVolume;
import org.rajawali3d.curves.CatmullRomCurve3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderAWD;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.NormalMapTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.terrain.SquareTerrain;
import org.rajawali3d.terrain.TerrainGenerator;
import org.rajawali3d.vr.renderer.VRRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Vector;

/**
 * Created by hardcopyworld.com on 2016-06-05.
 */
public class World {
    private static final String TAG = "World";

    private static final double CAM_OFFSET = 8;
    private static final long ANIM_DURATION = 120000;
    private static final int MISSILE_DAMAGE = 15;
    private static final long MISSILE_INTERVAL = 400;
    private static final int SCORE_ENEMY_ATTACK = 15;
    private static final int SCORE_ENEMY_DESTROY = 100;
    private static final int SCORE_FRIENDLY_DESTROY = -100;
    private static final int SCORE_MOTHERSHIP_ATTACK = -1;
    private static final int SCORE_DESTROYER_ATTACK = 1;
    private static final int MAX_MOTHERSHIP_MISSILE = 6;
    private static final int ENEMY_MISSILE_COLOR = 0x55000099;
    private static final int FRIENDLY_MISSILE_COLOR = 0x55990000;

    public static final int GAME_STATUS_PLAYING = 1;
    public static final int GAME_STATUS_PAUSE = 11;
    public static final int GAME_STATUS_END = 101;

    // Android framework
    private Context mContext;
    // 3D essentials
    private VRRenderer mRenderer;

    // World
    private SquareTerrain mTerrain;
    private int mScore = 0;
    private int mGameStatus = GAME_STATUS_PLAYING;

    // Friendly objects
    private Mothership mMothership;
    private Object3D mMothershipObj;
    private SplineTranslateAnimation3D mMothershipAnim;

    // Enemy objects
    public Destroyer mDestroyer;
    private List<EnemySpaceship> mEnemies;
    private int mEnemyCountMax = 5;
    private int mEnemyCount = 0;
    private int mEnemyDestroyed = 0;

    // Moving things
    private volatile List<Missile> mMissiles;
    private Object3D mMissileObj;
    private long mLastFireTime;

    // Camera
    private Vector3 mPrevCameraPos = new Vector3(0,0,0);
    private Vector3 mHeadViewForward = new Vector3(0,0,0);
    private Vector3 mHeadViewUp = new Vector3(0,0,0);
    private SplineTranslateAnimation3D mCameraAnim;
    private Cube mCameraBox;    // In rajawali-VR, I cannot get position from animated camera.
                                // Use camera chasing box instead.

    // Audio
    private CardboardAudioEngine cardboardAudioEngine;
    private volatile int spaceShipSoundId = CardboardAudioEngine.INVALID_ID;
    private volatile int sonarSoundId = CardboardAudioEngine.INVALID_ID;



    public World(Context c, VRRenderer renderer) {
        mContext = c;
        mRenderer = renderer;
        initialize();
    }

    /***************************************************
     * Initialization
     ***************************************************/
    public void initialize() {
        // Lights
        setLights();
        // Camera settings
        makeCamera();
        // Create terrain from bitmap image
        //createTerrain();
        // Set skybox
        setSkybox();
        // Audio
        initAudio();

        // Make objects
        makeFixedObjects();
        // Make mothership
        makeMothership();

        // Make enemy
        mEnemies = Collections.synchronizedList(new ArrayList<EnemySpaceship>());
        long delay = 10000;
        for(int i=0; i<mEnemyCountMax; i++) {
            EnemySpaceship ship = makeEnemy(EnemySpaceship.ENEMY_TYPE_DARKFIGHTER, 5, 0, delay);
            if(ship != null && ship.object != null)
                mEnemies.add(ship);
            delay = delay + 5000;
        }
        // cache missile object
        mMissiles = new Vector<Missile>();
        prepareMissile();
    }


    /***************************************************
     * Public methods
     ***************************************************/
    public void finish() {
        pauseAudio();
        pauseGame();
        mGameStatus = GAME_STATUS_END;
        mContext = null;
    }

    public void update(float[] headViewForward, float[] headViewUp) {
        // Remember camera look-at vector
        mHeadViewForward.setAll(headViewForward[0], headViewForward[1], headViewForward[2]);
        mHeadViewUp.setAll(headViewUp[0], headViewUp[1], headViewUp[2]);

        // Check game status
        if(mGameStatus != GAME_STATUS_PLAYING) {
            return;
        }

        // Update mothership
        mMothership.update();
        mMothership.initBoundingBox();
        if(mMothership.isAttackingTime()) {
                attackDestroyer(mMothership.object.getX(),
                        mMothership.object.getY(),
                        mMothership.object.getZ());
                mMothership.setAttackTime();
        }

        // Update destroyer
        mDestroyer.update();
        if(mDestroyer.isAttackingTime()) {
            attackMothership(mDestroyer.object.getX(),
                    mDestroyer.object.getY() + 10,
                    mDestroyer.object.getZ(),
                    Missile.FROM_DESTROYER);
            mDestroyer.setAttackTime();
        }

        // Update enemy movement
        for(int i=0; i<mEnemies.size(); i++) {
            EnemySpaceship enemy = mEnemies.get(i);
            enemy.setTarget(mMothershipObj.getX(), mMothershipObj.getY(), mMothershipObj.getZ());
            enemy.update();
            enemy.initBoundingBox();
            // attack mothership
            if(enemy.isReadyToAttack()) {
                attackMothership(enemy.object.getX(), enemy.object.getY(), enemy.object.getZ(),
                        Missile.FROM_ENEMY);
                enemy.setAttackTime();
            }
        }

        synchronized (mMissiles) {
            // Update missile movement and check collision
            boolean removeMissile = false;
            for(int i=mMissiles.size()-1; i>-1; i--) {
                Missile missile = mMissiles.get(i);
                // update movement
                if(missile.update() == Missile.MODE_DESTROYED) {
                    // delete missile
                    mRenderer.getCurrentScene().removeChild(missile.object);
                    //mMissileObj.removeChild(missile.object);
                    mMissiles.remove(missile);
                    continue;
                }
                if(missile.from >= Missile.FROM_DESTROYER)
                    continue;
                // check collision
                missile.initBoundingBox();
                IBoundingVolume bbox = missile.getBoundingBox();
                if(bbox.intersectsWith(mMothership.getBoundingBox())) {
                    mMothership.attacked(MISSILE_DAMAGE);
                    mScore += SCORE_MOTHERSHIP_ATTACK;
                    if(mMothership.getMode() == Mothership.MODE_DESTROYED) {
                        endGame();
                        return;
                    }
                }
//                else if(missile.from == Missile.FROM_FRIENDLY
//                        && bbox.intersectsWith(mDestroyer.getBoundingBox())) {
//                    mDestroyer.attacked(MISSILE_DAMAGE);
//                    mScore += SCORE_DESTROYER_ATTACK;
//                    removeMissile = true;
//                }
                else {
                    for(int j=0; j<mEnemies.size(); j++) {
                        EnemySpaceship enemy = mEnemies.get(j);
                        if(enemy.getMode() != EnemySpaceship.MODE_DYING
                                && missile.from == Missile.FROM_FRIENDLY
                                && bbox.intersectsWith(enemy.getBoundingBox())) {
                            enemy.attacked(MISSILE_DAMAGE);
                            enemy.setAttackVector(missile.object.getLookAt());
                            mScore += SCORE_ENEMY_ATTACK;
                            if(enemy.getMode() == EnemySpaceship.MODE_DYING) {
                                mScore += SCORE_ENEMY_DESTROY;
                            }
                            removeMissile = true;
                        }
                    }
                }
                // remove missile
                if(removeMissile) {
                    mRenderer.getCurrentScene().removeChild(missile.object);
                    //mMissileObj.removeChild(missile.object);
                    mMissiles.remove(missile);
                    System.gc();
                }
            }
        }
    }

    public void fire() {
        if(mLastFireTime + MISSILE_INTERVAL > System.currentTimeMillis()) {
            return;
        }
        // clone object and add to scene
        Object3D missileObj = mMissileObj.clone();
        missileObj.setX(mCameraBox.getX());
        missileObj.setY(mCameraBox.getY());
        missileObj.setZ(mCameraBox.getZ());
        missileObj.setColor(FRIENDLY_MISSILE_COLOR);
        mRenderer.getCurrentScene().addChild(missileObj);
        //mMissileObj.addChild(missileObj);
        // make Missile instance and set location and target
        Missile missile = new Missile(mCameraBox.getX(), mCameraBox.getY(), mCameraBox.getZ(),
                Missile.FROM_FRIENDLY, missileObj);
        Vector3 targetVec = new Vector3(mHeadViewForward);
        targetVec.normalize();
        targetVec.multiply(30);
        missile.setTarget(mCameraBox.getX() + targetVec.x,
                mCameraBox.getY() + targetVec.y,
                mCameraBox.getZ() + targetVec.z);
        synchronized (mMissiles) {
            mMissiles.add(missile);
        }
    }

    public int getScore() {
        return mScore;
    }

    public int getGameStatus() {
        return mGameStatus;
    }

    public void pauseGame() {
        if(mGameStatus != GAME_STATUS_PAUSE) {
            mCameraAnim.pause();
            mMothershipAnim.pause();
            mGameStatus = GAME_STATUS_PAUSE;
        }
    }

    public void resumeGame() {
        if(mGameStatus != GAME_STATUS_PAUSE) {
            mCameraAnim.play();
            mMothershipAnim.play();
            mGameStatus = GAME_STATUS_PLAYING;
        }
    }

    public void pauseAudio() {
        if(cardboardAudioEngine != null) {
            cardboardAudioEngine.pause();
        }
    }

    public void resumeAudio() {
        if(cardboardAudioEngine != null) {
            cardboardAudioEngine.resume();
        }
    }


    /***************************************************
     * Private methods
     ***************************************************/
    private void createTerrain() {
        //
        // -- Load a bitmap that represents the terrain. Its color values will
        //    be used to generate heights.
        Bitmap bmp = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.terrain);

        try {
            SquareTerrain.Parameters terrainParams = SquareTerrain.createParameters(bmp);
            // -- set terrain scale
            terrainParams.setScale(8f, 40f, 8f);
            // -- the number of plane subdivisions
            terrainParams.setDivisions(128);
            // -- the number of times the textures should be repeated
            terrainParams.setTextureMult(4);
            //
            // -- Terrain colors can be set by manually specifying base, middle and
            //    top colors.
            // --  terrainParams.setBasecolor(Color.argb(255, 0, 0, 0));
            //     terrainParams.setMiddleColor(Color.argb(255, 200, 200, 200));
            //     terrainParams.setUpColor(Color.argb(255, 0, 30, 0));
            // -- However, for this example we'll use a bitmap
            terrainParams.setColorMapBitmap(bmp);
            // -- create the terrain
            mTerrain = TerrainGenerator.createSquareTerrainFromBitmap(terrainParams, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // -- The bitmap won't be used anymore, so get rid of it.
        bmp.recycle();

        // -- A normal map material will give the terrain a bit more detail.
        Material material = new Material();
        material.enableLighting(true);
        material.useVertexColors(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());
        try {
            Texture groundTexture = new Texture("ground", R.drawable.ground);
            groundTexture.setInfluence(.5f);
            material.addTexture(groundTexture);
            material.addTexture(new NormalMapTexture("groundNormalMap", R.drawable.groundnor));
            material.setColorInfluence(0);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }

        //
        // -- Blend the texture with the vertex colors
        //
        material.setColorInfluence(.5f);
        mTerrain.setY(-80);
        mTerrain.setMaterial(material);

        mRenderer.getCurrentScene().addChild(mTerrain);
    }

    private void setSkybox() {
        try {
            mRenderer.getCurrentScene().setSkybox(R.drawable.posx, R.drawable.negx, R.drawable.posy, R.drawable.negy, R.drawable.posz, R.drawable.negz);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void makeFixedObjects() {
        try {
            // Make fixed objects
            Object3D obj = null;
            LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                    mRenderer.getTextureManager(), R.raw.destroyer_obj);
            loader.parse();
            obj = loader.getParsedObject();
            obj.enableLookAt();
            obj.setScale(20f);

            Material objMaterial = new Material();
            objMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            //objMaterial.setColorInfluence(0);
            objMaterial.enableLighting(true);
            objMaterial.setColor(0xff444444);

            obj.setMaterial(objMaterial);
            mRenderer.getCurrentScene().addChild(obj);

//            LoaderAWD loader = new LoaderAWD(mContext.getResources(), mRenderer.getTextureManager(),
//                    R.raw.space_cruiser);
//            loader.parse();
//
//            Material cruiserMaterial = new Material();
//            cruiserMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
//            cruiserMaterial.setColorInfluence(0);
//            cruiserMaterial.enableLighting(true);
//            cruiserMaterial.addTexture(new Texture("spaceCruiserTex", R.drawable.space_cruiser_4_color_1));
//
//            Object3D obj = loader.getParsedObject();
//            obj.setMaterial(cruiserMaterial);
//            obj.setScale(10);
//            obj.setZ(-6);
//            obj.setY(1);
//            mRenderer.getCurrentScene().addChild(obj);

            mDestroyer = new Destroyer(obj);
            mDestroyer.initBoundingBox();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void makeMothership() {
        try {
            CatmullRomCurve3D path;

            LoaderAWD loader = new LoaderAWD(mContext.getResources(),
                    mRenderer.getTextureManager(), R.raw.capital);
            loader.parse();

            Material capitalMaterial = new Material();
            capitalMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            capitalMaterial.setColorInfluence(0);
            capitalMaterial.enableLighting(true);
            capitalMaterial.addTexture(new Texture("capitalTex", R.drawable.hullw));
            capitalMaterial.addTexture(new NormalMapTexture("capitalNormTex", R.drawable.hulln));

            mMothershipObj = loader.getParsedObject();
            mMothershipObj.setMaterial(capitalMaterial);
            mMothershipObj.setScale(5);
            mMothershipObj.enableLookAt();
            mRenderer.getCurrentScene().addChild(mMothershipObj);

            path = new CatmullRomCurve3D();
            path.addPoint(new Vector3(0, 15, 100));
            path.addPoint(new Vector3(-100, 15, 0));
            path.addPoint(new Vector3(0, 15, -100));
            path.addPoint(new Vector3(100, 15, 0));
            path.isClosedCurve(true);

            mMothershipAnim = new SplineTranslateAnimation3D(path);
            mMothershipAnim.setDurationMilliseconds(ANIM_DURATION);
            mMothershipAnim.setRepeatMode(Animation.RepeatMode.INFINITE);
            mMothershipAnim.setOrientToPath(true);
            mMothershipAnim.setTransformable3D(mMothershipObj);
            mRenderer.getCurrentScene().registerAnimation(mMothershipAnim);
            mMothershipAnim.play();

            mMothership = new Mothership(mMothershipObj);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }  // End of makeMothership()

    private void makeFriendlyFighter() {

    }

    private Object3D[] mObjectCache = new Object3D[7];
    private EnemySpaceship makeEnemy(int type, double target_offset, double max_speed, long delay) {
        EnemySpaceship enemy = null;
        Random rand = new Random();
        int x_offset = rand.nextInt(100) + 50;
        int y_offset = rand.nextInt(70) - 20;
        int z_offset = rand.nextInt(100) - 50;
        // Clone object from cache if possible
        if(type > -1 && type < mObjectCache.length
                && mObjectCache[type] != null) {
            Object3D obj = mObjectCache[type].clone();
            mRenderer.getCurrentScene().addChild(obj);
            enemy = new EnemySpaceship(x_offset, 15+y_offset, z_offset,
                    target_offset, max_speed, delay, obj);
            return enemy;
        }

        // Or load object model from file
        try {
            Object3D obj = null;

            Material material;
            material = new Material();
            material.setDiffuseMethod(new DiffuseMethod.Lambert());
            material.setColorInfluence(0);
            material.enableLighting(true);

            // Load enemy spaceship
            if(type == EnemySpaceship.ENEMY_TYPE_SPACESHIP1) {
                LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.spaceship1_obj);
                loader.parse();
                obj = loader.getParsedObject();
                obj.setScale(1.2);
            } else if(type == EnemySpaceship.ENEMY_TYPE_SPACESHIP2) {
                LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.spaceship2_obj);
                loader.parse();
                obj = loader.getParsedObject();
                obj.setScale(1.5);
            } else if(type == EnemySpaceship.ENEMY_TYPE_SPACESHIP3) {
                LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.spaceship3_obj);
                loader.parse();
                obj = loader.getParsedObject();
                obj.setScale(1.5);
            } else if(type == EnemySpaceship.ENEMY_TYPE_SPACESHIP4) {
                LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.spaceship4_obj);
                loader.parse();
                obj = loader.getParsedObject();
                obj.setScale(1.5);
            } else if(type == EnemySpaceship.ENEMY_TYPE_SPACESHIP5) {
                LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.spaceship5_obj);
                loader.parse();
                obj = loader.getParsedObject();
                obj.setScale(1.5);
            } else if(type == EnemySpaceship.ENEMY_TYPE_SPACESHIP6) {
                LoaderOBJ loader = new LoaderOBJ(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.spaceship6_obj);
                loader.parse();
                obj = loader.getParsedObject();
                obj.setScale(1.5);
            } else {
                LoaderAWD loader = new LoaderAWD(mContext.getResources(),
                        mRenderer.getTextureManager(), R.raw.dark_fighter);
                loader.parse();
                obj = loader.getParsedObject();
                material.addTexture(new Texture("darkFighterTex", R.drawable.dark_fighter_6_color));
            }

            obj.setMaterial(material);
            obj.enableLookAt();
            mRenderer.getCurrentScene().addChild(obj);
            enemy = new EnemySpaceship(0+x_offset, 15+y_offset, 0+z_offset,
                    target_offset, max_speed, delay, obj);
        } catch (Exception e) {
            e.printStackTrace();
            enemy = null;
        }
        return enemy;
    }  // End of makeEnemy()

    private void makeCamera() {
        mRenderer.getCurrentCamera().setFarPlane(500);
        mRenderer.getCurrentScene().setBackgroundColor(0xdddddd);

        // Make camera
        CatmullRomCurve3D path = new CatmullRomCurve3D();
        path.addPoint(new Vector3(0, 15+CAM_OFFSET, 100));
        path.addPoint(new Vector3(-100+CAM_OFFSET, 15, 0));
        path.addPoint(new Vector3(0, 15-CAM_OFFSET, -100));
        path.addPoint(new Vector3(100+CAM_OFFSET, 15, 0));
        path.isClosedCurve(true);

        SplineTranslateAnimation3D camAnim = new SplineTranslateAnimation3D(path);
        camAnim.setDurationMilliseconds(ANIM_DURATION);
        camAnim.setRepeatMode(Animation.RepeatMode.INFINITE);
        //mCamAnim.setOrientToPath(true);       // Do not use this. This prevents head tracking by accel/gyro
        camAnim.setTransformable3D(mRenderer.getCurrentCamera());
        mRenderer.getCurrentScene().registerAnimation(camAnim);
        camAnim.play();

        // Make camera chasing box
        mCameraBox = new Cube(0.1f);
        Material boxMaterial = new Material();
        mCameraBox.setMaterial(boxMaterial);
        mCameraBox.setColor(0xff000000);
        mRenderer.getCurrentScene().addChild(mCameraBox);

        path = new CatmullRomCurve3D();
        path.addPoint(new Vector3(0, 15+CAM_OFFSET, 100));
        path.addPoint(new Vector3(-100+CAM_OFFSET, 15, 0));
        path.addPoint(new Vector3(0, 15-CAM_OFFSET, -100));
        path.addPoint(new Vector3(100+CAM_OFFSET, 15, 0));
        path.isClosedCurve(true);

        mCameraAnim = new SplineTranslateAnimation3D(path);
        mCameraAnim.setDurationMilliseconds(120000);
        mCameraAnim.setRepeatMode(Animation.RepeatMode.INFINITE);
        //mCameraAnim.setOrientToPath(true);       // Do not use this. This stops head tracking of accel/gyro
        mCameraAnim.setTransformable3D(mCameraBox);
        mRenderer.getCurrentScene().registerAnimation(mCameraAnim);
        mCameraAnim.play();
    }  // End of makeCamera()

    private void setLights() {
        DirectionalLight light = new DirectionalLight(-1f, 1f, -1f);
        light.setPower(.8f);
        mRenderer.getCurrentScene().addLight(light);

        light = new DirectionalLight(1f, 1f, 1f);
        light.setPower(.8f);
        mRenderer.getCurrentScene().addLight(light);
    }

    private void initAudio() {
        cardboardAudioEngine =
                new CardboardAudioEngine(mContext.getAssets(), CardboardAudioEngine.RenderingQuality.HIGH);

        new Thread(
            new Runnable() {
                public void run() {
                    if(mMothershipObj == null)
                        return;
                    cardboardAudioEngine.preloadSoundFile("spaceship.wav");
                    spaceShipSoundId = cardboardAudioEngine.createSoundObject("spaceship.wav");
                    cardboardAudioEngine.setSoundObjectPosition(spaceShipSoundId,
                            (float)mMothershipObj.getX(), (float)mMothershipObj.getY(), (float)mMothershipObj.getZ()
                    );
                    cardboardAudioEngine.playSound(spaceShipSoundId, true);

                    cardboardAudioEngine.preloadSoundFile("sonar.wav");
                    sonarSoundId = cardboardAudioEngine.createSoundObject("sonar.wav");
                    cardboardAudioEngine.setSoundObjectPosition(sonarSoundId,
                            (float)mMothershipObj.getX(), (float)mMothershipObj.getY(), (float)mMothershipObj.getZ()
                    );
                    cardboardAudioEngine.playSound(sonarSoundId, true);
                }
            })
            .start();
    }

    private void prepareMissile() {
        // Make object model of missile.
        // When user fire missile, this object will be cloned
        try {
            mMissileObj = new Cube(0.12f);
            Material boxMaterial = new Material();
            boxMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            //material.setColorInfluence(0);
            boxMaterial.enableLighting(false);
            //mMissileObj.setMaterial(new DiffuseMaterial());
            mMissileObj.setMaterial(boxMaterial);
            mMissileObj.setX(0); mMissileObj.setY(0); mMissileObj.setZ(0);
            //mMissileObj.setRenderChildrenAsBatch(true);
            mRenderer.getCurrentScene().addChild(mMissileObj);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void attackMothership(double x, double y, double z, int from) {
        // Make target vector
        Vector3 targetVec = new Vector3(mMothershipObj.getX(), mMothershipObj.getY(), mMothershipObj.getZ());
        double distance = targetVec.distanceTo(x, y, z);
        targetVec.subtract(x, y, z);
        targetVec.normalize();
        if(distance > 50) {
            targetVec.multiply(30);
        } else {
            targetVec.multiply(3);
        }

        // clone object and add to scene
        Object3D missileObj = mMissileObj.clone();
        missileObj.setX(x+targetVec.x);
        missileObj.setY(y+targetVec.y);
        missileObj.setZ(z+targetVec.z);
        missileObj.setColor(ENEMY_MISSILE_COLOR);
        mRenderer.getCurrentScene().addChild(missileObj);
        //mMissileObj.addChild(missileObj);
        // make Missile instance and set location and target
        Missile missile = new Missile(x+targetVec.x, y+targetVec.y, z+targetVec.z,
                from, missileObj);
        targetVec.multiply((distance+10)/3);
        missile.setTarget(mMothershipObj.getX(), mMothershipObj.getY(), mMothershipObj.getZ());
        if(distance > 50) {
            missile.setSpeed(0.75);
        }
        synchronized (mMissiles) {
            mMissiles.add(missile);
        }
    }

    private void attackDestroyer(double x, double y, double z) {
        // Make target vector
        Random rand = new Random();
        Vector3 targetVec = new Vector3(mDestroyer.object.getX()+rand.nextInt(20),
                mDestroyer.object.getY()+rand.nextInt(20),
                mDestroyer.object.getZ()+rand.nextInt(20));
        targetVec.subtract(x, y, z);
        targetVec.normalize();
        targetVec.multiply(3);
        // clone object and add to scene
        Object3D missileObj = mMissileObj.clone();
        missileObj.setX(x+targetVec.x);
        missileObj.setY(y+targetVec.y);
        missileObj.setZ(z+targetVec.z);
        missileObj.setColor(FRIENDLY_MISSILE_COLOR);
        mRenderer.getCurrentScene().addChild(missileObj);
        //mMissileObj.addChild(missileObj);
        // make Missile instance and set location and target
        Missile missile = new Missile(x+targetVec.x, y+targetVec.y, z+targetVec.z,
                Missile.FROM_MOTHERSHIP, missileObj);
        targetVec.multiply(20);
        missile.setTarget(targetVec.x, targetVec.y, targetVec.z);
        synchronized (mMissiles) {
            mMissiles.add(missile);
        }
    }

    private void endGame() {
        // TODO: end game
        mGameStatus = GAME_STATUS_END;
    }

}
