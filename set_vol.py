#!/usr/bin/env python3
"""
Automatic loudness compensation based on real volume level.
Uses optimal offset calculation algorithm from APOLoudness.

Logic:
- If real_vol < 80: target = real_vol, reference = 80
- If real_vol >= 80: target = reference = real_vol (or closest available)
"""

import sys
import os
import subprocess
import math

# Device ID (hardcoded as requested)
DEVICE_ID = "LMG850N620fcfd4"

# Maximum real volume assumed
MAX_REAL_VOL = 96.0

# Measured real dB SPL values (from optimaloffsetcalculator.cpp)
# These are base measurements at reference=80
measured_dbspl = {
    40.0: 59.3,
    50.0: 65.4,
    60.0: 71.8,
    70.0: 77.7,
    80.0: 83.0,
    90.0: 88.3
}

# Preamp lookup table (simplified version)
preamp_table = {
    75: {40: -25.06, 50: -20.60, 60: -16.29, 70: -11.67, 80: -6.24, 90: -0.80},
    80: {40: -27.72, 50: -23.26, 60: -18.95, 70: -14.33, 80: -8.90, 90: -3.47},
    85: {40: -30.18, 50: -25.72, 60: -21.40, 70: -16.79, 80: -11.36, 90: -5.92},
    90: {40: -32.48, 50: -28.02, 60: -23.71, 70: -19.09, 80: -13.66, 90: -8.23}
}

def interpolate(x, x1, x2, y1, y2):
    """Linear interpolation"""
    return y1 + (y2 - y1) * (x - x1) / (x2 - x1)

def get_base_dbspl(target_phon):
    """Get base dB SPL for target phon with interpolation"""
    phons = sorted(measured_dbspl.keys())
    
    # Exact match
    if target_phon in measured_dbspl:
        return measured_dbspl[target_phon]
    
    # Below minimum
    if target_phon < phons[0]:
        return interpolate(target_phon, phons[0], phons[1], 
                         measured_dbspl[phons[0]], measured_dbspl[phons[1]])
    
    # Above maximum
    if target_phon > phons[-1]:
        return interpolate(target_phon, phons[-2], phons[-1],
                         measured_dbspl[phons[-2]], measured_dbspl[phons[-1]])
    
    # Interpolate between points
    for i in range(len(phons) - 1):
        if phons[i] <= target_phon <= phons[i+1]:
            return interpolate(target_phon, phons[i], phons[i+1],
                             measured_dbspl[phons[i]], measured_dbspl[phons[i+1]])
    
    return 71.8  # fallback

def calculate_offset_effect(base_preamp, offset_preamp):
    """Calculate the effect of offset on real dB SPL"""
    base_range = (base_preamp + 40.0) * 2.5
    offset_range = (offset_preamp + 40.0) * 2.5
    
    if base_range > 0 and offset_range > 0:
        return 20.0 * math.log10(offset_range / base_range)
    return 0.0

def find_optimal_offset(target_phon, base_preamp, target_real_dbspl):
    """Find offset that makes real dB SPL match target"""
    best_offset = 0.0
    min_error = 999.0
    
    # Search from -30dB to +30dB in 0.1dB steps
    for offset in range(-300, 301):
        offset_db = offset / 10.0
        final_preamp = base_preamp + offset_db
        
        # Calculate real dB SPL with this offset
        base_dbspl = get_base_dbspl(target_phon)
        offset_effect = calculate_offset_effect(base_preamp, final_preamp)
        real_dbspl = base_dbspl + offset_effect
        
        error = abs(real_dbspl - target_real_dbspl)
        
        if error < min_error:
            min_error = error
            best_offset = offset_db
            
        if error < 0.1:  # Early exit if close enough
            break
    
    return best_offset

def get_preamp(listening_level, reference_level):
    """Get preamp value with interpolation"""
    # Find closest reference levels
    ref_keys = sorted(preamp_table.keys())
    closest_ref = min(ref_keys, key=lambda x: abs(x - reference_level))
    
    # Get preamp for closest reference
    preamps = preamp_table[closest_ref]
    listen_keys = sorted(preamps.keys())
    
    # Exact match
    if listening_level in preamps:
        return preamps[listening_level]
    
    # Interpolate
    for i in range(len(listen_keys) - 1):
        if listen_keys[i] <= listening_level <= listen_keys[i+1]:
            return interpolate(listening_level, listen_keys[i], listen_keys[i+1],
                             preamps[listen_keys[i]], preamps[listen_keys[i+1]])
    
    # Extrapolate if outside range
    if listening_level < listen_keys[0]:
        return interpolate(listening_level, listen_keys[0], listen_keys[1],
                         preamps[listen_keys[0]], preamps[listen_keys[1]])
    else:
        return interpolate(listening_level, listen_keys[-2], listen_keys[-1],
                         preamps[listen_keys[-2]], preamps[listen_keys[-1]])

