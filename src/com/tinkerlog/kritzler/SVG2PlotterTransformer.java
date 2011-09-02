package com.tinkerlog.kritzler;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PShape;

public class SVG2PlotterTransformer {
  
  private static final float MAX_DISTANCE = 300.0F;
  
  private float minX = Float.MAX_VALUE;
  private float maxX = Float.MIN_VALUE;
  private float minY = Float.MAX_VALUE;
  private float maxY = Float.MIN_VALUE;
  
  private int bezierDetail = 20;
  private float prev[];
  private PMatrix3D draw;
  private PMatrix3D bezierBasisMatrix = new PMatrix3D(
      -1,  3, -3,  1,
      3, -6,  3,  0,
      -3,  3,  0,  0,
      1,  0,  0,  0);
  private List<float[]> lines;
  private List<Instruction> instructions;
  

  public SVG2PlotterTransformer() {
    draw = new PMatrix3D();
    splineForward(bezierDetail, draw);
    draw.apply(bezierBasisMatrix);
    lines = new ArrayList<float[]>(100);
    instructions = new ArrayList<Instruction>();
    prev = new float[2];
  }
  
  public List<float[]> getLines() {
    return lines;
  }
  
  public List<Instruction> getInstructions() {
    return instructions;
  }
  
  public float getMinX() {
    return minX;
  }

  public float getMaxX() {
    return maxX;
  }
  
  public float getMinY() {
    return minY;
  }

  public float getMaxY() {
    return maxY;
  }
    
  public void analyzeShape(PShape shape) {
    if (shape.getFamily() == PShape.GROUP) {
      for (int i = 0; i < shape.getChildCount(); i++) {
        PShape child = shape.getChild(i);
        analyzeShape(child);
      }
    }
    else if (shape.getFamily() == PShape.PATH) {
      analyzePath(shape);
    }
    else {
      System.out.println("unknow type: " + shape.getFamily());
    }
  }
  
  public void analyzePath(PShape s) { 
    int i = 0;
    boolean first = true;
    for (int j = 0; j < s.getVertexCodeCount(); j++) {
      switch (s.getVertexCode(j)) {
      case PShape.VERTEX:        
        float p1[];
        p1 = s.getVertex(i);
        checkPoint(p1[0], p1[1]);
        if (first) {
          first = false;
          instructions.add(new Instruction(Instruction.MOVE_ABS, p1[0], p1[1]));
        }
        else {
          addLine(prev[0], prev[1], p1[0], p1[1]);
          instructions.add(new Instruction(Instruction.LINE_ABS, p1[0], p1[1]));
        }
        prev = p1;
        i++;
        break;
      case PShape.BEZIER_VERTEX:
        float[] c1, c2, p2;
        c1 = s.getVertex(i);
        c2 = s.getVertex(i+1);
        p2 = s.getVertex(i+2);
        bezierVertex(c1[0], c1[1], c2[0], c2[1], p2[0], p2[1]);
        checkPoint(p2[0], p2[1]);
        i += 3;
        break;
      case PShape.BREAK:
        first = true;
        break;
      default:
        System.out.println("unknow code: " + s.getVertexCode(j));
      }
    }
  }
  
  private void bezierVertex(float x2, float y2, float x3, float y3, float x4, float y4) {

    float x1 = prev[0];
    float y1 = prev[1];

    float xplot1 = draw.m10*x1 + draw.m11*x2 + draw.m12*x3 + draw.m13*x4;
    float xplot2 = draw.m20*x1 + draw.m21*x2 + draw.m22*x3 + draw.m23*x4;
    float xplot3 = draw.m30*x1 + draw.m31*x2 + draw.m32*x3 + draw.m33*x4;

    float yplot1 = draw.m10*y1 + draw.m11*y2 + draw.m12*y3 + draw.m13*y4;
    float yplot2 = draw.m20*y1 + draw.m21*y2 + draw.m22*y3 + draw.m23*y4;
    float yplot3 = draw.m30*y1 + draw.m31*y2 + draw.m32*y3 + draw.m33*y4;

    float oldx = x1;
    float oldy = y1;
    for (int j = 0; j < bezierDetail; j++) {
      x1 += xplot1; xplot1 += xplot2; xplot2 += xplot3;
      y1 += yplot1; yplot1 += yplot2; yplot2 += yplot3;
      addLine(oldx, oldy, x1, y1);
      instructions.add(new Instruction(Instruction.LINE_ABS, x1, y1));
      oldx = x1;
      oldy = y1;
    }
    prev[0] = x4;
    prev[1] = y4;    
  }  

  private void addLine(float x1, float y1, float x2, float y2) {
    float[] line = new float[4];
    line[0] = x1+20; 
    line[1] = y1+20;
    line[2] = x2+20; 
    line[3] = y2+20;
    lines.add(line);    
  }
  
