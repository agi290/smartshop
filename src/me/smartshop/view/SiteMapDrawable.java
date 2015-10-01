package me.smartshop.view;

import java.util.Vector;

import org.metalev.multitouch.controller.MultiTouchController.PointInfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import me.smartshop.CompassListener;
import me.smartshop.CompassMonitor;
import me.smartshop.Logger;
import me.smartshop.R;
import me.smartshop.ToolBox;

public class SiteMapDrawable extends MultiTouchDrawable implements CompassListener {

	/**
	 * @uml.property name="backgroundImage"
	 */
	protected Bitmap backgroundImage;

	float angleAdjustment = 0.0f;

	protected float lastAngle;

	protected Vector<PointF> steps;

	protected static final double MIN_ANGLE_CHANGE = Math.toRadians(5);

	public SiteMapDrawable(Context ctx, RefreshableView refresher) {
		super(ctx, refresher);
		init();
	}

	public SiteMapDrawable(Context ctx, MultiTouchDrawable superDrawable) {
		super(ctx, superDrawable);
		init();
	}

	protected void init() {
		width = displayWidth;
		height = displayHeight;
		backgroundImage = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.shop_f);
		steps = new Vector<PointF>();
		this.resetXY();
	}
	
	
	public void drawpath(Canvas c){
		
		
		Paint paint = new Paint();
	    Path p = new Path();
	    
	    
		 c.drawColor(Color.rgb(250, 250, 250));
		 paint.setStyle(Paint.Style.FILL);
		 paint.setColor(Color.MAGENTA);
		 paint.setAntiAlias(true);
   	     p.moveTo(20, 20);
		 p.lineTo(100, 200);
		 p.lineTo(200, 100);
		 c.drawPath(p, paint);

   	
		 
   }

	public void startAutoRotate() {
		CompassMonitor.registerListener(ctx, this);
		Logger.d("Auto rotate started. North value: " + angleAdjustment);
	}

	public void stopAutoRotate() {
		CompassMonitor.unregisterListener(this);
	}

	@Override
	public Drawable getDrawable() {
		Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

		Canvas canvas = new Canvas(bmp);
		canvas.drawColor(Color.rgb(250, 250, 250));

		return new BitmapDrawable(ctx.getResources(), bmp);
	}

	@Override
	public void draw(Canvas canvas) {
		// Logger.d("Drawing " + this.toString());
		canvas.save();
		float dx = (maxX + minX) / 2;
		float dy = (maxY + minY) / 2;

		canvas.translate(dx, dy);
		canvas.rotate(angle * 180.0f / (float) Math.PI);
		canvas.translate(-dx, -dy);

		// fill the canvas with nearly white colur
		canvas.drawColor(Color.rgb(250, 250, 250));

		// draw backgroundimage
		if (backgroundImage != null)
			canvas.drawBitmap(backgroundImage, new Rect(0, 0, backgroundImage.getWidth(), backgroundImage.getHeight()), new Rect((int) minX,
					(int) minY, (int) maxX, (int) maxY), null);

		synchronized (steps) {

			if (steps != null) {

				Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
				pointPaint.setStyle(Paint.Style.STROKE);
				pointPaint.setColor(Color.rgb(191, 64, 64));
				pointPaint.setStrokeWidth(30);
				pointPaint.setAntiAlias(true);

				canvas.save();

				canvas.translate(dx, dy);
				canvas.rotate((float) Math.toDegrees(angle));
				canvas.translate(-dx, -dy);

				canvas.translate(minX, minY);
				

				int i = -1;
				for (PointF step : steps) {
					canvas.drawCircle(step.x * scaleX, step.y * scaleY, 0.3f, pointPaint);

					if (i >= 0) {
						canvas.drawLine(steps.get(i).x * scaleX, steps.get(i).y * scaleY, step.x * scaleX, step.y * scaleY, pointPaint);
					}
					i++;
				}

				canvas.restore();

			}

		}

		this.drawSubdrawables(canvas);
	}

	@Override
	public void setAngle(float angle) {
		super.setAngle(angle);
	}

	@Override
	public void setScale(float scaleX, float scaleY) {
		this.scaleX = scaleX;
		this.scaleY = scaleY;
	}

	@Override
	public void setRelativePosition(float xPos, float yPos) {
	}

	@Override
	public boolean isScalable() {
		return true;
	}

	@Override
	public boolean isRotatable() {
		return true;
	}

	@Override
	public boolean isDragable() {
		return true;
	}

	@Override
	public boolean isOnlyInSuper() {
		return false;
	}

	@Override
	public boolean hasSuperDrawable() {
		return false;
	}

	@Override
	public MultiTouchDrawable getSuperDrawable() {
		return null;
	}

	/**
	 * set the size of the map
	 * 
	 * @param width
	 *            in pixels
	 * @param height
	 *            in pixels
	 */
	public void setSize(int width, int height) {
		this.width = width;
		this.height = height;
		this.recalculatePositions();

	}

	/**
	 * @return the backgroundImage
	 * @uml.property name="backgroundImage"
	 */
	public Bitmap getBackgroundImage() {
		return backgroundImage;
	}

	/**
	 * @param backgroundImage
	 *            the backgroundImage to set
	 * @uml.property name="backgroundImage"
	 */
	public void setBackgroundImage(Bitmap backgroundImage) {
		this.backgroundImage = backgroundImage;
	}

	/**
	 * @return the steps
	 */
	public Vector<PointF> getSteps() {
		return steps;
	}

	/**
	 * @param steps
	 *            the steps to set
	 */
	public void setSteps(Vector<PointF> steps) {
		this.steps = steps;
	}

	public void addStep(PointF step) {
		if (steps == null) {
			steps = new Vector<PointF>();
		}
		steps.add(step);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.fhstp.wificompass.view.MultiTouchDrawable#bringSubDrawableToFront(at.fhstp.wificompass.view.MultiTouchDrawable)
	 */
	@Override
	protected void bringSubDrawableToFront(MultiTouchDrawable drawable) {
		super.bringSubDrawableToFront(drawable);

		if (!(drawable instanceof UserDrawable)) {
			// user should be one of the last drawables, so we search the vector reverse
			for (int i = subDrawables.size() - 1; i >= 0; i--) {
				if (subDrawables.get(i) instanceof UserDrawable) {
					// the user should always be in front
					subDrawables.get(i).bringToFront();
					break;
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.fhstp.wificompass.view.MultiTouchDrawable#onSingleTouch(org.metalev.multitouch.controller.MultiTouchController.PointInfo)
	 */
	@Override
	public boolean onSingleTouch(PointInfo pointinfo) {
		hidePopups();
		return true;
	}

	public void setAngleAdjustment(float adjustment) {
		this.angleAdjustment = adjustment;
		this.angleChangeCallback = null;
	}

	@Override
	public void onCompassChanged(float azimuth, float angle, String direction) {
		// azimuth = (float) Math.toRadians(azimuth);
		float adjusted = ToolBox.normalizeAngle((angle + angleAdjustment) * -1.0f);

		// filter small movements
		if (Math.abs(lastAngle - adjusted) > MIN_ANGLE_CHANGE) {
			this.setAngle(adjusted);
			this.recalculatePositions();
			this.lastAngle = adjusted;
		}
	}

	/**
	 * 
	 */
	public void deleteAllSteps() {
		steps = new Vector<PointF>();
	}

}
