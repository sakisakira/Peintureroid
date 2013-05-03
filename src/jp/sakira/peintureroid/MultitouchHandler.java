package jp.sakira.peintureroid;

import java.util.Vector;

import android.annotation.TargetApi;
import android.util.Log;
import android.view.MotionEvent;

@TargetApi(5)
public class MultitouchHandler {
  Vector<Integer> _touch_ids;
  float[] _prev_xs, _prev_ys;
  float _prev_cx, _prev_cy;
  double _prev_dist;
  APView _view = null;
  
  MultitouchHandler(APView v) {
    this._view = v;
    
    this._touch_ids = new Vector<Integer>();
    this._prev_xs = new float[2];
    this._prev_ys = new float[2];
  }
  
  private void replace_touch_ids(MotionEvent e) {
    int id_count = e.getPointerCount();
    this._touch_ids.clear();
    for (int i = 0; i < id_count; i ++)
      this._touch_ids.add(e.getPointerId(i));
    _view.cancel_stroke();
    _view._x0 = _view._y0 = -1;
  }
  
  
  boolean multitouch_handler(MotionEvent e) {
    int id_count = e.getPointerCount();
//    Log.i("peintueroid", "pointerCount: " + id_count +
//        " action: " + e.getAction());
//    if (_view._x0 < 0 && id_count > 1)
//      return true;
  
    if (this._touch_ids.size() != id_count ||
        id_count > 2) {
      this.replace_touch_ids(e);
    } else {
      for (int i = 0; i < id_count; i ++) {
        if (!this._touch_ids.contains(e.getPointerId(i))) {
          this.replace_touch_ids(e);
        }
      }
    }
  
    switch (e.getAction() & MotionEvent.ACTION_MASK) {
    case MotionEvent.ACTION_DOWN:
    case MotionEvent.ACTION_POINTER_DOWN: 
      for (int i = 0; i < Math.min(id_count, 2); i ++) {
        this._prev_xs[i] = e.getX(this._touch_ids.get(i));
        this._prev_ys[i] = e.getY(this._touch_ids.get(i));
      }
      if (id_count == 2) {
        float x0 = e.getX(_touch_ids.get(0));
        float x1 = e.getX(_touch_ids.get(1));
        float y0 = e.getY(_touch_ids.get(0));
        float y1 = e.getY(_touch_ids.get(1));
        this._prev_cx = (x0 + x1) / 2; 
        this._prev_cy = (y0 + y1) / 2;
        this._prev_dist = 
          Math.sqrt((x0 - x1) * (x0 - x1) +
              (y0 - y1) * (y0 - y1));
        Log.d("peintu", "prev_dist:" + _prev_dist);
      }
      break;
    case MotionEvent.ACTION_MOVE:
      if (id_count == 2) {
        float x0 = e.getX(_touch_ids.get(0));
        float x1 = e.getX(_touch_ids.get(1));
        float y0 = e.getY(_touch_ids.get(0));
        float y1 = e.getY(_touch_ids.get(1));
        float cx = (x0 + x1) / 2;
        float cy = (y0 + y1) / 2;
        double dist = Math.sqrt((x0 - x1) * (x0 - x1) + 
            (y0 - y1) * (y0 - y1));
      
        float nzoom = _view._zoom * (float)(dist / this._prev_dist);
//        _view._shift_x += _view._x1 * (1.0f / _view._zoom - 1.0f / nzoom);
//        _view._shift_y += _view._y1 * (1.0f / _view._zoom - 1.0f / nzoom);        
        _view._shift_x += cx * (1.0f / _view._zoom - 1.0f / nzoom);
        _view._shift_y += cy * (1.0f / _view._zoom - 1.0f / nzoom);        
        _view._shift_x -= (cx - this._prev_cx) / _view._zoom;
        _view._shift_y -= (cy - this._prev_cy) / _view._zoom;
        Log.i("peintureroid", "prev_dist:" + _prev_dist + " dist:" + dist);
        _view._zoom = nzoom;
        _view.adjust_shift();
        this._prev_xs[0] = x0;
        this._prev_xs[1] = x1;
        this._prev_ys[0] = y0;
        this._prev_ys[1] = y1;
        this._prev_cx = cx;
        this._prev_cy = cy;
        this._prev_dist = dist;
        
        _view._x0 = _view._x1 = (int)cx;
        _view._y0 = _view._y1 = (int)cy;
      }
      break;
    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_POINTER_UP: 
      if (id_count == 2) {
        this.replace_touch_ids(e);
        return true;
      }
    }
  
    return (id_count != 1);
  }

}
