package net.osmand.plus.views;

import net.osmand.OsmAndFormatter;
import net.osmand.osm.MapUtils;
import net.osmand.plus.R;
import net.osmand.plus.OsmandSettings.CommonPreference;
import net.osmand.plus.activities.MapActivity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;

public class MapControlsLayer implements OsmandMapLayer {

	private static final int SHOW_SEEKBAR_MSG_ID = 2;
	private static final int SHOW_SEEKBAR_DELAY = 7000;
	private static final int SHOW_SEEKBAR_SECOND_DELAY = 25000;
	
	private static final int SHOW_ZOOM_LEVEL_MSG_ID = 3;
	private static final int SHOW_ZOOM_LEVEL_DELAY = 2000;
	

	private OsmandMapTileView view;
	private DisplayMetrics dm;
	private final MapActivity activity;
	private Handler showUIHandler;
	
	private boolean showZoomLevel = false;
	
	
	private Button zoomInButton;
	private Button zoomOutButton;
	
	private TextPaint zoomTextPaint;
	private Drawable zoomShadow;
	
	private Button backToMenuButton;
	
	private Drawable rulerDrawable;
	private TextPaint rulerTextPaint;
	private final static double screenRulerPercent = 0.25;
	private SeekBar transparencyBar;
	private CommonPreference<Integer> settingsToTransparency;
	private BaseMapLayer[] transparencyLayers;
	

	public MapControlsLayer(MapActivity activity){
		this.activity = activity;
	}
	
	
	@Override
	public boolean drawInScreenPixels() {
		return true;
	}

	@Override
	public void initLayer(final OsmandMapTileView view) {
		this.view = view;
		dm = new DisplayMetrics();
		WindowManager wmgr = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
		wmgr.getDefaultDisplay().getMetrics(dm);
		FrameLayout parent = (FrameLayout) view.getParent();
		showUIHandler = new Handler();
		
		initZoomButtons(view, parent);

		initBackToMenuButton(view, parent);
		
		initRuler(view, parent);
		
		initTransparencyBar(view, parent);
	}

	@Override
	public void destroyLayer() {
	}

	@Override
	public void onDraw(Canvas canvas, RectF latlonRect, RectF tilesRect, boolean nightMode) {
		BaseMapLayer mainLayer = view.getMainLayer();
		boolean zoomInEnabled = mainLayer != null && view.getZoom() < mainLayer.getMaximumShownMapZoom();
		boolean zoomOutEnabled = mainLayer != null && view.getZoom() > mainLayer.getMinimumShownMapZoom();
		if(zoomInButton.isEnabled() != zoomInEnabled){
			zoomInButton.setEnabled(zoomInEnabled);
		}
		if(zoomOutButton.isEnabled() != zoomOutEnabled){
			zoomOutButton.setEnabled(zoomOutEnabled);
		}
		
		if(view.isZooming()){
			showZoomLevel = true;
			showUIHandler.removeMessages(SHOW_ZOOM_LEVEL_MSG_ID);
		} else {
			if(showZoomLevel){
				hideZoomLevelInTime();
			}
		}
		if (showZoomLevel) {
			drawZoomLevel(canvas);
		} else {
			drawRuler(canvas);
		}
	}


	
	private void drawZoomLevel(Canvas canvas) {
		String zoomText = view.getZoom() + "";
		float length = zoomTextPaint.measureText(zoomText);
		if (zoomShadow.getBounds().width() == 0) {
			zoomShadow.setBounds(zoomInButton.getLeft() - 2, zoomInButton.getTop() - (int) (18 * dm.density), zoomInButton.getRight()
					, zoomInButton.getBottom());
		}
		zoomShadow.draw(canvas);
				
		canvas.drawText(zoomText, zoomInButton.getLeft() + (zoomInButton.getWidth() - length - 2) / 2,
				zoomInButton.getTop() + 4 * dm.density, zoomTextPaint);
	}
	
