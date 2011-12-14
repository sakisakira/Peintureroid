package jp.sakira.peintureroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.util.Log;

public class AndroidPeinture extends Activity implements
    android.widget.CompoundButton.OnCheckedChangeListener {
  int _height, _width;
  SeekBar _zoom_slider;
  View _spuit_view;
  CalibrationView _calibration_view;
  int _calibration_index;

  TouchDelegate _touch_delegate;
  static APView _view = null;
  AP_IO _io;

  public Button _layer_btn, _width_btn, _color_btn, _density_btn,
      _change_pen_btn;
  Button _chosen_btn;
  public TextView _message_area;
  public Gallery _image_gallery;

  static final String keyAPLayerNumber = "APLayerNumber";
  static final String keyLayerImage = "LayerImage";
  static final String keyCompositionMethod = "CompositionMethod";
  static final String keyCalibrationCenterX = "CalibrationCenterX";
  static final String keyCalibrationCenterY = "CalibrationCenterY";
  static final String keyCalibrationDiffX = "CalibrationDiffX";
  static final String keyCalibrationDiffY = "CalibrationDiffY";

  RadioButton[][] _comp_method_btns;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    DisplayMetrics dm = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(dm);
    _height = dm.heightPixels;
    _width = dm.widthPixels;
    
    setContentView(R.layout.main);
    AndroidPeinture._view = (APView) findViewById(R.id.apview);
    AndroidPeinture._view.start(this, _width, _height);

    _zoom_slider = (SeekBar) findViewById(R.id.zoom_slider);
    _zoom_slider.setOnSeekBarChangeListener(_view);

    _spuit_view = (View) findViewById(R.id.spuit_view);
    
    _calibration_view = (CalibrationView)findViewById(R.id.calibration_view);
    _calibration_view._app = this;
    _calibration_view.setVisibility(View.INVISIBLE);

    Button drawbtn = (Button) findViewById(R.id.draw_mode_button);
    drawbtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        DrawMode(v);
      }
    });

    Button shiftbtn = (Button) findViewById(R.id.shift_mode_button);
    shiftbtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        ShiftMode(v);
      }
    });

    System.gc();
    loadSavedState(savedInstanceState);

    _layer_btn = (Button) findViewById(R.id.layer_button);
    _layer_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showLayerButtons();
      }
    });

    _width_btn = (Button) findViewById(R.id.width_button);
    _width_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showWidthSlider();
      }
    });

    _color_btn = (Button) findViewById(R.id.color_button);
    _color_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        // showRGBSlider();
        showColorPanel();
      }
    });

    _density_btn = (Button) findViewById(R.id.density_button);
    _density_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDensitySlider();
      }
    });

    _change_pen_btn = (Button) findViewById(R.id.change_pen_button);
    _change_pen_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showChangePenButtons();
      }
    });

    _message_area = (TextView) findViewById(R.id.message_area);
    
    setPaintButtons(_view._paints[_view._paint_index]);

    _io = new AP_IO(this, _view);
    _io.setSize(_width, _height);
    _image_gallery = (Gallery) findViewById(R.id.image_gallery);
    _image_gallery.setOnItemClickListener(_io);

    setShowSelectors(false);
  }

  public void DrawMode(View v) {
    AndroidPeinture._view.setMode(APView.DRAW_MODE);
  }

  public void ShiftMode(View v) {
    AndroidPeinture._view.setMode(APView.SHIFT_MODE);
  }

  void setShowSelectors(boolean show) {
    int visibility = show ? View.VISIBLE : View.INVISIBLE;

    _image_gallery.setVisibility(visibility);
  }

  LinearLayout clearOptionalControls() {
    _view.setMode();
    LinearLayout l = (LinearLayout) findViewById(R.id.optional_controls);
    if (l.getChildCount() > 0)
      l.removeAllViews();
    return l;
  }

  void setPaintButtons(Paint paint) {
    _width_btn.setText("W:" + String.format("%.1f", paint.getStrokeWidth()));
    _color_btn.setTextColor(paint.getColor());
    _density_btn
        .setText("D:" + String.format("%.2f", paint.getAlpha() / 255.0));

  }

  void setPaintColor(int col, int x, int y) {
    this.setPaintColor(col);
    _view.layoutSpuitView(x, y);
  }

  void setPaintColor(int col) {
    _color_btn.setTextColor(col);
    _view.setPenColor(col);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Uri idata = intent.getData();
    Log.i("peintureroid", "onNewIntent intent:" + idata);
    String ipath = idata.getPath();
    Log.i("peintureroid", "onNewIntent path:" + ipath);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Peintureroid");
    builder.setMessage("Uri: " + idata + "\nPath: " + ipath);
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
      }
    });

    builder.show();
  }

  @Override
  protected void onResume() {
    super.onResume();
    Intent intent = getIntent();
    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
    if (uri != null) {
      Log.i("peintureroid", "onResume extra:" + uri);
      Bitmap bitmap = AP_IO.loadImage(uri);
      AndroidPeinture._view.setImage(0, bitmap);
      _view.clear_pen_layer();
      _view.change_layer(0);
    }
  }

  public void showAboutAlert(View v) {
    String versionName;
    PackageManager pm = getPackageManager();
    try {
      PackageInfo info = null;
      info = pm.getPackageInfo("jp.sakira.peintureroid", 0);
      versionName = info.versionName;
    } catch (NameNotFoundException e) {
      versionName = "";
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Peintureroid");
    builder.setMessage("version " + versionName + "\n\n" + "twitter: sakira");
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
      }
    });

    builder.show();
  }

  void showLayerButtons() {
    final LinearLayout l = clearOptionalControls();

    if (_chosen_btn == _layer_btn) {
      _chosen_btn = null;
      return;
    }
    _chosen_btn = _layer_btn;

    this._comp_method_btns = new RadioButton[APView.LayerNumber][];
    for (int i = 0; i < APView.LayerNumber; i++) {
      final int compNum = APView.CompositionNames.length;
      LinearLayout hl = new LinearLayout(this);
      hl.setOrientation(LinearLayout.HORIZONTAL);
      hl.setBackgroundColor(0xff808080);
      Button b = new Button(this);
      b.setText("Layer:" + i);
      hl.addView(b);

      RadioButton[] rbs;
      if (i == 0) {
        rbs = new RadioButton[0];
      } else {
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rbs = new RadioButton[compNum];
        for (int j = 0; j < compNum; j++) {
          rbs[j] = new RadioButton(this);
          rbs[j].setText(APView.CompositionNames[j]);
          rg.addView(rbs[j], j);
        }
        rbs[APView._compositionMethods[i]].setChecked(true);
        Log.i("peintureroid", "layer:" + i + " comp:"
            + APView._compositionMethods[i]);
        hl.addView(rg);
      }
      l.addView(hl);
      this._comp_method_btns[i] = rbs;

      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          LinearLayout lh = (LinearLayout)v.getParent();
          int index = l.indexOfChild(lh);
          _view.change_layer(index);
          _layer_btn.setText("L:" + index);
          l.removeAllViews();
          _chosen_btn = null;
        }
      });
      for (int ci = 0; ci < rbs.length; ci++)
        rbs[ci].setOnCheckedChangeListener(this);
    }
  }

  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    if (isChecked) {
      for (int li = 1; li < APView.LayerNumber; li++)
        for (int ci = 0; ci < APView.CompositionNumber; ci++)
          if (buttonView == this._comp_method_btns[li][ci]) {
            _view.setCompositionMode(li, ci);
            Log.i("peintureroid", "layer:" + li + " comp:" + ci);
          }
    }
  }

  void showWidthSlider() {
    final LinearLayout l = clearOptionalControls();
    final TextView title = new TextView(this);
    final double a = 100 / Math.log(APView.PenWidthMax / APView.PenWidthMin);

    if (_chosen_btn == _width_btn) {
      _chosen_btn = null;
      return;
    }
    _chosen_btn = _width_btn;

    float pw = _view.getPenWidth();
    title.setText("Pen Width = " + pw);
    l.addView(title);
    SeekBar slider = new SeekBar(this);
    slider.setMax(100);
    slider.setProgress((int)(a * Math.log(pw / APView.PenWidthMin)));
    l.addView(slider);
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress,
          boolean fromTouch) {
        progress = Math.max(1, progress);
        title.setText("Pen Width = " + 
            String.format("%f", APView.PenWidthMin * Math.exp(progress / a)));
      }

      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      public void onStopTrackingTouch(SeekBar seekBar) {
        int progress = seekBar.getProgress();
        _view.setPenWidth((float)(APView.PenWidthMin * Math.exp(progress / a)));
        setPaintButtons(_view.paint());
        l.removeAllViews();
        _chosen_btn = null;
      }
    });
  }

  void showColorPanel() {
    final LinearLayout l = clearOptionalControls();

    if (_chosen_btn == _color_btn) {
      _chosen_btn = null;
      return;
    }
    _chosen_btn = _color_btn;

    int pc = _view.getPenColor() | 0xff000000;
    int size = Math.min(_height, _width) / 2;
    ColorPanel cp = new ColorPanel(this, size, pc);

    l.addView(cp.getHueLine());
    l.addView(cp.getSatLum());

    Button picker_btn = new Button(this);
    picker_btn.setText("Spuit");
    l.addView(picker_btn);
    picker_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        _view.setMode(APView.COLOR_PICK_MODE);
        l.removeAllViews();
        _chosen_btn = null;
      }
    });

  }

  void showRGBSlider() {
    final LinearLayout l = clearOptionalControls();
    final TextView title = new TextView(this);

    int pc = _view.getPenColor() | 0xff000000;
    title.setText("set Red, Green, and Blue");
    title.setTextColor(pc);
    l.addView(title);
    final SeekBar rslider = new SeekBar(this);
    rslider.setMax(0xff);
    rslider.setProgress((pc & 0xff0000) >> 16);
    l.addView(rslider);
    final SeekBar gslider = new SeekBar(this);
    gslider.setMax(0xff);
    gslider.setProgress((pc & 0xff00) >> 8);
    l.addView(gslider);
    final SeekBar bslider = new SeekBar(this);
    bslider.setMax(0xff);
    bslider.setProgress((pc & 0xff));
    l.addView(bslider);

    SeekBar.OnSeekBarChangeListener RGBlister = new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress,
          boolean fromTouch) {
        int pc = 0xff000000 | (rslider.getProgress() << 16)
            | (gslider.getProgress() << 8) | (bslider.getProgress());
        title.setTextColor(pc);
      }

      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    };
    rslider.setOnSeekBarChangeListener(RGBlister);
    gslider.setOnSeekBarChangeListener(RGBlister);
    bslider.setOnSeekBarChangeListener(RGBlister);

    Button ok_btn = new Button(this);
    ok_btn.setText("OK");
    l.addView(ok_btn);
    ok_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        int pc = (0xff000000) | (rslider.getProgress() << 16)
            | (gslider.getProgress() << 8) | (bslider.getProgress());
        _view.setPenColor(pc);
        l.removeAllViews();
        setPaintButtons(_view.paint());
        _chosen_btn = null;
      }
    });

    Button picker_btn = new Button(this);
    picker_btn.setText("Color Picker");
    l.addView(picker_btn);
    picker_btn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        _view.setMode(APView.COLOR_PICK_MODE);
        l.removeAllViews();
        _chosen_btn = null;
      }
    });
  }

  void showDensitySlider() {
    final LinearLayout l = clearOptionalControls();
    final TextView title = new TextView(this);

    if (_chosen_btn == _density_btn) {
      _chosen_btn = null;
      return;
    }
    _chosen_btn = _density_btn;

    int pd = _view.getPenDensity();
    title.setText("Pen Density = " + String.format("%.2f", pd / 255.0));
    l.addView(title);
    SeekBar slider = new SeekBar(this);
    slider.setMax(0xff);
    slider.setProgress(pd);
    l.addView(slider);
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress,
          boolean fromTouch) {
        title.setText("Pen Density = "
            + String.format("%.2f", progress / 255.0));
      }

      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      public void onStopTrackingTouch(SeekBar seekBar) {
        int progress = seekBar.getProgress();
        _view.setPenDensity(progress);
        l.removeAllViews();
        setPaintButtons(_view.paint());
        _chosen_btn = null;
      }
    });
  }

  void showChangePenButtons() {
    final LinearLayout l = clearOptionalControls();

    if (_chosen_btn == _change_pen_btn) {
      _chosen_btn = null;
      return;
    }
    _chosen_btn = _change_pen_btn;

    for (int i = 0; i < APView.PaintNumber; i++) {
      Button b = new Button(this);
      if (i == APView.EraserIndex) {
        b.setText("Eraser");
        b.setTextColor(0xff000000);
      } else {
        b.setText("Pen: #" + i);
        b.setTextColor(_view.getPenColor(i));
      }
      l.addView(b);
      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          int index = l.indexOfChild(v);
          _view.change_paint(index);
          if (index == APView.EraserIndex)
            _change_pen_btn.setText("Ers");
          else
            _change_pen_btn.setText("P#" + index);
          l.removeAllViews();
          _chosen_btn = null;
        }
      });
    }
  }
  
  void setCalibrationView(CalibrationView v) {
    v.setVisibility(View.VISIBLE);
    int w = _view.getWidth();
    int h = _view.getHeight();
    int size = v.getWidth();
    if (_calibration_index == 0) {
      v.layout(0, 0, size, size);
      _view._calibration_center_x[0] = (0 + size) / 2;
      _view._calibration_center_y[0] = (0 + size) / 2;
    } else if (_calibration_index == 1) {
      v.layout(w - size, 0, w, size);
      _view._calibration_center_x[0] = (w + w - size) / 2;
      _view._calibration_center_y[0] = (0 + size) / 2;
    } else if (_calibration_index == 2) {
      v.layout(0, h - size, size, h);
      _view._calibration_center_x[0] = (0 + size) / 2;
      _view._calibration_center_y[0] = (h + h - size) / 2;
    } else if (_calibration_index == 3) {
      v.layout(w - size, h - size, w, h);
      _view._calibration_center_x[0] = (w + w - size) / 2;
      _view._calibration_center_y[0] = (h + h - size) / 2;
    }
  }
  
  void calibrationViewClickedWithDiff(CalibrationView cv, 
      float dx, float dy) {
    _view._diff_x[_calibration_index] = dx;
    _view._diff_y[_calibration_index] = dy;
    
    if (_calibration_index == 3) {
      _calibration_view.setVisibility(View.INVISIBLE);
      saveCalibrationResult();
    } else {
      _calibration_index ++;
      setCalibrationView(cv);
    }
  }

  String imageStateKeyName(int li) {
    return keyLayerImage + String.valueOf(li) + ".png";
  }

  String compositionMethodKeyName(int li) {
    return keyCompositionMethod + String.valueOf(li);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(keyAPLayerNumber, APView.LayerNumber);
    outState.putIntArray(keyCompositionMethod, APView._compositionMethods);

    ByteArrayOutputStream ostream;
    for (int li = 0; li < APView.LayerNumber; li++) {
      ostream = new ByteArrayOutputStream();
      if (_view.outputPNG(li, ostream))
        outState.putByteArray(imageStateKeyName(li), ostream.toByteArray());
      else
        outState.remove(imageStateKeyName(li));
    }
  }

  void loadSavedState(Bundle savedState) {
    if (savedState == null) {
      loadPreferences();
      return;
    }

    byte[] png_data;

    for (int li = 0; li < APView.LayerNumber; li++) {
      if (savedState.containsKey(imageStateKeyName(li))) {
        png_data = savedState.getByteArray(imageStateKeyName(li));
        _view.inputImage(li, png_data);
      }
    }
    _view.clear_pen_layer();
    _view.change_layer(0);
    
  }

  private void saveCalibrationResult() {
    SharedPreferences pref = getPreferences(0);
    SharedPreferences.Editor editor = pref.edit();
    for (int i = 0; i < 4; i ++) {
      editor.putFloat(keyCalibrationCenterX + i, _view._calibration_center_x[i]);
      editor.putFloat(keyCalibrationCenterY + i, _view._calibration_center_y[i]);
      editor.putFloat(keyCalibrationDiffX + i, _view._diff_x[i]);
      editor.putFloat(keyCalibrationDiffY + i, _view._diff_y[i]);
    }
    editor.commit();
  }
  
  private void loadCalibrationResult() {
    SharedPreferences pref = getPreferences(0);
    for (int i = 0; i < 4; i ++) {
      _view._calibration_center_x[i] = pref.getFloat(keyCalibrationCenterX + i, 0f);
      _view._calibration_center_y[i] = pref.getFloat(keyCalibrationCenterY + i, 0f);
      _view._diff_x[i] = pref.getFloat(keyCalibrationDiffX + i, 0f);
      _view._diff_y[i] = pref.getFloat(keyCalibrationDiffY + i, 0f);
    }
  }
  
  void savePreferences() {
    SharedPreferences pref = getPreferences(0);
    SharedPreferences.Editor editor = pref.edit();
    editor.putInt(keyAPLayerNumber, APView.LayerNumber);
    for (int i = 0; i < APView.LayerNumber; i++)
      editor.putInt(compositionMethodKeyName(i), APView._compositionMethods[i]);
    editor.commit();
    
    saveCalibrationResult();

    OutputStream ostream;
    for (int li = 0; li < APView.LayerNumber; li++) {
      boolean success = false;
      if (APView._layers[li] != null) {
        try {
          ostream = openFileOutput(imageStateKeyName(li), 0);
          success = _view.outputPNG(li, ostream);
          ostream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (!success) {
        String filename = imageStateKeyName(li);
        for (String fn : fileList())
          if (filename.equals(fn))
            deleteFile(fn);
      }
    }
  }

  void loadPreferences() {
    SharedPreferences pref = getPreferences(0);
    int cm;
    for (int i = 0; i < APView.LayerNumber; i++)
      if (pref.contains(compositionMethodKeyName(i))) {
        cm = pref.getInt(compositionMethodKeyName(i), 0);
        if (cm > 0)
          APView._compositionMethods[i] = cm;
      }

    loadCalibrationResult();
    
    String[] fns = fileList();
    InputStream istream;
    for (int li = 0; li < APView.LayerNumber; li++) {
      String filename = imageStateKeyName(li);
      for (String fn : fns)
        if (fn.equals(filename)) {
          try {
            istream = openFileInput(imageStateKeyName(li));
            _view.inputImage(li, istream);
            istream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
    }

    _view.clear_pen_layer();
    _view.change_layer(0);
  }

  @Override
  protected void onStop() {
    super.onStop();

    savePreferences();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    clearOptionalControls();

    MenuItem item;

    item = menu.add(0, 0, 0, "undo");
    item.setIcon(R.drawable.menuitem_undo);
    item = menu.add(0, 1, 0, "clear image");
    item.setIcon(R.drawable.menuitem_new_image);
    item = menu.add(0, 2, 0, "load to current layer");
    item.setIcon(R.drawable.menuitem_load_image);
    item = menu.add(0, 3, 0, "save image");
    item.setIcon(R.drawable.menuitem_save_image);
    item = menu.add(0, 4, 0, "save current layer");
    item.setIcon(R.drawable.menuitem_save_current_layer);
    item = menu.add(0, 5, 0, "calibration");
    item.setIcon(R.drawable.menuitem_calibration);
    item = menu.add(0, 6, 0, "quit");
    item.setIcon(R.drawable.menuitem_quit);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case 0: // undo
      _view.undo();
      return true;
    case 1: // clear image
    {
      AlertDialog.Builder builder =
        new AlertDialog.Builder(this);
      builder.setTitle("clear image");
      builder.setMessage("Choose which layer you clear");
      builder.setPositiveButton("only current layer",
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
          _view.initCurrentLayer();
        }});
      builder.setNeutralButton("all layers", 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          _view.clear_buf(_width, _height);
        }});

      builder.show();
    }
      return true;
    case 2: // load image to current layer
      _io.setImages(_image_gallery);
      _image_gallery.setVisibility(View.VISIBLE);
      return true;
    case 3: // save image
      _io.saveImage(APView._bitmap, true);
      return true;
    case 4: // save current layer
      _io.saveCurrentLayer();
      return true;
    case 5: // calibration
      for (int i = 0; i < 4; i++)
        _view._diff_x[i] = _view._diff_y[i] = 0;
      saveCalibrationResult();
      
      _calibration_index = 0;
      setCalibrationView(_calibration_view);
      return true;
    case 6: // quit
      savePreferences();
      finish();
    }
    return false;
  }
}