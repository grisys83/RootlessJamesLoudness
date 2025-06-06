#!/bin/bash

# JamesDSP Filter Copy Script for Linux/Mac
# Usage: ./copy_filters.sh [source_directory] [pattern]

SOURCE_DIR="${1:-/mnt/d/FIR-Filter-Maker-for-Equal-Loudness--Loudness/48000}"
PATTERN="${2:-*.wav}"
DEST_DIR="/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver"

echo "========================================"
echo "JamesDSP Filter Copy Tool"
echo "========================================"
echo

# Check if source directory exists
if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: Source directory not found: $SOURCE_DIR"
    exit 1
fi

# Count matching files
COUNT=$(find "$SOURCE_DIR" -name "$PATTERN" -type f | wc -l)

if [ $COUNT -eq 0 ]; then
    echo "No files matching pattern '$PATTERN' found"
    exit 1
fi

echo "Found $COUNT files matching: $PATTERN"
echo "Source: $SOURCE_DIR"
echo "Destination: $DEST_DIR"
echo

# Create destination directory
adb shell mkdir -p "$DEST_DIR" 2>/dev/null

# Method 1: Try to push entire directory (fastest)
echo "Attempting bulk copy..."
if adb push "$SOURCE_DIR"/*.wav "$DEST_DIR/" 2>/dev/null; then
    echo "Bulk copy successful!"
else
    echo "Bulk copy failed, copying files individually..."
    
    # Method 2: Copy files individually with progress
    COPIED=0
    FAILED=0
    
    find "$SOURCE_DIR" -name "$PATTERN" -type f | while read -r file; do
        filename=$(basename "$file")
        echo -n "Copying: $filename... "
        
        if adb push "$file" "$DEST_DIR/$filename" >/dev/null 2>&1; then
            echo "OK"
            ((COPIED++))
        else
            echo "FAILED"
            ((FAILED++))
        fi
    done
    
    echo
    echo "Copied: $COPIED files"
    echo "Failed: $FAILED files"
fi

# Verify files on device
echo
echo "Verifying files on device..."
DEVICE_COUNT=$(adb shell ls "$DEST_DIR"/*.wav 2>/dev/null | wc -l)
echo "Files on device: $DEVICE_COUNT"

echo
echo "========================================"
echo "Copy operation completed!"
echo "========================================"