package jp.sakira.peintureroid;

public class RGB_HSV {
    int red, green, blue;
    int val, val_min;
    // note that 0 <= hue < 0x600

    static final int r_lum = 76;
    static final int g_lum = 150;
    static final int b_lum = 30;

    public void setRGB(int r, int g, int b) {
	red = r;
	green = g;
	blue = b;

	val = Math.max(r, Math.max(g, b));
	val_min = Math.min(r, Math.min(g, b));
    }

    public void setColor(int col) {
	setRGB((col >> 16) & 0xff, (col >> 8) & 0xff, col & 0xff);
    }

    public int getColor() {
	return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    public int luminance(int r, int g, int b) {
	return (r_lum * r + g_lum * g + b_lum * b) >> 8;
    }

    public int luminance() {
	return luminance(red, green, blue);
    }

    public int hue() {
	int c = val - val_min;

	if (c == 0) return 0;
  
	if (val == red)
	    return ((green - blue) << 8) / c + 0x100;
	else if (val == green)
	    return ((blue - red) << 8) / c + 0x300;
	else
	    return ((red - green) << 8) / c + 0x500;
    }

    public int chroma() {
	return val - val_min;
    }

    public int saturation() {
	if (val != 0)
	    return ((val - val_min) << 8) / val;
	else
	    return 0;
    }

    public int value() {
	return val;
    }

    public void setHCL(int h, int c, int l) {
	int c32 = c;
	int r, g, b, h2;
	    //, *_max, *_mid, *_min;
	int l_mid, l_min;

	if (h < 0x100) {
	    h2 = 0x100 - h;
	    r = 256;
	    g = 256 - c32; l_min = g_lum;
	    b = g + (h2 * c32 >> 8); l_mid = b_lum;
	} else if (h < 0x200) {
	    h2 = h - 0x100;
	    r = 256;
	    b = 256 - c32; l_min = b_lum;
	    g = b + (h2 * c32 >> 8); l_mid = g_lum;
	} else if (h < 0x300) {
	    h2 = 0x300 - h;
	    g = 256;
	    b = 256 - c32; l_min = b_lum;
	    r = b + (h2 * c32 >> 8); l_mid = r_lum;
	} else if (h < 0x400) {
	    h2 = h - 0x300;
	    g = 256;
	    r = 256 - c32; l_min = r_lum;
	    b = r + (h2 * c32 >> 8); l_mid = b_lum;
	} else if (h < 0x500) {
	    h2 = 0x500 - h;
	    b = 256;
	    r = 256 - c32; l_min = r_lum;
	    g = r + (h2 * c32 >> 8); l_mid = g_lum;
	} else {
	    h2 = h - 0x500;
	    b = 256;
	    g = 256 - c32; l_min = g_lum;
	    r = g + (h2 * c32 >> 8); l_mid = r_lum;
	}
  
	int l2 = luminance(r, g, b);
	if (l2 == 0) {
	    red = green = blue = l;
	    return;
	}

	if (l <= l2) {
	    r = l * r / l2;
	    g = l * g / l2;
	    b = l * b / l2;
	} else {
	    c32 = ((256 - l) << 16) /
		((l_min << 8) + (256 - h2) * l_mid);
	    if (h < 0x100) {
		r = 255;
		g = 256 - c32;
		b = g + (h2 * c32 >> 8);
	    } else if (h < 0x200) {
		r = 255;
		b = 256 - c32;
		g = b + (h2 * c32 >> 8);
	    } else if (h < 0x300) {
		g = 255;
		b = 256 - c32;
		r = b + (h2 * c32 >> 8);
	    } else if (h < 0x400) {
		g = 255;
		r = 256 - c32;
		b = r + (h2 * c32 >> 8);
	    } else if (h < 0x500) {
		b = 255;
		r = 256 - c32;
		g = r + (h2 * c32 >> 8);
	    } else {
		b = 255;
		g = 256 - c32;
		r = g + (h2 * c32 >> 8);
	    }
	}
      
	red = Math.min(r, 255);
	green = Math.min(g, 255);
	blue = Math.min(b, 255);
    }
}

