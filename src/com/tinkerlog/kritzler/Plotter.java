package com.tinkerlog.kritzler;


import geomerative.RG;
import geomerative.RPath;
import geomerative.RPoint;
import geomerative.RShape;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import processing.core.PApplet;
import processing.serial.Serial;

@SuppressWarnings("serial")
public class Plotter extends PApplet {
  
  private static final String BUFFER_ACC_PATH = "buffer_acc/";
  private static final String BUFFER_DONE_PATH = "buffer_done/";
  private static final String BUFFER_DENIED_PATH = "buffer_denied/";
    
  private static final int MAX_PLOTTER_X = 7000;
  private static final int MAX_PLOTTER_Y = 8000;
  
  //private static final int MAX_SCREEN_X = 800;
  private static final int MAX_SCREEN_Y = 700; 
  
  private static final int SCREEN_PADDING = 20;
  
  private static final int START_X = 4000;
  private static final int START_Y = 4000;
  private static final int HOME_X = 7500;
  private static final int HOME_Y = 7500;
  
  private static final int STATE_START = 1;
  private static final int STATE_WAITING = 2;
  private static final int STATE_PLOTTING_SCREEN = 3;
  private static final int STATE_WAITING_INPUT = 4;
  private static final int STATE_SETUP_PLOTTER = 5;
  private static final int STATE_PLOTTING = 6;
  private int state = STATE_START;
  
  private boolean serialAvailable = true;
  // private boolean serialAvailable = false;

  private static final float STROKE_WEIGHT = 4.0F;
  private static final float DELTA_STEP = 10.0F;
  
  float screenScale = 0.0F;
  float plotterScale = 1.0F;
  float dx, dy = 0.0F;
  
  private RShape shape;
  private Serial port;
  private Kritzler plotter;
  private String currentFileName;

  private long nextUpdate = 0;  
  private boolean paused = false;
  private boolean plotting = false;
    
  public void setup() {
    
    if (serialAvailable) {
      println("Available serial ports:");
      println(Serial.list());
      // port = new Serial(this, Serial.list()[0], 38400);
      port = new Serial(this, Serial.list()[0], 57600);
    }

    RG.init(this);    
    
    screenScale = (MAX_SCREEN_Y - 2F*SCREEN_PADDING) / MAX_PLOTTER_Y;    
    int xsize = (int)(MAX_PLOTTER_X * screenScale) + 2 * SCREEN_PADDING;
    int ysize = (int)(MAX_PLOTTER_Y * screenScale) + 2 * SCREEN_PADDING;
    System.out.println(screenScale + " " + xsize + " " + ysize);
    // screenScale *= 10;
    
    smooth();
    size(xsize, ysize);
    background(100);
  }
  
  
  public void draw() {
    switch (state) {
    case STATE_START:
      dx = 0;
      dy = 0;
      shape = null;
      translate(SCREEN_PADDING, SCREEN_PADDING);
      scale(screenScale);
      rect(0, 0, MAX_PLOTTER_X, MAX_PLOTTER_Y);
      state = STATE_WAITING;
      break;
    case STATE_WAITING:
      if (shape == null && System.currentTimeMillis() > nextUpdate) {
        nextUpdate = System.currentTimeMillis() + 3000;
        shape = loadNewShape();
        if (shape != null) {
          // analyzeShape(shape, "");
          state = STATE_PLOTTING_SCREEN;
        }
      }
      break;
    case STATE_PLOTTING_SCREEN:
      translate(SCREEN_PADDING, SCREEN_PADDING);
      scale(screenScale * plotterScale);
      rect(0, 0, MAX_PLOTTER_X, MAX_PLOTTER_Y);
      translate(dx, dy);
      strokeWeight(STROKE_WEIGHT);
      drawShape(shape);
      state = STATE_WAITING_INPUT;
      break;
    case STATE_WAITING_INPUT:
      if (plotting) {
        state = STATE_SETUP_PLOTTER;
        plotting = false;
      }
      break;
    case STATE_SETUP_PLOTTER:
      List<Instruction> instructions = new ArrayList<Instruction>();
      convertToInstructions(instructions, shape);
      setupPlotter(instructions);
      state = STATE_PLOTTING;
      break;
    case STATE_PLOTTING:
      if (!paused) {
        plotter.checkSerial();
        if (plotter.isFinished()) {
          plotter.setScale(1.0F);
          plotter.translate(0, 0);
          plotter.sendInstruction(new Instruction(Instruction.MOVE_ABS, HOME_X, HOME_Y));
          moveShapeDone();
          state = STATE_WAITING;
        }
      }
      break;
    }       
  }
  
