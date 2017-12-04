package cn.icheny.download;

import android.content.Context;
import android.content.SharedPreferences;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.util.ArrayList;
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

    private SharedPreferences mSubSP;

    public DownloadDBHelper() {
        File dir = MyApplication.sApp.getDatabasePath(BuildConfig.APPLICATION_ID);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        mSubSP = MyApplication.sApp.getSharedPreferences("download_sub_process", Context.MODE_PRIVATE);

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
        for (int i = 0; ; i++) {
            String key = String.format("download_sub_%s_%s", recordKey, i);
            if (mSubSP.contains(key)) {
                mSubSP.edit().remove(key).apply();
            } else {
                break;
            }
        }
    }

    public List<DownloadSubProcess> getSubProcess(String recordKey) {
        List<DownloadSubProcess> allSubProcess = new ArrayList<>();
        for (int i = 0; ; i++) {
            String key = String.format("download_sub_%s_%s", recordKey, i);
            if (mSubSP.contains(key)) {
                String str = mSubSP.getString(key, null);
                if (str == null) {
                    break;
                } else {
                    allSubProcess.add(JSON.parseObject(str, DownloadSubProcess.class));
                }
            } else {
                break;
            }
        }

        return allSubProcess;
    }

    public void saveSubProcess(DownloadSubProcess subProcess) {
        String key = String.format("download_sub_%s_%s", subProcess.recordKey, subProcess.subId);
        mSubSP.edit().putString(key, JSON.toJSONString(subProcess)).apply();
    }

    private static class SingletonHolder {
        static DownloadDBHelper sInstance = new DownloadDBHelper();
    }
}
