package com.tinkerlog.kritzler;

import geomerative.RG;
import geomerative.RPoint;
import geomerative.RShape;
import processing.core.PApplet;
import processing.core.PImage;

@SuppressWarnings("serial")
public class JpgToSvg extends PApplet {

  private static final String BUFFER_ACC_PATH = "buffer_acc/";
  private static String fileName;
  
  private static final int MAX_SIZE = 100;
  private static final int TILE_SIZE = 8;
  
  private static final int MAX_PLOTTER_X = 700;
  private static final int MAX_PLOTTER_Y = 800;
  
  private static final int STATE_START = 0;
  private static final int STATE_WAITING = 1;
  private static final int STATE_PLOT = 2;
  private int state = STATE_START;
  
  private PImage img;
  private RShape shape;
  private String exportFileName;
  private float brightScale = 1.0F;
  private float scale;
  private float outScale;
  
  public void setup() {
    size(600, 600);
    RG.init(this);
    img = loadImage(fileName);
    if (img.height > img.width) {
      img.resize(0, MAX_SIZE);            
    }
    else {
      img.resize(MAX_SIZE, 0);      
    }
    System.out.println("w: " + img.width + ", h: " + img.height);
    img.resize(0, MAX_SIZE);
    img.loadPixels();
    smooth();    
    scale = 600F / (MAX_SIZE * (TILE_SIZE));
    outScale = 7000F / (MAX_SIZE * TILE_SIZE);
    System.out.println(scale + ", " + outScale);
    exportFileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".svg";
  }
  
  public void draw() {
    switch (state) {
    case STATE_START:
      rect(0, 0, MAX_PLOTTER_X, MAX_PLOTTER_Y);
      background(255);
      fill(100);
      image(img, 0, 0);
      state = STATE_PLOT;
      break;
    case STATE_PLOT:
      rect(0, 0, MAX_PLOTTER_X, MAX_PLOTTER_Y);
      background(255);
      fill(100);
      image(img, 0, 0);
      scale(scale);
      drawShape();
      state = STATE_WAITING;
      break;
    case STATE_WAITING:
      break;
    }
  
  }

  public void keyPressed() {
    switch (key) {
    case 'p' : 
      export();
      break;
    case CODED:
      switch (keyCode) {
      case LEFT:
        brightScale -= 0.05;
        state = STATE_PLOT;
        break;
      case RIGHT:
        brightScale += 0.05;
        state = STATE_PLOT;
        break;
      }
      break;
    }
  }

  private void drawShape() {
    int i = 0;
    shape = new RShape();
    for (int y = 0; y < img.height; y++) {
      RShape s = new RShape();
      s.addMoveTo(new RPoint(0*TILE_SIZE, y*TILE_SIZE));
      for (int x = 0; x < img.width; x++) {
        pushMatrix();
        int c = img.pixels[i++];
        int r = (c >> 16) & 0xff;
        int g = (c >> 8) & 0xff;
        int b = c & 0xff;  
        // 76.5 + 150.45 + 28.05 = 255;
        //float bright = (float)(0.3F * r + 0.59F * g + 0.11F * b);
        float bright = (r + g + b) / 768F;
        bright = 1 - (bright);
        bright *= brightScale;
        // bright = circleSize - bright;
        // println(bright);
        translate(x*TILE_SIZE, y*TILE_SIZE);
        if (bright < 0.2) {
          s.addMoveTo(new RPoint((x+1)*TILE_SIZE, y*TILE_SIZE+4));
        }
        else if (bright < 0.4) {
          line(0, 3, TILE_SIZE, 3);
          s.addLineTo(new RPoint((x+1)*TILE_SIZE, y*TILE_SIZE+4));
        } 
        else if (bright < 0.6) {
          line(0, 4, 2, 1);
          line(2, 1, 4, 5);
          line(4, 5, 8, 4); 
          s.addLineTo(new RPoint(x*TILE_SIZE+2, y*TILE_SIZE+1));
          s.addLineTo(new RPoint(x*TILE_SIZE+4, y*TILE_SIZE+5));
          s.addLineTo(new RPoint(x*TILE_SIZE+8, y*TILE_SIZE+4));
        }
        else if (bright < 0.8) {
          line(0, 4, 2, 1);
          line(2, 1, 4, 8);
          line(4, 8, 8, 4);
          s.addLineTo(new RPoint(x*TILE_SIZE+2, y*TILE_SIZE+1));
          s.addLineTo(new RPoint(x*TILE_SIZE+4, y*TILE_SIZE+8));
          s.addLineTo(new RPoint(x*TILE_SIZE+8, y*TILE_SIZE+4));
        }
        else {
          line(0, 4, 2, 0);
          line(2, 0, 3, 7);
          line(3, 7, 4, 1);
          line(4, 1, 6, 7);
          line(6, 7, 8, 4); 
          s.addLineTo(new RPoint(x*TILE_SIZE+2, y*TILE_SIZE+0));
          s.addLineTo(new RPoint(x*TILE_SIZE+3, y*TILE_SIZE+7));
          s.addLineTo(new RPoint(x*TILE_SIZE+4, y*TILE_SIZE+1));
          s.addLineTo(new RPoint(x*TILE_SIZE+6, y*TILE_SIZE+7));
          s.addLineTo(new RPoint(x*TILE_SIZE+8, y*TILE_SIZE+4));
        }
        popMatrix();       
      }
      shape.addChild(s);
    }    
  }
  
  private void export() {
    scale(scale);
    System.out.println("x:" + shape.getX() + ", y:" + shape.getY() + ", " + shape.getWidth() + ", " + shape.getHeight());
    float w = shape.getWidth();
    float h = shape.getHeight();
    float s;
    if (w > h) {
      s = MAX_PLOTTER_X / w;
    }
    else {
      s = MAX_PLOTTER_Y / h;
    }
    shape.scale(s);
    System.out.println("exporting svg to " + BUFFER_ACC_PATH + exportFileName);
    RG.saveShape(BUFFER_ACC_PATH + exportFileName, shape);
  }
  
  public static void main(String args[]) {
    if (args.length != 1) {
      System.out.println("syntax: JpgToSvg file.jpg");
      System.exit(1);
    }
    fileName = args[0];    
    PApplet.main(new String[] { "com.tinkerlog.kritzler.JpgToSvg" });
  }      
  
}
