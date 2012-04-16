/*
 * Kritzelbot
 *
 * motor
 * QSH4218-35-10-027
 *          Voltage = 5.3V
 *    Phase current = 1.0A
 * Phase resistance = 5 Ohm
 *        Full step = 1.8
 * red, blue, green, black
 *
 * 37.5 rot/m quarter step (1ms delay)
 *
 */
 
#include <Servo.h>
#include <stdlib.h>

#define PB1 1
#define PB2 2
#define PB3 3
#define PB4 4
#define PB5 5

// distance between both motors (axis) 150 cm
#define AXIS_DISTANCE_X 15000
#define AXIS_DISTANCE_Y 15000

// starting position
// a = b = 10607 --> 1060.7mm
// m2s = 0.7853982
//#define START_STEPS_M1 13505
//#define START_STEPS_M2 13505

#define START_X 7500
#define START_Y 7500

// pulley radius 10mm
#define PULLEY_R 100
#define PI 3.14159
// circumference 2*PI*r = 62.8 mm 
#define CIRCUMFERENCE 628

// quarter step, 800 steps per rotation
#define STEPS_PER_ROT 800

// motor directions
#define DIR_UP 1
#define DIR_DOWN 0

// pen states
#define PEN_UP 0
#define PEN_DOWN 1
#define PEN_UP_POS 90
#define PEN_DOWN_POS 55
#define PEN_DELAY 500

// driver states 
#define D_STATE_IDLE 0 
#define D_STATE_PULSE 1
#define D_STATE_PULSE_DOWN 2
#define D_STATE_PAUSE 3

// main states
#define M_STATE_WAIT_READ 0
#define M_STATE_WAIT_IDLE 1
#define M_STATE_WAIT_NOT_IDLE 2

// default speed
#define PAUSE_DELAY 1

// possible commands
#define CMD_NONE 0
#define CMD_LINE_A 1
#define CMD_MOVE_A 2
#define CMD_LINE_R 3
#define CMD_MOVE_R 4
#define CMD_HELLO 5
#define CMD_END  6

#define SERVO_PIN 8


byte ledPin = 6; 
//byte ledPin = 13; 

byte stepPinM1 = 9;  // PB1
byte dirPinM1 = 10;  // PB2
byte stepPinM2 = 11; // PB3
byte dirPinM2 = 12;  // PB4

float m2s;
Servo servo;

int currentX = 0;
int currentY = 0;
byte currentCommand = CMD_NONE;

long targetX = 0;
long targetY = 0;
int dirM1 = 1;
int dirM2 = 1;
long stepsM1 = 0;
long stepsM2 = 0;
long targetM1 = 0;
long targetM2 = 0;
long dsM1 = 0;
long dsM2 = 0;
long dM1 = 0;
long dM2 = 0;
int err = 0;
int e2 = 0;
byte driverState = D_STATE_IDLE;
byte penState = PEN_UP;
byte mainState = M_STATE_WAIT_READ;

byte pause_count = 1;
byte newTarget = 0;

int led = 0;

byte cmdPtr;
byte cmpTop;

void setup() {

  // Serial.begin(38400);
  Serial.begin(57600);
  Serial.println("#start up");

  // compute mm to steps
  m2s = (2 * PI * PULLEY_R) / STEPS_PER_ROT; 

  // compute starting pos
  currentX = START_X;
  currentY = START_Y;
  targetX = currentX;
  targetY = currentY;
  stepsM1 = computeA(currentX, currentY) / m2s;
  stepsM2 = computeB(currentX, currentY) / m2s;
  targetM1 = stepsM1;
  targetM2 = stepsM2;
  Serial.print("#start XY: "); Serial.print(currentX); Serial.print(" "); Serial.println(currentY);
  Serial.print("#start steps: "); Serial.print(stepsM1); Serial.print(" "); Serial.println(stepsM2);

  pinMode(ledPin, OUTPUT);
  pinMode(dirPinM1, OUTPUT);
  pinMode(stepPinM1, OUTPUT);
  pinMode(dirPinM2, OUTPUT);
  pinMode(stepPinM2, OUTPUT);
  digitalWrite(dirPinM2, LOW);

  servo.attach(SERVO_PIN);
  penState = PEN_DOWN;
  penUp();

  // 8MHz / 8 = 1MHz (prescaler 8)
  // 1MHz / 256 = 3906 Hz
  TCCR2A = 0;           // normal operation                                                  
  TCCR2B = (1<<CS21);   // prescaler 8                                                       
  TIMSK2 = (1<<TOIE2);  // enable overflow interrupt                                         

  while (Serial.available()) {
    Serial.read();
  }
  Serial.println("OK");
}

