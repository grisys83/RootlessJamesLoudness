#!/usr/bin/env python3
"""
JamesDSP Loudness Controller for QPython3
Install QPython3 from Play Store and run this script
"""

import os
import subprocess
import time
from androidhelper import Android

droid = Android()

# Preamp lookup table
preamp_table = {
    75: {40: -25.06, 50: -20.60, 60: -16.29, 70: -11.67, 80: -6.24, 90: -0.80},
    80: {40: -27.72, 50: -23.26, 60: -18.95, 70: -14.33, 80: -8.90, 90: -3.47},
    85: {40: -30.18, 50: -25.72, 60: -21.40, 70: -16.79, 80: -11.36, 90: -5.92},
    90: {40: -32.48, 50: -28.02, 60: -23.71, 70: -19.09, 80: -13.66, 90: -8.23}
}

def interpolate(x, x1, x2, y1, y2):
    return y1 + (y2 - y1) * (x - x1) / (x2 - x1)

def get_preamp(listening, reference):
    ref_keys = sorted(preamp_table.keys())
    closest_ref = min(ref_keys, key=lambda x: abs(x - reference))
    
    preamps = preamp_table[closest_ref]
    listen_keys = sorted(preamps.keys())
    
    if listening in preamps:
        return preamps[listening]
    
    for i in range(len(listen_keys) - 1):
        if listen_keys[i] <= listening <= listen_keys[i+1]:
            return interpolate(listening, listen_keys[i], listen_keys[i+1],
                             preamps[listen_keys[i]], preamps[listen_keys[i+1]])
    
    if listening < listen_keys[0]:
        return interpolate(listening, listen_keys[0], listen_keys[1],
                         preamps[listen_keys[0]], preamps[listen_keys[1]])
    else:
        return interpolate(listening, listen_keys[-2], listen_keys[-1],
                         preamps[listen_keys[-2]], preamps[listen_keys[-1]])

def main():
    droid.dialogCreateSeekBar(40, 96, 70, "Real Volume (dB SPL)")
    droid.dialogSetPositiveButtonText("Apply")
    droid.dialogSetNegativeButtonText("Cancel")
    droid.dialogShow()
    
    response = droid.dialogGetResponse().result
    
    if response.get("which") == "positive":
        volume = droid.dialogGetSelectedItems().result[0] + 40
        
        # Calculate settings
        if volume < 80:
            target = volume
            reference = 80.0
        else:
            reference = float(int(volume))
            target = volume
        
        target = round(target, 1)
        
        # Get preamp
        preamp = get_preamp(target, reference)
        
        # Show result
        droid.makeToast(f"Volume: {volume} dB\nTarget: {target} phon\nReference: {reference} phon\nPreamp: {preamp:.2f} dB")
        
        # Generate config
        config_path = "/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf"
        eel_path = f"/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/loudness_{volume}.eel"
        
        config = f"""# JamesDSP APO Loudness Configuration
# Real Volume: {volume} dB SPL
# Target Phon: {target}
# Reference Phon: {reference}
# Preamp: {preamp:.2f} dB

# Enable master switch
MasterSwitch: enabled

# Enable Convolver with the appropriate FIR filter
Convolver: enabled file="/storage/emulated/0/JamesDSP/Convolver/{target:.1f}-{reference:.1f}_filter.wav"

# Apply preamp gain via Liveprog
Liveprog: enabled file="/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/loudness_{volume}.eel"
"""
        
        eel = f"""desc: APO Loudness {preamp:.2f}dB

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp({preamp:.2f} * DB_2_LOG);

@sample
spl0 = spl0 * gainLin;
spl1 = spl1 * gainLin;
"""
        
        # Write files
        with open(config_path, 'w') as f:
            f.write(config)
        
        with open(eel_path, 'w') as f:
            f.write(eel)
        
        droid.makeToast("Configuration saved!")
        
        # Restart JamesDSP
        os.system("am force-stop me.timschneeberger.rootlessjamesdsp.debug")
        time.sleep(1)
        os.system("monkey -p me.timschneeberger.rootlessjamesdsp.debug -c android.intent.category.LAUNCHER 1")
        time.sleep(11)
        os.system("input tap 800 2600")
        
        droid.makeToast("JamesDSP restarted!")

if __name__ == "__main__":
    main()