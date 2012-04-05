package com.tinkerlog.kritzler;

public class Instruction {
  
  public static final int MOVE_ABS = 0;
  public static final int LINE_ABS = 2;
  
  public float x;
  public float y;
  public int type;
  public Instruction(int type, float x, float y) {
    this.type = type;
    this.x = x;
    this.y = y;
  }
  
}
