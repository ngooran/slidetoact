package com.ncorti.slidetoact

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.support.graphics.drawable.VectorDrawableCompat
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.util.Xml
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.OvershootInterpolator
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 *  Class representing the custom view, SlideToActView.
 *
 *  SlideToActView is an elegant material designed slider, that enrich your app
 *  with a "Slide-to-unlock" like widget.
 *
 *  @author cortinico
 */
class SlideToActView(context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int) : View(context, attrs, defStyleAttr) {

    constructor(context: Context) : this(context, null, R.styleable.SlideToActViewTheme_slideToActViewStyle)
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, R.styleable.SlideToActViewTheme_slideToActViewStyle)

    /* -------------------- LAYOUT BOUNDS -------------------- */

    private var mDesiredSliderHeightDp: Float = 72F
    private var mDesiredSliderWidthDp: Float = 280F
    private var mDesiredSliderHeight: Int = 0
    private var mDesiredSliderWidth: Int = 0

    /* -------------------- MEMBERS -------------------- */

    /** Height of the drawing area */
    private var mAreaHeight: Int = 0
    /** Width of the drawing area */
    private var mAreaWidth: Int = 0
    /** Actual Width of the drawing area, used for animations */
    private var mActualAreaWidth: Int = 0
    /** Border Radius, default to mAreaHeight/2, -1 when not initialized */
    private var mBorderRadius: Int = -1
    /** Margin of the cursor from the outer area */
    private var mActualAreaMargin: Int
    private val mOriginAreaMargin: Int

    /** Text message */
    var text: CharSequence = ""
        set(value) {
            field = value
            invalidate()
        }

    /** Typeface for the text field */
    var typeFace = Typeface.NORMAL
        set(value) {
            field = value
            mTextPaint.typeface = Typeface.create(Typeface.DEFAULT, value)
            invalidate()
        }

    /** Size for the text message */
    var mTextSize = 16
        set(value) {
            field = value
            mTextPaint.textSize = value.toFloat()
            invalidate()
        }

    private var mTextYPosition = -1f
    private var mTextXPosition = -1f

    /** Outer color used by the slider (primary) */
    var outerColor: Int = 0
        set(value) {
            field = value
            mOuterPaint.color = value
            mDrawableArrow.setTint(value)
            invalidate()
        }

    /** Inner color used by the slider (secondary, icon and border) */
    var innerColor: Int = 0
        set(value) {
            field = value
            mInnerPaint.color = value
            mTextPaint.color = value
            mDrawableRight.setTint(value)
            invalidate()
        }

    /** Slider cursor position (between 0 and (`reaWidth - mAreaHeight)) */
    private var mPosition: Int = 0
        set(value) {
            field = value
            if (mAreaWidth - mAreaHeight == 0) {
                // Avoid 0 division
                mPositionPerc = 0f
                mPositionPercInv = 1f
                return
            }
            mPositionPerc = value.toFloat() / (mAreaWidth - mAreaHeight).toFloat()
            mPositionPercInv = 1 - value.toFloat() / (mAreaWidth - mAreaHeight).toFloat()
        }
    /** Slider cursor position in percentage (between 0f and 1f) */
    private var mPositionPerc: Float = 0f
    /** 1/mPositionPerc */
    private var mPositionPercInv: Float = 1f

    /* -------------------- ICONS -------------------- */

    private val mIconMargin: Int
    /** Margin for Arrow Icon */
    private var mArrowMargin: Int
    /** Current angle for Arrow Icon */
    private var mArrowAngle: Float = 0f
    /** Margin for Tick Icon */
    private var mTickMargin: Int

    /** Arrow drawable */
    private val mDrawableArrow: VectorDrawableCompat

    /** Arrow drawable */
    private val mDrawableRight: VectorDrawableCompat

    /** Tick drawable, is actually an AnimatedVectorDrawable */
    private val mDrawableTick: Drawable
    private var mFlagDrawTick: Boolean = false

    /** The icon for the drawable */
    private var mIcon: Int = R.drawable.ic_arrow

    /** The icon for the right drawable */
    private var mRightIcon: Int = R.drawable.ic_unlocked

    /* -------------------- PAINT & DRAW -------------------- */
    /** Paint used for outer elements */
    private val mOuterPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Paint used for inner elements */
    private val mInnerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Paint used for text elements */
    private val mTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Inner rectangle (used for arrow rotation) */
    private var mInnerRect: RectF
    /** Outer rectangle (used for area drawing) */
    private var mOuterRect: RectF
    /** Grace value, when mPositionPerc > mGraceValue slider will perform the 'complete' operations */
    private val mGraceValue: Float = 0.8F

    /** Last X coordinate for the touch event */
    private var mLastX: Float = 0F
    /** Flag to understand if user is moving the slider cursor */
    private var mFlagMoving: Boolean = false

    /** Private flag to check if the slide gesture have been completed */
    private var mIsCompleted = false

    /** Private flag to check if the slider is still working and showing animations */
    private var mIsAnimating = false

    /** Public flag to lock the slider */
    var isLocked = false

    private var mIsStartedOnce = false

    /** Public Slide event listeners */
    var onSlideToActAnimationEventListener: OnSlideToActAnimationEventListener? = null
    var onSlideCompleteListener: OnSlideCompleteListener? = null
    var onSlideResetListener: OnSlideResetListener? = null
    var onSlideStartedListener: OnSlideStartedListener? = null

    init {
        val actualOuterColor : Int
        val actualInnerColor : Int

        val layoutAttrs: TypedArray = context.theme.obtainStyledAttributes(attrs,
            R.styleable.SlideToActView, defStyleAttr, R.style.SlideToActView)
        try {
            mDesiredSliderHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mDesiredSliderHeightDp, resources.displayMetrics).toInt()
            mDesiredSliderWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mDesiredSliderWidthDp, resources.displayMetrics).toInt()
            mDesiredSliderHeight = layoutAttrs.getDimensionPixelSize(R.styleable.SlideToActView_slider_height, mDesiredSliderHeight)

            mBorderRadius = layoutAttrs.getDimensionPixelSize(R.styleable.SlideToActView_border_radius, -1)

            val defaultOuter = ContextCompat.getColor(this.context, R.color.defaultAccent)
            val defaultInner = ContextCompat.getColor(this.context, R.color.white)
            actualOuterColor = layoutAttrs.getColor(R.styleable.SlideToActView_outer_color, defaultOuter)
            actualInnerColor = layoutAttrs.getColor(R.styleable.SlideToActView_inner_color, defaultInner)
            text = layoutAttrs.getString(R.styleable.SlideToActView_text)
            typeFace = layoutAttrs.getInt(R.styleable.SlideToActView_text_style, 0)

            isLocked = layoutAttrs.getBoolean(R.styleable.SlideToActView_slider_locked, false)

            mTextSize = layoutAttrs.getDimensionPixelSize(R.styleable.SlideToActView_text_size, resources.getDimensionPixelSize(R.dimen.default_text_size))
            mOriginAreaMargin = layoutAttrs.getDimensionPixelSize(R.styleable.SlideToActView_area_margin, resources.getDimensionPixelSize(R.dimen.default_area_margin))
            mActualAreaMargin = mOriginAreaMargin

            mIcon = layoutAttrs.getResourceId(R.styleable.SlideToActView_slider_icon, R.drawable.ic_arrow)
            mRightIcon = layoutAttrs.getResourceId(R.styleable.SlideToActView_slider_right_icon, R.drawable.ic_unlocked)
        } finally {
            layoutAttrs.recycle()
        }

        mInnerRect = RectF((mActualAreaMargin + mPosition).toFloat(), mActualAreaMargin.toFloat(),
            (mAreaHeight + mPosition).toFloat() - mActualAreaMargin.toFloat(),
            mAreaHeight.toFloat() - mActualAreaMargin.toFloat())

        mOuterRect = RectF(mActualAreaWidth.toFloat(), 0f, mAreaWidth.toFloat() - mActualAreaWidth.toFloat(), mAreaHeight.toFloat())

        mDrawableArrow = parseVectorDrawableCompat(context.resources, mIcon, context.theme)
        mDrawableRight = parseVectorDrawableCompat(context.resources, mRightIcon, context.theme)

        // Due to bug in the AVD implementation in the support library, we use it only for API < 21
        mDrawableTick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.resources.getDrawable(R.drawable.animated_ic_check, context.theme) as AnimatedVectorDrawable
        } else {
            AnimatedVectorDrawableCompat.create(context, R.drawable.animated_ic_check)!!
        }

        mTextPaint.textAlign = Paint.Align.CENTER
        mTextPaint.textSize = mTextSize.toFloat()

        outerColor = actualOuterColor
        innerColor = actualInnerColor

        mIconMargin = context.resources.getDimensionPixelSize(R.dimen.default_icon_margin)
        mArrowMargin = mIconMargin
        mTickMargin = mIconMargin

        // This outline provider force removal of shadow
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = SlideToActOutlineProvider()
        }
    }

    private fun parseVectorDrawableCompat(res: Resources, resId: Int, theme: Resources.Theme): VectorDrawableCompat {
        val parser = res.getXml(resId)
        val attrs = Xml.asAttributeSet(parser)
        var type: Int = parser.next()
        while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            type = parser.next()
        }
        if (type != XmlPullParser.START_TAG) {
            throw XmlPullParserException("No start tag found")
        }
        return VectorDrawableCompat.createFromXmlInner(res, parser, attrs, theme)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val width: Int

        width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> Math.min(mDesiredSliderWidth, widthSize)
            MeasureSpec.UNSPECIFIED -> mDesiredSliderWidth
            else -> mDesiredSliderWidth
        }
        setMeasuredDimension(width, mDesiredSliderHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mAreaWidth = w
        mAreaHeight = h
        if (mBorderRadius == -1) // Round if not set up
            mBorderRadius = h / 2

        // Text horizontal/vertical positioning (both centered)
        mTextXPosition = mAreaWidth.toFloat() / 2
        mTextYPosition = (mAreaHeight.toFloat() / 2) - (mTextPaint.descent() + mTextPaint.ascent()) / 2
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) return

        // Outer area
        mOuterRect.set(mActualAreaWidth.toFloat(), 0f, mAreaWidth.toFloat() - mActualAreaWidth.toFloat(), mAreaHeight.toFloat())
        canvas.drawRoundRect(mOuterRect, mBorderRadius.toFloat(), mBorderRadius.toFloat(), mOuterPaint)

        // Inner Cursor
        // ratio is used to compute the proper border radius for the inner rect (see #8).
        val ratio = (mAreaHeight - 2 * mActualAreaMargin).toFloat() / mAreaHeight.toFloat()
        mInnerRect.set((mActualAreaMargin + mPosition).toFloat(),
                mActualAreaMargin.toFloat(),
                (mAreaHeight + mPosition).toFloat() - mActualAreaMargin.toFloat(),
                mAreaHeight.toFloat() - mActualAreaMargin.toFloat())
        canvas.drawRoundRect(mInnerRect, mBorderRadius.toFloat() * ratio, mBorderRadius.toFloat() * ratio, mInnerPaint)

        // Text alpha
        mTextPaint.alpha = (255 * mPositionPercInv).toInt()

        // Vertical + Horizontal centering
        canvas.drawText(text.toString(), mTextXPosition, mTextYPosition, mTextPaint)

        // Right Icon
        mDrawableRight.setBounds((mOuterRect.right.toInt() - mAreaHeight + mArrowMargin * 1.75).toInt(), (mOuterRect.top.toInt() + mArrowMargin * 1.75).toInt(),
                (mOuterRect.right.toInt() - mArrowMargin * 1.75).toInt(),
                (mOuterRect.bottom.toInt() - mArrowMargin * 1.75).toInt())
        mDrawableRight.draw(canvas)

        // Arrow angle
        mArrowAngle = -180 * mPositionPerc
        mDrawableArrow.setBounds(mInnerRect.left.toInt() + mArrowMargin,
            mInnerRect.top.toInt() + mArrowMargin,
            mInnerRect.right.toInt() - mArrowMargin,
            mInnerRect.bottom.toInt() - mArrowMargin)
        if (mDrawableArrow.bounds.left <= mDrawableArrow.bounds.right &&
            mDrawableArrow.bounds.top <= mDrawableArrow.bounds.bottom) {
            mDrawableArrow.draw(canvas)
        }

        // Tick drawing
        mDrawableTick.setBounds(
            mActualAreaWidth + mTickMargin,
            mTickMargin,
            mAreaWidth - mTickMargin - mActualAreaWidth,
            mAreaHeight - mTickMargin)

        // Tinting the tick with the proper implementation method
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mDrawableTick.setTint(innerColor)
        } else {
            (mDrawableTick as AnimatedVectorDrawableCompat).setTint(innerColor)
        }

        if (mFlagDrawTick) {
            mDrawableTick.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && isEnabled) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkInsideButton(event.x, event.y)) {
                        mFlagMoving = true
                        mLastX = event.x
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    mFlagMoving = false
                    parent.requestDisallowInterceptTouchEvent(false)
                    if ((mPosition > 0 && isLocked) || (mPosition > 0 && mPositionPerc < mGraceValue)) {
                        // Check for grace value

                        val positionAnimator = ValueAnimator.ofInt(mPosition, 0)
                        positionAnimator.duration = 300
                        positionAnimator.addUpdateListener({
                            mPosition = it.animatedValue as Int
                            invalidateArea()
                        })
                        positionAnimator.start()
                    } else if (mPosition > 0 && mPositionPerc >= mGraceValue) {
                        isEnabled = false // Fully disable touch events
                        startAnimationComplete()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mFlagMoving) {
                        val diffX = event.x - mLastX
                        mLastX = event.x
                        increasePosition(diffX.toInt())
                        invalidateArea()
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun invalidateArea() {
        invalidate(mOuterRect.left.toInt(), mOuterRect.top.toInt(), mOuterRect.right.toInt(), mOuterRect.bottom.toInt())
    }

    /**
     * Private method to check if user has touched the slider cursor
     * @param x The x coordinate of the touch event
     * @param y The y coordinate of the touch event
     * @return A boolean that informs if user has pressed or not
     */
    private fun checkInsideButton(x: Float, y: Float): Boolean {
        return (0 < y && y < mAreaHeight && mPosition < x && x < (mAreaHeight + mPosition))
    }

    /**
     * Private method for increasing/decreasing the position
     * Ensure that position never exits from its range [0, (mAreaWidth - mAreaHeight)]
     *
     * @param inc Increment to be performed (negative if it's a decrement)
     */
    private fun increasePosition(inc: Int) {
        mPosition += inc
        if (mPosition < 0)
            mPosition = 0
        if (mPosition > (mAreaWidth - mAreaHeight))
            mPosition = mAreaWidth - mAreaHeight
        if (mPosition == 0) {
            mIsStartedOnce = false
        }
        if (mPosition > 16 && mPosition < 80 && !mIsStartedOnce) {
            onSlideStartedListener?.onSlideStarted(this@SlideToActView)
            mIsStartedOnce = true
        }
    }

    /**
     * Private method that is performed when user completes the slide
     */
    private fun startAnimationComplete() {
        mIsAnimating = true
        val animSet = AnimatorSet()

        // Animator that moves the cursor
        val finalPositionAnimator = ValueAnimator.ofInt(mPosition, mAreaWidth - mAreaHeight)
        finalPositionAnimator.addUpdateListener({
            mPosition = it.animatedValue as Int
            invalidateArea()
        })

        // Animator that bounce away the cursors
        val marginAnimator = ValueAnimator.ofInt(mActualAreaMargin, (mInnerRect.width() / 2).toInt() + mActualAreaMargin)
        marginAnimator.addUpdateListener({
            mActualAreaMargin = it.animatedValue as Int
            invalidateArea()
        })
        marginAnimator.interpolator = AnticipateOvershootInterpolator(2f)

        // Animator that reduces the outer area (to right)
        val areaAnimator = ValueAnimator.ofInt(0, (mAreaWidth - mAreaHeight) / 2)
        areaAnimator.addUpdateListener {
            mActualAreaWidth = it.animatedValue as Int
            if (Build.VERSION.SDK_INT >= 21) {
                invalidateOutline()
            }
            invalidateArea()
        }

        val tickAnimator: ValueAnimator
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            // Fallback not using AVD.
            tickAnimator = ValueAnimator.ofInt(0, 255)
            tickAnimator.addUpdateListener {
                mTickMargin = mIconMargin
                mFlagDrawTick = true
                mDrawableTick.alpha = it.animatedValue as Int
                invalidateArea()
            }
        } else {
            // Used AVD Animation.
            tickAnimator = ValueAnimator.ofInt(0)
            tickAnimator.addUpdateListener {
                if (!mFlagDrawTick) {
                    mTickMargin = mIconMargin
                    mFlagDrawTick = true
                    startTickAnimation()
                    invalidateArea()
                }
            }
        }

        if (mPosition >= mAreaWidth - mAreaHeight) {
            animSet.playSequentially(marginAnimator, areaAnimator)
        } else {
            animSet.playSequentially(finalPositionAnimator, marginAnimator, areaAnimator)
        }

        animSet.duration = 300

        animSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator?) {
                mDrawableRight.alpha = 0
                onSlideToActAnimationEventListener?.onSlideCompleteAnimationStarted(this@SlideToActView, mPositionPerc)
            }

            override fun onAnimationCancel(p0: Animator?) {
            }

            override fun onAnimationEnd(p0: Animator?) {
                mIsCompleted = true
                onSlideToActAnimationEventListener?.onSlideCompleteAnimationEnded(this@SlideToActView)
                onSlideCompleteListener?.onSlideComplete(this@SlideToActView)
            }

            override fun onAnimationRepeat(p0: Animator?) {
            }
        })
        animSet.start()
    }

    /**
     * Method that completes the slider
     */
    fun completeSlider() {
        if (!mIsCompleted) {
            startAnimationComplete()
        }
    }

    /**
     * Method that reset the slider
     */
    fun resetSlider() {
        if (mIsCompleted) {
            startAnimationReset()
        }
    }

    /**
     * Method that returns the 'mIsCompleted' flag
     * @return True if slider is in the Complete state
     */
    fun isCompleted(): Boolean {
        return this.mIsCompleted
    }

    /**
     * Method that returns the 'mIsAnimating' flag
     * @return True if slider is still working and showing animation
     */
    fun isAnimating(): Boolean {
        return this.mIsAnimating
    }

    fun setIsAnimating(animating: Boolean) {
        this.mIsAnimating = animating
    }

    /**
     * Private method that is performed when you want to reset the cursor
     */
    private fun startAnimationReset() {
        mIsCompleted = false
        val animSet = AnimatorSet()

        // Animator that enlarges the outer area
        val tickAnimator = ValueAnimator.ofInt(mTickMargin, mAreaWidth / 2)
        tickAnimator.addUpdateListener({
            mTickMargin = it.animatedValue as Int
            invalidateArea()
        })

        // Animator that enlarges the outer area
        val areaAnimator = ValueAnimator.ofInt(mActualAreaWidth, 0)
        areaAnimator.addUpdateListener({
            // Now we can hide the tick till the next complete
            mFlagDrawTick = false
            mActualAreaWidth = it.animatedValue as Int
            if (Build.VERSION.SDK_INT >= 21) {
                invalidateOutline()
            }
            invalidateArea()
        })

        val positionAnimator = ValueAnimator.ofInt(mPosition, 0)
        positionAnimator.addUpdateListener({
            mPosition = it.animatedValue as Int
            invalidateArea()
        })

        // Animator that re-draw the cursors
        val marginAnimator = ValueAnimator.ofInt(mActualAreaMargin, mOriginAreaMargin)
        marginAnimator.addUpdateListener({
            mActualAreaMargin = it.animatedValue as Int
            invalidateArea()
        })
        marginAnimator.interpolator = AnticipateOvershootInterpolator(2f)

        // Animator that makes the arrow appear
        val arrowAnimator = ValueAnimator.ofInt(mArrowMargin, mIconMargin)
        arrowAnimator.addUpdateListener({
            mArrowMargin = it.animatedValue as Int
            invalidateArea()
        })


        marginAnimator.interpolator = OvershootInterpolator(2f)
        animSet.playSequentially(areaAnimator, positionAnimator, marginAnimator, arrowAnimator)
        animSet.duration = 300

        animSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator?) {
                mDrawableRight.alpha = 255
                onSlideToActAnimationEventListener?.onSlideResetAnimationStarted(this@SlideToActView)
            }

            override fun onAnimationCancel(p0: Animator?) {
            }

            override fun onAnimationEnd(p0: Animator?) {
                isEnabled = true
                stopTickAnimation()
                onSlideToActAnimationEventListener?.onSlideResetAnimationEnded(this@SlideToActView)
                onSlideResetListener?.onSlideReset(this@SlideToActView)
            }

            override fun onAnimationRepeat(p0: Animator?) {
            }
        })
        animSet.start()
    }

    /**
     * Private method to start the Tick AVD animation, with the proper library based on API level.
     */
    private fun startTickAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (mDrawableTick as AnimatedVectorDrawable).start()
        } else {
            (mDrawableTick as AnimatedVectorDrawableCompat).start()
        }
    }

    /**
     * Private method to stop the Tick AVD animation, with the proper library based on API level.
     */
    private fun stopTickAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (mDrawableTick as AnimatedVectorDrawable).stop()
        } else {
            (mDrawableTick as AnimatedVectorDrawableCompat).stop()
        }
    }

    /**
     * Event handler for the SlideToActView animation events.
     * This event handler can be used to react to animation events from the Slide,
     * the event will be fired whenever an animation start/end.
     */
    interface OnSlideToActAnimationEventListener {

        /**
         * Called when the slide complete animation start. You can perform actions during the complete
         * animations.
         *
         * @param view The SlideToActView who created the event
         * @param threshold The mPosition (in percentage [0f,1f]) where the user has left the cursor
         */
        fun onSlideCompleteAnimationStarted(view: SlideToActView, threshold: Float)

        /**
         * Called when the slide complete animation finish. At this point the slider is stuck in the
         * center of the slider.
         *
         * @param view The SlideToActView who created the event
         */
        fun onSlideCompleteAnimationEnded(view: SlideToActView)

        /**
         * Called when the slide reset animation start. You can perform actions during the reset
         * animations.
         *
         * @param view The SlideToActView who created the event
         */
        fun onSlideResetAnimationStarted(view: SlideToActView)

        /**
         * Called when the slide reset animation finish. At this point the slider will be in the
         * ready on the left of the screen and user can interact with it.
         *
         * @param view The SlideToActView who created the event
         */
        fun onSlideResetAnimationEnded(view: SlideToActView)
    }

    /**
     * Event handler for the slide complete event.
     * Use this handler to react to slide event
     */
    interface OnSlideCompleteListener {
        /**
         * Called when user performed the slide
         * @param view The SlideToActView who created the event
         */
        fun onSlideComplete(view: SlideToActView)
    }

    /**
     * Event handler for the slide react event.
     * Use this handler to inform the user that he can slide again.
     */
    interface OnSlideResetListener {
        /**
         * Called when slides is again available
         * @param view The SlideToActView who created the event
         */
        fun onSlideReset(view: SlideToActView)
    }

    /**
     * Event handler for the slide started event.
     * Use this handler to find out if the user started to slide the slider.
     */
    interface OnSlideStartedListener {
        /**
         * Called when the user startes to slide the slider
         * @param view The SlideToActView who created the event
         */
        fun onSlideStarted(view: SlideToActView)
    }

    /**
     * Outline provider for the SlideToActView.
     * This outline will suppress the shadow (till the moment when Android will support
     * updatable Outlines).
     */
    private inner class SlideToActOutlineProvider : ViewOutlineProvider() {

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun getOutline(view: View?, outline: Outline?) {
            if (view == null || outline == null)
                return

            outline.setRoundRect(mActualAreaWidth, 0, mAreaWidth - mActualAreaWidth, mAreaHeight,mBorderRadius.toFloat())
        }
    }
}



