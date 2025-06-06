import numpy as np
from scipy import signal # For zpk2tf, zpk2sos, sosfreqz, freqz (for potential FIR response)
from scipy.interpolate import CubicSpline # Not strictly needed if interpolating gains directly for firwin2

# --- Weighting Filter Functions (from provided script) ---
def matched_z(z_analog, p_analog, fs_digital):
    return np.exp(z_analog / fs_digital), np.exp(p_analog / fs_digital)

def normalise_a2d(z_coeffs, p_coeffs, k_gain, fs_digital):
    if fs_digital:
        z_digital, p_digital = matched_z(z_coeffs, p_coeffs, fs_digital)
        b_coeffs, a_coeffs = signal.zpk2tf(z_digital, p_digital, 1) # k=1 for initial tf
        # No signal.freqz with just one frequency point and fs. Use freqz with w.
        # w_1khz = 2 * np.pi * 1000 / fs_digital # Normalized angular frequency
        # _, h_response = signal.freqz(b_coeffs, a_coeffs, worN=[w_1khz])
        # Correct way with fs for freqz for a single point:
        _, h_response = signal.freqz(b_coeffs, a_coeffs, worN=[1000], fs=fs_digital)
        k_normalized = 1.0 / np.abs(h_response[0])
        return z_digital, p_digital, k_normalized
    else: # Analog
        b_analog, a_analog = signal.zpk2tf(z_coeffs, p_coeffs, k_gain)
        w_analog, h_analog = signal.freqs(b_analog, a_analog, worN=[2 * np.pi * 1000])
        k_normalized = k_gain / np.abs(h_analog[0])
        return z_coeffs, p_coeffs, k_normalized


def get_zpk(curve='A', fs=False):
    if curve not in 'ABC':
        raise ValueError('Curve type not understood')
    z = [0, 0]
    p = [-2*np.pi*20.598997057568145,
         -2*np.pi*20.598997057568145,
         -2*np.pi*12194.21714799801,
         -2*np.pi*12194.21714799801]
    k = 1
    if curve == 'A':
        p.extend([-2*np.pi*107.65264864304628, -2*np.pi*737.8622307362899])
        z.extend([0, 0])
    elif curve == 'B':
        p.append(-2*np.pi*10**2.2)
        z.append(0)
    p_arr = np.array(p)
    z_arr = np.array(z)
    return normalise_a2d(z_arr, p_arr, k, fs)

