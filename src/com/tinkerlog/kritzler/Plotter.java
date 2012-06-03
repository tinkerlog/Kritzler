package com.tinkerlog.kritzler;

import geomerative.RG;
import geomerative.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import processing.core.PApplet;
import processing.serial.Serial;

@SuppressWarnings("serial")
public class Plotter extends PApplet {

    private static final String BUFFER_ACC_PATH = "buffer_acc/";
    private static final String BUFFER_DONE_PATH = "buffer_done/";
    private static final String BUFFER_DENIED_PATH = "buffer_denied/";

    private static final int MAX_PLOTTER_X = 6000;
    private static final int MAX_PLOTTER_Y = 3000;

    // private static final int MAX_SCREEN_X = 800;
    private static final int MAX_SCREEN_Y = 500;

    private static final int SCREEN_PADDING = 20;

    private static final int START_X = 2500;
    private static final int START_Y = 2500;
    private static final int HOME_X = 5715 - START_X;
    private static final int HOME_Y = 3778 - START_Y;

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
    private static final float DELTA_STEP = 100.0F;

    float screenScale = 0.0F;
    float plotterScale = 1.0F;
    float svgScale = 5.0F;
    float dx, dy;

    private RShape shape;
    private Serial port;
    private Kritzler plotter;
    private String currentFileName;
    private List<Instruction> instructions;
    private PrintWriter output;
    private int currentFileIndex = 0;
    private int availableFiles;

    private long nextUpdate = 0;
    private boolean paused = false;
    private boolean plotting = false;
    
    private boolean DRAW_GRID = true;
    private boolean DRAW_BOUNDING_BOX = true;

    /**
     * Initial sketch set up
     * 
     * @see processing.core.PApplet#setup()
     */
    public void setup() {

        // Set up the serial connection
        if (serialAvailable) {
            println("Available serial ports:");
            println(Serial.list());
            port = new Serial(this, Serial.list()[2], 57600);
        }

        // Set up the Geomerative library
        RG.init(this);

        // Determine the screen scale and window size
        screenScale = (MAX_SCREEN_Y - 2F * SCREEN_PADDING) / MAX_PLOTTER_Y;
        int xsize = (int) (MAX_PLOTTER_X * screenScale) + 2 * SCREEN_PADDING;
        int ysize = (int) (MAX_PLOTTER_Y * screenScale) + 2 * SCREEN_PADDING;
        System.out.println(screenScale + " " + xsize + " " + ysize);

        smooth();
        size(xsize, ysize);
        background(100);
    }

    /**
     * Main program loop
     * 
     * @see processing.core.PApplet#draw()
     */
    public void draw() {
        switch (state) {

        // Initial sketch set up
        case STATE_START:
            dx = 0;
            dy = 0;
            shape = null;
            translate(SCREEN_PADDING, SCREEN_PADDING);
            scale(screenScale);
            rect(0, 0, MAX_PLOTTER_X, MAX_PLOTTER_Y);
            state = STATE_WAITING;
            break;

        // Load SVG file contents, wait to load
        case STATE_WAITING:
            if (shape == null && System.currentTimeMillis() > nextUpdate) {
                nextUpdate = System.currentTimeMillis() + 3000;
                shape = loadNewShape();
                if (shape != null) {
                    state = STATE_PLOTTING_SCREEN;
                }
            }
            break;

        // Draw the canvas to the screen
        case STATE_PLOTTING_SCREEN:
            drawCanvas();
            state = STATE_WAITING_INPUT;
            break;

        // Waiting for user to begin plotting by pressing 'p'
        case STATE_WAITING_INPUT:
            if (plotting) {
                state = STATE_SETUP_PLOTTER;
                plotting = false;
            }
            break;

        // Generate Instructions from the SVG file, thn set up the Kritzler
        // object
        case STATE_SETUP_PLOTTER:
            List<Instruction> instructions = new ArrayList<Instruction>();
            convertToInstructions(instructions, shape);
            setupPlotter(instructions);
            state = STATE_PLOTTING;
            break;

        // Actively plotting
        case STATE_PLOTTING:
            if (!paused) {
                plotter.checkSerial();

                // When finished drawing, return to the coordinates specified by
                // HOME_X and HOME_Y
                if (plotter.isFinished()) {
                    plotter.setState(plotter.STATE_FINISHED);
                    drawCanvas();                    
                    plotter.drawBot();

                    state = STATE_WAITING;
                } else {
                    plotter.setState(plotter.STATE_PLOTTING);
                    drawCanvas();                    
                    plotter.drawBot();
                }
            } else {
                plotter.setState(plotter.STATE_PAUSED);
                drawCanvas();                
                plotter.drawBot();
            }
            break;
        }
    }

    /**
     * Set up the Kritzler object
     * 
     * @param instructions
     *            Instruction set to use
     */
    public void setupPlotter(List<Instruction> instructions) {
        plotter = new Kritzler(this, port);
        plotter.setInstructions(instructions);
        plotter.translate(START_X + dx, START_Y + dy);
        plotter.setScale(plotterScale);
    }

