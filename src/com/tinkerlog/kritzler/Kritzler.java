package com.tinkerlog.kritzler;

import java.io.PrintWriter;
import java.util.List;

import processing.core.PApplet;
import processing.serial.Serial;

public class Kritzler {

    private PApplet parent;
    private Serial port;
    private List<Instruction> instructions;
    private int currentInst;
    private StringBuilder buf = new StringBuilder();
    PrintWriter output;

    private float tx, ty;
    private int botX, botY;
    private float scale;

    public final int STATE_PLOTTING = 0;
    public final int STATE_PAUSED = 1;
    public final int STATE_FINISHED = 2;
    private int currentState;

    /**
     * Constructor, creates a new Kritzler object
     * 
     * @param parent
     *            PApplet object used by main sketch
     * @param port
     *            Serial port set up in main sketch
     */
    public Kritzler(PApplet parent, Serial port) {
        this.port = port;
        this.parent = parent;

        this.botX = parent.screenWidth / 2;
        this.botY = parent.screenHeight / 2;
        this.currentState = STATE_PAUSED;
    }

    /**
     * Set the current Instruction list, reset current Instruction count
     * 
     * @param instructions
     *            New set of Instructions
     */
    public void setInstructions(List<Instruction> instructions) {
        this.instructions = instructions;
        this.currentInst = 0;
    }

    /**
     * Returns the instructions in use
     * 
     * @return Instruction set in use by Kritzler
     */
    public List<Instruction> getInstructions() {
        return instructions;
    }

    /**
     * Offsets all Instructions by supplied values.
     * 
     * Values should correspond to coordinate of top-left corner of physical
     * canvas.
     * 
     * @param x
     *            X offset
     * @param y
     *            Y offset
     */
    public void translate(float x, float y) {
        this.tx = x;
        this.ty = y;
    }

    /**
     * Set the scale factor
     * 
     * @param s
     *            Scale factor (1 = no scaling)
     */
    public void setScale(float s) {
        this.scale = s;
    }

    /**
     * Determine whether or not the current Instruction is the final Instruction
     * 
     * @return True if current instruction is final instruction, false if not
     */
    public boolean isFinished() {
        if (port == null)
            return true;

        return instructions.size() == currentInst;
    }

    /**
     * Check the Serial port for new messages
     */
    public void checkSerial() {
        if (port != null && port.available() > 0)
            processSerial();
    }

    /**
     * Process any incoming Serial messages coming from the Arduino
     */
    public void processSerial() {
        while (port.available() > 0) {
            int c = port.read();

            // Fill the buffer until carriage return is received
            if (c != 10) {
                buf.append((char) c);

                // Process buffer
            } else {
                // Retroeve complete message from Serial buffer, then reset
                // buffer
                String message = buf.toString();
                buf.setLength(0);

                // Remove the last character (line break)
                if (message.length() > 0)
                    message = message.substring(0, message.length() - 1);

                // Send "#" messages to console, others to processMessage
                if (message.startsWith("#")) {
                    System.out.println("bot: " + message);

                    // Obtain the bot's current XY through the idle messages
                    if (message.startsWith("#idle, xy:")) {
                        message = message.substring(11, message.length());
                        String[] tokens = message.split(" ");
                        this.botX = (int) (Integer.valueOf(tokens[0]) - tx);
                        this.botY = (int) (Integer.valueOf(tokens[1]) - ty);
                    }
                } else {
                    processMessage(message);
                }

            }
        }
    }

    /**
     * Processes messages sent from the Serial port from the Arduino
     * 
     * @param message
     *            Message sent from Arduino
     */
    public void processMessage(String message) {
        if (message.equals("OK")) {
            System.out.println("received ok");

            if (instructions != null) {
                if (currentInst >= instructions.size()) {
                    System.out.println("nothing to do");
                } else {
                    Instruction inst = instructions.get(currentInst);
                    currentInst++;
                    sendInstruction(inst);
                }
            }
        } else {
            System.out.println("unknown: " + message);
        }
    }

    /**
     * Processes and sends Serial command based on supplied Instruction
     * 
     * @param i
     *            Instruction to send
     */
    public void sendInstruction(Instruction i) {
        // Abort if Serial port is unavailable
        if (port == null)
            return;

        // Prepare variables for output
        String msg = null;
        int x = (int) (i.x * scale);
        int y = (int) (i.y * scale);

        // Generate the actual command
        switch (i.type) {
        case Instruction.MOVE_ABS:
            msg = "M " + (int) (x + tx) + " " + (int) (y + ty) + '\r';
            break;
        case Instruction.LINE_ABS:
            msg = "L " + (int) (x + tx) + " " + (int) (y + ty) + '\r';
            break;
        }

        // Output message to console and write to Serial port
        System.out.println("sending (" + currentInst + "): " + msg);
        port.write(msg);
    }

    /**
     * Draws a small blue dot at the current location of the pen carriage
     * 
     * @author Jason Webb
     * @date 5/25/2012
     */
    public void drawBot() {
        parent.noStroke();

        switch (currentState) {
        case STATE_PLOTTING:
            parent.fill(33, 134, 248, 100);
            break;
        case STATE_PAUSED:
            parent.fill(254, 26, 26, 100);
            break;
        case STATE_FINISHED:
            parent.fill(28, 247, 12, 100);
            break;
        }

        parent.ellipseMode(parent.CENTER);
        parent.ellipse(botX, botY, 200, 200);
    }

    /**
     * Setter method for isPlotting variable
     * 
     * @param plotting
     *            true for plotting, false for no activity
     */
    public void setState(int state) {
        this.currentState = state;
    }

}
