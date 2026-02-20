package proj.rengserver.background;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import proj.rengserver.controller.RecordReceiver;
import proj.rengstation.RengIssueItem;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * reading the issue report csv file and import to the database via the api call
 *
 * @author Danny Wong
 */
public class IssueReportImporter extends Thread
{
    private static final File PREV_ISSUE_FILE = new File("\\\\gilliam\\OPERATIONS\\Production Engineering\\Projects\\2021 New test equipment\\4. Data\\myFile.txt");
    private static final File PREV_ISSUE_LAST_CHECK = new File("db", "last_issue.json");
    private static final File PREV_ISSUE_LAST_CHECK_BACKUP = new File("db", "last_issue.backup");

    private static final HashMap<String, Long> lastCheck = new HashMap<>();
    boolean updatePrinted = false;

    private static final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private void saveStatus () throws Exception
    {
        //save status
        JsonObject lastCheckJson = new JsonObject();
        for ( Map.Entry<String, Long> set : lastCheck.entrySet() )
        {
            lastCheckJson.addProperty(set.getKey(), set.getValue());
        }
        if ( PREV_ISSUE_LAST_CHECK.exists() ) PREV_ISSUE_LAST_CHECK.delete();
        if ( !PREV_ISSUE_LAST_CHECK.exists() )
        {
            PREV_ISSUE_LAST_CHECK.getParentFile().mkdirs();
            PREV_ISSUE_LAST_CHECK.createNewFile();
        }
        try ( RandomAccessFile check = new RandomAccessFile(PREV_ISSUE_LAST_CHECK, "rw") )
        {
            check.writeBytes(lastCheckJson.toString());
        }
        if ( PREV_ISSUE_LAST_CHECK_BACKUP.exists() ) PREV_ISSUE_LAST_CHECK_BACKUP.delete();
        if ( !PREV_ISSUE_LAST_CHECK_BACKUP.exists() )
        {
            PREV_ISSUE_LAST_CHECK_BACKUP.getParentFile().mkdirs();
            PREV_ISSUE_LAST_CHECK_BACKUP.createNewFile();
        }
        try ( RandomAccessFile check = new RandomAccessFile(PREV_ISSUE_LAST_CHECK_BACKUP, "rw") )
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
            if ( PREV_ISSUE_LAST_CHECK.exists() && PREV_ISSUE_LAST_CHECK.isFile() )
            {
                try ( RandomAccessFile raf = new RandomAccessFile(PREV_ISSUE_LAST_CHECK, "r") )
                {
                    JsonObject json = new JsonParser().parse(raf.readLine()).getAsJsonObject();
                    for ( String key : json.keySet() ) lastCheck.put(key, json.get(key).getAsLong());
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                }
            }

            if ( !PREV_ISSUE_FILE.exists() || !PREV_ISSUE_FILE.isFile() )
            {
                if ( lastCheck.containsKey(PREV_ISSUE_FILE.getName()) )
                {
                    lastCheck.remove(PREV_ISSUE_FILE.getName());
                    saveStatus();
                    //alert
                    System.out.println("Issue Report pointer reset for " + PREV_ISSUE_FILE.getName());
                }
            }
            else
            {
                //check
                if ( lastCheck.containsKey(PREV_ISSUE_FILE.getName()) )
                {
                    if ( lastCheck.get(PREV_ISSUE_FILE.getName()) < 0 )
                    {
                        lastCheck.remove(PREV_ISSUE_FILE.getName());
                        saveStatus();
                    }
                    //skip this file if not updated
                    else if ( PREV_ISSUE_FILE.length() <= lastCheck.get(PREV_ISSUE_FILE.getName()) ) return;
                }

                updatePrinted = false;
                //read
                try ( RandomAccessFile raf = new RandomAccessFile(PREV_ISSUE_FILE, "r") )
                {
                    //goto last processed
                    if ( lastCheck.containsKey(PREV_ISSUE_FILE.getName()) )
                        raf.seek(lastCheck.get(PREV_ISSUE_FILE.getName()));
                    String data = "";

                    nextRow:
                    while ( null != data )
                    {
                        data = raf.readLine();
                        if ( null == data || !data.contains(";") ) continue;

                        //build record and push to server
                        String[] csvs = data.split(( ";" ));
                        if ( csvs.length < 4 ) continue nextRow;
                        if ( "Missing Program".equals(csvs[3]) && csvs.length < 5 ) continue nextRow;
                        if ( !"Missing Program".equals(csvs[3]) && csvs.length < 7 ) continue nextRow;
                        JsonObject json = new JsonObject();
                        json.addProperty(RengIssueItem.DATE_TIME.getTag(), csvs[0]);
                        json.addProperty(RengIssueItem.STATION.getTag(), csvs[1].trim());
                        json.addProperty(RengIssueItem.OPERATOR.getTag(), csvs[2].trim());
                        json.addProperty(RengIssueItem.ISSUE.getTag(), csvs[3].trim());
                        json.addProperty(RengIssueItem.PROGRAM.getTag(), csvs[4].trim());
                        if ( csvs.length >= 6 ) json.addProperty(RengIssueItem.REASON.getTag(), csvs[5].trim());
                        if ( csvs.length >= 7 ) json.addProperty(RengIssueItem.SCREENSHOT.getTag(), csvs[6].trim());
                        if ( !json.has(RengIssueItem.REASON.getTag()) )
                            json.addProperty(RengIssueItem.REASON.getTag(), csvs[3].trim() + " " + csvs[4].trim());

                        String result = RecordReceiver.getInstance().postIssue(json.toString());
                        System.out.println("import issue report : " + data + " > " + result);
                        if ( !result.equals("OK") )
                        {
                            //log to a file
                            File errLog = new File("db", "fail.log");
                            if ( !errLog.exists() ) errLog.createNewFile();
                            try ( RandomAccessFile err = new RandomAccessFile(errLog, "rw") )
                            {
                                err.seek(err.length());
                                err.writeBytes("File : " + PREV_ISSUE_FILE.getName() + "\r\n");
                                err.writeBytes("Row : " + data + "\r\n");
                                err.writeBytes("Result : " + result + "\r\n\r\n");
                            }
                        }
                    }
                    //remember current row, next time check from this row
                    lastCheck.put(PREV_ISSUE_FILE.getName(), raf.getFilePointer());
                    saveStatus();

                    if ( !updatePrinted )
                    {
                        System.out.println("All Issue imported at " + dbDateFormat.format(new Date()));
                        updatePrinted = true;
                    }
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
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
                sleep(5000); //waiting interval between each call
                importData();
            }
            catch ( Throwable t )
            {
                t.printStackTrace();
            }
        }
    }
}
