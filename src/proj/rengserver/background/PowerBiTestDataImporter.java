package proj.rengserver.background;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import proj.rengserver.controller.RecordReceiver;

import java.io.File;
import java.io.FileFilter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static proj.rengstation.RengRecordItem.*;

/**
 * reading the powerbi csv file and import to the database via the api call
 *
 * @author Danny Wong
 */
public class PowerBiTestDataImporter extends Thread
{
    private static final File PREV_TESTDATA_FOLDER = new File("\\\\gilliam\\OPERATIONS\\Production Engineering\\Projects\\2021 New test equipment\\4. Data");
    private static final File PREV_TESTDATA_LAST_CHECK = new File("db", "last_check.json");
    private static final File PREV_TESTDATA_LAST_CHECK_BACKUP = new File("db", "last_check.backup");

    private static final HashMap<String, Long> lastCheck = new HashMap<>();
    boolean updatePrinted = false;

    private static final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private void saveStatus () throws Exception
    {//save status
        JsonObject lastCheckJson = new JsonObject();
        for ( Map.Entry<String, Long> set : lastCheck.entrySet() )
        {
            lastCheckJson.addProperty(set.getKey(), set.getValue());
        }
        if ( PREV_TESTDATA_LAST_CHECK.exists() ) PREV_TESTDATA_LAST_CHECK.delete();
        if ( !PREV_TESTDATA_LAST_CHECK.exists() )
        {
            PREV_TESTDATA_LAST_CHECK.getParentFile().mkdirs();
            PREV_TESTDATA_LAST_CHECK.createNewFile();
        }
        try ( RandomAccessFile check = new RandomAccessFile(PREV_TESTDATA_LAST_CHECK, "rw") )
        {
            check.writeBytes(lastCheckJson.toString());
        }
        if ( PREV_TESTDATA_LAST_CHECK_BACKUP.exists() ) PREV_TESTDATA_LAST_CHECK_BACKUP.delete();
        if ( !PREV_TESTDATA_LAST_CHECK_BACKUP.exists() )
        {
            PREV_TESTDATA_LAST_CHECK_BACKUP.getParentFile().mkdirs();
            PREV_TESTDATA_LAST_CHECK_BACKUP.createNewFile();
        }
        try ( RandomAccessFile check = new RandomAccessFile(PREV_TESTDATA_LAST_CHECK_BACKUP, "rw") )
        {
            check.writeBytes(lastCheckJson.toString());
        }
    }

