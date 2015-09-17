package com.ecloud.pulltozoomview;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Author:    ZhuWenWu
 * Version    V1.0
 * Date:      2014/11/7  14:18.
 * Description:
 * Modification  History:
 * Date         	Author        		Version        	Description
 * -----------------------------------------------------------------------------------
 * 2014/11/7        ZhuWenWu            1.0                    1.0
 * Why & What is modified:
 */
public abstract class PullToZoomBase<T extends View> extends LinearLayout implements IPullToZoom<T> {

    public static final int PULL_TO_REFRESH = 1;
    public static final int RELEASE_TO_REFRESH = 2;
    public static final int REFRESHING = 3;

    public static final int THRESHOLDER_REFRESH = 100;//dp
    private static final float FRICTION = 2.0f;
    protected T mRootView;
    protected View mHeaderView;//头部View
    protected View mZoomView;//缩放拉伸View

    protected int mScreenHeight;
    protected int mScreenWidth;

    private boolean isZoomEnabled = true;
    private boolean isParallax = true;
    private boolean isZooming = false;
    private boolean isHideHeader = false;

    private int mTouchSlop;
    private boolean mIsBeingDragged = false;
    private float mLastMotionY;
    private float mLastMotionX;
    private float mInitialMotionY;
    private float mInitialMotionX;
    private OnPullZoomListener onPullZoomListener;
    private int mThresholderRefresh = THRESHOLDER_REFRESH;
    private int status = PULL_TO_REFRESH;

    protected int scrollEnd = 0;

    public PullToZoomBase(Context context) {
        this(context, null);
    }

