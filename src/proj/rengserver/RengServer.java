package proj.rengserver;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.*;
import org.springframework.context.*;
import proj.rengserver.background.IssueReportImporter;
import proj.rengserver.background.PoolDBConnection;
import danny.dbconn.DBConnectionType;
import proj.rengserver.background.PowerBiTestDataImporter;
import proj.rengserver.background.TestDataLogAnalyzer;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.sql.ResultSet;

/**
 * @author Danny Wong
 */
@SpringBootApplication
public class RengServer
{
    public static ConfigurableApplicationContext springboot;

    public RengServer ()
    {
        //test db connection
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, "Server", "Startup") ;
              ResultSet rs = pdb.getDb().querySQL("SELECT 1") )
        {
        }
        catch ( Exception e ) { e.printStackTrace(); }

//        thread for import testdata from previous version
        //new PowerBiTestDataImporter().start();
        //new TestDataLogAnalyzer().start();
        //new IssueReportImporter().start();

        //show the dashboard UI
//        if ( new File("bin", "ui.bat").exists() )
//        {
//            try
//            {
//                ProcessBuilder processBuilder = new ProcessBuilder("\"" + new File("bin", "ui.bat").getAbsolutePath() + "\"");
//                processBuilder.start();
//            }
//            catch ( Exception e ) {}
//        }
    }

    public static void main ( String[] args )
    {
        try
        {
            String port = "80";
            boolean debug = false;
            boolean useHttps = false;
            int thread = 200;

            if ( args.length > 0 )
            {
                try { if ( Integer.parseInt(args[0]) > 0 ) port = "" + Integer.parseInt(args[0]); }
                catch ( Exception e ) {}
            }
            if ( args.length >= 1 )
            {
                for ( String arg : args )
                {
                    try
                    {
                        if ( ( "" + port ).equals(arg) ) {}
                        else if ( "-debug".equals(arg) ) debug = true;
                        else if ( "-https".equals(arg) )
                        {
                            useHttps = true;
                            if ( "80".equals(port) ) port = "443";
                        }
                        else if ( arg.startsWith("-thread:") )
                            thread = Integer.parseInt(arg.substring("-thread:".length()));
                        else throw new Exception("undefined");
                    }
                    catch ( Exception e )
                    {
                        System.out.println("invalid parameter [" + arg + "] : " + e.getMessage());
                    }
                }
            }

//            //update current process id to text file for other checking
//            try
//            {
//                final File pid = new File("db", "pid.sys");
//                if ( pid.exists() ) pid.delete();
//                if ( !pid.exists() )
//                {
//                    pid.getParentFile().mkdirs();
//                    pid.createNewFile();
//                }
//                String getpid = ManagementFactory.getRuntimeMXBean().getName();
//                getpid = getpid.substring(0, getpid.indexOf("@"));
//                RandomAccessFile raf = new RandomAccessFile(pid, "rw");
//                raf.writeBytes(getpid);
//                raf.close();
//            }
//            catch ( Exception e ) { e.printStackTrace(); }

            //setup springboot
            System.setProperty("spring.application.name", "RengServer");
            System.setProperty("server.port", port);
            System.setProperty("spring.main.banner-mode", "off");
            System.setProperty("server.tomcat.max-threads", "" + thread);

            if ( useHttps )
            {
                //setup https
                System.setProperty("server.ssl.protocol", "TLS");
                System.setProperty("server.ssl.key-store", "db/wllreng.p12");
                System.setProperty("server.ssl.key-store-password", "Wh!tecr0ft");
                System.setProperty("server.ssl.key-store-type", "PKCS12");
                System.setProperty("server.ssl.enabled", "true");
            }

//            System.setProperty( "" ,  );
            if ( debug )
            {
                System.setProperty("debug", "true");
                System.setProperty("trace", "true");
                System.setProperty("logging.file", "/log/springboot.err");
                System.setProperty("logging.level.root", "DEBUG");
            }
//            System.setProperty( "java.util.logging.SimpleFormatter.format" , "" );  //disable all springboot logs...
//            else
//            System.setProperty( "logging.level.root" , "WARN" );

            SpringApplicationBuilder builder = new SpringApplicationBuilder(RengServer.class);
            springboot = builder.headless(false).run(args);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
    }
}
