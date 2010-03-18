/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.replica.replicaisland;

import android.os.SystemClock;
import android.view.KeyEvent;


/** 
 * The GameThread contains the main loop for the game engine logic.  It invokes the game graph,
 * manages synchronization of input events, and handles the draw queue swap with the rendering
 * thread.
 */
public class GameThread implements Runnable {
    private long mLastTime;
    private float mLastMotionX;
    private float mLastMotionY;
    private int mLastTouchX;
    private int mLastTouchY;
    private boolean mTouchReleased;
    private boolean mClickUp;
    private boolean mClickDown;
    private float mOrientationX;
    private float mOrientationY;
    private float mOrientationZ;
    private boolean mOrientationChanged;
    private boolean mKeyLeft;
    private boolean mKeyRight;
    private boolean mKeyDown;
    private boolean mKeyUp;
    private boolean mKeyTouch;
    private boolean mKeyClick;
    private boolean mKeyInputReceived;
    private boolean mKeyLeftUp;
    private boolean mKeyRightUp;
    private boolean mKeyDownUp;
    private boolean mKeyUpUp;
    private boolean mKeyTouchUp;
    private boolean mKeyClickUp;

    private ObjectManager mGameRoot;
    private GameRenderer mRenderer;
    private Object mInputLock;
    private Object mPauseLock;
    private boolean mFinished;
    private boolean mPaused = false;
    private int mProfileFrames;
    private long mProfileTime;
    private static final float PROFILE_REPORT_DELAY = 3.0f;
    
    public GameThread(GameRenderer renderer) {
        mLastTime = SystemClock.uptimeMillis();
        mRenderer = renderer;
        mInputLock = new Object();
        mPauseLock = new Object();
        mFinished = false;
        mPaused = false;
    }

    public void run() {
        mLastTime = SystemClock.uptimeMillis();
        mFinished = false;
        while (!mFinished) {
            if (mGameRoot != null) {
                mRenderer.waitDrawingComplete();
                
                final long time = SystemClock.uptimeMillis();
                final long timeDelta = time - mLastTime;
                long finalDelta = timeDelta;
                if (timeDelta > 12) {
                    float secondsDelta = (time - mLastTime) * 0.001f;
                    if (secondsDelta > 0.1f) {
                        secondsDelta = 0.1f;
                    }
                    mLastTime = time;
    
                    synchronized (mInputLock) {
                        
                       
                        if (mKeyInputReceived) {
                            BaseObject.sSystemRegistry.inputSystem.keyUp(
                                    mKeyLeftUp,
                                    mKeyRightUp,
                                    mKeyUpUp,
                                    mKeyDownUp,
                                    mKeyTouchUp,
                                    mKeyClickUp);
                            BaseObject.sSystemRegistry.inputSystem.keyDown(
                                    mKeyLeft,
                                    mKeyRight,
                                    mKeyUp,
                                    mKeyDown,
                                    mKeyTouch,
                                    mKeyClick);
                            mKeyInputReceived = false;
                            mKeyLeft = false;
                            mKeyRight = false;
                            mKeyUp = false;
                            mKeyDown = false;
                            mKeyTouch = false;
                            mKeyClick = false;
                            mKeyLeftUp = false;
                            mKeyRightUp = false;
                            mKeyUpUp = false;
                            mKeyDownUp = false;
                            mKeyTouchUp = false;
                            mKeyClickUp = false;
                        }
                        
                        if (mLastMotionX != 0 || mLastMotionY != 0) {
                            BaseObject.sSystemRegistry.inputSystem
                                            .roll(mLastMotionX, mLastMotionY);
                            mLastMotionX = 0;
                            mLastMotionY = 0;
                        }
                        if (mLastTouchX != 0 || mLastTouchY != 0) {
                            BaseObject.sSystemRegistry.inputSystem
                                            .touch(mLastTouchX, mLastTouchY, mTouchReleased);
                            mLastTouchX = 0;
                            mLastTouchY = 0;
                            mTouchReleased = false;
                        }
                        
                        if (mClickDown) {
                            BaseObject.sSystemRegistry.inputSystem.clickDown();
                            mClickDown = false;
                        } 
                        if (mClickUp) {
                            BaseObject.sSystemRegistry.inputSystem.clickUp();
                            mClickUp = false;
                        }
                        
                        if (mOrientationChanged) {
                            BaseObject.sSystemRegistry.inputSystem.setOrientation(
                                    mOrientationX,
                                    mOrientationY,
                                    mOrientationZ);
                            mOrientationChanged = false;
                        }
                        
                        
                    }
    
                    mGameRoot.update(secondsDelta, null);
    
                    CameraSystem camera = mGameRoot.sSystemRegistry.cameraSystem;
                    float x = 0.0f;
                    float y = 0.0f;
                    if (camera != null) {
                    	x = camera.getFocusPositionX();
                    	y = camera.getFocusPositionY();
                    }
                    BaseObject.sSystemRegistry.renderSystem.swap(mRenderer, x, y);
                    
                    final long endTime = SystemClock.uptimeMillis();
                    
                    finalDelta = endTime - time;
                    
                    mProfileTime += finalDelta;
                    mProfileFrames++;
                    if (mProfileTime > PROFILE_REPORT_DELAY * 1000) {
                        final long averageFrameTime = mProfileTime / mProfileFrames;
                        DebugLog.d("Game Profile", "Average: " + averageFrameTime);
                        mProfileTime = 0;
                        mProfileFrames = 0;
                    }
                }
                // If the game logic completed in less than 16ms, that means it's running
                // faster than 60fps, which is our target frame rate.  In that case we should
                // yield to the rendering thread, at least for the remaining frame.
               
                if (finalDelta < 16) {
                    try {
                        Thread.sleep(16 - finalDelta);
                    } catch (InterruptedException e) {
                        // Interruptions here are no big deal.
                    }
                }
                
                synchronized(mPauseLock) {
                    if (mPaused) {
                    	SoundSystem sound = BaseObject.sSystemRegistry.soundSystem;
                    	if (sound != null) {
                    		sound.pauseAll();
                    		BaseObject.sSystemRegistry.inputSystem.releaseAllKeys();
                    	}
                        while (mPaused) {
                            try {
                            	mPauseLock.wait();
                            } catch (InterruptedException e) {
                                // No big deal if this wait is interrupted.
                            }
                        }
                    }
                }
            } 
        }
        // Make sure our dependence on the render system is cleaned up.
        BaseObject.sSystemRegistry.renderSystem.emptyQueues(mRenderer);
    }

