package jp.sakira.peintureroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CalibrationView extends View {
  AndroidPeinture _app;
  
  public CalibrationView(Context context) {
    super(context);
  }
  
  public CalibrationView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CalibrationView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  
  protected void onDraw(Canvas canvas) {
    float cx = getWidth() / 2;
    float cy = getHeight() / 2;
    Paint p = new Paint();
    p.setStrokeWidth(1);
    p.setColor(0xff000000);
    
    canvas.drawARGB(200, 255, 128, 128);
    canvas.drawLine(cx, 0, cx, cy * 2, p);
    canvas.drawLine(0, cy, cx * 2, cy, p);
  }
  
  public boolean onTouchEvent(MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_UP) {
      float cx = getWidth() / 2;
      float cy = getHeight() / 2;
      _app.calibrationViewClickedWithDiff(this, e.getX() - cx, e.getY() - cy);
    }
    
    return true;
  }
  
}