# --- ISO Curve Data and Interpolation Functions (from grisys's script) ---
iso_freq = [20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000]
iso_curves = {
    0: [76.5517, 65.6189, 55.1228, 45.5340, 37.6321, 30.8650, 25.0238, 20.5100, 16.6458, 13.1160, 10.0883, 7.5436, 5.1137, 3.0589, 1.4824, 0.3029, -0.3026, -0.0103, 1.0335, -1.1863, -4.1116, -7.0462, -9.0260, -8.4944, -4.4829, 3.2817, 9.8291, 10.4757, 8.3813, 14.1000, 79.6500],
    10: [83.7500, 75.7579, 68.2089, 61.1365, 54.9638, 49.0098, 43.2377, 38.1338, 33.4772, 28.7734, 24.8417, 21.3272, 18.0522, 15.1379, 12.9768, 11.1791, 9.9918, 9.9996, 11.2621, 10.4291, 7.2744, 4.4508, 3.0404, 3.7961, 7.4583, 14.3483, 20.9841, 23.4306, 22.3269, 25.1700, 81.4700],
    20: [89.5781, 82.6513, 75.9764, 69.6171, 64.0178, 58.5520, 53.1898, 48.3809, 43.9414, 39.3702, 35.5126, 31.9922, 28.6866, 25.6703, 23.4263, 21.4825, 20.1011, 20.0052, 21.4618, 21.4013, 18.1515, 15.3844, 14.2559, 15.1415, 18.6349, 25.0196, 31.5227, 34.4256, 33.0444, 34.6700, 84.1800],
    40: [99.8539, 93.9444, 88.1659, 82.6287, 77.7849, 73.0825, 68.4779, 64.3711, 60.5855, 56.7022, 53.4087, 50.3992, 47.5775, 44.9766, 43.0507, 41.3392, 40.0618, 40.0100, 41.8195, 42.5076, 39.2296, 36.5090, 35.6089, 36.6492, 40.0077, 45.8283, 51.7968, 54.2841, 51.4859, 51.9600, 92.7700],
    60: [109.5113,  104.2279, 99.0779, 94.1773, 89.9635, 85.9434, 82.0534, 78.6546, 75.5635, 72.4743, 69.8643, 67.5348, 65.3917, 63.4510, 62.0512, 60.8150, 59.8867, 60.0116, 62.1549, 63.1894, 59.9616, 57.2552, 56.4239, 57.5699, 60.8882, 66.3613, 71.6640, 73.1551, 68.6308, 68.4300, 104.9200],
    80: [118.9900, 114.2326, 109.6457, 105.3367, 101.7214, 98.3618, 95.1729, 92.4797, 90.0892, 87.8162, 85.9166, 84.3080, 82.8934, 81.6786, 80.8634, 80.1736, 79.6691, 80.0121, 82.4834, 83.7408, 80.5867, 77.8847, 77.0748, 78.3124, 81.6182, 86.8087, 91.4062, 91.7361, 85.4068, 84.6700, 118.9500],
    100: [128.4100, 124.1500, 120.1100, 116.3800, 113.3500, 110.6500, 108.1600, 106.1700, 104.4800, 103.0300, 101.8500, 100.9700, 100.300, 99.8300, 99.6200, 99.500, 99.4400, 100.0100, 102.8100, 104.2500, 101.1800, 98.4800, 97.6700, 99.00, 102.300, 107.2300, 111.1100, 110.2300, 102.0700, 100.8300, 133.7300]
}
def create_primary_interpolated_curves(curves_data):
    primary_curves_result = {}
    for phon_level in [0, 10, 20, 40, 60, 80, 100]:
        if phon_level in curves_data:
             primary_curves_result[phon_level] = curves_data[phon_level]
    for phon_level in [30, 50, 70, 90]:
        lower_phon = phon_level - 10
        upper_phon = phon_level + 10
        if lower_phon in curves_data and upper_phon in curves_data:
            weight = 0.5
            lower_curve = np.array(curves_data[lower_phon])
            upper_curve = np.array(curves_data[upper_phon])
            interpolated_curve = lower_curve * (1 - weight) + upper_curve * weight
            primary_curves_result[phon_level] = interpolated_curve.tolist()
    return primary_curves_result

curves_for_fine_interpolation = create_primary_interpolated_curves(iso_curves)

def create_fine_interpolated_curves(base_curves_data, freq_list, step=0.1):
    fine_curves_result = {}
    sorted_phon_levels = sorted(base_curves_data.keys())
    for phon_val_int in range(int(0.0 * 10), int(100.0 * 10) + int(step * 10), int(step*10)):
        phon_val_rounded = round(phon_val_int / 10.0, 1)
        lower_phon_base = -1; upper_phon_base = -1
        for i in range(len(sorted_phon_levels)):
            if sorted_phon_levels[i] <= phon_val_rounded: lower_phon_base = sorted_phon_levels[i]
            if sorted_phon_levels[i] >= phon_val_rounded: upper_phon_base = sorted_phon_levels[i];
            if lower_phon_base != -1 and upper_phon_base != -1 and sorted_phon_levels[i] >= phon_val_rounded : break # Optimization
        if lower_phon_base == -1 : lower_phon_base = sorted_phon_levels[0]
        if upper_phon_base == -1 : upper_phon_base = sorted_phon_levels[-1]
        if lower_phon_base == upper_phon_base:
            interpolated_curve_list = base_curves_data[lower_phon_base]
        else:
            denominator = upper_phon_base - lower_phon_base
            weight = 0 if denominator == 0 else (phon_val_rounded - lower_phon_base) / denominator
            lower_curve_for_interp = np.array(base_curves_data[lower_phon_base])
            upper_curve_for_interp = np.array(base_curves_data[upper_phon_base])
            interpolated_curve = lower_curve_for_interp * (1 - weight) + upper_curve_for_interp * weight
            interpolated_curve_list = interpolated_curve.tolist()
        fine_curves_result[phon_val_rounded] = [round(val, 4) for val in interpolated_curve_list]
    return fine_curves_result

fine_curves = create_fine_interpolated_curves(curves_for_fine_interpolation, iso_freq, step=0.1)

