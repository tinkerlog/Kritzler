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
 * LED_PIN2 --- 14
 * LED_PIN1 --- 10
 * DIR2     --- 9
 * STEP2    --- 8
 * DIR1     --- 7
 * STEP1    --- 6
 * SERVO    --- 5
 * MS3      --- 4
 * ENABLE   --- 3
 * MS1      --- 2 
 *
 *     TRI   SPA
 * A - BLK - RED
 * C - GRN - GRN
 * B - RED - YEL(BLK)
 * D - BLU - BLU
 *
 * Pololu - Mot(Tri) - Mot(Spa)
 *     2B - BLK      - RED
 *     2A - BRN/GRN  - GRN
 *     1A - BLU      - BLU
 *     1B - RED      - BLK 
 *
 *
 *
 * MS1  | MS3  | 
 * -----+------+--------
 * LOW  | LOW  | quarter
 * HIGH | LOW  | eighth
 * HIGH | HIGH | sixteenth
 *
 */
 
#include <Servo.h>
#include <stdlib.h>


// distance between both motors (axis) 150 cm
#define AXIS_DISTANCE_X 15000
#define AXIS_DISTANCE_Y 15000

// starting position
// a = b = 10607 --> 1060.7mm
// m2s = 0.7853982

#define START_X 7500
#define START_Y 7500

// pulley radius 10mm
//#define PULLEY_R 100
#define PULLEY_R 96
#define PI 3.14159
// circumference 2*PI*r = 62.8 mm 

// quarter step, 800 steps per rotation
#define STEPS_PER_ROT 800

// pen states
#define PEN_UP 0
#define PEN_DOWN 1
#define PEN_UP_POS 90
#define PEN_DOWN_POS 55
// delay to wait for the pen to go up or down
#define PEN_DELAY 1000

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

// command chars
#define CMD_CHAR_LINE_A 'L'
#define CMD_CHAR_LINE_R 'l'
#define CMD_CHAR_MOVE_A 'M'
#define CMD_CHAR_MOVE_R 'm'

// pin defines
#define SERVO_PIN 5
#define LED_PIN1 10
#define LED_PIN2 14
#define ENABLE_PIN 3
#define MS1_PIN 2
#define MS3_PIN 4
#define STEP_PIN_M1 6
#define STEP_PIN_M2 8
#define DIR_PIN_M1 7
#define DIR_PIN_M2 9

#define MAX_BUFFER_SIZE 50

float m2s;
Servo servo;

long stepsM1 = 0;
long stepsM2 = 0;
long targetM1 = 0;
long targetM2 = 0;
byte penState = PEN_UP;

char line[MAX_BUFFER_SIZE];

typedef struct {
  char cmd;
  long x;
  long y;
  long targetM1;
  long targetM2;
} command;

#define MAX_COMMANDS 5
command cmdBuffer[MAX_COMMANDS];
byte readPtr = 0;
byte writePtr = 0;


