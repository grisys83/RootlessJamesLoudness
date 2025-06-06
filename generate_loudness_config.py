#!/usr/bin/env python3
"""
Generate JamesDSP configuration for APO Loudness with FIR filters.
This script creates a config file that loads the appropriate FIR filter
and applies the correct preamp gain.
"""

import sys
import os

# Simplified preamp table for common reference levels
# Format: preamp_table[reference_level][listening_level] = preamp_db
preamp_table = {
    75: {
        40.0: -25.06, 50.0: -20.60, 60.0: -16.29, 70.0: -11.67, 80.0: -6.24, 90.0: -0.80
    },
    80: {
        40.0: -27.72, 50.0: -23.26, 60.0: -18.95, 70.0: -14.33, 80.0: -8.90, 90.0: -3.47
    },
    85: {
        40.0: -30.18, 50.0: -25.72, 60.0: -21.40, 70.0: -16.79, 80.0: -11.36, 90.0: -5.92
    },
    90: {
        40.0: -32.48, 50.0: -28.02, 60.0: -23.71, 70.0: -19.09, 80.0: -13.66, 90.0: -8.23
    }
}

def get_preamp(listening_level, reference_level):
    """Get preamp value with interpolation if needed."""
    # Find closest reference levels
    ref_keys = sorted(preamp_table.keys())
    
    # Exact match for reference level
    if reference_level in preamp_table:
        listen_keys = sorted(preamp_table[reference_level].keys())
        
        # Exact match for listening level
        if listening_level in preamp_table[reference_level]:
            return preamp_table[reference_level][listening_level]
        
        # Interpolate listening level
        for i in range(len(listen_keys) - 1):
            if listen_keys[i] <= listening_level <= listen_keys[i+1]:
                l1, l2 = listen_keys[i], listen_keys[i+1]
                p1 = preamp_table[reference_level][l1]
                p2 = preamp_table[reference_level][l2]
                ratio = (listening_level - l1) / (l2 - l1)
                return p1 + (p2 - p1) * ratio
    
    # Interpolate both reference and listening level
    # For simplicity, return closest match
    closest_ref = min(ref_keys, key=lambda x: abs(x - reference_level))
    return get_preamp(listening_level, closest_ref)

def generate_config(listening_level, reference_level, output_file="JamesDSP_loudness.conf"):
    """Generate JamesDSP configuration file."""
    
    # Get preamp value
    preamp_db = get_preamp(listening_level, reference_level)
    
    # Build filter filename
    filter_file = f"{listening_level:.1f}-{reference_level:.1f}_filter.wav"
    filter_path = f"/storage/emulated/0/JamesDSP/Convolver/{filter_file}"
    
    # Generate Liveprog EEL script for preamp
    eel_script = f"""desc: APO Loudness Preamp {preamp_db:.2f}dB

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp({preamp_db:.2f} * DB_2_LOG);

@sample
spl0 = spl0 * gainLin;
spl1 = spl1 * gainLin;
"""
    
    # Write EEL script
    eel_filename = f"loudness_preamp_{listening_level:.1f}_{reference_level:.1f}.eel"
    with open(eel_filename, 'w') as f:
        f.write(eel_script)
    
    # Generate configuration
    config = f"""# JamesDSP APO Loudness Configuration
# Listening Level: {listening_level:.1f} phon
# Reference Level: {reference_level:.1f} phon
# Preamp: {preamp_db:.2f} dB

# Enable master switch
MasterSwitch: enabled

# Enable Convolver with the appropriate FIR filter
Convolver: enabled file="{filter_path}"

# Apply preamp gain via Liveprog (more natural than GraphicEQ)
Liveprog: enabled file="/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/{eel_filename}"
"""
    
    # Write configuration file
    with open(output_file, 'w') as f:
        f.write(config)
    
    print(f"Configuration generated: {output_file}")
    print(f"Filter: {filter_file}")
    print(f"Preamp: {preamp_db:.2f} dB")
    print(f"EEL Script: {eel_filename}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generate_loudness_config.py <listening_level> <reference_level>")
        print("Example: python generate_loudness_config.py 80.0 85.0")
        sys.exit(1)
    
    try:
        listening = float(sys.argv[1])
        reference = float(sys.argv[2])
        
        if not (40.0 <= listening <= 90.0):
            raise ValueError("Listening level must be between 40.0 and 90.0")
        if not (75.0 <= reference <= 90.0):
            raise ValueError("Reference level must be between 75.0 and 90.0")
        
        generate_config(listening, reference)
        
    except ValueError as e:
        print(f"Error: {e}")
        sys.exit(1)