void printState() {
  Serial.print("#state: ");
  Serial.print(driverState, DEC);
  Serial.print(", M1: ");
  Serial.print(stepsM1, DEC);
  Serial.print(", M2: ");
  Serial.println(stepsM2);
}


byte preScaler = 0;

ISR(TIMER2_OVF_vect) {

  preScaler++;
  if (preScaler < 2) {
    return;
  }
  preScaler = 0;

  switch (driverState) {
  case D_STATE_IDLE:
    if (newTarget) {
      newTarget = 0;
      digitalWrite(ledPin, HIGH);
      // compute deltas
      dM1 = abs(targetM1 - stepsM1);
      dM2 = abs(targetM2 - stepsM2);
      err = dM1 - dM2;
      // set directions
      dsM1 = (targetM1 > stepsM1) ? +1 : -1;
      dsM2 = (targetM2 > stepsM2) ? +1 : -1;
      digitalWrite(dirPinM1, (targetM1 > stepsM1) ? DIR_UP : DIR_DOWN);
      digitalWrite(dirPinM2, (targetM2 > stepsM2) ? DIR_DOWN : DIR_UP);
      driverState = D_STATE_PULSE;
    }
    break;
  case D_STATE_PULSE:
    e2 = err * 2;
    if (e2 > -dM2) {
      err = err - dM2;
      digitalWrite(stepPinM1, HIGH);
      stepsM1 += dsM1;
    }
    if (e2 < dM1) {
      err = err + dM1;
      digitalWrite(stepPinM2, HIGH);
      stepsM2 += dsM2;
    }
    pause_count = 0;
    driverState = D_STATE_PULSE_DOWN;
    break;
  case D_STATE_PULSE_DOWN:
    digitalWrite(stepPinM1, LOW);
    digitalWrite(stepPinM2, LOW);
    if ((stepsM1 == targetM1) && (stepsM2 == targetM2)) {
      driverState = D_STATE_IDLE;
      digitalWrite(ledPin, LOW);
    }
    else if (pause_count < PAUSE_DELAY) {
      driverState = D_STATE_PAUSE;
    }
    else {
      driverState = D_STATE_PULSE;
    }
    break;
  case D_STATE_PAUSE:
    if (++pause_count >= PAUSE_DELAY) {
      driverState = D_STATE_PULSE;
    }
    break;
  }
}


int computeA(long x, long y) {
  return sqrt(x * x + y * y);
}
  
int computeB(long x, long y) {
  long distanceX = AXIS_DISTANCE_X - x;
  return sqrt((distanceX * distanceX) + y * y);
}

void readToken(char *buf) {
  int c;
  while (true) {
    if (Serial.available()) {
      c = Serial.read();
      //DEBUG_PRINT(c);
      if ((c == ' ') || (c == 13) || (c == -1) || (c == ';')) {
        break;
      }
      *buf++ = c;
    }
  }
  *buf = '\0';
}

void skipWhiteSpace() {
  int c;
  while (true) {
    if (Serial.available()) {
      c = Serial.peek();
      if ((c != ' ') && (c != 13)) {
        break;
      }
      else {
	Serial.read();
      }
    }
  }
}

long readLong() {
  char buf[20]; 
  skipWhiteSpace();
  readToken(buf);
  // Serial.print("buf:");
  // Serial.println(buf);
  return atol(buf);
}

