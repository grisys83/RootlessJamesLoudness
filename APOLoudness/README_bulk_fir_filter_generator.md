# Bulk FIR Filter Generator

## Overview

The `bulk_fir_filter_generator.py` script is a comprehensive tool for generating FIR (Finite Impulse Response) filters based on ISO 226 equal-loudness contours. It creates filters that compensate for the difference in perceived loudness between different phon levels, enabling consistent tonal balance across various volume levels.

## Features

- **ISO 226 Equal-Loudness Contours**: Uses standard ISO 226 data for accurate loudness compensation
- **Bulk Generation**: Can generate hundreds of filters for different target/reference phon combinations
- **High Precision**: Supports 0.1 phon interpolation for smooth transitions
- **Multiple Output Formats**: Saves filters as WAV files and/or CSV coefficient files
- **Metadata Tracking**: Generates JSON metadata with filter characteristics
- **Progress Monitoring**: Real-time progress updates with ETA
- **Memory Efficient**: Batch processing to handle large-scale generation

## Technical Details

### Filter Design Process

1. **Interpolation**: Creates fine-grained equal-loudness curves with 0.1 phon resolution
2. **SPL Difference Calculation**: Computes the SPL difference between target and reference curves
3. **1kHz Normalization**: Uses 1kHz as the reference point (0dB) for consistent baseline
4. **FIR Design**: Uses `scipy.signal.firwin2` for arbitrary frequency response approximation
5. **Post-processing**: Ensures exact 0dB at 1kHz through normalization

### Parameters

- **Sampling Rate**: 48000 Hz (configurable)
- **Filter Length**: 4095 taps (must be odd number)
- **Frequency Points**: Based on ISO 226 standard frequencies (20Hz to 20kHz)
- **Window Function**: Hann window (default)

## Usage

### Basic Usage

```python
from bulk_fir_filter_generator import BulkFIRFilterGenerator

# Initialize generator
generator = BulkFIRFilterGenerator(fs=48000, numtaps=4095)

# Generate single filter
fir_coeff, info = generator.design_single_filter(
    target_phon=60.0,    # Target loudness level
    reference_phon=80.0  # Reference loudness level
)

# Save to WAV file
generator.save_filter_to_wav(fir_coeff, "60-80_filter.wav")
```

### Bulk Generation

```python
# Generate filters for multiple combinations
success, error = generator.generate_filter_range(
    target_range=(40.0, 85.0, 1.0),      # 40 to 85 phon, 1 phon steps
    reference_range=(75.0, 85.0, 1.0),   # 75 to 85 phon, 1 phon steps
    output_dir="filters",                 # Output directory
    file_format="wav",                    # "wav", "csv", or "both"
    save_metadata=True                    # Save JSON metadata
)
```

### Output Files

1. **WAV Files**: `<target>-<reference>_filter.wav`
   - 16-bit PCM format
   - Single channel
   - Normalized to prevent clipping

2. **CSV Files**: `<target>-<reference>_filter.csv`
   - Raw FIR coefficients
   - One coefficient per line

3. **Metadata**: `filter_metadata.json`
   - Generation timestamp
   - Filter parameters
   - Performance characteristics (max boost/cut)

## Example Output

For a 60.0 → 80.0 phon filter:
- Filename: `60.0-80.0_filter.wav`
- Metadata:
  ```json
  {
    "target_phon": 60.0,
    "reference_phon": 80.0,
    "max_boost_db": 12.5,
    "max_cut_db": -3.2,
    "filter_length": 4095
  }
  ```

## Performance

- Single filter generation: ~0.1-0.3 seconds
- Bulk generation (500+ filters): ~50-150 seconds
- Memory usage: Moderate (processes one filter at a time)

## Dependencies

- NumPy
- SciPy
- Python wave module
- Python standard library (os, time, datetime, csv, json)

## Notes

- The script includes debug mode for troubleshooting filter design issues
- Supports keyboard interrupt (Ctrl+C) for graceful termination during bulk generation
- Automatically creates output directories if they don't exist
- Validates all filter design parameters to ensure proper FIR filter generation