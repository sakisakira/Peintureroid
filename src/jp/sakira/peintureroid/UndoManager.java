package jp.sakira.peintureroid;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;


import android.graphics.Paint;
import android.util.Log;
import android.widget.TextView;

class APLine {
  public Paint paint = null;
  public float x0, y0, x1, y1;
  
  public APLine() {
    super();
    paint = null;
    x0 = y0 = x1 = y1 = -1;
  }
  
  public APLine(float x0, float y0, float x1, float y1, Paint paint) {
    super();
    this.paint = new Paint(paint);
    this.x0 = x0;
    this.y0 = y0;
    this.x1 = x1;
    this.y1 = y1;
  }
}

class APStroke extends ArrayList<APLine> {
  private static final long serialVersionUID = 1L;
  int layerIndex = 0;
  public Paint paint = null;
  
  public APStroke(Paint p, int l_index) {
    super();
    paint = p;
    layerIndex = l_index;
  }
}

class APStrokeArray extends ArrayList<APStroke> {
  private static final long serialVersionUID = 1;
  
  public APStrokeArray() {
    super();
  }
  
  public APStrokeArray(int l) {
    super(l);
  }
  
  public void trimToSize(int l) {
    if (l < size())
      removeRange(l, size());
  }
}


public class UndoManager {  
  static final int DefaultUndoCountMax = 99;
  static final String keyLayerImage = "UndoLayerImage";
  
  static public boolean undoAcrossLayers = false;
  
  public TextView undoLabel = null;
  private ContextWrapper contextWrapper = null;
  private APView apview = null;
  
  protected int undoCountMax;
  private APStrokeArray strokes = null;
  private APStroke currentStroke = null;
  private Bitmap tempBitmap = null;
  private Bitmap baseBitmap = null;
  private int undoIndex;
  private int currentLayerIndex;
  
  static boolean StoreLayerToMemory = true;
  
  private byte[][] layerByteArrays = new byte[APView.LayerNumber][];
  
  private boolean[] imageSaved = new boolean[APView.LayerNumber];

  public UndoManager(ContextWrapper cw, APView av) {
    super();
    contextWrapper = cw;
    apview = av;
    undoCountMax = DefaultUndoCountMax;
    setup();
  }
  
  public UndoManager(ContextWrapper cw, APView av, int len) {
    super();
    contextWrapper = cw;
    apview = av;
    undoCountMax = len;
    setup();
  }
  
  public void setup() {
    strokes = new APStrokeArray(undoCountMax + 1);
    currentStroke = null;
    undoIndex = 0;
    currentLayerIndex = -1;
    tempBitmap = Bitmap.createBitmap(apview._bitmap.getWidth(),
        apview._bitmap.getHeight(),
        Bitmap.Config.ARGB_8888);
    for (int li = 0; li < APView.LayerNumber; li ++)
      imageSaved[li] = false;
  }
  
  public void setImageToLayer(Bitmap bitmap, int li, boolean force) {
    if (li >= 0 && li < APView.LayerNumber && bitmap != null) {
      if (force || !imageSaved[li]) {
        saveLayerImage(li, bitmap);
        imageSaved[li] = true;
      }        
    }
    updateLabel();
  }
  
  public int getIndex() {
    return undoIndex;
  }
  
  public void startStroke(Paint paint, int layer_index) {
    strokes.trimToSize(undoIndex);
    currentStroke = new APStroke(paint, layer_index);
  }
  
  public void addLine(float x0, float y0, float x1, float y1, Paint p) {
    // this method is called when currentStroke is null during calibration.
    if (currentStroke != null)
      currentStroke.add(new APLine(x0, y0, x1, y1, p));
  }
  
  public void checkLayerChanged(final int li) {
    if (li == currentLayerIndex) return;
    
    currentLayerIndex = li;
    if (undoAcrossLayers) {
      setImageToLayer(apview._bitmap_t, li, false);
    } else {
      strokes.clear();
      undoIndex = 0;
      saveLayerImage(li, apview._bitmap_t.copy(Config.ARGB_8888, false));
    }    
  }

  public int endStroke() {
    checkLayerChanged(currentStroke.layerIndex);
    
    strokes.add(currentStroke);
    currentStroke = null;
    if (strokes.size() > undoCountMax) {
      APStroke s = strokes.remove(0);
      Bitmap bm = loadLayerImage(s.layerIndex);
      if (bm != null) {
        Bitmap bm_ = bm.copy(Bitmap.Config.ARGB_8888, true);
        draw(bm_, s);
        saveLayerImage(s.layerIndex, bm_);
      }
    }
    undoIndex = strokes.size();
    updateLabel();
    return undoIndex;
  }
  
