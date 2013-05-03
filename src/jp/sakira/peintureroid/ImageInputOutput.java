package jp.sakira.peintureroid;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.text.format.DateFormat;
import android.util.Log;

public class ImageInputOutput {
  private Activity activity;
  private APView apview;
  
  public ImageInputOutput(Activity a, APView v) {
    super();
    activity = a;
    apview = v;
  }
  
  private boolean outputPNG(OutputStream os) {
    return apview._bitmap.compress(CompressFormat.PNG, 100, os);
  }
  
  public boolean outputPNG(int li, OutputStream os) {
    if (li < 0 || li >= APView.LayerNumber) return false;
    if (apview._layers[li] == null) return false;
    
    return apview._layers[li].compress(CompressFormat.PNG, 100, os);
  }
      
  private boolean outputCurrentLayerPNG(OutputStream os) {
    return outputPNG(apview._layer_index, os);
  }

  private boolean outputRAW(int li, OutputStream os) {
    if (li < 0 || li >= APView.LayerNumber) return false;
    
    ByteBuffer bbuf = ByteBuffer.allocate(apview._width * apview._height * 4);
    apview._layers[li].copyPixelsToBuffer(bbuf);
    byte[] data = bbuf.array();
    try {
    os.write(data, 0, data.length);
  } catch (IOException e1) {
    e1.printStackTrace();
    return false;
  }
  return true;
  }
  
private boolean inputRAW(int li, InputStream is) {
  if (li < 0 || li >= APView.LayerNumber)
    return false;

  byte[] data = new byte[apview._width * apview._height * 4];
  try {
    is.read(data, 0, data.length);
  } catch (IOException e1) {
    e1.printStackTrace();
    return false;
  }
  IntBuffer ibuf = ByteBuffer.wrap(data).asIntBuffer();
  int[] pixels = new int[apview._width * apview._height];
  ibuf.get(pixels);
  Bitmap temp_bmp = Bitmap.createBitmap(pixels, apview._width, apview._height,
      Bitmap.Config.ARGB_8888);
  Canvas cur_canvas = new Canvas(apview._layers[li]);
  cur_canvas.drawBitmap(temp_bmp, 0, 0, apview._src_paint);
  temp_bmp.recycle();
  apview._undo_manager.setImageToLayer(apview._layers[li], li, true);
  return true;
}
  
  public void inputImage(int li, InputStream is) {
    if (li < 0 || li >= APView.LayerNumber) return;
    
    if (apview._layers[li] == null)
      apview.initLayer(li);
    Bitmap temp_bmp = BitmapFactory.decodeStream(is);
    if (temp_bmp == null) {
      apview._layers[li].recycle();
      apview._layers[li] = null;
    } else {
      Canvas cur_canvas = new Canvas(apview._layers[li]);
      cur_canvas.drawBitmap(temp_bmp, 0, 0, apview._src_paint);
      temp_bmp.recycle();
      apview._undo_manager.setImageToLayer(apview._layers[li], li, true);
    }
  }
  
  private void inputImageToCurrentLayer(InputStream is) {
    inputImage(apview._layer_index, is);
  }
  
  public void inputImage(int li, byte[] imgdata) {
    if (li < 0 || li >= APView.LayerNumber) return;
    
    Bitmap temp_bmp = BitmapFactory.decodeByteArray(imgdata, 0, imgdata.length);
    if (temp_bmp != null) {
      Canvas cur_canvas = new Canvas(apview._layers[li]);
      cur_canvas.drawBitmap(temp_bmp, 0, 0, apview._src_paint);
      temp_bmp.recycle();
    }
  }

  public void setImage(final int li, final Bitmap b) {
    final int bw = b.getWidth();
    final int bh = b.getHeight();
    float ratio = (float)bh / apview._height;
    Rect rect;

    if (ratio * apview._width <= bw) {
      rect = new Rect((int)(bw - ratio * apview._width) / 2, 0,
          (int)(bw + ratio * apview._width) / 2, bh);
    } else {
      ratio = (float)bw / apview._width;
      rect = new Rect(0, (int)(bh - ratio * apview._height) / 2,
          bw, (int)(bh + ratio * apview._width) / 2);
    }
    
    final Canvas cur_canvas = new Canvas(apview._layers[li]);    
    cur_canvas.drawBitmap(b, rect, 
        new Rect(0, 0, apview._width, apview._height),
        apview._src_paint);
    apview._undo_manager.setup();
    apview._undo_manager.setImageToLayer(apview._layers[li], li, true);
  }
  
  public void setImageToCurrentLayer(final Bitmap b) {
    apview._undo_manager.setup();
    setImage(apview._layer_index, b);
  }
  
  public boolean loadImageToLayer(final int layer_i, final Uri uri) {
    final Bitmap bitmap = imageFromUri(uri);
    if (bitmap != null) {
      setImage(layer_i, bitmap);
      return true;
    } else {
      return false;
    }
  }
  
  public boolean loadImageToCurrentLayer(final Uri uri) {
    final Bitmap bitmap = imageFromUri(uri);
    if (bitmap != null) {
      setImageToCurrentLayer(bitmap);
      return true;
    } else {
      return false;
    }
  }
  
  private byte[] bytesFromUri(final Uri uri) {
    final ContentResolver cont_reslv = activity.getContentResolver();
    final byte[] buffer = new byte[64 * 1024];
    final ByteArrayOutputStream outs = new ByteArrayOutputStream(); 
    
    try {
      final InputStream ins = cont_reslv.openInputStream(uri);
      int len = ins.read(buffer);
      while (len > 0) {
        outs.write(buffer, 0, len);
        len = ins.read(buffer); 
      }
      ins.close();
      outs.close();
    } catch (IOException e) {
      Log.i("peintureroid", "" + e);
    }

    return outs.toByteArray(); 
  }
  
