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

    public long saveRecord(DownloadRecord downloadRecord) {
        try {
            return downloadRecordDao.insert(downloadRecord);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }
        return 0;
    }

    public DownloadRecord getRecord(String url) {
        try {
            return downloadRecordDao.queryFirstRecord("download_url = ?", url);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            dbSqlite.closeDB();
        }
    }

    public void deleteRecord(DownloadRecord downloadRecord) {
        try {
            downloadRecordDao.delete("id = ?", String.valueOf(downloadRecord.id));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }
    }

    public void deleteRecord(String downloadUrl) {
        try {
            int size = downloadRecordDao.delete("download_url = ?", downloadUrl);
            Log.d("vincent", "deleted size:" + size);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }
    }

    public void clearSubProcess(int recordId) {
        try {
            downloadSubProcessDao.delete("record_id = ?", String.valueOf(recordId));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }
    }

    public List<DownloadSubProcess> getSubProcess(int recordId) {
        try {
            return downloadSubProcessDao.query("record_id = ?", new String[]{String.valueOf(recordId)});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }

        return null;
    }

    public void saveSubProcess(DownloadSubProcess subProcess) {
        try {
            downloadSubProcessDao.insert(subProcess);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }
    }

    public void updateSubProcess(DownloadSubProcess subProcess, long startIdx) {
        try {
            downloadSubProcessDao.update(subProcess, "start_idx = ?", String.valueOf(startIdx));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbSqlite.closeDB();
        }
    }

    public static DownloadDBHelper getInstance() {
        return SingletonHolder.sInstance;
    }

    private static class SingletonHolder {
        static DownloadDBHelper sInstance = new DownloadDBHelper();
    }
}
