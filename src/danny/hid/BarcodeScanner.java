package danny.hid;

import java.util.*;

//library for native keyboard/mouse hook
import org.jnativehook.*;
import org.jnativehook.keyboard.*;

/**
 * keyboard input reader for capturing Zebra LS1203 code scanner input
 * code scanner required to config to add an ENTER key after
 * print the Barcode Scanner LS1203.pdf out and scan configuration barcode 1,2,3 at "ADD AN ENTER KEY(CARRIAGE RETURN/LINE FEED)
 *
 * @author Danny Wong
 */
public class BarcodeScanner implements NativeKeyListener
{
    private ArrayList<BarcodeListener> listeners = new ArrayList<BarcodeListener>();

    long lastReceived = -1;
    static final int TIMEOUT = 50;
    StringBuffer buffer = new StringBuffer( "" );

    public void addBarcodeListener ( BarcodeListener listener ) throws NativeHookException
    {
        if ( listeners.contains( listener ) ) return;
        listeners.add( listener );
        if ( listeners.size() == 1 ) init();
    }

    public void removeBarcodeListener ( BarcodeListener listener )
    {
        if ( !listeners.contains( listener ) ) return;
        listeners.remove( listener );
        listeners.trimToSize();
        if ( listeners.size() == 0 ) close();
    }

    public void init () throws NativeHookException
    {
        if ( !GlobalScreen.isNativeHookRegistered() ) GlobalScreen.registerNativeHook();
        GlobalScreen.getInstance().addNativeKeyListener( this );
    }

    public void close ()
    {
        //Clean up the native hook.
        try
        {
            GlobalScreen.getInstance().removeNativeKeyListener( this );
            if ( GlobalScreen.isNativeHookRegistered() ) GlobalScreen.unregisterNativeHook();
        }
        catch ( NativeHookException nhe )
        {
        }
    }

    public void nativeKeyReleased ( NativeKeyEvent e )
    {
    }

    public void nativeKeyPressed ( NativeKeyEvent e )
    {
    }

    public void nativeKeyTyped ( NativeKeyEvent e )
    {
        if ( lastReceived + TIMEOUT <= System.currentTimeMillis() )
            buffer.delete( 0, buffer.length() );    //timeout to filter out human input
        lastReceived = System.currentTimeMillis();

        if ( 9 == e.getKeyChar() )
        {
            if ( buffer.length() > 0 )
            {
                final String code = buffer.toString();
                buffer.delete( 0, buffer.length() );
                for ( BarcodeListener listener : listeners )
                {
                    try { listener.codeScanned( code ); }
                    catch ( Throwable t ) {}
                }
            }
        }
        else
        {
            buffer.append( (char) e.getKeyChar() );
        }
    }

    public static void main ( String[] args ) throws Exception
    {
        new BarcodeScanner().addBarcodeListener( new BarcodeListener()
        {
            public void codeScanned ( String code )
            {
                System.out.println( "Code : " + code );
            }
        } );
    }
}












