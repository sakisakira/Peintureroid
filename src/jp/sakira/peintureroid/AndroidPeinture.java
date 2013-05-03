package jp.sakira.peintureroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;

public class AndroidPeinture extends FragmentActivity implements
    android.widget.CompoundButton.OnCheckedChangeListener {
  private int _height, _width;
  public SeekBar _zoom_slider;
  public View _spuit_view;
  private CalibrationView _calibration_view;
  private int _calibration_index;

  private APView _view = null;
  private ImageInputOutput _image_io;
  private boolean _getting_image_from_gallery = false;

  public Button _layer_btn, _width_btn, _color_btn, _density_btn,
      _change_pen_btn;
  Button _chosen_btn;
  public TextView _message_area;

  static final String keyAPLayerNumber = "APLayerNumber";
  static final String keyLayerImage = "LayerImage";
  static final String keyCompositionMethod = "CompositionMethod";
  static final String keyCalibrationCenterX = "CalibrationCenterX";
  static final String keyCalibrationCenterY = "CalibrationCenterY";
  static final String keyCalibrationDiffX = "CalibrationDiffX";
  static final String keyCalibrationDiffY = "CalibrationDiffY";
  static final String keyMaxPressure = "MaxPressure";
  static final String keyMaxTapsize = "MaxTapsize";
  static final String keyFingerFunction = "FingerFunction";
  static final String keyUndoAcrossLayer = "UndoAcrossLayer";
  
  static final int codeRequestGalleryForSingleLayer = 0;
  static final int codeRequestGalleryForAllLayers = 1;
  
  RadioButton[][] _comp_method_btns;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    DisplayMetrics dm = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(dm);
    _height = dm.heightPixels;
    _width = dm.widthPixels;
    
    setContentView(R.layout.main);
    _view = (APView) findViewById(R.id.apview);
    _view.start(this, _width, _height);

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
//    loadPreferences();

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

    _image_io = new ImageInputOutput(this, _view);
    _getting_image_from_gallery = false;
    
    setRequestedOrientation(getResources().getConfiguration().orientation);
  }
  

  @Override
  protected void onStop() {
    super.onStop();

    savePreferences();
  }
  
  @Override
  protected void onStart() {
    super.onStart();
    
    if (_getting_image_from_gallery)
      _getting_image_from_gallery = false;
    else
      loadPreferences();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Uri idata = intent.getData();
    Log.i("peintureroid", "onNewIntent intent:" + idata);
    if (idata == null) return;
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
      _image_io.readImageAsPtpt(uri);
      _view.clearPenLayer();
      _view.changeLayer(0);
    }
  }
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(keyAPLayerNumber, APView.LayerNumber);
    final int[] cms = new int[_view._compositionMethods.length];
    for (int i = 0; i < _view._compositionMethods.length; i ++)
      cms[i] = _view._compositionMethods[i].ordinal();
    outState.putIntArray(keyCompositionMethod, cms);

    ByteArrayOutputStream ostream;
    for (int li = 0; li < APView.LayerNumber; li++) {
      ostream = new ByteArrayOutputStream();
      if (_image_io.outputPNG(li, ostream))
        outState.putByteArray(imageStateKeyName(li), ostream.toByteArray());
      else
        outState.remove(imageStateKeyName(li));
    }
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle state) {
    super.onRestoreInstanceState(state);
    
    loadSavedState(state);
  }

  private void loadSavedState(Bundle savedState) {
    if (savedState == null) {
      loadPreferences();
      return;
    }

    byte[] png_data;

    for (int li = 0; li < APView.LayerNumber; li++) {
      if (savedState.containsKey(imageStateKeyName(li))) {
        png_data = savedState.getByteArray(imageStateKeyName(li));
        _image_io.inputImage(li, png_data);
      }
    }
    _view.clearPenLayer();
    _view.changeLayer(0);
    
    final int[] cms = savedState.getIntArray(keyCompositionMethod);
    _view._compositionMethods = new APView.Composition[cms.length];
    for (int i = 0; i < cms.length; i ++)
      _view._compositionMethods[i] = APView.Composition.values()[cms[i]];
  }

  public void DrawMode(View v) {
    _view.setMode(ViewMode.DRAW);
  }

  public void ShiftMode(View v) {
    _view.setMode(ViewMode.SHIFT);
  }
  
  public void undoButtonPressed(View v) {
    _view.undo();
  }
  
  public void redoButtonPressed(View v) {
    _view.redo();
  }

  LinearLayout clearOptionalControls() {
    _view.setMode();
    LinearLayout l = (LinearLayout) findViewById(R.id.optional_controls);
    if (l.getChildCount() > 0)
      l.removeAllViews();
    _calibration_view.setVisibility(View.INVISIBLE);
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

  public void showAboutAlert(View v) {
    String versionName;
    PackageManager pm = getPackageManager();
    try {
      PackageInfo info = pm.getPackageInfo(getPackageName(),
          PackageManager.GET_ACTIVITIES);
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
        rbs[_view._compositionMethods[i].ordinal()].setChecked(true);
        Log.i("peintureroid", "layer:" + i + " comp:"
            + _view._compositionMethods[i]);
        hl.addView(rg);
      }
      l.addView(hl);
      this._comp_method_btns[i] = rbs;

      b.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          LinearLayout lh = (LinearLayout)v.getParent();
          int index = l.indexOfChild(lh);
          _view.changeLayer(index);
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
        for (int ci = 0; ci < APView.Composition.values().length; ci++)
          if (buttonView == this._comp_method_btns[li][ci]) {
            _view.setCompositionMode(li, APView.Composition.values()[ci]);
            Log.i("peintureroid", "layer:" + li + " comp:" + ci);
          }
    }
  }

  void showWidthSlider() {
    final LinearLayout l = clearOptionalControls();
    final TextView title = new TextView(this);
    final double a = 100 / Math.log(APView.penWidthMax / APView.PenWidthMin);

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
        float w = (float)(APView.PenWidthMin * Math.exp(progress / a));
        _view.setPenWidth(w);
        progress = Math.max(1, progress);
        title.setText("Pen Width = " + String.format("%f", w));
        _view.drawCurrentPen();
      }

      public void onStartTrackingTouch(SeekBar seekBar) {}

      public void onStopTrackingTouch(SeekBar seekBar) {
        setPaintButtons(_view.paint());
        l.removeAllViews();
        _chosen_btn = null;
        _view.clearCurrentPen();
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
        _view.setMode(ViewMode.COLOR_PICK);
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
        _view.setMode(ViewMode.COLOR_PICK);
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
        _view.setPenDensity(progress);
        _view.drawCurrentPen();
      }

      public void onStartTrackingTouch(SeekBar seekBar) {}

      public void onStopTrackingTouch(SeekBar seekBar) {
        l.removeAllViews();
        setPaintButtons(_view.paint());
        _chosen_btn = null;
        _view.clearCurrentPen();
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
  
  void showPressureTapsizeCalibration() {
    final LinearLayout l = clearOptionalControls();
    _chosen_btn = null;
    
    int size = Math.min(_height, _width) / 2;
    PressureTapsizeCalibrationView cview =
        new PressureTapsizeCalibrationView(this, _view);
    cview.layout(0, 0, size, size);
    TextView tv;
    tv = new TextView(this);
    tv.setText(getString(R.string.Pressure));
    l.addView(tv);
    l.addView(cview.pressure_slider);
    tv = new TextView(this);
    tv.setText(getString(R.string.TapSize));
    l.addView(tv);
    l.addView(cview.tapsize_slider);
    tv = new TextView(this);
    tv.setText(getString(R.string.drag_the_blue_rect));
    l.addView(tv);
    l.addView(cview);
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
  
  private void setFingerFunction() {
    final AlertDialog.Builder builder =
        new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.finger_function_title));
//    builder.setMessage(getString(R.string.finger_function_description));
    
    final APView.FingerFunction[] ffs = APView.FingerFunction.values();
    final String[] ffnames = new String[ffs.length];
    for (int i = 0; i < ffnames.length; i ++) 
      switch (ffs[i]) {
      case Normal:
        ffnames[i] = getString(R.string.finger_function_normal); break;
      case Eraser:
        ffnames[i] = getString(R.string.finger_function_eraser); break;
      case Shift:
        ffnames[i] = getString(R.string.finger_function_shift); break;
      case None:
        ffnames[i] = getString(R.string.finger_function_none); break;
      }
    
    builder.setSingleChoiceItems(ffnames,
        _view.fingerFunction().ordinal(), new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            _view.setFingerFunction(ffs[which]);
            dialog.dismiss();
          }
        });
   
    builder.show();
  }
  
  private void setUndoAcrossLayer() {
    final AlertDialog.Builder builder =
        new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.undo_across_layer));
    
    String[] names = new String[2];
    names[0] = getString(R.string.single_layer);
    names[1] = getString(R.string.across_layers);
    builder.setSingleChoiceItems(names,
        UndoManager.undoAcrossLayers ? 1 : 0,
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) 
              UndoManager.undoAcrossLayers = false;
            else if (which == 1)
              UndoManager.undoAcrossLayers = true;
            _view._undo_manager.setup();
            _view.changeLayer(_view._layer_index);
            dialog.dismiss();
          }
        });
   
    builder.show();
  }

  private String imageStateKeyName(int li) {
    return keyLayerImage + String.valueOf(li) + ".png";
  }

  private String compositionMethodKeyName(int li) {
    return keyCompositionMethod + String.valueOf(li);
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
  
  private void savePreferences() {
    SharedPreferences pref = getPreferences(0);
    SharedPreferences.Editor editor = pref.edit();
    editor.putInt(keyAPLayerNumber, APView.LayerNumber);
    for (int i = 0; i < APView.LayerNumber; i++)
      editor.putInt(compositionMethodKeyName(i), 
          _view._compositionMethods[i].ordinal());
    editor.putFloat(keyMaxPressure, _view._max_pressure);
    editor.putFloat(keyMaxTapsize, _view._max_tapsize);
    editor.putString(keyFingerFunction, _view.fingerFunction().name());
    editor.putBoolean(keyUndoAcrossLayer, UndoManager.undoAcrossLayers);
    editor.commit();
    
    saveCalibrationResult();

    OutputStream ostream;
    for (int li = 0; li < APView.LayerNumber; li++) {
      boolean success = false;
      if (_view._layers[li] != null) {
        try {
          ostream = openFileOutput(imageStateKeyName(li), 0);
          success = _image_io.outputPNG(li, ostream);
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

  private void loadPreferences() {
    SharedPreferences pref = getPreferences(0);
    int cm_;
    for (int i = 0; i < APView.LayerNumber; i++)
      if (pref.contains(compositionMethodKeyName(i))) {
        cm_ = pref.getInt(compositionMethodKeyName(i), 0);
        if (cm_ > 0) {
          final APView.Composition cm = APView.Composition.values()[cm_];  
          _view._compositionMethods[i] = cm;
        }
      }
    _view._max_pressure = pref.getFloat(keyMaxPressure, 0.01f);
    _view._max_tapsize = pref.getFloat(keyMaxTapsize, 0.01f);
    String ffname = pref.getString(keyFingerFunction, APView.FingerFunction.Normal.name());
    for (APView.FingerFunction ff : APView.FingerFunction.values())
      if (ff.name().equals(ffname))
        _view.setFingerFunction(ff);
    UndoManager.undoAcrossLayers = pref.getBoolean(keyUndoAcrossLayer, false);

    loadCalibrationResult();
    
    String[] fns = fileList();
    InputStream istream;
    for (int li = 0; li < APView.LayerNumber; li++) {
      String filename = imageStateKeyName(li);
      for (String fn : fns)
        if (fn.equals(filename)) {
          try {
            istream = openFileInput(imageStateKeyName(li));
            _image_io.inputImage(li, istream);
            istream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
    }

    _view.clearPenLayer();
    _view.changeLayer(0);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    clearOptionalControls();

    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.mainmenu, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
    case R.id.menu_clear_image: // clear image
    {
      AlertDialog.Builder builder =
        new AlertDialog.Builder(this);
      builder.setTitle(getString(R.string.clear_image));
      builder.setMessage(getString(R.string.choose_which_layer_you_clear));
      builder.setPositiveButton(getString(R.string.only_current_layer),
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
          _view.initCurrentLayer();
        }});
      builder.setNeutralButton(getString(R.string.all_layers),
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          _view.clearBuffer(_width, _height);
        }});

      builder.show();
    }
      return true;
    case R.id.menu_load_image: // load image to all layers
      intent = new Intent();
      intent.setType("image/png");
      intent.setAction(Intent.ACTION_GET_CONTENT);
      _getting_image_from_gallery = true;
      startActivityForResult(intent, codeRequestGalleryForAllLayers);
      return true;      
    case R.id.menu_load_layer: // load image to current layer
      intent = new Intent();
      intent.setType("image/*");
      intent.setAction(Intent.ACTION_GET_CONTENT);
      _getting_image_from_gallery = true;
      startActivityForResult(intent, codeRequestGalleryForSingleLayer);
      return true;
    case R.id.menu_save_image: // save image
      _image_io.writeImageAsPtpt();
      return true;
    case R.id.menu_save_layer: // save current layer
      _image_io.writeImageOfCurrentLayer();
      return true;
    case R.id.menu_calibration: // calibration
      for (int i = 0; i < 4; i++)
        _view._diff_x[i] = _view._diff_y[i] = 0;
      saveCalibrationResult();     
      _calibration_index = 0;
      setCalibrationView(_calibration_view);
      return true;
    case R.id.menu_pressure_width: // pressure/tap-width calibration
      showPressureTapsizeCalibration();
      return true;
    case R.id.menu_finger_function:
      setFingerFunction();
      return true;
    case R.id.menu_undo_across_layer:
      setUndoAcrossLayer();
      return true;
    case R.id.menu_quit: // quit
      savePreferences();
      finish();
    }
    return false;
  }
  
  @Override
  public void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    
    if (request == codeRequestGalleryForSingleLayer && result == Activity.RESULT_OK) {
      _image_io.loadImageToCurrentLayer(data.getData());
      _view.clearPenLayer();
      _view.changeLayer(_view._layer_index);
      _view.invalidate();
    } else if (request == codeRequestGalleryForAllLayers && result == Activity.RESULT_OK) {
      _image_io.readImageAsPtpt(data.getData());
      _view.clearPenLayer();
      _view.changeLayer(_view._layer_index);
      _layer_btn.setText("L:" + _view._layer_index);
      _view.invalidate();
    }
  }
  
}