void setup() {

  Serial.begin(57600);
  Serial.println("#start up");

  // compute mm to steps
  m2s = (2 * PI * PULLEY_R) / STEPS_PER_ROT; 

  // compute starting pos
  stepsM1 = computeA(START_X, START_Y) / m2s;
  stepsM2 = computeB(START_X, START_Y) / m2s;
  targetM1 = stepsM1;
  targetM2 = stepsM2;
  Serial.print("#start steps: "); Serial.print(stepsM1); Serial.print(" "); Serial.println(stepsM2);

  pinMode(MS1_PIN, OUTPUT);
  pinMode(MS3_PIN, OUTPUT);
  pinMode(ENABLE_PIN, OUTPUT);
  pinMode(DIR_PIN_M1, OUTPUT);
  pinMode(STEP_PIN_M1, OUTPUT);
  pinMode(DIR_PIN_M2, OUTPUT);
  pinMode(STEP_PIN_M2, OUTPUT);
  pinMode(LED_PIN1, OUTPUT);
  pinMode(LED_PIN2, OUTPUT);

  // blinky hello  
  digitalWrite(LED_PIN1, HIGH);
  digitalWrite(LED_PIN2, LOW);
  delay(1000);
  digitalWrite(LED_PIN1, LOW);
  digitalWrite(LED_PIN2, HIGH);
  delay(1000);
  digitalWrite(LED_PIN1, HIGH);
  digitalWrite(LED_PIN2, LOW);  
  delay(1000);
  digitalWrite(LED_PIN1, LOW);

  digitalWrite(DIR_PIN_M2, LOW);
  digitalWrite(ENABLE_PIN, LOW);
  // set motors to quarter stepping
  digitalWrite(MS1_PIN, LOW);
  digitalWrite(MS3_PIN, LOW);
  
  // move pen up
  servo.attach(SERVO_PIN);
  penState = PEN_UP;
  servo.write(PEN_UP_POS);
  delay(500);

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


// driver states 
#define D_STATE_IDLE 0 
#define D_STATE_PULSE 1
#define D_STATE_PULSE_DOWN 2
#define D_STATE_PAUSE 3
#define D_STATE_WAIT_SERVO 4

// motor directions
#define DIR_UP 1
#define DIR_DOWN 0

byte preScaler = 0;
byte newReadPtr;
word idleCount = 0;
word servoDelay = 0;
byte driverState = D_STATE_IDLE;
byte pause_count = 1;
int dsM1 = 0;
int dsM2 = 0;
int dM1 = 0;
int dM2 = 0;
int err = 0;
int e2 = 0;

/*
 * Timer overflow service routine
 * Called 3906Hz at 8MHz
 */
ISR(TIMER2_OVF_vect) {

  long tM1, tM2;
  byte cmd;

  preScaler++;
  if (preScaler < 2) {
    return;
  }
  preScaler = 0;

  switch (driverState) {
  case D_STATE_IDLE:
    if (writePtr != readPtr) {
      idleCount = 0;
      digitalWrite(ENABLE_PIN, LOW);
      newReadPtr = (readPtr + 1) % MAX_COMMANDS;
      // read the actual command 
      tM1 = cmdBuffer[newReadPtr].targetM1;
      tM2 = cmdBuffer[newReadPtr].targetM2;
      cmd = cmdBuffer[newReadPtr].cmd;
      // compute deltas
      dM1 = abs(tM1 - stepsM1);
      dM2 = abs(tM2 - stepsM2);
      err = dM1 - dM2;
      // set directions
      dsM1 = (tM1 > stepsM1) ? +1 : -1;
      dsM2 = (tM2 > stepsM2) ? +1 : -1;
      digitalWrite(DIR_PIN_M1, (targetM1 > stepsM1) ? DIR_UP : DIR_DOWN);
      digitalWrite(DIR_PIN_M2, (targetM2 > stepsM2) ? DIR_DOWN : DIR_UP);
      // go to pulsing/stepping state ...
      driverState = D_STATE_PULSE;      
      // ... but move the pen up or down before, if needed
      switch (cmd) {
      case CMD_CHAR_MOVE_A:
      case CMD_CHAR_MOVE_R:
	if (penState == PEN_DOWN) {
	  penState = PEN_UP;
	  servo.write(PEN_UP_POS);
	  driverState = D_STATE_WAIT_SERVO;
	}
	break;
      case CMD_CHAR_LINE_A:
      case CMD_CHAR_LINE_R:
	if (penState == PEN_UP) {
	  penState = PEN_DOWN;
	  servo.write(PEN_DOWN_POS);
	  driverState = D_STATE_WAIT_SERVO;
	}
	break;
      }
      Serial.print("state: ");
      Serial.print("X ");
      Serial.println(driverState, DEC);
    }
    else {
      idleCount++;
      if (idleCount == 10000) {
	// disable the motors if not in use
        digitalWrite(LED_PIN1, LOW);
        digitalWrite(ENABLE_PIN, HIGH);
      }
    }
    break;
  case D_STATE_WAIT_SERVO:
    if (servoDelay++ >= PEN_DELAY) {
      Serial.println("WAIT_SERVO --> PULSE");
      driverState = D_STATE_PULSE;
      servoDelay = 0;
    }
    break;
  case D_STATE_PULSE:
    e2 = err * 2;
    Serial.print("P");
    if (e2 > -dM2) {
      err = err - dM2;
      digitalWrite(STEP_PIN_M1, HIGH);
      stepsM1 += dsM1;
    }
    if (e2 < dM1) {
      err = err + dM1;
      digitalWrite(STEP_PIN_M2, HIGH);
      stepsM2 += dsM2;
    }
    Serial.print("steps: ");
    Serial.print(stepsM1);
    Serial.print(", ");
    Serial.println(stepsM2);
    pause_count = 0;
    driverState = D_STATE_PULSE_DOWN;
    break;
  case D_STATE_PULSE_DOWN:
    digitalWrite(STEP_PIN_M1, LOW);
    digitalWrite(STEP_PIN_M2, LOW);
    if ((stepsM1 == targetM1) && (stepsM2 == targetM2)) {
      Serial.println("PULSE_DOWN --> IDLE");
      driverState = D_STATE_IDLE;
      // signal that we have consumed the command by advancing the read pointer
      readPtr = newReadPtr;
      digitalWrite(LED_PIN2, LOW);
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
  // skipWhiteSpace();
  readToken(buf);
  Serial.print("buf:");
  Serial.println(buf);
  return atol(buf);
}

char *readToken(char *str, char *buf, char delimiter) {
  uint8_t c = 0;
  while (true) {
    c = *str++;
    if ((c == delimiter) || (c == '\0')) {
      break;
    }
    else if (c != ' ') {
      *buf++ = c;
    }
  }
  *buf = '\0';
  return str;
}

byte parseLine(char *line) {

  byte newWritePtr;
  char tcmd;
  long tx, ty;
  long a, b;
  char buf[10];

  // wait until there is space for this command
  do {
    newWritePtr = (writePtr+1) % MAX_COMMANDS;
    Serial.print("write:");
    Serial.print(newWritePtr);
    Serial.print(", read:");
    Serial.println(readPtr);
    if (newWritePtr == readPtr) {
      delay(1000);
      Serial.println("waiting ...");
    }
  } while (newWritePtr == readPtr);

  switch (line[0]) {
  case 'M':
  case 'L':
    tcmd = line[0];
    break;
  default:
    Serial.print("unknown command: ");
    Serial.println(line[0]);
    return 1;
  }
  line += 2; // skip command and space
  line = readToken(line, buf, ' ');
  tx = atol(buf);
  line = readToken(line, buf, ' ');
  ty = atol(buf);

  Serial.print("cmd: ");
  Serial.print(tcmd);
  Serial.print(", x:" );
  Serial.print(tx);
  Serial.print(", y:");
  Serial.println(ty);

  // compute a and b from x and y
  a = computeA(tx, ty);
  b = computeB(tx, ty);

  cmdBuffer[newWritePtr].x = tx;
  cmdBuffer[newWritePtr].y = ty;
  cmdBuffer[newWritePtr].cmd = tcmd;
  cmdBuffer[newWritePtr].targetM1 = a / m2s;  // convert to steps for motor 1
  cmdBuffer[newWritePtr].targetM2 = b / m2s;  // convert to steps for motor 2

  // advance the write ptr
  writePtr = newWritePtr;

  return 0;
}

byte readLine(char *line, byte size) {
  byte length = 0;
  char c;
  while (length < size) {
    if (Serial.available()) {
      c = Serial.read();
      length++;
      Serial.print(c);
      if ((c == '\r') || (c == '\n')) {
        break;
	*line = '\0';
      }
      *line++ = c;
    }
  }
  Serial.println();
  return length;
}

void loop() {
  byte length;
  byte error;
  length = readLine(line, MAX_BUFFER_SIZE);
  if (length > 0) {
    error = parseLine(line);
    if (error) {
      Serial.println("errored, stopped!");
      while (true);
    }
  }

}

