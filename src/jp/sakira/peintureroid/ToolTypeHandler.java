package jp.sakira.peintureroid;

import jp.sakira.peintureroid.APView.FingerFunction;
import android.annotation.TargetApi;
import android.os.Build;
import android.view.MotionEvent;

class DrawInfo {
  ViewMode mode = ViewMode.DRAW; 
  int paintIndex = 0;
  boolean process = true, isStylus = false;
}

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class ToolTypeHandler {
  public enum Type {
     Finger, Stylus, Eraser
  }
  private DrawInfo _stylus_draw_info, _finger_draw_info, _eraser_draw_info;
  
  public ToolTypeHandler() {
    super();
    _stylus_draw_info = new DrawInfo();
    _stylus_draw_info.process = true;
    _stylus_draw_info.isStylus = true;
    
    _finger_draw_info = new DrawInfo();
    _finger_draw_info.isStylus = false;
    
    _eraser_draw_info = new DrawInfo();
    _eraser_draw_info.paintIndex = APView.EraserIndex;
    _eraser_draw_info.process = true;
    _eraser_draw_info.isStylus = true;
    
    setDrawInfo(FingerFunction.Normal, 0);
  }
  
  public Type toolTypeOf(MotionEvent e) {
    try {
      if (e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)
        return Type.Stylus;
      else if (e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)
        return Type.Eraser;
      else
        return Type.Finger;
    } catch (VerifyError err) {
      return Type.Finger;
    }
  }
  
  public void setDrawInfo(final FingerFunction ff, final int paint_index) {
    _stylus_draw_info.mode = ViewMode.DRAW;
    _stylus_draw_info.paintIndex = paint_index;
    _stylus_draw_info.process = true;
    _stylus_draw_info.isStylus = true;
    
    _eraser_draw_info.mode = ViewMode.DRAW;
    _eraser_draw_info.paintIndex = APView.EraserIndex;
    _eraser_draw_info.process = true;
    _eraser_draw_info.isStylus = true;

    switch (ff) {
    case Normal:
      _finger_draw_info.mode = ViewMode.DRAW;
      _finger_draw_info.paintIndex = paint_index;
      _finger_draw_info.process = true;
      _finger_draw_info.isStylus = false;
      break;
    case Eraser:
      _finger_draw_info.mode = ViewMode.DRAW;
      _finger_draw_info.paintIndex = APView.EraserIndex;
      _finger_draw_info.process = true;
      _finger_draw_info.isStylus = false;
      break;
    case Shift:
      _finger_draw_info.mode = ViewMode.SHIFT;
      _finger_draw_info.paintIndex = paint_index;
      _finger_draw_info.process = true;
      _finger_draw_info.isStylus = false;
      break;
    case None:
      _finger_draw_info.mode = ViewMode.DRAW;
      _finger_draw_info.paintIndex = paint_index;
      _finger_draw_info.process = false;
      _finger_draw_info.isStylus = false;
      break;
    }
  }
    
  public DrawInfo parse(MotionEvent event) {
    switch (toolTypeOf(event)) {
    case Stylus:
      return _stylus_draw_info;
    case Eraser:
      return _eraser_draw_info;
    case Finger:
    default:
      return _finger_draw_info;
    }
  }
}
