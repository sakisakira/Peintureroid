package jp.sakira.peintureroid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class AP_IO implements OnItemClickListener {
	static AndroidPeinture _ap;
	static APView _view;
	Images _imgs;
	
	int _width, _height;
	
	AP_IO(AndroidPeinture ap, APView v) {
		_ap = ap;
		_view = v;
	}
	
	void setImages(Gallery g) {
		_imgs = new Images(_ap);
		_imgs.setupImages();
		g.setAdapter(_imgs);
	}
	
	void setSize(int w, int h) {
		_width = w;
		_height = h;
	}
	
	static Bitmap loadImage(Uri uri) {
    Bitmap bitmap;
    InputStream imgis;
    BitmapFactory.Options bm_opt;
    int zoomx, zoomy;
    
    ContentResolver cont_reslv = AP_IO._ap.getContentResolver();
    DisplayMetrics dm = new DisplayMetrics();
    AP_IO._ap.getWindowManager().getDefaultDisplay().getMetrics(dm);
    int height = dm.heightPixels;
    int width = dm.widthPixels;

    
    bm_opt = new BitmapFactory.Options();
    
    try {
      imgis = cont_reslv.openInputStream(uri);
      bm_opt.inJustDecodeBounds = true;
      bitmap = BitmapFactory.decodeStream(imgis, 
          null, bm_opt);
      imgis.close();
      
      zoomx = (int) Math.floor((double)bm_opt.outWidth / width);
      zoomy = (int) Math.floor((double)bm_opt.outHeight / height);
      bm_opt.inJustDecodeBounds = false;
      bm_opt.inSampleSize = Math.min(zoomx, zoomy);
      imgis = cont_reslv.openInputStream(uri);
      bitmap = BitmapFactory.decodeStream(imgis,
          null, bm_opt);
      imgis.close();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }

    return bitmap;
	}
	
	Uri saveImage(Bitmap bitmap, boolean saveAsPng) {
	  final String DirName = "/DCIM/Drawing/";
	  final String Title = "Peintureroid";
	  
	  long dateTaken = System.currentTimeMillis();
	  String fn = DateFormat.format("yyyyMMdd-kkmmss", dateTaken).toString() +
	  	(saveAsPng ? ".png" : ".jpg");
	  String mime = (saveAsPng ? "image/png" : "image/jpeg");
	  File dir = new File(Environment.getExternalStorageDirectory() + DirName);
	  if (!dir.exists())
		  dir.mkdirs();
	  File file = new File(dir, fn);
	  
	  try {
		  FileOutputStream outStream = new FileOutputStream(file);
		  bitmap.compress(saveAsPng ?
				  Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
				  100, outStream);
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

	  Uri uri = _ap.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
	  
	  return uri;
	}
	
	void saveCurrentLayer() {
		Bitmap source = APView._layers[_view._layer_index];
		saveImage(source, true);
	}

	class Images extends BaseAdapter {
		String[] _tn_image_ids;
		String[] _image_ids;
		Activity _activity;
		int _count;
		Uri _image_uri, _tn_image_uri;
		ContentResolver _cont_reslv;
		
		Images(Activity a) {
			super();
			_activity = a;
			_image_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
			_tn_image_uri = MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI;
			_cont_reslv = _activity.getContentResolver();
			_tn_image_ids = null;
			_image_ids = null;
		}
		
		void setupImages() {
			int img_key, tn_key, kind_key;
			int count;
			int[] kinds;
			Cursor cursor;
			
			cursor = _activity.managedQuery(_tn_image_uri,
					null, null, null, null);
			if (cursor == null) return;
			
			tn_key = cursor.getColumnIndex(MediaStore.Images.Thumbnails._ID);
			img_key = cursor.getColumnIndex(MediaStore.Images.Thumbnails.IMAGE_ID);
			kind_key = cursor.getColumnIndex(MediaStore.Images.Thumbnails.KIND);

			count = cursor.getCount();

			if (tn_key < 0 || img_key < 0 || kind_key < 0) return;

			_image_ids = new String[count];
			_tn_image_ids = new String[count];
			kinds = new int[count];

			int pos = 0;
			int new_kind;
			String new_id;
			cursor.moveToLast();
			while (!cursor.isBeforeFirst()) {
				new_id = cursor.getString(img_key);
				new_kind = cursor.getInt(kind_key);
				for (int j = 0; j < pos; j ++)
					if (new_id.equals(_image_ids[j])) {
						if (new_kind < kinds[j]) {
							_tn_image_ids[j] = cursor.getString(tn_key);
							kinds[j] = new_kind;
						}
						new_id = null;
						break;
					}
					if (new_id != null) {
						_image_ids[pos] = new_id;
						_tn_image_ids[pos] = cursor.getString(tn_key);
						kinds[pos] = new_kind;
						pos ++;
					}
				cursor.moveToPrevious();
			}	
			_count = pos;
		}

		public int getCount() {
			return _count;
		}

		public Object getItem(int position) {
		  Uri uri = Uri.withAppendedPath(_image_uri, "" + _image_ids[position]);
		  Log.i("peintureroid", "AP_IO getItem uri:" + uri);
		  
		  return AP_IO.loadImage(uri);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ImageAndText view;
			Bitmap bitmap;
			String description = "";
			Cursor cursor;
			int desc_id;
			
			cursor = _activity.managedQuery(_image_uri,
					null, 
					MediaStore.Images.Media._ID + "=" + _image_ids[position], 
					null, null);
			desc_id = cursor.getColumnIndex(MediaStore.Images.Media.DESCRIPTION);
			if (desc_id >= 0) {
				cursor.moveToFirst();
				description = cursor.getString(desc_id);
			}
			
			try {
				bitmap = MediaStore.Images.Media
				  .getBitmap(_cont_reslv,
						  Uri.withAppendedPath(_tn_image_uri,
								  _tn_image_ids[position]));
			} catch (Exception e) {
				e.printStackTrace();
				bitmap = null;
			}
			
            if (convertView == null) {
                view = new ImageAndText(_activity,
                		bitmap, description);
                view.setLayoutParams(new Gallery.
                		LayoutParams(_width / 2, _height / 2 + 20));
            } else {
                view = (ImageAndText)convertView;
                view.setImageAndText(bitmap, description);
            }
            
            return view;
		}
		
		class ImageAndText extends LinearLayout {
			ImageView _img;
			TextView _text;
			
			ImageAndText(Context c, Bitmap b, String str) {
				super(c);
				_img = new ImageView(c);
				_img.setImageBitmap(b);
				_text = new TextView(c);
				_text.setText(str);
				setOrientation(VERTICAL);
				addView(_text);
				addView(_img);
			}
			
			void setImageAndText(Bitmap b, String str) {
				_img.setImageBitmap(b);
				_text.setText(str);
			}
		}
		
	}

	public void onItemClick(AdapterView<?> arg0, View arg1, int id, long arg3) {
		Log.i("APeinture", "selected id:" + id);
		Bitmap bitmap = (Bitmap)_imgs.getItem(id);
		if (bitmap == null) return;
		
		_view.setImageToCurrentLayer(bitmap);
		_view.clear_pen_layer();
		_view.change_layer(_view._layer_index);
		_view.invalidate();
		_ap.setShowSelectors(false);
	}
}
