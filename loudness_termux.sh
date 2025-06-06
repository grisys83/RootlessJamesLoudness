#!/data/data/com.termux/files/usr/bin/bash
# Loudness Controller for Termux
# Run this script in Termux to control JamesDSP loudness

# Function to calculate preamp
calculate_preamp() {
    local vol=$1
    local ref=$2
    
    # Simplified preamp calculation
    if [ "$vol" -lt 80 ]; then
        target=$vol
        reference=80
    else
        target=$vol
        reference=$vol
    fi
    
    # Basic preamp values (simplified)
    case "$target" in
        40) preamp="-30.18";;
        50) preamp="-25.72";;
        60) preamp="-21.40";;
        70) preamp="-16.79";;
        80) preamp="-11.36";;
        90) preamp="-5.92";;
        *) preamp="-15.00";;  # default
    esac
    
    echo "$preamp"
}

# Main script
echo "=== JamesDSP Loudness Controller ==="
echo "Enter real volume (40-96 dB SPL):"
read volume

if [ "$volume" -lt 40 ] || [ "$volume" -gt 96 ]; then
    echo "Volume must be between 40 and 96"
    exit 1
fi

# Calculate settings
if [ "$volume" -lt 80 ]; then
    target=$volume
    reference=80
else
    target=$volume
    reference=$volume
fi

preamp=$(calculate_preamp $volume $reference)

echo "Target: ${target} phon"
echo "Reference: ${reference} phon"
echo "Preamp: ${preamp} dB"

# Generate config
cat > /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf << EOF
# JamesDSP APO Loudness Configuration
# Real Volume: ${volume} dB SPL
# Target Phon: ${target}
# Reference Phon: ${reference}
# Preamp: ${preamp} dB

# Enable master switch
MasterSwitch: enabled

# Enable Convolver with the appropriate FIR filter
Convolver: enabled file="/storage/emulated/0/JamesDSP/Convolver/${target}.0-${reference}.0_filter.wav"

# Apply preamp gain via Liveprog
Liveprog: enabled file="/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/loudness_${volume}.eel"
EOF

# Generate EEL script
cat > /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/loudness_${volume}.eel << EOF
desc: APO Loudness ${preamp}dB

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp(${preamp} * DB_2_LOG);

@sample
spl0 = spl0 * gainLin;
spl1 = spl1 * gainLin;
EOF

echo "Configuration saved!"

# Restart JamesDSP
am force-stop me.timschneeberger.rootlessjamesdsp.debug
sleep 1
monkey -p me.timschneeberger.rootlessjamesdsp.debug -c android.intent.category.LAUNCHER 1
sleep 11
input tap 800 2600

echo "JamesDSP restarted and enabled!"