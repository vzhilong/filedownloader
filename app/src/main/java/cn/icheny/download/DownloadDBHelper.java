package cn.icheny.download;

import android.util.Log;

import java.io.File;
import java.util.List;

import io.github.yedaxia.sqliteutils.DaoFactory;
import io.github.yedaxia.sqliteutils.DbSqlite;
import io.github.yedaxia.sqliteutils.IBaseDao;

/**
 * Created by vincent on 2017/12/1.
 */

public class DownloadDBHelper {

    private DbSqlite dbSqlite;
    private IBaseDao<DownloadRecord> downloadRecordDao;
    private IBaseDao<DownloadSubProcess> downloadSubProcessDao;

    public DownloadDBHelper() {
        File dir = MyApplication.sApp.getDatabasePath(BuildConfig.APPLICATION_ID);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        dbSqlite = new DbSqlite(MyApplication.sApp, dir.getPath() + "/default.db");

        downloadRecordDao = DaoFactory.createGenericDao(dbSqlite, DownloadRecord.class);
        downloadRecordDao.createTable();
        downloadRecordDao.updateTable();

        downloadSubProcessDao = DaoFactory.createGenericDao(dbSqlite, DownloadSubProcess.class);
        downloadSubProcessDao.createTable();
        downloadSubProcessDao.updateTable();
    }

    public static DownloadDBHelper getInstance() {
        return SingletonHolder.sInstance;
    }

    public long saveRecord(DownloadRecord downloadRecord) {
        try {
            downloadRecordDao.insert(downloadRecord);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }
        return 0;
    }

    public DownloadRecord getRecord(String key) {
        try {
            return downloadRecordDao.queryFirstRecord("key = ?", key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
//            dbSqlite.closeDB();
        }
    }

    public void deleteRecord(String key) {
        try {
            downloadRecordDao.delete("key = ?", key);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }
    }

    public List<DownloadRecord> getAllRecord() {
        try {
            return downloadRecordDao.queryAll();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }

        return null;
    }

    public void clearSubProcess(String recordKey) {
        try {
            downloadSubProcessDao.delete("record_key = ?", recordKey);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }
    }

    public List<DownloadSubProcess> getSubProcess(String recordKey) {
        try {
            return downloadSubProcessDao.query("record_key = ?", new String[]{recordKey});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }

        return null;
    }

    public void saveSubProcess(DownloadSubProcess subProcess) {
        try {
            downloadSubProcessDao.insert(subProcess);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }
    }

    public void updateSubProcess(DownloadSubProcess subProcess, long startIdx) {
        try {
            downloadSubProcessDao.update(subProcess, "start_idx = ?", String.valueOf(startIdx));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }
    }

    private static class SingletonHolder {
        static DownloadDBHelper sInstance = new DownloadDBHelper();
    }
}
