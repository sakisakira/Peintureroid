package jp.sakira.peintureroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.zip.CRC32;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.util.Log;

public class PtptFormat {
  private final int PNGHeaderLength = 8;
  private final byte[] PNGHeaderChars = 
    {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
  private final byte[] PtPtChunkName = "ptPt".getBytes();
  
  enum Composition {Minimal, Multiply, Saturation,
    Color, Normal, Maximam, Mask, AlphaChannel,
    Screen, Dodge}
  
  protected class LayerInfo {
    int offset, length;
    Composition compMethod;
    byte alpha;
    int paperColor;
  }
  
  protected class ThumbnailInfo {
    int offset, length;
  }
  
  private int currentLayerIndex;
  private ArrayList<LayerInfo> layerInfos = new ArrayList<LayerInfo>();
  private ThumbnailInfo thumbnailInfo = new ThumbnailInfo();
  private ByteBuffer ptptData;
  
  public PtptFormat() {
    super();
    ptptData = ByteBuffer.allocate(0);
    setNumOfLayers(1, true);
  }
  
  public PtptFormat(int n) {
    super();
    ptptData = ByteBuffer.allocate(0);
    setNumOfLayers(n, true);
  }
  
  public void setNumOfLayers(int n) {
    setNumOfLayers(n, false);
  }

  public void setNumOfLayers(int n, boolean reset) {
    while (n < layerInfos.size())
      layerInfos.remove(n);
    while (layerInfos.size() < n)
      layerInfos.add(new LayerInfo());
    
    currentLayerIndex = 0;
    
    if (reset) {
      ptptData = ByteBuffer.allocate(0x20 + 0x10 * n);
    }
  }

  private void prepareData() {
    ptptData.position(0);
    
    // file header
    ptptData.put((byte)'P');
    ptptData.put((byte)'t');
    ptptData.put((byte)'P');
    ptptData.put((byte)'t');
    ptptData.put((byte)0);
    ptptData.put((byte)'1');
    ptptData.put((byte)'.');
    ptptData.put((byte)'0');
        
    // information header
    ptptData.put(0x10, (byte)layerInfos.size());
    
    // thumbnail info
    for (int j = 0; j < 4; j ++) {
      ptptData.put(0x14 + j, 
          (byte)((thumbnailInfo.offset >> (24 - 8 * j)) & 0xff));
      ptptData.put(0x18 + j,
          (byte)((thumbnailInfo.length >> (24 - 8 * j)) & 0xff));
    }
    
    // layer info
    for (int k = 0; k < layerInfos.size(); k ++) {
      for (int j = 0; j < 4; j ++) {
        ptptData.put(0x20 + k * 0x10 + j,
            (byte)((layerInfos.get(k).offset >> (24 - 8 * j)) & 0xff));
        ptptData.put(0x24 + k * 0x10 + j,
            (byte)((layerInfos.get(k).length >> (24 - 8 * j)) & 0xff));
      }
      
      ptptData.put(0x28 + k * 0x10,
          (byte)layerInfos.get(k).compMethod.ordinal());
      ptptData.put(0x29 + k * 0x10,
          layerInfos.get(k).alpha);
      ptptData.put(0x2a + k + 0x10,
          (byte)((layerInfos.get(k).paperColor >> 16) & 0xff)); // red
      ptptData.put(0x2b + k + 0x10,
          (byte)((layerInfos.get(k).paperColor >>  8) & 0xff)); // green
      ptptData.put(0x2c + k + 0x10,
          (byte)((layerInfos.get(k).paperColor >>  0) & 0xff)); // blue
    }
  }
  
  private void parseData() {
    if (!isPtpt()) return;
    
    // information header
    setNumOfLayers(ptptData.get(0x10));
    
    int offset, length;
    for (int k = 0; k < layerInfos.size(); k ++) {
      offset = length = 0;
      for (int j = 0; j < 4; j ++) {
        offset = (offset << 8) +
            (((int)ptptData.get(0x20 + k * 0x10 + j)) & 0xff);
        length = (length << 8) +
            (((int)ptptData.get(0x24 + k * 0x10 + j)) & 0xff);
      }
      layerInfos.get(k).offset = offset;
      layerInfos.get(k).length = length;
      
      layerInfos.get(k).compMethod = 
          Composition.values()[ptptData.get(0x28 + k * 0x10)];
      layerInfos.get(k).alpha = ptptData.get(0x29 + k * 0x10);
      
      final byte r = ptptData.get(0x2a + k * 0x10);
      final byte g = ptptData.get(0x2b + k * 0x10);
      final byte b = ptptData.get(0x2c + k * 0x10);
      layerInfos.get(k).paperColor = 0xff000000 |
          ((r << 16) & 0x00ff0000) |
          ((g << 8) & 0x0000ff00) | (b & 0x000000ff);
    }
    
    offset = length = 0;
    for (int j = 0; j < 4; j ++) {
      offset = (offset << 8) + (((int)ptptData.get(0x14 + j)) & 0xff);
      length = (length << 8) + (((int)ptptData.get(0x18 + j)) & 0xff);
    }
    thumbnailInfo.offset = offset;
    thumbnailInfo.length = length;
  }
  
  public boolean isPtpt() {
    return (ptptData.get(0) == 'P' &&
        ptptData.get(1) == 't' &&
        ptptData.get(2) == 'P' &&
        ptptData.get(3) == 't' &&
        ptptData.get(4) == 0 &&
        ptptData.get(5) >= '0' && ptptData.get(5) <= '9' &&
        ptptData.get(6) == '.' &&
        ptptData.get(7) >= '0' && ptptData.get(7) <= '9');
  }
  
  private ArrayList<ByteBuffer> chunksOfPNG(final ByteBuffer png_data) {
    png_data.position(0);
    if (png_data.limit() < PNGHeaderLength) return null;
    for (int i = 0; i < PNGHeaderLength; i ++)
      if (png_data.get(i) != PNGHeaderChars[i]) return null;
    
    png_data.order(ByteOrder.BIG_ENDIAN);
    final byte[] png_bytes = png_data.array();
    ArrayList<ByteBuffer> chunks = new ArrayList<ByteBuffer>();
    int start_index = PNGHeaderLength;
    while (start_index < png_data.limit()) {
      final int len = png_data.getInt(start_index);
      if (start_index + 8 + len + 4 > png_data.limit()) break;
      final ByteBuffer chdat = ByteBuffer.allocate(8 + len + 4);
      chdat.put(png_bytes, start_index, 8 + len + 4);
      chunks.add(chdat);
      start_index += 8 + len + 4;
    }
      
    return chunks;
  } 
  
  private ByteBuffer ptptChunk() {
    prepareData();
    
    final ByteBuffer body = 
        ByteBuffer.allocate(PtPtChunkName.length + ptptData.limit());
    body.put(PtPtChunkName);
    ptptData.position(0);
    body.put(ptptData);
    final CRC32 crc32 = new CRC32();
    crc32.update(body.array());
    final int size = ptptData.limit();
    
    final ByteBuffer chunk = 
        ByteBuffer.allocate(4 + body.limit() + 4);
    chunk.putInt(size);
    body.position(0);
    chunk.put(body);
    chunk.putInt((int)crc32.getValue());
    
    return chunk;
  }
  
  private ByteBuffer pngFromChunks(ArrayList<ByteBuffer> chunks) {
    int png_len = PNGHeaderLength;
    for (final ByteBuffer ch : chunks)
      png_len += ch.limit();
    ByteBuffer png_dat = ByteBuffer.allocate(png_len);
    
    png_dat.put(PNGHeaderChars);
    for (final ByteBuffer ch : chunks) {
      ch.position(0);
      png_dat.put(ch);
    }
    
    return png_dat;
  }
  
  private ByteBuffer findPtptChunkFrom(ArrayList<ByteBuffer> chunk_array) {
    if (chunk_array == null) return null;
    for (final ByteBuffer chunk : chunk_array) {
      if (chunk.get(4) == PtPtChunkName[0] &&
          chunk.get(5) == PtPtChunkName[1] &&
          chunk.get(6) == PtPtChunkName[2] &&
          chunk.get(7) == PtPtChunkName[3])
        return chunk;
    }    
    return null;
  }
  
  private void parsePNGWrappedPtpt(final ByteBuffer png_data) {
    png_data.position(0);
    final ArrayList<ByteBuffer> chunks = chunksOfPNG(png_data);
    ByteBuffer ptpt = null;
    if (chunks != null)
      ptpt = findPtptChunkFrom(chunks);
    if (ptpt != null && ptpt.limit() > 12) {
      ptpt.position(0);
      ptptData = ByteBuffer.allocate(ptpt.limit() - 12);
      ptptData.put(ptpt.array(), 8, ptpt.limit() - 12);
      parseData();
    } else {
      final byte[] pb = png_data.array();
      final Bitmap bm = BitmapFactory.decodeByteArray(pb, 0, pb.length);
      setNumOfLayers(1, true);
      addLayer(bm);
      setCompositionMethod(0, Composition.Multiply);
    }
  }
  
  public void addLayer(Bitmap img) {
    if (currentLayerIndex < 0 || 
        currentLayerIndex >= layerInfos.size()) return;
    
    final int start_o = ptptData.limit();
    final ByteArrayOutputStream data_os = new ByteArrayOutputStream();
    img.compress(CompressFormat.PNG, 100, data_os);
    final int size = data_os.size();
    final ByteBuffer new_buf = ByteBuffer.allocate(start_o + size);
    
    ptptData.position(0);
    new_buf.put(ptptData);
    new_buf.put(data_os.toByteArray());
    ptptData = new_buf;
    
    layerInfos.get(currentLayerIndex).offset = start_o;
    layerInfos.get(currentLayerIndex).length = size;
    
    currentLayerIndex ++;
  }
  
  public void setCompositionMethod(int k, Composition m) {
    if (k >= 0 && k < layerInfos.size())
      layerInfos.get(k).compMethod = m;
  }
  
  public void setAlpha(int k, byte alpha) {
    if (k >= 0 && k < layerInfos.size())
      layerInfos.get(k).alpha = alpha;
  }
  
  public void setPaperColor(int k, int col) {
    if (k >= 0 && k < layerInfos.size())
      layerInfos.get(k).paperColor = col;
  }
  
  public void setThumbnail(Bitmap img) {
    final int start_o = ptptData.limit();
    final ByteArrayOutputStream data_os = new ByteArrayOutputStream();
    img.compress(CompressFormat.JPEG, 90, data_os);
    final int size = data_os.size();
    final ByteBuffer new_buf = ByteBuffer.allocate(start_o + size);
    
    ptptData.position(0);
    new_buf.put(ptptData);
    new_buf.put(data_os.toByteArray());
    ptptData = new_buf;
        
    thumbnailInfo.offset = start_o;
    thumbnailInfo.length = size;
  }
  
  public Bitmap getThumbanil() {
    return BitmapFactory.decodeByteArray(ptptData.array(),
        thumbnailInfo.offset, thumbnailInfo.length);
  }
  
  public int numOfLayers() {
    return layerInfos.size();
  }
  
  public Bitmap layer(int k) {
    return BitmapFactory.decodeByteArray(ptptData.array(), 
        layerInfos.get(k).offset, layerInfos.get(k).length);
  }
  
  public Composition compositionMethod(int k) {
    return layerInfos.get(k).compMethod;
  }
  
  public byte alpha(int k) {
    return layerInfos.get(k).alpha;
  }
  
  public int paperColor(int k) {
    return layerInfos.get(k).paperColor;
  }
  
  public boolean read(final InputStream istr) {
    final int InputBufferLength = 64 * 1024;
    final byte[] buf = new byte[InputBufferLength];
    final ByteArrayOutputStream ostr = new ByteArrayOutputStream();
    int len;
    try {
      len = istr.read(buf, 0, buf.length);
      while (len >= 0) {
        ostr.write(buf, 0, len);
        len = istr.read(buf, 0, buf.length);
      }
    } catch (IOException e) {
      Log.i("peintureroid", "" + e);
      return false;
    }
    final ByteBuffer pngbuf = ByteBuffer.allocate(ostr.size());
    pngbuf.put(ostr.toByteArray());
    parsePNGWrappedPtpt(pngbuf);
    return true;
  }
  
  public boolean write(final OutputStream ostr, final Bitmap img) {
    final ByteArrayOutputStream pngstr = new ByteArrayOutputStream();
    img.compress(CompressFormat.PNG, 100, pngstr);
    final ByteBuffer pngbuf = ByteBuffer.allocate(pngstr.size());
    pngbuf.put(pngstr.toByteArray());
    final ArrayList<ByteBuffer> chunks = chunksOfPNG(pngbuf);
    if (chunks.size() < 1) return false;
    chunks.add(chunks.size() - 1, ptptChunk());
    final ByteBuffer ptpt = pngFromChunks(chunks);
    
    try {
      ostr.write(ptpt.array());
      ostr.close();
    } catch (IOException e) {
      Log.i("peintureroid", "" + e);
      return false;
    }
    return true;
  }
  
}
