package jp.sakira.peintureroid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Xfermode;
import android.graphics.Bitmap.CompressFormat;
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

public class APView extends View 
    implements OnSeekBarChangeListener  {
  static final int DRAW_MODE = 1;
  static final int COLOR_PICK_MODE = 2;
  static final int SHIFT_MODE = 3;
  
  static final int CompositionMultiply = 0;
  static final int CompositionScreen = 1;
  static final int CompositionNormal = 2;
  static final int CompositionNumber = 3;
  static final String[] CompositionNames = {"Mul", "Scr", "Nor"};
  static Paint[] _compPaint = new Paint[CompositionNumber];
  Paint _src_paint;

  static final int LayerNumber = 5;
    
  static final int PaintNumber = 6;
  static final int EraserIndex = 0;
    
  static final int PressureSmoothLevel = 10;
  
  static final float PenWidthMin = 0.1f;
  static final float PenWidthMax = 50.0f;
  static final float PenWidthMult = 4.0f;
  static final float ZoomMin = 0.5f;
  static final float ZoomMax = 16.0f;
  
  float _pen_width_mult_smooth = 1.0f;

	int _layer_index, _paint_index;
	int _x0, _y0, _x1, _y1;
	int _width, _height;
	float _shift_x, _shift_y, _last_shift_x, _last_shift_y;
	float _zoom, _last_zoom;
	float _pressure;
	float[] _pressures;
	int _pressures_index;
	int _mode;
  static Bitmap _bitmap = null;
  static Bitmap _bitmap_p, _bitmap_t, _bitmap565;
  static Bitmap _bitmap_u;
	static Bitmap[] _layers = new Bitmap[LayerNumber];
	static int[] _compositionMethods = new int[LayerNumber];
	Paint[] _paints;
	AndroidPeinture _app;
	MultitouchHandler _mt_handler = null;
	
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
		_mode = DRAW_MODE;
		
		_bitmap = _bitmap_p = _bitmap_t = null;
		_bitmap565 = null;
		_bitmap_u = null;
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
		clear_pressures();
		
		for (int i = 0; i < LayerNumber; i ++)
			_compositionMethods[i] = CompositionMultiply;
		
		Xfermode mult_xfer = new PorterDuffXfermode(
		    PorterDuff.Mode.MULTIPLY);
		Paint mult_p = new Paint();
		mult_p.setXfermode(mult_xfer);
		mult_p.setFilterBitmap(true);
		APView._compPaint[CompositionMultiply] = mult_p;
		Xfermode scrn_xfer = new PorterDuffXfermode(
		    PorterDuff.Mode.SCREEN);
		Paint scrn_p = new Paint();
		scrn_p.setXfermode(scrn_xfer);
		scrn_p.setFilterBitmap(true);
		APView._compPaint[CompositionScreen] = scrn_p;
//		Xfermode nrml_xfer = new PorterDuffXfermode(
//		    PorterDuff.Mode.SRC_IN);
//		Paint nrml_p = new Paint();
//		nrml_p.setXfermode(nrml_xfer);
		Paint normal_paint = new Paint();
		normal_paint.setFilterBitmap(true);
		APView._compPaint[CompositionNormal] = normal_paint;
		
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
	  int lcm = _compositionMethods[li];
	  int col;
	  switch (lcm) {
	  case CompositionMultiply:
	    col = 0xffffffff;
	    break;
	  case CompositionScreen:
	    col = 0xff000000;
	    break;
	  case CompositionNormal:
	    col = 0xff000000;
	    break;
	  default:
	    col = 0;
	  }
	  return col;
	}
	
    void start(AndroidPeinture app, int w, int h) {
    	_app = app;
    	if (_bitmap == null)
    	  clear_buf(w, h);
    	setFocusable(true);
    	setFocusableInTouchMode(true);
    	requestFocus();
    	requestFocusFromTouch();
    	setClickable(true);
    	setDrawingCacheQuality(2);
    	
    	try {
    	  this._mt_handler = new MultitouchHandler(this);
    	} catch (VerifyError e) {
    	  this._mt_handler = null;
    	}
    	
   	  _calibration_center_x[0] = _calibration_center_x[2] = 0;
   	  _calibration_center_x[1] = _calibration_center_x[3] = w;
   	  _calibration_center_y[0] = _calibration_center_y[1] = 0;
   	  _calibration_center_y[2] = _calibration_center_y[3] = h;
    }
    
    void clear_pressures() {
    	for (int i = 0; i < PressureSmoothLevel; i ++)
    		_pressures[i] = 0.0f;
    	_pressures_index = 0;
    }
    
    float smooth_pressure(float f) {
    	float s = 0.0f;
    	
    	float const_pressure = _pressures[0];
    	boolean is_constant = true;
    	for (int i = 1; i < PressureSmoothLevel; i ++) {
    	  if (const_pressure != _pressures[i])
    	    is_constant = false;
    	}
    	if (is_constant || (const_pressure != 0)) return 1.0f;
    	
    	_pressures[_pressures_index] = f;
    	_pressures_index = (_pressures_index + 1) % PressureSmoothLevel;
    	
    	for (int i = 0;i < PressureSmoothLevel; i ++)
    		s += _pressures[i];
    	
    	return s / PressureSmoothLevel;
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
    
    boolean outputPNG(OutputStream os) {
    	return _bitmap.compress(CompressFormat.PNG, 100, os);
    }
    
    boolean outputPNG(int li, OutputStream os) {
    	if (li < 0 || li >= LayerNumber) return false;
    	if (_layers[li] == null) return false;
    	
    	return _layers[li].compress(CompressFormat.PNG, 100, os);
    }
        
    boolean outputCurrentLayerPNG(OutputStream os) {
    	return outputPNG(_layer_index, os);
    }

    boolean outputRAW(int li, OutputStream os) {
    	if (li < 0 || li >= LayerNumber) return false;
    	
    	ByteBuffer bbuf = ByteBuffer.allocate(_width * _height * 4);
    	_layers[li].copyPixelsToBuffer(bbuf);
    	byte[] data = bbuf.array();
    	try {
			os.write(data, 0, data.length);
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
		return true;
    }
    
    boolean inputRAW(int li, InputStream is) {
    	if (li < 0 || li >= LayerNumber) return false;

    	byte[] data = new byte[_width * _height * 4];
    	try {
			is.read(data, 0, data.length);
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
		IntBuffer ibuf = ByteBuffer.wrap(data).asIntBuffer();
    	int[] pixels = new int[_width * _height];
    	ibuf.get(pixels);
    	Bitmap temp_bmp = Bitmap.createBitmap(pixels, _width, _height, 
    			Bitmap.Config.ARGB_8888);
    	Canvas cur_canvas = new Canvas(_layers[li]);
    	cur_canvas.drawBitmap(temp_bmp, 0, 0, _src_paint);
    	temp_bmp.recycle();
    	return true;
    }
    
    void inputImage(int li, InputStream is) {
    	if (li < 0 || li >= LayerNumber) return;
    	
    	if (_layers[li] == null)
    		init_layer(li);
    	Bitmap temp_bmp = BitmapFactory.decodeStream(is);
    	if (temp_bmp == null) {
    	  _layers[li].recycle();
    	  _layers[li] = null;
    	} else {
    		Canvas cur_canvas = new Canvas(_layers[li]);
    		cur_canvas.drawBitmap(temp_bmp, 0, 0, _src_paint);
    		temp_bmp.recycle();
    	}
    }
    
    void inputImage(int li, File imgfile) {
    	if (li < 0 || li >= LayerNumber) return;
    	
    	Bitmap temp_bmp = BitmapFactory.decodeFile(imgfile.getAbsolutePath());
    	if (temp_bmp != null) {
    		Canvas cur_canvas = new Canvas(_layers[li]);
    		cur_canvas.drawBitmap(temp_bmp, 0, 0, _src_paint);
    		temp_bmp.recycle();
    	}
    }
    
    void inputImageToCurrentLayer(InputStream is) {
    	inputImage(_layer_index, is);
    }
    
    void inputImage(int li, byte[] imgdata) {
    	if (li < 0 || li >= LayerNumber) return;
    	
    	Bitmap temp_bmp = BitmapFactory.decodeByteArray(imgdata, 0, imgdata.length);
    	if (temp_bmp != null) {
    		Canvas cur_canvas = new Canvas(_layers[li]);
    		cur_canvas.drawBitmap(temp_bmp, 0, 0, _src_paint);
    		temp_bmp.recycle();
    	}
    }

    void setImage(int li, Bitmap b) {
    	float ratio;
    	int bw, bh;
    	Rect rect;
    	Canvas cur_canvas = new Canvas(_layers[li]);
    	
    	bw = b.getWidth();
    	bh = b.getHeight();
    	ratio = (float)bh / _height;
    	if (ratio * _width <= bw) {
    		rect = new Rect((int)(bw - ratio * _width) / 2, 0,
    				(int)(bw + ratio * _width) / 2, bh);
    	} else {
    		ratio = (float)bw / _width;
    		rect = new Rect(0, (int)(bh - ratio * _height) / 2,
    				bw, (int)(bh + ratio * _width) / 2);
    	}
    	
    	cur_canvas.drawBitmap(b, rect, new Rect(0, 0, _width, _height), 
    	    _src_paint);
    }
    
    void setImageToCurrentLayer(Bitmap b) {
    	setImage(_layer_index, b);
    }
    
    void setMessage(String msg) {
    	_app._message_area.setText(msg);
    }
    
    void setMode() {
    	this.setMode(this._mode);
    }
    
    void setMode(int m) {
    	boolean zoom_slider_visible = false;
    	boolean spuit_view_visible = false;

    	String msg = "";
    	switch (m) {
    		case DRAW_MODE:
    			msg = "Draw Mode";
    			zoom_slider_visible = false;
    			spuit_view_visible = false;
    			break;
    		case COLOR_PICK_MODE:
    			msg = "Color Pick Mode";
    			zoom_slider_visible = false;
    			spuit_view_visible = true;
    			break;
    		case SHIFT_MODE:
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
      init_layer(_layer_index);
      clear_pen_layer();
      apply_layers(new Rect(0, 0, _width, _height));
      invalidate();
    }
    
    void init_layer(int li) {
      if (_layers[li] != null)
        _layers[li].recycle();

      _layers[li] =  Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      int ci = _compositionMethods[li];
      int col = 0;
      if (ci == CompositionMultiply)
        col = 0xffffffff;
      else if (ci == CompositionScreen)
        col = 0xff000000;
      else if (ci == CompositionNormal)
        col = 0x00ffffff;
      Log.i("peintureroid", "li:" + li + " ci:" + ci + " col:" + col);
      _layers[li].eraseColor(col);
    }
    
    void setCompositionMode(int li, int ci) {
      _compositionMethods[li] = ci;
      clear_pen_layer();
      apply_layers(new Rect(0, 0, _width, _height));
      change_layer(li);
    }
    
    void clear_buf(int w, int h) {
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
      if (_bitmap_u != null) _bitmap_u.recycle();
      _bitmap_u = null;
      if (_bitmap565 != null) _bitmap565.recycle();
      _bitmap565 = null;

      System.gc();

      this._layer_index = 0;
      init_layer(0);

      for (int i = 0; i < LayerNumber; i ++)
        _compositionMethods[i] = CompositionMultiply;

      _bitmap = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap_p = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap_t = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap_u = Bitmap.createBitmap(_width, _height, 
          Bitmap.Config.ARGB_8888);
      _bitmap565 = Bitmap.createBitmap(2, 2,
          Bitmap.Config.RGB_565);

      _bitmap_t.eraseColor(0);
      _bitmap_p.eraseColor(0);
      _bitmap_u.eraseColor(0);

      clear_pen_layer();
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
          _compositionMethods[_layer_index] == CompositionNormal) {
        Xfermode mult = new PorterDuffXfermode(
            PorterDuff.Mode.DST_OUT);
        p.setXfermode(mult);
      }
      cl.drawBitmap(_bitmap_p, rect, rect, p);

      Canvas canvas = new Canvas(_bitmap);
//      canvas.drawBitmap(_layers[0], rect, rect, null);
      canvas.drawBitmap(_layers[0], rect, rect, _src_paint);
      for (int i = 1; i < LayerNumber; i ++)
        if (_layers[i] != null)
          canvas.drawBitmap(_layers[i], rect, rect, 
              _compPaint[_compositionMethods[i]]);
    }
	
    void apply_pen_layer() {
    	Canvas cl = new Canvas(_layers[_layer_index]);
    	cl.drawBitmap(_bitmap_t, 0, 0, _src_paint);
    	Paint p = new Paint();
    	p.setAlpha(paint().getAlpha());
    	p.setFilterBitmap(true);
    	if (_paint_index == EraserIndex && 
    			_compositionMethods[_layer_index] == CompositionNormal) {
    		Xfermode mult = new PorterDuffXfermode(
    				PorterDuff.Mode.DST_OUT);
    		p.setXfermode(mult);
    	}
    	cl.drawBitmap(_bitmap_p, 0, 0, p);
    }

    void clear_pen_layer() {
      Canvas cu = new Canvas(_bitmap_u);
      cu.drawBitmap(_bitmap_t, 0, 0, _src_paint);
      _bitmap_p.eraseColor(0);
      
      int ci = _compositionMethods[_layer_index];
      int col = 0;
      if (ci == CompositionMultiply)
        col = 0xffffffff;
      else if (ci == CompositionScreen)
        col = 0xff000000;
      else if (ci == CompositionNormal)
        col = 0x00ffffff;
      _bitmap_t.eraseColor(col);
      Canvas ct = new Canvas(_bitmap_t);
      ct.drawBitmap(_layers[_layer_index], 0, 0, null);
    }
    
    void cancel_stroke() {
      _bitmap_p.eraseColor(0);
      apply_layers(new Rect(0, 0, _width, _height));
    }

    void change_layer(int l) {
      if (l >= LayerNumber || l < 0) return;

      apply_pen_layer();
      if (_layers[l] == null)
        init_layer(l);
      _layer_index = l;
      clear_pen_layer();

      apply_layers(new Rect(0, 0, _width, _height));
      Log.i("peintureroid", "layer changed to " + l);
    }
    
    void change_paint(int p) {
      if (p >= PaintNumber || p < 0) return;

      _paint_index = p;
      _app.setPaintButtons(paint());
    }

    void undo() {
      Canvas ct = new Canvas(_bitmap_t);
      ct.drawBitmap(_bitmap_u, 0, 0, _src_paint);
      change_layer(_layer_index);

      invalidate();
    }
    
    int zoomx(int ox) {
    	return (int)(_shift_x + ox / _zoom);
    }
    
    int zoomy(int oy) {
    	return (int)(_shift_y + oy / _zoom);
    }
    
    void adjust_shift() {
      _zoom = Math.max(Math.min(_zoom, ZoomMax), ZoomMin);
    	
    	if ((_last_zoom - 1.0f) * (_zoom - 1.0f) < 0)
    		_zoom = 1.0f;
    	
    	if (_zoom <= 1.0f) {
    		_shift_x = - (_width / _zoom - _width) / 2;
    		_shift_y = - (_height / _zoom - _height) / 2;
    	} else {
    		_shift_x = Math.max(- _width / (_zoom  * 2),
    				Math.min(_width - _width / (_zoom  * 2), _shift_x));
    		_shift_y = Math.max(- _height / (_zoom  * 2),
    				Math.min(_height - _height / (_zoom  * 2), _shift_y));
    	}
    	
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
    	if (_paint_index == EraserIndex)
    	  p.setColor(eraserColor());
    	p.setAlpha(0xff);
    	float pen_width;
    	if (_pressure == 0.0f)
    	  pen_width = paint().getStrokeWidth();
    	else
    	  pen_width = paint().getStrokeWidth() * _pressure * PenWidthMult;
    	p.setStrokeWidth(pen_width);
    	c.drawLine(zoomx(ox0), zoomy(oy0), 
    			zoomx(ox1), zoomy(oy1), p);
    }
	
	void touchPressed(int x, int y) {
		setMessage("");
		_x0 = x;
		_y0 = y;
	}
	
	void touchDragged(int[] xs, int[] ys) {
		if (_x0 < 0) return;
		
		int pen_width = (int)paint().getStrokeWidth();
		int pwh = (int)((pen_width / 2 + 1) * _zoom * PenWidthMult);
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
		apply_pen_layer();
		clear_pen_layer();
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
	
	void calibrate(PointF pt) {
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
	
		PointF cpt = new PointF(event.getX(), event.getY());
		calibrate(cpt);
    int x = (int)cpt.x;
    int y = (int)cpt.y;
	    
//    Log.i("peinturoid", "pressure" + event.getPressure());
    _pressure = smooth_pressure(event.getPressure());
	    
    if (_mode == DRAW_MODE) {
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
        break;
      }	
    } else if (_mode == COLOR_PICK_MODE) {
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
        setMode(DRAW_MODE);
        break;
      }
    } else if (_mode == SHIFT_MODE) {
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
	
	void change_zoom(float diffz) {
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
			if (_mode != SHIFT_MODE)
				setMode(SHIFT_MODE);
			else
				setMode(DRAW_MODE);
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
    if (seekBar == _app._zoom_slider && this._mode == SHIFT_MODE) {
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

}
