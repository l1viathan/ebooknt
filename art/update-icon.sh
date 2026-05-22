#!/bin/bash

APP_DIR=../ebooknt/src/main/res
LDPI_DIR=$APP_DIR/drawable-ldpi
MDPI_DIR=$APP_DIR/drawable-mdpi
HDPI_DIR=$APP_DIR/drawable-hdpi
XDPI_DIR=$APP_DIR/drawable-xhdpi
XXDPI_DIR=$APP_DIR/drawable-xxhdpi
XXXDPI_DIR=$APP_DIR/drawable-xxxhdpi
DEFAULT_DIR=$APP_DIR/drawable

NAME="application_icon"
SVG="logo_ebooknt.svg"

inkscape -w 36 -h 36 -o "$LDPI_DIR/$NAME.png" $SVG
inkscape -w 48 -h 48 -o "$MDPI_DIR/$NAME.png" $SVG
inkscape -w 72 -h 72 -o "$HDPI_DIR/$NAME.png" $SVG
inkscape -w 96 -h 96 -o "$XDPI_DIR/$NAME.png" $SVG
inkscape -w 144 -h 144 -o "$XXDPI_DIR/$NAME.png" $SVG
inkscape -w 192 -h 192 -o "$XXXDPI_DIR/$NAME.png" $SVG
inkscape -w 48 -h 48 -o "$DEFAULT_DIR/$NAME.png" $SVG
