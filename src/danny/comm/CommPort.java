package danny.comm;

import java.util.*;

import com.fazecast.jSerialComm.SerialPort;

/**
 * @author Danny Wong
 */
public class CommPort
{
    public static ArrayList<String> listSerialPorts ()
    {
        final ArrayList<String> portstr = new ArrayList<String>();
        SerialPort[] ports = SerialPort.getCommPorts();
        for ( SerialPort port : ports ) portstr.add( port.getSystemPortName() );
        return portstr;
    }

    public static void main ( String[] args )
    {
        System.out.println( "Available COM port:" );
        for ( String port : listSerialPorts() ) System.out.println( port );
    }
}