  private Bitmap imageFromUri(final Uri uri) {
    final ContentResolver cont_reslv = activity.getContentResolver();    
    final BitmapFactory.Options bm_opt = new BitmapFactory.Options();
    Bitmap bitmap;
    
    try {
      InputStream imgis = cont_reslv.openInputStream(uri);
      bm_opt.inJustDecodeBounds = true;
      bitmap = BitmapFactory.decodeStream(imgis, 
          null, bm_opt);
      imgis.close();
      
      final int zoomx = (int) Math.floor((double)bm_opt.outWidth / apview._width);
      final int zoomy = (int) Math.floor((double)bm_opt.outHeight / apview._height);
      bm_opt.inJustDecodeBounds = false;
      bm_opt.inSampleSize = Math.min(zoomx, zoomy);
      imgis = cont_reslv.openInputStream(uri);
      bitmap = BitmapFactory.decodeStream(imgis,
          null, bm_opt);
      imgis.close();
    } catch (IOException e) {
      Log.i("peintureroid", "" + e);
      return null;
    }

    return bitmap;
  }
  
  private Uri saveAsPng(final byte[] data) {
    final String DirName = "/DCIM/PtPt/";
    final String Title = "Peintureroid";
    
    final long dateTaken = System.currentTimeMillis();
    final String fn = 
        DateFormat.format("yyyyMMdd-kkmmss", dateTaken).toString() +
        ".png";
    final String mime = "image/png";
    final File dir = new File(Environment.getExternalStorageDirectory() + DirName);
    if (!dir.exists())
      dir.mkdirs();
    final File file = new File(dir, fn);

    try {
      final FileOutputStream outStream = new FileOutputStream(file);
      outStream.write(data);
      outStream.close();
    } catch (Exception e) {
      Log.e("peintureroid", "exception while writing image" + e);
      return null;
    }

    ContentValues values = new ContentValues();
    values.put(Media.DISPLAY_NAME, fn);
    values.put(Media.TITLE, Title);
    values.put(Media.DESCRIPTION, "Peintureroid");
    values.put(Media.DATE_TAKEN, dateTaken);
    values.put(Media.DATE_ADDED, dateTaken / 1000);
    values.put(Media.DATE_MODIFIED, dateTaken / 1000);
    values.put(Media.MIME_TYPE, mime);
    values.put(Media.ORIENTATION, 0);
    values.put(Media.DATA, file.getPath());
    values.put(Media.SIZE, file.length());

    Uri uri = activity.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
    
    return uri;
  }
  
  private Uri writeImage(final Bitmap bitmap) {
    final ByteArrayOutputStream bostr =
        new ByteArrayOutputStream();
    bitmap.compress(CompressFormat.PNG, 100, bostr);
    return saveAsPng(bostr.toByteArray());
  }
  
  public Uri writeImageOfLayer(int li) {
    return writeImage(apview._layers[li]);
  }
  
  public Uri writeImageOfCurrentLayer() {
    return writeImageOfLayer(apview._layer_index);
  }
  
  private PtptFormat ptptFromLayers() {
    final ArrayList<Integer> lis = new ArrayList<Integer>(APView.LayerNumber);
    for (int i = 0; i < APView.LayerNumber; i ++)
      if (apview._layers[i] != null)
        lis.add(i);

    final PtptFormat ptpt = new PtptFormat(lis.size());
    for (final int li : lis) {
      ptpt.setAlpha(li, (byte)0xff);
      switch (apview._compositionMethods[li]) {
      case Multiply:
        ptpt.setCompositionMethod(li, PtptFormat.Composition.Multiply);
        ptpt.setPaperColor(li, 0xffffffff);
        break;
      case Screen:
        ptpt.setCompositionMethod(li, PtptFormat.Composition.Screen);
        ptpt.setPaperColor(li, 0xff000000);
        break;
      case Normal:
        ptpt.setCompositionMethod(li, PtptFormat.Composition.Normal);
        ptpt.setPaperColor(li, 0xffffffff);
        break;
      }
      ptpt.setPaperColor(li, 0xffffffff);
      ptpt.addLayer(apview._layers[li]);
    }
    return ptpt;
  }
  
  public Uri writeImageAsPtpt() {
    final PtptFormat ptpt =  ptptFromLayers();
    final ByteArrayOutputStream os = new ByteArrayOutputStream();
    ptpt.write(os, apview._bitmap);
    return saveAsPng(os.toByteArray());
  }
  
  public boolean readImageAsPtpt(final Uri uri) {
    final PtptFormat ptpt = new PtptFormat();
    final byte[] bytes = bytesFromUri(uri);
    if (bytes == null) return false;
    
    InputStream ins;
    try {
      ins = activity.getContentResolver().openInputStream(uri);
    } catch (FileNotFoundException e) {
      Log.i("peinturerdoi", "" + e);
      return false;
    }
    if (ins == null) return false;
    
    ptpt.read(ins);
    apview.clearBuffer(apview._width, apview._height);
    for (int li = 0; li < ptpt.numOfLayers(); li ++) {
      apview.initLayer(li);
      setImage(li, ptpt.layer(li));
      switch (ptpt.compositionMethod(li)) {
      case Screen:
        apview.setCompositionMode(li, APView.Composition.Screen);
        break;
      case Normal:
        apview.setCompositionMode(li, APView.Composition.Normal);
        break;
      case Multiply:
      default:
        apview.setCompositionMode(li, APView.Composition.Multiply);
      }
    }
    apview._layer_index = 0;
    return true;
  }

}
