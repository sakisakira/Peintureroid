package jp.sakira.peintureroid;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;

public class ColorPanel {
  public int width;
  public RGB_HSV rgb_hsv;
  public int sat_num, lum_num;
  public HueLine hue;
  public SatLum sat_lum;

  AndroidPeinture _app;

  public ColorPanel(AndroidPeinture app, int w, int init_col) {
    width = w;
    _app = app;

    rgb_hsv = new RGB_HSV();
    hue = new HueLine(this, w, 40);
    sat_lum = new SatLum(this, w, w);

    setColor(init_col);
  }
    
  public HueLine getHueLine() {
    return hue;
  }
    
  public SatLum getSatLum() {
    return sat_lum;
  }

  public void setColor(int col) {
    this.setColor(col, -1, -1, true);
  }
    
  public void setColor(int col, int x, int y,
      boolean update_hue) {
    rgb_hsv.setColor(col);
    sat_num = rgb_hsv.chroma();
    lum_num = rgb_hsv.luminance();
    if (update_hue)
      sat_lum.setColor(rgb_hsv.hue());

		_app.setPaintColor(col, x, y);
  }

  public void setHue(int hue) {
    rgb_hsv.setHCL(hue, sat_num, lum_num);
    sat_lum.setColor(hue);
  }

  public void setHueSatLum(int hue, int sat, int lum) {
    rgb_hsv.setHCL(hue, sat, lum);
    int col = rgb_hsv.getColor();

    setColor(col);
    sat_num = sat;
    lum_num = lum;

    _app.setPaintColor(col);
  }
}

////////////////////////////////////////////////////////////
class HueLine extends ImageView {
  int width, height;
  Bitmap _bitmap;
  ColorPanel _colorpanel;

  HueLine(ColorPanel colp, int w, int h) {
    super(colp._app);
    _colorpanel = colp;
    width = w;
    height = h;

    setScaleType(ImageView.ScaleType.FIT_XY);

    setPadding(10, 5, 10, 5);
    setMaxWidth(w);
    setMaxHeight(h);
    int[] buf = new int[w * h];

    RGB_HSV rgb_hsv = new RGB_HSV();
    int x, y, c;

    for (x = 0; x < width; x ++) {
      rgb_hsv.setHCL(x * 0x600 / width, 256, 128);
      c = rgb_hsv.getColor() | 0xff000000;
      for (y = 0; y < height; y ++)
        buf[y * width + x] = c;
    }

    _bitmap = Bitmap.createBitmap(buf, width, height,
	    Bitmap.Config.ARGB_8888);
    setImageBitmap(_bitmap);
  }
    
  @Override
  public boolean onTouchEvent(MotionEvent e) {
    _colorpanel.setHue((int)(e.getX()) * 0x600 / getWidth());
    return true;
  }
}

////////////////////////////////////////////////////////////
class SatLum extends ImageView {
  ColorPanel _colorpanel;
  int _hue;
  Bitmap _bitmap;
  RGB_HSV _rh;

  static final int width = 171;
  static final int height = 171;

  SatLum(ColorPanel c, int w, int h) {
    super(c._app);
    _colorpanel = c;
    this._hue = -1;
	
    _rh = new RGB_HSV();
	
    setScaleType(ImageView.ScaleType.FIT_XY);

    setPadding(10, 5, 10, 5);
//    setMaxWidth(w);
//    setMaxHeight(h);
    setMinimumWidth(w);
    setMinimumHeight(h);
  }

  public void setColor(int h) {
    if (h == this._hue) return;
    
    int[] buf = new int[width * height];

    _hue = h;

    int x, y, l, c32;
    for (y = 0; y < height; y ++) {
      c32 = (y * 3) >> 1;  /* c32 = y * 256 / 171 */
	    for (x = 0; x < width; x ++) {
	      l = (x * 3) >> 1;  /* l = x * 256 / 171 */

	      this._rh.setHCL(h, c32, l);
	      buf[y * width + x] = this._rh.getColor();
	    }
    }
	
    _bitmap = Bitmap.createBitmap(buf, width, height,
		      Bitmap.Config.ARGB_8888);
    setImageBitmap(_bitmap);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    int c, l;

    l = (int)(e.getX()) * 256 / getWidth();
    c = (int)(e.getY()) * 256 / getHeight();
    Log.i("peintu", "l:" + l + " c:" + c);
	
    this._rh.setHCL(this._hue, c, l);
    if (e.getAction() == MotionEvent.ACTION_DOWN ||
			e.getAction() == MotionEvent.ACTION_MOVE)
      _colorpanel.setColor(this._rh.getColor(),
          (int)e.getRawX(), (int)e.getRawY(), false);
    else
      _colorpanel.setColor(this._rh.getColor(), 
          -1, -1, false);
	
	return true;
    }
}


