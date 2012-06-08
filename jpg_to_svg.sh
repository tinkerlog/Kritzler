
export CP=lib/RXTXcomm.jar:lib/core.jar:lib/serial.jar:bin:lib/geomerative.jar
echo "left, right for darker or lighter, p for export"
java -d32 -Djava.library.path=lib -cp $CP com.tinkerlog.kritzler.JpgToSvg $1