    public void stopGame() {
    	synchronized (mPauseLock) {
            mPaused = false;
            mFinished = true;
            mPauseLock.notifyAll();
    	}
    }
    
    public void pauseGame() {
        synchronized (mPauseLock) {
            mPaused = true;
        }
    }

    public void resumeGame() {
        synchronized (mPauseLock) {
            mPaused = false;
            mPauseLock.notifyAll();
        }
    }
    
    public boolean getPaused() {
        return mPaused;
    }

    public void setGameRoot(ObjectManager gameRoot) {
        mGameRoot = gameRoot;
    }

    public void rollEvent(float f, float g) {
        synchronized (mInputLock) {
            mLastMotionX += f;
            mLastMotionY += g;
        }
    }
    
    public void clickEvent(boolean down) {
        synchronized (mInputLock) {
            if (down) {
                mClickDown = true;
            } else {
                mClickUp = true;
            }
        }
    }
    
    public void orientationEvent(float x, float y, float z) {
        synchronized (mInputLock) {
            mOrientationX = x;
            mOrientationY = y;
            mOrientationZ = z;
            mOrientationChanged = true;
        }
    }
    
    public void touchDownEvent(float x, float y) {
        synchronized (mInputLock) {
        	mTouchReleased = false;
            mLastTouchX = (int)x;
            mLastTouchY = (int)y;
        }
    }
    
    public void touchUpEvent(float x, float y) {
        synchronized (mInputLock) {
        	mTouchReleased = true;
            mLastTouchX = (int)x;
            mLastTouchY = (int)y;
        }
    }
    
    public boolean keydownEvent(int keycode) {
        boolean ateKey = true;
        synchronized (mInputLock) {
            switch (keycode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    mKeyLeft = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mKeyRight = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    mKeyUp = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    mKeyDown = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    mKeyClick = true;
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    mKeyTouch = true;
                    break;
                case KeyEvent.KEYCODE_B:
                	mKeyClick = true;
                    break;
                case KeyEvent.KEYCODE_A:
                	mKeyTouch = true;
                    break;
                default:
                    ateKey = false;
            }
            
            mKeyInputReceived = ateKey;
        }
        return ateKey;
    }
    
    public boolean keyupEvent(int keycode) {
        boolean ateKey = true;
        synchronized (mInputLock) {
            switch (keycode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    mKeyLeftUp = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mKeyRightUp = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    mKeyUpUp = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    mKeyDownUp = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    mKeyClickUp = true;
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    mKeyTouchUp = true;
                    break;
                case KeyEvent.KEYCODE_B:
                	mKeyClickUp = true;
                    break;
                case KeyEvent.KEYCODE_A:
                    mKeyTouchUp = true;
                    break;
                default:
                    ateKey = false;
            }            
            mKeyInputReceived = ateKey;
        }
        return ateKey;
    }
    
}