void penUp() {
  if (penState == PEN_DOWN) {
    penState = PEN_UP;
    servo.write(PEN_UP_POS);
    delay(PEN_DELAY);
  }
}

void penDown() {
  if (penState == PEN_UP) {
    penState = PEN_DOWN;
    servo.write(PEN_DOWN_POS);
    delay(PEN_DELAY);
  }
}


long a, b;
int cmd;
long newX, newY;
long arg1, arg2;

void loop() {

  switch (mainState) {
  case M_STATE_WAIT_READ:
    if (Serial.available()) {
      cmd = Serial.read();
      switch (cmd) {
      case 'M':
	arg1 = readLong();
	arg2 = readLong();
	currentCommand = CMD_MOVE_A;
	Serial.print("#move abs "); Serial.print(arg1); Serial.print(" "); Serial.println(arg2);
	mainState = M_STATE_WAIT_IDLE;
	Serial.println("#--> WAIT_IDLE");
	break;
      case 'm':	
	arg1 = readLong();
	arg2 = readLong();
	currentCommand = CMD_MOVE_R;
	Serial.print("#move rel "); Serial.print(arg1); Serial.print(" "); Serial.println(arg2);
	mainState = M_STATE_WAIT_IDLE;
	Serial.println("#--> WAIT_IDLE");
	break;
      case 'L':
	arg1 = readLong();
	arg2 = readLong();
	currentCommand = CMD_LINE_A;
	Serial.print("#line abs "); Serial.print(arg1); Serial.print(" "); Serial.println(arg2);
	mainState = M_STATE_WAIT_IDLE;
	Serial.println("#--> WAIT_IDLE");
	break;
      case 'l':
	arg1 = readLong();
	arg2 = readLong();
	currentCommand = CMD_LINE_R;
	Serial.print("#line rel "); Serial.print(arg1); Serial.print(" "); Serial.println(arg2);
	mainState = M_STATE_WAIT_IDLE;
	Serial.println("#--> WAIT_IDLE");
	break;
      case 'h':
	skipWhiteSpace();
	currentCommand = CMD_HELLO;
	penUp();
	Serial.println("OK");
	break;
      default:
	Serial.print("#unknown command: ");
	Serial.println(cmd);
      }
    }
    break;
  case M_STATE_WAIT_IDLE:
    if (driverState == D_STATE_IDLE) {
      currentX = targetX;
      currentY = targetY;
      Serial.print("#idle, steps: "); Serial.print(stepsM1); Serial.print(" "); Serial.println(stepsM2);
      Serial.print("#idle, xy: "); Serial.print(currentX); Serial.print(" "); Serial.println(currentY);
      switch (currentCommand) {
      case CMD_MOVE_A:
	penUp();
	newX = arg1;
	newY = arg2;
	break;
      case CMD_MOVE_R:
	penUp();
	newX = currentX + arg1;
	newY = currentY + arg2;
	break;
      case CMD_LINE_A:
	penDown();
	newX = arg1;
	newY = arg2;
	break;
      case CMD_LINE_R:
	penDown();
	newX = currentX + arg1;
	newY = currentY + arg2;
	break;
      }
      Serial.print("#new XY: "); Serial.print(newX); Serial.print(" "); Serial.println(newY);
      a = computeA(newX, newY);
      b = computeB(newX, newY);
      // Serial.print("target a b: "); Serial.print(a); Serial.print(" "); Serial.println(b);
      targetM1 = a / m2s;
      targetM2 = b / m2s;
      Serial.print("#target steps: "); Serial.print(targetM1); Serial.print(" "); Serial.println(targetM2);
      newTarget = 1;
      Serial.println("#--> WAIT_NOT_IDLE");
      mainState = M_STATE_WAIT_NOT_IDLE;
    }
    break;
  case M_STATE_WAIT_NOT_IDLE:
    if ((driverState != D_STATE_IDLE) || (newTarget == 0)) {
      targetX = newX;
      targetY = newY;
      Serial.println("OK");
      Serial.println("#--> WAIT_READ");
      mainState = M_STATE_WAIT_READ;
    }
    break;
  }
}