    public void importData ()
    {
        if ( null == RecordReceiver.getInstance() ) return;
        try
        {
            //load status - file end of previous checking
            if ( PREV_TESTDATA_LAST_CHECK.exists() && PREV_TESTDATA_LAST_CHECK.isFile() )
            {
                try ( RandomAccessFile raf = new RandomAccessFile(PREV_TESTDATA_LAST_CHECK, "r") )
                {
                    JsonObject json = new JsonParser().parse(raf.readLine()).getAsJsonObject();
                    for ( String key : json.keySet() ) lastCheck.put(key, json.get(key).getAsLong());
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                }
            }

            //filter out test data folders
            File[] stationFolders = PREV_TESTDATA_FOLDER.listFiles(new FileFilter()
            {
                public boolean accept ( File pathname ) { return pathname.isDirectory() && pathname.getName().startsWith("CFL") && pathname.getName().endsWith(" Test Data") && pathname.getName().length() == 15; }
            });

            if ( null != stationFolders )   //maybe sometime cannot reach shared folder
            {
                boolean anyFileProcessed = false;
                nextFile:
                for ( File station : stationFolders )
                {
                    File testData = new File(station, "TestDataPowerBi.txt");
                    if ( !testData.exists() || !testData.isFile() )
                    {
                        if ( lastCheck.containsKey(testData.getParentFile().getName()) )
                        {
                            lastCheck.remove(testData.getParentFile().getName());
                            saveStatus();
                            //alert
                            System.out.println("PowerBi pointer reset for " + testData.getParentFile().getName());
                        }
                        continue;
                    }

                    //check
                    if ( lastCheck.containsKey(testData.getParentFile().getName()) )
                    {
                        if ( lastCheck.get(testData.getParentFile().getName()) < 0 )
                        {
                            lastCheck.remove(testData.getParentFile().getName());
                            saveStatus();
                        }
                        //skip this file if not updated
                        else if ( testData.length() <= lastCheck.get(testData.getParentFile().getName()) ) continue;
                    }

                    anyFileProcessed = true;
                    updatePrinted = false;
                    //read
                    try ( RandomAccessFile raf = new RandomAccessFile(testData, "r") )
                    {
                        //goto last processed
                        if ( lastCheck.containsKey(testData.getParentFile().getName()) )
                            raf.seek(lastCheck.get(testData.getParentFile().getName()));
                        String data = "";

                        nextRow:
                        while ( null != data )
                        {
                            data = raf.readLine();
                            if ( null == data || !data.contains(";") || data.startsWith("D") ) continue;

                            //build record and push to server
                            String[] csvs = data.split(( ";" ));
                            if ( csvs.length < 9 ) continue nextRow;
                            for ( String csv : csvs ) if ( csv.isEmpty() ) continue nextRow;
                            JsonObject json = new JsonObject();
                            json.addProperty(STATION.getTag(), testData.getParentFile().getName().replace("Test", "").replace("Data", "").replace(" ", ""));
                            json.addProperty(DATE_TIME.getTag(), csvs[0].substring(6, 10) + "-" + csvs[0].substring(3, 5) + "-" + csvs[0].substring(0, 2) + csvs[0].substring(10));
                            json.addProperty(WORK_ORDER.getTag(), csvs[1]);
                            json.addProperty(OPERATOR.getTag(), csvs[2]);
                            if ( csvs[3].contains(".") )
                            {
                                json.addProperty(WIRE_1.getTag(), csvs[3].split("\\.")[0]);
                                json.addProperty(WIRE_2.getTag(), csvs[3].split("\\.")[1]);
                            }
                            else
                            {
                                json.addProperty(WIRE_1.getTag(), csvs[3].substring(0, csvs[3].length() / 2));
                                json.addProperty(WIRE_2.getTag(), csvs[3].substring(csvs[3].length() / 2));
                            }
                            json.addProperty(QUANTITY.getTag(), csvs[4]);
                            json.addProperty(TODO.getTag(), csvs[5]);
                            json.addProperty(PASS.getTag(), csvs[6]);
                            json.addProperty(FAIL.getTag(), csvs[7]);
                            //new columns
                            //0= 12/11/2025 12:14:04;
                            //1= 123456;
                            // 2 = 1234;
                            // 3 = 1234.5678;
                            // 4 = 12;
                            // 5 = 12;
                            // 6 = 0;
                            // 7 = 2;
                            // 8 = 00:34;
                            // 9 = Regulating  HFR;
                            // 10 = Fail;
                            // 11 = Lux ON test FAIL;
                            // 12 = 12/11/2025 12:13:15;

                            //12/11/2025 13:59:10;
                            // 1520345;
                            // 0425;
                            // 393.0000 ;
                            // 16;
                            // 7;
                            // 9;
                            // 5;
                            // 01:26;
                            // HFYEM-PRO;
                            // Fail;
                            // Fail : Profile not matched;
                            // 12/11/2025 13:52:08;
                            json.addProperty(DURATION.getTag(), csvs[8]);
                            if ( csvs.length >= 10 ) json.addProperty(TYPE.getTag(), csvs[9]);
                            if ( csvs.length >= 11 ) json.addProperty(RESULT.getTag(), csvs[10]);
                            if ( csvs.length >= 12 ) json.addProperty(FAIL_AT.getTag(), csvs[11]);
                            if ( csvs.length >= 13 && csvs[12].length() == 19 )
                                json.addProperty(LAST_DATETIME.getTag(), csvs[12].substring(6, 10) + "-" + csvs[12].substring(3, 5) + "-" + csvs[12].substring(0, 2) + csvs[12].substring(10));

                            String result = RecordReceiver.getInstance().postRecord(json.toString());
                            System.out.println("import " + testData.getParentFile().getName() + " : " + data + " > " + result);
                            if ( !result.equals("OK") )
                            {
                                //log to a file
                                File errLog = new File("db", "fail.log");
                                if ( !errLog.exists() ) errLog.createNewFile();
                                try ( RandomAccessFile err = new RandomAccessFile(errLog, "rw") )
                                {
                                    err.seek(err.length());
                                    err.writeBytes("File : " + testData.getParentFile().getName() + "\r\n");
                                    err.writeBytes("Row : " + data + "\r\n");
                                    err.writeBytes("Result : " + result + "\r\n\r\n");
                                }
                            }
                        }
                        //remember current row, next time check from this row
                        lastCheck.put(testData.getParentFile().getName(), raf.getFilePointer());
                        saveStatus();
                    }
                    catch ( Exception e )
                    {
                        e.printStackTrace();
                    }
                }
                if ( !anyFileProcessed && !updatePrinted )
                {
                    System.out.println("All PowerBI imported at " + dbDateFormat.format(new Date()));
                    updatePrinted = true;
                }
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
    }

    public void run ()
    {
        while ( true )
        {
            try
            {
                sleep(10000); //waiting interval between each call
                importData();
            }
            catch ( Throwable t )
            {
                t.printStackTrace();
            }
        }
    }
}
