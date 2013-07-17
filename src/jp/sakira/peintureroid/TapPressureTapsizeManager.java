package jp.sakira.peintureroid;

/**
 * Created by sakira on 2013/05/22.
 */
public class TapPressureTapsizeManager {
  private static final int PressureSmoothLevel = 12;
  private static final int TapsizeSmoothLevel = 12;

  private boolean _pressure_is_constant = true;
  private float _pressure, _last_pressure = -1;
  private float[] _pressures;
  private int _pressures_index;
  private float _max_pressure = 0.01f;

  private boolean _tapsize_is_constant = true;
  private float _tapsize, _last_tapsize = -1;
  private float[] _tapsizes;
  private int _tapsizes_index;
  private float _max_tapsize = 0.01f;

  public TapPressureTapsizeManager() {
    super();
    setup();
  }

  public void setup() {
    _pressures = new float[PressureSmoothLevel];
    _tapsizes = new float[TapsizeSmoothLevel];

    clear_pressures();
    clear_tapsizes();
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

  public float smoothPressure(final float f) {
    _pressure = smooth_pressure(f / _max_pressure);
    if (_pressure > 1.0f) _pressure = 1.0f;
    return _pressure;
  }

  public float smoothRadius(final float f) {
    _tapsize = smooth_tapsize(f / _max_tapsize);
    if (_tapsize > 1.0f) _tapsize = 1.0f;
    return _tapsize;
  }

  public float pressure() {
    return _pressure;
  }

  public float radius() {
    return _tapsize;
  }

  public boolean pressureIsConstant() {
    return _pressure_is_constant;
  }

  public boolean radiusIsConstant() {
    return _tapsize_is_constant;
  }
}
