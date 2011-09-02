
export CP=lib/RXTXcomm.jar:lib/core.jar:lib/serial.jar:bin:lib/geomerative.jar

java -d32 -Djava.library.path=lib -cp $CP com.tinkerlog.kritzler.FillSvg $1