# --- Helper Functions for SPL Change and Preamp Suggestion ---
def get_accurate_c_weighting_gains_linear(frequencies_hz, fs_param):
    z_c, p_c, k_c = get_zpk(curve='C', fs=fs_param)
    sos_c = signal.zpk2sos(z_c, p_c, k_c)
    # Ensure frequencies are valid for sosfreqz
    valid_frequencies = np.maximum(frequencies_hz, 1e-6) # Avoid 0 Hz
    valid_frequencies = np.minimum(valid_frequencies, fs_param/2 - 1e-6) # Avoid Nyquist
    
    # Get complex frequency response
    _, h_complex = signal.sosfreqz(sos_c, worN=valid_frequencies, fs=fs_param)
    gain_linear_c = np.abs(h_complex)

    # Interpolate back to original frequencies_hz to handle exact 0 or Nyquist if they were in input
    # and to ensure output array matches input frequency array shape.
    gain_interpolated = np.interp(frequencies_hz, valid_frequencies, gain_linear_c, left=0, right=0) # fill out-of-band with 0 gain
    return gain_interpolated


def get_eq_filter_relative_gains_linear(target_phon, reference_phon, iso_freq_list, fine_curves_data, target_frequencies_hz):
    target_phon_key = round(float(target_phon), 1)
    reference_phon_key = round(float(reference_phon), 1)
    if target_phon_key not in fine_curves_data or reference_phon_key not in fine_curves_data: return None
    target_curve_spl = np.array(fine_curves_data[target_phon_key])
    reference_curve_spl = np.array(fine_curves_data[reference_phon_key])
    db_diff = reference_curve_spl - target_curve_spl
    try: idx_1khz = iso_freq_list.index(1000)
    except ValueError: return None
    reference_db_diff_at_1khz = db_diff[idx_1khz]
    relative_gains_db_at_iso_freq = reference_db_diff_at_1khz - db_diff
    relative_gains_linear_at_iso_freq = 10**(relative_gains_db_at_iso_freq / 20.0)
    
    left_fill_value = relative_gains_linear_at_iso_freq[0]
    right_fill_value = relative_gains_linear_at_iso_freq[-1]
    interpolated_gains_linear = np.interp(target_frequencies_hz, iso_freq_list, 
                                          relative_gains_linear_at_iso_freq,
                                          left=left_fill_value, right=right_fill_value)
    return interpolated_gains_linear

def get_pink_noise_power_spectrum_linear(frequencies_hz):
    frequencies_hz = np.maximum(frequencies_hz, 1e-6) 
    power_spectrum = 1.0 / frequencies_hz 
    return power_spectrum / np.sum(power_spectrum) # Normalized

def get_max_boost_from_relative_gains(relative_gains_linear):
    # Convert linear gains to dB to find max boost
    # Avoid log(0) if any gain is zero (though unlikely for these filters' relative gains)
    gains_db = 20 * np.log10(np.maximum(relative_gains_linear, 1e-9))
    return np.max(gains_db)

# --- Main Calculation Loop ---
fs_main = 48000 # System sampling rate
min_freq_calc = 20.0
max_freq_calc = fs_main / 2.0 -1 
num_freq_points_calc = 512 # More points for better accuracy
calculation_frequencies_hz = np.logspace(np.log10(min_freq_calc), np.log10(max_freq_calc), num_freq_points_calc)

pink_noise_power_spec = get_pink_noise_power_spectrum_linear(calculation_frequencies_hz)
c_weighting_gains_lin = get_accurate_c_weighting_gains_linear(calculation_frequencies_hz, fs_main)

# Calculate reference C-weighted power for a "flat" EQ filter (Target 80 / Ref 80)
eq_filter_flat_gains_lin = get_eq_filter_relative_gains_linear(80.0, 80.0, iso_freq, fine_curves, calculation_frequencies_hz)
if eq_filter_flat_gains_lin is None:
    print("Error: Could not calculate flat EQ filter for reference power calculation.")
    level_ref_c_weighted_power_val = 1.0 # Fallback, should not happen
else:
    power_after_flat_eq_ref = pink_noise_power_spec * (eq_filter_flat_gains_lin**2)
    level_ref_c_weighted_power_val = np.sum(power_after_flat_eq_ref * (c_weighting_gains_lin**2))
    if level_ref_c_weighted_power_val == 0:
        print("Warning: Reference C-weighted power is zero. SPL Change calculations might be problematic.")
        level_ref_c_weighted_power_val = 1e-12 # Avoid division by zero

# User-defined parameters for preamp calculation
CALIBRATED_SPL_AT_REF80_TARGET80 = 80.0 # dB SPL(C)
USER_DESIRED_ACTUAL_SPL = 70.0 # dB SPL(C) - Example, user can set this
TARGET_TRUE_PEAK_HEADROOM = -1.0 # dBTP

