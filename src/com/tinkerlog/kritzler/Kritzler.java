package com.tinkerlog.kritzler;


import java.util.List;

import processing.core.PApplet;
import processing.serial.Serial;

public class Kritzler {
    
  private Serial port;
  private List<Instruction> instructions;
  private int currentInst;
  private StringBuilder buf = new StringBuilder();
  
  private float tx, ty;
  private float scale;
  
  public Kritzler(PApplet parent, Serial port) {
    this.port = port;
  }
  
  public void setInstructions(List<Instruction> instructions) {
    this.instructions = instructions;
    this.currentInst = 0;
  }
  
  public void translate(float x, float y) {
    this.tx = x;
    this.ty = y;
  }
  
  public void setScale(float s) {
    this.scale = s;
  }

  public boolean isFinished() {
    if (port == null) return true;
    return instructions.size() == currentInst;
  }
  
  public void checkSerial() {
    if (port != null && port.available() > 0) {
      processSerial();
    }
  }
  
  public void processSerial() {
    while (port.available() > 0) {
      int c = port.read();
      if (c != 10) {
        buf.append((char)c);        
      }
      else {
        String message = buf.toString();
        buf.setLength(0);
        if (message.length() > 0) {
          message = message.substring(0, message.length()-1);
        }
        if (message.startsWith("#")) {
          System.out.println("bot: " + message);
        }
        else {          
          processMessage(message);
        }        
      }
    }
  }
  
  public void processMessage(String message) {
    if (message.equals("OK")) {
      System.out.println("received ok");
      if (instructions != null) {
        if (currentInst >= instructions.size()) {
          System.out.println("nothing to do");
        }
        else {
          Instruction inst = instructions.get(currentInst);
          currentInst++;
          sendInstruction(inst);
        }
      }      
    }
    else {
      System.out.println("unknown: " + message);
    }
  }
  
  public void sendInstruction(Instruction i) {
    if (port == null) return;
    String msg = null;
    int x = (int)(i.x * scale);
    int y = (int)(i.y * scale);    
    switch (i.type) {
    case Instruction.MOVE_ABS:
      msg = "M " + (int)(x + tx) + " " + (int)(y + ty) + '\r';
      break;
    case Instruction.LINE_ABS:
      msg = "L " + (int)(x + tx) + " " + (int)(y + ty) + '\r';
      break;
    }
    System.out.println("sending (" + currentInst + "): " + msg);
    port.write(msg);
  }
  
}