  private void draw(final Bitmap bm, final APStroke stroke) {
    tempBitmap.eraseColor(0);
    Canvas tc = new Canvas(tempBitmap);
    for (APLine l : stroke)
      tc.drawLine(l.x0, l.y0, l.x1, l.y1, l.paint);
    
    Canvas c = new Canvas(bm);
    Paint p = new Paint();
    p.setAlpha(stroke.paint.getAlpha());
    p.setFilterBitmap(true);
    c.drawBitmap(tempBitmap, 0, 0, p);
  }
  
  private void drawStrokesOnLayer(final int stroke_index, final int layer_index) {
    Bitmap ibm = loadLayerImage(layer_index);
    if (ibm == null) return;
    
    Bitmap bm = apview._layers[layer_index];
    Canvas c = new Canvas(bm);
    c.drawBitmap(ibm, 0, 0, null);
    for (int i = 0; i < stroke_index; i ++) {
      APStroke stroke = strokes.get(i);
      if (stroke.layerIndex == layer_index)
        draw(bm, stroke);
    }
  }
  
  private void drawStrokesUntil(final int stroke_index) {
    boolean[] modified = new boolean[APView.LayerNumber];
    for (int li = 0; li < APView.LayerNumber; li ++)
      modified[li] = false;
    
    for (APStroke s : strokes) 
      modified[s.layerIndex] = true;
    
    undoIndex = Math.max(Math.min(stroke_index, strokes.size()), 0);
    for (int li = 0; li < APView.LayerNumber; li ++)
      if (modified[li])
        drawStrokesOnLayer(undoIndex, li);
    
    updateLabel();
  }
  
  public void undo() {
    drawStrokesUntil(undoIndex - 1);
  }
  
  public void redo() {
    drawStrokesUntil(undoIndex + 1);
  }
  
  private void updateLabel() {
    if (undoLabel == null) return;
    
    String str =
        contextWrapper.getString(R.string.Undo) +
        String.format("\n%02d/%02d", 
        getIndex(), strokes.size());
    undoLabel.setText(str);
  }
  
  private String filename(int layer_index) {
    return keyLayerImage + layer_index + ".png";
  }
  
  private boolean saveLayerImage(final int layer_index, final Bitmap bitmap) {
    if (undoAcrossLayers) {
      if (StoreLayerToMemory)
        return saveLayerImageToMemory(layer_index, bitmap);
      else
        return saveLayerImageToFile(layer_index, bitmap);
    } else {
      baseBitmap = bitmap.copy(Config.ARGB_8888, false);
      return true;
    }
  }
  
  private boolean saveLayerImageToMemory(int layer_index, Bitmap bitmap) {
    final ByteArrayOutputStream ostr = new ByteArrayOutputStream(); 
    final boolean result = bitmap.compress(CompressFormat.PNG, 100, ostr);
    layerByteArrays[layer_index] = ostr.toByteArray();
    try {
      ostr.close();
    } catch (IOException e) {
      Log.i("peintureroid", "IOException in saveLayerImageToMemory()");
      return false;
    }
    return result;
  }
  
  private boolean saveLayerImageToFile(final int layer_index, final Bitmap bitmap_) {
    final String fn = filename(layer_index);
    
    OutputStream ostr;
    boolean result;
    try {
      ostr = contextWrapper.openFileOutput(fn, 0);
      result = bitmap_.compress(CompressFormat.PNG, 100, ostr);
      ostr.close();
    } catch (FileNotFoundException e) {
      Log.i("peintureroid", "FileNotFoundException in saveLayerImage()");
      return false;
    } catch (IOException e) {
      Log.i("peintureroid", "IOException in saveLayerImage()");
      return false;
    }
    return result;
  }
  
  private Bitmap loadLayerImage(final int layer_index) {
    if (undoAcrossLayers) {
      if (StoreLayerToMemory)
        return loadLayerImageFromMemory(layer_index);
      else
        return loadLayerImageFromFile(layer_index);
    } else {
      return baseBitmap;
    }
  }
  
  private Bitmap loadLayerImageFromMemory(final int layer_index) {
    return BitmapFactory.decodeByteArray(layerByteArrays[layer_index], 
            0, layerByteArrays[layer_index].length);
  }
  
  private Bitmap loadLayerImageFromFile(final int layer_index) {
    String fn = filename(layer_index);
    Bitmap bitmap = null;
    
    InputStream istr;
    try {
      istr = contextWrapper.openFileInput(fn);
      bitmap = BitmapFactory.decodeStream(istr);    
      istr.close();
    } catch(FileNotFoundException e) {
      Log.i("peintureroid", "FileNotFoundException in loadLayerImage()");
      return null;
    } catch (IOException e) {
      Log.i("peintureroid", "IOException in loadLayerImage()");
      return null;
    }
    return bitmap;
  }
  
}