  public void setupPlotter(List<Instruction> instructions) {
    plotter = new Kritzler(this, port);
    plotter.setInstructions(instructions);
    plotter.setScale(1.0F);
    plotter.translate(START_X + dx, START_Y + dy);
    plotter.setScale(plotterScale);
    plotter.sendInstruction(new Instruction(Instruction.MOVE_REL, 0, 0));    
  }
  
  public void drawShape(RShape shape) {
    for (int i = 0; i < shape.countChildren(); i++) {
      RShape s = shape.children[i];
      drawShape(s);
    }
    for (int i = 0; i < shape.countPaths(); i++) {
      RPath p = shape.paths[i];
      RPoint[] points = p.getPoints();
      for (int k = 0; k < points.length-1; k++) {
        line(points[k].x, points[k].y, points[k+1].x, points[k+1].y);
        // ellipse(points[k].x, points[k].y, 10, 10);
        // ellipse(points[k+1].x, points[k+1].y, 10, 10);
      }
    }    
  }
  
  public void convertToInstructions(List<Instruction> instructions, RShape shape) {
    for (int i = 0; i < shape.countChildren(); i++) {
      RShape s = shape.children[i];
      convertToInstructions(instructions, s);
    }
    for (int i = 0; i < shape.countPaths(); i++) {
      RPath p = shape.paths[i];
      RPoint[] points = p.getPoints();
      RPoint p1 = points[0];
      instructions.add(new Instruction(Instruction.MOVE_ABS, p1.x, p1.y));
      for (int k = 0; k < points.length-1; k++) {
        RPoint p2 = points[k];
        instructions.add(new Instruction(Instruction.LINE_ABS, p2.x, p2.y));
      }
    }    
  }
  
  public void keyPressed() {
    switch (key) {
    case 'p':
      plotting = true;
      break;
    case ' ':
      paused = !paused;
      break;
    case CODED:
      if (shape == null || state == STATE_PLOTTING) return;
      switch (keyCode) {
      case UP: 
        dy -= DELTA_STEP; 
        state = STATE_PLOTTING_SCREEN;
        break;
      case DOWN: 
        dy += DELTA_STEP; 
        state = STATE_PLOTTING_SCREEN;
        break;
      case LEFT: 
        dx -= DELTA_STEP; 
        state = STATE_PLOTTING_SCREEN;
        break;
      case RIGHT: 
        dx += DELTA_STEP; 
        state = STATE_PLOTTING_SCREEN;
        break;
      }
      break;
    case 'l':
      if (shape == null) return;
      shape.scale(0.95F);
      print(shape, "l: ");
      state = STATE_PLOTTING_SCREEN;
      break;
    case 'L':
      if (shape == null) return;
      shape.scale(1.05F);
      print(shape, "L: ");
      state = STATE_PLOTTING_SCREEN;
      break;
    case 'm':      
      if (shape == null) return;
      float width = shape.getBottomRight().x;
      shape.scale(-1.0F, 1.0F);
      shape.translate(width, 0);
      print(shape, "m3 : ");
      state = STATE_PLOTTING_SCREEN;
      break;
    case 'M':
      if (shape == null) return;
      shape.scale(1.0F, -1.0F);
      state = STATE_PLOTTING_SCREEN;
      break;
    case 's':
      if (shape == null) return;
      moveShapeDenied();
      state = STATE_START;
      break;
    }    
  }
  
  private void print(RShape p, String msg) {
    RPoint p1 = p.getTopLeft();
    RPoint p2 = p.getBottomRight();
    System.out.println(msg + " (" + p1.x + ", " + p1.y + "), (" + p2.x + ", " + p2.y + ")");
  }
  
  private void moveShapeDone() {
    System.out.println("moving file to " + BUFFER_DONE_PATH + currentFileName);
    File file = new File(BUFFER_ACC_PATH + currentFileName);
    File newFile = new File(BUFFER_DONE_PATH + currentFileName);
    file.renameTo(newFile);
  }

  private void moveShapeDenied() {
    System.out.println("moving file to " + BUFFER_DENIED_PATH + currentFileName);
    File file = new File(BUFFER_ACC_PATH + currentFileName);
    File newFile = new File(BUFFER_DENIED_PATH + currentFileName);
    file.renameTo(newFile);
  }
  
  private RShape loadNewShape() {
    File dir = new File(BUFFER_ACC_PATH);
    String[] listing = dir.list(new FilenameFilter() {
      public boolean accept(File file, String filename) {
        return filename.endsWith("svg");
      }
    });
    if (listing != null && listing.length > 0) {
      currentFileName = listing[0];
      System.out.println("loading " + currentFileName);
      RShape shape = RG.loadShape(BUFFER_ACC_PATH + currentFileName);
      shape.scale(10.0F);
      print(shape, "loaded: ");
      return shape;
    }
    return null;
  }
          
  public static void main(String args[]) {
    PApplet.main(new String[] { "com.tinkerlog.kritzler.Plotter" });
  }    
  
}
