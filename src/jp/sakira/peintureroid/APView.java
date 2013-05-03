package jp.sakira.peintureroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Xfermode;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

enum ViewMode {DRAW, COLOR_PICK, SHIFT};

public class APView extends View 
    implements OnSeekBarChangeListener {
  
  enum Composition {Multiply, Screen, Normal};
  static final String[] CompositionNames = {"Mul", "Scr", "Nor"};
  static Paint[] _compPaint = new Paint[Composition.values().length];
  Paint _src_paint;
  
  public enum FingerFunction {
    Normal, Eraser, Shift, None
  }
  private FingerFunction _finger_function = FingerFunction.Normal;

  static final int LayerNumber = 5;
    
  static final int PaintNumber = 6;
  static final int EraserIndex = 0;
    
  static final int PressureSmoothLevel = 12;
  static final int TapsizeSmoothLevel = 12;
  
  static final float PenWidthMin = 0.1f;
  static float penWidthMax = 50.0f;
  static final float ZoomMin = 0.5f;
  static final float ZoomMax = 16.0f;
    
	public int _layer_index, _paint_index;
	public int _x0, _y0, _x1, _y1;
	public int _width, _height;
	public float _shift_x, _shift_y, _last_shift_x, _last_shift_y;
	public float _zoom, _last_zoom;
	
	boolean _pressure_is_constant = true;
	float _pressure, _last_pressure = -1;
	float[] _pressures;
	int _pressures_index;
	float _max_pressure = 0.01f;
	
	boolean _tapsize_is_constant = true;
	float _tapsize, _last_tapsize = -1;
	float[] _tapsizes;
	int _tapsizes_index;
	float _max_tapsize = 0.01f;
	boolean _is_of_stylus = false;
	
	ViewMode _mode;
  Bitmap _bitmap = null;
  Bitmap _bitmap_p, _bitmap_t, _bitmap565;
	Bitmap[] _layers = new Bitmap[LayerNumber];
	Composition[] _compositionMethods = new Composition[LayerNumber];
	Paint[] _paints;
	AndroidPeinture _app;
	MultitouchHandler _mt_handler = null;
	ToolTypeHandler _tool_type_handler = null;
	UndoManager _undo_manager = null;
	
	float[] _diff_x = {0f, 0f, 0f, 0f};
	float[] _diff_y = {0f, 0f, 0f, 0f};
	float[] _calibration_center_x = new float[4];
	float[] _calibration_center_y = new float[4];
	
	public APView(Context context) {
	  super(context);
	  setup();
	}
    
	public APView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup();
	}
	
	public APView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup();
	}
	
	void setup() {
		_x0 = _y0 = -1;
		_layer_index = 0;
		_mode = ViewMode.DRAW;
		
		_bitmap = _bitmap_p = _bitmap_t = null;
		_bitmap565 = null;
		for (int i = 0; i < LayerNumber; i ++)
			_layers[i] = null;
		
		_paint_index = 1;
		_paints = new Paint[PaintNumber];
		for (int i = 0; i < PaintNumber; i ++) {
			_paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
			_paints[i].setStrokeJoin(Paint.Join.ROUND);
			_paints[i].setStrokeCap(Paint.Cap.ROUND);
			_paints[i].setFilterBitmap(true);
		}
		_paints[EraserIndex].setColor(0xffffffff);  // EraserIndex == 0 
		_paints[EraserIndex].setStrokeWidth(10);
		_paints[1].setColor(0xc0000000);
		_paints[1].setStrokeWidth(2);
		_paints[2].setColor(0x80ff0000);
		_paints[2].setStrokeWidth(10);
		_paints[3].setColor(0xffffffff);
		_paints[3].setStrokeWidth(7);
		_paints[4].setColor(0x8000ff00);
		_paints[4].setStrokeWidth(7);
		_paints[5].setColor(0x800000ff);
		_paints[5].setStrokeWidth(7);
		
		_shift_x = _shift_y = _last_shift_x = _last_shift_y = 0.0f;
		_zoom = _last_zoom = 1.0f;
		_pressures = new float[PressureSmoothLevel];
		_tapsizes = new float[TapsizeSmoothLevel];
		clear_pressures();
		clear_tapsizes();
		
		for (int i = 0; i < LayerNumber; i ++)
			_compositionMethods[i] = Composition.Multiply;
		
		Xfermode mult_xfer = new PorterDuffXfermode(
		    PorterDuff.Mode.MULTIPLY);
		Paint mult_p = new Paint();
		mult_p.setXfermode(mult_xfer);
		mult_p.setFilterBitmap(true);
		APView._compPaint[Composition.Multiply.ordinal()] = mult_p;
		Xfermode scrn_xfer = new PorterDuffXfermode(
		    PorterDuff.Mode.SCREEN);
		Paint scrn_p = new Paint();
		scrn_p.setXfermode(scrn_xfer);
		scrn_p.setFilterBitmap(true);
		APView._compPaint[Composition.Screen.ordinal()] = scrn_p;
//		Xfermode nrml_xfer = new PorterDuffXfermode(
//		    PorterDuff.Mode.SRC_IN);
//		Paint nrml_p = new Paint();
//		nrml_p.setXfermode(nrml_xfer);
		Paint normal_paint = new Paint();
		normal_paint.setFilterBitmap(true);
		APView._compPaint[Composition.Normal.ordinal()] = normal_paint;
		
		Xfermode src_xfer = new PorterDuffXfermode(
		    PorterDuff.Mode.SRC);
		_src_paint = new Paint();
		_src_paint.setXfermode(src_xfer);
		_src_paint.setFilterBitmap(true);
	}
	
	int eraserColor() {
	  return eraserColor(_layer_index);
	}
	
	int eraserColor(int li) {
	  Composition lcm = _compositionMethods[li];
	  int col;
	  switch (lcm) {
	  case Multiply:
	    col = 0xffffffff;
	    break;
	  case Screen:
	    col = 0xff000000;
	    break;
	  case Normal:
	    col = 0xff000000;
	    break;
	  default:
	    col = 0;
	  }
	  return col;
	}
	
    void start(AndroidPeinture app, int w, int h) {
    	_app = app;
    	penWidthMax = (w + h) / 4;
    	
    	if (_bitmap == null)
    	  clearBuffer(w, h);
    	setFocusable(true);
    	setFocusableInTouchMode(true);
    	requestFocus();
    	requestFocusFromTouch();
    	setClickable(true);
    	setDrawingCacheQuality(2);
    	
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
    	  this._mt_handler = new MultitouchHandler(this);
    	} else {
    	  this._mt_handler = null;
    	}

    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
    	  this._tool_type_handler = new ToolTypeHandler();
    	} else {
    	  this._tool_type_handler = null;
    	}
    	
   	  _calibration_center_x[0] = _calibration_center_x[2] = 0;
   	  _calibration_center_x[1] = _calibration_center_x[3] = w;
   	  _calibration_center_y[0] = _calibration_center_y[1] = 0;
   	  _calibration_center_y[2] = _calibration_center_y[3] = h;
   	  
      clearPenLayer();
      apply_pen_layer();
      changeLayer(0);
    }
    
    private void clear_pressures() {
    	for (int i = 0; i < PressureSmoothLevel; i ++)
    		_pressures[i] = -1.0f;
    	_pressures_index = 0;
    }
    
    private void clear_tapsizes() {
      for (int i = 0; i < TapsizeSmoothLevel; i ++)
        _tapsizes[i] = -1.0f;
      _tapsizes_index = 0;
    }
    
    private float smooth_pressure(float f) {
      if (_pressure_is_constant) {
        if (_last_pressure < 0.0f && f > 0)
          _last_pressure = f;
        if (_last_pressure != f && f > 0)
          _pressure_is_constant = false;
      }
      if (_pressure_is_constant) return 1.0f;
    	    	
    	_pressures[_pressures_index] = f;
    	_pressures_index = (_pressures_index + 1) % PressureSmoothLevel;
    	
      float s = 0.0f;
      int count = 0;
    	for (int i = 0;i < PressureSmoothLevel; i ++)
    	  if (_pressures[i] >= 0) {
    	    s += _pressures[i];
    	    count ++;
    	  }
    	s /= count;
    	return s;
    }
    
    private float smooth_tapsize(float f) {
      if (_tapsize_is_constant) {
        if (_last_tapsize < 0 && f > 0)
          _last_tapsize = f;
        if (_last_tapsize != f && f > 0)
          _tapsize_is_constant = false;
      }
      if (_tapsize_is_constant) return 1.0f;
      
      _tapsizes[_tapsizes_index] = f;
      _tapsizes_index = (_tapsizes_index + 1) % TapsizeSmoothLevel;
      
      float s = 0.0f;
      int count = 0;
      for (int i = 0; i < TapsizeSmoothLevel; i ++)
        if (_tapsizes[i] >= 0) {
          s += _tapsizes[i];
          count ++;
        }
      s /= count;
      return s;
    }
    
    public FingerFunction fingerFunction() {
      return _finger_function;
    }
    
    public void setFingerFunction(FingerFunction func) {
      _finger_function = func;
      if (_tool_type_handler != null)
        _tool_type_handler.setDrawInfo(_finger_function, _paint_index);
    }
    
    Paint paint() {
    	return _paints[_paint_index];
    }
    
    float getPenWidth() {
    	return paint().getStrokeWidth();
    }
    
    void setPenWidth(float w) {
    	paint().setStrokeWidth(w);
    }
    
    int getPenDensity() {
    	return (int)paint().getAlpha();
    }
    
    void setPenDensity(int w) {
    	paint().setAlpha(w);
    }
	
    int getPenColor() {
    	return paint().getColor();
    }
    
    int getPenColor(int i) {
      if (i == EraserIndex)
        return eraserColor();
      else
        return _paints[i].getColor();
    }
    
    void setPenColor(int c) {
    	paint().setColor((paint().getColor() & 0xff000000) |
    			(c & 0x00ffffff));
    }
    
    
    void setMessage(String msg) {
    	_app._message_area.setText(msg);
    }
    
    void setMode() {
    	this.setMode(this._mode);
    }
    
    void setMode(ViewMode m) {
    	boolean zoom_slider_visible = false;
    	boolean spuit_view_visible = false;

    	String msg = "";
    	switch (m) {
    		case DRAW:
    			msg = "Draw Mode";
    			zoom_slider_visible = false;
    			spuit_view_visible = false;
    			break;
    		case COLOR_PICK:
    			msg = "Color Pick Mode";
    			zoom_slider_visible = false;
    			spuit_view_visible = true;
    			break;
    		case SHIFT:
    			msg = "Shift Mode";
    			zoom_slider_visible = true;
    			spuit_view_visible = false;
    			_app._zoom_slider
    			  .setProgress((int)((this._zoom - ZoomMin) * 100.0f));
    			break;
    	}
    	
    	_app._zoom_slider.setVisibility(zoom_slider_visible ?
    	    View.VISIBLE : View.INVISIBLE);
    	_app._spuit_view.setVisibility(spuit_view_visible ?
    	    View.VISIBLE : View.INVISIBLE);
    	_mode = m;
    	setMessage(msg);
    }
    
    void initCurrentLayer() {
      initLayer(_layer_index);
      clearPenLayer();
      _undo_manager.setImageToLayer(_layers[_layer_index], _layer_index, false);
      apply_layers(new Rect(0, 0, _width, _height));
      invalidate();
    }
    
    public void initLayer(int li) {
      if (_layers[li] != null)
        _layers[li].recycle();

      _layers[li] =  Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      Composition ci = _compositionMethods[li];
      int col = 0;
      if (ci == Composition.Multiply)
        col = 0xffffffff;
      else if (ci == Composition.Screen)
        col = 0xff000000;
      else if (ci == Composition.Normal)
        col = 0x00ffffff;
      Log.i("peintureroid", "li:" + li + " ci:" + ci + " col:" + col);
      _layers[li].eraseColor(col);
    }
    
    void setCompositionMode(int li, Composition ci) {
      _compositionMethods[li] = ci;
      clearPenLayer();
      apply_layers(new Rect(0, 0, _width, _height));
      changeLayer(li);
    }
    
    public void clearBuffer(int w, int h) {
      _width = w;
      _height = h;

      for (int i = 0; i < LayerNumber; i ++) {
        if (_layers[i] != null)
          _layers[i].recycle();
        _layers[i] = null;
      }

      if (_bitmap != null) _bitmap.recycle();
      _bitmap = null;
      if (_bitmap_p != null) _bitmap_p.recycle();
      _bitmap_p = null;
      if (_bitmap_t != null) _bitmap_t.recycle();
      _bitmap_t = null;
      if (_bitmap565 != null) _bitmap565.recycle();
      _bitmap565 = null;
      _undo_manager = null;

      System.gc();

      this._layer_index = 0;
      initLayer(0);

      for (int i = 0; i < LayerNumber; i ++)
        _compositionMethods[i] = Composition.Multiply;

      _bitmap = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap_p = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap_t = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap565 = Bitmap.createBitmap(2, 2,
          Bitmap.Config.RGB_565);
      _undo_manager = new UndoManager(_app.getApplication(), this);
      _undo_manager.undoLabel = (TextView)_app.findViewById(R.id.undo_label);
      _undo_manager.setImageToLayer(_layers[_layer_index], _layer_index, true);
      
      _bitmap_t.eraseColor(0);
      _bitmap_p.eraseColor(0);

      clearPenLayer();
      apply_layers(new Rect(0, 0, _width, _height));

      invalidate();
    }
    
    void apply_layers(Rect orect) {
      Rect rect = new Rect();
      if (!rect.setIntersect(orect, 
          new Rect(0, 0, this._width, this._height)))
        return;

      Canvas cl = new Canvas(_layers[_layer_index]);
      cl.drawBitmap(_bitmap_t, rect, rect, _src_paint);
      Paint p = new Paint();
      p.setAlpha(paint().getAlpha());
      p.setFilterBitmap(true);
      if (_paint_index == EraserIndex && 
          _compositionMethods[_layer_index] == Composition.Normal) {
        Xfermode mult = new PorterDuffXfermode(
            PorterDuff.Mode.DST_OUT);
        p.setXfermode(mult);
      }
      cl.drawBitmap(_bitmap_p, rect, rect, p);

      Canvas canvas = new Canvas(_bitmap);
      canvas.drawBitmap(_layers[0], rect, rect, _src_paint);
      for (int i = 1; i < LayerNumber; i ++)
        if (_layers[i] != null)
          canvas.drawBitmap(_layers[i], rect, rect, 
              _compPaint[_compositionMethods[i].ordinal()]);
    }
	
    void apply_pen_layer() {
    	Canvas cl = new Canvas(_layers[_layer_index]);
    	cl.drawBitmap(_bitmap_t, 0, 0, _src_paint);
    	Paint p = new Paint();
    	p.setAlpha(paint().getAlpha());
    	p.setFilterBitmap(true);
    	if (_paint_index == EraserIndex && 
    			_compositionMethods[_layer_index] == Composition.Normal) {
    		Xfermode mult = new PorterDuffXfermode(
    				PorterDuff.Mode.DST_OUT);
    		p.setXfermode(mult);
    	}
    	Log.i("peintureroid", "_bitmap_p:" + _bitmap_p);
    	cl.drawBitmap(_bitmap_p, 0, 0, p);
    }

    public void clearPenLayer() {
      _bitmap_p.eraseColor(0);
      if (_layers[_layer_index] == null)
        initLayer(_layer_index);
      
      Composition ci = _compositionMethods[_layer_index];
      int col = 0;
      if (ci == Composition.Multiply)
        col = 0xffffffff;
      else if (ci == Composition.Screen)
        col = 0xff000000;
      else if (ci == Composition.Normal)
        col = 0x00ffffff;
      _bitmap_t.eraseColor(col);
      Canvas ct = new Canvas(_bitmap_t);
      ct.drawBitmap(_layers[_layer_index], 0, 0, null);
    }
    
    void cancel_stroke() {
      _bitmap_p.eraseColor(0);
      apply_layers(new Rect(0, 0, _width, _height));
    }

    public void changeLayer(int l) {
      if (l >= LayerNumber || l < 0) return;

      apply_pen_layer();
      if (_layers[l] == null)
        initLayer(l);
      _layer_index = l;
      clearPenLayer();

      apply_layers(new Rect(0, 0, _width, _height));
      _undo_manager.setImageToLayer(_layers[_layer_index], _layer_index, false);
      _undo_manager.checkLayerChanged(_layer_index);
      Log.i("peintureroid", "layer changed to " + l);
    }
    
    void change_paint(int p) {
      if (p >= PaintNumber || p < 0) return;

      _paint_index = p;
      _app.setPaintButtons(paint());
      
      if (_tool_type_handler != null)
        _tool_type_handler.setDrawInfo(_finger_function, _paint_index);
    }

    public void undo() {
      _undo_manager.undo();
      Canvas c = new Canvas(_bitmap_t);
      c.drawBitmap(_layers[_layer_index], 0, 0, _src_paint);
      _bitmap_p.eraseColor(0);
      changeLayer(_layer_index);

      invalidate();
    }
    
    public void redo() {
      _undo_manager.redo();
      Canvas c = new Canvas(_bitmap_t);
      c.drawBitmap(_layers[_layer_index], 0, 0, _src_paint);
      _bitmap_p.eraseColor(0);
      changeLayer(_layer_index);

      invalidate();      
    }
    
    private int zoomx(int ox) {
    	return (int)(_shift_x + ox / _zoom);
    }
    
    private int zoomy(int oy) {
    	return (int)(_shift_y + oy / _zoom);
    }
    
    public void adjust_shift() {
      _zoom = Math.max(Math.min(_zoom, ZoomMax), ZoomMin);

      if ((_last_zoom - 1.0f) * (_zoom - 1.0f) < 0)
        _zoom = 1.0f;

      _shift_x = Math.max(- _width / (_zoom  * 2),
          Math.min(_width - _width / (_zoom  * 2), _shift_x));
      _shift_y = Math.max(- _height / (_zoom  * 2),
          Math.min(_height - _height / (_zoom  * 2), _shift_y));

      AnimationSet anim = new AnimationSet(true);
      anim.addAnimation(new TranslateAnimation(
          - _last_shift_x, - _shift_x,
          - _last_shift_y, - _shift_y));
      anim.addAnimation(new ScaleAnimation(_last_zoom, _zoom,
          _last_zoom, _zoom));
      anim.setFillAfter(true);
      anim.setDuration(100);
      startAnimation(anim);

      _last_shift_x = _shift_x;
      _last_shift_y = _shift_y;
      _last_zoom = _zoom;
    }
	
    public void drawLine(int ox0, int oy0, int ox1, int oy1) {
      Canvas c = new Canvas(_bitmap_p);
      Paint p = new Paint(paint());
      if (_paint_index == EraserIndex) {
        p.setColor(eraserColor());
      } else {
        p.setAlpha((int)(_pressure * 0xff));
        p.setXfermode(_src_paint.getXfermode());
      }

      float pen_width;
      if (_is_of_stylus) {
        pen_width = paint().getStrokeWidth() * _pressure;
      } else if (_pressure_is_constant) {
        pen_width = paint().getStrokeWidth();
      } else {
        pen_width = paint().getStrokeWidth() * _tapsize;
      }
      p.setStrokeWidth(pen_width);
      float x0 = zoomx(ox0);
      float y0 = zoomy(oy0);
      float x1 = zoomx(ox1);
      float y1 = zoomy(oy1);
      c.drawLine(x0, y0, x1, y1, p);
      _undo_manager.addLine(x0, y0, x1, y1, p);
    }

    void touchPressed(int x, int y) {
      setMessage("");
      _x0 = x;
      _y0 = y;
      _undo_manager.startStroke(paint(), _layer_index);
    }
	
	void touchDragged(int[] xs, int[] ys) {
		if (_x0 < 0) return;
		
		int pen_width = (int)paint().getStrokeWidth();
		int pwh = (int)((pen_width / 2 + 1) * _zoom);
		int lastx = this._x0;
		int lasty = this._y0;
		
		int x, y, min_x, min_y, max_x, max_y;
		x = lastx; y = lasty;
		min_x = lastx - pwh;
		max_x = lastx + pwh;
		min_y = lasty - pwh;
		max_y = lasty + pwh;
		for (int i = 0; i < xs.length; i ++) {
		  x = xs[i];
		  y = ys[i];
		  drawLine(lastx, lasty, x, y);
		  min_x = Math.min(min_x, x - pwh);
		  max_x = Math.max(max_x, x + pwh);
		  min_y = Math.min(min_y, y - pwh);
		  max_y = Math.max(max_y, y + pwh);
		  lastx = x; lasty = y;
		}
		
		apply_layers(new Rect(zoomx(min_x), zoomy(min_y),
				zoomx(max_x), zoomy(max_y)));
		_x0 = lastx; _y0 = lasty;
    _x1 = x; _y1 = y;
		invalidate(new Rect(min_x, min_y, max_x, max_y));
	}
	
	void touchReleased(int x, int y) {
	  _undo_manager.endStroke();
		apply_pen_layer();
		clearPenLayer();
		apply_layers(new Rect(0, 0, _width, _height));
		_x0 = _y0 = -1;
	}
	
	void touchPressedShift(int x, int y) {
		_x0 = x;
		_y0 = y;
	}
	
	void touchDraggedShift(int x, int y) {
		_shift_x += (_x0 - x) / _zoom;
		_shift_y += (_y0 - y) / _zoom;
		_x0 = x;
		_y0 = y;
		adjust_shift();
		invalidate();
	}

	void touchReleasedShift(int x, int y) {
		_x0 = _y0 = _x1 = _y1 = -1;
	}
	
	private void calibrate(PointF pt) {
	  if (_diff_x[0] == 0 && _diff_y[0] == 0 &&
	      _diff_x[1] == 0 && _diff_y[1] == 0 &&
	      _diff_x[2] == 0 && _diff_y[2] == 0 &&
	      _diff_x[3] == 0 && _diff_y[3] == 0)
	    return;
	  
	  float[] l = new float[4];
	  float L = 0f;
	  for (int i = 0; i < 4; i ++) {
	    float dcx = _calibration_center_x[i] - pt.x;
	    float dcy = _calibration_center_y[i] - pt.y;
	    l[i] = (float)(dcx * dcx + dcy * dcy);
	    L += l[i];
	  }
	  
	  float dx = 0f;
	  float dy = 0f;
	  for (int i = 0; i < 4; i ++) {
	    dx += (L - l[i]) * _diff_x[i];
	    dy += (L - l[i]) * _diff_y[i];
	  }
	  dx /= 3 * L;
	  dy /= 3 * L;
	  
	  pt.x -= dx;
	  pt.y -= dy;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (_bitmap == null) return false;

    if (_mt_handler != null &&
        _mt_handler.multitouch_handler(event)) 
      return true;

    ViewMode mode = _mode;
    if (_tool_type_handler == null) {
      _is_of_stylus = false;
    } else if (mode == ViewMode.DRAW) {
      DrawInfo draw_info = _tool_type_handler.parse(event);
      mode = draw_info.mode;
      _paint_index = draw_info.paintIndex;
      _is_of_stylus = draw_info.isStylus;
      if (!draw_info.process) return true;
    }    
		
		PointF cpt = new PointF(event.getX(), event.getY());
		calibrate(cpt);
    int x = (int)cpt.x;
    int y = (int)cpt.y;
	    
    _pressure = smooth_pressure(event.getPressure() / _max_pressure);
    _tapsize = smooth_tapsize(event.getSize() / _max_tapsize);
    if (_pressure > 1.0f) _pressure = 1.0f;
    if (_tapsize > 1.0f) _tapsize = 1.0f;
	    
    if (mode == ViewMode.DRAW) {
      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        touchPressed(x, y);
        break;
      case MotionEvent.ACTION_MOVE:
        int hist_size = event.getHistorySize();
        int[] xs = new int[hist_size + 1];
        int[] ys = new int[hist_size + 1];
        for (int i = 0; i < hist_size; i ++) {
          cpt.x = event.getHistoricalX(i);
          cpt.y = event.getHistoricalY(i);
          calibrate(cpt);
          xs[i] = (int)cpt.x;
          ys[i] = (int)cpt.y;
        }
        xs[hist_size] = x;
        ys[hist_size] = y;
        touchDragged(xs, ys);
        break;
      case MotionEvent.ACTION_UP:
        touchReleased(x, y);
        clear_pressures();
        clear_tapsizes();
        break;
      }	
    } else if (mode == ViewMode.COLOR_PICK) {
      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_MOVE:
        Canvas c = new Canvas(_bitmap565);
        int ax = zoomx(x);
        int ay = zoomy(y);
        c.drawBitmap(_bitmap, 
            new Rect(ax, ay, ax + 1, ay + 1),
            new Rect(0, 0, 1, 1),
            null);
        int pc = _bitmap565.getPixel(0, 0);
        setPenColor(pc);
        _app.setPaintButtons(paint());
        this.layoutSpuitView(x, y);
        break;
      case MotionEvent.ACTION_UP:
        setMode(ViewMode.DRAW);
        break;
      }
    } else if (mode == ViewMode.SHIFT) {
      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        touchPressedShift(x, y);
        break;
      case MotionEvent.ACTION_MOVE:
        touchDraggedShift(x, y);
        break;
      case MotionEvent.ACTION_UP:
        touchReleasedShift(x, y);
        break;
      }
    }
    return true;
	}
		
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		if (_bitmap != null) {
			canvas.drawBitmap(_bitmap, 0, 0, null);
		}
	}
	
	private void change_zoom(float diffz) {
		float nzoom = _zoom + diffz;
		if (_x1 < 0 || _x1 >= getWidth() ||
				_y1 < 0 || _y1 >= getHeight()) {
			_x1 = getWidth() / 2;
			_y1 = getHeight() / 2;
		}
		_shift_x += _x1 * (1.0f / _zoom - 1.0f / nzoom);
		_shift_y += _y1 * (1.0f / _zoom - 1.0f / nzoom);
		_zoom = nzoom;
		adjust_shift();
//		invalidate();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch(keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
			if (_mode != ViewMode.SHIFT)
				setMode(ViewMode.SHIFT);
			else
				setMode(ViewMode.DRAW);
			return true;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			change_zoom(- 0.2f);
			return true;
		case KeyEvent.KEYCODE_DPAD_UP:
			change_zoom(+ 0.2f);
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    if (seekBar == _app._zoom_slider && this._mode == ViewMode.SHIFT) {
      float nzoom = (progress / 100.0f) + ZoomMin;
      if (_x1 < 0 || _x1 >= getWidth() ||
          _y1 < 0 || _y1 >= getHeight()) {
        _x1 = getWidth() / 2;
        _y1 = getHeight() / 2;
      }
      _shift_x += _x1 * (1.0f / _zoom - 1.0f / nzoom);
      _shift_y += _y1 * (1.0f / _zoom - 1.0f / nzoom);
      _zoom = nzoom;
      adjust_shift();
    }
  }

  public void onStartTrackingTouch(SeekBar seekBar) {}
  public void onStopTrackingTouch(SeekBar seekBar) {}
  
  void layoutSpuitView(int x, int y) {
	  if (x < 0) {
		  _app._spuit_view.setVisibility(INVISIBLE);
		  return;
	  }
	  
    int w = _app._spuit_view.getWidth();
    int h = _app._spuit_view.getHeight();
    int y_ = y * getWidth() / this._width - h * 3 / 2;
    int x_ = x * getWidth() / this._width - w / 2;
    _app._spuit_view.setBackgroundColor(this.getPenColor() | 0xff000000);
    _app._spuit_view.layout(x_, y_, x_ + w, y_ + h);
    Log.i("peintureroid", "spuit x=" + x_ + " y=" + 
    		y_ + " w=" + w + " h=" + h);
    _app._spuit_view.setVisibility(VISIBLE);
  }
  
  void drawCurrentPen() {
    _is_of_stylus = false;
    _pressure_is_constant = true;
    _bitmap_p.eraseColor(0);
    final int cx = zoomx(getWidth() / 2);
    final int cy = zoomy(getHeight() / 2);
    drawLine(cx - 1, cy - 1 , cx + 1, cy + 1);
    apply_layers(new Rect(0, 0, _width, _height));
    invalidate();
  }
  
  void clearCurrentPen() {
    _bitmap_p.eraseColor(0);
    apply_layers(new Rect(0, 0, _width, _height));
    invalidate();
  }

}
