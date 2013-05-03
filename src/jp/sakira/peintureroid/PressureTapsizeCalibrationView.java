package jp.sakira.peintureroid;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

public class PressureTapsizeCalibrationView extends View implements 
SeekBar.OnSeekBarChangeListener {
  static final float PressureScale = 50.0f;
  static final float SizeScale = 50.0f;
  
  SeekBar pressure_slider, tapsize_slider;
  APView apview;
  
  public PressureTapsizeCalibrationView(Context context, APView view) {
    super(context);
    apview = view;
    pressure_slider = new SeekBar(context);
    tapsize_slider = new SeekBar(context);
    
    setSeekBarsProgress();
    pressure_slider.setOnSeekBarChangeListener(this);
    tapsize_slider.setOnSeekBarChangeListener(this);
  }
  
  protected void onDraw(Canvas canvas) {
    canvas.drawARGB(200, 128, 128, 255);
  }
  
  private void setSeekBarsProgress() {
    pressure_slider.setProgress((int)(apview._max_pressure * PressureScale));
    tapsize_slider.setProgress((int)(apview._max_tapsize * SizeScale));
  }
  
  public boolean onTouchEvent(MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_MOVE) {
      Log.i("peintureroid", "raw pressure:" + e.getPressure() + " raw size:" + e.getSize());
      apview._max_pressure = Math.max(apview._max_pressure, e.getPressure());
      apview._max_tapsize = Math.max(apview._max_tapsize, e.getSize());
      setSeekBarsProgress();
    }
    
    return true;
  }

  // SeekBar.OnSeekBarChangeListener
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    apview._max_pressure = pressure_slider.getProgress() / PressureScale;
    apview._max_tapsize = tapsize_slider.getProgress() / SizeScale;
  }

  public void onStartTrackingTouch(SeekBar seekBar) {}
  public void onStopTrackingTouch(SeekBar seekBar) {}
}