recommended_preamp_map = {}

reference_phon_levels_calc = np.arange(80.0, 90.1, 1.0)
target_phon_levels_calc = np.arange(40.0, 90.1, 0.1)

for ref_phon in reference_phon_levels_calc:
    ref_phon_key = round(ref_phon, 1)
    recommended_preamp_map[ref_phon_key] = {}
    print(f"Processing Reference Phon: {ref_phon_key:.1f}...")
    for target_phon in target_phon_levels_calc:
        target_phon_key = round(target_phon, 1)

        # 1. Get EQ filter's relative linear gains
        eq_gains_lin = get_eq_filter_relative_gains_linear(target_phon_key, ref_phon_key, iso_freq, fine_curves, calculation_frequencies_hz)
        if eq_gains_lin is None:
            recommended_preamp_map[ref_phon_key][target_phon_key] = "EQ_Error"
            continue

        # 2. Calculate theoretical SPL_Change_dB due to this EQ filter on C-weighted pink noise
        power_after_eq_current = pink_noise_power_spec * (eq_gains_lin**2)
        level_final_c_weighted_power_current = np.sum(power_after_eq_current * (c_weighting_gains_lin**2))
        
        spl_change_db_val = 0.0 # Default if ratio is problematic
        if level_final_c_weighted_power_current > 0 and level_ref_c_weighted_power_val > 0:
            ratio_spl = level_final_c_weighted_power_current / level_ref_c_weighted_power_val
            if ratio_spl > 0:
                spl_change_db_val = 10 * np.log10(ratio_spl)
        elif level_final_c_weighted_power_current == 0 and level_ref_c_weighted_power_val > 0 :
             spl_change_db_val = -np.inf # Effectively infinite attenuation
        # else: (level_ref_c_weighted_power_val is 0 or negative, handled above)

        # 3. Calculate Preamp needed to achieve USER_DESIRED_ACTUAL_SPL
        # Preamp = Desired_SPL - Calibrated_SPL_Ref - SPL_Change_From_Filter
        preamp_for_spl_target = USER_DESIRED_ACTUAL_SPL - CALIBRATED_SPL_AT_REF80_TARGET80 - spl_change_db_val
        if np.isinf(preamp_for_spl_target): preamp_for_spl_target = -99.0 # Cap if inf

        # 4. Calculate Preamp needed to avoid clipping (based on max boost of the EQ filter)
        max_boost_of_eq_filter = get_max_boost_from_relative_gains(eq_gains_lin)
        preamp_for_clipping_avoidance = TARGET_TRUE_PEAK_HEADROOM - max(0.0, max_boost_of_eq_filter) # max_boost should be non-negative

        # 5. Final Recommended Preamp: Choose the more conservative (more attenuation)
        final_recommended_preamp = min(preamp_for_spl_target, preamp_for_clipping_avoidance)
        
        recommended_preamp_map[ref_phon_key][target_phon_key] = round(final_recommended_preamp, 2)

# --- C++ std::map 스타일로 출력 ---
print(f"\n\n// --- Recommended Preamp Map ---")
print(f"// Based on: CALIBRATED_SPL_AT_REF80_TARGET80 = {CALIBRATED_SPL_AT_REF80_TARGET80:.1f} dB SPL(C)")
print(f"//           USER_DESIRED_ACTUAL_SPL = {USER_DESIRED_ACTUAL_SPL:.1f} dB SPL(C)")
print(f"//           TARGET_TRUE_PEAK_HEADROOM = {TARGET_TRUE_PEAK_HEADROOM:.1f} dBTP")
print("std::map<double, std::map<double, double>> recommended_preamp_map = {")
for ref_ph, t_map in recommended_preamp_map.items():
    print(f"    {{{ref_ph:.1f}, {{ // For Reference Phon {ref_ph:.1f}")
    cnt = 0
    l_content = []
    sorted_t_phons = sorted([k for k in t_map.keys() if isinstance(k, float)])
    for t_ph in sorted_t_phons:
        p_val = t_map.get(t_ph, "Error")
        if isinstance(p_val, str):
            l_content.append(f"{{{t_ph:.1f}, -99.0 /*{p_val}*/}}") # Error case
        else:
            l_content.append(f"{{{t_ph:.1f}, {p_val:.2f}}}")
        cnt += 1
        if cnt % 5 == 0 or t_ph == sorted_t_phons[-1]:
            if l_content: print(f"        {', '.join(l_content)},")
            l_content = []
    print("    }},")
print("};")