    public PullToZoomBase(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs);
    }

    public int dp2px(Context context,int dp){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    private void init(Context context, AttributeSet attrs) {
        setGravity(Gravity.CENTER);

        ViewConfiguration config = ViewConfiguration.get(context);
        mTouchSlop = config.getScaledTouchSlop();

        DisplayMetrics localDisplayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(localDisplayMetrics);
        mScreenHeight = localDisplayMetrics.heightPixels;
        mScreenWidth = localDisplayMetrics.widthPixels;

        // Refreshable View
        // By passing the attrs, we can add ListView/GridView params via XML
        mRootView = createRootView(context, attrs);

        if (attrs != null) {
            LayoutInflater mLayoutInflater = LayoutInflater.from(getContext());
            //初始化状态View
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.PullToZoomView);

            int zoomViewResId = a.getResourceId(R.styleable.PullToZoomView_zoomView, 0);
            if (zoomViewResId > 0) {
                mZoomView = mLayoutInflater.inflate(zoomViewResId, null, false);
            }

            int headerViewResId = a.getResourceId(R.styleable.PullToZoomView_headerView, 0);
            if (headerViewResId > 0) {
                mHeaderView = mLayoutInflater.inflate(headerViewResId, null, false);
            }

            isParallax = a.getBoolean(R.styleable.PullToZoomView_isHeaderParallax, true);

            // Let the derivative classes have a go at handling attributes, then
            // recycle them...
            handleStyledAttributes(a);
            a.recycle();
        }
        addView(mRootView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void setOnPullZoomListener(OnPullZoomListener onPullZoomListener) {
        this.onPullZoomListener = onPullZoomListener;
    }

    @Override
    public T getPullRootView() {
        return mRootView;
    }

    @Override
    public View getZoomView() {
        return mZoomView;
    }

    @Override
    public View getHeaderView() {
        return mHeaderView;
    }

    @Override
    public boolean isPullToZoomEnabled() {
        return isZoomEnabled && status != REFRESHING;
    }

    @Override
    public boolean isZooming() {
        return isZooming;
    }

    @Override
    public boolean isParallax() {
        return isParallax;
    }

    @Override
    public boolean isHideHeader() {
        return isHideHeader;
    }

    public void setZoomEnabled(boolean isZoomEnabled) {
        this.isZoomEnabled = isZoomEnabled;
    }

    public void setParallax(boolean isParallax) {
        this.isParallax = isParallax;
    }

    public void setHideHeader(boolean isHideHeader) {//header显示才能Zoom
        this.isHideHeader = isHideHeader;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isPullToZoomEnabled() || isHideHeader()) {
            return false;
        }

        final int action = event.getAction();

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mIsBeingDragged = false;
            return false;
        }

        if (action != MotionEvent.ACTION_DOWN && mIsBeingDragged) {
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (isReadyForPullStart()) {
                    final float y = event.getY(), x = event.getX();
                    final float diff, oppositeDiff, absDiff;

                    // We need to use the correct values, based on scroll
                    // direction
                    diff = y - mLastMotionY;
                    oppositeDiff = x - mLastMotionX;
                    absDiff = Math.abs(diff);

                    if (absDiff > mTouchSlop && absDiff > Math.abs(oppositeDiff)) {
                        if (diff >= 1f && isReadyForPullStart()) {
                            mLastMotionY = y;
                            mLastMotionX = x;
                            mIsBeingDragged = true;
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                if (isReadyForPullStart()) {
                    mLastMotionY = mInitialMotionY = event.getY();
                    mLastMotionX = mInitialMotionX = event.getX();
                    mIsBeingDragged = false;
                }
                break;
            }
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isPullToZoomEnabled() || isHideHeader()) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN && event.getEdgeFlags() != 0) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                if (mIsBeingDragged) {
                    mLastMotionY = event.getY();
                    mLastMotionX = event.getX();
                    pullEvent();
                    isZooming = true;
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                if (isReadyForPullStart()) {
                    mLastMotionY = mInitialMotionY = event.getY();
                    mLastMotionX = mInitialMotionX = event.getX();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    // If we're already refreshing, just scroll back to the top
                    if(isZooming() && isReadyToRefresh()){
                        scrollEnd = dp2px(getContext(),mThresholderRefresh);
                        smoothScrollToTop();
                        if(onRefreshingListener != null){
                            onRefreshingListener.onRefreshing();
                        }

                        return true;
                    }

                    else if(!isReadyToRefresh()){
                        onRefreshComplete();
                        return true;
                    }

                    if (isZooming() && !isReadyToRefresh()) {
                        scrollEnd = 0;
                        smoothScrollToTop();
                        if (onPullZoomListener != null) {
                            onPullZoomListener.onPullZoomEnd();
                        }
                        isZooming = false;
                        return true;
                    }
                    return true;
                }else{
                    onRefreshComplete();
                }
                break;
            }
        }
        return false;
    }

    public void onRefreshComplete(){
        scrollEnd = 0;
        smoothScrollToTop();
        if (onPullZoomListener != null) {
            onPullZoomListener.onPullZoomEnd();
        }
        isZooming = false;
    }

    private boolean isReadyToRefresh(){
        return status == RELEASE_TO_REFRESH;
    }

    public interface OnRefreshingListener{
        void onRefreshing();
    }

    private OnRefreshingListener onRefreshingListener;

    public void setOnRefreshingListener(OnRefreshingListener onRefreshingListener) {
        this.onRefreshingListener = onRefreshingListener;
    }

    private void pullEvent() {
        final int newScrollValue;
        final float initialMotionValue, lastMotionValue;

        initialMotionValue = mInitialMotionY;
        lastMotionValue = mLastMotionY;

        if(status != REFRESHING){
            if(mLastMotionY >= dp2px(getContext(),mThresholderRefresh)){
                status = RELEASE_TO_REFRESH;
            }else{
                status = PULL_TO_REFRESH;
            }
        }

        doStatusUi();

        newScrollValue = Math.round(Math.min(initialMotionValue - lastMotionValue, 0) / FRICTION);

        pullHeaderToZoom(newScrollValue);
        if (onPullZoomListener != null) {
            onPullZoomListener.onPullZooming(newScrollValue);
        }
    }

    private void doStatusUi(){
        switch (status){
            case PULL_TO_REFRESH:
                System.out.println("下拉刷新...");
                break;
            case RELEASE_TO_REFRESH:
                System.out.println("释放开始刷新...");
                break;
            case REFRESHING:
                break;
        }
    }


    protected abstract void pullHeaderToZoom(int newScrollValue);

    public abstract void setHeaderView(View headerView);

    public abstract void setZoomView(View zoomView);

    protected abstract T createRootView(Context context, AttributeSet attrs);

    protected abstract void smoothScrollToTop();

    protected abstract boolean isReadyForPullStart();

    public interface OnPullZoomListener {
        public void onPullZooming(int newScrollValue);

        public void onPullZoomEnd();
    }
}
