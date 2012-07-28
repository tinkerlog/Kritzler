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
 * Pololu - Mot(Tri) - Mot(Spa) - Mot(Pololu/1200)
 *     2B - BLK      - RED      - RED
 *     2A - BRN/GRN  - GRN      - BLU
 *     1A - BLU      - BLU      - GRN
 *     1B - RED      - BLK      - BLK
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
#define MIN_X 4000
#define MAX_X 11000
#define MIN_Y 4000
#define MAX_Y 12000

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
#define PEN_UP_POS 55
#define PEN_DOWN_POS 90
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
#define CMD_CHAR_OFF 'o'
#define CMD_CHAR_ON 'O'

// pin defines
#define MS1_PIN 2
#define ENABLE_PIN 3
#define MS3_PIN 4
#define SERVO_PIN 5
#define STEP_PIN_M1 6
#define DIR_PIN_M1 7
#define STEP_PIN_M2 8
#define DIR_PIN_M2 9
#define LED_PIN1 10
#define LED_PIN2 14

#define MAX_BUFFER_SIZE 50

float m2s;
Servo servo;

long currentX = 0;
long currentY = 0;
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

#define MAX_COMMANDS 10
command cmdBuffer[MAX_COMMANDS];
byte readPtr = 0;
byte writePtr = 0;


