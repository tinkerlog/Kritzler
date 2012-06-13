
export CP=lib/RXTXcomm.jar:lib/core.jar:lib/serial.jar:bin:lib/geomerative.jar:lib/controlP5.jar

java -d32 -Djava.library.path=lib -cp $CP com.tinkerlog.kritzler.Plotter
