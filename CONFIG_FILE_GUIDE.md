# JamesDSP Configuration File Guide

## Overview
This feature allows you to configure JamesDSP effects using a text file, similar to Equalizer APO. Changes to the configuration file are automatically detected and applied without restarting the app.

## Configuration File Location
The configuration file is located at:
```
/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp/files/JamesDSP/JamesDSP.conf
```

Note: The exact path may vary slightly depending on your device. The file will be automatically created when the service starts for the first time.

## File Format
The configuration file uses a simple text format. Each line represents a DSP effect configuration.

### Basic Syntax
- Lines starting with `#` are comments
- Empty lines are ignored
- Each effect line starts with the effect name followed by parameters
- Use `enabled` or `disabled` to control effect state

### Supported Effects

#### Convolver (FIR Filter)
```
# Enable convolver with impulse response file
Convolver: enabled file="Convolver/impulse.wav" mode=0 adv="-80;-100;0;0;0;0"

# Disable convolver
Convolver: disabled
```

Parameters:
- `file`: Path to impulse response file (relative to external files directory)
- `mode`: Convolution mode (0 = normal)
- `adv`: Advanced parameters (semicolon-separated)

#### Graphic Equalizer
```
GraphicEQ: enabled bands="31 0.0; 62 2.0; 125 1.0; 250 0.0; 500 -1.0; 1000 0.0"
```

Parameters:
- `bands`: Space-separated frequency and gain pairs

#### Parametric Equalizer
```
Equalizer: enabled type=0 mode=0 bands="25.0;40.0;63.0;100.0;160.0;250.0;400.0;630.0;1000.0;1600.0;2500.0;4000.0;6300.0;10000.0;16000.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0"
```

Parameters:
- `type`: Filter type (0 = FIR)
- `mode`: Interpolation mode
- `bands`: Semicolon-separated frequency and gain values

#### Bass Boost
```
BassBoost: enabled gain=5.0
```

Parameters:
- `gain`: Bass boost gain in dB

#### Reverb
```
Reverb: enabled preset=0
```

Parameters:
- `preset`: Reverb preset index

#### Stereo Widening
```
StereoWide: enabled level=60.0
```

Parameters:
- `level`: Stereo widening level (0-100)

#### Crossfeed
```
Crossfeed: enabled mode=0
```

Parameters:
- `mode`: Crossfeed mode index

#### Tube Simulator
```
Tube: enabled drive=2.0
```

Parameters:
- `drive`: Tube drive amount

#### DDC (Digital Room Correction)
```
DDC: enabled file="DDC/correction.vdc"
```

Parameters:
- `file`: Path to VDC file

#### Liveprog (Custom DSP Script)
```
Liveprog: enabled file="Liveprog/custom.eel"
```

Parameters:
- `file`: Path to EEL script file

#### Output Control
```
Output: gain=0.0 limiter_threshold=-0.1 limiter_release=60.0
```

Parameters:
- `gain`: Output gain in dB
- `limiter_threshold`: Limiter threshold
- `limiter_release`: Limiter release time

#### Compander
```
Compander: enabled timeconstant=0.22 granularity=2 tfresolution=0 response="95.0;200.0;400.0;800.0;1600.0;3400.0;7500.0;0;0;0;0;0;0;0"
```

Parameters:
- `timeconstant`: Time constant
- `granularity`: Granularity level
- `tfresolution`: TF resolution
- `response`: Semicolon-separated response values

## Example Configuration

```
# JamesDSP Configuration Example
# Last updated: 2024-01-01

# Apply convolution with room correction impulse
Convolver: enabled file="Convolver/room_correction.wav" mode=0

# Boost bass slightly
BassBoost: enabled gain=3.0

# Apply parametric EQ
GraphicEQ: enabled bands="100 2.0; 1000 -1.0; 10000 1.0"

# Add slight stereo widening
StereoWide: enabled level=30.0

# Set output gain
Output: gain=-3.0 limiter_threshold=-0.1 limiter_release=60.0
```

## Tips

1. **File Paths**: All file paths are relative to the app's external files directory
2. **Live Updates**: Changes are applied immediately when you save the file
3. **Testing**: Use a text editor that supports auto-save to see changes in real-time
4. **Backup**: Keep a backup of your configuration file before making major changes
5. **Comments**: Use comments (#) to document your settings

## Troubleshooting

1. **File Not Found**: Make sure the service has been started at least once to create the default config file
2. **Settings Not Applied**: Check the file syntax and ensure the service is running
3. **Path Issues**: Use the file manager in the app to verify correct paths for impulse response files

## Integration with Other Tools

Since this is a standard text file, you can:
- Use scripts to generate configurations
- Switch between different profiles by copying files
- Version control your configurations with Git
- Share configurations with other users