	private void hideZoomLevelInTime(){
		if (!showUIHandler.hasMessages(SHOW_ZOOM_LEVEL_MSG_ID)) {
			Message msg = Message.obtain(showUIHandler, new Runnable() {
				@Override
				public void run() {
					showZoomLevel = false;
					view.refreshMap();
				}

			});
			msg.what = SHOW_ZOOM_LEVEL_MSG_ID;
			showUIHandler.sendMessageDelayed(msg, SHOW_ZOOM_LEVEL_DELAY);
		}
	}

	@Override
	public boolean onLongPressEvent(PointF point) {
		return false;
	}

	@Override
	public boolean onTouchEvent(PointF point) {
		return false;
	}

	
	private void initBackToMenuButton(final OsmandMapTileView view, FrameLayout parent) {
		android.widget.FrameLayout.LayoutParams params;
		backToMenuButton = new Button(view.getContext());
		backToMenuButton.setBackgroundResource(R.drawable.map_btn_menu);
		params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
					Gravity.BOTTOM | Gravity.LEFT);
		parent.addView(backToMenuButton, params);
		backToMenuButton.setEnabled(true);
		
		
		backToMenuButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				activity.backToMainMenu();
			}
		});
	}
	
	private void initRuler(OsmandMapTileView view, FrameLayout parent) {
		rulerTextPaint = new TextPaint();
		rulerTextPaint.setTextSize(20 * dm.density);
		rulerTextPaint.setAntiAlias(true);
		
		
		rulerDrawable = view.getResources().getDrawable(R.drawable.ruler);
	}
	
	private void initZoomButtons(final OsmandMapTileView view, FrameLayout parent) {
		int minimumWidth = view.getResources().getDrawable(R.drawable.map_zoom_in).getMinimumWidth();
		
		zoomTextPaint = new TextPaint();
		zoomTextPaint.setTextSize(18 * dm.density);
		zoomTextPaint.setAntiAlias(true);
		zoomTextPaint.setFakeBoldText(true);
		
		zoomShadow = view.getResources().getDrawable(R.drawable.zoom_background);
		
		zoomInButton = new Button(view.getContext());
		zoomInButton.setBackgroundResource(R.drawable.map_zoom_in);
		android.widget.FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
					Gravity.BOTTOM | Gravity.RIGHT);
		params.setMargins(0, 0, 0, 0);
		parent.addView(zoomInButton, params);
		
		
		zoomOutButton = new Button(view.getContext());
		zoomOutButton.setBackgroundResource(R.drawable.map_zoom_out);
		params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
					Gravity.BOTTOM | Gravity.RIGHT);
		
		params.setMargins(0, 0, minimumWidth , 0);
		parent.addView(zoomOutButton, params);
		
		zoomInButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(view.isZooming()){
					activity.changeZoom(view.getZoom() + 2);
				} else {
					activity.changeZoom(view.getZoom() + 1);
				}
				
			}
		});
		
		zoomOutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				activity.changeZoom(view.getZoom() - 1);
				
			}
		});
	}
	



	/////////////////  Transparency bar /////////////////////////
	private void initTransparencyBar(final OsmandMapTileView view, FrameLayout parent) {
		int minimumHeight = view.getResources().getDrawable(R.drawable.map_zoom_in).getMinimumHeight();
		android.widget.FrameLayout.LayoutParams params;
		transparencyBar = new SeekBar(view.getContext());
		transparencyBar.setVisibility(View.GONE);
		transparencyBar.setMax(255);
		params = new FrameLayout.LayoutParams((int) (dm.density * 100), LayoutParams.WRAP_CONTENT,
				Gravity.BOTTOM | Gravity.CENTER);
		params.setMargins(0, 0, 0, minimumHeight + 3);
		parent.addView(transparencyBar, params);
		transparencyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if(settingsToTransparency != null && transparencyLayers != null){
					settingsToTransparency.set(progress);
					for(BaseMapLayer base : transparencyLayers){
						base.setAlpha(progress);
					}
					MapControlsLayer.this.view.refreshMap();
					showAndHideTransparencyBar(settingsToTransparency, transparencyLayers, SHOW_SEEKBAR_SECOND_DELAY);
				}
			}
		});
	}
	
	public void showAndHideTransparencyBar(CommonPreference<Integer> transparenPreference,
			BaseMapLayer[] layerToChange) {
		showAndHideTransparencyBar(transparenPreference, layerToChange, SHOW_SEEKBAR_DELAY);
	}
	private void showAndHideTransparencyBar(CommonPreference<Integer> transparenPreference,
			BaseMapLayer[] layerToChange, int delay) {
		transparencyBar.setVisibility(View.VISIBLE);
		transparencyBar.setProgress(transparenPreference.get());
		this.transparencyLayers = layerToChange;
		this.settingsToTransparency = transparenPreference;
		Message msg = Message.obtain(showUIHandler, new Runnable() {
			@Override
			public void run() {
				transparencyBar.setVisibility(View.GONE);
			}

		});
		msg.what = SHOW_SEEKBAR_MSG_ID;
		showUIHandler.removeMessages(SHOW_SEEKBAR_MSG_ID);
		showUIHandler.sendMessageDelayed(msg, delay);
	}
	
	
	/////////////////////// Ruler ///////////////////
	// cache values for ruler
	String cacheRulerText = null;
	int cacheRulerZoom = 0;
	double cacheRulerTileX = 0;
	double cacheRulerTileY = 0;
	float cacheRulerTextLen = 0;	
	
	private void drawRuler(Canvas canvas) {
		// update cache
		if (view.isZooming()) {
			cacheRulerText = null;
		} else if(view.getZoom() != cacheRulerZoom || 
				Math.abs(view.getXTile() - cacheRulerTileX) +  Math.abs(view.getYTile() - cacheRulerTileY) > 1){
			cacheRulerZoom = view.getZoom();
			cacheRulerTileX = view.getXTile();
			cacheRulerTileY = view.getYTile();
			double latitude = view.getLatitude();
			double tileNumberLeft = cacheRulerTileX - ((double) view.getWidth()) / (2d * view.getTileSize());
			double tileNumberRight = cacheRulerTileX + ((double) view.getWidth()) / (2d * view.getTileSize());
			double dist = MapUtils.getDistance(latitude, MapUtils.getLongitudeFromTile(view.getZoom(), tileNumberLeft), latitude,
					MapUtils.getLongitudeFromTile(view.getZoom(), tileNumberRight));
			double pixDensity = view.getWidth() / dist; 
			
			double roundedDist = OsmAndFormatter.calculateRoundedDist(dist * screenRulerPercent, view.getContext());
			
			int cacheRulerDistPix = (int) (pixDensity * roundedDist);
			cacheRulerText = OsmAndFormatter.getFormattedDistance((float) roundedDist, view.getContext());
			cacheRulerTextLen = zoomTextPaint.measureText(cacheRulerText);
			
			Rect bounds = rulerDrawable.getBounds();
			bounds.right = (int) (view.getWidth() - 7 * dm.density);
			bounds.bottom = (int) (view.getHeight() - view.getResources().getDrawable(R.drawable.map_zoom_in).getMinimumHeight());
			bounds.top = bounds.bottom - rulerDrawable.getMinimumHeight();
			bounds.left = bounds.right - cacheRulerDistPix;
			rulerDrawable.setBounds(bounds);
		} 
		if (cacheRulerText != null) {
			rulerDrawable.draw(canvas);
			Rect bounds = rulerDrawable.getBounds();
			canvas.drawText(cacheRulerText, bounds.left + (bounds.width() - cacheRulerTextLen) / 2, bounds.bottom - 8 * dm.density,
					rulerTextPaint);
		}
	}


	

}
