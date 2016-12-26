package com.brainy.iskin;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.widget.ImageView;

import java.lang.ref.SoftReference;
import java.util.ArrayList;


public class FasterAnimationsContainer{
    private class AnimationFrame{
        private int mResourceId;
        private int mDuration;
        AnimationFrame(int resourceId, int duration){
            mResourceId = resourceId;
            mDuration = duration;
        }
        public int getResourceId() {
            return mResourceId;
        }
        public int getDuration() {
            return mDuration;
        }
    }
    private FramesSequenceAnimation mFram;
    private ArrayList<AnimationFrame> mAnimationFrames; // list for all frames of animation
    private int mIndex; // index of current frame

    private boolean mShouldRun; // true if the animation should continue running. Used to stop the animation
    public boolean mIsRunning; // true if the animation prevents starting the animation twice
    private SoftReference<ImageView> mSoftReferenceImageView; // Used to prevent holding ImageView when it should be dead.
    private Handler mHandler; // Handler to communication with UIThread

    // Listeners
    private OnAnimationStoppedListener mOnAnimationStoppedListener;
    private OnAnimationFrameChangedListener mOnAnimationFrameChangedListener;

    private FasterAnimationsContainer(ImageView imageView) {
        init(imageView);
    };

    public FasterAnimationsContainer(ImageView imageView, int num) {
        init(imageView);
        getInstance(imageView, num);
    };

    // single instance procedures
    private FasterAnimationsContainer sInstance;
    private FasterAnimationsContainer sInstance2;
    private FasterAnimationsContainer sInstance3;

    public FasterAnimationsContainer getInstance(ImageView imageView, int num) {
        if(num==1) {
            sInstance2 = new FasterAnimationsContainer(imageView);
            return sInstance2;
        }
        else if(num==2){
            sInstance3 = new FasterAnimationsContainer(imageView);
            return sInstance3;
        }
        else {
            sInstance = new FasterAnimationsContainer(imageView);
            return sInstance;
        }
    }

    /**
     * initialize imageview and frames
     * @param imageView
     */
    public void init(ImageView imageView){
        mAnimationFrames = new ArrayList<AnimationFrame>();
        mSoftReferenceImageView = new SoftReference<ImageView>(imageView);

        mHandler = new Handler();
        if(mIsRunning == true){
            stop();
        }

        mShouldRun = false;
        mIsRunning = false;

        mIndex = -1;
    }

    /**
     * add a frame of animation
     * @param index index of animation
     * @param resId resource id of drawable
     * @param interval milliseconds
     */
    public void addFrame(int index, int resId, int interval){
        mAnimationFrames.add(index, new AnimationFrame(resId, interval));
    }

    /**
     * add a frame of animation
     * @param resId resource id of drawable
     * @param interval milliseconds
     */
    public void addFrame(int resId, int interval){
        mAnimationFrames.add(new AnimationFrame(resId, interval));
    }

    /**
     * add all frames of animation
     * @param /resId resource id of drawable
     * @param interval milliseconds
     */
    public void addAllFrames(int[] resIds, int interval){
        for(int resId : resIds){
            mAnimationFrames.add(new AnimationFrame(resId, interval));
        }
    }

    public void addNewAnim(int[] resIds, int interval){
        if(mIsRunning)stop();
        mIndex = 0;
        try { removeAllFrames(); }
        catch (Exception e) {}
        addAllFrames(resIds,interval);
        start();
    }
    /**
     * remove a frame with index
     * @param index index of animation
     */
    public void removeFrame(int index){
        mAnimationFrames.remove(index);
    }

    /**
     * clear all frames
     */
    public void removeAllFrames(){
        mAnimationFrames.clear();
    }

    /**
     * change a frame of animation
     * @param index index of animation
     * @param resId resource id of drawable
     * @param interval milliseconds
     */
    public void replaceFrame(int index, int resId, int interval){
        mAnimationFrames.set(index, new AnimationFrame(resId, interval));
    }

    public void replaceFrames(int[] resIds,int interval) {
        if(resIds.length>=mAnimationFrames.size()) {
            for(int i=0; i<mAnimationFrames.size(); i++) {
                replaceFrame(i,resIds[i],interval);
            }
            for(int i=mAnimationFrames.size(); i<resIds.length; i++) {
                addFrame(resIds[i],interval);
            }
        }
        else {
            for(int i=0; i<resIds.length; i++) {
                replaceFrame(i,resIds[i],interval);
            }
            for(int i=mAnimationFrames.size()-1; i>=resIds.length; i--) {
                removeFrame(i);
            }
        }

        mIndex=0;
    }

    private AnimationFrame getNext() {
        mIndex++;
        if (mIndex >= mAnimationFrames.size())
            mIndex = 0;
        return mAnimationFrames.get(mIndex);
    }

    /**
     * Listener of animation to detect stopped
     *
     */
    public interface OnAnimationStoppedListener{
        public void onAnimationStopped();
    }

    /**
     * Listener of animation to get index
     *
     */
    public interface OnAnimationFrameChangedListener{
        public void onAnimationFrameChanged(int index);
    }


    /**
     * set a listener for OnAnimationStoppedListener
     * @param listener OnAnimationStoppedListener
     */
    public void setOnAnimationStoppedListener(OnAnimationStoppedListener listener){
        mOnAnimationStoppedListener = listener;
    }

    /**
     * set a listener for OnAnimationFrameChangedListener
     * @param listener OnAnimationFrameChangedListener
     */
    public void setOnAnimationFrameChangedListener(OnAnimationFrameChangedListener listener){
        mOnAnimationFrameChangedListener = listener;
    }

    /**
     * Starts the animation
     */
    public synchronized void start() {
        mShouldRun = true;
        if (mIsRunning)
            return;
        mFram = new FramesSequenceAnimation();
        mHandler.post(mFram);
    }

    /**
     * Stops the animation
     */
    public synchronized void stop() {
        mShouldRun = false;
        // TODO
        mHandler = null;
        mHandler.removeCallbacks(mFram);

    }

    private class FramesSequenceAnimation implements Runnable{

        @Override
        public void run() {
            ImageView imageView = mSoftReferenceImageView.get();
            if (!mShouldRun || imageView == null) {
                mIsRunning = false;
                if (mOnAnimationStoppedListener != null) {
                    mOnAnimationStoppedListener.onAnimationStopped();
                }
                return;
            }
            mIsRunning = true;

            if (imageView.isShown()) {
                AnimationFrame frame = getNext();
                GetImageDrawableTask task = new GetImageDrawableTask(imageView);
                task.execute(frame.getResourceId());
                // TODO postDelayed after onPostExecute
                mHandler.postDelayed(this, frame.getDuration());
            }
        }
    }

    private class GetImageDrawableTask extends AsyncTask<Integer, Void, Drawable> {

        private ImageView mImageView;

        public GetImageDrawableTask(ImageView imageView) {
            mImageView = imageView;
        }

        @Override
        protected Drawable doInBackground(Integer... params) {

            //return mImageView.getContext().getResources().getDrawable(params[0]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return mImageView.getContext().getResources().getDrawable(params[0], mImageView.getContext().getTheme());
            } else {
                return mImageView.getContext().getResources().getDrawable(params[0]);
            }
        }

        @Override
        protected void onPostExecute(Drawable result) {
            super.onPostExecute(result);
            if(result!=null) mImageView.setImageDrawable(result);
            if (mOnAnimationFrameChangedListener != null)
                mOnAnimationFrameChangedListener.onAnimationFrameChanged(mIndex);
        }

    }
}