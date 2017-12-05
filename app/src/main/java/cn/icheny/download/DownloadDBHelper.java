package cn.icheny.download;

import android.content.Context;
import android.content.SharedPreferences;

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
    private IBaseDao<DownloadProcess> downloadProcessDao;
    private IBaseDao<DownloadSubProcess> downloadSubProcessDao;

    private SharedPreferences mSubSP;

    public DownloadDBHelper() {
        File dir = MyApplication.sApp.getDatabasePath(BuildConfig.APPLICATION_ID);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        mSubSP = MyApplication.sApp.getSharedPreferences("download_sub_process", Context.MODE_PRIVATE);

        dbSqlite = new DbSqlite(MyApplication.sApp, dir.getPath() + "/default.db");

        downloadProcessDao = DaoFactory.createGenericDao(dbSqlite, DownloadProcess.class);
        downloadProcessDao.createTable();
        downloadProcessDao.updateTable();

        downloadSubProcessDao = DaoFactory.createGenericDao(dbSqlite, DownloadSubProcess.class);
        downloadSubProcessDao.createTable();
        downloadSubProcessDao.updateTable();
    }

    public static DownloadDBHelper getInstance() {
        return SingletonHolder.sInstance;
    }

    public void saveProcess(DownloadProcess downloadProcess) {
        try {
            downloadProcessDao.insert(downloadProcess);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    public List<DownloadProcess> getAllProcess() {
        try {
            return downloadProcessDao.queryAll();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return null;
    }

    public void deleteProcess(String url) {
        try {
            downloadProcessDao.delete("download_url = ?", url);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            dbSqlite.closeDB();
        }
    }

    public void deleteSubProcess(String url) {
        try {
            downloadSubProcessDao.delete("download_url = ?", url);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }


    public void saveSubProcess(DownloadSubProcess subProcess) {
        try {
            downloadSubProcessDao.insert(subProcess);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    public List<DownloadSubProcess> getAllSubProcessList() {
        try {
            return downloadSubProcessDao.queryAll();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return null;
    }

    public List<DownloadSubProcess> getSubProcessList(String url) {
        try {
            return downloadSubProcessDao.query("download_url = ?", new String[]{url});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return null;
    }

    private static class SingletonHolder {
        static DownloadDBHelper sInstance = new DownloadDBHelper();
    }
}
