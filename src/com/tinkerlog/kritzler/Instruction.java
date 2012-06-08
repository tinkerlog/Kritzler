package com.tinkerlog.kritzler;

public class Instruction {

  public static final int MOVE_ABS = 0;
  public static final int MOVE_REL = 1;
  public static final int LINE_ABS = 2;
  public static final int LINE_REL = 3;

  public float x;
  public float y;
  public int type;

  /**
   * Creates a new Instruction object
   * 
   * @param type
   *            Type of movement (relative or absolute)
   * @param x
   *            X coordinate
   * @param y
   *            Y coordinate
   */
  public Instruction(int type, float x, float y) {
    this.type = type;
    this.x = x;
    this.y = y;
  }

  /**
   * Returns the actual command that would be generated from this Instruction
   * 
   * @see java.lang.Object#toString()
   */
  public String toString() {
    String commandChar = "";

    switch (type) {
    case MOVE_ABS:
      commandChar = "M";
      break;
    case LINE_ABS:
      commandChar = "L";
      break;
    }

    return commandChar + " " + x + " " + y;
  }

}