    /**
     * Draw the canvas to the screen
     */
    public void drawCanvas() {
        
        // Change the background color to match the state of the plotter
        if( plotter != null ) {
            int state = plotter.getState();
            switch(state) {
            case 0:
                background(33, 134, 248, 100);
                break;
            case 1:
                background(254, 26, 26, 100);
                break;
            case 2:
                background(28, 247, 12, 100);
                break;
            default:
                background(100);
            }
        } else {
            background(100);
        }

        // Draw the canvas rectangle
        translate(SCREEN_PADDING, SCREEN_PADDING);
        scale(screenScale * plotterScale);
        fill(255);        
        rect(0, 0, MAX_PLOTTER_X, MAX_PLOTTER_Y);
        
        // Draw the grid
        if(DRAW_GRID) {
            stroke(210);
            int cols = MAX_PLOTTER_X / 100;
            int rows = MAX_PLOTTER_Y / 100;
            
            for(int i=0; i<cols; i++)
                line(i*100, 0, i*100, MAX_PLOTTER_Y);
            
            for(int i=0; i<rows; i++)
                line(0, i*100, MAX_PLOTTER_X, i*100);
        }
        
        // Draw the homing crosshairs
        strokeWeight(1);
        stroke(150);
        line(MAX_PLOTTER_X/2, 0, MAX_PLOTTER_X/2, MAX_PLOTTER_Y);
        line(0, MAX_PLOTTER_Y/2, MAX_PLOTTER_X, MAX_PLOTTER_Y/2);

        translate(dx, dy);        
        
        // Draw the bounding box of the current shape
        if(DRAW_BOUNDING_BOX) {
            // Bounding box
            RPoint bounds[] = shape.getBoundsPoints();
            strokeWeight(5);
            stroke(255,0,0);
            line( bounds[0].x, bounds[0].y, bounds[1].x, bounds[1].y );
            line( bounds[1].x, bounds[1].y, bounds[2].x, bounds[2].y );
            line( bounds[2].x, bounds[2].y, bounds[3].x, bounds[3].y );
            line( bounds[3].x, bounds[3].y, bounds[0].x, bounds[0].y );
            
            // Center cross hairs
            RPoint center = shape.getCenter();
            line( center.x, bounds[0].y, center.x, bounds[0].y - 200 );
            line( center.x, bounds[3].y, center.x, bounds[3].y + 200 );
            line( bounds[0].x, center.y, bounds[0].x - 200, center.y );
            line( bounds[1].x, center.y, bounds[1].x + 200, center.y );
        }
        
        // Draw the SVG content
        strokeWeight(STROKE_WEIGHT);
        stroke(0);
        drawShape(shape);
    }

    /**
     * Draw the SVG file contents to the screen
     * 
     * @param shape
     *            Shape to draw
     */
    public void drawShape(RShape shape) {
        // Recurse through any children of this shape
        for (int i = 0; i < shape.countChildren(); i++) {
            RShape s = shape.children[i];
            drawShape(s);
        }

        // Iterate through each path of the shape, drawing them to the screen
        for (int i = 0; i < shape.countPaths(); i++) {
            // Get the points of the current path
            RPath p = shape.paths[i];
            RPoint[] points = p.getPoints();

            // Connect each of the points using lines
            for (int k = 0; k < points.length - 1; k++) {
                line(points[k].x, points[k].y, points[k + 1].x, points[k + 1].y);
            }
        }
    }

    /**
     * Convert SVG file contents into Instruction objects
     * 
     * @param instructions
     *            Resulting List of Instruction objects
     * @param shape
     *            RShape to proces
     */
    public void convertToInstructions(List<Instruction> instructions,
            RShape shape) {
        // Recurse through any children of current shape
        for (int i = 0; i < shape.countChildren(); i++) {
            RShape s = shape.children[i];
            convertToInstructions(instructions, s);
        }

        // Generate Instruction objects for every path of shape
        for (int i = 0; i < shape.countPaths(); i++) {
            // Get the first point of this path
            RPath p = shape.paths[i];
            RPoint[] points = p.getPoints();
            RPoint p1 = points[0];

            // Move to that point
            instructions.add(new Instruction(Instruction.MOVE_ABS, p1.x, p1.y));

            // Draw lines to any subsequent points
            for (int k = 0; k < points.length - 1; k++) {
                RPoint p2 = points[k];
                instructions.add(new Instruction(Instruction.LINE_ABS, p2.x,
                        p2.y));
            }
        }
    }