  private void checkPoint(float x, float y) {    
    if (x < minX) {
      minX = x;      
    }
    if (x > maxX) {
      maxX = x;
    }
    if (y < minY) {
      minY = y;
    }
    if (y > maxY) {
      maxY = y;
    }
  }
  
  
  public List<float[]> getPatternLines(List<float[]> lines, List<Instruction> instructions) {
    
    List<float[]> cutLines = new ArrayList<float[]>();
    for (int i = 0; i < 300; i++) {
      float[] l = new float[4];
      l[0] = 0F;
      l[1] = i*45F;
      l[2] = i*45F;
      l[3] = 0F;
      cutLines.add(l);
    }
    
    List<float[]> patternLines = intersectShapeLines(lines, cutLines);
    
    List<float[]> shortPatternLines = makeShortLines(patternLines);
    
    System.out.println("patternLines: " + patternLines.size());
    for (int i = 0; i < shortPatternLines.size(); i++) {
      float[] l = shortPatternLines.get(i);
      // TODO optimize
      instructions.add(new Instruction(Instruction.MOVE_ABS, l[0], l[1]));
      instructions.add(new Instruction(Instruction.LINE_ABS, l[2], l[3]));
    }
    System.out.println("shortPatternLines: " + shortPatternLines.size());
    
    return shortPatternLines;    
  }
   
  public List<float[]> intersectShapeLines(List<float[]> shape, List<float[]> lines) {
    List<float[]> result = new ArrayList<float[]>();
    for (int i = 0; i < lines.size(); i++) {
      float[] line = lines.get(i);
      
      List<Point> intersections = intersectShapeLine(shape, line);
      
      if (intersections.size() > 0) {
        if (intersections.size() % 2 != 0) {
          // jitter lines if odd number of intersections
          line[0] += Math.random();
          line[2] += Math.random();
          intersections = intersectShapeLine(shape, line);
          if (intersections.size() % 2 != 0) {
            System.out.println("--> intersections: " + intersections.size());
          }
        }
        int k = 0;
        while (k+1 < intersections.size()) {
          Point p1 = intersections.get(k);
          Point p2 = intersections.get(k+1);
          float d = p1.distance(p2);
          if (d > 5) {
            float[] l = {p1.x, p1.y, p2.x, p2.y};
            result.add(l);    
          }
          k += 2;
        }
      }
    }
    return result;    
  }

  public List<Point> intersectShapeLine(List<float[]> shape, float[] line) {
    List<Point> intersections = new ArrayList<Point>();
    for (int j = 0; j < shape.size(); j++) {
      float[] shapeLine = shape.get(j);
      Point p = intersectLine(
          line[0], line[1], line[2], line[3],
          shapeLine[0], shapeLine[1],shapeLine[2],shapeLine[3]);
      if (p != null) {
        intersections.add(p);
      }
    }
    Collections.sort(intersections);
    return intersections;
  }

  private List<float[]> makeShortLines(List<float[]> lines) {
    List<float[]> newLines = new ArrayList<float[]>();
    for (int i = 0; i < lines.size(); i++) {
      float[] line = lines.get(i);      
      float x1 = line[2];
      float y1 = line[3];
      float x2 = line[0];
      float y2 = line[1];
//      float y2y1 = line[1] - line[3];
//      float x2x1 = line[0] - line[2];
      float y2y1 = y1 - y2;
      float x2x1 = x1 - x2;
      float distance = (float)Math.sqrt(y2y1*y2y1 + x2x1*x2x1);
      if (distance > MAX_DISTANCE) {
        // System.out.println("distance: " + distance);
        float m = y2y1 / x2x1;
        float n = y1 - m * x1;
        float dc = distance / MAX_DISTANCE;
        //System.out.println("  dc: " + dc);
        float dx = x2x1 / dc;
        //System.out.println("  dx: " + dx);
//        float x2 = line[2];
//        float x1 = line[0];
//        float y2 = line[3];
//        float y1 = 0.0F;
        while (x1 > x2) {
          float[] newLine = new float[4];
          newLine[0] = x2;
          newLine[1] = y2;
          //System.out.println("  x2, x1 " + x2 + ", " + x1);
          x2 = x2 + dx;
          if (x2 > x1) {
            x2 = x1;
          }
          newLine[2] = x2;
          newLine[3] = m * x2 + n;
          y2 = newLine[3];
          newLines.add(newLine);
        }        
      }
      else {
        newLines.add(line);
      }
      
    }
    return newLines;
  }
  
  
  public Point intersectLine(float p0x, float p0y, float p1x, float p1y, 
      float p2x, float p2y, float p3x, float p3y) {
    
    float x, y;
    
    float d1x = p1x - p0x;
    float d2x = p3x - p2x;
    float d1y = p1y - p0y;
    float d2y = p3y - p2y;
    
    float s = (-d1y * (p0x - p2x) + d1x * (p0y - p2y)) / (-d2x * d1y + d1x * d2y);
    float t = ( d2x * (p0y - p2y) - d2y * (p0x - p2x)) / (-d2x * d1y + d1x * d2y);

    if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
      // Collision detected
      x = p0x + (t * d1x);
      y = p0y + (t * d1y);
      return new Point(x, y); 
    }    
    return null;      
  }
  
  private class Point implements Comparable<Point> {
    
    public float x;
    public float y;
    
    public Point(float x, float y) {
      this.x = x;
      this.y = y;
    }
    
    public float distance(Point p) {
      float dx = x - p.x;
      float dy = y - p.y;
      return PApplet.sqrt(dx*dx + dy*dy);
    }

    @Override
    public int compareTo(Point other) {
      return (x < other.x) ? -1 : (x == other.x) ? 0 : 1;
    } 
    
  }
  
  
  
  
  private void splineForward(int segments, PMatrix3D matrix) {
    float f  = 1.0f / segments;
    float ff = f * f;
    float fff = ff * f;
    matrix.set(0,     0,    0, 1,
               fff,   ff,   f, 0,
               6*fff, 2*ff, 0, 0,
               6*fff, 0,    0, 0);
  }
      
}
