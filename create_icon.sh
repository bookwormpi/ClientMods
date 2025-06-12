#!/bin/bash

# Enhanced SVG to PNG Conversion Script for Minecraft Mod Icons
# This script provides high-quality conversion of SVG to PNG with features preservation

# Directory paths (use relative paths for better portability)
MOD_ROOT=$(dirname "$0")
ICON_PATH="src/main/resources/assets/clientsidetesting"
SVG_SOURCE="$MOD_ROOT/mod_icon.svg"
PNG_DESTINATION="$MOD_ROOT/$ICON_PATH/icon.png"
ICON_SIZE=128

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Create the assets directory structure if it doesn't exist
mkdir -p "$MOD_ROOT/$ICON_PATH"

echo -e "${BLUE}=== Minecraft Mod Icon Converter ===${NC}"
echo "Source: $SVG_SOURCE"
echo "Destination: $PNG_DESTINATION"
echo "Icon size: ${ICON_SIZE}x${ICON_SIZE} pixels"
echo

# Function to check if the conversion was successful
check_conversion() {
    if [ -f "$PNG_DESTINATION" ]; then
        filesize=$(stat -f%z "$PNG_DESTINATION" 2>/dev/null || stat -c%s "$PNG_DESTINATION" 2>/dev/null)
        if [ $filesize -gt 1000 ]; then
            echo -e "${GREEN}✓ Successfully created icon at $ICON_PATH/icon.png ($(($filesize / 1024)) KB)${NC}"
            return 0
        fi
    fi
    echo -e "${RED}✗ Conversion failed or produced an invalid file${NC}"
    return 1
}

# Try different conversion methods in order of preference
# 1. librsvg (rsvg-convert) - Best quality for SVG with filters and effects
if command -v rsvg-convert &> /dev/null; then
    echo -e "${BLUE}Converting with rsvg-convert (librsvg)...${NC}"
    # Enhanced parameters for better quality
    rsvg-convert -w $ICON_SIZE -h $ICON_SIZE --keep-aspect-ratio --format=png \
                 --dpi-x=300 --dpi-y=300 \
                 "$SVG_SOURCE" -o "$PNG_DESTINATION"
    
    check_conversion && exit 0

# 2. Inkscape - Very good SVG rendering
elif command -v inkscape &> /dev/null; then
    echo -e "${BLUE}Converting with Inkscape...${NC}"
    inkscape "$SVG_SOURCE" --export-filename="$PNG_DESTINATION" \
             --export-width=$ICON_SIZE --export-height=$ICON_SIZE \
             --export-background-opacity=0 2>/dev/null
             
    check_conversion && exit 0

# 3. ImageMagick - Widely available but variable quality with complex SVGs
elif command -v convert &> /dev/null; then
    echo -e "${BLUE}Converting with ImageMagick...${NC}"
    # Enhanced parameters for better quality
    convert -density 300 -background none "$SVG_SOURCE" \
            -resize ${ICON_SIZE}x${ICON_SIZE} -quality 100 \
            -define png:color-type=6 -define png:bit-depth=8 \
            "$PNG_DESTINATION"
            
    check_conversion && exit 0

# 4. CairoSVG (Python library)
elif command -v pip3 &> /dev/null || command -v pip &> /dev/null; then
    PIP_CMD=$(command -v pip3 || command -v pip)
    echo -e "${YELLOW}No direct conversion tools found. Attempting to install CairoSVG...${NC}"
    $PIP_CMD install cairosvg --user
    
    if command -v cairosvg &> /dev/null; then
        echo -e "${BLUE}Converting with CairoSVG...${NC}"
        cairosvg "$SVG_SOURCE" -o "$PNG_DESTINATION" -W $ICON_SIZE -H $ICON_SIZE
        check_conversion && exit 0
    else
        echo -e "${RED}Failed to install CairoSVG${NC}"
    fi

# 5. Alternative browser-based method for macOS
elif [ "$(uname)" == "Darwin" ] && command -v /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome &> /dev/null; then
    echo -e "${BLUE}Converting with Chrome headless mode...${NC}"
    chrome_path="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    
    # Create temporary HTML file
    tmp_html=$(mktemp).html
    cat > "$tmp_html" <<EOL
    <!DOCTYPE html>
    <html>
    <head><title>SVG Converter</title></head>
    <body style="margin:0; background:transparent;">
        <img src="file://$SVG_SOURCE" width="$ICON_SIZE" height="$ICON_SIZE" />
    </body>
    </html>
EOL
    
    "$chrome_path" --headless --disable-gpu --window-size=${ICON_SIZE},${ICON_SIZE} \
                   --screenshot="$PNG_DESTINATION" "$tmp_html" 2>/dev/null
    rm "$tmp_html"
    
    check_conversion && exit 0
fi

# Manual fallback with detailed instructions
echo -e "${YELLOW}No automated conversion methods available. Please convert manually:${NC}"
echo
echo "Option 1: Install one of these tools and run this script again:"
echo "  • librsvg (brew install librsvg)"
echo "  • Inkscape (brew install inkscape)"
echo "  • ImageMagick (brew install imagemagick)"
echo
echo "Option 2: Convert manually:"
echo "  1. Open mod_icon.svg in a web browser or graphics editor"
echo "  2. Export as PNG at ${ICON_SIZE}x${ICON_SIZE} pixels"
echo "  3. Ensure transparency is preserved"
echo "  4. Save as $ICON_PATH/icon.png"
echo
echo "Option 3: Use an online SVG to PNG converter:"
echo "  1. Visit https://svgtopng.com/ or https://cloudconvert.com/svg-to-png"
echo "  2. Upload your SVG and set the size to ${ICON_SIZE}x${ICON_SIZE}"
echo "  3. Download and save as $ICON_PATH/icon.png"

exit 1
