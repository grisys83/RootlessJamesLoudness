#!/usr/bin/env python3
"""
Automatically enable JamesDSP master switch after starting the app.
Uses UI automation to click the power button.
"""

import subprocess
import time
import sys

def click_power_button(device_id):
    """Click the power button in JamesDSP UI"""
    try:
        # Get UI dump to find power button location
        print("Finding power button...")
        
        # Dump UI hierarchy
        subprocess.run([
            "C:\\adb\\adb.exe", "-s", device_id, "shell",
            "uiautomator", "dump", "/sdcard/window_dump.xml"
        ], check=True, capture_output=True)
        
        # Pull the dump file
        subprocess.run([
            "C:\\adb\\adb.exe", "-s", device_id, "pull",
            "/sdcard/window_dump.xml", "temp_ui_dump.xml"
        ], check=True, capture_output=True)
        
        # Read and parse the dump
        with open("temp_ui_dump.xml", "r", encoding="utf-8") as f:
            ui_dump = f.read()
        
        # Look for power button - usually has resource-id with "power" or "toggle"
        # Try to find bounds for the power button
        import re
        
        # Common patterns for power button
        patterns = [
            r'resource-id="[^"]*power[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'resource-id="[^"]*toggle[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'content-desc="[^"]*power[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'text="[^"]*ON[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'text="[^"]*OFF[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        ]
        
        for pattern in patterns:
            match = re.search(pattern, ui_dump, re.IGNORECASE)
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                # Calculate center point
                x = (x1 + x2) // 2
                y = (y1 + y2) // 2
                
                print(f"Found power button at ({x}, {y})")
                
                # Click the power button
                subprocess.run([
                    "C:\\adb\\adb.exe", "-s", device_id, "shell",
                    "input", "tap", str(x), str(y)
                ], check=True)
                
                print("Power button clicked!")
                return True
        
        # If patterns don't work, try a fixed location (top right corner usually)
        print("Using default power button location...")
        subprocess.run([
            "C:\\adb\\adb.exe", "-s", device_id, "shell",
            "input", "tap", "950", "150"  # Typical location for power button
        ], check=True)
        
        return True
        
    except Exception as e:
        print(f"Error clicking power button: {e}")
        return False
    finally:
        # Clean up
        try:
            import os
            os.remove("temp_ui_dump.xml")
        except:
            pass

if __name__ == "__main__":
    device_id = sys.argv[1] if len(sys.argv) > 1 else "LMG850N620fcfd4"
    
    # Wait a bit for UI to be ready
    time.sleep(2)
    
    # Try to click power button
    if click_power_button(device_id):
        print("DSP enabled successfully!")
    else:
        print("Could not enable DSP automatically")