def set_volume_loudness(real_vol_db):
    """Set loudness compensation based on real volume"""
    
    # Determine target and reference based on real volume
    if real_vol_db < 80:
        target_phon = real_vol_db
        reference_phon = 80.0
    else:
        # For volumes >= 80dB, use same value for both
        # Round to nearest available reference level
        available_refs = [75, 80, 85, 90]
        reference_phon = min(available_refs, key=lambda x: abs(x - real_vol_db))
        target_phon = real_vol_db
    
    # Ensure values are in valid range
    target_phon = max(40.0, min(90.0, target_phon))
    reference_phon = max(75.0, min(90.0, reference_phon))
    
    # Round to 0.1 for target (filter files are in 0.1 steps)
    target_phon = round(target_phon, 1)
    reference_phon = float(int(reference_phon))  # Reference must be integer
    
    # Get base preamp
    base_preamp = get_preamp(target_phon, reference_phon)
    
    # Calculate optimal offset to match real volume
    optimal_offset = find_optimal_offset(target_phon, base_preamp, real_vol_db)
    
    # Final preamp
    final_preamp = base_preamp + optimal_offset
    
    print(f"Real Volume: {real_vol_db:.1f} dB SPL")
    print(f"Target Phon: {target_phon:.1f}")
    print(f"Reference Phon: {reference_phon:.1f}")
    print(f"Base Preamp: {base_preamp:.2f} dB")
    print(f"Optimal Offset: {optimal_offset:.2f} dB")
    print(f"Final Preamp: {final_preamp:.2f} dB")
    print()
    
    # Generate configuration
    filter_file = f"{target_phon:.1f}-{reference_phon:.1f}_filter.wav"
    filter_path = f"/storage/emulated/0/JamesDSP/Convolver/{filter_file}"
    
    # Generate EEL script for preamp
    eel_script = f"""desc: APO Loudness Auto {final_preamp:.2f}dB

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp({final_preamp:.2f} * DB_2_LOG);

@sample
spl0 = spl0 * gainLin;
spl1 = spl1 * gainLin;
"""
    
    eel_filename = f"loudness_auto_{real_vol_db:.1f}.eel"
    with open(eel_filename, 'w') as f:
        f.write(eel_script)
    
    # Generate config
    config = f"""# JamesDSP APO Loudness Auto Configuration
# Real Volume: {real_vol_db:.1f} dB SPL
# Target Phon: {target_phon:.1f}
# Reference Phon: {reference_phon:.1f}
# Final Preamp: {final_preamp:.2f} dB

# Enable master switch
MasterSwitch: enabled

# Enable Convolver with the appropriate FIR filter
Convolver: enabled file="{filter_path}"

# Apply preamp gain via Liveprog
Liveprog: enabled file="/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/{eel_filename}"
"""
    
    with open("JamesDSP_loudness.conf", 'w') as f:
        f.write(config)
    
    # Push to device
    try:
        subprocess.run([
            "C:\\adb\\adb.exe", "-s", DEVICE_ID, "push", 
            "JamesDSP_loudness.conf",
            "/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf"
        ], check=True)
        
        subprocess.run([
            "C:\\adb\\adb.exe", "-s", DEVICE_ID, "push",
            eel_filename,
            "/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/"
        ], check=True)
        
        print("Configuration applied successfully!")
        
        # Restart JamesDSP to apply changes
        print("Restarting JamesDSP...")
        try:
            # Force stop the app
            subprocess.run([
                "C:\\adb\\adb.exe", "-s", DEVICE_ID, "shell",
                "am", "force-stop", "me.timschneeberger.rootlessjamesdsp.debug"
            ], check=True)
            
            # Wait a moment
            import time
            time.sleep(1)
            
            
            # Start the app again - try different methods
            try:
                # Method 1: Launch by package name only
                subprocess.run([
                    "C:\\adb\\adb.exe", "-s", DEVICE_ID, "shell",
                    "monkey", "-p", "me.timschneeberger.rootlessjamesdsp.debug", "-c", "android.intent.category.LAUNCHER", "1"
                ], check=True)
                
                # Wait for app to start completely
                time.sleep(11)
                
                # Click power button at fixed position
                subprocess.run([
                    "C:\\adb\\adb.exe", "-s", DEVICE_ID, "shell",
                    "input", "tap", "800", "2600"
                ], capture_output=True)
                
                print("Power button clicked (bottom center)")
                
            except subprocess.CalledProcessError:
                print("Warning: Could not launch JamesDSP with monkey command")
            
            print("JamesDSP restarted successfully!")
            
        except subprocess.CalledProcessError:
            print("Warning: Could not restart JamesDSP automatically")
        
    except subprocess.CalledProcessError as e:
        print(f"Error pushing files: {e}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python set_vol.py <real_volume_db>")
        print("Example: python set_vol.py 70")
        sys.exit(1)
    
    try:
        real_vol = float(sys.argv[1])
        
        if not (40.0 <= real_vol <= MAX_REAL_VOL):
            raise ValueError(f"Real volume must be between 40 and {MAX_REAL_VOL} dB")
        
        set_volume_loudness(real_vol)
        
    except ValueError as e:
        print(f"Error: {e}")
        sys.exit(1)