void setup() {

  while (!Serial) {
    ;
  }
  Serial.begin(57600);
  Serial.println("#start up");

  // compute mm to steps
  m2s = (2 * PI * PULLEY_R) / STEPS_PER_ROT; 

  // compute starting pos
  currentX = START_X;
  currentY = START_Y;
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

  // 16 MHz / 8 = 2 MHz (prescaler 8)
  // 2MHz / 256 = 7812.5 Hz
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
 */
ISR(TIMER2_OVF_vect) {

  long tM1, tM2;
  byte cmd;

  preScaler++;
  if (preScaler < 4) {
    return;
  }
  preScaler = 0;

  switch (driverState) {
  case D_STATE_IDLE:
    if (writePtr != readPtr) {
      idleCount = 0;
      digitalWrite(ENABLE_PIN, LOW);
      digitalWrite(LED_PIN1, HIGH);
      newReadPtr = (readPtr + 1) % MAX_COMMANDS;
      // read the actual command 
      cmd = cmdBuffer[newReadPtr].cmd;
      if ((cmd != CMD_CHAR_ON) && (cmd != CMD_CHAR_OFF)) {
	tM1 = cmdBuffer[newReadPtr].targetM1;
	tM2 = cmdBuffer[newReadPtr].targetM2;
	// compute deltas
	dM1 = abs(tM1 - stepsM1);
	dM2 = abs(tM2 - stepsM2);
	err = dM1 - dM2;
	// set directions
	dsM1 = (tM1 > stepsM1) ? +1 : -1;
	dsM2 = (tM2 > stepsM2) ? +1 : -1;
	digitalWrite(DIR_PIN_M1, (tM1 > stepsM1) ? DIR_UP : DIR_DOWN);
	digitalWrite(DIR_PIN_M2, (tM2 > stepsM2) ? DIR_DOWN : DIR_UP);
	targetM1 = tM1;
	targetM2 = tM2;
	// go to pulsing/stepping state ...
	driverState = D_STATE_PULSE;      
      }
      // ... but move the pen up or down before, if needed
      switch (cmd) {
      case CMD_CHAR_ON:
	digitalWrite(LED_PIN1, HIGH);
        digitalWrite(ENABLE_PIN, LOW);
	readPtr = newReadPtr;
	break;
      case CMD_CHAR_OFF:
	digitalWrite(LED_PIN1, LOW);
        digitalWrite(ENABLE_PIN, HIGH);
	readPtr = newReadPtr;
	break;
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
      /*
      Serial.print("state: ");
      Serial.println(driverState, DEC);
      Serial.print("target: ");
      Serial.print(targetM1);
      Serial.print(", ");
      Serial.print(targetM2);
      Serial.print(", delta: ");
      Serial.print(dsM1);
      Serial.print(", ");
      Serial.println(dsM2);
      */
    }
    else {
      /*
      idleCount++;
      if (idleCount == 10000) {
	// disable the motors if not in use
        digitalWrite(LED_PIN1, LOW);
        digitalWrite(ENABLE_PIN, HIGH);
      }
      */
    }
    break;
  case D_STATE_WAIT_SERVO:
    if (servoDelay++ >= PEN_DELAY) {
      driverState = D_STATE_PULSE;
      servoDelay = 0;
    }
    break;
  case D_STATE_PULSE:
    e2 = err * 2;
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
    /*
    Serial.print("steps: ");
    Serial.print(stepsM1);
    Serial.print(", ");
    Serial.print(stepsM2);
    Serial.print(", ");
    Serial.print(targetM1);
    Serial.print(", ");
    Serial.println(targetM2);
    */
    pause_count = 0;
    driverState = D_STATE_PULSE_DOWN;
    break;
  case D_STATE_PULSE_DOWN:
    digitalWrite(STEP_PIN_M1, LOW);
    digitalWrite(STEP_PIN_M2, LOW);
    if ((stepsM1 == targetM1) && (stepsM2 == targetM2)) {
      driverState = D_STATE_IDLE;
      // signal that we have consumed the command by advancing the read pointer
      readPtr = newReadPtr;
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
  long tx = 0, ty = 0;
  long a, b;
  char buf[10];

  digitalWrite(LED_PIN2, HIGH);

  // wait until there is space for this command
  do {
    newWritePtr = (writePtr+1) % MAX_COMMANDS;
    if (newWritePtr == readPtr) {
      delay(1000);
      Serial.println("#waiting ...");
    }
  } while (newWritePtr == readPtr);

  switch (line[0]) {
  case 'm':
  case 'l':
    tcmd = line[0];
    line += 2; // skip command and space
    line = readToken(line, buf, ' ');
    tx = atol(buf) + currentX;
    line = readToken(line, buf, ' ');
    ty = atol(buf) + currentY;
    break;
  case 'M':
  case 'L':
    tcmd = line[0];
    line += 2; // skip command and space
    line = readToken(line, buf, ' ');
    tx = atol(buf);
    line = readToken(line, buf, ' ');
    ty = atol(buf);
    break;
  case 'O':
  case 'o':
    tcmd = line[0];
    tx = currentX;
    ty = currentY;
    break;
  default:
    Serial.print("#unknown command: ");
    Serial.println(line[0]);
    return 1;
  }

  if (tx < MIN_X) tx = MIN_X;
  if (tx > MAX_X) tx = MAX_X;
  if (ty < MIN_Y) ty = MIN_Y;
  if (ty > MAX_Y) ty = MAX_Y;

  Serial.print("#cmd: ");
  Serial.print(tcmd);
  Serial.print(", x:" );
  Serial.print(tx);
  Serial.print(", y:");
  Serial.println(ty);

  // compute a and b from x and y
  a = computeA(tx, ty);
  b = computeB(tx, ty);

  currentX = tx;
  currentY = ty;
  cmdBuffer[newWritePtr].x = tx;
  cmdBuffer[newWritePtr].y = ty;
  cmdBuffer[newWritePtr].cmd = tcmd;
  cmdBuffer[newWritePtr].targetM1 = a / m2s;  // convert to steps for motor 1
  cmdBuffer[newWritePtr].targetM2 = b / m2s;  // convert to steps for motor 2

  // advance the write ptr
  writePtr = newWritePtr;

  digitalWrite(LED_PIN2, LOW);
  Serial.println("OK");
  return 0;
}

byte readLine(char *line, byte size) {
  byte length = 0;
  char c;
  while (length < size) {
    if (Serial.available()) {
      c = Serial.read();
      length++;
      if ((c == '\r') || (c == '\n')) {
	*line = '\0';
        break;
      }
      *line++ = c;
    }
  }
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