    /**
     * Process keyboard input
     * 
     * @see processing.core.PApplet#keyPressed()
     */
    public void keyPressed() {
        switch (key) {

        // p = begin plotting
        case 'p':
            plotting = true;
            break;

        // [space] = pause plotting
        case ' ':
            paused = !paused;
            break;

        // Arrow keys = translate shape around canvas
        case CODED:
            if (shape == null || state == STATE_PLOTTING)
                return;

            // Move SVG shape around based on arrow keys
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

        // l = scale shape down
        case 'l':
            if (shape == null)
                return;

            shape.scale(0.95F);
            print(shape, "l: ");
            state = STATE_PLOTTING_SCREEN;
            break;

        // L = scale shape up
        case 'L':
            if (shape == null)
                return;

            shape.scale(1.05F);
            print(shape, "L: ");
            state = STATE_PLOTTING_SCREEN;
            break;

        // m = flip shape horizontally
        case 'm':
            if (shape == null)
                return;

            float width = shape.getBottomRight().x;
            shape.scale(-1.0F, 1.0F);
            shape.translate(width, 0);
            print(shape, "m3 : ");
            state = STATE_PLOTTING_SCREEN;
            break;

        // M = flip shape vertically
        case 'M':
            if (shape == null)
                return;

            shape.scale(1.0F, -1.0F);
            state = STATE_PLOTTING_SCREEN;
            break;

        // s = deny shape (?)
        case 's':
            if (shape == null)
                return;

            moveShapeDenied();
            state = STATE_START;
            break;

        // e = export Instructions to text file
        case 'e':
            export();
            break;
            
        // ] = next SVG file
        case ']':
            if(currentFileIndex < availableFiles-1)
                currentFileIndex++;
            else
                currentFileIndex = 0;
            
            shape = loadNewShape();
            state = STATE_PLOTTING_SCREEN;
            break;
        
        // [ = previous SVG file
        case '[':
            if(currentFileIndex > 0)
                currentFileIndex--;
            else
                currentFileIndex = availableFiles-1;
            
            shape = loadNewShape();
            state = STATE_PLOTTING_SCREEN;            
            break;
            
        // g = toggle grid
        case 'g':
            DRAW_GRID = !DRAW_GRID;
            state = STATE_PLOTTING_SCREEN;
            break;
            
        // b = toggle bounding box
        case 'b':
            DRAW_BOUNDING_BOX = !DRAW_BOUNDING_BOX;
            state = STATE_PLOTTING_SCREEN;
            break;
            
        }
    }

    /**
     * Print a message to the console, along with the coordinates of the top
     * left and bottom right extends of the SVG shape
     * 
     * @param p
     *            RShape to acquire extents from
     * @param msg
     *            Message to print to console
     */
    private void print(RShape p, String msg) {
        RPoint p1 = p.getTopLeft();
        RPoint p2 = p.getBottomRight();
        System.out.println(msg + " (" + p1.x + ", " + p1.y + "), (" + p2.x
                + ", " + p2.y + ")");
    }

    /**
     * Move current SVG file to the folder specified by BUFFER_DONE_PATH
     */
    private void moveShapeDone() {
        System.out.println("moving file to " + BUFFER_DONE_PATH
                + currentFileName);
        File file = new File(BUFFER_ACC_PATH + currentFileName);
        File newFile = new File(BUFFER_DONE_PATH + currentFileName);
        file.renameTo(newFile);
    }

    /**
     * Move current SVG file to the folder specified by BUFFER_DENIED_PATH
     */
    private void moveShapeDenied() {
        System.out.println("moving file to " + BUFFER_DENIED_PATH
                + currentFileName);
        File file = new File(BUFFER_ACC_PATH + currentFileName);
        File newFile = new File(BUFFER_DENIED_PATH + currentFileName);
        file.renameTo(newFile);
    }

    /**
     * Loads the first SVG file from the BUFFER_ACC_PATH folder
     * 
     * @return RShape The SVG file contents, if it exists. Otherwise null.
     */
    private RShape loadNewShape() {
        // Get a list of all SVG files in the BUFFER_ACC_PATH folder
        File dir = new File(BUFFER_ACC_PATH);
        String[] listing = dir.list(new FilenameFilter() {
            public boolean accept(File file, String filename) {
                return filename.endsWith("svg");
            }
        });
        
        availableFiles = listing.length;

        // Load the first SVG file from the list
        if (listing != null && listing.length > 0) {
            currentFileName = listing[currentFileIndex];
            System.out.println("loading " + currentFileName);
            RShape shape = RG.loadShape(BUFFER_ACC_PATH + currentFileName);
            shape.scale(svgScale, shape.getCenter());
            print(shape, "loaded: ");
            return shape;
        }

        return null;
    }

    /**
     * Export Instructions to a text file
     */
    private void export() {
        print("Outputting Instructions to file ... ");

        // Generate instructions right now
        List<Instruction> instructions = new ArrayList<Instruction>();
        convertToInstructions(instructions, shape);

        // Verify Instructions
        if (instructions == null || instructions.size() == 0) {
            println("failed\nNo instructions!");
            return;
        }

        // Prepare the output file
        output = createWriter("output/instructions.txt");
        
        // TODO: Write configuration information to the file
        

        // Write all the Instructions to the file
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            output.println(instruction.toString());
        }

        // Finish the file
        output.flush();
        output.close();

        println("done");
    }

    /**
     * Run the PApplet when this file is run as an application
     * 
     * @param args
     */
    public static void main(String args[]) {
        PApplet.main(new String[] { "com.tinkerlog.kritzler.Plotter" });
    }

}
